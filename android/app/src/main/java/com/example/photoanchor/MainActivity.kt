package com.example.photoanchor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/* ============================================================================
 * BitPolito Design System (from design/bitcoin-network-lab & design/decks)
 * ============================================================================ */

private data class BitPolitoPalette(
    val isDark: Boolean,
    val bg: Color,
    val surf: Color,
    val fg: Color,
    val ink: Color,
    val mu: Color,
    val bd: Color,
    val soft: Color,
    val primaryFg: Color,
    val ok: Color,
    val okBg: Color,
    val okBd: Color,
    val amb: Color,
    val ambBg: Color,
    val ambBd: Color,
    val ambText: Color,
    val rd: Color,
    val rdBg: Color,
    val rdBd: Color,
)

private val LightBitPolito = BitPolitoPalette(
    isDark = false,
    bg = Color(0xFFF9F9F9),
    surf = Color(0xFFFFFFFF),
    fg = Color(0xFF001CE0),
    ink = Color(0xFF202632),
    mu = Color(0xFF4155E7),
    bd = Color(0xFFC7CDF4),
    soft = Color(0xFFEAECF7),
    primaryFg = Color(0xFFFFFFFF),
    ok = Color(0xFF15803D),
    okBg = Color(0xFFEFFAF3),
    okBd = Color(0xFFBBF7D0),
    amb = Color(0xFFF59E0B),
    ambBg = Color(0xFFFFFBEB),
    ambBd = Color(0xFFFCD34D),
    ambText = Color(0xFF451A03),
    rd = Color(0xFFDC2626),
    rdBg = Color(0xFFFFF3ED),
    rdBd = Color(0xFFEECAC2),
)

private val DarkBitPolito = BitPolitoPalette(
    isDark = true,
    bg = Color(0xFF001CE0),
    surf = Color(0xFF253DE4),
    fg = Color(0xFFF9F9F9),
    ink = Color(0xFFF9F9F9),
    mu = Color(0xFFC7CDF4),
    bd = Color(0x4DF9F9F9),
    soft = Color(0xFF142EE2),
    primaryFg = Color(0xFF001CE0),
    ok = Color(0xFF86EFAC),
    okBg = Color(0x4D15803D),
    okBd = Color(0x7386EFAC),
    amb = Color(0xFFF59E0B),
    ambBg = Color(0x8C451A03),
    ambBd = Color(0x8CF59E0B),
    ambText = Color(0xFFFEF3C7),
    rd = Color(0xFFFCA5A5),
    rdBg = Color(0x4DB91C1C),
    rdBd = Color(0x73FCA5A5),
)

private val LocalPalette = staticCompositionLocalOf { LightBitPolito }

