@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.kommunalka.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate
import java.time.format.DateTimeParseException

/* ---------- Квартира ---------- */

private data class FixedDraft(val name: String, val amount: String)

@Composable
fun AptEditScreen(repo: Repo, aptId: String?, onClose: () -> Unit, onSaved: (String, Boolean) -> Unit) {
    val existing = remember(aptId) { repo.state.value.apts.firstOrNull { it.id == aptId } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var from by remember { mutableStateOf((existing?.winFrom ?: 15).toString()) }
    var to by remember { mutableStateOf((existing?.winTo ?: 25).toString()) }
    var sewerOn by remember { mutableStateOf(existing?.sewerOn ?: true) }
    var sewerT by remember { mutableStateOf(existing?.sewerTariff?.takeIf { it > 0 }?.let { numInput(it, 2) } ?: "") }
    val fixed = remember {
        mutableStateListOf<FixedDraft>().apply {
            val src = existing?.fixed?.map { FixedDraft(it.name, numInput(it.amount, 2)) }
            addAll(if (src.isNullOrEmpty()) listOf(FixedDraft("Содержание жилья", "")) else src)
        }
    }
    var err by remember { mutableStateOf<String?>(null) }
    var confirmDel by remember { mutableStateOf(false) }

    OverlayScaffold(if (existing == null) "Новая квартира" else "Квартира", onClose) {
        TextInput(name, { name = it; err = null }, "Название", placeholder = "Например, Дом или Сдаю на Ленина")
        Spacer(Modifier.height(8.dp))
        TextInput(address, { address = it }, "Адрес", placeholder = "Город, улица, дом, квартира")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField(from, { from = it }, "Показания с числа", Modifier.weight(1f))
            NumField(to, { to = it }, "по число", Modifier.weight(1f))
        }
        Muted("Окно, когда управляющая компания принимает показания. В эти дни придут напоминания.")

        SubTitle("Водоотведение")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Считать по сумме ХВС и ГВС", modifier = Modifier.weight(1f))
            Switch(checked = sewerOn, onCheckedChange = { sewerOn = it })
        }
        if (sewerOn) NumField(sewerT, { sewerT = it }, "Тариф, ₽ за м³")

        SubTitle("Фиксированные начисления в месяц")
        Muted("Содержание жилья, отопление без счётчика, вывоз мусора, домофон")
        for (i in fixed.indices) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                OutlinedTextField(
                    value = fixed[i].name, onValueChange = { v -> if (v.length <= 80) fixed[i] = fixed[i].copy(name = v) },
                    label = { Text("Услуга") }, singleLine = true, modifier = Modifier.weight(1.4f)
                )
                Spacer(Modifier.width(8.dp))
                NumField(fixed[i].amount, { v -> fixed[i] = fixed[i].copy(amount = v) }, "₽", Modifier.weight(1f))
                IconButton(onClick = { fixed.removeAt(i) }) { Icon(Icons.Filled.Close, contentDescription = "Удалить строку") }
            }
        }
        TextButton(onClick = { fixed.add(FixedDraft("", "")) }) { Text("+ Добавить строку") }

        err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 6.dp)) }
        Button(onClick = {
            val f = from.trim().toIntOrNull() ?: -1
            val t = to.trim().toIntOrNull() ?: -1
            val badFixed = fixed.any { d ->
                (d.name.isNotBlank() || d.amount.isNotBlank()) && (d.name.isBlank() || parseNum(d.amount) == null)
            }
            val sewerBad = sewerOn && sewerT.isNotBlank() && parseNum(sewerT) == null
            when {
                name.isBlank() -> { err = "Укажите название квартиры" }
                f !in 1..31 || t !in 1..31 || t < f -> { err = "Проверьте окно передачи: числа от 1 до 31, «с» не позже «по»" }
                sewerBad -> { err = "Тариф водоотведения — число, например 48,10" }
                badFixed -> { err = "В фиксированных начислениях укажите и услугу, и сумму числом — или удалите строку" }
                else -> {
                    val fx = fixed.mapNotNull { d ->
                        val amt = parseNum(d.amount)
                        if (d.name.isNotBlank() && amt != null) Fixed(newId(), d.name.trim(), round2(amt)) else null
                    }
                    val st = parseNum(sewerT) ?: 0.0
                    if (existing != null) {
                        repo.updateApt(existing.id) {
                            it.copy(name = name.trim(), address = address.trim(), winFrom = f, winTo = t,
                                sewerOn = sewerOn, sewerTariff = st, fixed = fx)
                        }
                        onSaved(existing.id, false)
                    } else {
                        val a = Apartment(newId(), name.trim(), address.trim(), f, t, sewerOn, st, fx, emptyList(), emptyMap())
                        repo.update { it.copy(apts = it.apts + a) }
                        onSaved(a.id, true)
                    }
                }
            }
        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp)) {
            Text(if (existing == null) "Добавить квартиру" else "Сохранить")
        }
        if (existing != null) {
            OutlinedButton(
                onClick = { confirmDel = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Удалить квартиру") }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmDel && existing != null) {
        ConfirmDialog("Удалить «${existing.name}» со всеми счётчиками и оплатами?", "Удалить", onConfirm = {
            repo.update { s -> s.copy(apts = s.apts.filter { it.id != existing.id }) }
            confirmDel = false
            onClose()
        }, onDismiss = { confirmDel = false })
    }
}

/* ---------- Счётчик ---------- */

@Composable
fun MeterEditScreen(repo: Repo, aptId: String, meterId: String?, onClose: () -> Unit) {
    val existing = remember(meterId) {
        repo.state.value.apts.firstOrNull { it.id == aptId }?.meters?.firstOrNull { it.id == meterId }
    }
    var kind by remember { mutableStateOf(existing?.kind ?: Kind.COLD) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var serial by remember { mutableStateOf(existing?.serial ?: "") }
    var zones by remember { mutableStateOf(existing?.zones ?: 1) }
    val tariffs = remember {
        mutableStateListOf("", "", "").apply { existing?.tariffs?.forEachIndexed { i, v -> if (i < 3) this[i] = numInput(v, 2) } }
    }
    val starts = remember { mutableStateListOf("", "", "") }
    var verify by remember { mutableStateOf(parseIso(existing?.verifyUntil)?.format(DMY) ?: "") }
    var err by remember { mutableStateOf<String?>(null) }
    var confirmDel by remember { mutableStateOf(false) }
    val z = if (kind == Kind.ELEC) zones else 1

    OverlayScaffold(if (existing == null) "Новый счётчик" else "Счётчик", onClose) {
        Muted("Ресурс")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kind.entries.forEach { k ->
                FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.title) })
            }
        }
        Spacer(Modifier.height(8.dp))
        TextInput(name, { name = it }, "Название", placeholder = "Например, ХВС кухня")
        Spacer(Modifier.height(8.dp))
        TextInput(serial, { serial = it }, "Заводской номер", placeholder = "Указан на корпусе", maxLength = 40)
        if (kind == Kind.ELEC) {
            SubTitle("Тарифность")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "Однотарифный", 2 to "День / ночь", 3 to "Три зоны").forEach { (n, t) ->
                    FilterChip(selected = zones == n, onClick = { zones = n }, label = { Text(t) })
                }
            }
        }
        SubTitle("Тариф")
        for (i in 0 until z) {
            NumField(tariffs[i], { tariffs[i] = it }, (if (z > 1) zoneNames(z)[i] + ", " else "") + "₽ за ${kind.unit}")
            Spacer(Modifier.height(6.dp))
        }
        Muted("Тариф указан в квитанции. При вводе показаний он запоминается, поэтому смена тарифа не меняет прошлые расчёты.")
        Spacer(Modifier.height(8.dp))
        TextInput(verify, { verify = it }, "Поверка действительна до", placeholder = "ДД.ММ.ГГГГ")
        if (existing == null) {
            SubTitle("Текущее показание (необязательно)")
            for (i in 0 until z) {
                NumField(starts[i], { starts[i] = it }, (if (z > 1) zoneNames(z)[i] + ", " else "") + kind.unit, mono = true)
                Spacer(Modifier.height(6.dp))
            }
            Muted("Станет начальным, и расход посчитается уже со следующего ввода.")
        }

        err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 6.dp)) }
        Button(onClick = {
            val tar = (0 until z).map { parseNum(tariffs[it]) }
            val ver: String? = if (verify.isBlank()) null else try {
                LocalDate.parse(verify.trim(), DMY).toString()
            } catch (e: DateTimeParseException) {
                "bad"
            }
            val startTexts = (0 until z).map { starts[it] }
            val startsBad = existing == null && startTexts.any { it.isNotBlank() } &&
                startTexts.any { parseNum(it) == null }
            when {
                tar.any { it == null } -> { err = "Укажите тариф числом — его можно найти в квитанции" }
                startsBad -> { err = "Текущее показание — число; для многотарифного счётчика заполните все зоны" }
                ver == "bad" -> { err = "Дата поверки в формате ДД.ММ.ГГГГ, например 15.03.2031" }
                existing != null && existing.zones != z && existing.readings.isNotEmpty() -> {
                    err = "Нельзя сменить тарифность счётчика с показаниями — добавьте новый счётчик"
                }
                else -> {
                    val tl = tar.map { it ?: 0.0 }
                    if (existing != null) {
                        repo.updateMeter(aptId, existing.id) {
                            it.copy(kind = kind, name = name.trim(), serial = serial.trim(), zones = z, tariffs = tl, verifyUntil = ver)
                        }
                    } else {
                        val sv = (0 until z).map { parseNum(starts[it]) }
                        val readings = if (sv.all { it != null }) {
                            listOf(Reading(newId(), ymAdd(ymNow(), -1), LocalDate.now().toString(), sv.map { it ?: 0.0 }, tl))
                        } else emptyList()
                        val m = Meter(newId(), kind, name.trim(), serial.trim(), z, tl, ver, readings)
                        repo.updateApt(aptId) { it.copy(meters = it.meters + m) }
                    }
                    onClose()
                }
            }
        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp)) {
            Text(if (existing == null) "Добавить счётчик" else "Сохранить")
        }
        if (existing != null) {
            OutlinedButton(
                onClick = { confirmDel = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Удалить счётчик") }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmDel && existing != null) {
        ConfirmDialog("Удалить счётчик «${existing.label}» и всю его историю?", "Удалить", onConfirm = {
            repo.updateApt(aptId) { a -> a.copy(meters = a.meters.filter { it.id != existing.id }) }
            confirmDel = false
            onClose()
        }, onDismiss = { confirmDel = false })
    }
}

/* ---------- Ввод показаний ---------- */

@Composable
fun EnterScreen(repo: Repo, aptId: String, onlyMeter: String?, onClose: () -> Unit) {
    val state by repo.state.collectAsState()
    val apt = state.apts.firstOrNull { it.id == aptId }
    if (apt == null) {
        LaunchedEffect(Unit) { onClose() }
    } else {
        EnterContent(repo, apt, onlyMeter, onClose)
    }
}

@Composable
private fun EnterContent(repo: Repo, apt: Apartment, onlyMeter: String?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var month by rememberSaveable { mutableStateOf(ymNow()) }
    val meters = if (onlyMeter != null) apt.meters.filter { it.id == onlyMeter } else apt.meters
    val inputs = remember { mutableStateMapOf<String, String>() }
    var scan by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(month) {
        inputs.clear()
        meters.forEach { m ->
            val r = m.readingAt(month)
            for (z in 0 until m.zones) {
                inputs["${m.id}:$z"] = r?.values?.getOrNull(z)?.let { numInput(it, m.kind.frac) } ?: ""
            }
        }
    }

    val sc = scan
    val scMeter = sc?.let { s -> meters.firstOrNull { it.id == s.first } }
    if (sc != null && scMeter != null) {
        BackHandler { scan = null }
        val zoneTitle = if (scMeter.zones > 1) " — " + zoneNames(scMeter.zones)[sc.second] else ""
        ScannerScreen(
            kind = scMeter.kind,
            title = scMeter.label + zoneTitle,
            prev = scMeter.prev(month)?.values?.getOrNull(sc.second),
            avg = if (scMeter.zones == 1) scMeter.avgConsumption(month) else null,
            onResult = { v ->
                inputs["${scMeter.id}:${sc.second}"] = numInput(v, scMeter.kind.frac)
                scan = null
            },
            onClose = { scan = null }
        )
    } else {
        OverlayScaffold(if (onlyMeter != null && meters.isNotEmpty()) meters[0].label else "Показания", onClose) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = ymAdd(month, -1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Muted("Показания за")
                    Text(monthTitle(month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = { month = ymAdd(month, 1) }, enabled = month < ymNow()) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
                }
            }
            meters.forEach { m ->
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KindDot(m.kind)
                        Spacer(Modifier.width(8.dp))
                        Text(m.label, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    for (z in 0 until m.zones) {
                        val key = "${m.id}:$z"
                        val txt = inputs[key] ?: ""
                        val chk = checkValue(m, z, month, txt)
                        val color = if (chk.level == null) MaterialTheme.colorScheme.onSurfaceVariant else levelColors(chk.level).second
                        NumField(
                            value = txt,
                            onChange = { inputs[key] = it; err = null },
                            label = (if (m.zones > 1) zoneNames(m.zones)[z] + ", " else "") + m.kind.unit,
                            mono = true,
                            isError = !chk.ok,
                            supporting = chk.msg,
                            supportingColor = color,
                            trailing = { TextButton(onClick = { scan = m.id to z }) { Text("Камера") } }
                        )
                    }
                }
            }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 6.dp)) }
            Button(onClick = {
                val vals = mutableMapOf<String, List<Double>>()
                var bad: String? = null
                for (m in meters) {
                    val texts = (0 until m.zones).map { inputs["${m.id}:$it"] ?: "" }
                    if (texts.all { it.isBlank() }) continue
                    if ((0 until m.zones).any { !checkValue(m, it, month, texts[it]).ok }) {
                        bad = "Исправьте показания, отмеченные красным"; break
                    }
                    val nums = texts.map { parseNum(it) }
                    if (nums.any { it == null }) {
                        bad = "Для многотарифного счётчика заполните все зоны"; break
                    }
                    vals[m.id] = nums.map { it ?: 0.0 }
                }
                when {
                    bad != null -> { err = bad }
                    vals.isEmpty() -> { err = "Введите хотя бы одно показание" }
                    else -> {
                        val today = LocalDate.now().toString()
                        repo.updateApt(apt.id) { a ->
                            a.copy(meters = a.meters.map { m ->
                                val v = vals[m.id]
                                if (v == null) m else {
                                    val ex = m.readingAt(month)
                                    val r = Reading(ex?.id ?: newId(), month, today, v, m.tariffs)
                                    m.copy(readings = m.readings.filter { it.month != month } + r)
                                }
                            })
                        }
                        Toast.makeText(ctx, "Показания сохранены: ${vals.size}", Toast.LENGTH_SHORT).show()
                        onClose()
                    }
                }
            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp)) { Text("Сохранить показания") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ---------- Карточка счётчика ---------- */

@Composable
fun MeterViewScreen(repo: Repo, aptId: String, meterId: String, onClose: () -> Unit, onEnter: () -> Unit, onEdit: () -> Unit) {
    val state by repo.state.collectAsState()
    val m = state.apts.firstOrNull { it.id == aptId }?.meters?.firstOrNull { it.id == meterId }
    if (m == null) {
        LaunchedEffect(Unit) { onClose() }
    } else {
        MeterViewContent(repo, aptId, m, onClose, onEnter, onEdit)
    }
}

@Composable
private fun MeterViewContent(repo: Repo, aptId: String, m: Meter, onClose: () -> Unit, onEnter: () -> Unit, onEdit: () -> Unit) {
    val meterId = m.id
    var toDelete by remember { mutableStateOf<Reading?>(null) }
    val last = m.last()
    OverlayScaffold(m.label, onClose) {
        zoneNames(m.zones).forEachIndexed { i, z ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                if (m.zones > 1) Muted(z, Modifier.width(92.dp))
                Odometer(last?.values?.getOrNull(i), m.kind, 26.sp)
            }
        }
        val info = listOfNotNull(
            if (m.serial.isNotBlank()) "№ ${m.serial}" else null,
            "тариф " + m.tariffs.joinToString(" / ") { fmt(it, 2) } + " ₽ за ${m.kind.unit}",
            m.verifyUntil?.let { "поверка до ${fullDate(it)}" }
        ).joinToString(" · ")
        Muted(info, Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEnter, modifier = Modifier.weight(1f)) { Text("Внести показание") }
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Изменить") }
        }
        if (m.readings.size > 1) {
            SubTitle("Расход за 12 месяцев, ${m.kind.unit}")
            Section { ConsumptionChart(m) }
        }
        SubTitle("История")
        Section {
            val rs = m.ordered().reversed()
            if (rs.isEmpty()) Muted("Показаний пока нет.")
            rs.forEach { r ->
                val c = m.consumption(r.month)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(monthTitle(r.month))
                        val taken = parseIso(r.date)?.let { "снято " + dayMonth(it) } ?: ""
                        Muted(r.values.joinToString(" / ") { fmt(it, m.kind.frac) } + if (taken.isNotEmpty()) " · $taken" else "")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (c != null) "${fmt(c.total)} ${m.kind.unit}" else "начальное")
                        if (c != null) Muted(money(c.cost))
                    }
                    IconButton(onClick = { toDelete = r }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить показание за ${monthTitle(r.month)}")
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    toDelete?.let { r ->
        ConfirmDialog("Удалить показание за ${monthTitle(r.month)}?", "Удалить", onConfirm = {
            repo.updateMeter(aptId, meterId) { mm -> mm.copy(readings = mm.readings.filter { it.id != r.id }) }
            toDelete = null
        }, onDismiss = { toDelete = null })
    }
}

@Composable
fun ConsumptionChart(m: Meter) {
    val end = m.last()?.month ?: ymNow()
    val months = (11 downTo 0).map { ymAdd(end, -it.toLong()) }
    val vals = months.map { m.consumption(it)?.total }
    val max = (vals.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(1e-9)
    val color = kindColor(m.kind)
    Row(Modifier.fillMaxWidth().height(150.dp), verticalAlignment = Alignment.Bottom) {
        months.forEachIndexed { i, ym ->
            val v = vals[i]
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (v != null && (i == 11 || v == max)) {
                    Text(fmt(v, 1), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                val frac = if (v == null) 0f else (0.72 * (v / max)).toFloat().coerceIn(0.02f, 0.72f)
                Box(
                    Modifier
                        .fillMaxWidth(0.62f)
                        .fillMaxHeight(frac)
                        .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                )
                Text(monthShort(ym), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/* ---------- Текст для управляющей компании ---------- */

@Composable
fun SendTextScreen(repo: Repo, aptId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val apt = remember(aptId) { repo.state.value.apts.firstOrNull { it.id == aptId } }
    val text = apt?.sendText() ?: ""
    OverlayScaffold("Отправить в УК", onClose) {
        Muted("Для письма, мессенджера или формы на сайте управляющей компании.")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = text, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            try {
                ctx.startActivity(Intent.createChooser(send, "Отправить показания"))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(ctx, "Нет приложения, чтобы отправить текст. Скопируйте его.", Toast.LENGTH_LONG).show()
            }
        }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp)) { Text("Поделиться") }
        OutlinedButton(onClick = {
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Скопировать") }
    }
}

/* ---------- Напоминания ---------- */

@Composable
fun NotifyScreen(repo: Repo, onClose: () -> Unit) {
    val state by repo.state.collectAsState()
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(Reminders.canNotify(ctx)) }
    var askedHere by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = Reminders.canNotify(ctx)
        askedHere = true
    }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = Reminders.canNotify(ctx) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val pending = Reminders.pending(state, LocalDate.now())

    OverlayScaffold("Напоминания", onClose) {
        Section {
            Text(if (granted) "Уведомления включены" else "Уведомления выключены", fontWeight = FontWeight.SemiBold)
            Muted("Раз в день приложение проверяет, открыто ли окно передачи показаний, не подходит ли срок оплаты (за 5 дней) и не истекает ли поверка счётчиков.")
            if (!granted) {
                Button(onClick = {
                    val needAsk = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (needAsk && !askedHere) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openAppSettings(ctx, notifications = true)
                    }
                }, modifier = Modifier.padding(top = 8.dp)) { Text("Включить уведомления") }
            }
        }
        Section(title = "Время проверки") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(8, 9, 10, 12, 18, 20).forEach { h ->
                    FilterChip(
                        selected = state.notifyHour == h,
                        onClick = {
                            repo.update { it.copy(notifyHour = h) }
                            Reminders.schedule(ctx, h, replace = true)
                        },
                        label = { Text("$h:00") }
                    )
                }
            }
            Muted("Android может сдвинуть время на несколько минут, чтобы беречь батарею.", Modifier.padding(top = 4.dp))
        }
        Section(title = "Сейчас") {
            if (pending.isEmpty()) Muted("Напоминать не о чем.")
            pending.forEach { (a, al) -> AlertRow(al, if (state.apts.size > 1) a.name else null) {} }
            OutlinedButton(onClick = {
                val ok = Reminders.check(ctx, test = true)
                Toast.makeText(ctx, if (ok) "Уведомление отправлено" else "Сначала включите уведомления", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Прислать уведомление сейчас") }
        }
        Spacer(Modifier.height(24.dp))
    }
}
