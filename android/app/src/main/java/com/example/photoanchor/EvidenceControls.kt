package com.example.photoanchor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun EvidenceSetupPanel(state: PhotoAnchorState, model: PhotoAnchorModel, onOpenCamera: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var retire by remember { mutableStateOf(false) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importCalibrationFrame(uri)
    }
    SurfCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BitPolitoOutlinedButton(if (expanded) "Hide Device & PRNU Lab" else "Device & PRNU Lab", onClick = { expanded = !expanded })
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Device identity · distinct from your Nostr key", style = MaterialTheme.typography.titleMedium)
                    Text(state.deviceStatus)
                    Text("Hardware enrollment does not authenticate camera pixels. The local operator configures trust.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = model::enrollDevice, enabled = !state.busy) { Text("Enroll / retry") }
                        OutlinedButton(onClick = model::refreshDevice, enabled = !state.busy) { Text("Refresh") }
                    }
                    TextButton(onClick = { retire = true }, enabled = !state.busy) { Text("Recover / retire installation key") }
                    HorizontalDivider()
                    Text("Private PRNU calibration · local server", style = MaterialTheme.typography.titleMedium)
                    Text(state.calibrationStatus)
                    state.calibrationGuide?.let { guide ->
                        Text(guide.summary)
                        if (guide.demo) Text("DEMO series · captures may share one scene. These are not independent calibration measurements.")
                        if (guide.guided) {
                            Text("Single-Pixel experiment · fixed rear camera · flash off · keep orientation unchanged.")
                            Text("Camera ${guide.camera} · ${guide.mode}", style = MaterialTheme.typography.bodySmall)
                        } else Text("Previous manual/test session. Start a new guide from a fresh capture to keep it separate.")
                    }
                    Text("Guide: 30 reference + 10 development + 10 independent held-out photos. Keep originals private. A score is not an authenticity verdict; no thresholds are enabled by this guide.")
                    OutlinedButton(onClick = onOpenCamera, enabled = state.ready && !state.busy) { Text("Capture calibration frames") }
                    OutlinedButton(onClick = { import.launch(arrayOf("image/jpeg")) }, enabled = !state.busy) { Text("Import other-camera held-out JPEG") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = model::evaluateCalibration, enabled = !state.busy &&
                            state.calibrationGuide?.let { if (it.guided) it.ready else it.state == "COLLECTING" && it.count("enrollment") >= 2 } == true) { Text("Evaluate") }
                        OutlinedButton(onClick = model::refreshCalibration, enabled = !state.busy) { Text("Refresh scores") }
                    }
                }
            }
        }
    }
    if (retire) AlertDialog(onDismissRequest = { retire = false }, title = { Text("Retire installation key?") },
        text = { Text("This retires only the P-256 installation key. Previous receipts remain verifiable; your Nostr profile stays intact. New enrollment and camera associations will be needed.") },
        confirmButton = { TextButton(onClick = { retire = false; model.resetDevice() }) { Text("Retire") } },
        dismissButton = { TextButton(onClick = { retire = false }) { Text("Cancel") } })
}

@Composable
internal fun CaptureEvidenceControls(state: PhotoAnchorState, model: PhotoAnchorModel, caption: String) {
    var showLabOptions by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(state.privacy)
        OutlinedButton(onClick = model::cleanCopy, enabled = !state.busy) { Text("Use cleaned copy · ordinary Nostr only") }
        if (state.captured?.name?.startsWith("cleaned-") == true)
            TextButton(onClick = model::useOriginal, enabled = !state.busy) { Text("Return to original JPEG") }
        Text("If analysis is queued, retry after a few seconds. No new signature is created on retry. Origin can remain unverified; an unavailable enrollment cannot issue a v3 receipt.")
        BitPolitoOutlinedButton(if (showLabOptions) "Hide Calibration Options" else "Calibration Options", onClick = { showLabOptions = !showLabOptions })
        AnimatedVisibility(visible = showLabOptions) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = model::renewPreparation, enabled = !state.busy) { Text("Renew an expired v3 preparation") }
                HorizontalDivider()
                Text("Or keep this frame private for calibration")
                OutlinedButton(onClick = { confirmNew = true }, enabled = !state.busy && state.captured?.name?.startsWith("cleaned-") != true) { Text("New guided calibration · 30 / 10 / 10") }
                if (state.calibrationGuide?.guided != true) {
                    OutlinedButton(onClick = { model.startCalibration() }, enabled = !state.busy) { Text("Start / resume manual calibration") }
                    for ((role, title) in listOf("enrollment" to "Enrollment frame", "development" to "Development frame", "genuine" to "Held-out genuine frame")) {
                        TextButton(onClick = { model.addCalibrationFrame(role) }, enabled = !state.busy) { Text("Store $title privately") }
                    }
                }
            }
        }
    }
    if (confirmNew) AlertDialog(onDismissRequest = { confirmNew = false },
        title = { Text("Start a separate PRNU collection?") },
        text = { Column {
            Text("Previous sessions, frames and references are retained. This guide starts empty: 30 reference, 10 development and 10 held-out frames. Use the fixed rear camera, flash off and one orientation. Store each new shot explicitly; nothing is published or approved automatically.")
            TextButton(onClick = { confirmNew = false; model.startCalibration(true, true) }) { Text("Start demo series · non-independent") }
        } },
        confirmButton = { TextButton(onClick = { confirmNew = false; model.startCalibration(true) }) { Text("Start new guide") } },
        dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancel") } })
}

@Composable
internal fun GuidedCalibrationCapture(state: PhotoAnchorState, model: PhotoAnchorModel) {
    val guide = state.calibrationGuide ?: return
    if (!guide.guided || guide.state != "COLLECTING") return
    val role = guide.nextRole
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (guide.demo) "Private PRNU · DEMO series" else "Private PRNU collection", style = MaterialTheme.typography.titleMedium)
        Text(guide.summary)
        Text(if (guide.demo) "Same-scene demo captures exercise the software only. No independent calibration, threshold approval or sensor attribution follows." else when (role) {
            "enrollment" -> "Reference: evenly lit plain wall or paper, no screen or bright white clipping. Move slightly between independent shots to change scene detail; keep camera and orientation fixed."
            "development" -> "Development: new shots of varied subjects, including simple and natural scenes. Do not reuse reference frames."
            "genuine" -> "Held-out: photograph new scenes, kept separate from development. Do not tune the reference using these results."
            else -> "Collection ready. Open Profile and Evaluate. No sensor match is approved automatically."
        })
        Text("Originals are uploaded privately, outside the public feed.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = model::addGuidedCalibrationFrame, enabled = !state.busy && state.captured?.name?.startsWith("cleaned-") != true) {
            Text(if (role == null) "Recover last stored frame" else
                "Store privately · ${CalibrationGuide.labels.getValue(role)} ${guide.count(role) + 1}/${CalibrationGuide.targets.getValue(role)}")
        }
        TextButton(onClick = model::discard, enabled = !state.busy) { Text("Retake this frame") }
        HorizontalDivider()
    }
}
