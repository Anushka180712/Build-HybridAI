/**
 * Application entry point: initialises Firebase, App Check (debug provider in debug builds)
 * and anonymous sign-in.
 */
package app.chaukas

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

class ChaukasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(context = this)
        app.chaukas.guard.Guard.initChannels(this)
        // App Check is required for Firebase AI Logic. Debug builds use the
        // debug provider (register the token shown on the Engine screen); release
        // builds use Play Integrity.
        val factory = if (BuildConfig.DEBUG) debugFactory() else PlayIntegrityAppCheckProviderFactory.getInstance()
        Firebase.appCheck.installAppCheckProviderFactory(factory)
        // Debug builds: ask for a token right away, so the debug secret exists and is shown
        // on the Engine screen (and printed to Logcat) without having to run a check first.
        if (BuildConfig.DEBUG) Firebase.appCheck.getAppCheckToken(false)
    }

    private fun debugFactory(): com.google.firebase.appcheck.AppCheckProviderFactory =
        // Loaded reflectively so the debug artifact is only needed in debug builds.
        Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
            .getMethod("getInstance").invoke(null) as com.google.firebase.appcheck.AppCheckProviderFactory
}

/**
 * The App Check debug secret of this install, for debug builds only. The debug provider keeps
 * it in its own SharedPreferences file; it is read here so it can be shown and copied in the
 * app instead of being searched for in Logcat. Returns null in release builds or before the
 * secret exists.
 */
object DebugToken {
    fun read(ctx: android.content.Context): String? {
        if (!BuildConfig.DEBUG) return null
        val dir = java.io.File(ctx.applicationInfo.dataDir, "shared_prefs")
        val files = dir.listFiles { f -> f.name.contains("appcheck.debug") }.orEmpty()
        for (f in files) {
            val prefs = ctx.getSharedPreferences(f.name.removeSuffix(".xml"), android.content.Context.MODE_PRIVATE)
            prefs.all.entries.firstOrNull { it.key.contains("DEBUG_SECRET") }?.let { return it.value?.toString() }
        }
        return null
    }
}
