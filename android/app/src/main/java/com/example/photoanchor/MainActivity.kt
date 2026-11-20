package com.example.photoanchor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val wanted = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            // Android 13+: media images; Android 10-12: read external storage (writing to MediaStore needs none)
            add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE)
        }.toTypedArray()
    }
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    var camera by remember { mutableStateOf(has(Manifest.permission.CAMERA)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        camera = has(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) { if (wanted.any { !has(it) }) launcher.launch(wanted) }
    if (camera) CameraScreen() else Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Permesso fotocamera necessario")
            Button(onClick = { launcher.launch(wanted) }) { Text("Concedi permessi") }
        }
    }
}

@Composable
fun CameraScreen() {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val capture = remember { ImageCapture.Builder().build() }
    var back by remember { mutableStateOf(true) }
    var flashOn by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var thumb by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var pressed by remember { mutableStateOf(false) }
    var blink by remember { mutableStateOf(false) }
    val inner by animateFloatAsState(if (pressed) 0.82f else 1f, label = "shutter")
    val blinkAlpha by animateFloatAsState(if (blink) 0.8f else 0f, label = "blink")
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    LaunchedEffect(Unit) {
        runCatching { latestPhoto(ctx) }.getOrNull()?.let { lastUri = it; thumb = loadThumb(ctx, it) }
    }
    LaunchedEffect(back) {
        val provider = ProcessCameraProvider.getInstance(ctx).get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        provider.unbindAll()
        provider.bindToLifecycle(owner,
            if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture)
    }
    capture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

    fun shoot() {
        blink = true
        val file = File(ctx.filesDir, "photo_${System.currentTimeMillis()}.jpg")
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    blink = false
                    val res = PhotoProcessor.process(file)
                    info = "SHA-256 ${res.sha256.take(12)}…"
                    runCatching { saveToGallery(ctx, file) }
                        .onSuccess { u ->
                            if (u != null) { lastUri = u; thumb = loadThumb(ctx, u); info = "Salvata in Immagini/PhotoAnchor" }
                            else info = "Salvataggio in galleria non riuscito"
                        }
                        .onFailure { info = "Errore salvataggio: ${it.message}" }
                }
                override fun onError(e: ImageCaptureException) { blink = false; info = "Errore: ${e.message}" }
            })
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().padding(bottom = 140.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)))
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = blinkAlpha)))

        // top bar
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundIcon(40, { drawFlash(flashOn) }) { flashOn = !flashOn }
            info?.let {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f)) {
                    Text(it, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White)
                }
            }
        }

        // bottom controls
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp).navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color.DarkGray).border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                .clickable {
                    val u = lastUri
                    if (u == null) Toast.makeText(ctx, "Nessuna foto", Toast.LENGTH_SHORT).show()
                    else runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(u, "image/*")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }.onFailure { Toast.makeText(ctx, "Nessuna app per aprire la foto", Toast.LENGTH_SHORT).show() }
                }) {
                thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Box(Modifier.size(84.dp).clickable(remember { MutableInteractionSource() }, null) { shoot() }
                , contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color.White, radius = size.minDimension / 2 - 3.dp.toPx(), style = Stroke(4.dp.toPx()))
                    drawCircle(Color.White, radius = (size.minDimension / 2 - 10.dp.toPx()) * inner)
                }
            }
            RoundIcon(52, { drawFlip() }) { back = !back }
        }
    }
}

@Composable
fun RoundIcon(size: Int, icon: DrawScope.() -> Unit, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
        .clickable(remember { MutableInteractionSource() }, null, onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size((size * 0.55f).dp)) { icon() }
    }
}

// Icons drawn on a 24x24 grid
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
        drawArc(Color.White, start, 120f, false, Offset(ctr.x - r, ctr.y - r),
            androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = Stroke(2f * k, cap = StrokeCap.Round))
        val a = Math.toRadians((start + 120f).toDouble())
        val tip = Offset(ctr.x + r * cos(a).toFloat(), ctr.y + r * sin(a).toFloat())
        val tx = -sin(a).toFloat(); val ty = cos(a).toFloat()   // tangent
        val nx = cos(a).toFloat(); val ny = sin(a).toFloat()    // radial
        val h = 4.5f * k
        val head = Path().apply {
            moveTo(tip.x + tx * h, tip.y + ty * h)
            lineTo(tip.x + nx * h * 0.9f, tip.y + ny * h * 0.9f)
            lineTo(tip.x - nx * h * 0.9f, tip.y - ny * h * 0.9f); close()
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
    ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf("Pictures/PhotoAnchor%"),
        "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use {
        if (it.moveToFirst()) android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it.getLong(0)) else null
    }

private fun loadThumb(ctx: Context, uri: Uri) =
    runCatching { ctx.contentResolver.loadThumbnail(uri, Size(200, 200), null).asImageBitmap() }.getOrNull()
