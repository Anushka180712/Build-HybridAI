/**
 * Single activity. Receives text or screenshots shared from other apps (ACTION_SEND), the
 * "Check with Chaukas" text-selection action, and taps on an auto-check warning.
 */
package app.chaukas

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.chaukas.data.Repository
import app.chaukas.guard.Guard
import app.chaukas.guard.GuardService
import app.chaukas.ui.ChaukasRoot
import app.chaukas.ui.theme.ChaukasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: ChaukasViewModel by viewModels()

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestSmsPermission.launch(Manifest.permission.RECEIVE_SMS)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { Repository.ensureAuth(applicationContext) }
        }
        if (Guard.isOn(this) && Guard.hasAccess(this)) {
            Guard.start(this)
            Guard.forceRebind(this)
        }
        setContent { ChaukasTheme { ChaukasRoot(vm) } }
    }

    // If the phone stopped the auto-check in the background, opening the app reconnects it.
    override fun onResume() {
        super.onResume()
        if (Guard.isOn(this) && Guard.hasAccess(this)) {
            Guard.start(this)
            Guard.forceRebind(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
        val from = intent.getStringExtra(Guard.EXTRA_FROM).orEmpty()
        val isKnown = if (intent.hasExtra(Guard.EXTRA_IS_KNOWN)) {
            intent.getBooleanExtra(Guard.EXTRA_IS_KNOWN, false)
        } else if (from.isNotBlank()) {
            Guard.isSenderKnown(this, from)
        } else {
            false
        }
        val autoShare = intent.getBooleanExtra("auto_share", false)
        val shared = when (intent.action) {
            Intent.ACTION_SEND, Guard.ACTION_CHECK -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!shared.isNullOrBlank()) {
            vm.text = shared
            vm.check(isKnown = isKnown, fromSender = from, autoShare = autoShare)
        }
    }
}
