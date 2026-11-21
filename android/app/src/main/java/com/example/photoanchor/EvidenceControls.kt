package com.example.photoanchor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EvidenceSetupPanel(state: PhotoAnchorState, model: PhotoAnchorModel) {
    val p = LocalPalette.current
    var expanded by remember { mutableStateOf(false) }
    var retire by remember { mutableStateOf(false) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importCalibrationFrame(uri)
    }

    SurfCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PixelIcon(DeckIcon.Wrench, size = 15.dp, tint = p.fg)
                    Column {
                        Text(
                            text = "Sensor & Device Lab",
                            color = p.ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = "Hardware enrollment & PRNU sensor calibration",
                            color = p.mu,
                            fontSize = 11.5.sp,
                        )
                    }
                }
                BitPolitoBadge(
                    text = if (expanded) "Hide" else "Configure",
                    kind = "default",
                    icon = DeckIcon.Shield,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = p.bd)
                    Text("Device Enrollment", color = p.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(state.deviceStatus, color = p.mu, fontSize = 11.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BitPolitoOutlinedButton("Enroll", onClick = model::enrollDevice, enabled = !state.busy)
                        BitPolitoOutlinedButton("Refresh", onClick = model::refreshDevice, enabled = !state.busy)
                        BitPolitoOutlinedButton("Reset Key", onClick = { retire = true }, enabled = !state.busy)
                    }

                    HorizontalDivider(color = p.bd)
                    Text("PRNU Sensor Calibration", color = p.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(state.calibrationStatus, color = p.mu, fontSize = 11.5.sp)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BitPolitoOutlinedButton(
                            text = "Evaluate",
                            onClick = model::evaluateCalibration,
                            enabled = !state.busy,
                        )
                        BitPolitoOutlinedButton(
                            text = "Refresh Scores",
                            onClick = model::refreshCalibration,
                            enabled = !state.busy,
                        )
                        BitPolitoOutlinedButton(
                            text = "Import Held-Out JPEG",
                            onClick = { import.launch(arrayOf("image/jpeg")) },
                            enabled = !state.busy,
                        )
                    }
                }
            }
        }
    }

    if (retire) {
        AlertDialog(
            onDismissRequest = { retire = false },
            title = { Text("Reset device installation key?") },
            text = { Text("Retires only the P-256 device installation key. Your Nostr profile and existing pins stay intact.") },
            confirmButton = {
                TextButton(onClick = { retire = false; model.resetDevice() }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { retire = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
internal fun CaptureEvidenceControls(state: PhotoAnchorState, model: PhotoAnchorModel, caption: String) {
    val p = LocalPalette.current
    var showLabOptions by remember { mutableStateOf(false) }
    val isCleaned = state.captured?.name?.startsWith("cleaned-") == true

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BitPolitoBadge(
                text = if (isCleaned) "EXIF Cleaned Copy" else "Original Camera JPEG",
                kind = "ok",
                icon = DeckIcon.Shield,
            )
            Text(
                text = if (showLabOptions) "Hide Lab Options" else "Calibration & EXIF Options",
                color = p.fg,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { showLabOptions = !showLabOptions },
            )
        }

        AnimatedVisibility(visible = showLabOptions) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!isCleaned) {
                        BitPolitoOutlinedButton("Use Cleaned Copy", onClick = model::cleanCopy, enabled = !state.busy)
                    } else {
                        BitPolitoOutlinedButton("Use Original JPEG", onClick = model::useOriginal, enabled = !state.busy)
                    }
                    BitPolitoOutlinedButton("Renew v3 Prep", onClick = model::renewPreparation, enabled = !state.busy)
                    BitPolitoOutlinedButton("Start Calibration", onClick = model::startCalibration, enabled = !state.busy)
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for ((role, title) in listOf(
                        "enrollment" to "+ Enrollment Frame",
                        "development" to "+ Dev Frame",
                        "genuine" to "+ Held-Out Frame",
                    )) {
                        FilterChipPill(
                            label = title,
                            selected = false,
                            onClick = { if (!state.busy) model.addCalibrationFrame(role) },
                        )
                    }
                }
            }
        }
    }
}
