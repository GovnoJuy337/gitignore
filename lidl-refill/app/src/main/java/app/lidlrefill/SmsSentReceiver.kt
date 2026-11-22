package app.lidlrefill

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager

/** Результат отправки Refill: реально ли SMS ушло в сеть. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val p = Prefs(ctx)
        val msg = when (resultCode) {
            Activity.RESULT_OK -> "SMS «${p.text}» ушло в сеть ✓"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "SMS НЕ ушло: нет сети на SIM Lidl"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "SMS НЕ ушло: SIM/радио выключено"
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED ->
                "SMS НЕ ушло: Android запретил короткий номер. Разреши «Платные SMS» для Lidl Refill"
            else -> "SMS НЕ ушло: ошибка $resultCode"
        }
        RefillService.addLog(p, msg)
        if (resultCode != Activity.RESULT_OK) {
            // Не ждём 3 минуты подтверждения — попробуем снова на следующей проверке через минуту.
            p.lastSentTime = System.currentTimeMillis() - 2 * 60_000
        }
    }
}
