package app.lidlrefill

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (Prefs(ctx).enabled) {
            try { RefillService.start(ctx) } catch (_: Exception) {}
        }
    }
}
