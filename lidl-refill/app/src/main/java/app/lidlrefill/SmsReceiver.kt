package app.lidlrefill

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager

/** Ловит SMS от Lidl: «почти израсходовано» → шлём Refill, «gebucht» → подтверждение. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val p = Prefs(ctx)
        if (!p.enabled) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(i)
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val from = msgs.firstOrNull()?.originatingAddress ?: ""
        val warning = RefillService.isWarning(p, body)
        val confirm = RefillService.isConfirmation(p, body)
        if (!warning && !confirm) return

        // SMS пришло на SIM Lidl — запоминаем её, чтобы отвечать именно с неё.
        val sub = i.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            i.getIntExtra("subscription", -1))
        if (sub >= 0 && sub != p.subId) {
            p.subId = sub
            RefillService.addLog(p, "SIM Lidl определена автоматически (id $sub)")
        }
        if (confirm) RefillService.onConfirmed(ctx)
        if (warning) {
            if (from.isNotEmpty() && from != p.number)
                RefillService.addLog(p, "Предупреждение пришло с номера $from")
            RefillService.sendRefill(ctx, "Lidl прислал «почти израсходовано»")
        }
    }
}
