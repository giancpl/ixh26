package com.example.photoanchor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun EvidenceSetupPanel(state: PhotoAnchorState, model: PhotoAnchorModel) {
    var retire by remember { mutableStateOf(false) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importCalibrationFrame(uri)
    }
    SurfCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
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
            Text("Capture independent frames through one fixed route. Start with 30 enrollment frames; reserve separate development and held-out samples. Scores alone do not approve thresholds.")
            OutlinedButton(onClick = { import.launch(arrayOf("image/jpeg")) }, enabled = !state.busy) { Text("Import other-camera held-out JPEG") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = model::evaluateCalibration, enabled = !state.busy) { Text("Evaluate") }
                OutlinedButton(onClick = model::refreshCalibration, enabled = !state.busy) { Text("Refresh scores") }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(state.privacy)
        OutlinedButton(onClick = model::cleanCopy, enabled = !state.busy) { Text("Use cleaned copy · ordinary Nostr only") }
        if (state.captured?.name?.startsWith("cleaned-") == true)
            TextButton(onClick = model::useOriginal, enabled = !state.busy) { Text("Return to original JPEG") }
        Button(onClick = { model.publishEvidence(caption) }, enabled = !state.busy &&
            state.captured?.name?.startsWith("cleaned-") != true) { Text("Publish original with v3 evidence") }
        Text("If analysis is queued, retry after a few seconds. No new signature is created on retry. Origin can remain unverified; an unavailable enrollment cannot issue a v3 receipt.")
        TextButton(onClick = model::renewPreparation, enabled = !state.busy) { Text("Renew an expired v3 preparation") }
        HorizontalDivider()
        Text("Or keep this frame private for calibration")
        OutlinedButton(onClick = model::startCalibration, enabled = !state.busy) { Text("Start / resume calibration on this route") }
        for ((role, title) in listOf("enrollment" to "Enrollment frame", "development" to "Development frame", "genuine" to "Held-out genuine frame")) {
            TextButton(onClick = { model.addCalibrationFrame(role) }, enabled = !state.busy) { Text("Store $title privately") }
        }
    }
}
