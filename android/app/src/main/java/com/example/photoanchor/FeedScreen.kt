package com.example.photoanchor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FeedScreen(
    state: PhotoAnchorState,
    search: String,
    filterMode: String,
    onFilterChange: (String) -> Unit,
    savedIds: Set<String>,
    zapsByPost: Map<String, Int> = emptyMap(),
    onToggleSave: (String) -> Unit,
    onZap: (String) -> Unit = {},
    refresh: () -> Unit,
    select: (PhotoPost) -> Unit,
    onOpenCamera: () -> Unit,
) {
    val p = LocalPalette.current
    var feedLayout by rememberSaveable { mutableStateOf("pins") } // "pins" | "stream"

    val filtered = remember(state.feed, search, filterMode, savedIds, state.pubkey) {
        state.feed.filter { entry ->
            val matchesFilter = when (filterMode) {
                "saved" -> entry.post.id in savedIds
                "mine" -> entry.post.pubkey == state.pubkey
                else -> true
            }
            if (!matchesFilter) return@filter false
            val q = search.trim().lowercase()
            if (q.isEmpty()) return@filter true
            entry.post.caption.lowercase().contains(q) ||
                entry.name.lowercase().contains(q) ||
                boardForCaption(entry.post.caption).lowercase().contains(q)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Filter Chips + Pins/Stream View Switcher + Sync
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChipPill(
                    label = "All",
                    selected = filterMode == "all",
                    onClick = { onFilterChange("all") },
                )
                FilterChipPill(
                    label = "Saved (${savedIds.size})",
                    selected = filterMode == "saved",
                    icon = DeckIcon.Heart,
                    onClick = { onFilterChange("saved") },
                )
                FilterChipPill(
                    label = "Mine",
                    selected = filterMode == "mine",
                    onClick = { onFilterChange("mine") },
                )
            }

            Spacer(Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Pinterest Masonry ("Pins") vs X/Nostr Timeline ("Stream")
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = p.soft,
                    border = BorderStroke(1.dp, p.bd),
                ) {
                    Row(
                        Modifier.padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf("pins" to "Pins", "stream" to "Stream").forEach { (key, lbl) ->
                            val active = feedLayout == key
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (active) p.fg else Color.Transparent)
                                    .clickable { feedLayout = key }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = lbl,
                                    color = if (active) p.primaryFg else p.mu,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                // Sync button
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(p.soft)
                        .border(1.dp, p.bd, CircleShape)
                        .semantics { contentDescription = "Sync feed" }
                        .clickable(role = Role.Button, onClick = refresh),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelIcon(DeckIcon.Lightning, size = 14.dp, tint = p.fg)
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                SurfCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                    padding = 28.dp,
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(p.soft)
                                .border(1.dp, p.bd, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelIcon(DeckIcon.CowHead, size = 32.dp, tint = p.fg)
                        }
                        Text(
                            text = when (filterMode) {
                                "saved" -> "No saved Pins yet"
                                "mine" -> "No Pins created yet"
                                else -> "Your visual feed is ready"
                            },
                            color = p.ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = when (filterMode) {
                                "saved" -> "Tap the heart on any Pin to bookmark it in your collection."
                                else -> "Snap a live photo with the camera to publish your first signed Pin."
                            },
                            color = p.mu,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        BitPolitoPrimaryButton(
                            text = if (filterMode == "saved") "Explore Pins" else "Snap a Pin",
                            icon = if (filterMode == "saved") DeckIcon.Globe else DeckIcon.Camera,
                            onClick = {
                                if (filterMode == "saved") onFilterChange("all") else onOpenCamera()
                            },
                        )
                    }
                }
            }
        } else if (feedLayout == "pins") {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalItemSpacing = 12.dp,
            ) {
                items(filtered, key = { it.post.id }) { entry ->
                    PinterestPinCard(
                        entry = entry,
                        isSaved = entry.post.id in savedIds,
                        zapSats = (zapsByPost[entry.post.id] ?: 0),
                        onToggleSave = { onToggleSave(entry.post.id) },
                        onZap = { onZap(entry.post.id) },
                        onOpen = { select(entry.post) },
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.post.id }) { entry ->
                    StreamPostCard(
                        entry = entry,
                        isSaved = entry.post.id in savedIds,
                        zapSats = (zapsByPost[entry.post.id] ?: 0),
                        onToggleSave = { onToggleSave(entry.post.id) },
                        onZap = { onZap(entry.post.id) },
                        onOpen = { select(entry.post) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PinterestPinCard(
    entry: FeedEntry,
    isSaved: Boolean,
    zapSats: Int = 0,
    onToggleSave: () -> Unit,
    onZap: () -> Unit = {},
    onOpen: () -> Unit,
) {
    val p = LocalPalette.current
    val creatorName = entry.name.ifBlank { "Creator" }
    val title = entry.post.caption.ifBlank { "Untitled Pin" }
    val boardTag = boardForCaption(entry.post.caption)
    var zapBurstKey by remember { mutableIntStateOf(0) }
    val ratio = remember(entry.post.width, entry.post.height) {
        if (entry.post.width > 0 && entry.post.height > 0) {
            (entry.post.width.toFloat() / entry.post.height.toFloat()).coerceIn(0.68f, 1.42f)
        } else {
            0.85f
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        color = p.surf,
        border = BorderStroke(1.dp, p.bd),
        shadowElevation = 2.dp,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .background(p.soft)
                    .pointerInput(entry.post.id) {
                        detectTapGestures(
                            onTap = { onOpen() },
                            onDoubleTap = {
                                zapBurstKey++
                                onZap()
                            },
                        )
                    },
            ) {
                entry.preview?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } ?: Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelIcon(DeckIcon.Camera, size = 28.dp, tint = p.mu)
                }

                // Double-tap animated Pixel Lightning Bolt burst
                LightningZapBurstOverlay(burstTrigger = zapBurstKey)

                // Top-left Board / Signed pill
                Box(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            PixelIcon(DeckIcon.Shield, size = 10.dp, tint = Color(0xFFFCD34D))
                            Text(
                                text = boardTag,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Top-right Save/Heart button
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isSaved) p.fg else Color.Black.copy(alpha = 0.52f))
                        .semantics {
                            contentDescription = if (isSaved) "Remove local bookmark" else "Save local bookmark"
                            stateDescription = if (isSaved) "Saved on this device" else "Not saved"
                        }
                        .clickable(role = Role.Button, onClick = onToggleSave),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelIcon(
                        icon = DeckIcon.Heart,
                        size = 13.dp,
                        tint = if (isSaved) p.primaryFg else Color.White,
                    )
                }
            }

            Column(
                Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    color = p.ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(p.fg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = initialsOf(creatorName),
                                color = p.primaryFg,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp,
                            )
                        }
                        Text(
                            text = creatorName,
                            color = p.mu,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Nostr Zap mini pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(p.soft)
                            .clickable {
                                zapBurstKey++
                                onZap()
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        PixelIcon(DeckIcon.Lightning, size = 10.dp, tint = p.amb)
                        Text(
                            text = "$zapSats",
                            color = p.fg,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamPostCard(
    entry: FeedEntry,
    isSaved: Boolean,
    zapSats: Int,
    onToggleSave: () -> Unit,
    onZap: () -> Unit,
    onOpen: () -> Unit,
) {
    val p = LocalPalette.current
    val creatorName = entry.name.ifBlank { "Creator" }
    val handle = handleOf(creatorName, entry.post.pubkey)
    val boardTag = boardForCaption(entry.post.caption)
    var zapBurstKey by remember { mutableIntStateOf(0) }

    SurfCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        padding = 14.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(creatorName, color = p.ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp)
                            PixelIcon(DeckIcon.Shield, size = 12.dp, tint = p.fg)
                        }
                        Text("$handle · ${formatShortDate(entry.post.createdAt)}", color = p.mu, fontSize = 11.5.sp)
                    }
                }
                BitPolitoBadge("#$boardTag", kind = "default", icon = DeckIcon.Bookmark)
            }

            if (entry.post.caption.isNotBlank()) {
                Text(entry.post.caption, color = p.ink, fontSize = 14.sp, lineHeight = 19.sp)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0D1124))
                    .border(1.dp, p.bd, RoundedCornerShape(16.dp))
                    .pointerInput(entry.post.id) {
                        detectTapGestures(
                            onTap = { onOpen() },
                            onDoubleTap = {
                                zapBurstKey++
                                onZap()
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                entry.preview?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = entry.post.caption,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                LightningZapBurstOverlay(burstTrigger = zapBurstKey)
            }

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
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PixelIcon(DeckIcon.Lightning, size = 12.dp, tint = p.amb)
                        Text("Demo: $zapSats", color = p.ambText, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                FilterChipPill(
                    label = if (isSaved) "Saved" else "Save",
                    selected = isSaved,
                    icon = DeckIcon.Heart,
                    onClick = onToggleSave,
                )

                FilterChipPill(
                    label = "Proof",
                    selected = false,
                    icon = DeckIcon.Shield,
                    onClick = onOpen,
                )
            }
        }
    }
}
