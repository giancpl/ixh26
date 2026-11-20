package com.example.photoanchor

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

private const val PREFS_NAME = "photoanchor_prefs"
private const val KEY_SERVER_URL = "server_url"
private const val KEY_AUTO_SYNC = "auto_sync"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { App() }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val wanted = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE)
        }.toTypedArray()
    }
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    var camera by remember { mutableStateOf(has(Manifest.permission.CAMERA)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        camera = has(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) { if (wanted.any { !has(it) }) launcher.launch(wanted) }
    if (camera) {
        PhotoAnchorMain()
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Permesso fotocamera necessario", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { launcher.launch(wanted) }) { Text("Concedi permessi") }
            }
        }
    }
}

@Composable
fun PhotoAnchorMain() {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var activeTab by remember { mutableStateOf("camera") } // "camera" | "social"
    var serverUrl by remember {
        mutableStateOf(
            prefs.getString(KEY_SERVER_URL, null)?.takeIf { it.isNotBlank() }
                ?: BuildConfig.DEFAULT_SERVER_URL,
        )
    }
    var autoSync by remember { mutableStateOf(prefs.getBoolean(KEY_AUTO_SYNC, true)) }
    var showServerDialog by remember { mutableStateOf(false) }

    val capture = remember { ImageCapture.Builder().build() }
    var back by remember { mutableStateOf(true) }
    var flashOn by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var lastResult by remember { mutableStateOf<PhotoProcessor.Result?>(null) }
    var blink by remember { mutableStateOf(false) }

    val blinkAlpha by animateFloatAsState(if (blink) 0.8f else 0f, label = "blink")
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    LaunchedEffect(Unit) {
        runCatching { latestPhoto(ctx) }.getOrNull()?.let { lastUri = it; thumb = loadThumb(ctx, it) }
    }
    LaunchedEffect(back, activeTab) {
        if (activeTab == "camera") {
            val provider = ProcessCameraProvider.getInstance(ctx).get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            provider.bindToLifecycle(
                owner,
                if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                capture,
            )
        }
    }
    capture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

    fun syncToWeb(res: PhotoProcessor.Result) {
        val url = serverUrl.trim()
        if (url.isEmpty()) return
        info = "Invio & OTS Bitcoin..."
        scope.launch {
            runCatching { WebBridgeAnchorer(url).anchor(res) }
                .onSuccess { status -> info = status }
                .onFailure { err -> info = "Locale OK · Server offline (${err.message?.take(30)})" }
        }
    }

    fun shoot() {
        blink = true
        val file = File(ctx.filesDir, "photo_${System.currentTimeMillis()}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    blink = false
                    val res = PhotoProcessor.process(file)
                    lastResult = res
                    info = "SHA-256 ${res.sha256.take(12)}…"
                    runCatching { saveToGallery(ctx, file) }
                        .onSuccess { u ->
                            if (u != null) {
                                lastUri = u
                                thumb = loadThumb(ctx, u)
                                info = if (serverUrl.isNotBlank() && autoSync) {
                                    "Salvata · Invio al social..."
                                } else {
                                    "Salvata in Immagini/PhotoAnchor"
                                }
                            } else {
                                info = "Salvataggio in galleria non riuscito"
                            }
                        }
                        .onFailure { info = "Errore salvataggio: ${it.message}" }

                    if (serverUrl.isNotBlank() && autoSync) {
                        syncToWeb(res)
                    }
                }

                override fun onError(e: ImageCaptureException) {
                    blink = false
                    info = "Errore: ${e.message}"
                }
            },
        )
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (activeTab == "camera") {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 128.dp)
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
                    )
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = blinkAlpha)))

                    // Top bar
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RoundIcon(40, { drawFlash(flashOn) }) { flashOn = !flashOn }

                            Surface(
                                shape = CircleShape,
                                color = if (serverUrl.isNotBlank()) Color(0xFF001CE0).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.18f),
                                modifier = Modifier.clickable { showServerDialog = true },
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (serverUrl.isNotBlank()) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.5f)),
                                    )
                                    Text(
                                        text = if (serverUrl.isNotBlank()) {
                                            serverUrl.removePrefix("http://").removePrefix("https://").take(22)
                                        } else {
                                            "Imposta Server"
                                        },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }

                        info?.let { msg ->
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clickable { lastResult?.let { if (serverUrl.isNotBlank()) syncToWeb(it) } },
                            ) {
                                Text(msg, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    // Bottom camera shutter controls
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(128.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.DarkGray)
                                .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                .clickable {
                                    val u = lastUri
                                    if (u == null) {
                                        Toast.makeText(ctx, "Nessuna foto", Toast.LENGTH_SHORT).show()
                                    } else {
                                        runCatching {
                                            ctx.startActivity(
                                                Intent(Intent.ACTION_VIEW)
                                                    .setDataAndType(u, "image/*")
                                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                            )
                                        }.onFailure {
                                            Toast.makeText(ctx, "Nessuna app per aprire la foto", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                        ) {
                            thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }

                        Box(
                            Modifier
                                .size(82.dp)
                                .clickable(remember { MutableInteractionSource() }, null) { shoot() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawCircle(Color.White, radius = size.minDimension / 2 - 3.dp.toPx(), style = Stroke(4.dp.toPx()))
                                drawCircle(Color.White, radius = size.minDimension / 2 - 10.dp.toPx())
                            }
                        }

                        RoundIcon(52, { drawFlip() }) { back = !back }
                    }
                }
            } else {
                SocialWebScreen(
                    serverUrl = serverUrl,
                    onConfigure = { showServerDialog = true },
                )
            }
        }

        // Bottom app switcher: Fotocamera | Social Nostr
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A1028))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val isCam = activeTab == "camera"
            Button(
                onClick = { activeTab = "camera" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCam) Color(0xFF001CE0) else Color(0xFF1A2242),
                    contentColor = Color.White,
                ),
            ) {
                Text("Fotocamera")
            }
            Button(
                onClick = { activeTab = "social" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isCam) Color(0xFF001CE0) else Color(0xFF1A2242),
                    contentColor = Color.White,
                ),
            ) {
                Text("Social Nostr")
            }
        }
    }

    if (showServerDialog) {
        ServerConfigDialog(
            initialUrl = serverUrl,
            initialAutoSync = autoSync,
            hasPendingPhoto = lastResult != null,
            onDismiss = { showServerDialog = false },
            onSave = { url, auto ->
                val clean = url.trim().trimEnd('/')
                serverUrl = clean
                autoSync = auto
                prefs.edit()
                    .putString(KEY_SERVER_URL, clean)
                    .putBoolean(KEY_AUTO_SYNC, auto)
                    .apply()
                showServerDialog = false
                info = if (clean.isNotBlank()) "Server: $clean" else "Server scollegato"
            },
            onSendLast = { url ->
                val clean = url.trim().trimEnd('/')
                serverUrl = clean
                prefs.edit().putString(KEY_SERVER_URL, clean).apply()
                showServerDialog = false
                lastResult?.let { syncToWeb(it) }
            },
            onOpenWeb = { url ->
                val clean = url.trim().trimEnd('/')
                if (clean.isNotBlank()) {
                    serverUrl = clean
                    prefs.edit().putString(KEY_SERVER_URL, clean).apply()
                    showServerDialog = false
                    activeTab = "social"
                }
            },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SocialWebScreen(
    serverUrl: String,
    onConfigure: () -> Unit,
) {
    val clean = serverUrl.trim()
    if (clean.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF9F9F9))
                .statusBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Indirizzo Backend BitPin", fontSize = 20.sp, color = Color(0xFF001CE0))
                Text(
                    "Inserisci l'IP locale o VPN del server (es. http://192.168.1.x:8787) per aprire il social Nostr con profili, foto e bacheche.",
                    fontSize = 13.sp,
                    color = Color(0xFF202632),
                )
                Button(
                    onClick = onConfigure,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001CE0)),
                ) {
                    Text("Inserisci URL Server", color = Color.White)
                }
            }
        }
        return
    }

    AndroidView(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadUrl(clean)
            }
        },
        update = { wv ->
            if (wv.url.isNullOrEmpty()) wv.loadUrl(clean)
        },
    )
}

