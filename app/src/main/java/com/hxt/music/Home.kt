package com.hxt.music

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun HomeScreen(vm: MusicVM, dock: Dp, onOpenSettings: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        AnimatedVisibility(
            started,
            enter = fadeIn(tween(600)) + slideInVertically(tween(600, easing = Ease)) { -it / 2 },
        ) { Header(onOpenSettings) }
        AnimatedVisibility(
            started,
            enter = fadeIn(tween(600, 80)) + slideInVertically(tween(600, 80, Ease)) { it / 3 },
        ) { SearchRow(vm) }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.searching) ResultsBox(vm, dock) else LikedBox(vm, dock)
        }
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Logo(22)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(42.dp).clip(shape).background(Col.Panel).border(1.dp, Col.Line, shape).pressable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) { Svg(Ic.GEAR, 20.dp, Col.Text, stroke = 2f) }
    }
}

@Composable
private fun SearchRow(vm: MusicVM) {
    val focus = LocalFocusManager.current
    val backW by animateDpAsState(if (vm.searching) 60.dp else 0.dp, tween(400, easing = Ease), label = "backW")
    val backM by animateDpAsState(if (vm.searching) 12.dp else 0.dp, tween(400, easing = Ease), label = "backM")
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(backW).height(60.dp).clip(shape).background(Col.Panel).border(1.dp, Col.Line, shape)
                .pressable(enabled = vm.searching) { focus.clearFocus(); vm.exitSearch() },
            contentAlignment = Alignment.Center,
        ) { Svg(Ic.BACK, 24.dp, Col.Text, stroke = 2.4f) }
        Spacer(Modifier.width(backM))
        SearchField(vm, Modifier.weight(1f))
    }
}

@Composable
private fun SearchField(vm: MusicVM, modifier: Modifier) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val has = vm.query.isNotEmpty()
    val shape = RoundedCornerShape(18.dp)
    val borderC by animateColorAsState(if (focused) Col.Red else Col.Line, tween(150), label = "sb")
    val endPad by animateDpAsState(if (has) 142.dp else 106.dp, tween(250, easing = Ease), label = "ep")
    val loading = vm.searchState == SearchState.Loading
    val inf = rememberInfiniteTransition(label = "pulse")
    val pulse = inf.animateFloat(1f, 1.3f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "pu")

    Box(modifier.height(60.dp).clip(shape).background(Col.Panel).border(1.dp, borderC, shape)) {
        Svg(
            Ic.SEARCH, 22.dp, Col.RedHi, stroke = 2.2f,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp)
                .graphicsLayer { val s = if (loading) pulse.value else 1f; scaleX = s; scaleY = s },
        )
        BasicTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            singleLine = true,
            textStyle = TextStyle(color = Col.Text, fontSize = 16.sp, fontFamily = Fonts.body, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(Col.RedHi),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.submit() }),
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth()
                .padding(start = 54.dp, end = endPad)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) vm.enterSearch() },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (!has) Text(
                        "Найти песню или исполнителя",
                        color = Color(0xFF6F545B), fontSize = 16.sp, fontFamily = Fonts.body,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    inner()
                }
            },
        )
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                has,
                enter = scaleIn(tween(350, easing = Ease), initialScale = .3f) + fadeIn(tween(200)),
                exit = scaleOut(tween(200), targetScale = .3f) + fadeOut(tween(150)),
            ) {
                Box(
                    Modifier.padding(end = 8.dp).size(38.dp).clip(CircleShape)
                        .background(Color(0xFFFF2D4D).copy(alpha = .16f))
                        .pressable { vm.clearQuery() },
                    contentAlignment = Alignment.Center,
                ) { Svg(Ic.CLEAR, 20.dp, Color(0xFFFF2D4D), stroke = 3.2f) }
            }
            Box(
                Modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Col.RedHi, Col.RedDeep)))
                    .pressable(scaleDown = .95f) { focus.clearFocus(); vm.submit() }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { T("Найти", 15, FontWeight.Bold) }
        }
    }
}

// ---------- Liked ----------

