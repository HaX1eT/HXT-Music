package com.hxt.music

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlin.math.abs
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// ---------- мини-плеер ----------

@Composable
fun MiniPlayer(vm: MusicVM, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val t = vm.current
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 10.dp)
            .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color(0xFF180A0E).copy(alpha = .95f))
            .border(1.dp, Col.RedHi.copy(alpha = .22f), shape)
            .padding(start = 10.dp, top = 10.dp, end = 14.dp, bottom = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = t != null) { onOpen() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val coverModel = remember(t?.videoId, t?.downloaded) { t?.let { vm.cover(it) } }
            Cover(
                coverModel, 48.dp, 15.dp,
                Modifier.shadow(10.dp, RoundedCornerShape(15.dp), ambientColor = Col.Red, spotColor = Col.Red),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(t?.title ?: "Ничего не играет", 15, FontWeight.Bold)
                if (vm.preparing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spinner(14.dp)
                        Spacer(Modifier.width(8.dp))
                        T("Загрузка трека (один раз)…", 13, color = Col.Muted)
                    }
                } else {
                    T(t?.artist ?: "Выберите трек", 13, color = Col.Muted)
                }
            }
            if (t != null) Heart(t.liked, { vm.toggleLike(t) })
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(38.dp, { vm.step(-1) }) { Svg(Ic.PREV, 20.dp, Col.Text, fill = true) }
                PlayButton(48.dp, 22.dp, vm.isPlaying, { vm.toggle() })
                IconBtn(38.dp, { vm.step(1) }) { Svg(Ic.NEXT, 20.dp, Col.Text, fill = true) }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(fmtTime(vm.positionMs), 12, color = Col.Muted)
                SeekBar(
                    if (vm.durationMs > 0) vm.positionMs.toFloat() / vm.durationMs else 0f,
                    { vm.seekFraction(it) },
                    Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                T(fmtTime(vm.durationMs), 12, color = Col.Muted)
            }
        }
    }
}

// ---------- полноэкранный плеер ----------

@Composable
fun FullPlayer(vm: MusicVM, visible: Boolean, onClose: () -> Unit) {
    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(500, easing = Ease)) { it / 16 },
        exit = fadeOut(tween(250)) + slideOutVertically(tween(400, easing = Ease)) { it / 16 },
    ) {
        val t = vm.current
        val model = t?.let { vm.cover(it) }
        Box(
            Modifier.fillMaxSize().background(Col.Bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        ) {
            // размытый фон из обложки
            Crossfade(model, animationSpec = tween(900), label = "fbg") { m ->
                if (m != null) AsyncImage(
                    m, null,
                    Modifier.fillMaxSize().scale(1.3f).blur(70.dp).alpha(.5f),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Col.Bg.copy(alpha = .35f),
                        .75f to Col.Bg.copy(alpha = .92f),
                        1f to Col.Bg.copy(alpha = .92f),
                    ),
                ),
            )

            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp).padding(top = 18.dp, bottom = 34.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(38.dp, onClose) { Svg(Ic.CHEVRON, 26.dp, Col.Text, stroke = 2.2f) }
                    Logo(15, muted = true)
                    Spacer(Modifier.width(38.dp))
                }
                // загружаем текст, когда он открыт и трек сменился
                val lyricsOn = vm.showLyrics && t != null
                LaunchedEffect(t?.videoId, lyricsOn) { if (lyricsOn && t != null) vm.loadLyrics(t) }
                // Карточка текста стоит ровно на месте обложки и имеет тот же размер (rememberArtSize)
                Crossfade(lyricsOn, Modifier.weight(1f).fillMaxWidth(), animationSpec = tween(400), label = "lyr") { lyr ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(28.dp),
                        ) {
                            if (lyr && t != null) LyricsCard(vm, t, model, Modifier.size(rememberArtSize()))
                            else Art(vm, t, model)
                            Info(vm, t)
                        }
                    }
                }
            }
        }
    }
}

/** Сторона квадратной обложки. Её же использует карточка текста, чтобы размеры совпадали абсолютно. */
@Composable
private fun rememberArtSize(): Dp {
    val cfg = LocalConfiguration.current
    return minOf(cfg.screenWidthDp * .84f, cfg.screenHeightDp * .38f, 440f).dp
}