@Composable
private fun ServerConfigDialog(
    initialUrl: String,
    initialAutoSync: Boolean,
    hasPendingPhoto: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit,
    onSendLast: (String) -> Unit,
    onOpenWeb: (String) -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var auto by remember { mutableStateOf(initialAutoSync) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Backend (LAN / VPN)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Inserisci l'IP locale o VPN del server BitPin. Nessun codice pairing richiesto.",
                    fontSize = 12.sp,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; testStatus = null },
                    label = { Text("URL Server (es. http://192.168.1.x:8787)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Invia & ancora dopo ogni scatto", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val target = url.trim()
                        if (target.isBlank()) {
                            testStatus = "Inserisci un URL"
                        } else {
                            testStatus = "Verifica..."
                            scope.launch {
                                testStatus = runCatching { WebBridgeAnchorer.ping(target) }
                                    .getOrElse { "Errore: ${it.message}" }
                            }
                        }
                    }) { Text("Testa") }
                    if (hasPendingPhoto && url.isNotBlank()) {
                        TextButton(onClick = { onSendLast(url) }) { Text("Invia ultima") }
                    }
                    if (url.isNotBlank()) {
                        TextButton(onClick = { onOpenWeb(url) }) { Text("Apri Social") }
                    }
                }
                testStatus?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(url, auto) }) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Chiudi") }
        },
    )
}

