@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.kommunalka.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/* ---------- Тема ---------- */

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F4F86), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7F5), onPrimaryContainer = Color(0xFF0E2A4A),
    secondaryContainer = Color(0xFFDCE7F5), onSecondaryContainer = Color(0xFF0E2A4A),
    background = Color(0xFFEDF0F3), onBackground = Color(0xFF18212C),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF18212C),
    surfaceVariant = Color(0xFFF1F4F7), onSurfaceVariant = Color(0xFF5B6775),
    outline = Color(0xFFAEB8C3), outlineVariant = Color(0xFFDDE2E8),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FB0EC), onPrimary = Color(0xFF0E1620),
    primaryContainer = Color(0xFF203750), onPrimaryContainer = Color(0xFFDCE7F5),
    secondaryContainer = Color(0xFF203750), onSecondaryContainer = Color(0xFFDCE7F5),
    background = Color(0xFF10151B), onBackground = Color(0xFFE6EAEE),
    surface = Color(0xFF1A2129), onSurface = Color(0xFFE6EAEE),
    surfaceVariant = Color(0xFF222B35), onSurfaceVariant = Color(0xFF97A3B0),
    outline = Color(0xFF4A5663), outlineVariant = Color(0xFF2B3540),
    error = Color(0xFFF27A70)
)

@Composable
fun KomTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

@Composable
fun kindColor(kind: Kind): Color {
    val dark = isSystemInDarkTheme()
    return when (kind) {
        Kind.COLD -> if (dark) Color(0xFF6AA3E8) else Color(0xFF2563A8)
        Kind.HOT -> if (dark) Color(0xFFF07463) else Color(0xFFC23A2B)
        Kind.ELEC -> if (dark) Color(0xFFE6B53A) else Color(0xFFA87400)
        Kind.GAS -> if (dark) Color(0xFFA796F0) else Color(0xFF6A4FC9)
        Kind.HEAT -> if (dark) Color(0xFFF0995A) else Color(0xFFC2621B)
    }
}

/** Пара (фон, текст) для статуса. */
@Composable
fun levelColors(level: Level): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    return when (level) {
        Level.OK -> if (dark) Color(0xFF173326) to Color(0xFF5CC48A) else Color(0xFFE3F2E9) to Color(0xFF2E7D4F)
        Level.WARN -> if (dark) Color(0xFF3A2C12) to Color(0xFFF0B04A) else Color(0xFFFBEFD9) to Color(0xFF9A5C00)
        Level.BAD -> if (dark) Color(0xFF3D1A18) to Color(0xFFF27A70) else Color(0xFFFBE4E2) to Color(0xFFB3261E)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/* ---------- Счётный механизм ---------- */

private data class Cell(val c: Char, val frac: Boolean, val dim: Boolean)

/** Показание в виде барабанов счётчика: целая часть — тёмные, дробная — красные. */
@Composable
fun Odometer(value: Double?, kind: Kind, size: TextUnit = 20.sp) {
    val drum = if (isSystemInDarkTheme()) Color(0xFF0A0E12) else Color(0xFF1E2630)
    val red = Color(0xFFB3261E)
    val ink = Color(0xFFF3F5F7)

    val cells: List<Cell> = if (value == null) {
        List(kind.intDigits) { Cell('–', false, true) }
    } else {
        val s = String.format(Locale.US, "%.${kind.frac}f", value)
        val ip = s.substringBefore('.').padStart(kind.intDigits, '0')
        val fp = s.substringAfter('.', "")
        val firstNonZero = ip.indexOfFirst { it != '0' }.let { if (it < 0) ip.length - 1 else it }
        ip.mapIndexed { i, c -> Cell(c, false, i < firstNonZero) } + fp.map { Cell(it, true, false) }
    }
    val desc = if (value == null) "нет показаний" else "${fmt(value, kind.frac)} ${kind.unit}"
    Row(
        Modifier
            .semantics { contentDescription = desc }
            .background(drum, RoundedCornerShape(6.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        cells.forEach { cell ->
            Box(
                Modifier
                    .background(if (cell.frac) red else Color.White.copy(alpha = 0.07f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    cell.c.toString(),
                    color = if (cell.dim) ink.copy(alpha = 0.35f) else ink,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = size
                )
            }
        }
    }
}

/* ---------- Общие элементы ---------- */

@Composable
fun KindDot(kind: Kind) {
    Box(Modifier.size(10.dp).background(kindColor(kind), RoundedCornerShape(3.dp)))
}

@Composable
fun Section(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (title != null || trailing != null) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

@Composable
fun SubTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun StatusChip(level: Level, text: String) {
    val (bg, fg) = levelColors(level)
    Text(
        text,
        color = fg,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
fun KvRow(
    left: String,
    right: String = "",
    sub: String? = null,
    bold: Boolean = false,
    onClick: (() -> Unit)? = null,
    rightContent: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(left, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
            if (sub != null) Muted(sub)
        }
        Spacer(Modifier.width(12.dp))
        if (rightContent != null) rightContent()
        else Text(right, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun AlertRow(al: Alert, aptName: String?, onClick: () -> Unit) {
    val (_, fg) = levelColors(al.level)
    val bar = if (al.level == Level.INFO) MaterialTheme.colorScheme.outline else fg
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.padding(top = 2.dp).width(4.dp).height(38.dp).background(bar, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(12.dp))
        Column {
            if (aptName != null) Muted(aptName)
            Text(al.title)
            if (al.sub.isNotBlank()) Muted(al.sub)
        }
    }
}

@Composable
fun NumField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    supporting: String? = null,
    supportingColor: Color = Color.Unspecified,
    isError: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 20) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = if (mono) LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 18.sp) else LocalTextStyle.current,
        isError = isError,
        trailingIcon = trailing,
        supportingText = if (supporting != null) {
            { Text(supporting, color = supportingColor) }
        } else null
    )
}

@Composable
fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    maxLength: Int = 120
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else null
    )
}

@Composable
fun ConfirmDialog(text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        text = { Text(text) }
    )
}

/** Полноэкранный экран поверх вкладок: заголовок с кнопкой «Закрыть» и прокручиваемое содержимое. */
@Composable
fun OverlayScaffold(
    title: String,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Закрыть") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            content = content
        )
    }
}

/** Открывает настройки приложения (или его уведомлений) — когда системный запрос разрешения больше не показывается. */
fun openAppSettings(ctx: Context, notifications: Boolean) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
    val intent = if (notifications) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    } else details
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        try {
            ctx.startActivity(details)
        } catch (e2: ActivityNotFoundException) {
            // Настройки недоступны — пользователь сможет открыть их вручную
        }
    }
}
