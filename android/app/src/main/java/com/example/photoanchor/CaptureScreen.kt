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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                    "Welcome to PhoTO",
                    color = p.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Choose your creator name to start snapping Pins.",
                    color = p.mu,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    label = { Text("Creator name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                BitPolitoPrimaryButton(
                    text = "Open Camera",
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
    model: PhotoAnchorModel,
) {
    val p = LocalPalette.current
    val caption = state.draftCaption
    val boards = remember { listOf("Inspiration", "Bitcoin", "Street", "Architecture", "Nature") }
    var chosenBoard by remember { mutableStateOf("Inspiration") }
    var useV3Proof by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SurfCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp),
            padding = 18.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "New Pin",
                        color = p.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                    )
                    BitPolitoBadge(
                        text = if (useV3Proof) "v3 Sensor Proof" else "Signed Nostr Pin",
                        kind = if (useV3Proof) "ok" else "default",
                        icon = DeckIcon.Shield,
                    )
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
                                .heightIn(max = 260.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = model::setCaption,
                    enabled = !state.busy,
                    label = { Text("Add a title or #tags") },
                    placeholder = { Text("e.g. Golden hour in Turin #Street #Bitcoin") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                // Pinterest Board selector pills
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pin to Board", color = p.ink, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        boards.forEach { b ->
                            FilterChipPill(
                                label = b,
                                selected = chosenBoard == b,
                                icon = DeckIcon.Bookmark,
                                onClick = {
                                    chosenBoard = b
                                    if (!caption.contains("#$b", ignoreCase = true)) {
                                        val nextCap = if (caption.isBlank()) "#$b" else "$caption #$b"
                                        model.setCaption(nextCap)
                                    }
                                },
                            )
                        }
                    }
                }

                // Clean 2-pill Mode Selector + Collapsible Lab tools
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChipPill(
                        label = "Standard Pin",
                        selected = !useV3Proof,
                        icon = DeckIcon.Lightning,
                        onClick = { useV3Proof = false },
                    )
                    FilterChipPill(
                        label = "v3 Evidence Proof",
                        selected = useV3Proof,
                        icon = DeckIcon.Shield,
                        onClick = { useV3Proof = true },
                    )
                }

                CaptureEvidenceControls(state, model, caption)

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
                        text = if (state.busy) "Posting…" else "Post Pin",
                        icon = DeckIcon.Lightning,
                        onClick = {
                            if (useV3Proof) {
                                model.publishEvidence(caption)
                            } else {
                                publish(caption)
                            }
                        },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1.3f),
                    )
                }
            }
        }
    }
}

@Composable
@androidx.annotation.OptIn(markerClass = [androidx.camera.camera2.interop.ExperimentalCamera2Interop::class])
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
    var routeReady by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf(ImageCapture.Builder().build()) }
    var routeInfo by remember { mutableStateOf(org.json.JSONObject().put("fixed", false)) }
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
                        "Allow camera access to snap authentic Pins directly from the camera.",
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
        routeReady = false
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (!disposed) runCatching {
                provider = future.get()
                val route = runCatching { FixedCaptureRoute.select(context, back) }.getOrNull()
                fun bind(fixed: Boolean) {
                    val captureBuilder = ImageCapture.Builder()
                    val previewBuilder = Preview.Builder()
                    if (fixed) route?.physical?.let { physical ->
                        androidx.camera.camera2.interop.Camera2Interop.Extender(captureBuilder).setPhysicalCameraId(physical)
                        androidx.camera.camera2.interop.Camera2Interop.Extender(previewBuilder).setPhysicalCameraId(physical)
                    }
                    capture = captureBuilder.build()
                    val preview = previewBuilder.build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val selector = if (fixed && route != null) CameraSelector.Builder().addCameraFilter { infos ->
                        infos.filter { androidx.camera.camera2.interop.Camera2CameraInfo.from(it).cameraId == route.logical }
                    }.build() else if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    provider!!.unbindAll()
                    val camera = provider!!.bindToLifecycle(owner, selector, preview, capture)
                    camera.cameraControl.setZoomRatio(1f)
                    val observed = if (fixed && route != null) route.cameraId else androidx.camera.camera2.interop.Camera2CameraInfo.from(camera.cameraInfo).cameraId
                    routeInfo = org.json.JSONObject().put("fixed", fixed).put("camera", observed)
                        .put("mode", if (fixed) "camerax-1.4.1/fixed-physical/zoom-1/rotation-0" else "camerax-1.4.1/unfixed-route")
                    routeReady = true
                }
                if (route == null) bind(false) else {
                    runCatching { bind(true) }.getOrElse { bind(false) }
                }
            }.onFailure { onError() }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; routeReady = false; provider?.unbindAll() }
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
                BitPolitoBadge("Camera-Only Pin", kind = "solid", icon = DeckIcon.Camera)
                BitPolitoBadge(
                    text = if (routeInfo.optBoolean("fixed")) "Sensor Verified Route" else "Standard Lens",
                    kind = if (routeInfo.optBoolean("fixed")) "ok" else "default",
                    icon = DeckIcon.Shield,
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
                    enabled = !capturing && !busy && routeReady,
                )

                Box(
                    Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .border(4.dp, p.fg, CircleShape)
                        .semantics { contentDescription = if (capturing) "Saving photo" else "Capture photo" }
                        .clickable(enabled = !capturing && !busy && routeReady, role = Role.Button) {
                            capturing = true
                            val file = File(context.filesDir, "capture-${UUID.randomUUID()}.jpg")
                            val observedRoute = org.json.JSONObject(routeInfo.toString()).also {
                                it.put("mode", it.optString("mode") + if (flash) "/flash-on" else "/flash-off")
                            }
                            capture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        File(file.absolutePath + ".route.json").writeText(observedRoute.toString())
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
                    enabled = !capturing && !busy && routeReady,
                )
            }
        }
    }
}
