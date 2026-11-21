package com.example.photoanchor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

@Composable
internal fun OnboardingNameCard(
    state: PhotoAnchorState,
    onSaveName: (String) -> Unit,
) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf("") }
    Box(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        SurfCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
            padding = 24.dp,
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(p.soft)
                        .border(1.dp, p.bd, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelIcon(DeckIcon.CowHead, size = 28.dp, tint = p.fg)
                }
                Text(
                    "Welcome to BitPin",
                    color = p.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Choose your creator name before snapping your first Pin.",
                    color = p.mu,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    label = { Text("Display name (declared)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                BitPolitoPrimaryButton(
                    text = "Continue to Camera",
                    icon = DeckIcon.Camera,
                    enabled = state.ready && !state.busy && name.isNotBlank(),
                    onClick = { onSaveName(name) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun PublishScreen(
    state: PhotoAnchorState,
    publish: (String) -> Unit,
    discard: () -> Unit,
) {
    val p = LocalPalette.current
    var caption by rememberSaveable(state.captured?.absolutePath) { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SurfCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp),
            padding = 20.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Create Pin",
                        color = p.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                    )
                    BitPolitoBadge("Not submitted", icon = DeckIcon.Hourglass)
                }

                state.preview?.let {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0D1124))
                            .border(1.dp, p.bd, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Captured Pin",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 1000) caption = it },
                    label = { Text("Title or description") },
                    placeholder = { Text("Tell everyone what your Pin is about…") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                Text(
                    "This exact JPEG is hashed and its post signed once. Publication and timestamp " +
                        "progress independently; regtest confirmation requires explicit operator mining.",
                    color = p.mu, fontSize = 12.sp,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BitPolitoOutlinedButton(
                        text = "Retake",
                        icon = DeckIcon.Camera,
                        onClick = discard,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    )
                    BitPolitoPrimaryButton(
                        text = if (state.busy) "Publishing…" else "Publish Pin",
                        icon = DeckIcon.Star,
                        onClick = { publish(caption) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1.3f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun CameraScreen(busy: Boolean, onCaptured: (File) -> Unit, onError: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permitted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    var back by remember { mutableStateOf(true) }
    var flash by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    val capture = remember { ImageCapture.Builder().build() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    LaunchedEffect(Unit) { if (!permitted) permission.launch(Manifest.permission.CAMERA) }

    if (!permitted) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            SurfCard(
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                padding = 24.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PixelIcon(DeckIcon.Camera, size = 32.dp, tint = p.fg)
                    Text(
                        "Camera Access Needed",
                        color = p.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Allow camera access to take a photo and publish a signed post.",
                        color = p.mu,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    BitPolitoPrimaryButton(
                        text = "Allow Camera",
                        icon = DeckIcon.Camera,
                        onClick = { permission.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        return
    }

    DisposableEffect(owner, back) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (!disposed) runCatching {
                provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider!!.unbindAll()
                provider!!.bindToLifecycle(
                    owner,
                    if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    capture,
                )
            }.onFailure { onError() }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; provider?.unbindAll() }
    }
    capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF05070E))
                .border(2.dp, p.bd, RoundedCornerShape(24.dp)),
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BitPolitoBadge("New Pin", kind = "solid", icon = DeckIcon.Camera)
                BitPolitoBadge(
                    text = if (flash) "Flash On" else "Flash Off",
                    kind = if (flash) "pending" else "default",
                    icon = DeckIcon.Lightning,
                )
            }
        }

        SurfCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BitPolitoOutlinedButton(
                    text = "Flip",
                    icon = DeckIcon.Smartphone,
                    onClick = { back = !back },
                    enabled = !capturing && !busy,
                )

                Box(
                    Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .border(4.dp, p.fg, CircleShape)
                        .semantics { contentDescription = if (capturing) "Saving photo" else "Capture photo" }
                        .clickable(enabled = !capturing && !busy, role = Role.Button) {
                            capturing = true
                            val file = File(context.filesDir, "capture-${UUID.randomUUID()}.jpg")
                            capture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        ContextCompat.getMainExecutor(context).execute {
                                            capturing = false
                                            onCaptured(file)
                                        }
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        ContextCompat.getMainExecutor(context).execute {
                                            capturing = false
                                            onError()
                                        }
                                    }
                                },
                            )
                        }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(if (capturing || busy) p.mu else p.fg),
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelIcon(DeckIcon.Camera, size = 22.dp, tint = p.primaryFg)
                    }
                }

                BitPolitoOutlinedButton(
                    text = if (flash) "Flash On" else "Flash",
                    icon = DeckIcon.Lightning,
                    onClick = { flash = !flash },
                    enabled = !capturing && !busy,
                )
            }
        }
    }
}
