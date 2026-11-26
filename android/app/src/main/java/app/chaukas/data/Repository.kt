/**
 * Firestore and Remote Config access. Writes the same redacted schema as the web and iOS apps,
 * so all three share history and circles.
 */
package app.chaukas.data

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import app.chaukas.ai.AnalysisRun
import app.chaukas.ai.Redactor
import app.chaukas.ai.Verdict
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class SyncedCheck(
    val id: String,
    val verdict: String,
    val riskScore: Int,
    val category: String,
    val summary: String,
    val excerpt: String,
    val source: String,
    val createdAt: Long,
)

data class Circle(
    val id: String,
    val name: String,
    val ownerUid: String,
    val members: List<String>,
    val memberNames: Map<String, String> = emptyMap(),
    val memberHandles: Map<String, String> = emptyMap(),
    val invited: List<String> = emptyList(),
    val invitedNames: Map<String, String> = emptyMap(),
    val invitedBy: Map<String, String> = emptyMap(),
)

data class Invite(
    val id: String,
    val name: String,
    val by: String,
    val members: Int,
)

data class CircleAlert(
    val id: String,
    val byUid: String,
    val byName: String,
    val verdict: String,
    val riskScore: Int,
    val category: String,
    val summary: String,
    val excerpt: String,
    val note: String,
    val createdAt: Long,
    val reactions: Map<String, String> = emptyMap(),
)

data class PersonInfo(val uid: String, val name: String, val handle: String)
data class KeepResult(val name: String, val group: String, val groupId: String)

/**
 * Shares the exact Firestore schema used by the web app, so a parent on
 * Android and a child on desktop Chrome see the same history and circle.
 */
object Repository {
    private val db get() = Firebase.firestore
    private val auth get() = Firebase.auth

    val uid: String? get() = auth.currentUser?.uid
    val currentUser get() = auth.currentUser
    val myDisplayName: String get() = auth.currentUser?.displayName?.ifBlank { null } ?: savedUserName ?: "Me"

    var savedUserName: String? = null
        private set

    fun initPrefs(context: Context) {
        val prefs = context.getSharedPreferences("chaukas_prefs", Context.MODE_PRIVATE)
        savedUserName = prefs.getString("chaukas_user_name", null)
    }

    fun getSavedName(context: Context): String? {
        if (savedUserName == null) initPrefs(context)
        return savedUserName
    }

    fun saveUserName(context: Context, name: String) {
        val trimmed = name.trim().take(40)
        savedUserName = trimmed
        val prefs = context.getSharedPreferences("chaukas_prefs", Context.MODE_PRIVATE)
        prefs
            .edit()
            .putString("chaukas_user_name", trimmed)
            .apply()
    }

    fun getSavedHandle(context: Context): String? {
        val prefs = context.getSharedPreferences("chaukas_prefs", Context.MODE_PRIVATE)
        return prefs.getString("chaukas_handle", null)
    }

