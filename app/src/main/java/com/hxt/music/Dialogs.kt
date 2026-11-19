package com.hxt.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ImportDialog(vm: MusicVM, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val frac by animateFloatAsState(
        if (vm.importTotal > 0) vm.importDone.toFloat() / vm.importTotal else 0f,
        tween(400), label = "imp",
    )
    val sheet = RoundedCornerShape(22.dp)
    val none = remember { MutableInteractionSource() }
    val clipboard = LocalClipboardManager.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f))
                .clickable(interactionSource = none, indication = null) { onDismiss() }
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 640.dp)
                    .clip(sheet).background(Col.Panel).border(1.dp, Col.Line, sheet)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .verticalScroll(rememberScrollState())
                    .padding(26.dp),
            ) {
                T("Импорт треков", 18, FontWeight.SemiBold, display = true)
                Spacer(Modifier.height(6.dp))
                T(
                    "Вставьте список: один трек на строку. Каждый найденный трек скачается один раз и попадёт в Liked.",
                    14, color = Col.Muted, maxLines = 5,
                )
                Spacer(Modifier.height(14.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = Col.Text, fontSize = 14.sp, lineHeight = 22.sp, fontFamily = Fonts.body, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(Col.RedHi),
                    modifier = Modifier.fillMaxWidth().height(190.dp)
                        .clip(RoundedCornerShape(14.dp)).background(Col.Bg)
                        .border(1.dp, Col.Line, RoundedCornerShape(14.dp)).padding(14.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text(
                                "First song - author\nSecond song\nAuthor - third song",
                                color = Color(0xFF6F545B), fontSize = 14.sp, lineHeight = 22.sp, fontFamily = Fonts.body,
                            )
                            inner()
                        }
                    },
                )
                Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp).height(6.dp).clip(RoundedCornerShape(6.dp)).background(Col.Track)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).background(Col.RedHi))
                }
                Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    vm.importItems.forEach { i ->
                        T(
                            "${if (i.ok) "✓" else "✕"} ${i.line} → ${i.found}",
                            13, color = if (i.ok) Color(0xFFE9C7CD) else Color(0xFFFF7A8C), maxLines = 2,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(Col.Card)
                        .border(1.dp, Col.Line, RoundedCornerShape(12.dp))
                        .pressable(scaleDown = .97f) {
                            clipboard.setText(AnnotatedString(Dbg.dump().ifBlank { "(лог пуст)" }))
                            vm.toast("Лог скопирован")
                        },
                    contentAlignment = Alignment.Center,
                ) { T("Скопировать лог", 13, FontWeight.SemiBold, color = Col.Muted) }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    Box(
                        Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(Col.Card)
                            .border(1.dp, Col.Line, RoundedCornerShape(12.dp)).pressable(scaleDown = .95f, onClick = onDismiss)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) { T("Закрыть", 15, FontWeight.Bold) }
                    Box(
                        Modifier.height(44.dp).alpha(if (vm.importRunning) .5f else 1f).clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Col.RedHi, Col.RedDeep)))
                            .pressable(enabled = !vm.importRunning, scaleDown = .95f) { vm.runImport(text) }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) { T("Импортировать", 15, FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun BackupDialog(onAllow: () -> Unit, onLater: () -> Unit) {
    val sheet = RoundedCornerShape(22.dp)
    val none = remember { MutableInteractionSource() }
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f))
                .clickable(interactionSource = none, indication = null) { onLater() }
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .clip(sheet).background(Col.Panel).border(1.dp, Col.Line, sheet)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(26.dp),
            ) {
                T("Сохранять Liked", 18, FontWeight.SemiBold, display = true)
                Spacer(Modifier.height(8.dp))
                T(
                    "Список Liked (только названия, без аудио) будет копироваться в файл HXT Music/liked.json во внутренней памяти. " +
                        "После удаления и новой установки треки вернутся в Liked, а скачаются при первом воспроизведении. " +
                        "Нужен доступ к файлам.",
                    14, color = Col.Muted, maxLines = 8,
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    Box(
                        Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(Col.Card)
                            .border(1.dp, Col.Line, RoundedCornerShape(12.dp)).pressable(scaleDown = .95f, onClick = onLater)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) { T("Не сейчас", 15, FontWeight.Bold) }
                    Box(
                        Modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Col.RedHi, Col.RedDeep)))
                            .pressable(scaleDown = .95f, onClick = onAllow)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) { T("Разрешить", 15, FontWeight.Bold) }
                }
            }
        }
    }
}
