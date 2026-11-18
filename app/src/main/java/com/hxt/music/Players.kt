package com.hxt.music

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
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
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        Art(vm, t, model)
                        Info(vm, t)
                    }
                }
            }
        }
    }
}

@Composable
private fun Art(vm: MusicVM, t: Track?, model: Any?) {
    val cfg = LocalConfiguration.current
    val artSize = minOf(cfg.screenWidthDp * .84f, cfg.screenHeightDp * .38f, 440f).dp
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
            // «Текст» — заглушка, как в веб-версии: выглядит так же, но не нажимается
            Pill(Modifier.weight(1f), "Текст", Ic.LYRICS, stub = true)
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