    fun saveHandle(context: Context, handle: String) {
        val prefs = context.getSharedPreferences("chaukas_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("chaukas_handle", handle).apply()
    }

    fun ensureMyHandleSync(name: String = myDisplayName, context: Context): String {
        val cached = getSavedHandle(context)
        if (!cached.isNullOrBlank() && InviteUtils.handleFitsName(cached, name)) {
            return cached
        }
        val secureRandom = java.security.SecureRandom()
        val digits = "%04d".format(secureRandom.nextInt(10000))
        val candidate = InviteUtils.makeHandle(name, digits)
        saveHandle(context, candidate)
        return candidate
    }

    const val MAX_GROUPS = 2
    const val SHARE_MIN_RISK = 65
    const val ALERT_DAYS = 3
    private const val ALERT_MS = ALERT_DAYS * 24L * 60 * 60 * 1000

    class GroupLimit : Exception("You can be in $MAX_GROUPS groups at a time. Leave one group first, then try again.")

    suspend fun ensureAuth(context: Context? = null): FirebaseUser {
        val current = auth.currentUser
        val saved = (if (context != null) getSavedName(context) else null) ?: savedUserName
        if (current != null) {
            if (!saved.isNullOrBlank() && current.displayName.isNullOrBlank()) {
                runCatching {
                    current.updateProfile(userProfileChangeRequest { displayName = saved }).await()
                }
            }
            return current
        }
        val user = auth.signInAnonymously().await().user ?: error("Failed to sign in anonymously")
        if (!saved.isNullOrBlank()) {
            runCatching {
                user.updateProfile(userProfileChangeRequest { displayName = saved }).await()
            }
        }
        return user
    }

    fun authUserFlow(): Flow<FirebaseUser?> = callbackFlow {
        trySend(auth.currentUser)
        val listener = FirebaseAuth.AuthStateListener { fAuth ->
            trySend(fAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(name: String, context: Context? = null): FirebaseUser {
        val user = ensureAuth(context)
        if (name.isNotBlank()) {
            val trimmed = name.trim().take(40)
            if (context != null) saveUserName(context, trimmed)
            else savedUserName = trimmed
            user.updateProfile(userProfileChangeRequest { displayName = trimmed }).await()
        }
        return user
    }

    fun signOut() {
        auth.signOut()
    }

    private fun excerpt(text: String, n: Int): String {
        val t = Redactor.redact(text).text.replace(Regex("\\s+"), " ").trim()
        return if (t.length > n) t.take(n - 1) + "…" else t
    }

    /* ---------------- History Checks ---------------- */

    fun deleteCheck(id: String) {
        val u = uid ?: return
        db.collection("users").document(u).collection("checks").document(id).delete()
    }

    fun saveCheck(run: AnalysisRun) {
        val u = uid ?: return
        db.collection("users").document(u).collection("checks").document(run.id).set(
            mapOf(
                "id" to run.id,
                "verdict" to run.analysis.verdict.name.lowercase(),
                "riskScore" to run.analysis.riskScore,
                "category" to run.analysis.category.take(40),
                "summary" to Redactor.redact(run.analysis.summary).text.take(400),
                "excerpt" to excerpt(run.input, 160),
                "source" to run.source.wire,
                "createdAt" to run.createdAt,
                "syncedAt" to FieldValue.serverTimestamp(),
            ),
        )
    }

    fun checks(): Flow<List<SyncedCheck>> = callbackFlow {
        val u = uid
        if (u == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val reg = db.collection("users").document(u).collection("checks")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { snap, _ ->
                trySend(
                    snap?.documents?.map {
                        SyncedCheck(
                            it.id,
                            it.getString("verdict") ?: "",
                            (it.getLong("riskScore") ?: 0).toInt(),
                            it.getString("category") ?: "",
                            it.getString("summary") ?: "",
                            it.getString("excerpt") ?: "",
                            it.getString("source") ?: "",
                            it.getLong("createdAt") ?: 0,
                        )
                    } ?: emptyList(),
                )
            }
        awaitClose { reg.remove() }
    }

    /* ---------------- Chaukas ID (Handles) ---------------- */

    suspend fun ensureMyHandle(name: String = myDisplayName, context: Context? = null): String {
        if (context != null) {
            val cached = getSavedHandle(context)
            if (!cached.isNullOrBlank() && InviteUtils.handleFitsName(cached, name)) {
                runCatching {
                    val u = ensureAuth(context).uid
                    db.collection("handles").document(cached).set(mapOf("uid" to u, "name" to name.take(60)))
                    db.collection("profiles").document(u).set(mapOf("handle" to cached))
                }
                return cached
            }
        }

        val fallback = if (context != null) ensureMyHandleSync(name, context) else {
            val digits = "%04d".format(java.security.SecureRandom().nextInt(10000))
            InviteUtils.makeHandle(name, digits)
        }

        return try {
            val user = ensureAuth(context)
            val u = user.uid
            val profileDoc = db.collection("profiles").document(u).get().await()
            val old = if (profileDoc.exists()) profileDoc.getString("handle") ?: "" else ""
            if (old.isNotBlank() && InviteUtils.handleFitsName(old, name)) {
                if (context != null) saveHandle(context, old)
                return old
            }

            var handle = fallback
            val secureRandom = java.security.SecureRandom()
            for (i in 0 until 10) {
                val candidate = if (i == 0) fallback else {
                    val digits = "%04d".format(secureRandom.nextInt(10000))
                    InviteUtils.makeHandle(name, digits)
                }
                try {
                    val exists = db.collection("handles").document(candidate).get().await().exists()
                    if (!exists) {
                        db.collection("handles").document(candidate).set(
                            mapOf("uid" to u, "name" to name.take(60)),
                        ).await()
                        handle = candidate
                        break
                    }
                } catch (_: Exception) {
                    break
                }
            }

            runCatching {
                db.collection("profiles").document(u).set(mapOf("handle" to handle)).await()
                if (old.isNotBlank() && old != handle) {
                    db.collection("handles").document(old).delete().await()
                }
            }
            if (context != null) saveHandle(context, handle)
            handle
        } catch (_: Exception) {
            if (context != null) saveHandle(context, fallback)
            fallback
        }
    }

    suspend fun findPerson(input: String, context: Context? = null): PersonInfo? {
        val handle = InviteUtils.normalizeHandle(input)
        if (handle.isBlank()) return null
        return runCatching {
            ensureAuth(context)
            val doc = db.collection("handles").document(handle).get().await()
            if (!doc.exists()) return@runCatching null
            val u = doc.getString("uid") ?: return@runCatching null
            val n = doc.getString("name") ?: "Member"
            PersonInfo(uid = u, name = n, handle = handle)
        }.getOrNull()
    }

    /* ---------------- Circles ---------------- */

    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private fun newCode(): String {
        val rnd = java.security.SecureRandom()
        return (0 until 6).map { ALPHABET[rnd.nextInt(ALPHABET.length)] }.joinToString("")
    }

    suspend fun checkGroupLimit(target: String, context: Context? = null) {
        val user = ensureAuth(context)
        val u = user.uid
        val snap = db.collection("circles").whereArrayContains("members", u).get().await()
        val others = snap.documents.count { it.id != target }
        if (others >= MAX_GROUPS) throw GroupLimit()
    }

    suspend fun createCircle(name: String, context: Context? = null): String {
        val user = ensureAuth(context)
        val u = user.uid
        val currentName = myDisplayName.ifBlank { "Me" }
        var lastErr: Exception? = null
        for (attempt in 0 until 3) {
            val code = newCode()
            try {
                db.collection("circles").document(code).set(
                    mapOf(
                        "name" to name.trim().take(40).ifBlank { "Family" },
                        "ownerUid" to u,
                        "members" to listOf(u),
                        "memberNames" to mapOf(u to currentName),
                        "invited" to emptyList<String>(),
                        "invitedNames" to emptyMap<String, String>(),
                        "invitedBy" to emptyMap<String, String>(),
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
                return code
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw lastErr ?: Exception("Could not create the circle.")
    }

    suspend fun joinCircle(codeRaw: String, context: Context? = null): String {
        val user = ensureAuth(context)
        val u = user.uid
        val clean = codeRaw.uppercase().filter { it.isLetterOrDigit() }
        require(clean.length == 6) { "Circle codes have 6 characters." }
        checkGroupLimit(clean, context)
        val ref = db.collection("circles").document(clean)
        ref.update(
            mapOf("members" to FieldValue.arrayUnion(u), "memberNames.$u" to myDisplayName),
        ).await()
        return clean
    }

    suspend fun leaveCircle(circle: Circle, context: Context? = null) {
        val user = runCatching { ensureAuth(context) }.getOrNull()
        val u = user?.uid ?: uid ?: return
        val ref = db.collection("circles").document(circle.id)
        val alone = circle.members.all { it == u }
        if (alone && circle.ownerUid == u) {
            ref.delete().await()
        } else {
            val updates = mutableMapOf<String, Any>(
                "members" to FieldValue.arrayRemove(u),
                "memberNames.$u" to FieldValue.delete(),
            )
            if (circle.memberHandles.containsKey(u)) {
                updates["memberHandles.$u"] = FieldValue.delete()
            }
            ref.update(updates).await()
        }
    }

    suspend fun deleteCircle(circle: Circle, context: Context? = null) {
        val user = runCatching { ensureAuth(context) }.getOrNull()
        val u = user?.uid ?: uid ?: return
        val ref = db.collection("circles").document(circle.id)
        if (circle.ownerUid == u) {
            ref.delete().await()
        } else {
            leaveCircle(circle, context)
        }
    }

    suspend fun setMyHandleIn(circleId: String, handle: String) {
        val u = uid ?: return
        db.collection("circles").document(circleId).update("memberHandles.$u", handle).await()
    }

    suspend fun keepPerson(handle: String, myGroup: Circle?, context: Context? = null): KeepResult {
        val user = ensureAuth(context)
        val u = user.uid
        val cleanHandle = InviteUtils.normalizeHandle(handle)
        if (cleanHandle.isBlank()) error("Please enter a valid Chaukas ID.")
        val person = findPerson(cleanHandle, context) ?: error("No one has that Chaukas ID. Check it and try again.")
        if (person.uid == u) error("That's your own ID.")
        if (myGroup != null && myGroup.members.contains(person.uid)) {
            error("${person.name} is already in ${myGroup.name}.")
        }
        if (myGroup != null && myGroup.invited.contains(person.uid)) {
            error("${person.name} is already invited. Ask them to tap Stay in Family Circle.")
        }

        val group = myGroup ?: run {
            val gName = "${myDisplayName.split(" ")[0]}'s family".take(40)
            val id = createCircle(gName, context)
            Circle(id = id, name = gName, ownerUid = u, members = listOf(u), memberNames = mapOf(u to myDisplayName))
        }

        db.collection("circles").document(group.id).update(
            mapOf(
                "invited" to FieldValue.arrayUnion(person.uid),
                "invitedNames.${person.uid}" to person.name.take(60),
                "invitedBy.${person.uid}" to myDisplayName,
            ),
        ).await()

        return KeepResult(name = person.name, group = group.name, groupId = group.id)
    }

    suspend fun stayInCircle(circleId: String, context: Context? = null) {
        val user = ensureAuth(context)
        val u = user.uid
        checkGroupLimit(circleId, context)
        db.collection("circles").document(circleId).update(
            mapOf(
                "members" to FieldValue.arrayUnion(u),
                "memberNames.$u" to myDisplayName,
                "invited" to FieldValue.arrayRemove(u),
                "invitedNames.$u" to FieldValue.delete(),
                "invitedBy.$u" to FieldValue.delete(),
            ),
        ).await()
    }

    suspend fun leaveInvite(circleId: String, context: Context? = null) {
        val user = runCatching { ensureAuth(context) }.getOrNull()
        val u = user?.uid ?: uid ?: return
        db.collection("circles").document(circleId).update(
            mapOf(
                "invited" to FieldValue.arrayRemove(u),
                "invitedNames.$u" to FieldValue.delete(),
                "invitedBy.$u" to FieldValue.delete(),
            ),
        ).await()
    }

    fun listenMyCircles(uid: String? = Repository.uid): Flow<List<Circle>> = callbackFlow {
        val u = uid
        if (u == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val reg = db.collection("circles").whereArrayContains("members", u).addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(
                snap?.documents?.map { d ->
                    @Suppress("UNCHECKED_CAST")
                    Circle(
                        id = d.id,
                        name = d.getString("name") ?: "Family",
                        ownerUid = d.getString("ownerUid") ?: "",
                        members = (d.get("members") as? List<String>) ?: emptyList(),
                        memberNames = (d.get("memberNames") as? Map<String, String>) ?: emptyMap(),
                        memberHandles = (d.get("memberHandles") as? Map<String, String>) ?: emptyMap(),
                        invited = (d.get("invited") as? List<String>) ?: emptyList(),
                        invitedNames = (d.get("invitedNames") as? Map<String, String>) ?: emptyMap(),
                        invitedBy = (d.get("invitedBy") as? Map<String, String>) ?: emptyMap(),
                    )
                } ?: emptyList(),
            )
        }
        awaitClose { reg.remove() }
    }

    fun listenMyInvites(uid: String? = Repository.uid): Flow<List<Invite>> = callbackFlow {
        val u = uid
        if (u == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val reg = db.collection("circles").whereArrayContains("invited", u).addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(
                snap?.documents?.map { d ->
                    @Suppress("UNCHECKED_CAST")
                    val byMap = d.get("invitedBy") as? Map<String, String>
                    val members = (d.get("members") as? List<String>)?.size ?: 1
                    Invite(
                        id = d.id,
                        name = d.getString("name") ?: "Family",
                        by = byMap?.get(u) ?: "Someone",
                        members = members,
                    )
                } ?: emptyList(),
            )
        }
        awaitClose { reg.remove() }
    }

    /* ---------------- Alerts & Reactions ---------------- */

    fun alerts(circleId: String): Flow<List<CircleAlert>> = callbackFlow {
        val reg = db.collection("circles").document(circleId).collection("alerts")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snap, _ ->
                val now = System.currentTimeMillis()
                trySend(
                    snap?.documents?.mapNotNull { d ->
                        val created = (d.getTimestamp("createdAt")?.toDate()?.time) ?: now
                        if (now - created > ALERT_MS) return@mapNotNull null
                        @Suppress("UNCHECKED_CAST")
                        val reactions = (d.get("reactions") as? Map<String, String>) ?: emptyMap()
                        CircleAlert(
                            id = d.id,
                            byUid = d.getString("byUid") ?: "",
                            byName = d.getString("byName") ?: "Someone",
                            verdict = d.getString("verdict") ?: "",
                            riskScore = (d.getLong("riskScore") ?: 0).toInt(),
                            category = d.getString("category") ?: "other",
                            summary = d.getString("summary") ?: "",
                            excerpt = d.getString("excerpt") ?: "",
                            note = d.getString("note") ?: "",
                            createdAt = created,
                            reactions = reactions,
                        )
                    } ?: emptyList(),
                )
            }
        awaitClose { reg.remove() }
    }

    suspend fun reactToAlert(circleId: String, alertId: String, reactionKey: String) {
        val u = uid ?: error("Sign in first")
        val alertDoc = db.collection("circles").document(circleId).collection("alerts").document(alertId).get().await()
        @Suppress("UNCHECKED_CAST")
        val current = (alertDoc.get("reactions") as? Map<String, String>)?.get(u)
        val update = if (current == reactionKey) {
            mapOf("reactions.$u" to FieldValue.delete())
        } else {
            mapOf("reactions.$u" to reactionKey)
        }
        db.collection("circles").document(circleId).collection("alerts").document(alertId).update(update).await()
    }

    suspend fun shareAlert(circleId: String, run: AnalysisRun, note: String) {
        val u = uid ?: auth.currentUser?.uid ?: runCatching { ensureAuth() }.getOrNull()?.uid ?: error("Sign in first")
        db.collection("circles").document(circleId).collection("alerts").add(
            mapOf(
                "byUid" to u,
                "byName" to myDisplayName,
                "verdict" to run.analysis.verdict.name.lowercase(),
                "riskScore" to run.analysis.riskScore,
                "category" to run.analysis.category.take(40),
                "summary" to Redactor.redact(run.analysis.summary).text.take(400),
                "excerpt" to excerpt(run.input, 220),
                "note" to note.take(280),
                "createdAt" to FieldValue.serverTimestamp(),
                "expireAt" to com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() + ALERT_MS)),
                "reactions" to emptyMap<String, String>(),
            ),
        ).await()
    }

    fun getActiveCircleId(ctx: Context): String? =
        ctx.getSharedPreferences("chaukas.settings", Context.MODE_PRIVATE).getString("activeCircleId", null)

    fun setActiveCircleId(ctx: Context, id: String) {
        ctx.getSharedPreferences("chaukas.settings", Context.MODE_PRIVATE).edit().putString("activeCircleId", id).apply()
    }

    suspend fun autoShareToCircles(run: AnalysisRun, context: Context? = null): List<String> {
        val user = runCatching { ensureAuth(context) }.getOrNull()
        val u = user?.uid ?: uid ?: return emptyList()
        if (run.analysis.verdict == Verdict.SAFE) return emptyList()
        val snap = runCatching { db.collection("circles").whereArrayContains("members", u).get().await() }.getOrNull()
        if (snap == null || snap.isEmpty) return emptyList()
        val sharedNames = mutableListOf<String>()
        for (doc in snap.documents) {
            val name = doc.getString("name") ?: "Family"
            runCatching {
                shareAlert(doc.id, run, "")
                sharedNames.add(name)
            }
        }
        return sharedNames
    }

    /* ---------------- Remote Config ---------------- */

    const val DEFAULT_TREND_BANNER = "Scam wave: fake electricity-bill and KYC \"update\" texts are spiking. Never pay or share an OTP through a texted link."

    suspend fun cloudModel(): String = try {
        val rc = Firebase.remoteConfig
        rc.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 }).await()
        rc.setDefaultsAsync(mapOf("cloud_model" to "gemini-3.5-flash-lite", "trend_banner" to DEFAULT_TREND_BANNER)).await()
        rc.fetchAndActivate().await()
        rc.getString("cloud_model").ifBlank { "gemini-3.5-flash-lite" }
    } catch (_: Exception) {
        "gemini-3.5-flash-lite"
    }

    fun trendBanner(): String = try {
        Firebase.remoteConfig.getString("trend_banner").ifBlank { DEFAULT_TREND_BANNER }
    } catch (_: Exception) {
        DEFAULT_TREND_BANNER
    }
}
