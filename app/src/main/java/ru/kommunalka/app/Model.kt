package ru.kommunalka.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

/* ---------- Справочники ---------- */

enum class Kind(val title: String, val short: String, val unit: String, val frac: Int, val intDigits: Int) {
    COLD("Холодная вода", "ХВС", "м³", 3, 5),
    HOT("Горячая вода", "ГВС", "м³", 3, 5),
    ELEC("Электроэнергия", "Свет", "кВт·ч", 1, 6),
    GAS("Газ", "Газ", "м³", 3, 5),
    HEAT("Отопление", "Тепло", "Гкал", 3, 4);

    val isWater: Boolean get() = this == COLD || this == HOT
}

fun zoneNames(zones: Int): List<String> = when (zones) {
    2 -> listOf("День (Т1)", "Ночь (Т2)")
    3 -> listOf("Пик (Т1)", "Ночь (Т2)", "Полупик (Т3)")
    else -> listOf("Показание")
}

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)
fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
fun round3(v: Double): Double = Math.round(v * 1000.0) / 1000.0

/* ---------- Данные ---------- */

/** month — "yyyy-MM", за какой месяц показание; date — "yyyy-MM-dd", когда снято. */
data class Reading(
    val id: String,
    val month: String,
    val date: String,
    val values: List<Double>,
    val tariffs: List<Double>
)

data class Meter(
    val id: String,
    val kind: Kind,
    val name: String,
    val serial: String,
    val zones: Int,
    val tariffs: List<Double>,
    val verifyUntil: String?,
    val readings: List<Reading>
) {
    val label: String get() = name.ifBlank { kind.title }
}

data class Fixed(val id: String, val name: String, val amount: Double)

data class Bill(val receipt: Double? = null, val paid: Double = 0.0, val paidDate: String? = null)

data class Apartment(
    val id: String,
    val name: String,
    val address: String,
    val winFrom: Int,
    val winTo: Int,
    val sewerOn: Boolean,
    val sewerTariff: Double,
    val fixed: List<Fixed>,
    val meters: List<Meter>,
    val bills: Map<String, Bill>
)

data class AppState(val apts: List<Apartment> = emptyList(), val notifyHour: Int = 10)

/* ---------- Даты и форматирование ---------- */

private val MONTHS = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
private val MONTHS_G = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
private val MONTHS_P = listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")
private val MONTHS_S = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

val DMY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun ymNow(): String = YearMonth.now().toString()
fun ymAdd(ym: String, n: Long): String = YearMonth.parse(ym).plusMonths(n).toString()
private fun monthIdx(ym: String): Int = ym.substring(5, 7).toInt() - 1
fun monthTitle(ym: String): String = MONTHS[monthIdx(ym)] + " " + ym.substring(0, 4)
fun monthPrep(ym: String): String = MONTHS_P[monthIdx(ym)]
fun monthShort(ym: String): String = MONTHS_S[monthIdx(ym)]
fun monthGen(d: LocalDate): String = MONTHS_G[d.monthValue - 1]
fun dayMonth(d: LocalDate): String = "${d.dayOfMonth} ${monthGen(d)}"
fun fullDate(d: LocalDate): String = "${dayMonth(d)} ${d.year}"
fun fullDate(iso: String): String = parseIso(iso)?.let { fullDate(it) } ?: iso

private val RU: Locale = Locale.forLanguageTag("ru-RU")

fun fmt(v: Double, digits: Int = 3): String =
    NumberFormat.getNumberInstance(RU).apply { maximumFractionDigits = digits; minimumFractionDigits = 0 }.format(v)

fun money(v: Double): String =
    NumberFormat.getNumberInstance(RU).apply { maximumFractionDigits = 2; minimumFractionDigits = 2 }.format(v) + " ₽"

/** Число для поля ввода: без пробелов-разделителей тысяч, с запятой. */
fun numInput(v: Double, digits: Int = 3): String =
    fmt(v, digits).replace("\u00A0", "").replace("\u202F", "").replace(" ", "")

private val NUM_RE = Regex("""^\d{1,12}(?:[.,]\d{0,6})?$""")

/**
 * Разбирает неотрицательное число, введённое пользователем.
 * Принимает только цифры и одну запятую/точку: «NaN», «Infinity», «1e9» и минус отклоняются,
 * иначе такое значение не сохранилось бы в JSON и сломало бы запись всех данных.
 */
