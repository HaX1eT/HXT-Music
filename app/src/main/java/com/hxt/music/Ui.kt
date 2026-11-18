package com.hxt.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

// ---------- текст ----------

@Composable
fun T(
    text: String,
    size: Int = 15,
    weight: FontWeight = FontWeight.Medium,
    color: Color = Col.Text,
    modifier: Modifier = Modifier,
    display: Boolean = false,
    maxLines: Int = 1,
    align: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text, modifier, color, size.sp,
        fontWeight = weight,
        fontFamily = if (display) Fonts.display else Fonts.body,
        textAlign = align,
        lineHeight = lineHeight,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
    )
}

@Composable
fun Logo(size: Int, muted: Boolean = false) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = if (muted) Col.Muted else Col.Text)) { append("HXT ") }
            withStyle(SpanStyle(color = Col.RedHi)) { append("Music") }
        },
        fontFamily = Fonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = size.sp,
        letterSpacing = (-0.02).em,
    )
}

// ---------- взаимодействие ----------

/** Клик без ripple, но с «нажимающейся» кнопкой (как button:active{scale} в CSS) */
fun Modifier.pressable(
    enabled: Boolean = true,
    scaleDown: Float = .92f,
    onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) scaleDown else 1f, tween(150), label = "press")
    this
        .graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun IconBtn(size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun Heart(on: Boolean, onClick: () -> Unit, size: Dp = 38.dp, icon: Dp = 20.dp) {
    val pop = remember { Animatable(1f) }
    var prev by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on && !prev) {
            pop.animateTo(1.5f, tween(190))
            pop.animateTo(1f, tween(360))
        }
        prev = on
    }
    val color by animateColorAsState(if (on) Col.RedHi else Col.Muted, tween(250), label = "heart")
    Box(Modifier.size(size).clip(CircleShape).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Svg(
            Ic.HEART, icon, color, stroke = 2f, fill = on,
            modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
    }
}

@Composable
fun Cover(model: Any?, size: Dp, radius: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(radius)).background(Col.Card)) {
        if (model != null) AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun Spinner(size: Dp = 18.dp) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = Col.RedHi,
        trackColor = Col.RedDeep,
        strokeWidth = 2.dp,
    )
}

// ---------- эквалайзер, скелетон, пустое состояние ----------

@Composable
fun Eq(playing: Boolean) {
    val inf = rememberInfiniteTransition(label = "eq")
    val bars = (0..2).map { i ->
        inf.animateFloat(
            .3f, 1f,
            infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(i * 170)),
            label = "bar$i",
        )
    }
    Row(
        Modifier.padding(start = 9.dp).height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { s ->
            Box(
                Modifier.width(3.dp).fillMaxHeight()
                    .graphicsLayer {
                        scaleY = if (playing) s.value else .3f
                        transformOrigin = TransformOrigin(.5f, 1f)
                    }
                    .background(Col.RedHi, RoundedCornerShape(2.dp)),
            )
        }
    }
}

private fun Modifier.shimmer(x: State<Float>): Modifier = drawBehind {
    val w = size.width
    drawRect(
        Brush.horizontalGradient(
            listOf(Col.Card, Color(0xFF2C1419), Col.Card),
            startX = x.value * w, endX = x.value * w + w,
        ),
    )
}

@Composable
fun Skeleton() {
    val inf = rememberInfiniteTransition(label = "sh")
    val x = inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(6) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).shimmer(x))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(.6f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer(x))
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth(.35f).height(10.dp).clip(RoundedCornerShape(6.dp)).shimmer(x))
                }
            }
        }
    }
}

@Composable
fun EmptyBox(title: String, text: String, big: Boolean = false) {
    val line = Col.Line
    Column(
        Modifier.fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    line, cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))),
                )
            }
            .padding(if (big) 46.dp else 34.dp, if (big) 46.dp else 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T(title, 16, FontWeight.Bold, maxLines = 2, align = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        T(text, 15, FontWeight.Medium, Col.Muted, maxLines = 4, align = TextAlign.Center)
    }
}

// ---------- ползунок перемотки ----------

