@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.kommunalka.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repo.get(this)
        Reminders.createChannel(this)
        Reminders.schedule(this, repo.state.value.notifyHour, replace = false)
        setContent {
            KomTheme { App(repo) }
        }
    }
}

enum class Tab(val title: String) { OVERVIEW("Обзор"), READINGS("Показания"), PAY("Оплата"), OBJECTS("Квартиры") }

private fun tabIcon(t: Tab): ImageVector = when (t) {
    Tab.OVERVIEW -> Icons.Filled.Home
    Tab.READINGS -> Icons.Filled.Edit
    Tab.PAY -> Icons.Filled.DateRange
    Tab.OBJECTS -> Icons.Filled.Settings
}

sealed interface Overlay {
    data class AptEdit(val aptId: String?) : Overlay
    data class MeterEdit(val aptId: String, val meterId: String?) : Overlay
    data class Enter(val aptId: String, val meterId: String?) : Overlay
    data class MeterView(val aptId: String, val meterId: String) : Overlay
    data class SendText(val aptId: String) : Overlay
    data object Notify : Overlay
}

/** Сохраняет открытый экран при смене темы или пересоздании процесса системой. */
private val OverlaySaver: Saver<Overlay?, ArrayList<String>> = Saver(
    save = { o ->
        when (o) {
            null -> null
            is Overlay.AptEdit -> arrayListOf("apt", o.aptId ?: "")
            is Overlay.MeterEdit -> arrayListOf("meterEdit", o.aptId, o.meterId ?: "")
            is Overlay.Enter -> arrayListOf("enter", o.aptId, o.meterId ?: "")
            is Overlay.MeterView -> arrayListOf("meterView", o.aptId, o.meterId)
            is Overlay.SendText -> arrayListOf("send", o.aptId)
            Overlay.Notify -> arrayListOf("notify")
        }
    },
    restore = { v ->
        val a = v.getOrNull(1).orEmpty()
        val b = v.getOrNull(2).orEmpty()
        when (v.getOrNull(0)) {
            "apt" -> Overlay.AptEdit(a.ifBlank { null })
            "meterEdit" -> Overlay.MeterEdit(a, b.ifBlank { null })
            "enter" -> Overlay.Enter(a, b.ifBlank { null })
            "meterView" -> Overlay.MeterView(a, b)
            "send" -> Overlay.SendText(a)
            "notify" -> Overlay.Notify
            else -> null
        }
    }
)

