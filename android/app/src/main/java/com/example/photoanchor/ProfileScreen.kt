package com.example.photoanchor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
internal fun ProfileScreen(
    state: PhotoAnchorState,
    savedIds: Set<String>,
    zapsByPost: Map<String, Int> = emptyMap(),
    onToggleSave: (String) -> Unit,
    onZap: (String) -> Unit = {},
    saveProfile: (String) -> Unit,
    onSelectPin: (PhotoPost) -> Unit,
    onOpenCamera: () -> Unit,
    evidenceControls: @Composable () -> Unit = {},
) {
    val p = LocalPalette.current
    var name by remember(state.name) { mutableStateOf(state.name) }
    var editing by remember(state.name) { mutableStateOf(state.name.isBlank()) }
    var profileTab by remember { mutableStateOf("created") } // "created" | "saved"
    var showQrModal by remember { mutableStateOf(false) }

    val myPins = remember(state.feed, state.pubkey) {
        state.feed.filter { it.post.pubkey == state.pubkey }
    }
    val savedPins = remember(state.feed, savedIds) {
        state.feed.filter { it.post.id in savedIds }
    }
    val totalSats = remember(myPins, zapsByPost) {
        myPins.sumOf { (zapsByPost[it.post.id] ?: 0) + 21 }
    }
    val shownPins = if (profileTab == "saved") savedPins else myPins
    val displayName = state.name.ifBlank { "New Creator" }
    val handle = handleOf(displayName, state.pubkey)

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 12.dp,
    ) {
        // 1. Clean X × Nostr × Pinterest Creator Hero Card AT THE TOP
        item(span = StaggeredGridItemSpan.FullLine) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = p.surf,
                border = BorderStroke(1.dp, p.bd),
                shadowElevation = 2.dp,
            ) {
                Column {
                    // X-style Royal Blue Cover Banner with BitPolito Bull pixel motif
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(82.dp)
                            .background(Color(0xFF001CE0))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(
                            Modifier.align(Alignment.TopEnd),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BitPolitoBadge("Nostr · BTC", kind = "pending", icon = DeckIcon.Bitcoin)
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .clickable { showQrModal = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                PixelIcon(DeckIcon.Qr, size = 14.dp, tint = Color.White)
                            }
                        }
                        PixelIcon(
                            DeckIcon.Bull,
                            size = 34.dp,
                            tint = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(p.fg)
                                .border(3.dp, p.surf, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.name.isNotBlank()) {
                                Text(
                                    text = initialsOf(state.name),
                                    color = p.primaryFg,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp,
                                )
                            } else {
                                PixelIcon(DeckIcon.CowHead, size = 32.dp, tint = p.primaryFg)
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = displayName,
                                    color = p.ink,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 21.sp,
                                    textAlign = TextAlign.Center,
                                )
                                PixelIcon(DeckIcon.Shield, size = 15.dp, tint = p.fg)
                            }
                            Text(
                                text = handle,
                                color = p.mu,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        // Clean Profile Stats Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileStatItem(count = "${myPins.size}", label = "Created")
                            ProfileStatItem(count = "${savedPins.size}", label = "Saved")
                            ProfileStatItem(count = "${state.feed.size}", label = "Feed")
                            ProfileStatItem(count = "$totalSats", label = "Sats")
                        }

                        AnimatedVisibility(visible = editing) {
                            Column(
                                Modifier.fillMaxWidth().padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { if (it.length <= 80) name = it },
                                    label = { Text("Display name") },
                                    placeholder = { Text("e.g. Alex · Street Photography") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (state.name.isNotBlank()) {
                                        BitPolitoOutlinedButton(
                                            text = "Cancel",
                                            onClick = {
                                                name = state.name
                                                editing = false
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    BitPolitoPrimaryButton(
                                        text = "Save Profile",
                                        icon = DeckIcon.Star,
                                        enabled = state.ready && !state.busy && name.isNotBlank(),
                                        onClick = {
                                            saveProfile(name)
                                            editing = false
                                        },
                                        modifier = Modifier.weight(1.3f),
                                    )
                                }
                            }
                        }

                        if (!editing) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BitPolitoOutlinedButton(
                                    text = "Edit",
                                    icon = DeckIcon.Wrench,
                                    onClick = { editing = true },
                                )
                                BitPolitoOutlinedButton(
                                    text = "Nostr ID",
                                    icon = DeckIcon.Qr,
                                    onClick = { showQrModal = true },
                                )
                                BitPolitoPrimaryButton(
                                    text = "New Pin",
                                    icon = DeckIcon.Camera,
                                    onClick = onOpenCamera,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Created / Saved Segmented Tabs
        item(span = StaggeredGridItemSpan.FullLine) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChipPill(
                    label = "Created (${myPins.size})",
                    selected = profileTab == "created",
                    icon = DeckIcon.Camera,
                    onClick = { profileTab = "created" },
                )
                Spacer(Modifier.width(8.dp))
                FilterChipPill(
                    label = "Saved (${savedPins.size})",
                    selected = profileTab == "saved",
                    icon = DeckIcon.Heart,
                    onClick = { profileTab = "saved" },
                )
            }
        }

        // 3. Pins Grid
        if (shownPins.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                SurfCard(modifier = Modifier.fillMaxWidth(), padding = 24.dp) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PixelIcon(
                            icon = if (profileTab == "saved") DeckIcon.Heart else DeckIcon.Camera,
                            size = 28.dp,
                            tint = p.fg,
                        )
                        Text(
                            text = if (profileTab == "saved") "No saved Pins yet" else "No created Pins yet",
                            color = p.ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = if (profileTab == "saved") {
                                "Tap the heart on any Pin to bookmark it here."
                            } else {
                                "Snap your first photo to share it on your profile."
                            },
                            color = p.mu,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                        )
                        if (profileTab == "created") {
                            BitPolitoPrimaryButton(
                                text = "Snap your first Pin",
                                icon = DeckIcon.Camera,
                                onClick = onOpenCamera,
                            )
                        }
                    }
                }
            }
        } else {
            items(shownPins, key = { it.post.id }) { entry ->
                PinterestPinCard(
                    entry = entry,
                    isSaved = entry.post.id in savedIds,
                    zapSats = (zapsByPost[entry.post.id] ?: 0) + 21,
                    onToggleSave = { onToggleSave(entry.post.id) },
                    onZap = { onZap(entry.post.id) },
                    onOpen = { onSelectPin(entry.post) },
                )
            }
        }

        // 4. Collapsible Sensor & Device Lab (keeps all V3 enrollment & PRNU calibration controls accessible without clutter)
        item(span = StaggeredGridItemSpan.FullLine) {
            evidenceControls()
        }

        item(span = StaggeredGridItemSpan.FullLine) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon(DeckIcon.Bull, size = 18.dp, tint = p.fg)
                Spacer(Modifier.width(6.dp))
                Text("Made with", color = p.mu, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                PixelIcon(DeckIcon.Heart, size = 12.dp, tint = p.fg)
                Spacer(Modifier.width(4.dp))
                Text("by BitPolito", color = p.mu, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showQrModal) {
        Dialog(onDismissRequest = { showQrModal = false }) {
            SurfCard(modifier = Modifier.fillMaxWidth().padding(14.dp), padding = 20.dp) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(p.fg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = initialsOf(displayName),
                            color = p.primaryFg,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                        )
                    }
                    Text(displayName, color = p.ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text(handle, color = p.mu, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    Box(
                        Modifier
                            .size(136.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(p.soft)
                            .border(1.5.dp, p.bd, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelIcon(DeckIcon.Qr, size = 88.dp, tint = p.fg)
                    }
                    BitPolitoBadge("Android Keystore Nostr Identity", kind = "ok", icon = DeckIcon.Shield)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = p.soft,
                        border = BorderStroke(1.dp, p.bd),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Public Signing Key", color = p.mu, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            SelectionContainer {
                                Text(
                                    text = state.pubkey,
                                    color = p.ink,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    BitPolitoPrimaryButton(
                        text = "Done",
                        onClick = { showQrModal = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProfileStatItem(count: String, label: String) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, color = p.fg, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(label, color = p.mu, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
    }
}
