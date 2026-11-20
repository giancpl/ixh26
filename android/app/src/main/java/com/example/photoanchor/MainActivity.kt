package com.example.photoanchor

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF12695E))) { PhotoAnchorApp() } }
    }
}

@Composable
private fun PhotoAnchorApp(model: PhotoAnchorModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("Feed") }
    LaunchedEffect(state.ready) { if (state.ready && state.name.isBlank()) tab = "Profile" }
    val saveBundle = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) model.export(uri)
    }
    val checkCopy = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.verifyCopy(uri)
    }
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                    Text("PhotoAnchor", style = MaterialTheme.typography.headlineSmall)
                    Text("Local Nostr · Bitcoin regtest", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        bottomBar = {
            NavigationBar {
                listOf("Feed", "Camera", "Profile").forEach { name ->
                    NavigationBarItem(selected = tab == name, onClick = { tab = name },
                        enabled = state.ready, icon = { Text(when (name) { "Feed" -> "▤"; "Camera" -> "◎"; else -> "○" }) },
                        label = { Text(name) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(state.message, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            if (state.queued > 0) Text("${state.queued} signed event(s) queued · retries preserve the signature",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (tab) {
                "Profile" -> ProfileScreen(state, model::saveProfile)
                "Camera" -> if (state.name.isBlank()) {
                    Text("Create your local profile first.", Modifier.padding(24.dp))
                } else if (state.captured != null) {
                    PublishScreen(state, model::publish, model::discard)
                } else CameraScreen(state.busy, model::captured, model::cameraError)
                else -> FeedScreen(state, model::refresh, model::select)
            }
        }
    }
    state.selected?.let { selected ->
        AlertDialog(onDismissRequest = model::dismiss,
            title = { Text("Verify photo") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VerificationLine("Signature and event ID", state.verification?.event ?: "Checking on this device…")
                    VerificationLine("JPEG integrity", state.verification?.integrity ?: "Checking original bytes…")
                    VerificationLine("Bitcoin timestamp", state.verification?.timestamp ?: "Requesting a fresh local node check…")
                    VerificationLine("Relay publication", state.verification?.publication ?: "Unknown")
                    Text("The timestamp is verified by the configured local node. Regtest is a development blockchain, not a mainnet anchor.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Names, captions and posted time are author declarations. A signature does not establish the scene's authenticity or capture time.",
                        style = MaterialTheme.typography.bodySmall)
                    SelectionContainer { Text("Signer: ${selected.pubkey}\nEvent: ${selected.id}", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(onClick = { checkCopy.launch(arrayOf("image/jpeg")) }, enabled = !state.busy) { Text("Check a JPEG copy") }
                    state.copyResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(onClick = { saveBundle.launch("photoanchor-${selected.id.take(12)}.zip") }, enabled = !state.busy) {
                        Text("Export proof bundle")
                    }
                }
            },
            confirmButton = { TextButton(onClick = model::verify) { Text("Verify again") } },
            dismissButton = { TextButton(onClick = model::dismiss) { Text("Close") } })
    }
}

@Composable
private fun VerificationLine(title: String, value: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProfileScreen(state: PhotoAnchorState, save: (String) -> Unit) {
    var name by remember(state.name) { mutableStateOf(state.name) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your local profile", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name, { if (it.length <= 80) name = it }, label = { Text("Display name (declared)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { save(name) }, enabled = state.ready && !state.busy && name.isNotBlank()) { Text("Save and publish profile") }
        Text("One key is generated on this device. The private key is encrypted using Android Keystore and excluded from backups. There is no key recovery in this demo.",
            style = MaterialTheme.typography.bodyMedium)
        Text("Public signing key", style = MaterialTheme.typography.titleSmall)
        SelectionContainer { Text(state.pubkey, style = MaterialTheme.typography.bodySmall) }
        Text("Your name can change. Existing photo signatures remain bound to this public key.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FeedScreen(state: PhotoAnchorState, refresh: () -> Unit, select: (PhotoPost) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Local photo feed", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = refresh) { Text("Refresh") }
        }
        if (state.feed.isEmpty()) Text("No local photos yet. Publish from Camera; the feed refreshes automatically.", Modifier.padding(20.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(state.feed, key = { it.post.id }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.name.ifBlank { entry.post.pubkey.take(16) + "…" }, style = MaterialTheme.typography.titleMedium)
                        Text("Declared name · signer ${entry.post.pubkey.take(12)}…", style = MaterialTheme.typography.labelSmall)
                        entry.preview?.let { image -> Image(image.asImageBitmap(), "Photo preview", Modifier.fillMaxWidth().heightIn(max = 360.dp), contentScale = ContentScale.Fit) }
                            ?: Text("Preview unavailable; original bytes are checked in Verify.")
                        if (entry.post.caption.isNotEmpty()) Text(entry.post.caption)
                        Text("Signature checked locally · timestamp checked separately", style = MaterialTheme.typography.labelSmall)
                        Button(onClick = { select(entry.post) }) { Text("Verify photo") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PublishScreen(state: PhotoAnchorState, publish: (String) -> Unit, discard: () -> Unit) {
    var caption by remember(state.captured) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Publish original JPEG", style = MaterialTheme.typography.titleLarge)
        state.preview?.let { Image(it.asImageBitmap(), "Captured photo", Modifier.fillMaxWidth().heightIn(max = 320.dp), contentScale = ContentScale.Fit) }
        OutlinedTextField(caption, { if (it.length <= 1000) caption = it }, label = { Text("Caption (optional)") }, modifier = Modifier.fillMaxWidth())
        Text("This exact JPEG will be hashed and the post signed once. Bitcoin timestamps may remain pending until the operator mines blocks.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { publish(caption) }, enabled = !state.busy) { Text("Sign and publish") }
        OutlinedButton(onClick = discard, enabled = !state.busy) { Text("Discard and retake") }
    }
}

@Composable
private fun CameraScreen(busy: Boolean, onCaptured: (File) -> Unit, onError: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
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
        Column(Modifier.padding(24.dp)) {
            Text("Camera permission is required to take a photo.")
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
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
                provider!!.bindToLifecycle(owner, if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture)
            }.onFailure { onError() }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; provider?.unbindAll() }
    }
    capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    Column(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().weight(1f))
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { back = !back }, enabled = !capturing && !busy) { Text("Flip") }
            Button(onClick = {
                capturing = true
                val file = File(context.filesDir, "capture-${UUID.randomUUID()}.jpg")
                capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        ContextCompat.getMainExecutor(context).execute { capturing = false; onCaptured(file) }
                    }
                    override fun onError(exception: ImageCaptureException) {
                        ContextCompat.getMainExecutor(context).execute { capturing = false; onError() }
                    }
                })
            }, enabled = !capturing && !busy) { Text(if (capturing) "Saving…" else "Capture") }
            OutlinedButton(onClick = { flash = !flash }, enabled = !capturing && !busy) { Text(if (flash) "Flash on" else "Flash off") }
        }
    }
}