fun parseNum(s: String): Double? {
    val t = s.replace(" ", "").replace("\u00A0", "").replace("\u202F", "").trim()
    if (!NUM_RE.matches(t)) return null
    return t.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}

private val YM_RE = Regex("""^\d{4}-(0[1-9]|1[0-2])$""")

fun isYm(s: String): Boolean = YM_RE.matches(s)

/** Безопасный разбор даты "yyyy-MM-dd": при ошибке — null вместо падения. */
fun parseIso(s: String?): LocalDate? =
    if (s.isNullOrBlank()) null else try { LocalDate.parse(s) } catch (e: Exception) { null }

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && (m100 < 10 || m100 >= 20) -> few
        else -> many
    }
}

/* ---------- Расчёты по счётчику ---------- */

fun Meter.ordered(): List<Reading> = readings.sortedBy { it.month }
fun Meter.readingAt(ym: String): Reading? = readings.firstOrNull { it.month == ym }
fun Meter.prev(ym: String): Reading? = ordered().lastOrNull { it.month < ym }
fun Meter.next(ym: String): Reading? = ordered().firstOrNull { it.month > ym }
fun Meter.last(): Reading? = ordered().lastOrNull()

data class Consumption(val diffs: List<Double>, val total: Double, val cost: Double, val tariffs: List<Double>)

fun Meter.consumption(ym: String): Consumption? {
    val r = readingAt(ym) ?: return null
    val p = prev(ym) ?: return null
    val diffs = r.values.mapIndexed { i, v -> round3(v - p.values.getOrElse(i) { 0.0 }) }
    val cost = round2(diffs.mapIndexed { i, d -> d * r.tariffs.getOrElse(i) { 0.0 } }.sum())
    return Consumption(diffs, round3(diffs.sum()), cost, r.tariffs)
}

fun Meter.avgConsumption(before: String, n: Int = 6): Double? {
    val months = ordered().map { it.month }.filter { it < before }
    val vals = mutableListOf<Double>()
    for (i in months.indices.reversed()) {
        if (i == 0 || vals.size >= n) break
        consumption(months[i])?.let { vals.add(it.total) }
    }
    return if (vals.isEmpty()) null else vals.average()
}

/* ---------- Расчёты по квартире ---------- */

data class MonthCalc(
    val items: List<Pair<Meter, Consumption>>,
    val missing: List<Meter>,
    val sewerQty: Double,
    val sewerCost: Double,
    val hasSewer: Boolean,
    val fixedSum: Double,
    val total: Double,
    val any: Boolean
)

fun Apartment.calc(ym: String): MonthCalc {
    val items = mutableListOf<Pair<Meter, Consumption>>()
    val missing = mutableListOf<Meter>()
    var water = 0.0
    for (m in meters) {
        if (m.readingAt(ym) == null) missing += m
        val c = m.consumption(ym) ?: continue
        items += m to c
        if (m.kind.isWater) water += c.total
    }
    val any = items.isNotEmpty()
    val hasSewer = sewerOn && any
    val sewerCost = if (hasSewer) round2(water * sewerTariff) else 0.0
    val fixedSum = fixed.sumOf { it.amount }
    val total = round2(items.sumOf { it.second.cost } + sewerCost + fixedSum)
    return MonthCalc(items, missing, round3(water), sewerCost, hasSewer, fixedSum, total, any)
}

enum class Level { BAD, WARN, INFO, OK, NONE }

data class BillInfo(
    val bill: Bill,
    val calc: MonthCalc,
    val due: Double,
    val paid: Double,
    val rest: Double,
    val level: Level,
    val status: String
)

/** Срок оплаты — до 10-го числа следующего месяца. */
fun dueDate(ym: String): LocalDate = YearMonth.parse(ym).plusMonths(1).atDay(10)

fun Apartment.billInfo(ym: String, today: LocalDate = LocalDate.now()): BillInfo {
    val b = bills[ym] ?: Bill()
    val c = calc(ym)
    val due = round2(b.receipt ?: if (c.any) c.total else 0.0)
    val paid = b.paid
    val (lv, st) = when {
        due <= 0.0 && paid <= 0.0 -> Level.NONE to "Нет данных"
        paid > 0.0 && paid >= due - 0.5 -> Level.OK to "Оплачено"
        today.isAfter(dueDate(ym)) -> Level.BAD to (if (paid > 0.0) "Недоплата" else "Просрочено")
        else -> Level.WARN to (if (paid > 0.0) "Частично" else "К оплате")
    }
    return BillInfo(b, c, due, paid, round2(maxOf(0.0, due - paid)), lv, st)
}