@Composable
fun App(repo: Repo) {
    val state by repo.state.collectAsState()
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    var aptId by rememberSaveable { mutableStateOf<String?>(null) }
    var payMonth by rememberSaveable { mutableStateOf(ymAdd(ymNow(), -1)) }
    var overlay by rememberSaveable(stateSaver = OverlaySaver) { mutableStateOf<Overlay?>(null) }
    val saveOk by repo.saveOk.collectAsState()
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmDemo by remember { mutableStateOf(false) }
    val apt = state.apts.firstOrNull { it.id == aptId } ?: state.apts.firstOrNull()

    // Один раз спрашиваем разрешение на уведомления — когда появилась первая квартира
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val hasApts = state.apts.isNotEmpty()
    LaunchedEffect(hasApts) {
        if (hasApts && Build.VERSION.SDK_INT >= 33) {
            val prefs = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted && !prefs.getBoolean("askedNotif", false)) {
                prefs.edit().putBoolean("askedNotif", true).apply()
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val onAlert: (Alert) -> Unit = { al ->
        aptId = al.aptId
        when (al.goal) {
            Goal.PAY -> {
                tab = Tab.PAY
                al.month?.let { payMonth = it }
            }
            Goal.METER -> {
                tab = Tab.READINGS
                al.meterId?.let { overlay = Overlay.MeterView(al.aptId, it) }
            }
            Goal.READINGS -> { tab = Tab.READINGS }
        }
    }

    val current = overlay
    if (current != null) {
        BackHandler { overlay = null }
        val close: () -> Unit = { overlay = null }
        when (current) {
            is Overlay.AptEdit -> AptEditScreen(repo, current.aptId, close) { id, isNew ->
                aptId = id
                if (isNew) {
                    tab = Tab.READINGS
                    overlay = Overlay.MeterEdit(id, null)
                } else {
                    overlay = null
                }
            }
            is Overlay.MeterEdit -> MeterEditScreen(repo, current.aptId, current.meterId, close)
            is Overlay.Enter -> EnterScreen(repo, current.aptId, current.meterId, close)
            is Overlay.MeterView -> MeterViewScreen(
                repo, current.aptId, current.meterId, close,
                onEnter = { overlay = Overlay.Enter(current.aptId, current.meterId) },
                onEdit = { overlay = Overlay.MeterEdit(current.aptId, current.meterId) }
            )
            is Overlay.SendText -> SendTextScreen(repo, current.aptId, close)
            Overlay.Notify -> NotifyScreen(repo, close)
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Коммуналка", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { overlay = Overlay.Notify }) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Напоминания")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                if (state.apts.isNotEmpty()) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(tabIcon(t), contentDescription = null) },
                                label = { Text(t.title) }
                            )
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                if (!saveOk) {
                    Text(
                        "Не удалось сохранить изменения на телефон — возможно, закончилась память. Освободите место.",
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                if (apt == null) {
                    Welcome(onAddApt = { overlay = Overlay.AptEdit(null) }, onDemo = { repo.update { demoState() } })
                } else {
                    if (tab == Tab.READINGS || tab == Tab.PAY) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.apts.forEach { a ->
                                FilterChip(selected = a.id == apt.id, onClick = { aptId = a.id }, label = { Text(a.name) })
                            }
                            AssistChip(onClick = { overlay = Overlay.AptEdit(null) }, label = { Text("+ Квартира") })
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            Tab.OVERVIEW -> OverviewScreen(state, onAlert = onAlert, onOpenApt = { id ->
                                aptId = id
                                tab = Tab.READINGS
                            })
                            Tab.READINGS -> ReadingsScreen(
                                apt,
                                onEnter = { overlay = Overlay.Enter(apt.id, null) },
                                onMeter = { id -> overlay = Overlay.MeterView(apt.id, id) },
                                onAddMeter = { overlay = Overlay.MeterEdit(apt.id, null) },
                                onSendText = { overlay = Overlay.SendText(apt.id) }
                            )
                            Tab.PAY -> PayScreen(repo, apt, payMonth, onMonth = { payMonth = it })
                            Tab.OBJECTS -> ObjectsScreen(
                                repo,
                                state,
                                onEditApt = { id -> overlay = Overlay.AptEdit(id) },
                                onAddApt = { overlay = Overlay.AptEdit(null) },
                                onEditMeter = { a, m -> overlay = Overlay.MeterEdit(a, m) },
                                onAddMeter = { a -> overlay = Overlay.MeterEdit(a, null) },
                                onDemo = { confirmDemo = true },
                                onWipe = { confirmWipe = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmWipe) {
        ConfirmDialog("Удалить все квартиры, счётчики, показания и оплаты? Это нельзя отменить.", "Удалить всё", onConfirm = {
            repo.update { AppState(notifyHour = it.notifyHour) }
            aptId = null
            tab = Tab.OVERVIEW
            confirmWipe = false
        }, onDismiss = { confirmWipe = false })
    }
    if (confirmDemo) {
        ConfirmDialog("Заменить текущие данные примером? Ваши квартиры и показания будут удалены.", "Заменить", onConfirm = {
            repo.update { demoState().copy(notifyHour = it.notifyHour) }
            aptId = null
            tab = Tab.OVERVIEW
            confirmDemo = false
        }, onDismiss = { confirmDemo = false })
    }
}
