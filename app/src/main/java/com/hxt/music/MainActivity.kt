package com.hxt.music

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {
    private val vm: MusicVM by viewModels()
    private var controller: ListenableFuture<MediaController>? = null
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val storagePerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.syncBackup() }
    private var askBackup by mutableStateOf(false)
    private val prefs by lazy { getSharedPreferences("hxt", MODE_PRIVATE) }

    private fun requestBackupAccess() {
        askBackup = false
        val i = Backup.settingsIntent(this)
        if (i == null) { storagePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE); return }
        try { startActivity(i) } catch (e: Exception) {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        Fonts.init(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        askBackup = !Backup.hasAccess(this) && !prefs.getBoolean("backup_declined", false)
        setContent {
            HxtApp(vm, onBackup = { if (Backup.hasAccess(this)) vm.mergeBackup() else requestBackupAccess() })
            if (askBackup) BackupDialog(
                onAllow = { requestBackupAccess() },
                onLater = { askBackup = false; prefs.edit().putBoolean("backup_declined", true).apply() },
            )
        }
    }

    // Вернулись из настроек (или просто открыли приложение): восстановить/обновить liked.json
    override fun onResume() {
        super.onResume()
        if (Backup.hasAccess(this)) { askBackup = false; vm.syncBackup() }
    }

    // Подключаемся к сервису, чтобы он жил как медиа-сервис с уведомлением и кнопками на экране блокировки
    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controller = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }
}

@Composable
fun HxtApp(vm: MusicVM, onBackup: () -> Unit) {
    var full by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var miniH by remember { mutableStateOf(160.dp) }
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dock = miniH + navBottom + 14.dp

    var playerIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { playerIn = true }

    BackHandler(enabled = full) { full = false }
    BackHandler(enabled = !full && vm.searching) { vm.exitSearch() }

    Box(
        Modifier.fillMaxSize().background(Col.Bg).drawBehind {
            drawRect(
                Brush.radialGradient(
                    0f to Color(0xFF3A0912), 1f to Color.Transparent,
                    center = Offset(size.width / 2f, -size.height * .03f), radius = size.width * 1.05f,
                ),
            )
        },
    ) {
        HomeScreen(vm, dock) { settings = true }

        AnimatedVisibility(
            playerIn,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(700, 250)) + slideInVertically(tween(700, 250)) { it / 2 },
        ) {
            MiniPlayer(
                vm, onOpen = { if (vm.current != null) full = true },
                modifier = Modifier.navigationBarsPadding().imePadding().padding(bottom = 10.dp)
                    .onSizeChanged { miniH = with(density) { it.height.toDp() } + 10.dp },
            )
        }

        FullPlayer(vm, full) { full = false }

        // тост
        var last by remember { mutableStateOf("") }
        LaunchedEffect(vm.toastMsg) { vm.toastMsg?.let { last = it } }
        AnimatedVisibility(
            vm.toastMsg != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = dock + 8.dp),
            enter = fadeIn(tween(250)) + slideInVertically(tween(400)) { it / 2 },
            exit = fadeOut(tween(250)),
        ) {
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier.padding(horizontal = 20.dp).background(Col.Card, shape)
                    .border(1.dp, Col.RedDeep, shape).padding(horizontal = 18.dp, vertical = 10.dp),
            ) { T(last, 14, maxLines = 3) }
        }
    }

    if (settings) ImportDialog(vm, onBackup) { settings = false }
}