fun Apartment.dataMonths(): List<String> {
    val set = bills.keys.toMutableSet()
    meters.forEach { m -> m.ordered().drop(1).forEach { set += it.month } }
    return set.sorted()
}

/* ---------- Напоминания ---------- */

enum class Goal { READINGS, PAY, METER }

data class Alert(
    val level: Level,
    val title: String,
    val sub: String,
    val aptId: String,
    val goal: Goal,
    val month: String? = null,
    val meterId: String? = null,
    val daysLeft: Int? = null
)

fun Apartment.alerts(today: LocalDate = LocalDate.now()): List<Alert> {
    val out = mutableListOf<Alert>()
    val cur = YearMonth.from(today).toString()
    val day = today.dayOfMonth
    val mg = monthGen(today)
    if (meters.isNotEmpty()) {
        val miss = meters.filter { it.readingAt(cur) == null }
        when {
            miss.isEmpty() -> out += Alert(Level.OK, "Показания за ${monthPrep(cur)} внесены", "", id, Goal.READINGS)
            day < winFrom -> out += Alert(
                Level.INFO, "Показания за ${monthPrep(cur)}: с $winFrom по $winTo $mg",
                "Напомним, когда откроется окно передачи", id, Goal.READINGS, daysLeft = winFrom - day
            )
            day <= winTo -> out += Alert(
                Level.WARN, "Передайте показания: ${miss.size} из ${meters.size}",
                "Окно открыто до $winTo $mg", id, Goal.READINGS, daysLeft = winTo - day
            )
            else -> out += Alert(
                Level.BAD, "Окно передачи показаний прошло",
                "Без показаний начислят по среднему — внесите их всё равно", id, Goal.READINGS
            )
        }
    }
    for (ym in dataMonths().filter { it < cur }) {
        val bi = billInfo(ym, today)
        val due = dueDate(ym)
        if (bi.level == Level.BAD) {
            out += Alert(
                Level.BAD, "Долг за ${monthPrep(ym)} ${ym.take(4)}: ${money(bi.rest)}",
                "Срок был до ${dayMonth(due)}", id, Goal.PAY, month = ym
            )
        } else if (bi.level == Level.WARN) {
            val note = if (bi.bill.receipt == null) " · сумма по расчёту" else ""
            out += Alert(
                Level.WARN, "Оплатить ${monthPrep(ym)}: ${money(bi.rest)}",
                "До ${dayMonth(due)}$note", id, Goal.PAY, month = ym,
                daysLeft = ChronoUnit.DAYS.between(today, due).toInt()
            )
        }
    }
    for (m in meters) {
        val v = m.verifyUntil ?: continue
        val vd = parseIso(v) ?: continue
        val d = ChronoUnit.DAYS.between(today, vd).toInt()
        if (d < 0) {
            out += Alert(Level.BAD, "Поверка просрочена: ${m.label}", "Срок истёк ${fullDate(v)}. Показания могут не принять", id, Goal.METER, meterId = m.id)
        } else if (d <= 60) {
            out += Alert(Level.WARN, "Скоро поверка: ${m.label}", "До ${fullDate(v)} — осталось $d дн.", id, Goal.METER, meterId = m.id, daysLeft = d)
        }
    }
    return out
}

/* ---------- Проверка вводимого показания ---------- */

data class ValueCheck(val level: Level?, val msg: String, val ok: Boolean)

