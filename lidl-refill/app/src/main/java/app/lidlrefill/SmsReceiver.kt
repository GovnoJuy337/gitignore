package app.lidlrefill

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Ловит входящие SMS и ищет подтверждение пополнения от Lidl. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val p = Prefs(ctx)
        if (!p.enabled) return
        val body = Telephony.Sms.Intents.getMessagesFromIntent(i)
            .joinToString("") { it.messageBody ?: "" }
        if (RefillService.isConfirmation(p, body)) RefillService.onConfirmed(ctx)
    }
}
