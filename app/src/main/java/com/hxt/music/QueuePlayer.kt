package com.hxt.music

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Обёртка над ExoPlayer для MediaSession: кнопки «вперёд/назад» в уведомлении
 * и на гарнитуре вызывают нашу очередь (в ExoPlayer всегда один трек).
 */
class QueuePlayer(p: Player) : ForwardingPlayer(p) {
    var onNext: () -> Unit = {}
    var onPrev: () -> Unit = {}

    private val extra = intArrayOf(
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    )

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(*extra).build()

    override fun isCommandAvailable(command: Int): Boolean =
        command in extra || super.isCommandAvailable(command)

    override fun hasNextMediaItem(): Boolean = true
    override fun hasPreviousMediaItem(): Boolean = true
    override fun seekToNext() = onNext()
    override fun seekToNextMediaItem() = onNext()
    override fun seekToPrevious() = onPrev()
    override fun seekToPreviousMediaItem() = onPrev()
}