@Composable
private fun LikedBox(vm: MusicVM, dock: Dp) {
    val list = vm.liked
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Liked", 17, FontWeight.SemiBold, display = true)
            Spacer(Modifier.width(10.dp))
            val cnt = when {
                vm.editing -> "Перетащите, чтобы изменить порядок"
                list.isNotEmpty() -> "${list.size} треков"
                else -> ""
            }
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    cnt,
                    transitionSpec = { (fadeIn(tween(400)) + slideInHorizontally(tween(400, easing = Ease)) { -it / 8 }) togetherWith fadeOut(tween(150)) },
                    label = "cnt",
                ) { T(it, 13, FontWeight.Medium, Col.Muted) }
            }
            AnimatedVisibility(
                list.isNotEmpty(),
                enter = scaleIn(tween(350, easing = Ease), initialScale = .6f) + fadeIn(),
                exit = scaleOut(targetScale = .6f) + fadeOut(),
            ) { EditButton(vm.editing) { vm.editing = !vm.editing } }
        }
        if (list.isEmpty()) {
            EmptyBox("Здесь пока пусто", "Нажмите ♥ у любого трека или импортируйте список в настройках.")
        } else {
            LikedList(vm, dock)
        }
    }
}

@Composable
private fun EditButton(editing: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    val bg = if (editing) Modifier.background(Brush.linearGradient(listOf(Col.RedHi, Col.RedDeep)))
    else Modifier.background(Col.Panel).border(1.dp, Col.Line, shape)
    Box(Modifier.size(40.dp).clip(shape).then(bg).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Crossfade(editing, animationSpec = tween(300), label = "editIcon") { e ->
            if (e) Svg(Ic.CHECK, 20.dp, Color.White, stroke = 3f) else Svg(Ic.PEN, 19.dp, Col.Muted, stroke = 2.2f)
        }
    }
}

@Composable
private fun LikedList(vm: MusicVM, dock: Dp) {
    val lazy = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(lazy) { from, to -> vm.moveLiked(from.index, to.index) }
    val seen = remember { mutableSetOf<String>() }
    val curId = vm.current?.videoId

    LazyColumn(
        state = lazy,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = dock),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(vm.liked, key = { _, t -> t.videoId }) { i, t ->
            ReorderableItem(reorder, key = t.videoId) { dragging ->
                val first = remember { seen.add(t.videoId) }
                TrackRow(
                    t = t,
                    cover = remember(t.videoId, t.downloaded) { vm.cover(t) },
                    current = t.videoId == curId,
                    playingNow = vm.isPlaying,
                    index = i,
                    animateIn = first && i < 14,
                    showHandle = vm.editing,
                    handle = Modifier.draggableHandle(enabled = vm.editing, onDragStopped = { vm.commitOrder() }),
                    dragging = dragging,
                    onClick = if (vm.editing) null else ({ vm.play(vm.liked, i) }),
                    onLike = { vm.toggleLike(t) },
                )
            }
        }
    }
}

// ---------- Результаты поиска ----------

@Composable
private fun ResultsBox(vm: MusicVM, dock: Dp) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Результаты", 17, FontWeight.SemiBold, display = true)
            Spacer(Modifier.width(10.dp))
            if (vm.searchState == SearchState.Results) T("${vm.results.size} найдено", 13, FontWeight.Medium, Col.Muted)
        }
        when (vm.searchState) {
            SearchState.Hint -> EmptyBox("Что послушаем?", "Введите название песни или исполнителя и нажмите «Найти».", big = true)
            SearchState.Loading -> Skeleton()
            SearchState.Empty -> EmptyBox("Ничего не найдено", "Попробуйте изменить запрос.")
            SearchState.Error -> EmptyBox("Ошибка поиска", "Проверьте подключение к интернету и попробуйте ещё раз.")
            SearchState.Results -> {
                val seen = remember(vm.results) { mutableSetOf<String>() }
                val curId = vm.current?.videoId
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = dock),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(vm.results, key = { _, t -> t.videoId }) { i, t ->
                        val first = remember { seen.add(t.videoId) }
                        TrackRow(
                            t = t,
                            cover = remember(t.videoId, t.downloaded) { vm.cover(t) },
                            current = t.videoId == curId,
                            playingNow = vm.isPlaying,
                            index = i,
                            animateIn = first && i < 14,
                            showHandle = false,
                            handle = Modifier,
                            dragging = false,
                            onClick = { vm.play(vm.results, i) },
                            onLike = { vm.toggleLike(t) },
                        )
                    }
                }
            }
        }
    }
}
