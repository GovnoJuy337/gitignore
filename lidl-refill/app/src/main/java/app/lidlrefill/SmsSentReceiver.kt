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
        if (resultCode == Activity.RESULT_OK) {
            // Refill ушёл — Lidl восстанавливает остаток до полного пакета. Подтверждение не ждём.
            if (p.baselineTime != 0L) {
                RefillService.resetCounter(ctx, p.packMb)
                RefillService.addLog(p, "Остаток сброшен до ${p.packMb} МБ")
            }
        } else {
            // Не ушло — пробуем снова примерно через минуту.
            p.lastSentTime = System.currentTimeMillis() - 2 * 60_000
        }
    }
}
