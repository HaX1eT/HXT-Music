package com.hxt.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Резервная копия Liked вне папки приложения: /storage/emulated/0/HXT Music/liked.json.
 * Только метаданные (id, название, автор, длительность, обложка-URL) и порядок — без аудио.
 * Переживает удаление приложения. После переустановки нужно снова выдать доступ к файлам.
 */
object Backup {
    private val file: File get() = File(File(Environment.getExternalStorageDirectory(), "HXT Music"), "liked.json")

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Android 11+: экран «Доступ ко всем файлам» для приложения; ниже — null (обычный runtime-запрос). */
    fun settingsIntent(ctx: Context): Intent? =
        if (Build.VERSION.SDK_INT >= 30)
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        else null

    fun read(): List<Track> = runCatching {
        if (!file.exists()) return emptyList()
        val a = JSONObject(file.readText()).getJSONArray("liked")
        List(a.length()) {
            val o = a.getJSONObject(it)
            Track(o.getString("id"), o.optString("title"), o.optString("artist"),
                o.optString("duration"), o.optString("thumb"), liked = true)
        }
    }.getOrElse { Dbg.log("backup read: $it"); emptyList() }

    /** Атомарная запись: во временный файл, затем rename — обрыв не портит старую копию. */
    fun write(tracks: List<Track>) {
        runCatching {
            val arr = JSONArray()
            tracks.forEach {
                arr.put(JSONObject().put("id", it.videoId).put("title", it.title).put("artist", it.artist)
                    .put("duration", it.duration).put("thumb", it.thumb))
            }
            val f = file
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp")
            tmp.writeText(JSONObject().put("version", 1).put("liked", arr).toString())
            f.delete()
            if (!tmp.renameTo(f)) throw java.io.IOException("rename failed")
        }.onFailure { Dbg.log("backup write: $it") }
    }
}