/** Official pixel-art SVG paths from design/decks/risorse grafiche/icons */
private enum class DeckIcon(val vw: Float, val vh: Float, val pathData: String) {
    CowHead(
        700f,
        560f,
        "M140 0H280V140H420V0H560V140H700V280H560V560H140V280H0V140H140V0Z",
    ),
    Bull(
        4933f,
        3147f,
        "M4475.94 233.293V10H4252.64V233.293H3806.05V10H3582.75V233.293H3359.46V10H1349.78V233.293H233.297V456.592H10V1796.37H233.297V3136.16H456.594V2019.67H679.892V1796.37H903.189V3136.16H1126.49V1573.08H1349.78V1796.37H1796.38V2019.67H2242.97V3136.16H2466.27V2242.97H2689.57V2019.67H2912.86V3136.16H3136.16V2019.67H3359.46V1796.37H3582.75V1349.78H4475.94V679.89H4699.24V456.592H4922.53V233.293H4475.94ZM3582.75 903.184H3359.46V679.89H3136.16V456.592H3359.46V679.89H3582.75V903.184Z",
    ),
    Globe(
        1540f,
        1540f,
        "M420 0V140H280V280H140V420H0V1120H140V1260H280V1400H420V1540H1120V1400H1260V1260H1400V1120H1540V420H1400V280H1260V140H1120V0H420ZM1120 700H980V840H840V1120H980V1260H1260V1120H1400V420H1260V280H1120V140H700V280H560V560H700V700H840V560H1120V700ZM1120 700V840H1260V700H1120ZM420 1400V1260H280V1120H560V1400H420ZM140 420H420V560H280V840H140V420Z",
    ),
    Camera(
        1400f,
        1400f,
        "M1120 0H280V140H140V280H0V1120H140V1260H280V1400H1120V1260H1260L1260 1120H1400V280H1260V140H1120V0ZM1120 280V140H280V280H140V1120H280V1260H1120V1120H1260L1260 280H1120ZM980 420V280H1120V420H980ZM980 560V420H840V280H560V420H420V560H280V840H420V980H560V1120H840V980H980V840H1120V560H980ZM980 560V840H840V980H560V840H420V560H560V420H840V560H980Z",
    ),
    User(
        1540f,
        1540f,
        "M420 0V140H280V560H140V840H280V980H420V1120H560V1260H280V1400H0V1540H700V1400H560V1260H980V1400H840V1540H1540V1400H1260V1260H980V1120H1120V980H1260V840H1400V560H1260V140H1120V0H420ZM980 280V140H1120V280H980ZM420 420H840V280H980V420H1120V980H980V1120H560V980H420V420Z",
    ),
    Bitcoin(
        1260f,
        1540f,
        "M700 0H560V140H280V280H420V420H280V840H140V1260H0V1400H280V1540H420V1400H560V1540H700V1400H980V1260H1120V840H980V700H1120V560H1260V280H1120V140H980V0H840V140H700V0ZM980 560H840V700H560V420H700V280H980V560ZM840 1120H700V1260H420V980H560V840H840V1120Z",
    ),
    Shield(
        980f,
        1400f,
        "M840 0H140V140H0V980H140V1120H280V1260H420V1400H560V1260H700V1120H840V980H980V140H840V0ZM490 630V140H140V630H490ZM700 1120V980H840V630H490V1120H700Z",
    ),
    Bookmark(
        980f,
        1120f,
        "M0 0H980V1120H840V980H700V840H560V700H420V840H280V980H140L140 1120H0V0Z",
    ),
    Hourglass(
        1120f,
        1260f,
        "M0 0L0 140L140 140L140 420L280 420V560H420L420 700H280V840H140L140 1120H0L0 1260L1120 1260V1120L980 1120V840H840V700H700V560H840V420H980V140L1120 140V0L0 0ZM840 420L700 420V560H420L420 420H280L280 140L840 140V420ZM700 840V700H420V840H280L280 980H420V840H700ZM840 980V840H700V980H840Z",
    ),
    Lightning(
        700f,
        980f,
        "M420 0H560V140H420V0ZM280 840H420V700H560V560H700V420H420V140H280V280H140V420H0V560H280V840ZM280 840V980H140V840H280Z",
    ),
    Warning(
        1540f,
        1540f,
        "M840 0H700V140H560V420H420V700H280L280 980H140V1260H0V1400H140V1540H1400V1400H1540V1260H1400V980H1260L1260 700H1120V420H980V140H840V0ZM840 140V420H980V700H1120V980H1260V1260H1400V1400H140V1260H280V980H420V700H560V420H700V140H840ZM840 560H700V980H840V560ZM840 1120H700V1260H840V1120Z",
    ),
    Heart(
        1260f,
        1120f,
        "M140 0V140H0V560H140V700H280V840H420V980H560V1120H700V980H840V840H980V700H1120V560H1260V140H1120V0H840V140H700V280H560V140H420V0H140ZM140 560V140H280V560H140Z",
    ),
    Star(
        1540f,
        1400f,
        "M700 0H840V280H700V0ZM700 560V280H560V560H0V700H280V840H420V1120H280V1400H420V1260H560V1120H700V980H840V1120H980V1260H1120V1400H1260V1120H1120V840H1260V700H1540V560H980V280H840V560H700ZM700 560H560V700H700V560Z",
    ),
    Sun(
        1260f,
        1260f,
        "M560 0H700V280H560V0ZM140 140H280V280H140V140ZM420 420H280V280H420V420ZM840 420H420V840H280V980H140V1120H280V980H420V840H840V980H980V1120H1120V980H980V840H840V420ZM980 280V420H840V280H980ZM980 280V140H1120V280H980ZM0 560H280V700H0V560ZM700 980H560V1260H700V980ZM980 560V700H1260V560H980Z",
    ),
    Moon(
        840f,
        980f,
        "M840 0H280V140H140V280H0V700H140V840H280V980H840V840H560V700H420V280H560V140H840V0Z",
    ),
    Smartphone(
        980f,
        1540f,
        "M840 0H140V140H0V1400H140V1540H840V1400H980V140H840V0ZM840 280H140V1120H840V280ZM420 1400V1260H560V1400H420Z",
    ),
    Wrench(
        700f,
        1680f,
        "M560 0V140H700V560H560V1540H420V1400H280V560H420V280H0V140H140V0H560ZM280 560H0V420H280V560ZM420 1540V1680H280V1540H420Z",
    ),
}

