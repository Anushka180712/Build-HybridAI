package app.chaukas.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/**
 * Handles quick dismissal actions from the scam warning notification
 * (e.g. "It's okay" button on known contact warning).
 */
class GuardActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra("notification_id", -1)
        if (notifId != -1) {
            runCatching {
                NotificationManagerCompat.from(context).cancel(notifId)
            }
        }
    }
}
