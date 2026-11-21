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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
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
    Share(
        1400f,
        1260f,
        "M840 0H700V140V280V350H560H420H280V490H140V630H0V770V910V1050V1260H140V1050H280V910H420H560H700V980V1120V1260H840V1120H980H1120V840H1260V700H1400V560H1260V420H1120V280H980V140H840V0Z",
    ),
    Qr(
        1260f,
        1260f,
        "M140 0H420V140H140V0ZM140 280V140H0V280H140ZM280 420V280H140V420H0V560H140V840H0V980H140V1120H0V1260H1260V0H980V140H840V0H560V140H420V420H280ZM700 280H840V140H700V280ZM700 560V280H560V560H420V700H280V840H140V980H280V1120H420V980H560V840H420V700H560V560H700ZM840 560H700V700H840V840H700V980H840V1120H980V980H1120V700H840V560ZM1120 420H840V560H1120V420ZM1120 280V140H980V280H1120ZM980 980H840V840H980V980ZM420 840V980H280V840H420ZM280 420V560H140V420H280Z",
    ),
    Message(
        1540f,
        1120f,
        "M140 0H1400V140H1540V980H1400V1120H420V980H280V1120H0V140H140V0ZM280 280V700H1260V280H280Z",
    ),
    Bell(
        1120f,
        1260f,
        "M420 0H700V140H840V280H980V700H1120V980H0V700H140V840H280V700H420V280H280V140H420V0ZM280 280V700H140V280H280ZM700 1120H420V1260H700V1120Z",
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
        modifier = modifier.heightIn(min = 42.dp),
        shape = RoundedCornerShape(if (pill) 99.dp else 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = p.fg,
            contentColor = p.primaryFg,
            disabledContainerColor = p.fg.copy(alpha = 0.38f),
            disabledContentColor = p.primaryFg.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    ) {
        if (icon != null) {
            PixelIcon(icon, size = 14.dp, tint = p.primaryFg)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
        modifier = modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(if (pill) 99.dp else 12.dp),
        border = BorderStroke(1.5.dp, p.bd),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = p.surf,
            contentColor = p.ink,
            disabledContainerColor = p.surf.copy(alpha = 0.5f),
            disabledContentColor = p.mu.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            PixelIcon(icon, size = 13.dp, tint = p.fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
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
            Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
            Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                PixelIcon(icon, size = 12.dp, tint = if (selected) p.primaryFg else p.fg)
            }
            Text(
                text = label,
                color = if (selected) p.primaryFg else p.ink,
                fontSize = 12.sp,
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

private fun handleOf(name: String, pubkey: String): String {
    val slug = name.trim().lowercase().replace(Regex("[^a-z0-9_]+"), "").take(14)
    return if (slug.isNotEmpty()) "@$slug" else "@nostr_${pubkey.take(6)}"
}

private fun formatRelativeTime(epochSeconds: Long): String {
    val diff = (System.currentTimeMillis() / 1000L) - epochSeconds
    return when {
        diff < 60 -> "now"
        diff < 3600 -> "${diff / 60}m"
        diff < 86400 -> "${diff / 3600}h"
        diff < 7 * 86400 -> "${diff / 86400}d"
        else -> runCatching {
            SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(epochSeconds * 1000L))
        }.getOrDefault("")
    }
}

private fun boardForPost(post: PhotoPost, boardAssignments: Map<String, String>): String {
    boardAssignments[post.id]?.let { return it }
    val c = post.caption.lowercase()
    return when {
        "bitcoin" in c || "btc" in c || "sat" in c -> "Bitcoin"
        "street" in c || "city" in c || "urban" in c -> "Street"
        "arch" in c || "design" in c || "building" in c -> "Architecture"
        "nature" in c || "sky" in c || "sun" in c -> "Nature"
        else -> "Inspiration"
    }
}

private data class ReplyComment(val author: String, val handle: String, val text: String, val time: String)

private data class ActivityNotification(
    val id: String,
    val kind: String, // "zap" | "social" | "bitcoin"
    val iconName: String, // "Lightning" | "Bookmark" | "Heart" | "Share" | "Message" | "Bitcoin" | "User"
    val title: String,
    val subtitle: String,
    val time: String,
    val postId: String? = null,
)

/** Animated BitPolito Pixel Lightning Bolt burst overlay shown on double-tap Zap (+21 sats) */
@Composable
private fun BoxScope.LightningZapBurstOverlay(burstTrigger: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(burstTrigger) {
        if (burstTrigger > 0) {
            visible = true
            delay(820)
            visible = false
        }
    }
    val pulseScale by animateFloatAsState(
        targetValue = if (visible) 1.08f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "zapPulseScale",
    )
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.Center),
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        Column(
            modifier = Modifier.graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF001CE0).copy(alpha = 0.92f))
                    .border(2.5.dp, Color(0xFFFCD34D), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PixelIcon(
                    icon = DeckIcon.Lightning,
                    size = 42.dp,
                    tint = Color(0xFFFCD34D),
                )
            }
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = Color(0xFFF59E0B),
                border = BorderStroke(1.5.dp, Color(0xFFFEF3C7)),
                shadowElevation = 6.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PixelIcon(DeckIcon.Lightning, size = 11.dp, tint = Color(0xFF451A03))
                    Text(
                        text = "+21 SATS ZAPPED",
                        color = Color(0xFF451A03),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/* ============================================================================
 * MainActivity & Root App Shell (Nostr × X × Pinterest)
 * ============================================================================ */

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

    var tab by remember { mutableStateOf("Feed") } // "Feed" | "Boards" | "Camera" | "Profile"
    var feedLayout by remember { mutableStateOf("pins") } // "pins" (Pinterest) | "stream" (X / Nostr)
    var feedChannel by remember { mutableStateOf("foryou") } // "foryou" | "following"
    var search by remember { mutableStateOf("") }
    var activeTag by remember { mutableStateOf("") }
    var viewingBoard by remember { mutableStateOf<String?>(null) }
    var showNotifications by remember { mutableStateOf(false) }

    // Social interactions state (persisted locally in SharedPreferences)
    var savedIds by remember {
        mutableStateOf(prefs.getStringSet("saved_pins", emptySet())?.toSet() ?: emptySet())
    }
    var likedIds by remember {
        mutableStateOf(prefs.getStringSet("liked_pins", emptySet())?.toSet() ?: emptySet())
    }
    var repostedIds by remember {
        mutableStateOf(prefs.getStringSet("reposted_pins", emptySet())?.toSet() ?: emptySet())
    }
    var followedPubkeys by remember {
        mutableStateOf(prefs.getStringSet("followed_pks", emptySet())?.toSet() ?: emptySet())
    }
    var customBoards by remember {
        mutableStateOf(
            prefs.getStringSet("custom_boards", setOf("Favorites", "Bitcoin", "Street", "Architecture", "Inspiration"))
                ?.toList()?.sorted()
                ?: listOf("Architecture", "Bitcoin", "Favorites", "Inspiration", "Street"),
        )
    }
    var zapsByPost by remember {
        mutableStateOf<Map<String, Int>>(
            runCatching {
                val raw = JSONObject(prefs.getString("zaps_map", "{}") ?: "{}")
                raw.keys().asSequence().associateWith { raw.optInt(it, 0) }
            }.getOrDefault(emptyMap()),
        )
    }
    var boardByPost by remember {
        mutableStateOf<Map<String, String>>(
            runCatching {
                val raw = JSONObject(prefs.getString("boards_map", "{}") ?: "{}")
                raw.keys().asSequence().associateWith { raw.optString(it, "Favorites") }
            }.getOrDefault(emptyMap()),
        )
    }
    var repliesByPost by remember {
        mutableStateOf<Map<String, List<ReplyComment>>>(
            runCatching {
                val raw = JSONObject(prefs.getString("replies_map", "{}") ?: "{}")
                raw.keys().asSequence().associateWith { k ->
                    val arr = raw.optJSONArray(k) ?: JSONArray()
                    (0 until arr.length()).mapNotNull { idx ->
                        val o = arr.optJSONObject(idx) ?: return@mapNotNull null
                        ReplyComment(
                            author = o.optString("author", "Creator"),
                            handle = o.optString("handle", "@nostr"),
                            text = o.optString("text", ""),
                            time = o.optString("time", "now"),
                        )
                    }
                }
            }.getOrDefault(emptyMap()),
        )
    }

    var readNotificationIds by remember {
        mutableStateOf(prefs.getStringSet("read_notif_ids", emptySet())?.toSet() ?: emptySet())
    }
    var userNotifications by remember {
        mutableStateOf<List<ActivityNotification>>(
            runCatching {
                val arr = JSONArray(prefs.getString("activity_notifs", "[]") ?: "[]")
                (0 until arr.length()).mapNotNull { idx ->
                    val o = arr.optJSONObject(idx) ?: return@mapNotNull null
                    ActivityNotification(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        kind = o.optString("kind", "social"),
                        iconName = o.optString("iconName", "Lightning"),
                        title = o.optString("title", ""),
                        subtitle = o.optString("subtitle", ""),
                        time = o.optString("time", "now"),
                        postId = o.optString("postId", "").ifBlank { null },
                    )
                }
            }.getOrDefault(emptyList()),
        )
    }

    fun pushNotification(
        kind: String,
        iconName: String,
        title: String,
        subtitle: String,
        postId: String? = null,
    ) {
        val item = ActivityNotification(
            id = "usr-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
            kind = kind,
            iconName = iconName,
            title = title,
            subtitle = subtitle,
            time = "now",
            postId = postId,
        )
        val next = (listOf(item) + userNotifications).take(40)
        userNotifications = next
        val arr = JSONArray()
        next.forEach { n ->
            arr.put(
                JSONObject()
                    .put("id", n.id)
                    .put("kind", n.kind)
                    .put("iconName", n.iconName)
                    .put("title", n.title)
                    .put("subtitle", n.subtitle)
                    .put("time", n.time)
                    .put("postId", n.postId ?: ""),
            )
        }
        prefs.edit().putString("activity_notifs", arr.toString()).apply()
    }

    val allNotifications = remember(userNotifications, state.feed, boardByPost) {
        val feedDerived = state.feed.take(10).flatMap { entry ->
            val creator = entry.name.ifBlank { "Creator" }
            val handle = handleOf(creator, entry.post.pubkey)
            val board = boardForPost(entry.post, boardByPost)
            val captionSnippet = entry.post.caption.ifBlank { "Authentic Pin #$board" }.take(48)
            val relTime = formatRelativeTime(entry.post.createdAt)
            listOf(
                ActivityNotification(
                    id = "btc-${entry.post.id}",
                    kind = "bitcoin",
                    iconName = "Bitcoin",
                    title = "Bitcoin OpenTimestamps Proof Active",
                    subtitle = "$captionSnippet · Anchored by $handle",
                    time = relTime,
                    postId = entry.post.id,
                ),
                ActivityNotification(
                    id = "zap-seed-${entry.post.id}",
                    kind = "zap",
                    iconName = "Lightning",
                    title = "$creator received +21 sats on Nostr",
                    subtitle = "Pin in #$board · \"$captionSnippet\"",
                    time = relTime,
                    postId = entry.post.id,
                ),
            )
        }
        userNotifications + feedDerived
    }

    val unreadCount = remember(allNotifications, readNotificationIds) {
        allNotifications.count { it.id !in readNotificationIds }
    }

    fun markAllNotificationsRead() {
        val next = readNotificationIds + allNotifications.map { it.id }
        readNotificationIds = next
        prefs.edit().putStringSet("read_notif_ids", next).apply()
    }

    fun markNotificationRead(id: String) {
        val next = readNotificationIds + id
        readNotificationIds = next
        prefs.edit().putStringSet("read_notif_ids", next).apply()
    }

    fun toggleSavePin(id: String, boardName: String? = null) {
        val wasSaved = id in savedIds
        val next = if (boardName != null) {
            savedIds + id
        } else {
            if (wasSaved) savedIds - id else savedIds + id
        }
        savedIds = next
        prefs.edit().putStringSet("saved_pins", next).apply()
        val post = state.feed.find { it.post.id == id }?.post
        val targetBoard = boardName ?: post?.let { boardForPost(it, boardByPost) } ?: "Favorites"
        if (boardName != null) {
            val nextMap = boardByPost + (id to boardName)
            boardByPost = nextMap
            val json = JSONObject()
            nextMap.forEach { (k, v) -> json.put(k, v) }
            prefs.edit().putString("boards_map", json.toString()).apply()
        }
        if (id in next) {
            pushNotification(
                kind = "social",
                iconName = "Bookmark",
                title = "Pinned to #$targetBoard",
                subtitle = post?.caption?.ifBlank { "Saved Pin to your Pinterest board" } ?: "Saved Pin to board",
                postId = id,
            )
        }
    }

    fun toggleLikePin(id: String) {
        val wasLiked = id in likedIds
        val next = if (wasLiked) likedIds - id else likedIds + id
        likedIds = next
        prefs.edit().putStringSet("liked_pins", next).apply()
        if (!wasLiked) {
            val entry = state.feed.find { it.post.id == id }
            pushNotification(
                kind = "social",
                iconName = "Heart",
                title = "Liked ${entry?.name?.ifBlank { "Creator" } ?: "Creator"}'s Pin",
                subtitle = entry?.post?.caption?.ifBlank { "Authentic Bitcoin-anchored Pin" } ?: "Liked Pin",
                postId = id,
            )
        }
    }

    fun toggleRepostPin(id: String) {
        val wasReposted = id in repostedIds
        val next = if (wasReposted) repostedIds - id else repostedIds + id
        repostedIds = next
        prefs.edit().putStringSet("reposted_pins", next).apply()
        if (!wasReposted) {
            val entry = state.feed.find { it.post.id == id }
            pushNotification(
                kind = "social",
                iconName = "Share",
                title = "Reposted Pin to your Nostr stream",
                subtitle = entry?.post?.caption?.ifBlank { "Shared with your followers" } ?: "Reposted Pin",
                postId = id,
            )
        }
    }

    fun toggleFollow(pubkey: String) {
        val wasFollowing = pubkey in followedPubkeys
        val next = if (wasFollowing) followedPubkeys - pubkey else followedPubkeys + pubkey
        followedPubkeys = next
        prefs.edit().putStringSet("followed_pks", next).apply()
        if (!wasFollowing) {
            val creator = state.feed.find { it.post.pubkey == pubkey }?.name?.ifBlank { "Creator" } ?: "Creator"
            pushNotification(
                kind = "social",
                iconName = "User",
                title = "Followed $creator (${handleOf(creator, pubkey)})",
                subtitle = "Their Pins now appear in your Following stream",
                postId = state.feed.find { it.post.pubkey == pubkey }?.post?.id,
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
            iconName = "Lightning",
            title = "Zapped +$amount sats to $creator",
            subtitle = "Total ${nextCount + 21} sats on \"${entry?.post?.caption?.ifBlank { "Authentic Pin" }?.take(40) ?: "Pin"}\"",
            postId = id,
        )
    }

    fun createBoard(title: String) {
        val clean = title.trim()
        if (clean.isEmpty() || clean in customBoards) return
        val next = (customBoards + clean).sorted()
        customBoards = next
        prefs.edit().putStringSet("custom_boards", next.toSet()).apply()
        pushNotification(
            kind = "social",
            iconName = "Bookmark",
            title = "Created new Pinterest Board #$clean",
            subtitle = "Start saving Bitcoin-verified Pins to #$clean",
        )
    }

    fun addReply(postId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val myAuthor = state.name.ifBlank { "Creator" }
        val myHandle = handleOf(myAuthor, state.pubkey)
        val comment = ReplyComment(myAuthor, myHandle, clean, "now")
        val nextList = (repliesByPost[postId] ?: emptyList()) + comment
        val nextMap = repliesByPost + (postId to nextList)
        repliesByPost = nextMap
        val root = JSONObject()
        nextMap.forEach { (k, list) ->
            val arr = JSONArray()
            list.forEach { c ->
                arr.put(
                    JSONObject()
                        .put("author", c.author)
                        .put("handle", c.handle)
                        .put("text", c.text)
                        .put("time", c.time),
                )
            }
            root.put(k, arr)
        }
        prefs.edit().putString("replies_map", root.toString()).apply()
        pushNotification(
            kind = "social",
            iconName = "Message",
            title = "Replied to thread as $myHandle",
            subtitle = "\"$clean\"",
            postId = postId,
        )
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
                    // Brand logo (BitPolito Cow + BitPin)
                    Row(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (state.ready) {
                                viewingBoard = null
                                activeTag = ""
                                tab = "Feed"
                            }
                        },
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

                    // Search Bar (Pinterest + X search)
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
                                        text = "Search pins, #tags, @creators…",
                                        color = p.mu,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                BasicTextField(
                                    value = search,
                                    onValueChange = {
                                        search = it
                                        if (tab != "Feed") tab = "Feed"
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
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

                    // Notification / Activity Center Bell Button with Unread Badge
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(p.soft)
                            .border(1.dp, p.bd, CircleShape)
                            .clickable { showNotifications = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelIcon(DeckIcon.Bell, size = 15.dp, tint = p.fg)
                        if (unreadCount > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-2).dp, y = 2.dp)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(p.amb)
                                    .border(1.5.dp, p.surf, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (unreadCount > 9) "9+" else "$unreadCount",
                                    color = Color(0xFF451A03),
                                    fontSize = 7.5.sp,
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
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val items = listOf(
                        Triple("Feed", "Home", DeckIcon.Globe),
                        Triple("Boards", "Boards", DeckIcon.Bookmark),
                        Triple("Camera", "Post", DeckIcon.Camera),
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
                                .clickable(enabled = state.ready) {
                                    viewingBoard = null
                                    tab = key
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                PixelIcon(icon, size = 16.dp, tint = fg)
                                Spacer(Modifier.height(2.dp))
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

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    "Profile" -> ProfileScreen(
                        state = state,
                        savedIds = savedIds,
                        likedIds = likedIds,
                        repostedIds = repostedIds,
                        followedPubkeys = followedPubkeys,
                        zapsByPost = zapsByPost,
                        boardByPost = boardByPost,
                        customBoards = customBoards,
                        onToggleSave = { id -> toggleSavePin(id) },
                        onToggleLike = ::toggleLikePin,
                        onZap = { id -> zapPost(id, 21) },
                        saveProfile = model::saveProfile,
                        onSelectPin = model::select,
                        onOpenBoard = { b ->
                            viewingBoard = b
                            tab = "Boards"
                        },
                        onOpenCamera = { tab = "Camera" },
                    )
                    "Boards" -> BoardsAndDiscoveryScreen(
                        state = state,
                        customBoards = customBoards,
                        boardByPost = boardByPost,
                        savedIds = savedIds,
                        followedPubkeys = followedPubkeys,
                        viewingBoard = viewingBoard,
                        onSelectBoard = { viewingBoard = it },
                        onCreateBoard = ::createBoard,
                        onToggleFollow = ::toggleFollow,
                        onToggleSave = { id -> toggleSavePin(id) },
                        onZap = { id -> zapPost(id, 21) },
                        zapsByPost = zapsByPost,
                        onSelectPin = model::select,
                        onSelectTag = { tag ->
                            activeTag = tag
                            tab = "Feed"
                        },
                        onOpenCamera = { tab = "Camera" },
                    )
                    "Camera" -> if (state.name.isBlank()) {
                        OnboardingNameCard(
                            state = state,
                            onSaveName = { newName -> model.saveProfile(newName) },
                        )
                    } else if (state.captured != null) {
                        PublishScreen(
                            state = state,
                            boards = customBoards,
                            publish = { caption, chosenBoard ->
                                model.publish(caption)
                                tab = "Feed"
                            },
                            discard = model::discard,
                        )
                    } else {
                        CameraScreen(state.busy, model::captured, model::cameraError)
                    }
                    else -> HomeFeedScreen(
                        state = state,
                        feedLayout = feedLayout,
                        onLayoutChange = { feedLayout = it },
                        feedChannel = feedChannel,
                        onChannelChange = { feedChannel = it },
                        search = search,
                        activeTag = activeTag,
                        onClearTag = { activeTag = "" },
                        savedIds = savedIds,
                        likedIds = likedIds,
                        repostedIds = repostedIds,
                        followedPubkeys = followedPubkeys,
                        zapsByPost = zapsByPost,
                        boardByPost = boardByPost,
                        repliesByPost = repliesByPost,
                        onToggleSave = { id -> toggleSavePin(id) },
                        onToggleLike = ::toggleLikePin,
                        onToggleRepost = ::toggleRepostPin,
                        onToggleFollow = ::toggleFollow,
                        onZap = { id -> zapPost(id, 21) },
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
            isLiked = selected.id in likedIds,
            isReposted = selected.id in repostedIds,
            isFollowing = selected.pubkey in followedPubkeys,
            zapSats = (zapsByPost[selected.id] ?: 0) + 21,
            assignedBoard = boardForPost(selected, boardByPost),
            boards = customBoards,
            replies = repliesByPost[selected.id] ?: emptyList(),
            onToggleSave = { board -> toggleSavePin(selected.id, board) },
            onToggleLike = { toggleLikePin(selected.id) },
            onToggleRepost = { toggleRepostPin(selected.id) },
            onToggleFollow = { toggleFollow(selected.pubkey) },
            onZap = { zapPost(selected.id, 21) },
            onAddReply = { text -> addReply(selected.id, text) },
            onVerifyAgain = model::verify,
            onDismiss = model::dismiss,
            onCheckCopy = { checkCopy.launch(arrayOf("image/jpeg")) },
            onExportBundle = { saveBundle.launch("bitpin-${selected.id.take(12)}.zip") },
        )
    }

    if (showNotifications) {
        NotificationsDialog(
            notifications = allNotifications,
            readIds = readNotificationIds,
            onMarkAllRead = ::markAllNotificationsRead,
            onSelectNotification = { notif ->
                markNotificationRead(notif.id)
                val targetPost = notif.postId?.let { pid -> state.feed.find { it.post.id == pid }?.post }
                showNotifications = false
                if (targetPost != null) {
                    model.select(targetPost)
                }
            },
            onDismiss = { showNotifications = false },
        )
    }
}

/* ============================================================================
 * Home Screen: X "For You / Following" + Switchable Pinterest Grid / X Stream
 * ============================================================================ */

@Composable
private fun HomeFeedScreen(
    state: PhotoAnchorState,
    feedLayout: String, // "pins" | "stream"
    onLayoutChange: (String) -> Unit,
    feedChannel: String, // "foryou" | "following"
    onChannelChange: (String) -> Unit,
    search: String,
    activeTag: String,
    onClearTag: () -> Unit,
    savedIds: Set<String>,
    likedIds: Set<String>,
    repostedIds: Set<String>,
    followedPubkeys: Set<String>,
    zapsByPost: Map<String, Int>,
    boardByPost: Map<String, String>,
    repliesByPost: Map<String, List<ReplyComment>>,
    onToggleSave: (String) -> Unit,
    onToggleLike: (String) -> Unit,
    onToggleRepost: (String) -> Unit,
    onToggleFollow: (String) -> Unit,
    onZap: (String) -> Unit,
    refresh: () -> Unit,
    select: (PhotoPost) -> Unit,
    onOpenCamera: () -> Unit,
) {
    val p = LocalPalette.current
    val filtered = remember(
        state.feed,
        search,
        activeTag,
        feedChannel,
        followedPubkeys,
        state.pubkey,
        boardByPost,
    ) {
        state.feed.filter { entry ->
            if (feedChannel == "following" &&
                entry.post.pubkey !in followedPubkeys &&
                entry.post.pubkey != state.pubkey
            ) {
                return@filter false
            }
            if (activeTag.isNotEmpty()) {
                val b = boardForPost(entry.post, boardByPost)
                if (!entry.post.caption.contains(activeTag, ignoreCase = true) &&
                    !b.equals(activeTag, ignoreCase = true)
                ) {
                    return@filter false
                }
            }
            val q = search.trim().lowercase()
            if (q.isEmpty()) return@filter true
            entry.post.caption.lowercase().contains(q) ||
                entry.name.lowercase().contains(q) ||
                boardForPost(entry.post, boardByPost).lowercase().contains(q)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // X-style Top Sub-Header ("For You" | "Following") + Layout Toggle ("Pins" | "Stream") + Sync
        Surface(
            color = p.surf,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // For You / Following tabs (X style)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChipPill(
                            label = "For You",
                            selected = feedChannel == "foryou",
                            onClick = { onChannelChange("foryou") },
                        )
                        FilterChipPill(
                            label = "Following",
                            selected = feedChannel == "following",
                            onClick = { onChannelChange("following") },
                        )
                    }

                    // View switcher: Pinterest Masonry ("Pins") vs X/Nostr Timeline ("Stream")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = p.soft,
                            border = BorderStroke(1.dp, p.bd),
                        ) {
                            Row(
                                Modifier.padding(3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                listOf("pins" to "Pins", "stream" to "Stream").forEach { (key, lbl) ->
                                    val active = feedLayout == key
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(if (active) p.fg else Color.Transparent)
                                            .clickable { onLayoutChange(key) }
                                            .padding(horizontal = 11.dp, vertical = 5.dp),
                                    ) {
                                        Text(
                                            text = lbl,
                                            color = if (active) p.primaryFg else p.mu,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }

                        // Nostr Relay Sync pill
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(p.soft)
                                .border(1.dp, p.bd, CircleShape)
                                .clickable(onClick = refresh),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelIcon(DeckIcon.Lightning, size = 14.dp, tint = p.fg)
                        }
                    }
                }

                if (activeTag.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BitPolitoBadge("Topic: #$activeTag", kind = "solid", icon = DeckIcon.Bookmark)
                        Text(
                            text = "Clear filter",
                            color = p.fg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = onClearTag),
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = p.bd)
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
                            text = if (feedChannel == "following") {
                                "No posts from followed creators"
                            } else {
                                "Your Nostr × Pinterest feed is ready"
                            },
                            color = p.ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (feedChannel == "following") {
                                "Follow creators in the Boards tab or switch to For You to discover every Bitcoin-anchored Pin."
                            } else {
                                "Snap an authentic photo to publish a Pin on Nostr, collect Zaps, and anchor it on Bitcoin."
                            },
                            color = p.mu,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        BitPolitoPrimaryButton(
                            text = if (feedChannel == "following") "Switch to For You" else "Snap & Post Pin",
                            icon = if (feedChannel == "following") DeckIcon.Globe else DeckIcon.Camera,
                            onClick = {
                                if (feedChannel == "following") onChannelChange("foryou") else onOpenCamera()
                            },
                        )
                    }
                }
            }
        } else if (feedLayout == "pins") {
            // Pinterest 2-Column Staggered Masonry Grid
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalItemSpacing = 12.dp,
            ) {
                items(filtered, key = { it.post.id }) { entry ->
                    PinterestPinCard(
                        entry = entry,
                        boardName = boardForPost(entry.post, boardByPost),
                        zapSats = (zapsByPost[entry.post.id] ?: 0) + 21,
                        isSaved = entry.post.id in savedIds,
                        onToggleSave = { onToggleSave(entry.post.id) },
                        onZap = { onZap(entry.post.id) },
                        onOpen = { select(entry.post) },
                    )
                }
            }
        } else {
            // X + Nostr Social Timeline Stream
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(filtered, key = { it.post.id }) { entry ->
                    XTimelinePostCard(
                        entry = entry,
                        isOwn = entry.post.pubkey == state.pubkey,
                        boardName = boardForPost(entry.post, boardByPost),
                        isSaved = entry.post.id in savedIds,
                        isLiked = entry.post.id in likedIds,
                        isReposted = entry.post.id in repostedIds,
                        isFollowing = entry.post.pubkey in followedPubkeys,
                        zapSats = (zapsByPost[entry.post.id] ?: 0) + 21,
                        replyCount = (repliesByPost[entry.post.id]?.size ?: 0),
                        onToggleSave = { onToggleSave(entry.post.id) },
                        onToggleLike = { onToggleLike(entry.post.id) },
                        onToggleRepost = { onToggleRepost(entry.post.id) },
                        onToggleFollow = { onToggleFollow(entry.post.pubkey) },
                        onZap = { onZap(entry.post.id) },
                        onOpen = { select(entry.post) },
                    )
                }
            }
        }
    }
}

/* ============================================================================
 * Card 1: Pinterest Masonry Pin Card (with Nostr Zap ⚡ & Board Pill)
 * ============================================================================ */

@Composable
private fun PinterestPinCard(
    entry: FeedEntry,
    boardName: String,
    zapSats: Int,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onZap: () -> Unit,
    onOpen: () -> Unit,
) {
    val p = LocalPalette.current
    val creatorName = entry.name.ifBlank { "Creator" }
    val title = entry.post.caption.ifBlank { "Untitled Pin" }
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

                // Animated Pixel Lightning Bolt burst on double-tap
                LightningZapBurstOverlay(burstTrigger = zapBurstKey)

                // Top-left Board tag pill
                Box(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = Color.Black.copy(alpha = 0.62f),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            PixelIcon(DeckIcon.Bitcoin, size = 10.dp, tint = Color(0xFFFCD34D))
                            Text(
                                text = boardName,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Top-right Pinterest "Save" pill button
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (isSaved) p.fg else Color.Black.copy(alpha = 0.62f))
                        .clickable(onClick = onToggleSave)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = if (isSaved) "Saved" else "Save",
                        color = if (isSaved) p.primaryFg else Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
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
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
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
                            fontSize = 11.sp,
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

/* ============================================================================
 * Card 2: X × Nostr Timeline Stream Post Card
 * ============================================================================ */

@Composable
private fun XTimelinePostCard(
    entry: FeedEntry,
    isOwn: Boolean,
    boardName: String,
    isSaved: Boolean,
    isLiked: Boolean,
    isReposted: Boolean,
    isFollowing: Boolean,
    zapSats: Int,
    replyCount: Int,
    onToggleSave: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleRepost: () -> Unit,
    onToggleFollow: () -> Unit,
    onZap: () -> Unit,
    onOpen: () -> Unit,
) {
    val p = LocalPalette.current
    val creatorName = entry.name.ifBlank { "Creator" }
    val handle = handleOf(creatorName, entry.post.pubkey)
    val timeAgo = formatRelativeTime(entry.post.createdAt)
    var zapBurstKey by remember { mutableIntStateOf(0) }

    SurfCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        padding = 14.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // X/Nostr Post Header: Avatar + Name + Verified Shield + @handle · time + Follow / Pin button
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
                                fontSize = 14.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            PixelIcon(DeckIcon.Shield, size = 13.dp, tint = p.fg)
                        }
                        Text(
                            text = "$handle · $timeAgo",
                            color = p.mu,
                            fontSize = 12.sp,
                        )
                    }
                }

                if (!isOwn) {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = if (isFollowing) p.soft else p.fg,
                        border = BorderStroke(1.dp, if (isFollowing) p.bd else p.fg),
                        modifier = Modifier.clickable(onClick = onToggleFollow),
                    ) {
                        Text(
                            text = if (isFollowing) "Following" else "Follow",
                            color = if (isFollowing) p.fg else p.primaryFg,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                } else {
                    BitPolitoBadge(boardName, kind = "default", icon = DeckIcon.Bookmark)
                }
            }

            // Post Text / Caption + Board Tag
            if (entry.post.caption.isNotBlank()) {
                Text(
                    text = entry.post.caption,
                    color = p.ink,
                    fontSize = 14.5.sp,
                    lineHeight = 20.sp,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BitPolitoBadge("#$boardName", kind = "default")
                BitPolitoBadge("Bitcoin OTS", kind = "ok", icon = DeckIcon.Bitcoin)
            }

            // Large Rounded Image (Double-tap to Zap +21 sats with Lightning animation)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        contentScale = ContentScale.Fit,
                    )
                } ?: Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelIcon(DeckIcon.Camera, size = 32.dp, tint = Color.White)
                }

                LightningZapBurstOverlay(burstTrigger = zapBurstKey)
            }

            // X × Nostr × Pinterest Action Bar: Reply · Repost · Zap ⚡ · Like ♥ · Pin 📌
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Reply (X)
                SocialActionPill(
                    icon = DeckIcon.Message,
                    label = if (replyCount > 0) "$replyCount" else "Reply",
                    active = false,
                    onClick = onOpen,
                )

                // Repost / Boost (X + Nostr)
                SocialActionPill(
                    icon = DeckIcon.Share,
                    label = if (isReposted) "Reposted" else "Repost",
                    active = isReposted,
                    onClick = onToggleRepost,
                )

                // Zap Sats (Nostr ⚡)
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
                        Text(
                            text = "$zapSats sats",
                            color = p.ambText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }

                // Like (X + Pinterest)
                SocialActionPill(
                    icon = DeckIcon.Heart,
                    label = if (isLiked) "1" else "Like",
                    active = isLiked,
                    onClick = onToggleLike,
                )

                // Save to Board (Pinterest)
                SocialActionPill(
                    icon = DeckIcon.Bookmark,
                    label = if (isSaved) "Pinned" else "Pin",
                    active = isSaved,
                    onClick = onToggleSave,
                )
            }
        }
    }
}

