package app.lidlrefill

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.text.InputType
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var number: EditText
    private lateinit var text: EditText
    private lateinit var pack: EditText
    private lateinit var thr: EditText
    private lateinit var maxDay: EditText
    private lateinit var interval: EditText
    private lateinit var remaining: EditText
    private lateinit var confirm: EditText
    private lateinit var warn: EditText
    private lateinit var sim: Spinner
    private var simIds = listOf(-1)
    private lateinit var status: TextView
    private lateinit var toggle: Button

    private fun field(root: LinearLayout, label: String, value: String, numeric: Boolean): EditText {
        root.addView(TextView(this).apply { this.text = label; setPadding(0, 24, 0, 0) })
        val e = EditText(this)
        e.setText(value)
        if (numeric) e.inputType = InputType.TYPE_CLASS_NUMBER
        root.addView(e)
        return e
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 24, 40, 40) }
        setContentView(ScrollView(this).apply { addView(root) })

        number = field(root, "Номер, куда слать SMS (из инструкции Lidl)", p.number, false)
        number.inputType = InputType.TYPE_CLASS_PHONE
        root.addView(TextView(this).apply { this.text = "SIM Lidl (SMS шлются ТОЛЬКО с неё)"; setPadding(0, 24, 0, 0) })
        sim = Spinner(this)
        root.addView(sim)
        text = field(root, "Текст SMS", p.text, false)
        pack = field(root, "Сколько МБ становится после Refill", p.packMb.toString(), true)
        thr = field(root, "Слать Refill, когда осталось ≤ (МБ)", p.thresholdMb.toString(), true)
        interval = field(root, "Проверять трафик каждые (секунд, минимум 5)", p.checkSec.toString(), true)
        maxDay = field(root, "Максимум Refill в сутки (0 = без ограничений)", p.maxPerDay.toString(), true)
        confirm = field(root, "Текст SMS-подтверждения от Lidl (часть, только для журнала)", p.confirmText, false)
        root.addView(TextView(this).apply { this.text = "Текст SMS-предупреждения от Lidl (часть)"; setPadding(0, 24, 0, 0) })
        warn = EditText(this).apply { setText(p.warnText) }
        root.addView(warn)
        remaining = field(root, "Сколько МБ осталось СЕЙЧАС (можно оставить пустым — тогда только по SMS от Lidl)", "", true)

        root.addView(Button(this).apply {
            this.text = "Задать остаток (не останавливая)"
            setOnClickListener {
                val r = remaining.text.toString().toLongOrNull()
                if (r == null) toast("Введи остаток в МБ") else {
                    RefillService.resetCounter(this@MainActivity, r)
                    RefillService.addLog(p, "Остаток задан вручную: $r МБ")
                    remaining.setText("")
                    refreshUi()
                }
            }
        })
        toggle = Button(this).apply { setOnClickListener { onToggle() } }
        root.addView(toggle)
        status = TextView(this).apply { setPadding(0, 32, 0, 0) }
        root.addView(status)
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        // Служба могла остановиться (обновление приложения, система) — поднимаем её.
        if (p.enabled) try { RefillService.start(this) } catch (_: Exception) {}
        refreshUi()
    }

    private fun save() {
        p.number = number.text.toString()
        p.text = text.text.toString().ifBlank { "Refill" }
        p.packMb = pack.text.toString().toLongOrNull() ?: 1024
        p.thresholdMb = thr.text.toString().toLongOrNull() ?: 50
        p.confirmText = confirm.text.toString()
        p.warnText = warn.text.toString()
        p.subId = simIds.getOrElse(sim.selectedItemPosition) { -1 }
        p.checkSec = (interval.text.toString().toIntOrNull() ?: 30).coerceAtLeast(5)
        p.maxPerDay = maxDay.text.toString().toIntOrNull() ?: 0
    }

    private fun onToggle() {
        if (p.enabled) {
            p.enabled = false
            RefillService.stop(this)
            refreshUi(); return
        }
        save()
        val rem = remaining.text.toString().toLongOrNull()
        when {
            p.number.isEmpty() -> toast("Введи номер для SMS")
            p.subId < 0 && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ->
                toast("Выбери SIM Lidl в списке")
            else -> {
                val need = mutableListOf<String>()
                if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
                    need += Manifest.permission.SEND_SMS
                if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
                    need += Manifest.permission.READ_PHONE_STATE
                if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED)
                    need += Manifest.permission.RECEIVE_SMS
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    need += Manifest.permission.POST_NOTIFICATIONS
                if (need.isNotEmpty()) {
                    requestPermissions(need.toTypedArray(), 1)
                    toast("Разреши SMS и уведомления, затем нажми ещё раз"); return
                }
                if (rem != null) RefillService.resetCounter(this, rem) else p.baselineTime = 0
                p.awaitingConfirm = false
                p.lastSentTime = 0
                p.enabled = true
                RefillService.start(this)
            }
        }
        refreshUi()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun loadSims() {
        RefillService.lidlSubId(this) // автопоиск SIM с «Lidl» в названии
        val names = mutableListOf("— не выбрана (SMS отправляться не будут) —")
        val ids = mutableListOf(-1)
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList?.forEach {
                    names += "SIM ${it.simSlotIndex + 1}: ${it.displayName} (${it.carrierName})"
                    ids += it.subscriptionId
                }
            } catch (_: SecurityException) {}
        } else names[0] += " (разреши «Телефон», чтобы выбрать SIM)"
        if (p.subId >= 0 && p.subId !in ids) { names += "SIM id ${p.subId} (определена по SMS)"; ids += p.subId }
        simIds = ids
        sim.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        sim.setSelection(ids.indexOf(p.subId).coerceAtLeast(0))
    }

    private fun refreshUi() {
        loadSims()
        toggle.text = if (p.enabled) "Остановить" else "Запустить"
        val est = if (p.enabled && p.baselineTime == 0L) "не считается (режим по SMS)" else if (p.enabled) try { "≈ ${RefillService.remainingMb(this)} МБ" } catch (e: Exception) { "?" } else "—"
        status.text = "Работает: ${if (p.enabled) "да" else "нет"}\n" +
            "Оценка остатка: $est\n" +
            "Refill сегодня: ${p.sentToday}\n" +
"\n" + p.log
        status.visibility = View.VISIBLE
    }
}