fun checkValue(m: Meter, z: Int, month: String, text: String): ValueCheck {
    val k = m.kind
    val p = m.prev(month)
    if (text.isBlank()) {
        val pv = p?.values?.getOrNull(z)
        val msg = if (p != null && pv != null) "Прошлое: ${fmt(pv, k.frac)} (${monthShort(p.month)})" else "Первое показание этого счётчика"
        return ValueCheck(null, msg, true)
    }
    val v = parseNum(text) ?: return ValueCheck(Level.BAD, "Введите число, например 123,456", false)
    val n = m.next(month)
    val pv = p?.values?.getOrNull(z)
    val nv = n?.values?.getOrNull(z)
    if (pv != null && v < pv - 1e-9) {
        return ValueCheck(Level.BAD, "Меньше прошлого (${fmt(pv, k.frac)}). Проверьте цифры", false)
    }
    if (n != null && nv != null && v > nv + 1e-9) {
        return ValueCheck(Level.BAD, "Больше показания за ${monthPrep(n.month)} (${fmt(nv, k.frac)})", false)
    }
    if (pv == null) return ValueCheck(null, "Первое показание — расход появится со следующего месяца", true)
    val d = round3(v - pv)
    val avg = m.avgConsumption(month)
    val tar = m.tariffs.getOrElse(z) { 0.0 }
    if (avg != null && m.zones == 1 && d > avg * 3 && d - avg > 1) {
        return ValueCheck(Level.WARN, "Расход ${fmt(d)} ${k.unit} — в ${fmt(d / avg, 1)} раза выше обычного. Не перепутаны ли ХВС и ГВС?", true)
    }
    if (d == 0.0 && k.isWater) return ValueCheck(Level.WARN, "Нулевой расход — счётчик мог остановиться", true)
    val cost = if (tar > 0) " ≈ ${money(d * tar)}" else ""
    return ValueCheck(Level.OK, "Расход ${fmt(d)} ${k.unit}$cost", true)
}

/* ---------- Текст для управляющей компании ---------- */

fun Apartment.sendText(): String {
    val cur = ymNow()
    val lines = mutableListOf(address.ifBlank { name }, "Показания за ${monthPrep(cur)} ${cur.take(4)}:")
    for (m in meters) {
        val r = m.readingAt(cur) ?: m.last()
        if (r == null) {
            lines += "${m.label}: нет показаний"
            continue
        }
        val vals = if (m.zones > 1) {
            zoneNames(m.zones).mapIndexed { i, z ->
                z.substringAfter("(").substringBefore(")") + " " + fmt(r.values.getOrElse(i) { 0.0 }, m.kind.frac)
            }.joinToString(", ")
        } else fmt(r.values.getOrElse(0) { 0.0 }, m.kind.frac)
        val serial = if (m.serial.isNotBlank()) " (№ ${m.serial})" else ""
        val old = if (r.month != cur) " — за ${monthPrep(r.month)}" else ""
        lines += "${m.label}$serial: $vals ${m.kind.unit}$old"
    }
    return lines.joinToString("\n")
}

/* ---------- JSON ---------- */

private fun List<Double>.toJArr(): JSONArray = JSONArray().also { a -> forEach { a.put(it) } }
private fun JSONArray?.doubles(): List<Double> =
    if (this == null) emptyList() else (0 until length()).map { getDouble(it) }

fun AppState.toJson(): JSONObject = JSONObject().apply {
    put("v", 1)
    put("notifyHour", notifyHour)
    put("apts", JSONArray().also { arr -> apts.forEach { arr.put(it.toJson()) } })
}

private fun Apartment.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("name", name); put("address", address)
    put("winFrom", winFrom); put("winTo", winTo)
    put("sewerOn", sewerOn); put("sewerTariff", sewerTariff)
    put("fixed", JSONArray().also { a ->
        fixed.forEach { f -> a.put(JSONObject().put("id", f.id).put("name", f.name).put("amount", f.amount)) }
    })
    put("meters", JSONArray().also { a -> meters.forEach { a.put(it.toJson()) } })
    put("bills", JSONObject().also { o ->
        bills.forEach { (k, b) ->
            val bo = JSONObject()
            b.receipt?.let { bo.put("receipt", it) }
            bo.put("paid", b.paid)
            b.paidDate?.let { bo.put("paidDate", it) }
            o.put(k, bo)
        }
    })
}

private fun Meter.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("kind", kind.name); put("name", name); put("serial", serial)
    put("zones", zones); put("tariffs", tariffs.toJArr())
    verifyUntil?.let { put("verifyUntil", it) }
    put("readings", JSONArray().also { a ->
        readings.forEach { r ->
            a.put(
                JSONObject().put("id", r.id).put("month", r.month).put("date", r.date)
                    .put("values", r.values.toJArr()).put("tariffs", r.tariffs.toJArr())
            )
        }
    })
}