@Composable
private fun Art(vm: MusicVM, t: Track?, model: Any?) {
    val artSize = rememberArtSize()
    val tx by animateFloatAsState(if (vm.isPlaying) 16f else 14f, tween(900, easing = Ease), label = "tx")
    val rot by animateFloatAsState(if (vm.isPlaying) 0f else 2.5f, tween(900, easing = Ease), label = "rot")
    val vid = t?.videoId
    val appear = remember(vid) { Animatable(0f) }
    LaunchedEffect(vid) { appear.animateTo(1f, tween(600, easing = Ease)) }
    val shape = RoundedCornerShape(26.dp)

    Box(Modifier.size(artSize)) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { translationX = tx.dp.toPx(); translationY = tx.dp.toPx(); rotationZ = rot; alpha = .75f }
                .background(Brush.linearGradient(listOf(Col.RedHi, Col.RedDeep)), shape),
        )
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val s = .93f + .07f * appear.value
                    scaleX = s; scaleY = s; alpha = appear.value
                }
                .shadow(30.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape).background(Col.Card),
        ) {
            if (model != null) AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun Info(vm: MusicVM, t: Track?) {
    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(t?.title ?: "", 22, FontWeight.ExtraBold, display = true, maxLines = 2, lineHeight = androidx.compose.ui.unit.TextUnit(25f, androidx.compose.ui.unit.TextUnitType.Sp))
                Spacer(Modifier.height(6.dp))
                T(t?.artist ?: "", 16, color = Col.Muted)
            }
            if (t != null) Heart(t.liked, { vm.toggleLike(t) }, size = 48.dp, icon = 26.dp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(fmtTime(vm.positionMs), 12, color = Col.Muted)
            SeekBar(
                if (vm.durationMs > 0) vm.positionMs.toFloat() / vm.durationMs else 0f,
                { vm.seekFraction(it) },
                Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            T(fmtTime(vm.durationMs), 12, color = Col.Muted)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconBtn(56.dp, { vm.step(-1) }) { Svg(Ic.PREV, 28.dp, Col.Text, fill = true) }
            PlayButton(78.dp, 32.dp, vm.isPlaying, { vm.toggle() }, glow = true)
            IconBtn(56.dp, { vm.step(1) }) { Svg(Ic.NEXT, 28.dp, Col.Text, fill = true) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(
                Modifier.weight(1f), listOf("Повтор", "Повтор: всё", "Повтор: 1")[vm.repeat],
                Ic.REPEAT, on = vm.repeat > 0, onClick = { vm.cycleRepeat() },
            )
            Pill(Modifier.weight(1f), "Текст", Ic.LYRICS, on = vm.showLyrics, onClick = { vm.toggleLyrics() })
            Pill(Modifier.weight(1f), "Микс", Ic.SHUFFLE, on = vm.shuffle, onClick = { vm.toggleShuffle() })
        }
    }
}

@Composable
private fun Pill(
    modifier: Modifier,
    text: String,
    icon: Array<String>,
    on: Boolean = false,
    stub: Boolean = false,
    onClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(24.dp)
    val bg by animateColorAsState(if (on) Col.RedDeep else Col.Card.copy(alpha = .8f), tween(150), label = "pbg")
    val bd by animateColorAsState(if (on) Col.Red else Col.Line, tween(150), label = "pbd")
    val fg by animateColorAsState(if (on) Color.White else Col.Muted, tween(150), label = "pfg")
    Row(
        modifier.height(48.dp).clip(shape).background(bg).border(1.dp, bd, shape)
            .then(if (stub) Modifier else Modifier.pressable(onClick = onClick))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Svg(icon, 18.dp, fg, stroke = 2f)
        Spacer(Modifier.width(8.dp))
        T(text, 12, FontWeight.Bold, fg)
    }
}


// ---------- текст песни ----------

/** Карточка вместо обложки: обложка размывается и затемняется, поверх — центрированный текст. */
@Composable
private fun LyricsCard(vm: MusicVM, t: Track, model: Any?, modifier: Modifier) {
    val shape = RoundedCornerShape(26.dp)
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val blurR by animateDpAsState(if (entered) 28.dp else 0.dp, tween(800, easing = Ease), label = "lblur")
    val dim by animateFloatAsState(if (entered) 1f else 0f, tween(800, easing = Ease), label = "ldim")

    Box(modifier.clip(shape).background(Col.Card).border(1.dp, Col.Line, shape)) {
        Crossfade(model, animationSpec = tween(700), label = "lcover") { m ->
            if (m != null) AsyncImage(
                m, null,
                Modifier.fillMaxSize().scale(1.2f).blur(blurR),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Col.Bg.copy(alpha = .55f * dim),
                    .5f to Col.Bg.copy(alpha = .62f * dim),
                    1f to Col.Bg.copy(alpha = .78f * dim),
                ),
            ),
        )
        val ly = vm.lyrics
        when {
            vm.lyricsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(26.dp) }
            vm.lyricsError -> LyricsMessage("Не удалось загрузить текст", "Нажмите, чтобы повторить") { vm.loadLyrics(t, force = true) }
            ly == null -> LyricsMessage("Текст не найден", "Для этого трека на lrclib его нет")
            ly.instrumental -> LyricsMessage("♪", "Инструментальный трек")
            ly.synced -> SyncedLyrics(vm, ly)
            else -> PlainLyrics(ly)
        }
    }
}

@Composable
private fun LyricsMessage(title: String, sub: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().then(
            if (onClick != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() } else Modifier,
        ).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ThickText(
            title,
            TextStyle(color = Col.Text, fontSize = 22.sp, fontFamily = Fonts.lyrics, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center),
            Modifier.fillMaxWidth(), thickness = LYRIC_THICKEN,
        )
        Spacer(Modifier.height(8.dp))
        Text(sub, color = Col.Muted, fontSize = 14.sp, fontFamily = Fonts.body, textAlign = TextAlign.Center)
    }
}

