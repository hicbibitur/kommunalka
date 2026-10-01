package ru.kommunalka.app

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/**
 * Экран сканирования: камера непрерывно распознаёт цифры в рамке,
 * пользователь нажимает на верный вариант.
 */
@Composable
fun ScannerScreen(
    kind: Kind,
    title: String,
    prev: Double?,
    avg: Double?,
    onResult: (Double) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var hasPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasPerm = ok
        asked = true
    }
    LaunchedEffect(Unit) { if (!hasPerm) launcher.launch(Manifest.permission.CAMERA) }

    var cands by remember { mutableStateOf<List<MeterOcr.Candidate>>(emptyList()) }
    var seen by remember { mutableStateOf("") }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var camError by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPerm) {
            CameraPreview(
                onCamera = { camera = it; torch = false },
                onError = { camError = true },
                onLines = { lines ->
                    val c = MeterOcr.candidates(lines, kind, prev, avg)
                    if (c.isNotEmpty()) {
                        cands = c
                        seen = lines.joinToString("  ").take(60)
                    }
                }
            )
            // Рамка, в которую нужно поймать цифры
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.86f)
                    .fillMaxHeight(0.18f)
                    .border(2.dp, Color.White, RoundedCornerShape(10.dp))
            )
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (asked) "Без доступа к камере распознать показания не получится. Разрешите его или введите цифры вручную."
                    else "Запрашиваем доступ к камере…",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                if (asked) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Разрешить камеру") }
                    TextButton(onClick = { openAppSettings(ctx, notifications = false) }) {
                        Text("Открыть настройки приложения", color = Color.White)
                    }
                }
            }
        }

        // Верхняя панель
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("Поймайте цифры счётчика в рамку", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            }
            val cam = camera
            if (cam != null && cam.cameraInfo.hasFlashUnit()) {
                TextButton(onClick = {
                    torch = !torch
                    cam.cameraControl.enableTorch(torch)
                }) { Text(if (torch) "Свет выкл." else "Свет", color = Color.White) }
            }
        }

        // Нижняя панель с вариантами
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .padding(16.dp)
        ) {
            if (camError) {
                Text("Камера недоступна", fontWeight = FontWeight.SemiBold)
                Muted("Возможно, её использует другое приложение. Закройте экран и введите показание вручную.")
            } else if (cands.isEmpty()) {
                Text("Ищем цифры…", fontWeight = FontWeight.SemiBold)
                Muted("Держите телефон ровно, без бликов. В темноте включите свет.")
            } else {
                Text("Нажмите на верное значение", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cands.forEachIndexed { i, c ->
                        val label = fmt(c.value, kind.frac)
                        if (i == 0) {
                            Button(onClick = { onResult(c.value) }) {
                                Text(label, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
                            }
                        } else {
                            OutlinedButton(onClick = { onResult(c.value) }) {
                                Text(label, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
                            }
                        }
                    }
                }
                Muted("Распознано: $seen", Modifier.padding(top = 6.dp))
            }
            if (prev != null) Muted("Прошлое показание: ${fmt(prev, kind.frac)} ${kind.unit}", Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun CameraPreview(onCamera: (Camera) -> Unit, onError: () -> Unit, onLines: (List<String>) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    // При повороте экрана камера перепривязывается, чтобы кадры анализировались с верной ориентацией
    val orientation = LocalConfiguration.current.orientation
    val previewView = remember {
        PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val currentOnLines by rememberUpdatedState(onLines)
    val currentOnCamera by rememberUpdatedState(onCamera)
    val currentOnError by rememberUpdatedState(onError)

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    DisposableEffect(owner, orientation) {
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(ctx)
        val analyzer = MeterAnalyzer { lines -> main.execute { currentOnLines(lines) } }
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        future.addListener({
            if (disposed) return@addListener
            val p = try {
                future.get()
            } catch (e: Exception) {
                Log.e("Kommunalka", "Камера недоступна", e)
                currentOnError()
                return@addListener
            }
            provider = p
            val selector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            try {
                p.unbindAll()
                currentOnCamera(p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
            } catch (e: Exception) {
                Log.e("Kommunalka", "Не удалось подключить камеру", e)
                currentOnError()
            }
        }, main)

        onDispose {
            disposed = true
            provider?.unbindAll()
            analyzer.close()
            executor.shutdown()
        }
    }
}

/** Распознаёт текст в кадре не чаще раза в 600 мс и отдаёт строки из центральной полосы кадра. */
private class MeterAnalyzer(private val onLines: (List<String>) -> Unit) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastTs = 0L

    @Volatile
    private var busy = false

    @Volatile
    private var closed = false

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        val media = imageProxy.image
        if (closed || media == null || busy || now - lastTs < 600) {
            imageProxy.close()
            return
        }
        busy = true
        lastTs = now
        val rot = imageProxy.imageInfo.rotationDegrees
        val w = (if (rot % 180 == 0) imageProxy.width else imageProxy.height).toFloat()
        val h = (if (rot % 180 == 0) imageProxy.height else imageProxy.width).toFloat()
        val task = try {
            recognizer.process(InputImage.fromMediaImage(media, rot))
        } catch (e: Exception) {
            busy = false
            imageProxy.close()
            return
        }
        task
            .addOnSuccessListener { text ->
                if (closed) return@addOnSuccessListener
                val all = text.textBlocks.flatMap { it.lines }
                val central = all.filter { line ->
                    val b = line.boundingBox ?: return@filter false
                    val cx = b.exactCenterX() / w
                    val cy = b.exactCenterY() / h
                    cx in 0.12f..0.88f && cy in 0.30f..0.70f
                }
                val chosen = if (central.isNotEmpty()) central else all
                if (chosen.isNotEmpty()) onLines(chosen.map { it.text })
            }
            .addOnCompleteListener {
                busy = false
                imageProxy.close()
            }
    }

    fun close() {
        closed = true
        recognizer.close()
    }
}
