package com.example.photoanchor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ProfileScreen(
    state: PhotoAnchorState,
    savedIds: Set<String>,
    onToggleSave: (String) -> Unit,
    saveProfile: (String) -> Unit,
    onSelectPin: (PhotoPost) -> Unit,
    onOpenCamera: () -> Unit,
) {
    val p = LocalPalette.current
    var name by remember(state.name) { mutableStateOf(state.name) }
    var editing by remember(state.name) { mutableStateOf(state.name.isBlank()) }
    var profileTab by remember { mutableStateOf("created") } // "created" | "saved"

    val myPins = remember(state.feed, state.pubkey) {
        state.feed.filter { it.post.pubkey == state.pubkey }
    }
    val savedPins = remember(state.feed, savedIds) {
        state.feed.filter { it.post.id in savedIds }
    }
    val shownPins = if (profileTab == "saved") savedPins else myPins

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 12.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            SurfCard(modifier = Modifier.fillMaxWidth(), padding = 22.dp) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(p.fg),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.name.isNotBlank()) {
                            Text(
                                text = initialsOf(state.name),
                                color = p.primaryFg,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 28.sp,
                            )
                        } else {
                            PixelIcon(DeckIcon.CowHead, size = 36.dp, tint = p.primaryFg)
                        }
                    }

                    Text(
                        text = state.name.ifBlank { "Set up your profile" },
                        color = p.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                    )

                    // Pinterest profile stats row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileStatItem(count = "${myPins.size}", label = "Created")
                        ProfileStatItem(count = "${savedPins.size}", label = "Saved")
                        ProfileStatItem(count = "${state.feed.size}", label = "Feed")
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

                    Text("Display name declared by this signer", color = p.mu, fontSize = 12.sp)
                    Text("Public signing key", color = p.ink, fontWeight = FontWeight.Bold)
                    SelectionContainer { Text(state.pubkey, color = p.ink, fontSize = 12.sp) }
                    Text(
                        "Your private key is encrypted with Android Keystore and excluded from backups. " +
                            "There is no key recovery in this demo. Changing your name does not change old signatures.",
                        color = p.mu, fontSize = 12.sp,
                    )
                    Text("Saved Pins are bookmarks on this device, not relay likes.", color = p.mu, fontSize = 12.sp)

                    if (!editing) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BitPolitoOutlinedButton(
                                text = "Edit Profile",
                                icon = DeckIcon.Wrench,
                                onClick = { editing = true },
                            )
                            BitPolitoPrimaryButton(
                                text = "Create Pin",
                                icon = DeckIcon.Camera,
                                onClick = onOpenCamera,
                            )
                        }
                    }
                }
            }
        }

        // Created / Saved Segmented Tabs
        item(span = StaggeredGridItemSpan.FullLine) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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

        if (shownPins.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                SurfCard(modifier = Modifier.fillMaxWidth(), padding = 26.dp) {
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
                                "Tap the heart icon on any Pin in Explore to save it here."
                            } else {
                                "Capture your first photo to share it on your profile."
                            },
                            color = p.mu,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                        )
                        if (profileTab == "created") {
                            BitPolitoPrimaryButton(
                                text = "Create your first Pin",
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
                    onToggleSave = { onToggleSave(entry.post.id) },
                    onOpen = { onSelectPin(entry.post) },
                )
            }
        }

        item(span = StaggeredGridItemSpan.FullLine) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
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
}

@Composable
internal fun ProfileStatItem(count: String, label: String) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, color = p.fg, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(label, color = p.mu, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
    }
}
