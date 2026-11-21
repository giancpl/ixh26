package com.example.photoanchor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
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
    zapSats: Int = 0,
    onToggleSave: () -> Unit,
    onZap: () -> Unit = {},
    onVerifyAgain: () -> Unit,
    onDismiss: () -> Unit,
    onCheckCopy: () -> Unit,
    onExportBundle: () -> Unit,
    onExportEvidence: () -> Unit,
) {
    val p = LocalPalette.current
    val creatorName = entry?.name?.ifBlank { "Creator" } ?: "Creator"
    val handle = handleOf(creatorName, selected.pubkey)
    val title = selected.caption.ifBlank { "Untitled Pin" }
    val dateLabel = formatShortDate(selected.createdAt)
    val boardTag = boardForCaption(selected.caption)
    var showProofDetails by remember { mutableStateOf(false) }
    var zapBurstKey by remember { mutableIntStateOf(0) }

    val timestamp = timestampBadge(state.verification?.timestamp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 18.dp),
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                    .size(40.dp)
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        text = creatorName,
                                        color = p.ink,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    PixelIcon(DeckIcon.Shield, size = 13.dp, tint = p.fg)
                                }
                                Text(
                                    text = if (dateLabel.isNotEmpty()) "$handle · $dateLabel" else handle,
                                    color = p.mu,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }

                        BitPolitoPrimaryButton(
                            text = if (isSaved) "Saved" else "Save",
                            icon = DeckIcon.Heart,
                            onClick = onToggleSave,
                        )
                    }

                    // Large Pin Image (Double-tap to Zap +21 sats with Lightning animation)
                    entry?.preview?.let { bitmap ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF0D1124))
                                .border(1.dp, p.bd, RoundedCornerShape(18.dp))
                                .pointerInput(selected.id) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            zapBurstKey++
                                            onZap()
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp),
                                contentScale = ContentScale.Fit,
                            )

                            LightningZapBurstOverlay(burstTrigger = zapBurstKey)

                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Color.Black.copy(alpha = 0.62f))
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    PixelIcon(DeckIcon.Lightning, size = 10.dp, tint = Color(0xFFFCD34D))
                                    Text(
                                        text = "Double-tap: demo zap",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    // Pin title
                    Text(
                        text = title,
                        color = p.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        lineHeight = 23.sp,
                    )

                    // Interactive Engagement & Status Row
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = p.ambBg,
                            border = BorderStroke(1.dp, p.ambBd),
                            modifier = Modifier.clickable {
                                zapBurstKey++
                                onZap()
                            },
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                PixelIcon(DeckIcon.Lightning, size = 13.dp, tint = p.amb)
                                Text(
                                    text = "Demo zaps: $zapSats · no payment",
                                    color = p.ambText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }

                        BitPolitoBadge(
                            text = "#$boardTag",
                            kind = "default",
                            icon = DeckIcon.Bookmark,
                        )

                        BitPolitoBadge(
                            text = timestamp.label,
                            kind = timestamp.tone.badgeKind,
                            icon = DeckIcon.Bitcoin,
                        )
                    }

                    // Quick Proof Actions Row (Always easy to reach, zero clutter)
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

                    if (selected.evidenceCommitment != null) {
                        BitPolitoOutlinedButton(
                            text = "Export v3 Evidence Bundle",
                            icon = DeckIcon.Shield,
                            onClick = onExportEvidence,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
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

                    // Expandable Technical Verification Details (Hidden by default so the UI stays clean)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showProofDetails = !showProofDetails }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PixelIcon(DeckIcon.Shield, size = 14.dp, tint = p.fg)
                            Text(
                                text = "Cryptographic Verification Details",
                                color = p.ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                        Text(
                            text = if (showProofDetails) "Hide" else "Inspect",
                            color = p.fg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }

                    AnimatedVisibility(visible = showProofDetails) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            VerificationCard(
                                icon = DeckIcon.Shield,
                                title = "Nostr Schnorr Signature",
                                value = state.verification?.event ?: "Checking event…",
                            )
                            VerificationCard(
                                icon = DeckIcon.Camera,
                                title = "Original JPEG Integrity",
                                value = state.verification?.integrity ?: "Checking original bytes…",
                            )
                            VerificationCard(
                                icon = DeckIcon.Bitcoin,
                                title = "Nostr Timestamp · local regtest node",
                                value = state.verification?.timestamp ?: "Checking node…",
                            )
                            if (selected.evidenceCommitment != null) {
                                VerificationCard(
                                    icon = DeckIcon.Shield,
                                    title = "v3 Receipt · signature and attachments",
                                    value = state.verification?.receipt ?: "Checking v3 receipt…",
                                )
                                VerificationCard(
                                    icon = DeckIcon.Shield,
                                    title = "Device enrollment · local authority",
                                    value = state.verification?.device ?: "Checking enrollment…",
                                )
                                VerificationCard(
                                    icon = DeckIcon.Bitcoin,
                                    title = "v3 Timestamp · local regtest node",
                                    value = state.verification?.evidenceTimestamp ?: "Checking v3 timestamp…",
                                )
                                VerificationCard(
                                    icon = DeckIcon.Camera,
                                    title = "Origin assessment · separate from timestamp",
                                    value = state.verification?.origin ?: "Checking origin assessment…",
                                )
                            } else {
                                VerificationCard(
                                    icon = DeckIcon.Camera,
                                    title = "Origin evidence · not included",
                                    value = "N/A — standard Nostr post. No v3 receipt, device enrollment or v3 timestamp is attached. These checks are not pending and do not indicate a verification failure.",
                                )
                            }
                            Text(
                                text = "A valid signature identifies the signer. A confirmed timestamp proves existence of the signed bytes; it does not verify the scene or camera origin.",
                                color = p.mu,
                                fontSize = 11.sp,
                            )
                            VerificationCard(
                                icon = DeckIcon.Globe,
                                title = "Relay Status",
                                value = state.verification?.publication ?: "Unknown",
                            )
                            SelectionContainer {
                                Text(
                                    text = "Signer: ${selected.pubkey.take(24)}…\nEvent: ${selected.id.take(24)}…",
                                    color = p.mu,
                                    fontSize = 11.sp,
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
                            text = "Refresh Proof",
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
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PixelIcon(icon, size = 12.dp, tint = accent)
                Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
            }
            Text(value, color = p.ink, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}