@Composable
fun SeekBar(value: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val cb by rememberUpdatedState(onSeek)
    val shown = drag ?: value
    val r = with(LocalDensity.current) { 6.5.dp.toPx() }
    Canvas(
        modifier
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> cb(((o.x - r) / (size.width - 2 * r)).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = ((o.x - r) / (size.width - 2 * r)).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { cb(it) }; drag = null },
                    onDragCancel = { drag = null },
                    onHorizontalDrag = { c, _ ->
                        c.consume()
                        drag = ((c.position.x - r) / (size.width - 2 * r)).coerceIn(0f, 1f)
                    },
                )
            },
    ) {
        val w = size.width
        val cy = size.height / 2
        val th = 5.dp.toPx()
        drawRoundRect(Col.Track, Offset(r, cy - th / 2), Size(w - 2 * r, th), CornerRadius(th / 2))
        val px = r + shown.coerceIn(0f, 1f) * (w - 2 * r)
        drawRoundRect(Col.RedHi, Offset(r, cy - th / 2), Size(px - r, th), CornerRadius(th / 2))
        drawCircle(Color.White, r, Offset(px, cy))
    }
}

// ---------- кнопка Play с пульсирующим кольцом ----------

@Composable
fun PlayButton(size: Dp, icon: Dp, playing: Boolean, onClick: () -> Unit, glow: Boolean = false) {
    val inf = rememberInfiniteTransition(label = "ring")
    val p = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing)), label = "p")
    val base = Modifier.size(size)
    Box(
        base
            .drawBehind {
                if (playing) {
                    val r = this.size.minDimension / 2
                    drawCircle(Col.RedHi.copy(alpha = .5f * (1f - p.value)), radius = r + 16.dp.toPx() * p.value)
                }
            }
            .then(if (glow) Modifier.shadow(20.dp, CircleShape, ambientColor = Col.RedHi, spotColor = Col.RedHi) else Modifier)
            .clip(CircleShape)
            .background(Col.Red)
            .pressable(scaleDown = .94f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Svg(if (playing) Ic.PAUSE else Ic.PLAY, icon, Color.White, fill = true)
    }
}

// ---------- строка трека ----------

@Composable
fun TrackRow(
    t: Track,
    cover: Any?,
    current: Boolean,
    playingNow: Boolean,
    index: Int,
    animateIn: Boolean,
    showHandle: Boolean,
    handle: Modifier,
    dragging: Boolean,
    onClick: (() -> Unit)?,
    onLike: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val a = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animateIn) {
            delay(index.coerceAtMost(14) * 45L)
            a.animateTo(1f, tween(550, easing = Ease))
        }
    }
    val dragScale by animateFloatAsState(if (dragging) 1.025f else 1f, tween(300, easing = Ease), label = "drag")
    val borderC by animateColorAsState(
        when {
            dragging -> Col.Red
            current -> Col.RedDeep
            else -> Color.Transparent
        },
        tween(200), label = "rowBorder",
    )
    val bg = when {
        dragging -> Brush.horizontalGradient(listOf(Col.Card, Col.Card))
        current -> Brush.horizontalGradient(listOf(Col.Red.copy(alpha = .22f), Color.Transparent))
        else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val handleW by animateDpAsState(if (showHandle) 38.dp else 0.dp, tween(450, easing = Ease), label = "handleW")
    val wide = LocalConfiguration.current.screenWidthDp > 760

    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                alpha = a.value
                translationY = (1f - a.value) * 16.dp.toPx()
                scaleX = dragScale; scaleY = dragScale
            }
            .then(if (dragging) Modifier.shadow(18.dp, shape) else Modifier)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderC, shape)
            .then(if (onClick != null) Modifier.pressable(scaleDown = .99f, onClick = onClick) else Modifier)
            .padding(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(cover, 48.dp, 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(t.title, 15, FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                if (current) Eq(playingNow)
            }
            T(t.artist, 13, FontWeight.Medium, Col.Muted)
        }
        if (wide && t.duration.isNotEmpty()) {
            Spacer(Modifier.width(14.dp))
            T(t.duration, 13, color = Col.Muted)
        }
        Spacer(Modifier.width(14.dp))
        Heart(t.liked, onLike)
        Box(
            Modifier.width(handleW).height(38.dp).clip(RoundedCornerShape(11.dp)).then(handle),
            contentAlignment = Alignment.Center,
        ) {
            if (handleW > 8.dp) Svg(Ic.DOTS, 18.dp, if (dragging) Col.RedHi else Col.Muted, fill = true)
        }
    }
}
