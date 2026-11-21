package com.example.photoanchor

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONObject
import java.util.UUID

internal data class ActivityNotification(
    val id: String,
    val kind: String, // "zap" | "social" | "system"
    val icon: DeckIcon,
    val title: String,
    val subtitle: String,
    val time: String,
    val postId: String? = null,
)

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
    var showNotifications by remember { mutableStateOf(false) }

    var savedIds by remember {
        mutableStateOf(prefs.getStringSet("saved_pins", emptySet())?.toSet() ?: emptySet())
    }
    var zapsByPost by remember {
        mutableStateOf<Map<String, Int>>(
            runCatching {
                val raw = JSONObject(prefs.getString("zaps_map", "{}") ?: "{}")
                raw.keys().asSequence().associateWith { raw.optInt(it, 0) }
            }.getOrDefault(emptyMap()),
        )
    }
    var userNotifications by remember { mutableStateOf<List<ActivityNotification>>(emptyList()) }
    var readNotifIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun pushNotification(kind: String, icon: DeckIcon, title: String, subtitle: String, postId: String? = null) {
        val item = ActivityNotification(
            id = UUID.randomUUID().toString(),
            kind = kind,
            icon = icon,
            title = title,
            subtitle = subtitle,
            time = "now",
            postId = postId,
        )
        userNotifications = (listOf(item) + userNotifications).take(30)
    }

    fun toggleSavePin(id: String) {
        val wasSaved = id in savedIds
        val next = if (wasSaved) savedIds - id else savedIds + id
        savedIds = next
        prefs.edit().putStringSet("saved_pins", next).apply()
        if (!wasSaved) {
            val entry = state.feed.find { it.post.id == id }
            pushNotification(
                kind = "social",
                icon = DeckIcon.Heart,
                title = "Saved Pin to your collection",
                subtitle = entry?.post?.caption?.ifBlank { "Signed Pin" } ?: "Saved Pin",
                postId = id,
            )
        }
    }

    fun zapPost(id: String, amount: Int = 21) {
        val nextCount = (zapsByPost[id] ?: 0) + amount
        val nextMap = zapsByPost + (id to nextCount)
        zapsByPost = nextMap
        val json = JSONObject()
        nextMap.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString("zaps_map", json.toString()).apply()
        val entry = state.feed.find { it.post.id == id }
        val creator = entry?.name?.ifBlank { "Creator" } ?: "Creator"
        pushNotification(
            kind = "zap",
            icon = DeckIcon.Lightning,
            title = "Demo zap +$amount to $creator · no payment",
            subtitle = entry?.post?.caption?.ifBlank { "Authentic Pin" } ?: "Pin on Nostr",
            postId = id,
        )
    }

    val allNotifications = remember(userNotifications, state.feed, state.queued, state.message) {
        val systemList = buildList {
            if (state.queued > 0) {
                add(
                    ActivityNotification(
                        id = "sys-queue-${state.queued}",
                        kind = "system",
                        icon = DeckIcon.Hourglass,
                        title = "${state.queued} signed Pin(s) queued",
                        subtitle = "Waiting for local relay sync · signatures preserved",
                        time = "live",
                    ),
                )
            }
            state.feed.take(6).forEach { entry ->
                val creator = entry.name.ifBlank { "Creator" }
                add(
                    ActivityNotification(
                        id = "feed-${entry.post.id}",
                        kind = "system",
                        icon = DeckIcon.Bitcoin,
                        title = "Signed Pin by $creator",
                        subtitle = entry.post.caption.ifBlank { "Tap to inspect Bitcoin proof" },
                        time = formatShortDate(entry.post.createdAt).ifEmpty { "recent" },
                        postId = entry.post.id,
                    ),
                )
            }
        }
        userNotifications + systemList
    }

    val unreadCount = remember(allNotifications, readNotifIds) {
        allNotifications.count { it.id !in readNotifIds }
    }

    LaunchedEffect(state.ready) {
        if (state.ready && state.name.isBlank()) tab = "Profile"
    }

    LaunchedEffect(state.lastPublishedId) {
        if (state.lastPublishedId != null) tab = "Feed"
    }

    val saveBundle = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) model.export(uri)
    }
    val saveEvidence = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) model.exportEvidence(uri)
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
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
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
                            text = "PhoTO",
                            color = p.fg,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                        )
                    }

                    // Search Bar
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
                                        text = "Search pins, #tags…",
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

                    // Notification Bell with Unread Dot
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(p.soft)
                            .border(1.dp, p.bd, CircleShape)
                            .semantics { contentDescription = "Notifications" }
                            .clickable(role = Role.Button) { showNotifications = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelIcon(DeckIcon.Bell, size = 15.dp, tint = p.fg)
                        if (unreadCount > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-2).dp, y = 2.dp)
                                    .size(13.dp)
                                    .clip(CircleShape)
                                    .background(p.amb)
                                    .border(1.5.dp, p.surf, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (unreadCount > 9) "9+" else "$unreadCount",
                                    color = Color(0xFF451A03),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                    }

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
            if (BuildConfig.DEBUG) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (state.demoMode) "SIMULATED DEMO · auto-publish on capture" else "Automatic demo · off", color = p.ink, fontSize = 11.sp,
                        modifier = Modifier.weight(1f))
                    Switch(checked = state.demoMode, onCheckedChange = model::setDemoMode, enabled = !state.busy,
                        modifier = Modifier.semantics { contentDescription = "Automatic demo simulation" })
                }
            }
            Text("Local demo · Bitcoin regtest", color = p.mu, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            Text(state.message, color = p.ink, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite })
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
                        zapsByPost = zapsByPost,
                        onToggleSave = ::toggleSavePin,
                        onZap = ::zapPost,
                        saveProfile = model::saveProfile,
                        onSelectPin = model::select,
                        onOpenCamera = { tab = "Camera" },
                        evidenceControls = { EvidenceSetupPanel(state, model, onOpenCamera = { tab = "Camera" }) },
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
                            publish = { caption ->
                                model.publish(caption)
                                tab = "Feed"
                            },
                            discard = model::discard,
                            model = model,
                        )
                    } else {
                        CameraScreen(state.busy, model::captured, model::cameraError, if (state.demoMode) null else state.calibrationGuide, state.demoMode)
                    }
                    else -> FeedScreen(
                        state = state,
                        search = search,
                        filterMode = filterMode,
                        onFilterChange = { filterMode = it },
                        savedIds = savedIds,
                        zapsByPost = zapsByPost,
                        onToggleSave = ::toggleSavePin,
                        onZap = ::zapPost,
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
            zapSats = (zapsByPost[selected.id] ?: 0),
            onToggleSave = { toggleSavePin(selected.id) },
            onZap = { zapPost(selected.id, 21) },
            onVerifyAgain = model::verify,
            onDismiss = model::dismiss,
            onCheckCopy = { checkCopy.launch(arrayOf("image/jpeg")) },
            onExportBundle = { saveBundle.launch("bitpin-${selected.id.take(12)}.zip") },
            onExportEvidence = { saveEvidence.launch("photo-v3-evidence.zip") },
        )
    }

    if (showNotifications) {
        NotificationsDialog(
            notifications = allNotifications,
            readIds = readNotifIds,
            statusMessage = state.message,
            onMarkAllRead = { readNotifIds = readNotifIds + allNotifications.map { it.id } },
            onSelectNotification = { notif ->
                readNotifIds = readNotifIds + notif.id
                val target = notif.postId?.let { pid -> state.feed.find { it.post.id == pid }?.post }
                showNotifications = false
                if (target != null) model.select(target)
            },
            onDismiss = { showNotifications = false },
        )
    }
}