/*
 * Разбор JSON намеренно «недоверчивый»: он используется и для своего файла, и для
 * восстановления из резервной копии, которую пользователь может выбрать откуда угодно.
 * Поэтому все поля проверяются и приводятся к допустимым значениям, битые элементы
 * пропускаются, а размеры ограничены — так повреждённый файл не уронит приложение.
 */

private const val MAX_APTS = 50
private const val MAX_METERS = 40
private const val MAX_READINGS = 600
private const val MAX_FIXED = 40
private const val MAX_TEXT = 200

private fun JSONObject.str(name: String, max: Int = MAX_TEXT): String = optString(name, "").take(max)

private fun JSONObject.num(name: String): Double {
    val v = optDouble(name, 0.0)
    return if (v.isFinite() && v >= 0 && v < 1e12) v else 0.0
}

private fun JSONArray?.cleanDoubles(size: Int): List<Double> = List(size) { i ->
    val v = this?.optDouble(i, 0.0) ?: 0.0
    if (v.isFinite() && v >= 0 && v < 1e12) v else 0.0
}

/** Уникальный id: пустые и повторяющиеся заменяются новыми. */
private fun uniqueId(raw: String, used: MutableSet<String>): String {
    val id = if (raw.isNotBlank() && raw.length <= 64 && raw !in used) raw else newId()
    used += id
    return id
}

fun stateFromJson(o: JSONObject): AppState {
    val arr = o.optJSONArray("apts")
    val used = mutableSetOf<String>()
    val apts = mutableListOf<Apartment>()
    if (arr != null) {
        for (i in 0 until minOf(arr.length(), MAX_APTS)) {
            val a = arr.optJSONObject(i) ?: continue
            try { apts += aptFromJson(a, used) } catch (e: Exception) { /* пропускаем битую квартиру */ }
        }
    }
    return AppState(apts, o.optInt("notifyHour", 10).coerceIn(0, 23))
}

private fun aptFromJson(o: JSONObject, used: MutableSet<String>): Apartment {
    val bills = mutableMapOf<String, Bill>()
    o.optJSONObject("bills")?.let { b ->
        b.keys().forEach { k ->
            val x = b.optJSONObject(k)
            if (x != null && isYm(k)) {
                bills[k] = Bill(
                    receipt = if (x.has("receipt") && !x.isNull("receipt")) x.num("receipt") else null,
                    paid = x.num("paid"),
                    paidDate = parseIso(x.optString("paidDate"))?.toString()
                )
            }
        }
    }
    var from = o.optInt("winFrom", 15).coerceIn(1, 31)
    var to = o.optInt("winTo", 25).coerceIn(1, 31)
    if (to < from) { from = 15; to = 25 }

    val fixed = mutableListOf<Fixed>()
    o.optJSONArray("fixed")?.let { fx ->
        for (i in 0 until minOf(fx.length(), MAX_FIXED)) {
            val f = fx.optJSONObject(i) ?: continue
            fixed += Fixed(uniqueId(f.optString("id"), used), f.str("name"), f.num("amount"))
        }
    }
    val meters = mutableListOf<Meter>()
    o.optJSONArray("meters")?.let { ms ->
        for (i in 0 until minOf(ms.length(), MAX_METERS)) {
            val m = ms.optJSONObject(i) ?: continue
            try { meters += meterFromJson(m, used) } catch (e: Exception) { /* пропускаем битый счётчик */ }
        }
    }
    return Apartment(
        id = uniqueId(o.optString("id"), used),
        name = o.str("name").ifBlank { "Квартира" },
        address = o.str("address"),
        winFrom = from,
        winTo = to,
        sewerOn = o.optBoolean("sewerOn", false),
        sewerTariff = o.num("sewerTariff"),
        fixed = fixed,
        meters = meters,
        bills = bills
    )
}

