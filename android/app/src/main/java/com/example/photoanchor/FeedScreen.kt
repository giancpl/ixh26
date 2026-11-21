package com.example.photoanchor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
    onToggleSave: (String) -> Unit,
    refresh: () -> Unit,
    select: (PhotoPost) -> Unit,
    onOpenCamera: () -> Unit,
) {
    val p = LocalPalette.current
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
                entry.name.lowercase().contains(q)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Pinterest Filter Chips Bar
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChipPill(
                label = "All Pins",
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
                label = "My Pins",
                selected = filterMode == "mine",
                onClick = { onFilterChange("mine") },
            )
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = p.soft,
                border = BorderStroke(1.dp, p.bd),
                modifier = Modifier.clickable(onClick = refresh),
            ) {
                Row(
                    Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PixelIcon(DeckIcon.Lightning, size = 12.dp, tint = p.fg)
                    Text("Sync", color = p.fg, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                                "mine" -> "You haven't created any Pins yet"
                                else -> "Your inspiration feed is ready"
                            },
                            color = p.ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = when (filterMode) {
                                "saved" -> "Tap the heart on any Pin to save it to your collection."
                                else -> "Publish a signed photo locally. Its regtest timestamp is checked separately."
                            },
                            color = p.mu,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        BitPolitoPrimaryButton(
                            text = if (filterMode == "saved") "Explore Pins" else "Create a Pin",
                            icon = if (filterMode == "saved") DeckIcon.Globe else DeckIcon.Camera,
                            onClick = {
                                if (filterMode == "saved") onFilterChange("all") else onOpenCamera()
                            },
                        )
                    }
                }
            }
        } else {
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
                        onToggleSave = { onToggleSave(entry.post.id) },
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
    onToggleSave: () -> Unit,
    onOpen: () -> Unit,
) {
    val p = LocalPalette.current
    val creatorName = entry.name.ifBlank { "Creator" }
    val title = entry.post.caption.ifBlank { "Untitled Pin" }
    val ratio = remember(entry.post.width, entry.post.height) {
        if (entry.post.width > 0 && entry.post.height > 0) {
            (entry.post.width.toFloat() / entry.post.height.toFloat()).coerceIn(0.65f, 1.45f)
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
                    .background(p.soft),
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

                // Only event signatures have been checked at feed construction time.
                Box(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = Color.Black.copy(alpha = 0.58f),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            PixelIcon(DeckIcon.Shield, size = 10.dp, tint = Color.White)
                            Text(
                                text = "Signed post",
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