@Composable
private fun NotificationsDialog(
    notifications: List<ActivityNotification>,
    readIds: Set<String>,
    statusMessage: String,
    onMarkAllRead: () -> Unit,
    onSelectNotification: (ActivityNotification) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    var filter by remember { mutableStateOf("all") }
    val filtered = remember(notifications, filter) {
        if (filter == "all") notifications else notifications.filter { it.kind == filter }
    }
    val unreadTotal = remember(notifications, readIds) {
        notifications.count { it.id !in readIds }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SurfCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .widthIn(max = 500.dp),
            padding = 18.dp,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.heightIn(max = 580.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(p.fg),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelIcon(DeckIcon.Bell, size = 16.dp, tint = p.primaryFg)
                        }
                        Column {
                            Text("Activity & Notifications", color = p.ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                            Text(
                                text = if (unreadTotal > 0) "$unreadTotal new updates" else "All caught up",
                                color = p.mu,
                                fontSize = 11.5.sp,
                            )
                        }
                    }
                    if (unreadTotal > 0) {
                        Text(
                            text = "Mark read",
                            color = p.fg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = onMarkAllRead),
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChipPill("All", filter == "all", { filter = "all" }, DeckIcon.Bell)
                    FilterChipPill("Zaps", filter == "zap", { filter = "zap" }, DeckIcon.Lightning)
                    FilterChipPill("Social", filter == "social", { filter = "social" }, DeckIcon.Heart)
                    FilterChipPill("Bitcoin", filter == "system", { filter = "system" }, DeckIcon.Bitcoin)
                }

                if (statusMessage.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = p.soft,
                        border = BorderStroke(1.dp, p.bd),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PixelIcon(DeckIcon.Bitcoin, size = 14.dp, tint = p.fg)
                            Text(statusMessage, color = p.ink, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelIcon(DeckIcon.Bell, size = 26.dp, tint = p.mu)
                            Text("No notifications yet", color = p.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Double-tap for a local demo zap (no payment), or save Pins to your collection.",
                                color = p.mu,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = { it.id }) { notif ->
                            val isUnread = notif.id !in readIds
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isUnread) p.soft else p.surf,
                                border = BorderStroke(1.dp, if (isUnread) p.fg else p.bd),
                                modifier = Modifier.fillMaxWidth().clickable { onSelectNotification(notif) },
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (notif.kind == "zap") p.ambBg else p.surf)
                                            .border(1.dp, p.bd, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        PixelIcon(
                                            notif.icon,
                                            size = 15.dp,
                                            tint = if (notif.kind == "zap") p.amb else p.fg,
                                        )
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(notif.title, color = p.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(notif.subtitle, color = p.mu, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(notif.time, color = p.mu, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                BitPolitoPrimaryButton(
                    text = "Done",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
