package com.example.photoanchor

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("bitpolito_ui", Context.MODE_PRIVATE) }
            var isDark by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
            val palette = if (isDark) DarkBitPolito else LightBitPolito

            val materialScheme = if (isDark) {
                darkColorScheme(
                    primary = palette.fg,
                    onPrimary = palette.primaryFg,
                    background = palette.bg,
                    onBackground = palette.ink,
                    surface = palette.surf,
                    onSurface = palette.ink,
                    outline = palette.bd,
                )
            } else {
                lightColorScheme(
                    primary = palette.fg,
                    onPrimary = palette.primaryFg,
                    background = palette.bg,
                    onBackground = palette.ink,
                    surface = palette.surf,
                    onSurface = palette.ink,
                    outline = palette.bd,
                )
            }

            CompositionLocalProvider(LocalPalette provides palette) {
                MaterialTheme(colorScheme = materialScheme) {
                    PhotoAnchorApp(
                        isDark = isDark,
                        onToggleTheme = {
                            isDark = !isDark
                            prefs.edit().putBoolean("dark_mode", isDark).apply()
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PhotoAnchorApp(
    isDark: Boolean = false,
    onToggleTheme: () -> Unit = {},
    model: PhotoAnchorModel = viewModel(),
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bitpolito_ui", Context.MODE_PRIVATE) }
    val state by model.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Feed") }
    var search by rememberSaveable { mutableStateOf("") }
    var filterMode by rememberSaveable { mutableStateOf("all") } // "all" | "saved" | "mine"
    var savedIds by remember {
        mutableStateOf(prefs.getStringSet("saved_pins", emptySet())?.toSet() ?: emptySet())
    }

    fun toggleSavePin(id: String) {
        val next = if (id in savedIds) savedIds - id else savedIds + id
        savedIds = next
        prefs.edit().putStringSet("saved_pins", next).apply()
    }

    LaunchedEffect(state.ready) {
        if (state.ready && state.name.isBlank()) tab = "Profile"
    }

    val saveBundle = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) model.export(uri)
    }
    val checkCopy = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.verifyCopy(uri)
    }

    Scaffold(
        containerColor = p.bg,
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(p.surf)
                    .statusBarsPadding(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Brand logo
                    Row(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { if (state.ready) tab = "Feed" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(p.fg),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelIcon(DeckIcon.CowHead, size = 19.dp, tint = p.primaryFg)
                        }
                        Text(
                            text = "BitPin",
                            color = p.fg,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                        )
                    }

                    // Pinterest-style rounded search bar
                    Surface(
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(99.dp),
                        color = p.soft,
                        border = BorderStroke(1.dp, p.bd),
                    ) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PixelIcon(DeckIcon.Globe, size = 13.dp, tint = p.mu)
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (search.isEmpty()) {
                                    Text(
                                        text = "Search pins, creators…",
                                        color = p.mu,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = search,
                                    onValueChange = {
                                        search = it
                                        if (tab != "Feed") tab = "Feed"
                                    },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = p.ink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (search.isNotEmpty()) {
                                Text(
                                    text = "Clear",
                                    color = p.fg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { search = "" },
                                )
                            }
                        }
                    }

                    BitPolitoThemeSwitch(isDark = isDark, onToggle = onToggleTheme)

                    // Profile Avatar
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(p.fg)
                            .semantics { contentDescription = "Open your profile" }
                            .clickable(enabled = state.ready, role = Role.Button) { tab = "Profile" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.name.isNotBlank()) {
                            Text(
                                text = initialsOf(state.name),
                                color = p.primaryFg,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                            )
                        } else {
                            PixelIcon(DeckIcon.User, size = 16.dp, tint = p.primaryFg)
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = p.bd)
            }
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(p.surf)
                    .navigationBarsPadding(),
            ) {
                HorizontalDivider(thickness = 1.dp, color = p.bd)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val items = listOf(
                        Triple("Feed", "Explore", DeckIcon.Globe),
                        Triple("Camera", "Create", DeckIcon.Camera),
                        Triple("Profile", "Profile", DeckIcon.User),
                    )
                    items.forEach { (key, label, icon) ->
                        val selected = tab == key
                        val bg = if (selected) p.fg else if (key == "Camera") p.soft else Color.Transparent
                        val fg = if (selected) p.primaryFg else if (key == "Camera") p.fg else p.mu
                        Box(
                            Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(bg)
                                .then(
                                    if (!selected && key == "Camera") {
                                        Modifier.border(1.dp, p.bd, RoundedCornerShape(99.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable(enabled = state.ready) { tab = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                PixelIcon(icon, size = 16.dp, tint = fg)
                                Text(
                                    text = label,
                                    color = fg,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(p.bg)
                .padding(padding),
        ) {
            Text(
                text = "Local demo · Bitcoin regtest",
                color = p.fg,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            Text(
                text = state.message,
                color = p.ink,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (state.queued > 0) {
                Text(
                    text = "${state.queued} signed event(s) queued · retries preserve the signature",
                    color = p.mu,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
            if (state.busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = p.fg,
                    trackColor = p.soft,
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    "Profile" -> ProfileScreen(
                        state = state,
                        savedIds = savedIds,
                        onToggleSave = ::toggleSavePin,
                        saveProfile = model::saveProfile,
                        onSelectPin = model::select,
                        onOpenCamera = { tab = "Camera" },
                    )
                    "Camera" -> if (state.name.isBlank()) {
                        OnboardingNameCard(
                            state = state,
                            onSaveName = { newName ->
                                model.saveProfile(newName)
                            },
                        )
                    } else if (state.captured != null) {
                        PublishScreen(
                            state = state,
                            publish = model::publish,
                            discard = model::discard,
                        )
                    } else {
                        CameraScreen(state.busy, model::captured, model::cameraError)
                    }
                    else -> FeedScreen(
                        state = state,
                        search = search,
                        filterMode = filterMode,
                        onFilterChange = { filterMode = it },
                        savedIds = savedIds,
                        onToggleSave = ::toggleSavePin,
                        refresh = model::refresh,
                        select = model::select,
                        onOpenCamera = { tab = "Camera" },
                    )
                }
            }
        }
    }

    state.selected?.let { selected ->
        val selectedEntry = state.feed.find { it.post.id == selected.id }
        PinDetailDialog(
            selected = selected,
            entry = selectedEntry,
            state = state,
            isSaved = selected.id in savedIds,
            onToggleSave = { toggleSavePin(selected.id) },
            onVerifyAgain = model::verify,
            onDismiss = model::dismiss,
            onCheckCopy = { checkCopy.launch(arrayOf("image/jpeg")) },
            onExportBundle = { saveBundle.launch("bitpin-${selected.id.take(12)}.zip") },
        )
    }
}
