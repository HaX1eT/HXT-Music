package com.hxt.music

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

class MusicVM(app: Application) : AndroidViewModel(app) {
    private val a = app as App
    private val lib = a.lib
    private val player = a.player

    // списки
    var liked by mutableStateOf<List<Track>>(emptyList())
    var results by mutableStateOf<List<Track>>(emptyList())

    // поиск
    var searching by mutableStateOf(false)
    var searchState by mutableStateOf(SearchState.Hint)
    var query by mutableStateOf("")

    // плеер
    var queue by mutableStateOf<List<Track>>(emptyList())
    var idx by mutableIntStateOf(-1)
    var isPlaying by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var preparing by mutableStateOf(false)
    var repeat by mutableIntStateOf(0)        // 0 выкл, 1 всё, 2 один трек
    var shuffle by mutableStateOf(false)

    // прочее
    var editing by mutableStateOf(false)
    var toastMsg by mutableStateOf<String?>(null)

    // импорт
    var importRunning by mutableStateOf(false)
    var importTotal by mutableIntStateOf(0)
    var importDone by mutableIntStateOf(0)
    var importItems by mutableStateOf<List<ImportItem>>(emptyList())

    val current: Track? get() = queue.getOrNull(idx)

    private var searchSeq = 0
    private var toastJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(p: Boolean) {
            isPlaying = p
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) onEnded()
        }
    }

    init {
        a.queuePlayer.onNext = { step(1) }
        a.queuePlayer.onPrev = { step(-1) }
        player.addListener(listener)
        isPlaying = player.isPlaying
        viewModelScope.launch {
            while (true) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = if (player.duration == C.TIME_UNSET) 0L else player.duration
                delay(250)
            }
        }
        refreshLiked()
    }

    override fun onCleared() {
        player.removeListener(listener)
        a.queuePlayer.onNext = {}
        a.queuePlayer.onPrev = {}
        super.onCleared()
    }

    // ---------- утилиты ----------

    fun toast(m: String) {
        toastMsg = m
        toastJob?.cancel()
        toastJob = viewModelScope.launch { delay(3200); toastMsg = null }
    }

    fun cover(t: Track): Any? {
        if (t.downloaded) {
            val f = lib.coverFile(t.videoId)
            if (f.exists()) return f
        }
        return t.thumb.ifBlank { null }
    }

    private fun patch(vid: String, isLiked: Boolean? = null, isDownloaded: Boolean? = null) {
        val f = { t: Track ->
            if (t.videoId == vid) t.copy(liked = isLiked ?: t.liked, downloaded = isDownloaded ?: t.downloaded) else t
        }
        results = results.map(f)
        liked = liked.map(f)
        queue = queue.map(f)
    }

    /** Восстановление/обновление backup Liked; вызывается при возврате в приложение. */
    fun syncBackup() {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { lib.syncBackup() }
            if (n > 0) {
                refreshLiked()
                toast("Восстановлено треков в Liked: $n")
            }
        }
    }

    /** Кнопка «Бэкап лайков»: объединяет локальный Liked и liked.json, результат пишет в liked.json. */
    fun mergeBackup() {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { lib.mergeBackup() }
            if (r == null) { toast("Нет доступа к файлам"); return@launch }
            refreshLiked()
            toast(if (r.first > 0) "Бэкап: добавлено из копии ${r.first}, всего ${r.second}" else "Бэкап обновлён, треков: ${r.second}")
        }
    }

    fun refreshLiked() {
        if (editing) return
        viewModelScope.launch { liked = withContext(Dispatchers.IO) { lib.likedTracks() } }
    }

    // ---------- Liked: порядок ----------

    fun moveLiked(from: Int, to: Int) {
        if (from == to) return
        liked = liked.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun commitOrder() {
        val ids = liked.map { it.videoId }
        viewModelScope.launch(Dispatchers.IO) { lib.saveOrder(ids) }
        val cur = current?.videoId
        val set = ids.toSet()
        if (cur != null && queue.size == liked.size && queue.all { it.videoId in set }) {
            queue = liked
            idx = liked.indexOfFirst { it.videoId == cur }
        }
    }

    // ---------- поиск ----------

    fun enterSearch() {
        if (searching) return
        searching = true
        editing = false
        if (results.isEmpty()) searchState = SearchState.Hint
    }

    fun exitSearch() {
        if (!searching) return
        searching = false
        searchSeq++
        query = ""
        results = emptyList()
        searchState = SearchState.Hint
        refreshLiked()
    }

    fun clearQuery() {
        query = ""
        results = emptyList()
        searchSeq++
        searchState = SearchState.Hint
    }

    fun submit() {
        enterSearch()
        val q = query.trim()
        if (q.isEmpty()) {
            results = emptyList(); searchSeq++; searchState = SearchState.Hint
            return
        }
        val my = ++searchSeq
        searchState = SearchState.Loading
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { Music.search(q).map { lib.decorate(it) } }
                if (my != searchSeq) return@launch
                results = data
                searchState = if (data.isEmpty()) SearchState.Empty else SearchState.Results
            } catch (e: Exception) {
                if (my == searchSeq) searchState = SearchState.Error
            }
        }
    }

    // ---------- воспроизведение ----------

    fun play(list: List<Track>, i: Int) {
        if (preparing) return
        queue = list
        idx = i
        startCurrent()
    }

    private fun startCurrent() {
        val t = queue.getOrNull(idx) ?: return
        viewModelScope.launch {
            var file = withContext(Dispatchers.IO) { lib.audioFile(t.videoId) }
            if (file == null) {
                preparing = true
                try {
                    lib.ensureDownloaded(t)           // скачиваем ОДИН раз
                    patch(t.videoId, isDownloaded = true)
                    file = withContext(Dispatchers.IO) { lib.audioFile(t.videoId) }
                } catch (e: Exception) {
                    toast("Не удалось загрузить: " + (e.message ?: "ошибка").take(80))
                    preparing = false
                    return@launch
                }
                preparing = false
            }
            val f = file ?: return@launch
            player.setMediaItem(buildItem(t, f))
            player.prepare()
            player.play()
            refreshLiked()
        }
    }

    private fun buildItem(t: Track, f: File): MediaItem {
        val md = MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist)
        val c = lib.coverFile(t.videoId)
        if (c.exists()) runCatching { md.setArtworkData(c.readBytes(), MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
        return MediaItem.Builder()
            .setMediaId(t.videoId)
            .setUri(Uri.fromFile(f))
            .setMediaMetadata(md.build())
            .build()
    }

    fun step(d: Int) {
        if (queue.isEmpty() || preparing) return
        var n = (idx + d + queue.size) % queue.size
        if (shuffle && queue.size > 1) {
            do { n = Random.nextInt(queue.size) } while (n == idx)
        }
        idx = n
        startCurrent()
    }

    private fun onEnded() {
        when {
            repeat == 2 -> { player.seekTo(0); player.play() }
            repeat == 0 && !shuffle && idx == queue.lastIndex -> Unit
            else -> step(1)
        }
    }

    fun toggle() {
        if (player.mediaItemCount == 0) return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekFraction(f: Float) {
        if (durationMs > 0) player.seekTo((f * durationMs).toLong())
    }

    fun cycleRepeat() { repeat = (repeat + 1) % 3 }
    fun toggleShuffle() { shuffle = !shuffle }

    // ---------- лайки ----------

    fun toggleLike(t: Track) {
        viewModelScope.launch {
            try {
                val on = lib.toggleLike(t)
                patch(t.videoId, isLiked = on, isDownloaded = true)
                refreshLiked()
            } catch (e: Exception) {
                toast("Ошибка: " + (e.message ?: "не удалось").take(80))
            }
        }
    }

    // ---------- импорт ----------

    fun runImport(text: String) {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty() || importRunning) return
        importRunning = true
        importTotal = lines.size
        importDone = 0
        importItems = emptyList()
        viewModelScope.launch {
            for (line in lines) {
                try {
                    val q = Regex("\\s*[-–—]\\s*").replaceFirst(line, " ")   // «Автор - Название» → «Автор Название»
                    val found = withContext(Dispatchers.IO) { Music.search(q, 1).firstOrNull() }
                    if (found != null) {
                        lib.ensureDownloaded(found)
                        lib.setLiked(found.videoId, true)
                        importItems = importItems + ImportItem(line, true, "${found.artist} — ${found.title}")
                    } else {
                        importItems = importItems + ImportItem(line, false, "не найдено")
                    }
                } catch (e: Exception) {
                    importItems = importItems + ImportItem(line, false, (e.message ?: "ошибка").take(80))
                }
                importDone++
            }
            importRunning = false
            editing = false
            refreshLiked()
            toast("Импорт завершён: ${importItems.count { it.ok }} из $importTotal")
        }
    }
}