/** Плавное затухание текста у верхнего и нижнего края карточки. */
private fun Modifier.fadeEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, .1f to Color.Black, .9f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

/** Дополнительное утолщение букв (dp): страхует, если Manrope не загрузился и взят системный шрифт. */
private val LYRIC_THICKEN = .5.dp

/** Текст с утолщением: поверх заливки рисуется обводка того же цвета (свечение — только у заливки). */
@Composable
private fun ThickText(text: String, style: TextStyle, modifier: Modifier = Modifier, thickness: Dp = LYRIC_THICKEN) {
    val px = with(LocalDensity.current) { thickness.toPx() }
    Box(modifier) {
        Text(text, Modifier.fillMaxWidth(), style = style)
        Text(
            text, Modifier.fillMaxWidth(),
            style = style.copy(shadow = null, drawStyle = Stroke(width = px, join = StrokeJoin.Round)),
        )
    }
}

private fun activeLine(lines: List<LyricLine>, posMs: Long): Int {
    var lo = 0
    var hi = lines.lastIndex
    var res = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].timeMs <= posMs) { res = mid; lo = mid + 1 } else hi = mid - 1
    }
    return res
}

@Composable
private fun SyncedLyrics(vm: MusicVM, ly: Lyrics) {
    val lines = ly.lines
    val state = rememberLazyListState()
    // +300 мс: позиция плеера обновляется раз в 250 мс, строка должна загораться чуть заранее
    val active by remember(ly) { derivedStateOf { activeLine(lines, vm.positionMs + 300) } }

    // пока пользователь листает сам — автоскролл не мешает
    var pauseUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state) {
        state.interactionSource.interactions.collect {
            when (it) {
                is DragInteraction.Start -> pauseUntil = Long.MAX_VALUE
                is DragInteraction.Stop, is DragInteraction.Cancel -> pauseUntil = System.currentTimeMillis() + 3500
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pad = maxHeight / 2
        LaunchedEffect(active, pauseUntil) {
            val wait = pauseUntil - System.currentTimeMillis()
            if (wait > 0) { if (pauseUntil == Long.MAX_VALUE) return@LaunchedEffect else kotlinx.coroutines.delay(wait) }
            val i = active.coerceAtLeast(0)
            fun centerDelta(): Float? {
                val li = state.layoutInfo
                val info = li.visibleItemsInfo.firstOrNull { it.index == i } ?: return null
                // центр видимой области в тех же координатах, что и offset элементов (учитывает contentPadding)
                val center = (li.viewportStartOffset + li.viewportEndOffset) / 2f
                return info.offset + info.size / 2f - center
            }
            var d = centerDelta()
            if (d == null) {
                // строка далеко и ещё не скомпонована — прыгаем к ней и после раскладки доводим до центра
                state.scrollToItem(i)
                androidx.compose.runtime.withFrameNanos { }
                d = centerDelta()
            }
            if (d != null && abs(d) > 1f) state.animateScrollBy(d, tween(500, easing = Ease))
        }
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().fadeEdges(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = pad, bottom = pad),
        ) {
            itemsIndexed(lines, key = { i, l -> "$i-${l.timeMs}" }) { i, l ->
                LyricRow(l.text, i - active) { vm.seekMs(l.timeMs) }
            }
        }
    }
}

@Composable
private fun LyricRow(text: String, rel: Int, onClick: () -> Unit) {
    val isActive = rel == 0
    val a by animateFloatAsState(if (isActive) 1f else 0f, tween(450, easing = Ease), label = "la")
    val rest = (.5f - .07f * (kotlin.math.abs(rel) - 1).coerceAtLeast(0)).coerceAtLeast(.2f)
    val alpha by animateFloatAsState(if (isActive) 1f else rest, tween(450, easing = Ease), label = "lal")
    ThickText(
        text,
        style = TextStyle(
            color = Col.Text,
            fontFamily = Fonts.lyrics,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 27.sp,
            lineHeight = 34.sp,
            textAlign = TextAlign.Center,
            shadow = Shadow(Col.RedHi.copy(alpha = .55f * a), blurRadius = 28f),
        ),
        modifier = Modifier.fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 10.dp)
            .graphicsLayer {
                val sc = .86f + .14f * a
                scaleX = sc; scaleY = sc
                this.alpha = alpha
                transformOrigin = TransformOrigin.Center
            },
        thickness = .7.dp,
    )
}

@Composable
private fun PlainLyrics(ly: Lyrics) {
    Column(
        Modifier.fillMaxSize().fadeEdges().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ly.lines.forEach { l ->
            if (l.text.isEmpty()) Spacer(Modifier.height(18.dp))
            else ThickText(
                l.text,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                style = TextStyle(
                    color = Col.Text.copy(alpha = .92f),
                    fontFamily = Fonts.lyrics,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                ),
                thickness = .5.dp,
            )
        }
        Spacer(Modifier.height(22.dp))
        Text("Текст без синхронизации", color = Col.Muted, fontSize = 12.sp, fontFamily = Fonts.body)
    }
}