@Composable
private fun SocialActionPill(
    icon: DeckIcon,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val tint = if (active) p.fg else p.mu
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (active) p.soft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PixelIcon(icon, size = 13.dp, tint = tint)
        Text(
            text = label,
            color = tint,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
        )
    }
}

/* ============================================================================
 * Boards & Discovery Tab (Pinterest Boards + X Trending Topics + Nostr Creators)
 * ============================================================================ */

@Composable
private fun BoardsAndDiscoveryScreen(
    state: PhotoAnchorState,
    customBoards: List<String>,
    boardByPost: Map<String, String>,
    savedIds: Set<String>,
    followedPubkeys: Set<String>,
    viewingBoard: String?,
    onSelectBoard: (String?) -> Unit,
    onCreateBoard: (String) -> Unit,
    onToggleFollow: (String) -> Unit,
    onToggleSave: (String) -> Unit,
    onZap: (String) -> Unit,
    zapsByPost: Map<String, Int>,
    onSelectPin: (PhotoPost) -> Unit,
    onSelectTag: (String) -> Unit,
    onOpenCamera: () -> Unit,
) {
    val p = LocalPalette.current
    var newBoardName by remember { mutableStateOf("") }

    if (viewingBoard != null) {
        val boardPins = remember(state.feed, viewingBoard, boardByPost, savedIds) {
            state.feed.filter { entry ->
                if (viewingBoard == "Favorites") {
                    entry.post.id in savedIds
                } else {
                    boardForPost(entry.post, boardByPost).equals(viewingBoard, ignoreCase = true)
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BitPolitoOutlinedButton(
                    text = "All Boards",
                    onClick = { onSelectBoard(null) },
                )
                Text(
                    text = viewingBoard,
                    color = p.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                )
                BitPolitoBadge("${boardPins.size} Pins", kind = "solid", icon = DeckIcon.Bookmark)
            }

            if (boardPins.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    SurfCard(modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp), padding = 24.dp) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PixelIcon(DeckIcon.Bookmark, size = 28.dp, tint = p.fg)
                            Text("This Board is empty", color = p.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                "Save Pins to \"$viewingBoard\" or snap a new photo.",
                                color = p.mu,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                            BitPolitoPrimaryButton(
                                text = "Snap a Pin",
                                icon = DeckIcon.Camera,
                                onClick = onOpenCamera,
                            )
                        }
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 12.dp,
                ) {
                    items(boardPins, key = { it.post.id }) { entry ->
                        PinterestPinCard(
                            entry = entry,
                            boardName = viewingBoard,
                            zapSats = (zapsByPost[entry.post.id] ?: 0) + 21,
                            isSaved = entry.post.id in savedIds,
                            onToggleSave = { onToggleSave(entry.post.id) },
                            onZap = { onZap(entry.post.id) },
                            onOpen = { onSelectPin(entry.post) },
                        )
                    }
                }
            }
        }
        return
    }

    val creators = remember(state.feed, state.pubkey, state.name) {
        val map = linkedMapOf<String, Pair<String, Int>>()
        if (state.pubkey.isNotBlank()) {
            val myCount = state.feed.count { it.post.pubkey == state.pubkey }
            map[state.pubkey] = (state.name.ifBlank { "You" }) to myCount
        }
        state.feed.forEach { entry ->
            val prev = map[entry.post.pubkey]
            val name = entry.name.ifBlank { prev?.first ?: "Creator" }
            val count = state.feed.count { it.post.pubkey == entry.post.pubkey }
            map[entry.post.pubkey] = name to count
        }
        map.entries.map { Triple(it.key, it.value.first, it.value.second) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 1. Trending Tags on Nostr & X
        SurfCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PixelIcon(DeckIcon.Lightning, size = 16.dp, tint = p.fg)
                    Text(
                        "Trending Topics",
                        color = p.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Bitcoin", "Street", "Architecture", "Inspiration", "Nostr", "Polito").forEach { tag ->
                        FilterChipPill(
                            label = "#$tag",
                            selected = false,
                            onClick = { onSelectTag(tag) },
                        )
                    }
                }
            }
        }

        // 2. Pinterest Visual Boards
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PixelIcon(DeckIcon.Bookmark, size = 16.dp, tint = p.fg)
                    Text(
                        "Pinterest Boards (${customBoards.size})",
                        color = p.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                    )
                }
            }

            // Create Board bar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newBoardName,
                    onValueChange = { if (it.length <= 40) newBoardName = it },
                    placeholder = { Text("Create new board (e.g. Travel)…") },
                    singleLine = true,
                    shape = RoundedCornerShape(99.dp),
                    modifier = Modifier.weight(1f),
                )
                BitPolitoPrimaryButton(
                    text = "Add",
                    enabled = newBoardName.isNotBlank(),
                    onClick = {
                        onCreateBoard(newBoardName)
                        newBoardName = ""
                    },
                )
            }

            customBoards.chunked(2).forEach { rowBoards ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowBoards.forEach { boardTitle ->
                        val matching = state.feed.filter { entry ->
                            if (boardTitle == "Favorites") {
                                entry.post.id in savedIds
                            } else {
                                boardForPost(entry.post, boardByPost).equals(boardTitle, ignoreCase = true)
                            }
                        }
                        PinterestBoardCard(
                            title = boardTitle,
                            entries = matching,
                            onClick = { onSelectBoard(boardTitle) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowBoards.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // 3. Who to Follow on Nostr (X / Nostr Creator Directory)
        SurfCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PixelIcon(DeckIcon.User, size = 16.dp, tint = p.fg)
                    Text(
                        "Creators on Nostr",
                        color = p.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                    )
                }

                creators.forEach { (pubkey, name, pinCount) ->
                    val isMe = pubkey == state.pubkey
                    val isFollowing = pubkey in followedPubkeys
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
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(p.fg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = initialsOf(name),
                                    color = p.primaryFg,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        text = name,
                                        color = p.ink,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                    if (isMe) {
                                        BitPolitoBadge("You", kind = "default")
                                    }
                                }
                                Text(
                                    text = "${handleOf(name, pubkey)} · $pinCount Pins",
                                    color = p.mu,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }

                        if (!isMe) {
                            BitPolitoOutlinedButton(
                                text = if (isFollowing) "Following" else "Follow",
                                onClick = { onToggleFollow(pubkey) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinterestBoardCard(
    title: String,
    entries: List<FeedEntry>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = p.surf,
        border = BorderStroke(1.dp, p.bd),
        shadowElevation = 2.dp,
    ) {
        Column {
            // 3-Photo Pinterest Board Collage
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .background(p.soft),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                        .background(p.soft),
                    contentAlignment = Alignment.Center,
                ) {
                    entries.getOrNull(0)?.preview?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } ?: PixelIcon(DeckIcon.Bookmark, size = 20.dp, tint = p.mu)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(p.soft.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        entries.getOrNull(1)?.preview?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(p.soft.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        entries.getOrNull(2)?.preview?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = title,
                    color = p.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entries.size} Pins",
                    color = p.mu,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/* ============================================================================
 * Profile Screen (X Banner + Nostr Zaps ⚡ + Pinterest Pins/Boards/Likes Tabs)
 * ============================================================================ */

@Composable
private fun ProfileScreen(
    state: PhotoAnchorState,
    savedIds: Set<String>,
    likedIds: Set<String>,
    repostedIds: Set<String>,
    followedPubkeys: Set<String>,
    zapsByPost: Map<String, Int>,
    boardByPost: Map<String, String>,
    customBoards: List<String>,
    onToggleSave: (String) -> Unit,
    onToggleLike: (String) -> Unit,
    onZap: (String) -> Unit,
    saveProfile: (String) -> Unit,
    onSelectPin: (PhotoPost) -> Unit,
    onOpenBoard: (String) -> Unit,
    onOpenCamera: () -> Unit,
) {
    val p = LocalPalette.current
    var name by remember(state.name) { mutableStateOf(state.name) }
    var editing by remember(state.name) { mutableStateOf(state.name.isBlank()) }
    var profileTab by remember { mutableStateOf("pins") } // "pins" | "boards" | "saved" | "likes"
    var showQrModal by remember { mutableStateOf(false) }

    val myPins = remember(state.feed, state.pubkey) {
        state.feed.filter { it.post.pubkey == state.pubkey }
    }
    val savedPins = remember(state.feed, savedIds) {
        state.feed.filter { it.post.id in savedIds }
    }
    val likedPins = remember(state.feed, likedIds) {
        state.feed.filter { it.post.id in likedIds }
    }
    val totalSatsEarned = remember(myPins, zapsByPost) {
        myPins.sumOf { (zapsByPost[it.post.id] ?: 0) + 21 }
    }
    val displayName = state.name.ifBlank { "New Creator" }
    val handle = handleOf(displayName, state.pubkey)

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 12.dp,
    ) {
        // X + Nostr + Pinterest Hero Profile Card
        item(span = StaggeredGridItemSpan.FullLine) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = p.surf,
                border = BorderStroke(1.dp, p.bd),
                shadowElevation = 2.dp,
            ) {
                Column {
                    // X-style Royal Blue Cover Banner with BitPolito Bull & Cow pixel motif
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(86.dp)
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
                        Row(
                            Modifier.align(Alignment.BottomStart),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PixelIcon(
                                DeckIcon.Bull,
                                size = 34.dp,
                                tint = Color.White.copy(alpha = 0.25f),
                            )
                        }
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Avatar + Name + @handle
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
                                )
                                PixelIcon(DeckIcon.Shield, size = 15.dp, tint = p.fg)
                            }
                            Text(
                                text = handle,
                                color = p.mu,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        // Stats Bar: Pins · Boards · Following · Sats Zapped ⚡
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileStatItem(count = "${myPins.size}", label = "Pins")
                            ProfileStatItem(count = "${customBoards.size}", label = "Boards")
                            ProfileStatItem(count = "${followedPubkeys.size}", label = "Following")
                            ProfileStatItem(count = "$totalSatsEarned", label = "Sats ⚡")
                        }

                        AnimatedVisibility(visible = editing) {
                            Column(
                                Modifier.fillMaxWidth().padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { if (it.length <= 80) name = it },
                                    label = { Text("Creator display name") },
                                    placeholder = { Text("e.g. Alex · Visual Creator") },
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
                                BitPolitoOutlinedButton(
                                    text = "Share QR",
                                    icon = DeckIcon.Qr,
                                    onClick = { showQrModal = true },
                                )
                                BitPolitoPrimaryButton(
                                    text = "Post Pin",
                                    icon = DeckIcon.Camera,
                                    onClick = onOpenCamera,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Profile Navigation Tabs: Pins · Boards · Saved · Likes
        item(span = StaggeredGridItemSpan.FullLine) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChipPill(
                    label = "Pins (${myPins.size})",
                    selected = profileTab == "pins",
                    icon = DeckIcon.Camera,
                    onClick = { profileTab = "pins" },
                )
                FilterChipPill(
                    label = "Boards (${customBoards.size})",
                    selected = profileTab == "boards",
                    icon = DeckIcon.Bookmark,
                    onClick = { profileTab = "boards" },
                )
                FilterChipPill(
                    label = "Saved (${savedPins.size})",
                    selected = profileTab == "saved",
                    icon = DeckIcon.Star,
                    onClick = { profileTab = "saved" },
                )
                FilterChipPill(
                    label = "Likes (${likedPins.size})",
                    selected = profileTab == "likes",
                    icon = DeckIcon.Heart,
                    onClick = { profileTab = "likes" },
                )
            }
        }

        if (profileTab == "boards") {
            items(customBoards, key = { it }) { boardTitle ->
                val matching = state.feed.filter { entry ->
                    if (boardTitle == "Favorites") {
                        entry.post.id in savedIds
                    } else {
                        boardForPost(entry.post, boardByPost).equals(boardTitle, ignoreCase = true)
                    }
                }
                PinterestBoardCard(
                    title = boardTitle,
                    entries = matching,
                    onClick = { onOpenBoard(boardTitle) },
                )
            }
        } else {
            val activeList = when (profileTab) {
                "saved" -> savedPins
                "likes" -> likedPins
                else -> myPins
            }
            if (activeList.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    SurfCard(modifier = Modifier.fillMaxWidth(), padding = 26.dp) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PixelIcon(
                                icon = when (profileTab) {
                                    "saved" -> DeckIcon.Bookmark
                                    "likes" -> DeckIcon.Heart
                                    else -> DeckIcon.Camera
                                },
                                size = 28.dp,
                                tint = p.fg,
                            )
                            Text(
                                text = when (profileTab) {
                                    "saved" -> "No saved Pins yet"
                                    "likes" -> "No liked Pins yet"
                                    else -> "No published Pins yet"
                                },
                                color = p.ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                            Text(
                                text = when (profileTab) {
                                    "saved" -> "Save Pins from the home feed into your Pinterest boards."
                                    "likes" -> "Tap the heart on posts you love."
                                    else -> "Snap an authentic photo to share your first Pin on Nostr."
                                },
                                color = p.mu,
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center,
                            )
                            if (profileTab == "pins") {
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
                items(activeList, key = { it.post.id }) { entry ->
                    PinterestPinCard(
                        entry = entry,
                        boardName = boardForPost(entry.post, boardByPost),
                        zapSats = (zapsByPost[entry.post.id] ?: 0) + 21,
                        isSaved = entry.post.id in savedIds,
                        onToggleSave = { onToggleSave(entry.post.id) },
                        onZap = { onZap(entry.post.id) },
                        onOpen = { onSelectPin(entry.post) },
                    )
                }
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

    if (showQrModal) {
        Dialog(onDismissRequest = { showQrModal = false }) {
            SurfCard(modifier = Modifier.fillMaxWidth().padding(16.dp), padding = 22.dp) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(p.fg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = initialsOf(displayName),
                            color = p.primaryFg,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                        )
                    }
                    Text(displayName, color = p.ink, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                    Text(handle, color = p.mu, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Box(
                        Modifier
                            .size(150.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(p.soft)
                            .border(1.5.dp, p.bd, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelIcon(DeckIcon.Qr, size = 96.dp, tint = p.fg)
                    }
                    BitPolitoBadge("Nostr Verified Creator", kind = "ok", icon = DeckIcon.Shield)
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
private fun ProfileStatItem(count: String, label: String) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, color = p.fg, fontWeight = FontWeight.ExtraBold, fontSize = 16.5.sp)
        Text(label, color = p.mu, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

/* ============================================================================
 * Create Pin / Post Flow (Onboarding, Camera, Publish with Board Selector)
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
                    "Pick your creator name to start posting Pins on Nostr.",
                    color = p.mu,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                BitPolitoPrimaryButton(
                    text = "Open Camera",
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
    boards: List<String>,
    publish: (String, String) -> Unit,
    discard: () -> Unit,
) {
    val p = LocalPalette.current
    var caption by remember(state.captured) { mutableStateOf("") }
    var chosenBoard by remember { mutableStateOf(boards.firstOrNull() ?: "Inspiration") }

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
                        "New Pin & Nostr Post",
                        color = p.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                    )
                    BitPolitoBadge("Bitcoin OTS", kind = "ok", icon = DeckIcon.Bitcoin)
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
                                .heightIn(max = 300.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 1000) caption = it },
                    label = { Text("What's happening? Add a title or #tags") },
                    placeholder = { Text("e.g. Golden hour in Turin #Street #Bitcoin") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                // Pinterest Board selector pills
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Pin to Board",
                        color = p.ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        boards.forEach { b ->
                            FilterChipPill(
                                label = b,
                                selected = chosenBoard == b,
                                icon = DeckIcon.Bookmark,
                                onClick = { chosenBoard = b },
                            )
                        }
                    }
                }

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
                        text = if (state.busy) "Posting…" else "Post Pin",
                        icon = DeckIcon.Lightning,
                        onClick = {
                            val finalCaption = if (caption.isBlank()) {
                                "#$chosenBoard"
                            } else if (!caption.contains("#$chosenBoard", ignoreCase = true)) {
                                "$caption #$chosenBoard"
                            } else {
                                caption
                            }
                            publish(finalCaption, chosenBoard)
                        },
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
                        "Allow camera access to snap authentic Pins anchored on Bitcoin.",
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
                BitPolitoBadge("Snap Authentic Pin", kind = "solid", icon = DeckIcon.Camera)
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
 * Pin & Thread Detail Lightbox (Pinterest Pin + X Replies + Nostr Zaps & Proof)
 * ============================================================================ */

@Composable
private fun PinDetailDialog(
    selected: PhotoPost,
    entry: FeedEntry?,
    state: PhotoAnchorState,
    isSaved: Boolean,
    isLiked: Boolean,
    isReposted: Boolean,
    isFollowing: Boolean,
    zapSats: Int,
    assignedBoard: String,
    boards: List<String>,
    replies: List<ReplyComment>,
    onToggleSave: (String?) -> Unit,
    onToggleLike: () -> Unit,
    onToggleRepost: () -> Unit,
    onToggleFollow: () -> Unit,
    onZap: () -> Unit,
    onAddReply: (String) -> Unit,
    onVerifyAgain: () -> Unit,
    onDismiss: () -> Unit,
    onCheckCopy: () -> Unit,
    onExportBundle: () -> Unit,
) {
    val p = LocalPalette.current
    val creatorName = entry?.name?.ifBlank { "Creator" } ?: "Creator"
    val handle = handleOf(creatorName, selected.pubkey)
    val title = selected.caption.ifBlank { "Untitled Pin" }
    val timeAgo = formatRelativeTime(selected.createdAt)
    var showProofDetails by remember { mutableStateOf(false) }
    var showBoardPicker by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var zapBurstKey by remember { mutableIntStateOf(0) }

    val tsUpper = (state.verification?.timestamp ?: "").uppercase()
    val (anchorLabel, anchorKind) = when {
        tsUpper.startsWith("CONFIRMED") -> "Anchored on Bitcoin" to "ok"
        tsUpper.startsWith("PENDING") -> "Bitcoin Anchor Pending" to "pending"
        tsUpper.startsWith("INVALID") -> "Invalid Proof" to "err"
        else -> "Bitcoin Verified Pin" to "default"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SurfCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .widthIn(max = 520.dp),
            padding = 18.dp,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Creator Header + Follow + Pinterest Save Button
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
                                text = "$handle · $timeAgo",
                                color = p.mu,
                                fontSize = 11.5.sp,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (selected.pubkey != state.pubkey) {
                            BitPolitoOutlinedButton(
                                text = if (isFollowing) "Following" else "Follow",
                                onClick = onToggleFollow,
                            )
                        }
                        BitPolitoPrimaryButton(
                            text = if (isSaved) "Saved" else "Save",
                            icon = DeckIcon.Bookmark,
                            onClick = { showBoardPicker = !showBoardPicker },
                        )
                    }
                }

                // Pinterest Board Picker Bar
                AnimatedVisibility(visible = showBoardPicker) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Save to Pinterest Board:", color = p.mu, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            boards.forEach { b ->
                                FilterChipPill(
                                    label = b,
                                    selected = assignedBoard == b && isSaved,
                                    icon = DeckIcon.Bookmark,
                                    onClick = {
                                        onToggleSave(b)
                                        showBoardPicker = false
                                    },
                                )
                            }
                        }
                    }
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
                                    text = "Double-tap to Zap",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                // Caption & Board Tag
                Text(
                    text = title,
                    color = p.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                )

                // Interactive Nostr × X × Pinterest Engagement Bar
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Zap ⚡
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
                                text = "Zap $zapSats sats",
                                color = p.ambText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }

                    SocialActionPill(
                        icon = DeckIcon.Share,
                        label = if (isReposted) "Reposted" else "Repost",
                        active = isReposted,
                        onClick = onToggleRepost,
                    )

                    SocialActionPill(
                        icon = DeckIcon.Heart,
                        label = if (isLiked) "Liked" else "Like",
                        active = isLiked,
                        onClick = onToggleLike,
                    )

                    BitPolitoBadge(
                        text = assignedBoard,
                        kind = "default",
                        icon = DeckIcon.Bookmark,
                    )
                }

                // Bitcoin Proof Status Pill + Expandable Actions
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
                        text = if (showProofDetails) "Hide Proof" else "Bitcoin Proof",
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

                // X-style Replies / Thread Section
                Text(
                    text = "Replies (${replies.size})",
                    color = p.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { if (it.length <= 280) replyText = it },
                        placeholder = { Text("Post your reply…") },
                        singleLine = true,
                        shape = RoundedCornerShape(99.dp),
                        modifier = Modifier.weight(1f),
                    )
                    BitPolitoPrimaryButton(
                        text = "Reply",
                        enabled = replyText.isNotBlank(),
                        onClick = {
                            onAddReply(replyText)
                            replyText = ""
                        },
                    )
                }

                replies.forEach { reply ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = p.soft,
                        border = BorderStroke(1.dp, p.bd),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${reply.author} ${reply.handle}",
                                    color = p.fg,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                )
                                Text(reply.time, color = p.mu, fontSize = 11.sp)
                            }
                            Text(reply.text, color = p.ink, fontSize = 13.sp)
                        }
                    }
                }

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

/* ============================================================================
 * Notification / Activity Center (Nostr Zaps ⚡ · X Social · Bitcoin Anchors)
 * ============================================================================ */

@Composable
private fun NotificationsDialog(
    notifications: List<ActivityNotification>,
    readIds: Set<String>,
    onMarkAllRead: () -> Unit,
    onSelectNotification: (ActivityNotification) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    var filter by remember { mutableStateOf("all") } // "all" | "zap" | "social" | "bitcoin"

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
                .padding(horizontal = 12.dp, vertical = 20.dp)
                .widthIn(max = 520.dp),
            padding = 18.dp,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.heightIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
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
                            PixelIcon(DeckIcon.Bell, size = 17.dp, tint = p.primaryFg)
                        }
                        Column {
                            Text(
                                text = "Notifications",
                                color = p.ink,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                            )
                            Text(
                                text = if (unreadTotal > 0) "$unreadTotal unread updates" else "All caught up",
                                color = p.mu,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    if (unreadTotal > 0) {
                        Text(
                            text = "Mark all read",
                            color = p.fg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.clickable(onClick = onMarkAllRead),
                        )
                    }
                }

                // Filter Chips: All · Zaps · Social · Bitcoin
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChipPill(
                        label = "All (${notifications.size})",
                        selected = filter == "all",
                        icon = DeckIcon.Bell,
                        onClick = { filter = "all" },
                    )
                    FilterChipPill(
                        label = "Zaps",
                        selected = filter == "zap",
                        icon = DeckIcon.Lightning,
                        onClick = { filter = "zap" },
                    )
                    FilterChipPill(
                        label = "Social",
                        selected = filter == "social",
                        icon = DeckIcon.Heart,
                        onClick = { filter = "social" },
                    )
                    FilterChipPill(
                        label = "Bitcoin",
                        selected = filter == "bitcoin",
                        icon = DeckIcon.Bitcoin,
                        onClick = { filter = "bitcoin" },
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = p.bd)

                if (filtered.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PixelIcon(DeckIcon.Bell, size = 28.dp, tint = p.mu)
                            Text(
                                text = "No notifications in this tab yet",
                                color = p.ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                            )
                            Text(
                                text = "Double-tap any photo to Zap +21 sats or save Pins to your Boards.",
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
                            val deckIcon = when (notif.iconName) {
                                "Lightning" -> DeckIcon.Lightning
                                "Bookmark" -> DeckIcon.Bookmark
                                "Heart" -> DeckIcon.Heart
                                "Share" -> DeckIcon.Share
                                "Message" -> DeckIcon.Message
                                "Bitcoin" -> DeckIcon.Bitcoin
                                "User" -> DeckIcon.User
                                else -> DeckIcon.Bell
                            }
                            val (iconBg, iconTint) = when (notif.kind) {
                                "zap" -> p.ambBg to p.amb
                                "bitcoin" -> p.okBg to p.ok
                                else -> p.soft to p.fg
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isUnread) p.soft else p.surf,
                                border = BorderStroke(1.dp, if (isUnread) p.fg else p.bd),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectNotification(notif) },
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(iconBg)
                                            .border(1.dp, p.bd, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        PixelIcon(deckIcon, size = 16.dp, tint = iconTint)
                                    }
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = notif.title,
                                                color = p.ink,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = notif.time,
                                                color = p.mu,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                        Text(
                                            text = notif.subtitle,
                                            color = p.mu,
                                            fontSize = 11.5.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 15.sp,
                                        )
                                    }
                                    if (isUnread) {
                                        Box(
                                            Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(p.fg),
                                        )
                                    }
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
