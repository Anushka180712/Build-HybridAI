/**
 * Firestore and Remote Config access. Writes the same redacted schema as the web and iOS apps,
 * so all three share history and circles.
 */
package app.chaukas.data

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import app.chaukas.ai.AnalysisRun
import app.chaukas.ai.Redactor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class SyncedCheck(val id: String, val verdict: String, val riskScore: Int, val category: String, val summary: String, val excerpt: String, val source: String, val createdAt: Long)
data class CircleInfo(val id: String, val name: String, val members: List<String>)
data class CircleAlert(val id: String, val byName: String, val verdict: String, val riskScore: Int, val excerpt: String, val note: String, val summary: String)

/**
 * Shares the exact Firestore schema used by the web app, so a parent on
 * Android and a child on desktop Chrome see the same history and circle.
 */
object Repository {
    private val db get() = Firebase.firestore
    private val auth get() = Firebase.auth

    val uid: String? get() = auth.currentUser?.uid

    suspend fun signIn(name: String) {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user!!
        if (name.isNotBlank()) user.updateProfile(userProfileChangeRequest { displayName = name.trim().take(40) }).await()
    }

    private fun excerpt(text: String, n: Int): String {
        val t = Redactor.redact(text).text.replace(Regex("\\s+"), " ").trim()
        return if (t.length > n) t.take(n - 1) + "…" else t
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
        if (u == null) { trySend(emptyList()); close(); return@callbackFlow }
        val reg = db.collection("users").document(u).collection("checks")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.map {
                    SyncedCheck(
                        it.id, it.getString("verdict") ?: "", (it.getLong("riskScore") ?: 0).toInt(), it.getString("category") ?: "",
                        it.getString("summary") ?: "", it.getString("excerpt") ?: "", it.getString("source") ?: "", it.getLong("createdAt") ?: 0,
                    )
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    fun circles(): Flow<List<CircleInfo>> = callbackFlow {
        val u = uid
        if (u == null) { trySend(emptyList()); close(); return@callbackFlow }
        val reg = db.collection("circles").whereArrayContains("members", u).addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.map { d ->
                @Suppress("UNCHECKED_CAST")
                CircleInfo(d.id, d.getString("name") ?: "Family", (d.get("members") as? List<String>) ?: emptyList())
            } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    fun alerts(circleId: String): Flow<List<CircleAlert>> = callbackFlow {
        val reg = db.collection("circles").document(circleId).collection("alerts")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.map {
                    CircleAlert(
                        it.id, it.getString("byName") ?: "Someone", it.getString("verdict") ?: "", (it.getLong("riskScore") ?: 0).toInt(),
                        it.getString("excerpt") ?: "", it.getString("note") ?: "", it.getString("summary") ?: "",
                    )
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    const val MAX_GROUPS = 2

    class GroupLimit : Exception("You can be in $MAX_GROUPS groups at a time. Leave one group first, then try again.")

    suspend fun joinCircle(code: String) {
        val u = uid ?: error("Sign in first")
        val clean = code.uppercase().filter { it.isLetterOrDigit() }
        require(clean.length == 6) { "Circle codes have 6 characters." }
        // A person can be in up to MAX_GROUPS family groups at once.
        val mine = db.collection("circles").whereArrayContains("members", u).get().await().documents.count { it.id != clean }
        if (mine >= MAX_GROUPS) throw GroupLimit()
        db.collection("circles").document(clean).update(
            mapOf("members" to FieldValue.arrayUnion(u), "memberNames.$u" to (auth.currentUser?.displayName ?: "Member")),
        ).await()
    }

    suspend fun shareAlert(circleId: String, run: AnalysisRun, note: String) {
        val u = uid ?: error("Sign in first")
        db.collection("circles").document(circleId).collection("alerts").add(
            mapOf(
                "byUid" to u,
                "byName" to (auth.currentUser?.displayName ?: "Someone"),
                "verdict" to run.analysis.verdict.name.lowercase(),
                "riskScore" to run.analysis.riskScore,
                "category" to run.analysis.category.take(40),
                "summary" to Redactor.redact(run.analysis.summary).text.take(400),
                "excerpt" to excerpt(run.input, 220),
                "note" to note.take(280),
                "createdAt" to FieldValue.serverTimestamp(),
                // Group warnings expire after 3 days (Firestore TTL policy on expireAt).
                "expireAt" to com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000)),
                "reactions" to emptyMap<String, String>(),
            ),
        ).await()
    }

    /** Same Remote Config keys as the web app. */
    suspend fun cloudModel(): String = try {
        val rc = Firebase.remoteConfig
        rc.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 }).await()
        rc.setDefaultsAsync(mapOf("cloud_model" to "gemini-3.5-flash-lite", "trend_banner" to "")).await()
        rc.fetchAndActivate().await()
        rc.getString("cloud_model").ifBlank { "gemini-3.5-flash-lite" }
    } catch (e: Exception) {
        "gemini-3.5-flash-lite"
    }

    fun trendBanner(): String = try { Firebase.remoteConfig.getString("trend_banner") } catch (e: Exception) { "" }
}
