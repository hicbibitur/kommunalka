@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.kommunalka.app

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/* ---------- Пустое состояние ---------- */

@Composable
fun Welcome(onAddApt: () -> Unit, onDemo: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Odometer(0.0, Kind.COLD, 26.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Добавьте первую квартиру",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Потом добавьте счётчики и тарифы. Приложение посчитает расход и сумму, распознает показания с камеры и напомнит о сроках.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddApt, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Добавить квартиру") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDemo, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Заполнить примером") }
    }
}

/* ---------- Обзор ---------- */

@Composable
fun OverviewScreen(state: AppState, onAlert: (Alert) -> Unit, onOpenApt: (String) -> Unit) {
    val today = LocalDate.now()
    val cur = ymNow()
    val prev = ymAdd(cur, -1)
    val names = state.apts.associate { it.id to it.name }
    val multi = state.apts.size > 1
    val all = state.apts.flatMap { it.alerts(today) }
    val urgent = all.filter { it.level == Level.BAD || it.level == Level.WARN }.sortedBy { it.level.ordinal }
    val info = all.filter { it.level == Level.INFO }
    val debt = state.apts.sumOf { a ->
        a.dataMonths().filter { it < cur }.sumOf { ym ->
            val b = a.billInfo(ym, today)
            if (b.level == Level.BAD || b.level == Level.WARN) b.rest else 0.0
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
        Muted("Сегодня ${fullDate(today)}")
        Text(
            if (debt > 0) "К оплате по всем квартирам: ${money(debt)}" else "Неоплаченных счетов нет",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
        )

        Section(
            title = "Что сделать",
            trailing = {
                Muted(if (urgent.isEmpty()) "всё в порядке" else "${urgent.size} ${plural(urgent.size, "задача", "задачи", "задач")}")
            }
        ) {
            if (urgent.isEmpty()) {
                AlertRow(Alert(Level.OK, "Срочных дел нет", "Напомним, когда откроется окно передачи показаний", "", Goal.READINGS), null) {}
            } else {
                urgent.forEach { al -> AlertRow(al, if (multi) names[al.aptId] else null) { onAlert(al) } }
            }
        }

        SubTitle("Квартиры")
        state.apts.forEach { a ->
            val done = a.meters.count { it.readingAt(cur) != null }
            val bi = a.billInfo(prev, today)
            Section(modifier = Modifier.clickable { onOpenApt(a.id) }, title = a.name) {
                Muted(a.address.ifBlank { "Адрес не указан" })
                KvRow("Показания за ${monthPrep(cur)}", if (a.meters.isEmpty()) "нет счётчиков" else "$done из ${a.meters.size}")
                KvRow("Оплата за ${monthPrep(prev)}", sub = if (bi.due > 0) money(bi.due) else null) {
                    StatusChip(bi.level, bi.status)
                }
            }
        }

        if (info.isNotEmpty()) {
            Section { info.forEach { al -> AlertRow(al, if (multi) names[al.aptId] else null) { onAlert(al) } } }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/* ---------- Показания ---------- */

@Composable
fun ReadingsScreen(
    apt: Apartment,
    onEnter: () -> Unit,
    onMeter: (String) -> Unit,
    onAddMeter: () -> Unit,
    onSendText: () -> Unit
) {
    val cur = ymNow()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (apt.meters.isEmpty()) {
            Section(title = "Счётчиков пока нет") {
                Text("Добавьте счётчики воды, электричества или газа — с номером, тарифом и датой поверки.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAddMeter) { Text("Добавить счётчик") }
            }
        } else {
            val done = apt.meters.count { it.readingAt(cur) != null }
            Button(onClick = onEnter, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Внести показания за ${monthPrep(cur)}")
            }
            Muted(
                "Внесено $done из ${apt.meters.size}. Окно передачи: ${apt.winFrom}–${apt.winTo} число.",
                Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
            )
            apt.meters.forEach { m -> MeterCard(m) { onMeter(m.id) } }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSendText, modifier = Modifier.weight(1f)) { Text("Отправить в УК") }
                OutlinedButton(onClick = onAddMeter, modifier = Modifier.weight(1f)) { Text("Новый счётчик") }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun MeterCard(m: Meter, onClick: () -> Unit) {
    val last = m.last()
    val c = last?.let { m.consumption(it.month) }
    Section(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindDot(m.kind)
            Spacer(Modifier.width(8.dp))
            Text(m.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Muted(last?.let { monthTitle(it.month) } ?: "нет данных")
        }
        Spacer(Modifier.height(8.dp))
        zoneNames(m.zones).forEachIndexed { i, z ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                if (m.zones > 1) Muted(z, Modifier.width(88.dp))
                Odometer(last?.values?.getOrNull(i), m.kind)
            }
        }
        Muted(
            when {
                c != null -> "Расход ${fmt(c.total)} ${m.kind.unit} · ${money(c.cost)}"
                last != null -> "Начальное показание — расход появится со следующего месяца"
                else -> "Внесите первое показание"
            },
            Modifier.padding(top = 6.dp)
        )
    }
}

/* ---------- Оплата ---------- */

@Composable
fun PayScreen(repo: Repo, apt: Apartment, month: String, onMonth: (String) -> Unit) {
    val ctx = LocalContext.current
    val bi = apt.billInfo(month)
    val c = bi.calc
    var receipt by remember(apt.id, month, bi.bill.receipt) {
        mutableStateOf(bi.bill.receipt?.let { numInput(it, 2) } ?: "")
    }
    var payDialog by remember { mutableStateOf(false) }
    val storedReceipt = bi.bill.receipt?.let { numInput(it, 2) } ?: ""

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(ymAdd(month, -1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
            }
            Text(
                monthTitle(month),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onMonth(ymAdd(month, 1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
            }
        }

        Section(title = "Начислено", trailing = { StatusChip(bi.level, bi.status) }) {
            if (!c.any && apt.fixed.isEmpty()) Muted("За этот месяц нет показаний. Внесите их во вкладке «Показания».")
            c.items.forEach { (m, x) ->
                val det = if (x.diffs.size > 1) {
                    x.diffs.mapIndexed { i, d ->
                        zoneNames(m.zones)[i].substringBefore(" ") + " " + fmt(d) + " × " + fmt(x.tariffs.getOrElse(i) { 0.0 }, 2)
                    }.joinToString("; ")
                } else "${fmt(x.total)} ${m.kind.unit} × ${fmt(x.tariffs.getOrElse(0) { 0.0 }, 2)} ₽"
                KvRow(m.label, money(x.cost), sub = det)
            }
            if (c.hasSewer) KvRow("Водоотведение", money(c.sewerCost), sub = "${fmt(c.sewerQty)} м³ × ${fmt(apt.sewerTariff, 2)} ₽")
            apt.fixed.forEach { f -> KvRow(f.name, money(f.amount), sub = "фиксированно") }
            KvRow("Итого по расчёту", money(c.total), bold = true)
            if (c.missing.isNotEmpty() && month <= ymNow()) {
                val (_, fg) = levelColors(Level.WARN)
                Text(
                    "Нет показаний за этот месяц: " + c.missing.joinToString(", ") { it.label },
                    color = fg,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Section(title = "Квитанция и оплата", trailing = { Muted("срок до ${dayMonth(dueDate(month))}") }) {
            NumField(
                value = receipt,
                onChange = { receipt = it },
                label = "Сумма по квитанции, ₽",
                mono = true,
                trailing = if (receipt != storedReceipt) {
                    {
                        IconButton(onClick = {
                            val v = parseNum(receipt)
                            if (v == null && receipt.isNotBlank()) {
                                Toast.makeText(ctx, "Сумма — число, например 5432,10", Toast.LENGTH_SHORT).show()
                            } else {
                                repo.updateApt(apt.id) { a ->
                                    val b = a.bills[month] ?: Bill()
                                    a.copy(bills = a.bills + (month to b.copy(receipt = v?.let { round2(it) })))
                                }
                                Toast.makeText(ctx, if (v == null) "Сумма квитанции очищена" else "Сумма квитанции сохранена", Toast.LENGTH_SHORT).show()
                            }
                        }) { Icon(Icons.Filled.Check, contentDescription = "Сохранить сумму") }
                    }
                } else null
            )
            val r = bi.bill.receipt
            if (r != null && c.any) {
                val diff = round2(r - c.total)
                val (lv, msg) = when {
                    kotlin.math.abs(diff) < 1 -> Level.OK to "Совпадает с расчётом"
                    diff > 0 -> Level.BAD to "В квитанции на ${money(diff)} больше расчёта. Проверьте, по каким показаниям начислили и не было ли начисления по нормативу."
                    else -> Level.WARN to "В квитанции на ${money(-diff)} меньше расчёта — возможно, учли не все показания или изменились тарифы."
                }
                Text(msg, color = levelColors(lv).second, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            } else {
                Muted("Сверим с расчётом и подсветим расхождение", Modifier.padding(top = 4.dp))
            }
            if (bi.paid > 0) {
                KvRow("Оплачено", money(bi.paid), sub = bi.bill.paidDate?.let { fullDate(it) })
                if (bi.rest > 0) KvRow("Осталось", money(bi.rest))
            }
            Spacer(Modifier.height(8.dp))
            if (bi.level == Level.OK) {
                OutlinedButton(onClick = {
                    repo.updateApt(apt.id) { a ->
                        val b = a.bills[month] ?: Bill()
                        a.copy(bills = a.bills + (month to b.copy(paid = 0.0, paidDate = null)))
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Отменить оплату") }
            } else {
                Button(onClick = { payDialog = true }, enabled = bi.due > 0, modifier = Modifier.fillMaxWidth()) { Text("Отметить оплату") }
            }
        }

        val months = apt.dataMonths().takeLast(12).reversed()
        if (months.isNotEmpty()) {
            Section(title = "История") {
                months.forEach { ym ->
                    val b = apt.billInfo(ym)
                    KvRow(monthTitle(ym), onClick = { onMonth(ym) }, sub = if (b.due > 0) money(b.due) else null) {
                        StatusChip(b.level, b.status)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (payDialog) {
        var amount by remember { mutableStateOf(numInput(if (bi.rest > 0) bi.rest else bi.due, 2)) }
        AlertDialog(
            onDismissRequest = { payDialog = false },
            title = { Text("Оплата за ${monthPrep(month)}") },
            text = {
                Column {
                    NumField(amount, { amount = it }, "Сумма оплаты, ₽", mono = true)
                    Muted("Дата оплаты — сегодня, ${fullDate(LocalDate.now())}")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = parseNum(amount)
                    if (v != null && v > 0) {
                        repo.updateApt(apt.id) { a ->
                            val b = a.bills[month] ?: Bill()
                            a.copy(bills = a.bills + (month to b.copy(paid = round2(b.paid + v), paidDate = LocalDate.now().toString())))
                        }
                        payDialog = false
                    } else {
                        Toast.makeText(ctx, "Укажите сумму больше нуля", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Отметить оплату") }
            },
            dismissButton = { TextButton(onClick = { payDialog = false }) { Text("Отмена") } }
        )
    }
}

/* ---------- Объекты ---------- */

@Composable
fun ObjectsScreen(
    repo: Repo,
    state: AppState,
    onEditApt: (String) -> Unit,
    onAddApt: () -> Unit,
    onEditMeter: (String, String) -> Unit,
    onAddMeter: (String) -> Unit,
    onDemo: () -> Unit,
    onWipe: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<AppState?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try { repo.exportTo(ctx, uri); true } catch (e: Exception) { false }
                }
                Toast.makeText(ctx, if (ok) "Резервная копия сохранена" else "Не удалось сохранить файл", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    try { repo.readBackup(ctx, uri) } catch (e: Exception) { null }
                }
                when {
                    result == null -> Toast.makeText(ctx, "Файл не похож на резервную копию «Коммуналки»", Toast.LENGTH_LONG).show()
                    result.apts.isEmpty() -> Toast.makeText(ctx, "В файле нет ни одной квартиры", Toast.LENGTH_LONG).show()
                    else -> pendingImport = result
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
        state.apts.forEach { a ->
            Section(title = a.name, trailing = { TextButton(onClick = { onEditApt(a.id) }) { Text("Изменить") } }) {
                Muted(a.address.ifBlank { "Адрес не указан" })
                a.meters.forEach { m ->
                    val sub = listOfNotNull(
                        if (m.serial.isNotBlank()) "№ ${m.serial}" else null,
                        if (m.zones > 1) "${m.zones}-тарифный" else null,
                        m.verifyUntil?.let { "поверка до ${fullDate(it)}" } ?: "дата поверки не указана"
                    ).joinToString(" · ")
                    KvRow(m.label, m.tariffs.joinToString(" / ") { fmt(it, 2) } + " ₽", sub = sub, onClick = { onEditMeter(a.id, m.id) })
                }
                TextButton(onClick = { onAddMeter(a.id) }) { Text("+ Счётчик") }
            }
        }
        Button(onClick = onAddApt, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Добавить квартиру") }

        SubTitle("Данные")
        Section {
            KvRow("Резервная копия", sub = "Сохранить все данные в файл — например, на Google Диск") {
                OutlinedButton(onClick = { exportLauncher.launch("kommunalka-${LocalDate.now()}.json") }) { Text("Сохранить") }
            }
            KvRow("Восстановить из копии", sub = "Заменит текущие данные содержимым файла") {
                OutlinedButton(onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }) { Text("Открыть") }
            }
            KvRow("Заполнить примером", sub = "Две квартиры с историей — заменит текущие данные") {
                OutlinedButton(onClick = onDemo) { Text("Пример") }
            }
            KvRow("Удалить все данные", sub = "Квартиры, счётчики, показания и оплаты") {
                OutlinedButton(
                    onClick = onWipe,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            }
        }
        Muted(
            "Данные хранятся только на этом телефоне. Время от времени сохраняйте резервную копию.",
            Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp)
        )
    }

    pendingImport?.let { imported ->
        val meters = imported.apts.sumOf { it.meters.size }
        ConfirmDialog(
            "Восстановить из копии: квартир — ${imported.apts.size}, счётчиков — $meters? Текущие данные будут заменены.",
            "Восстановить",
            onConfirm = {
                repo.replaceAll(imported)
                pendingImport = null
                Toast.makeText(ctx, "Данные восстановлены", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { pendingImport = null }
        )
    }
}
