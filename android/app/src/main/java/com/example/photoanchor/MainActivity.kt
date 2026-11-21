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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
    Key(
        1540f,
        700f,
        "M140 0H560V140H700V280H1400V420H1260V560H1120V420H980V560H840V420H700V560H560V700H140V560H0V140H140V0ZM700 560H840V700H700V560ZM980 560H1120V700H980V560ZM1400 560H1540V420H1400V560ZM1400 560H1260V700H1400V560ZM280 420V280H140V420H280Z",
    ),
    Lock(
        980f,
        1400f,
        "M280 0H700V140H280V0ZM840 1260V1400H140V1260H0V560H140V140H280V560H700V140H840V560H980V1260H840ZM700 840H420V1120H560V980H700V840Z",
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
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
    icon: DeckIcon? = null,
) {
    val p = LocalPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 46.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = p.fg,
            contentColor = p.primaryFg,
            disabledContainerColor = p.fg.copy(alpha = 0.38f),
            disabledContentColor = p.primaryFg.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            PixelIcon(icon, size = 16.dp, tint = p.primaryFg)
            Spacer(Modifier.width(8.dp))
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
    icon: DeckIcon? = null,
) {
    val p = LocalPalette.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, p.bd),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = p.surf,
            contentColor = p.ink,
            disabledContainerColor = p.surf.copy(alpha = 0.5f),
            disabledContentColor = p.mu.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    ) {
        if (icon != null) {
            PixelIcon(icon, size = 15.dp, tint = p.fg)
            Spacer(Modifier.width(8.dp))
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
                PixelIcon(icon, size = 12.dp, tint = fg)
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

/** Theme toggle switch matching #theme in design/bitcoin-network-lab/style.css */
@Composable
private fun BitPolitoThemeSwitch(isDark: Boolean, onToggle: () -> Unit) {
    val p = LocalPalette.current
    val knobOffset by animateDpAsState(if (isDark) 24.dp else 0.dp, label = "themeKnob")
    Box(
        Modifier
            .width(56.dp)
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
    val state by model.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("Feed") }
    LaunchedEffect(state.ready) { if (state.ready && state.name.isBlank()) tab = "Profile" }
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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Brand link (.brand from design/bitcoin-network-lab)
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { if (state.ready) tab = "Feed" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(p.soft)
                                .border(1.dp, p.bd, RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelIcon(DeckIcon.CowHead, size = 22.dp, tint = p.fg)
                        }
                        Column {
                            Text(
                                text = "BitPin · PhotoAnchor",
                                color = p.fg,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "BitPolito · Nostr & Bitcoin regtest",
                                color = p.mu,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Right actions: queued badge + theme switch + avatar chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.queued > 0) {
                            BitPolitoBadge(
                                text = "${state.queued} in coda",
                                kind = "pending",
                                icon = DeckIcon.Hourglass,
                            )
                        }
                        BitPolitoThemeSwitch(isDark = isDark, onToggle = onToggleTheme)
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
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val items = listOf(
                        Triple("Feed", "Esplora", DeckIcon.Globe),
                        Triple("Camera", "Scatta", DeckIcon.Camera),
                        Triple("Profile", "Profilo", DeckIcon.User),
                    )
                    items.forEach { (key, label, icon) ->
                        val selected = tab == key
                        val bg = if (selected) p.fg else if (key == "Camera") p.soft else Color.Transparent
                        val fg = if (selected) p.primaryFg else if (key == "Camera") p.fg else p.mu
                        Box(
                            Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .then(
                                    if (!selected && key == "Camera") {
                                        Modifier.border(1.dp, p.bd, RoundedCornerShape(12.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable(enabled = state.ready) { tab = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                PixelIcon(icon, size = 18.dp, tint = fg)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = label,
                                    color = fg,
                                    fontSize = 11.sp,
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

            // Status bar pill (.chip from design/bitcoin-network-lab)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val isUnavailable = state.message.contains("unavailable", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isUnavailable) p.ambBg else p.soft,
                    border = BorderStroke(1.dp, if (isUnavailable) p.ambBd else p.bd),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PixelIcon(
                            icon = if (isUnavailable) DeckIcon.Warning else DeckIcon.Bitcoin,
                            size = 14.dp,
                            tint = if (isUnavailable) p.ambText else p.fg,
                        )
                        Text(
                            text = if (state.queued > 0) {
                                "${state.message} · ${state.queued} evento/i firmato/i in coda"
                            } else {
                                state.message
                            },
                            color = if (isUnavailable) p.ambText else p.ink,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    "Profile" -> ProfileScreen(state, model::saveProfile)
                    "Camera" -> if (state.name.isBlank()) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SurfCard(
                                modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
                                padding = 24.dp,
                            ) {
                                Column(
                                    Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(p.soft)
                                            .border(1.dp, p.bd, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        PixelIcon(DeckIcon.User, size = 26.dp, tint = p.fg)
                                    }
                                    Text(
                                        "Completa prima il tuo profilo",
                                        color = p.ink,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        "Imposta un nome visualizzato per firmare e pubblicare i tuoi scatti Nostr certificati su Bitcoin.",
                                        color = p.mu,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                    BitPolitoPrimaryButton(
                                        text = "Vai al Profilo",
                                        icon = DeckIcon.User,
                                        onClick = { tab = "Profile" },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    } else if (state.captured != null) {
                        PublishScreen(state, model::publish, model::discard)
                    } else {
                        CameraScreen(state.busy, model::captured, model::cameraError)
                    }
                    else -> FeedScreen(
                        state = state,
                        refresh = model::refresh,
                        select = model::select,
                        onOpenCamera = { tab = "Camera" },
                    )
                }
            }
        }
    }

    state.selected?.let { selected ->
        VerifyPhotoDialog(
            selected = selected,
            state = state,
            onVerifyAgain = model::verify,
            onDismiss = model::dismiss,
            onCheckCopy = { checkCopy.launch(arrayOf("image/jpeg")) },
            onExportBundle = { saveBundle.launch("photoanchor-${selected.id.take(12)}.zip") },
        )
    }
}

@Composable
private fun VerifyPhotoDialog(
    selected: PhotoPost,
    state: PhotoAnchorState,
    onVerifyAgain: () -> Unit,
    onDismiss: () -> Unit,
    onCheckCopy: () -> Unit,
    onExportBundle: () -> Unit,
) {
    val p = LocalPalette.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SurfCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 520.dp),
            padding = 20.dp,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PixelIcon(DeckIcon.Shield, size = 20.dp, tint = p.fg)
                        Text(
                            "Verifica Crittografica",
                            color = p.ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                        )
                    }
                    BitPolitoBadge("Kind 20 · OTS", kind = "solid", icon = DeckIcon.Bitcoin)
                }

                VerificationCard(
                    icon = DeckIcon.Lock,
                    title = "Firma Schnorr & ID Evento",
                    value = state.verification?.event ?: "Checking on this device…",
                )
                VerificationCard(
                    icon = DeckIcon.Camera,
                    title = "Integrità JPEG (SHA-256)",
                    value = state.verification?.integrity ?: "Checking original bytes…",
                )
                VerificationCard(
                    icon = DeckIcon.Bitcoin,
                    title = "Timestamp Bitcoin (OpenTimestamps)",
                    value = state.verification?.timestamp ?: "Requesting a fresh local node check…",
                )
                VerificationCard(
                    icon = DeckIcon.Globe,
                    title = "Pubblicazione Relay Nostr",
                    value = state.verification?.publication ?: "Unknown",
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = p.soft,
                    border = BorderStroke(1.dp, p.bd),
                ) {
                    SelectionContainer {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Signer: ${selected.pubkey}",
                                color = p.ink,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                "Event:  ${selected.id}",
                                color = p.ink,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                "SHA256: ${selected.photoHash}",
                                color = p.mu,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }

                Text(
                    "Il timestamp è verificato dal nodo Bitcoin locale configurato (regtest). Nome, didascalia e orario sono dichiarazioni firmate dall'autore.",
                    color = p.mu,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BitPolitoOutlinedButton(
                        text = "Verifica copia JPEG",
                        icon = DeckIcon.Camera,
                        onClick = onCheckCopy,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    )
                    BitPolitoOutlinedButton(
                        text = "Esporta .zip",
                        icon = DeckIcon.Key,
                        onClick = onExportBundle,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    )
                }

                state.copyResult?.let { copyMsg ->
                    val copyOk = copyMsg.startsWith("VALID")
                    BitPolitoBadge(
                        text = copyMsg,
                        kind = if (copyOk) "ok" else "err",
                        icon = if (copyOk) DeckIcon.Star else DeckIcon.Warning,
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = p.bd)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BitPolitoOutlinedButton(
                        text = "Chiudi",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    BitPolitoPrimaryButton(
                        text = "Ricontrolla",
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
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PixelIcon(icon, size = 14.dp, tint = accent)
                Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
            Text(value, color = p.ink, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun ProfileScreen(state: PhotoAnchorState, save: (String) -> Unit) {
    val p = LocalPalette.current
    var name by remember(state.name) { mutableStateOf(state.name) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero Profile Card (.surf .hero from design/bitcoin-network-lab)
        SurfCard(modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp), padding = 22.dp) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(p.fg),
                    contentAlignment = Alignment.Center,
                ) {
                    if (name.isNotBlank()) {
                        Text(
                            text = initialsOf(name),
                            color = p.primaryFg,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                        )
                    } else {
                        PixelIcon(DeckIcon.CowHead, size = 34.dp, tint = p.primaryFg)
                    }
                }

                BitPolitoBadge(
                    text = "IDENTITÀ NOSTR · ANDROID KEYSTORE",
                    kind = "default",
                    icon = DeckIcon.Key,
                )

                Text(
                    text = name.ifBlank { "Il tuo profilo locale" },
                    color = p.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    label = { Text("Nome visualizzato (dichiarato)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                BitPolitoPrimaryButton(
                    text = "Salva e pubblica profilo",
                    icon = DeckIcon.Star,
                    onClick = { save(name) },
                    enabled = state.ready && !state.busy && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Public Key & Security Card
        SurfCard(modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp), padding = 20.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PixelIcon(DeckIcon.Lock, size = 18.dp, tint = p.fg)
                    Text(
                        "Chiave pubblica di firma (Schnorr)",
                        color = p.ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = p.soft,
                    border = BorderStroke(1.dp, p.bd),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SelectionContainer {
                        Text(
                            text = state.pubkey.ifBlank { "Generazione chiave in corso…" },
                            modifier = Modifier.padding(12.dp),
                            color = p.fg,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    }
                }

                Text(
                    "La chiave privata è generata sul dispositivo, cifrata con Android Keystore (AES-GCM) ed esclusa dai backup. Puoi cambiare nome in qualsiasi momento: le firme delle foto restano vincolate a questa chiave pubblica.",
                    color = p.mu,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        // BitPolito signature footer (from design/bitcoin-network-lab)
        Row(
            Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PixelIcon(DeckIcon.Bull, size = 20.dp, tint = p.fg)
            Text("Fatto con", color = p.mu, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            PixelIcon(DeckIcon.Heart, size = 13.dp, tint = p.fg)
            Text("da BitPolito · IXH26", color = p.mu, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FeedScreen(
    state: PhotoAnchorState,
    refresh: () -> Unit,
    select: (PhotoPost) -> Unit,
    onOpenCamera: () -> Unit,
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        // Section header bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PixelIcon(DeckIcon.Globe, size = 18.dp, tint = p.fg)
                Text(
                    "Feed Fotografico (${state.feed.size})",
                    color = p.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                )
            }
            BitPolitoOutlinedButton(
                text = "Aggiorna",
                icon = DeckIcon.Lightning,
                onClick = refresh,
            )
        }

        if (state.feed.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                SurfCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
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
                                .clip(RoundedCornerShape(16.dp))
                                .background(p.soft)
                                .border(1.dp, p.bd, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelIcon(DeckIcon.CowHead, size = 34.dp, tint = p.fg)
                        }
                        Text(
                            "Il feed è pronto per i tuoi scatti",
                            color = p.ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "Scatta una foto dalla scheda Scatta: il JPEG originale viene firmato con Nostr (kind 20) e ancorato su Bitcoin con OpenTimestamps.",
                            color = p.mu,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        BitPolitoPrimaryButton(
                            text = "Scatta una foto",
                            icon = DeckIcon.Camera,
                            onClick = onOpenCamera,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.feed, key = { it.post.id }) { entry ->
                    val authorLabel = entry.name.ifBlank { entry.post.pubkey.take(14) + "…" }
                    SurfCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Author header
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Box(
                                        Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(p.fg),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = initialsOf(authorLabel),
                                            color = p.primaryFg,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = authorLabel,
                                            color = p.ink,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "signer ${entry.post.pubkey.take(12)}… · ${entry.post.width}×${entry.post.height}",
                                            color = p.mu,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                                BitPolitoBadge(
                                    text = "Kind 20",
                                    kind = "default",
                                    icon = DeckIcon.Bitcoin,
                                )
                            }

                            // Photo preview frame
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0D1124))
                                    .border(1.dp, p.bd, RoundedCornerShape(12.dp))
                                    .clickable { select(entry.post) },
                                contentAlignment = Alignment.Center,
                            ) {
                                entry.preview?.let { image ->
                                    Image(
                                        bitmap = image.asImageBitmap(),
                                        contentDescription = "Photo preview",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 360.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                } ?: Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "Anteprima non in cache · tocca Verifica per controllare i byte originali",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }

                            if (entry.post.caption.isNotEmpty()) {
                                Text(
                                    text = entry.post.caption,
                                    color = p.ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            // Card footer
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                BitPolitoBadge(
                                    text = "SHA-256 ${entry.post.photoHash.take(10)}…",
                                    kind = "ok",
                                    icon = DeckIcon.Lock,
                                )
                                BitPolitoPrimaryButton(
                                    text = "Verifica prova",
                                    icon = DeckIcon.Shield,
                                    onClick = { select(entry.post) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PublishScreen(state: PhotoAnchorState, publish: (String) -> Unit, discard: () -> Unit) {
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
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            padding = 20.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Step pills (.steps from design/bitcoin-network-lab)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BitPolitoBadge("1 · Scatto completato", kind = "ok", icon = DeckIcon.Camera)
                    Spacer(Modifier.width(8.dp))
                    BitPolitoBadge("2 · Firma & Pubblica", kind = "solid", icon = DeckIcon.Bitcoin)
                }

                Text(
                    "Pubblica JPEG Originale",
                    color = p.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.preview?.let {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0D1124))
                            .border(1.dp, p.bd, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Captured photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 1000) caption = it },
                    label = { Text("Didascalia / Titolo (opzionale)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = p.soft,
                    border = BorderStroke(1.dp, p.bd),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PixelIcon(DeckIcon.Bitcoin, size = 18.dp, tint = p.fg)
                        Text(
                            "I metadati EXIF sensibili sono stati rimossi. Questo esatto JPEG verrà sottoposto a hash SHA-256, firmato con Schnorr e ancorato su Bitcoin.",
                            color = p.mu,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BitPolitoOutlinedButton(
                        text = "Riscatta",
                        icon = DeckIcon.Camera,
                        onClick = discard,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    )
                    BitPolitoPrimaryButton(
                        text = "Firma e pubblica",
                        icon = DeckIcon.Lock,
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
                modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
                padding = 24.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PixelIcon(DeckIcon.Camera, size = 32.dp, tint = p.fg)
                    Text(
                        "Permesso Fotocamera Richiesto",
                        color = p.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "PhotoAnchor certifica solo foto autentiche scattate direttamente dalla fotocamera.",
                        color = p.mu,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    BitPolitoPrimaryButton(
                        text = "Consenti Fotocamera",
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Viewfinder card
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF05070E))
                .border(2.dp, p.bd, RoundedCornerShape(22.dp)),
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Top overlay badges
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BitPolitoBadge("PhotoAnchor Camera", kind = "solid", icon = DeckIcon.Camera)
                BitPolitoBadge(
                    text = if (flash) "Flash ON" else "Flash OFF",
                    kind = if (flash) "pending" else "default",
                    icon = DeckIcon.Lightning,
                )
            }
        }

        // Camera controls bar (.surf)
        SurfCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BitPolitoOutlinedButton(
                    text = "Gira",
                    icon = DeckIcon.Smartphone,
                    onClick = { back = !back },
                    enabled = !capturing && !busy,
                )

                // Circular Shutter Button
                Box(
                    Modifier
                        .size(72.dp)
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
                    text = if (flash) "Flash ON" else "Flash",
                    icon = DeckIcon.Lightning,
                    onClick = { flash = !flash },
                    enabled = !capturing && !busy,
                )
            }
        }
    }
}
