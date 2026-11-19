package com.hxt.music

import android.app.Application
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import okhttp3.OkHttpClient
import com.yausername.youtubedl_android.YoutubeDL
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.TimeUnit

class App : Application() {
    lateinit var player: ExoPlayer
    lateinit var queuePlayer: QueuePlayer
    lateinit var lib: Library

    override fun onCreate() {
        super.onCreate()
        val http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        NewPipe.init(NpDownloader(http))
        // yt-dlp: инициализация + обновление в фоне (YouTube часто меняется, свежий yt-dlp важен)
        val init = runCatching { YoutubeDL.getInstance().init(this) }
        Music.ytdlpReady = init.isSuccess
        Dbg.log("yt-dlp init: " + (if (init.isSuccess) "ок" else "ОШИБКА ${init.exceptionOrNull()}"))
        if (Music.ytdlpReady) {
            Thread {
                synchronized(Music.ytLock) {
                    val r = runCatching { YoutubeDL.getInstance().updateYoutubeDL(this) }
                    Dbg.log("yt-dlp update: " + (if (r.isSuccess) "${r.getOrNull()}" else "ОШИБКА ${r.exceptionOrNull()}"))
                }
            }.start()
        }
        lib = Library(this, http)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        queuePlayer = QueuePlayer(player)
    }
}
