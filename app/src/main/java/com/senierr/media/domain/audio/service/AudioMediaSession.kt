package com.senierr.media.domain.audio.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.senierr.base.support.ktx.showToast
import com.senierr.base.util.LogUtil
import com.senierr.media.R
import com.senierr.media.domain.audio.viewmodel.AudioControlViewModel
import com.senierr.media.domain.audio.viewmodel.BaseControlViewModel
import com.senierr.media.ktx.applicationViewModel
import com.senierr.media.repository.entity.LocalAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 媒体会话
 *
 * @author senierr_zhou
 * @date 2022/08/14
 */
@SuppressLint("RestrictedApi")
class AudioMediaSession(
    private val context: Context,
    tag: String
) : MediaSessionCompat(context, tag), CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Main) {

    companion object {
        private const val TAG = "AudioMediaSession"
    }

    private val controlViewModel: AudioControlViewModel by applicationViewModel()

    // 指令接收
    private val callback: Callback = object : Callback() {
        override fun onPlay() {
            LogUtil.logD(TAG, "onPlay")
            controlViewModel.play()
        }

        override fun onPause() {
            LogUtil.logD(TAG, "onPause")
            controlViewModel.pause(true)
        }

        override fun onSkipToNext() {
            val hasNext = controlViewModel.hasNextItem()
            LogUtil.logD(TAG, "onSkipToNext: $hasNext")
            if (hasNext) {
                controlViewModel.skipToNext()
            } else {
                context.showToast(R.string.has_no_next)
            }
        }

        override fun onSkipToPrevious() {
            val hasPrevious = controlViewModel.hasPreviousItem()
            LogUtil.logD(TAG, "onSkipToPrevious: $hasPrevious")
            if (hasPrevious) {
                controlViewModel.skipToPrevious()
            } else {
                context.showToast(R.string.has_no_previous)
            }
        }

        override fun onCustomAction(action: String, extras: Bundle) {
            Log.i(TAG, "onCustomAction: $action, $extras")
        }
    }

    init {
        setCallback(callback, Handler(Looper.getMainLooper()))
        initViewModel()
    }

    override fun release() {
        LogUtil.logD(TAG, "release")
        cancel()
        super.release()
    }

    private fun initViewModel() {
        controlViewModel.playingItem
            .onEach { notifyPlayingItemChanged(it) }
            .launchIn(this)
        controlViewModel.playStatus
            .onEach { notifyIsPlayingChanged(it) }
            .launchIn(this)
        controlViewModel.progress
            .onEach { notifyProgressChanged(it) }
            .launchIn(this)
        controlViewModel.playMode
            .onEach { notifyPlayModeChanged(it) }
            .launchIn(this)
    }

    /**
     * 播放媒体变更
     */
    private fun notifyPlayingItemChanged(playingItem: LocalAudio?) {
        LogUtil.logD(TAG, "notifyPlayingItemChanged: ${playingItem?.displayName}")
        if (playingItem == null) {
            setMetadata(null)
        } else {
            val mediaMetadata: MediaMetadataCompat = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, playingItem.displayName)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, playingItem.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, playingItem.getUri().toString())
                .build()
            setMetadata(mediaMetadata)
        }
    }

    /**
     * 播放状态变更
     */
    private fun notifyIsPlayingChanged(isPlaying: Boolean) {
        LogUtil.logD(TAG, "notifyIsPlayingChanged: $isPlaying")
        val state = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY
                        or PlaybackStateCompat.ACTION_PAUSE
                        or PlaybackStateCompat.ACTION_PLAY_PAUSE
                        or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, controlViewModel.progress.value.position, 1.0f)
            .build()
        setPlaybackState(playbackState)
    }

    /**
     * 播放进度变更
     */
    private fun notifyProgressChanged(progress: BaseControlViewModel.Progress) {
        val playingItem = controlViewModel.playingItem.value?: return
        // 更新总时长
        val mediaMetadata: MediaMetadataCompat = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, playingItem.id.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, playingItem.displayName)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, playingItem.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, playingItem.path)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, progress.duration)
            .build()
        setMetadata(mediaMetadata)
        // 更新进度
        val state = if (controlViewModel.isPlaying()) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY
                        or PlaybackStateCompat.ACTION_PAUSE
                        or PlaybackStateCompat.ACTION_PLAY_PAUSE
                        or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, progress.position, 1.0f)
            .build()
        setPlaybackState(playbackState)
    }

    /**
     * 播放模式变更
     */
    private fun notifyPlayModeChanged(playMode: BaseControlViewModel.PlayMode) {
        LogUtil.logD(TAG, "notifyPlayModeChanged: $playMode")
        when (playMode) {
            BaseControlViewModel.PlayMode.ONE -> {
                setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)
                setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE)
            }
            BaseControlViewModel.PlayMode.LIST -> {
                setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
                setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE)
            }
            BaseControlViewModel.PlayMode.ALL -> {
                setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL)
                setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE)
            }
            BaseControlViewModel.PlayMode.SHUFFLE -> {
                setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL)
                setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL)
            }
        }
    }
}