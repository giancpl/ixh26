package com.example.photoanchor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** A presentation-only scenario. Never constructs Verification or exported evidence. */
@Composable
internal fun DemoWalkthrough(run: String) {
    val palette = LocalPalette.current
    val steps = listOf("Capture", "Signature", "Origin analysis", "Bitcoin timestamp", "Verification")
    var completed by remember(run) { mutableIntStateOf(0) }
    var showDemoSuccess by remember(run) { mutableStateOf(false) }
    LaunchedEffect(run) {
        for (step in 1..steps.size) {
            delay(600)
            completed = step
        }
    }
    Surface(color = palette.soft, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("SIMULATED DEMO · presentation only", color = palette.fg, fontSize = 13.sp)
            LinearProgressIndicator(progress = { completed.toFloat() / steps.size }, modifier = Modifier.fillMaxWidth())
            Text(if (completed == steps.size) "Simulated walkthrough complete" else "Simulation: ${steps[completed]}…", color = palette.ink)
            BitPolitoPrimaryButton(
                text = "Verifica demo",
                icon = DeckIcon.Shield,
                onClick = { showDemoSuccess = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Capture → sign → origin → timestamp → verify", color = palette.mu, fontSize = 11.sp)
            Text("This animation is not cryptographic evidence. Real results are shown in Cryptographic Verification Details; exports contain only actual proofs.", color = palette.mu, fontSize = 11.sp)
        }
    }
    if (showDemoSuccess) {
        AlertDialog(
            onDismissRequest = { showDemoSuccess = false },
            title = { Text("Verifica demo riuscita") },
            text = { Text("Esito simulato per la presentazione. I risultati crittografici reali sono disponibili in Inspect.") },
            confirmButton = {
                TextButton(onClick = { showDemoSuccess = false }) { Text("Chiudi") }
            },
        )
    }
}
