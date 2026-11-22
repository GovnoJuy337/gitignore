package app.lidlrefill

import android.content.Context

/** Все настройки и состояние счётчика. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("refill", Context.MODE_PRIVATE)

    var number: String
        get() = sp.getString("number", "80808") ?: ""
        set(v) = sp.edit().putString("number", v.trim()).apply()
    var text: String
        get() = sp.getString("text", "Refill") ?: "Refill"
        set(v) = sp.edit().putString("text", v.trim()).apply()
    /** Сколько МБ даёт один Refill. */
    var packMb: Long
        get() = sp.getLong("packMb", 1024)
        set(v) = sp.edit().putLong("packMb", v).apply()
    /** Отправлять Refill, когда осталось не больше N МБ. */
    var thresholdMb: Long
        get() = sp.getLong("thresholdMb", 50)
        set(v) = sp.edit().putLong("thresholdMb", v).apply()
    /** Защита от расходов: максимум Refill за сутки. */
    var maxPerDay: Int
        get() = sp.getInt("maxPerDay", 0)
        set(v) = sp.edit().putInt("maxPerDay", v).apply()
    /** Как часто проверять трафик, в секундах. */
    var checkSec: Int
        get() = sp.getInt("checkSec", 30)
        set(v) = sp.edit().putInt("checkSec", v).apply()
    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    // Состояние: на момент baselineTime оставалось baselineRemainingMb.
    var baselineTime: Long
        get() = sp.getLong("baselineTime", 0)
        set(v) = sp.edit().putLong("baselineTime", v).apply()
    var baselineRemainingMb: Long
        get() = sp.getLong("baselineRemainingMb", 0)
        set(v) = sp.edit().putLong("baselineRemainingMb", v).apply()

    var usedBytes: Long
        get() = sp.getLong("usedBytes", 0)
        set(v) = sp.edit().putLong("usedBytes", v).apply()
    var lastCounter: Long
        get() = sp.getLong("lastCounter", 0)
        set(v) = sp.edit().putLong("lastCounter", v).apply()

    var lastSentTime: Long
        get() = sp.getLong("lastSentTime", 0)
        set(v) = sp.edit().putLong("lastSentTime", v).apply()
    var dayKey: Long
        get() = sp.getLong("dayKey", 0)
        set(v) = sp.edit().putLong("dayKey", v).apply()
    var sentToday: Int
        get() = sp.getInt("sentToday", 0)
        set(v) = sp.edit().putInt("sentToday", v).apply()
    var awaitingConfirm: Boolean
        get() = sp.getBoolean("awaitingConfirm", false)
        set(v) = sp.edit().putBoolean("awaitingConfirm", v).apply()
    /** Кусок текста SMS-подтверждения от Lidl. */
    var confirmText: String
        get() = sp.getString("confirmText", "Nachbuchoption ist jetzt gebucht") ?: ""
        set(v) = sp.edit().putString("confirmText", v.trim()).apply()
    /** SIM для отправки: subscriptionId, -1 = SIM по умолчанию для SMS. */
    var subId: Int
        get() = sp.getInt("subId", -1)
        set(v) = sp.edit().putInt("subId", v).apply()
    /** Кусок текста SMS-предупреждения «почти израсходовано». */
    var warnText: String
        get() = sp.getString("warnText", "fast verbraucht") ?: ""
        set(v) = sp.edit().putString("warnText", v.trim()).apply()
    var log: String
        get() = sp.getString("log", "") ?: ""
        set(v) = sp.edit().putString("log", v).apply()
}
