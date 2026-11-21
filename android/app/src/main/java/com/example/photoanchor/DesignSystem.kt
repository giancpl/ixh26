package com.example.photoanchor

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class BitPolitoPalette(
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

internal val LightBitPolito = BitPolitoPalette(
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

internal val DarkBitPolito = BitPolitoPalette(
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

internal val LocalPalette = staticCompositionLocalOf { LightBitPolito }

/** Official pixel-art SVG paths from design/decks/risorse grafiche/icons */
internal enum class DeckIcon(val vw: Float, val vh: Float, val pathData: String) {
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

/** Animated BitPolito Pixel Lightning Bolt burst overlay shown on double-tap Zap (+21 sats) */
@Composable
internal fun BoxScope.LightningZapBurstOverlay(burstTrigger: Int) {
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
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF001CE0).copy(alpha = 0.92f))
                    .border(2.5.dp, Color(0xFFFCD34D), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PixelIcon(
                    icon = DeckIcon.Lightning,
                    size = 40.dp,
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

internal fun handleOf(name: String, pubkey: String): String {
    val slug = name.trim().lowercase().replace(Regex("[^a-z0-9_]+"), "").take(14)
    return if (slug.isNotEmpty()) "@$slug" else "@nostr_${pubkey.take(6)}"
}

internal fun boardForCaption(caption: String): String {
    val c = caption.lowercase()
    return when {
        "bitcoin" in c || "btc" in c || "sat" in c -> "Bitcoin"
        "street" in c || "city" in c || "urban" in c -> "Street"
        "arch" in c || "design" in c || "polito" in c -> "Architecture"
        "nature" in c || "sky" in c || "mountain" in c -> "Nature"
        else -> "Inspiration"
    }
}

@Composable
internal fun PixelIcon(
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
internal fun SurfCard(
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
internal fun BitPolitoPrimaryButton(
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
internal fun BitPolitoOutlinedButton(
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
internal fun BitPolitoBadge(
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
internal fun FilterChipPill(
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
internal fun BitPolitoThemeSwitch(isDark: Boolean, onToggle: () -> Unit) {
    val p = LocalPalette.current
    val knobOffset by animateDpAsState(if (isDark) 22.dp else 0.dp, label = "themeKnob")
    Box(
        Modifier
            .width(54.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(p.surf)
            .border(1.dp, p.bd, RoundedCornerShape(99.dp))
            .semantics { contentDescription = "Dark theme" }
            .toggleable(
                value = isDark,
                role = Role.Switch,
                onValueChange = { onToggle() },
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

internal fun initialsOf(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "BP" }

internal fun formatShortDate(epochSeconds: Long): String =
    runCatching {
        SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(epochSeconds * 1000L))
    }.getOrDefault("")