@Composable
fun RoundIcon(size: Int, icon: DrawScope.() -> Unit, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size((size * 0.55f).dp)) { icon() }
    }
}

private fun DrawScope.drawFlash(on: Boolean) {
    val k = size.minDimension / 24f
    val c = if (on) Color(0xFFFFD60A) else Color.White
    val p = Path().apply {
        moveTo(13f * k, 2f * k); lineTo(4f * k, 14f * k); lineTo(11f * k, 14f * k)
        lineTo(10f * k, 22f * k); lineTo(20f * k, 9f * k); lineTo(13f * k, 9f * k); close()
    }
    if (on) drawPath(p, c) else {
        drawPath(p, c, style = Stroke(2f * k))
        drawLine(c, Offset(3f * k, 3f * k), Offset(21f * k, 21f * k), 2.2f * k, StrokeCap.Round)
    }
}

private fun DrawScope.drawFlip() {
    val k = size.minDimension / 24f
    val r = 8f * k
    val ctr = Offset(12f * k, 12f * k)
    for (start in listOf(200f, 20f)) {
        drawArc(
            Color.White,
            start,
            120f,
            false,
            Offset(ctr.x - r, ctr.y - r),
            androidx.compose.ui.geometry.Size(2 * r, 2 * r),
            style = Stroke(2f * k, cap = StrokeCap.Round),
        )
        val a = Math.toRadians((start + 120f).toDouble())
        val tip = Offset(ctr.x + r * cos(a).toFloat(), ctr.y + r * sin(a).toFloat())
        val tx = -sin(a).toFloat()
        val ty = cos(a).toFloat()
        val nx = cos(a).toFloat()
        val ny = sin(a).toFloat()
        val h = 4.5f * k
        val head = Path().apply {
            moveTo(tip.x + tx * h, tip.y + ty * h)
            lineTo(tip.x + nx * h * 0.9f, tip.y + ny * h * 0.9f)
            lineTo(tip.x - nx * h * 0.9f, tip.y - ny * h * 0.9f)
            close()
        }
        drawPath(head, Color.White)
    }
}

private fun saveToGallery(ctx: Context, file: File): Uri? {
    val cv = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoAnchor")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val r = ctx.contentResolver
    val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
    r.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } }
    r.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    return uri
}

private fun latestPhoto(ctx: Context): Uri? =
    ctx.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Images.Media._ID),
        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
        arrayOf("Pictures/PhotoAnchor%"),
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use {
        if (it.moveToFirst()) ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it.getLong(0)) else null
    }

private fun loadThumb(ctx: Context, uri: Uri) =
    runCatching { ctx.contentResolver.loadThumbnail(uri, Size(200, 200), null).asImageBitmap() }.getOrNull()
