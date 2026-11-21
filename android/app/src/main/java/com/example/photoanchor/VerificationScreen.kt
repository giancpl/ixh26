package com.example.photoanchor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
internal fun PinDetailDialog(
    selected: PhotoPost,
    entry: FeedEntry?,
    state: PhotoAnchorState,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onVerifyAgain: () -> Unit,
    onDismiss: () -> Unit,
    onCheckCopy: () -> Unit,
    onExportBundle: () -> Unit,
    onExportEvidence: () -> Unit,
) {
    val p = LocalPalette.current
    val creatorName = entry?.name?.ifBlank { "Creator" } ?: "Creator"
    val title = selected.caption.ifBlank { "Untitled Pin" }
    val dateLabel = formatShortDate(selected.createdAt)
    var showProofDetails by remember { mutableStateOf(false) }

    val timestamp = timestampBadge(state.verification?.timestamp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            // Android 17 can retain platform fitting despite decorFits=false.
            // Let Compose handle system insets once, inside the full window.
            if (android.os.Build.VERSION.SDK_INT >= 30 && dialogWindow != null) {
                val attributes = dialogWindow.attributes
                val cutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                if (attributes.fitInsetsTypes != 0 || attributes.layoutInDisplayCutoutMode != cutoutMode) {
                    attributes.setFitInsetsTypes(0)
                    attributes.layoutInDisplayCutoutMode = cutoutMode
                    dialogWindow.attributes = attributes
                }
            }
        }
        // Bound the scrolling card to the usable window. A wrap-content dialog
        // can otherwise place its final actions beneath gesture navigation.
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            SurfCard(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                padding = 18.dp,
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Top creator bar + Save button
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(p.fg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = initialsOf(creatorName),
                                    color = p.primaryFg,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                )
                            }
                            Column {
                                Text(
                                    text = creatorName,
                                    color = p.ink,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (dateLabel.isNotEmpty()) {
                                    Text(
                                        text = "Posted $dateLabel (declared) · ${selected.width}×${selected.height}",
                                        color = p.mu,
                                        fontSize = 11.5.sp,
                                    )
                                }
                            }
                        }

                        BitPolitoPrimaryButton(
                            text = if (isSaved) "Saved" else "Save",
                            icon = DeckIcon.Heart,
                            onClick = onToggleSave,
                        )
                    }

                    // Large Pin Image
                    entry?.preview?.let { bitmap ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF0D1124))
                                .border(1.dp, p.bd, RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 380.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }

                    // Pin title & status badge
                    Text(
                        text = title,
                        color = p.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BitPolitoBadge(
                            text = timestamp.label,
                            kind = timestamp.tone.badgeKind,
                            icon = DeckIcon.Bitcoin,
                        )
                        Text(
                            text = if (showProofDetails) "Hide proof details" else "Proof & Actions",
                            color = p.fg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showProofDetails = !showProofDetails },
                        )
                    }

                    VerificationCard(
                        icon = DeckIcon.Shield,
                        title = "Event ID and Schnorr signature · on this device",
                        value = state.verification?.event ?: "Checking event…",
                    )
                    VerificationCard(
                        icon = DeckIcon.Camera,
                        title = "Original JPEG integrity · on this device",
                        value = state.verification?.integrity ?: "Checking original bytes…",
                    )
                    VerificationCard(
                        icon = DeckIcon.Bitcoin,
                        title = "Timestamp · configured local node · regtest",
                        value = state.verification?.timestamp ?: "Requesting a fresh local node check…",
                    )
                    VerificationCard(icon = DeckIcon.Shield, title = "P-256 v3 receipt · on this device", value = state.verification?.receipt ?: "Not checked")
                    VerificationCard(icon = DeckIcon.Shield, title = "Device enrollment · local authority", value = state.verification?.device ?: "Not checked")
                    VerificationCard(icon = DeckIcon.Camera, title = "Origin · local server analysis", value = state.verification?.origin ?: "Origin not verified")
                    VerificationCard(icon = DeckIcon.Bitcoin, title = "v3 timestamp · local node · regtest", value = state.verification?.evidenceTimestamp ?: "No v3 timestamp")
                    VerificationCard(
                        icon = DeckIcon.Globe,
                        title = "Relay publication · independent of timestamp",
                        value = state.verification?.publication ?: "Unknown",
                    )
                    Text(
                        "Regtest is a local development blockchain, not a mainnet anchor. Names, captions " +
                            "and posted times are author declarations. Signatures and timestamps do not " +
                            "establish scene authenticity or capture time.",
                        color = p.mu, fontSize = 12.sp,
                    )
                    SelectionContainer {
                        Text("Signer: ${selected.pubkey}\nEvent: ${selected.id}", color = p.ink, fontSize = 12.sp)
                    }
                    if (selected.evidenceCommitment != null) {
                        BitPolitoOutlinedButton(text = "Export v3 evidence separately", onClick = onExportEvidence, enabled = !state.busy)
                    }
                    AnimatedVisibility(visible = showProofDetails) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                BitPolitoOutlinedButton(
                                    text = "Compare Photo",
                                    icon = DeckIcon.Camera,
                                    onClick = onCheckCopy,
                                    enabled = !state.busy,
                                    modifier = Modifier.weight(1f),
                                )
                                BitPolitoOutlinedButton(
                                    text = "Export Proof",
                                    icon = DeckIcon.Bookmark,
                                    onClick = onExportBundle,
                                    enabled = !state.busy,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            state.copyResult?.let { copyMsg ->
                                val copyOk = verificationTone(copyMsg) == VerificationTone.SUCCESS
                                BitPolitoBadge(
                                    text = copyMsg,
                                    kind = verificationTone(copyMsg).badgeKind,
                                    icon = if (copyOk) DeckIcon.Star else DeckIcon.Warning,
                                )
                            }
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = p.bd)

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BitPolitoOutlinedButton(
                            text = "Close",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                        BitPolitoPrimaryButton(
                            text = "Refresh Status",
                            icon = DeckIcon.Lightning,
                            onClick = onVerifyAgain,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun VerificationCard(icon: DeckIcon, title: String, value: String) {
    val p = LocalPalette.current
    val (bg, bd, accent) = when (verificationTone(value)) {
        VerificationTone.SUCCESS -> Triple(p.okBg, p.okBd, p.ok)
        VerificationTone.FAILURE -> Triple(p.rdBg, p.rdBd, p.rd)
        VerificationTone.PENDING -> Triple(p.ambBg, p.ambBd, p.ambText)
        VerificationTone.NEUTRAL -> Triple(p.soft, p.bd, p.fg)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, bd),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PixelIcon(icon, size = 13.dp, tint = accent)
                Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text(value, color = p.ink, fontSize = 11.5.sp, lineHeight = 15.sp)
        }
    }
}