@Composable
private fun PixelIcon(
    icon: DeckIcon,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = LocalPalette.current.fg,
) {
    val composePath = remember(icon) {
        PathParser().parsePathString(icon.pathData).toPath().apply {
            fillType = PathFillType.EvenOdd
        }
    }
    Canvas(modifier.size(size)) {
        val sx = this.size.width / icon.vw
        val sy = this.size.height / icon.vh
        val s = minOf(sx, sy)
        val dx = (this.size.width - icon.vw * s) / 2f
        val dy = (this.size.height - icon.vh * s) / 2f
        withTransform({
            translate(dx, dy)
            scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) {
            drawPath(composePath, color = tint)
        }
    }
}

@Composable
private fun SurfCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Surface(
        modifier = modifier,
        shape = shape,
        color = p.surf,
        border = BorderStroke(1.dp, p.bd),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
private fun BitPolitoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pill: Boolean = true,
    icon: DeckIcon? = null,
) {
    val p = LocalPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(if (pill) 99.dp else 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = p.fg,
            contentColor = p.primaryFg,
            disabledContainerColor = p.fg.copy(alpha = 0.38f),
            disabledContentColor = p.primaryFg.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            PixelIcon(icon, size = 15.dp, tint = p.primaryFg)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
    }
}

@Composable
private fun BitPolitoOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pill: Boolean = true,
    icon: DeckIcon? = null,
) {
    val p = LocalPalette.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 42.dp),
        shape = RoundedCornerShape(if (pill) 99.dp else 12.dp),
        border = BorderStroke(1.5.dp, p.bd),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = p.surf,
            contentColor = p.ink,
            disabledContainerColor = p.surf.copy(alpha = 0.5f),
            disabledContentColor = p.mu.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            PixelIcon(icon, size = 14.dp, tint = p.fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun BitPolitoBadge(
    text: String,
    kind: String = "default", // "default" | "solid" | "ok" | "pending" | "err"
    icon: DeckIcon? = null,
) {
    val p = LocalPalette.current
    val (bg, bd, fg) = when (kind) {
        "solid" -> Triple(p.fg, p.fg, p.primaryFg)
        "ok" -> Triple(p.okBg, p.okBd, p.ok)
        "pending" -> Triple(p.ambBg, p.ambBd, p.ambText)
        "err" -> Triple(p.rdBg, p.rdBd, p.rd)
        else -> Triple(p.soft, p.bd, p.fg)
    }
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = bg,
        border = BorderStroke(1.dp, bd),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                PixelIcon(icon, size = 11.dp, tint = fg)
            }
            Text(
                text = text,
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: DeckIcon? = null,
) {
    val p = LocalPalette.current
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = if (selected) p.fg else p.surf,
        border = BorderStroke(1.dp, if (selected) p.fg else p.bd),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                PixelIcon(icon, size = 12.dp, tint = if (selected) p.primaryFg else p.fg)
            }
            Text(
                text = label,
                color = if (selected) p.primaryFg else p.ink,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Theme toggle switch matching #theme in design/bitcoin-network-lab/style.css */
@Composable
private fun BitPolitoThemeSwitch(isDark: Boolean, onToggle: () -> Unit) {
    val p = LocalPalette.current
    val knobOffset by animateDpAsState(if (isDark) 22.dp else 0.dp, label = "themeKnob")
    Box(
        Modifier
            .width(54.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(p.surf)
            .border(1.dp, p.bd, RoundedCornerShape(99.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(24.dp)
                .clip(CircleShape)
                .background(p.fg),
            contentAlignment = Alignment.Center,
        ) {
            PixelIcon(
                icon = if (isDark) DeckIcon.Moon else DeckIcon.Sun,
                size = 13.dp,
                tint = p.primaryFg,
            )
        }
    }
}

private fun initialsOf(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "BP" }

private fun formatShortDate(epochSeconds: Long): String =
    runCatching {
        SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(epochSeconds * 1000L))
    }.getOrDefault("")

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
private fun PhotoAnchorApp(
    isDark: Boolean = false,
    onToggleTheme: () -> Unit = {},
    model: PhotoAnchorModel = viewModel(),
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bitpolito_ui", Context.MODE_PRIVATE) }
    val state by model.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("Feed") }
    var search by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("all") } // "all" | "saved" | "mine"
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
                            .clickable(enabled = state.ready) { tab = "Profile" },
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
                            publish = { caption ->
                                model.publish(caption)
                                tab = "Feed"
                            },
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

/* ============================================================================
 * Explore / Pinterest Masonry Feed
 * ============================================================================ */

@Composable
private fun FeedScreen(
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
            Spacer(Modifier.weight(1f))
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
                                else -> "Snap a photo to create a Pin anchored on Bitcoin and shared with the community."
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
private fun PinterestPinCard(
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

                // Top-left Bitcoin badge
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
                            PixelIcon(DeckIcon.Bitcoin, size = 10.dp, tint = Color(0xFFFCD34D))
                            Text(
                                text = "Pinned",
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
                        .clickable(onClick = onToggleSave),
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

/* ============================================================================
 * Pinterest Profile Screen (Creator Header + Boards/Pins Grid)
 * ============================================================================ */

@Composable
private fun ProfileScreen(
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
private fun ProfileStatItem(count: String, label: String) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, color = p.fg, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(label, color = p.mu, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
    }
}

/* ============================================================================
 * Create Pin Flow (Onboarding, Camera, Publish)
 * ============================================================================ */

@Composable
private fun OnboardingNameCard(
    state: PhotoAnchorState,
    onSaveName: (String) -> Unit,
) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf("") }
    Box(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        SurfCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
            padding = 24.dp,
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(p.soft)
                        .border(1.dp, p.bd, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelIcon(DeckIcon.CowHead, size = 28.dp, tint = p.fg)
                }
                Text(
                    "Welcome to BitPin",
                    color = p.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Choose your creator name before snapping your first Pin.",
                    color = p.mu,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    label = { Text("Your display name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                BitPolitoPrimaryButton(
                    text = "Continue to Camera",
                    icon = DeckIcon.Camera,
                    enabled = state.ready && !state.busy && name.isNotBlank(),
                    onClick = { onSaveName(name) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PublishScreen(
    state: PhotoAnchorState,
    publish: (String) -> Unit,
    discard: () -> Unit,
) {
    val p = LocalPalette.current
    var caption by remember(state.captured) { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SurfCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp),
            padding = 20.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Create Pin",
                        color = p.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                    )
                    BitPolitoBadge("Bitcoin Anchored", kind = "ok", icon = DeckIcon.Bitcoin)
                }

                state.preview?.let {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0D1124))
                            .border(1.dp, p.bd, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Captured Pin",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 1000) caption = it },
                    label = { Text("Title or description") },
                    placeholder = { Text("Tell everyone what your Pin is about…") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BitPolitoOutlinedButton(
                        text = "Retake",
                        icon = DeckIcon.Camera,
                        onClick = discard,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    )
                    BitPolitoPrimaryButton(
                        text = if (state.busy) "Publishing…" else "Publish Pin",
                        icon = DeckIcon.Star,
                        onClick = { publish(caption) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1.3f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraScreen(busy: Boolean, onCaptured: (File) -> Unit, onError: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permitted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    var back by remember { mutableStateOf(true) }
    var flash by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    val capture = remember { ImageCapture.Builder().build() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    LaunchedEffect(Unit) { if (!permitted) permission.launch(Manifest.permission.CAMERA) }

    if (!permitted) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            SurfCard(
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                padding = 24.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PixelIcon(DeckIcon.Camera, size = 32.dp, tint = p.fg)
                    Text(
                        "Camera Access Needed",
                        color = p.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Allow camera access to snap and publish authentic Pins.",
                        color = p.mu,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    BitPolitoPrimaryButton(
                        text = "Allow Camera",
                        icon = DeckIcon.Camera,
                        onClick = { permission.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        return
    }

    DisposableEffect(owner, back) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (!disposed) runCatching {
                provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider!!.unbindAll()
                provider!!.bindToLifecycle(
                    owner,
                    if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    capture,
                )
            }.onFailure { onError() }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; provider?.unbindAll() }
    }
    capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF05070E))
                .border(2.dp, p.bd, RoundedCornerShape(24.dp)),
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BitPolitoBadge("New Pin", kind = "solid", icon = DeckIcon.Camera)
                BitPolitoBadge(
                    text = if (flash) "Flash On" else "Flash Off",
                    kind = if (flash) "pending" else "default",
                    icon = DeckIcon.Lightning,
                )
            }
        }

        SurfCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BitPolitoOutlinedButton(
                    text = "Flip",
                    icon = DeckIcon.Smartphone,
                    onClick = { back = !back },
                    enabled = !capturing && !busy,
                )

                Box(
                    Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .border(4.dp, p.fg, CircleShape)
                        .clickable(enabled = !capturing && !busy) {
                            capturing = true
                            val file = File(context.filesDir, "capture-${UUID.randomUUID()}.jpg")
                            capture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        ContextCompat.getMainExecutor(context).execute {
                                            capturing = false
                                            onCaptured(file)
                                        }
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        ContextCompat.getMainExecutor(context).execute {
                                            capturing = false
                                            onError()
                                        }
                                    }
                                },
                            )
                        }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(if (capturing || busy) p.mu else p.fg),
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelIcon(DeckIcon.Camera, size = 22.dp, tint = p.primaryFg)
                    }
                }

                BitPolitoOutlinedButton(
                    text = if (flash) "Flash On" else "Flash",
                    icon = DeckIcon.Lightning,
                    onClick = { flash = !flash },
                    enabled = !capturing && !busy,
                )
            }
        }
    }
}

/* ============================================================================
 * Pinterest Pin Lightbox Detail Dialog
 * ============================================================================ */

@Composable
private fun PinDetailDialog(
    selected: PhotoPost,
    entry: FeedEntry?,
    state: PhotoAnchorState,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onVerifyAgain: () -> Unit,
    onDismiss: () -> Unit,
    onCheckCopy: () -> Unit,
    onExportBundle: () -> Unit,
) {
    val p = LocalPalette.current
    val creatorName = entry?.name?.ifBlank { "Creator" } ?: "Creator"
    val title = selected.caption.ifBlank { "Untitled Pin" }
    val dateLabel = formatShortDate(selected.createdAt)
    var showProofDetails by remember { mutableStateOf(false) }

    val tsUpper = (state.verification?.timestamp ?: "").uppercase()
    val (anchorLabel, anchorKind) = when {
        tsUpper.startsWith("CONFIRMED") -> "Anchored on Bitcoin" to "ok"
        tsUpper.startsWith("PENDING") -> "Bitcoin Anchor Pending" to "pending"
        tsUpper.startsWith("INVALID") -> "Invalid Proof" to "err"
        else -> "Verified Pin" to "default"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SurfCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .widthIn(max = 520.dp),
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
                                    text = "$dateLabel · ${selected.width}×${selected.height}",
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
                        text = anchorLabel,
                        kind = anchorKind,
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

                AnimatedVisibility(visible = showProofDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VerificationCard(
                            icon = DeckIcon.Shield,
                            title = "Authentic Capture",
                            value = state.verification?.integrity ?: "Verifying photo…",
                        )
                        VerificationCard(
                            icon = DeckIcon.Bitcoin,
                            title = "Bitcoin Timestamp",
                            value = state.verification?.timestamp ?: "Checking Bitcoin status…",
                        )
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
                            val copyOk = copyMsg.startsWith("VALID")
                            BitPolitoBadge(
                                text = if (copyOk) "Photo matches original Pin" else "Photo does not match",
                                kind = if (copyOk) "ok" else "err",
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

@Composable
private fun VerificationCard(icon: DeckIcon, title: String, value: String) {
    val p = LocalPalette.current
    val upper = value.uppercase()
    val (bg, bd, accent) = when {
        upper.startsWith("VALID") || upper.startsWith("CONFIRMED") || upper == "PUBLISHED" ->
            Triple(p.okBg, p.okBd, p.ok)
        upper.startsWith("INVALID") || upper.startsWith("ERROR") ->
            Triple(p.rdBg, p.rdBd, p.rd)
        else ->
            Triple(p.soft, p.bd, p.fg)
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
