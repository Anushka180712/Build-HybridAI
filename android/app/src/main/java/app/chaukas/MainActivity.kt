/**
 * Single activity. Receives text or screenshots shared from other apps (ACTION_SEND), the
 * "Check with Chaukas" text-selection action, and taps on an auto-check warning.
 */
package app.chaukas

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.chaukas.guard.Guard
import app.chaukas.ui.ChaukasRoot
import app.chaukas.ui.theme.ChaukasTheme

class MainActivity : ComponentActivity() {
    private val vm: ChaukasViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { ChaukasTheme { ChaukasRoot(vm) } }
    }

    // If the phone stopped the auto-check in the background, opening the app reconnects it.
    override fun onResume() {
        super.onResume()
        if (app.chaukas.guard.Guard.isOn(this) && app.chaukas.guard.Guard.hasAccess(this)) {
            android.service.notification.NotificationListenerService.requestRebind(
                android.content.ComponentName(this, app.chaukas.guard.GuardService::class.java),
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Text or a screenshot shared from Messages, WhatsApp or Gmail, a text selection, or a warning tap. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
            vm.checkImage(this, uri)
            return
        }
        val shared = when (intent.action) {
            Intent.ACTION_SEND, Guard.ACTION_CHECK -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!shared.isNullOrBlank()) {
            vm.text = shared
            vm.check()
        }
    }
}