private fun meterFromJson(o: JSONObject, used: MutableSet<String>): Meter {
    val kind = try { Kind.valueOf(o.optString("kind", "COLD")) } catch (e: IllegalArgumentException) { Kind.COLD }
    val zones = if (kind == Kind.ELEC) o.optInt("zones", 1).coerceIn(1, 3) else 1
    val byMonth = linkedMapOf<String, Reading>() // одно показание на месяц
    o.optJSONArray("readings")?.let { rs ->
        for (i in 0 until minOf(rs.length(), MAX_READINGS)) {
            val r = rs.optJSONObject(i) ?: continue
            val month = r.optString("month")
            if (!isYm(month)) continue
            byMonth[month] = Reading(
                uniqueId(r.optString("id"), used),
                month,
                parseIso(r.optString("date"))?.toString() ?: "$month-01",
                r.optJSONArray("values").cleanDoubles(zones),
                r.optJSONArray("tariffs").cleanDoubles(zones)
            )
        }
    }
    return Meter(
        id = uniqueId(o.optString("id"), used),
        kind = kind,
        name = o.str("name"),
        serial = o.str("serial", 40),
        zones = zones,
        tariffs = o.optJSONArray("tariffs").cleanDoubles(zones),
        verifyUntil = parseIso(o.optString("verifyUntil"))?.toString(),
        readings = byMonth.values.toList()
    )
}

/* ---------- Пример данных ---------- */

fun demoState(): AppState {
    val rnd = Random(7)
    val cur = ymNow()
    val months = (0 until 10).map { ymAdd(cur, (it - 10).toLong()) }
    val today = LocalDate.now()

    fun mk(kind: Kind, name: String, serial: String, zones: Int, tariffs: List<Double>,
           start: List<Double>, per: List<Double>, verify: String): Meter {
        var vals = start
        val rs = months.mapIndexed { i, ym ->
            if (i > 0) vals = vals.mapIndexed { z, v -> round3(v + per[z] * (0.8 + rnd.nextDouble() * 0.45)) }
            Reading(newId(), ym, "$ym-${20 + rnd.nextInt(5)}", vals, tariffs)
        }
        return Meter(newId(), kind, name, serial, zones, tariffs, verify, rs)
    }

    val a1 = Apartment(
        newId(), "Дом", "Москва, ул. Профсоюзная, 12, кв. 48", 15, 25, true, 48.1,
        listOf(Fixed(newId(), "Содержание жилья", 2380.0), Fixed(newId(), "Отопление", 3120.4), Fixed(newId(), "Вывоз ТКО", 415.0)),
        listOf(
            mk(Kind.COLD, "ХВС кухня", "2104771", 1, listOf(63.2), listOf(214.517), listOf(4.1), "2031-03-01"),
            mk(Kind.HOT, "ГВС кухня", "2104802", 1, listOf(318.4), listOf(132.906), listOf(2.6), today.plusDays(40).toString()),
            mk(Kind.ELEC, "Электричество", "013457829", 2, listOf(9.5, 3.4), listOf(18452.3, 7311.8), listOf(165.0, 72.0), "2034-08-15")
        ),
        emptyMap()
    )
    val a2 = Apartment(
        newId(), "Сдаю на Ленина", "Подольск, ул. Ленина, 7, кв. 15", 20, 25, true, 45.3,
        listOf(Fixed(newId(), "Содержание жилья", 1890.0), Fixed(newId(), "Отопление", 2410.0)),
        listOf(
            mk(Kind.COLD, "ХВС", "00871244", 1, listOf(55.7), listOf(389.12), listOf(5.2), "2029-10-01"),
            mk(Kind.HOT, "ГВС", "00871301", 1, listOf(287.9), listOf(201.444), listOf(3.1), "2029-10-01"),
            mk(Kind.ELEC, "Электричество", "22051877", 1, listOf(7.8), listOf(30418.6), listOf(240.0), today.minusDays(12).toString())
        ),
        emptyMap()
    )

    fun withBills(a: Apartment, second: Boolean): Apartment {
        val bills = mutableMapOf<String, Bill>()
        for (ym in months.drop(1)) {
            val t = a.calc(ym).total
            val receipt = if (!second && ym == ymAdd(cur, -3)) round2(t + 412.6) else t
            val unpaid = ym == ymAdd(cur, -1) || (second && ym == ymAdd(cur, -2))
            bills[ym] = if (unpaid) Bill(receipt) else
                Bill(receipt, receipt, YearMonth.parse(ym).plusMonths(1).atDay(if (second) 5 else 3).toString())
        }
        return a.copy(bills = bills)
    }
    return AppState(listOf(withBills(a1, false), withBills(a2, true)))
}
