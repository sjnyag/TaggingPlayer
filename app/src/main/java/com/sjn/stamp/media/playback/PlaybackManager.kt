/*
* Copyright (C) 2014 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.sjn.stamp.media.playback

import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.sjn.stamp.R
import com.sjn.stamp.controller.CustomController
import com.sjn.stamp.controller.SongHistoryController
import com.sjn.stamp.controller.UserSettingController
import com.sjn.stamp.media.MediaLogger
import com.sjn.stamp.media.QueueManager
import com.sjn.stamp.media.StampSession
import com.sjn.stamp.media.provider.MusicProvider
import com.sjn.stamp.utils.*

/**
 * Manage the interactions among the container service, the queue manager and the actual playback.
 */
class PlaybackManager(
        private val service: MediaBrowserServiceCompat,
        private val serviceCallback: PlaybackServiceCallback,
        musicProvider: MusicProvider,
        playbackType: Playback.Type
) : Playback.Callback, MediaLogger.Listener, StampSession.SessionListener {

    private val session = StampSession(service, MediaSessionCallback(), this)
    private val queueManager = QueueManager(service, musicProvider, QueueUpdateListener())
    private val mediaLogger: MediaLogger = MediaLogger(this)
    private val availableActions: Long
        get() {
            return PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    if (playback.isPlaying) {
                        PlaybackStateCompat.ACTION_PAUSE
                    } else {
                        PlaybackStateCompat.ACTION_PLAY
                    }
        }

    var playback: Playback = playbackType.createInstance(service, this)

    val sessionToken: MediaSessionCompat.Token?
        get() = session.sessionToken

    init {
        updatePlaybackState(null)
    }

    fun restorePreviousState() {
        queueManager.restorePreviousState(UserSettingController().lastMusicId, UserSettingController().queueIdentifyMediaId)
        CustomController.setShuffleMode(service, PreferenceHelper.loadShuffle(service, PlaybackStateCompat.SHUFFLE_MODE_NONE))
        CustomController.setRepeatMode(service, PreferenceHelper.loadRepeat(service, PlaybackStateCompat.REPEAT_MODE_NONE))
        updatePlaybackState(null)
    }

    fun startNewQueue(title: String, mediaId: String, queueItemList: List<MediaSessionCompat.QueueItem>) {
        mediaLogger.onSkip(currentMediaId, currentPosition)
        AnalyticsHelper.trackCategory(service, mediaId)
        queueManager.setCurrentQueue(title, queueItemList, mediaId)
        queueManager.updateMetadata()
        handlePlayRequest()
    }

    /**
     * Update the current media player state, optionally showing an error message.
     *
     * @param error if not null, error message to present to the user.
     */
    fun updatePlaybackState(error: String?) {
        LogHelper.d(TAG, "updatePlaybackState, playback state=" + playback.state)
        val position = if (playback.isConnected) {
            playback.currentStreamPosition.toLong()
        } else {
            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
        }
        var state = playback.state
        val playBackState = PlaybackStateCompat.Builder().setActions(availableActions)
                .apply {
                    // If there is an error message, send it to the playback state:
                    error?.let {
                        // Error states are really only supposed to be used for errors that cause playback to
                        // stop unexpectedly and persist until the user takes action to fix it.
                        setErrorMessage(it)
                        state = PlaybackStateCompat.STATE_ERROR
                    }
                }
                .setState(state, position, 1.0f, SystemClock.elapsedRealtime())
                .apply {
                    // Set the activeQueueItemId if the current index is valid.
                    queueManager.currentMusic?.let {
                        setActiveQueueItemId(it.queueId)
                    }
                }.build()
        serviceCallback.onPlaybackStateUpdated(playBackState)
        if (state.isRequireNotification) {
            serviceCallback.onNotificationRequired()
        }
    }

    fun handleIntent(startIntent: Intent) {
        session.handleIntent(startIntent)
    }

    fun setActive(active: Boolean) {
        session.isActive = active
    }

    fun setPlaybackState(playbackState: PlaybackStateCompat) {
        session.setPlaybackState(playbackState)
    }

    fun stopCasting() {
        session.stopCasting(service)
    }

    /**
     * [Playback.Callback]
     */
    override fun onCompletion() {
        mediaLogger.onComplete(currentMediaId)
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        val skipCount = if (CustomController.getRepeatMode(service) == PlaybackStateCompat.REPEAT_MODE_ONE) 0 else 1
        if (queueManager.skipQueuePosition(skipCount)) {
            handlePlayRequest()
            queueManager.updateMetadata()
        } else {
            // If skipping was not possible, we stop and release the resources:
            handleStopRequest(null)
        }
    }

    override fun onPlaybackStatusChanged(state: Int) {
        updatePlaybackState(null)
    }

    override fun onError(error: String) {
        updatePlaybackState(error)
    }

    override fun setCurrentMediaId(mediaId: String) {
        mediaLogger.onSkip(currentMediaId, currentPosition)
        LogHelper.d(TAG, "setCurrentMediaId", mediaId)
        queueManager.setQueueFromMusic(mediaId)
    }

    /**
     * [StampSession.SessionListener]
     */
    override fun toLocalPlayback() {
        switchToPlayback(Playback.Type.LOCAL, false)
    }

    override fun toCastCallback() {
        switchToPlayback(Playback.Type.CAST, true)
    }

    override fun onSessionEnd() {
        playback.updateLastKnownStreamPosition()
    }

    /**
     * [MediaLogger.Listener]
     */
    override val currentMediaId: String?
        get() {
            return playback.currentMediaId
        }
    override val playbackState: Int
        get() {
            return playback.state
        }
    override val currentPosition: Int
        get() {
            return playback.currentStreamPosition
        }

    override fun onSongStart(mediaId: String) {
        SongHistoryController(service).onStart(mediaId, TimeHelper.japanNow.toDate())
    }

    override fun onSongPlay(mediaId: String) {
        SongHistoryController(service).onPlay(mediaId, TimeHelper.japanNow.toDate())
    }

    override fun onSongSkip(mediaId: String) {
        SongHistoryController(service).onSkip(mediaId, TimeHelper.japanNow.toDate())
    }

    override fun onSongComplete(mediaId: String) {
        SongHistoryController(service).onComplete(mediaId, TimeHelper.japanNow.toDate())
    }

    private inner class QueueUpdateListener : QueueManager.MetadataUpdateListener {
        override fun onMetadataChanged(metadata: MediaMetadataCompat) =
                session.setMetadata(metadata)

        override fun onMetadataRetrieveError() {
            updatePlaybackState(service.getString(R.string.error_no_metadata))
        }

        override fun onCurrentQueueIndexUpdated(queueIndex: Int) {
            MediaControllerHelper.getController(service, sessionToken)?.transportControls?.play()
        }

        override fun onQueueUpdated(title: String, newQueue: List<MediaSessionCompat.QueueItem>) =
                session.updateQueue(newQueue, title)
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {

        override fun onPlayFromUri(uri: Uri?, extras: Bundle?) {
            uri ?: return
            val queueItem = MediaItemHelper.createQueueItem(service, uri) ?: return
            if (playback.state.isPlayable) {
                mediaLogger.onStart()
            }
            serviceCallback.onPlaybackStart()
            playback.play(queueItem)
        }

        override fun onPlay() {
            LogHelper.d(TAG, "play")
            if (queueManager.currentMusic == null) {
                queueManager.setRandomQueue()
            }
            handlePlayRequest()
        }

        override fun onSkipToQueueItem(queueId: Long) {
            mediaLogger.onSkip(currentMediaId, currentPosition)
            LogHelper.d(TAG, "OnSkipToQueueItem:$queueId")
            queueManager.setCurrentQueueItem(queueId)
            queueManager.updateMetadata()
        }

        override fun onSeekTo(position: Long) {
            LogHelper.d(TAG, "onSeekTo:", position)
            playback.seekTo(position.toInt())
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle) {
            mediaLogger.onSkip(currentMediaId, currentPosition)
            LogHelper.d(TAG, "playFromMediaId mediaId:", mediaId, "  extras=", extras)
            AnalyticsHelper.trackCategory(service, mediaId)
            queueManager.setQueueFromMusic(mediaId)
            handlePlayRequest()
        }

        override fun onPause() {
            LogHelper.d(TAG, "pause. current state=" + playback.state)
            handlePauseRequest()
        }

        override fun onStop() {
            LogHelper.d(TAG, "releaseReceiver. current state=" + playback.state)
            handleStopRequest(null)
            session.release()
        }

        override fun onSkipToNext() {
            mediaLogger.onSkip(currentMediaId, currentPosition)
            LogHelper.d(TAG, "skipToNext")
            if (queueManager.skipQueuePosition(1)) {
                handlePlayRequest()
            } else {
                queueManager.skipTo0()
                handlePlayRequest()
            }
            queueManager.updateMetadata()
        }

        override fun onSkipToPrevious() {
            var position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
            if (playback.isConnected) {
                position = playback.currentStreamPosition.toLong()
            }
            if (position > 2000) {
                onSeekTo(0)
                return
            }
            if (queueManager.skipQueuePosition(-1)) {
                handlePlayRequest()
            } else {
                queueManager.skipTo0()
                handlePlayRequest()
            }
            queueManager.updateMetadata()
        }

        override fun onCustomAction(action: String, extras: Bundle?) {
        }

        override fun onSetShuffleMode(shuffleMode: Int) {
            super.onSetShuffleMode(shuffleMode)
            session.setShuffleMode(shuffleMode)
        }

        override fun onSetRepeatMode(repeatMode: Int) {
            super.onSetRepeatMode(repeatMode)
            session.setRepeatMode(repeatMode)
        }

        /**
         * Handle free and contextual searches.
         *
         *
         * All voice searches on Android Auto are sent to this method through a connected
         * [android.support.v4.media.session.MediaControllerCompat].
         *
         *
         * Threads and async handling:
         * Search, as a potentially slow operation, should run in another thread.
         *
         *
         * Since this method runs on the main thread, most apps with non-trivial metadata
         * should defer the actual search to another thread (for example, by using
         * an [AsyncTask] as we do here).
         */
        override fun onPlayFromSearch(query: String, extras: Bundle) {
            LogHelper.d(TAG, "playFromSearch  query=", query, " extras=", extras)
            //playback.state = PlaybackStateCompat.STATE_CONNECTING
            if (queueManager.setQueueFromSearch(query, extras)) {
                handlePlayRequest()
                queueManager.updateMetadata()
            } else {
                updatePlaybackState("Could not find music")
            }
        }
    }

    private val Int.isRequireNotification
        get() = this == PlaybackStateCompat.STATE_PLAYING || this == PlaybackStateCompat.STATE_PAUSED

    private val Int.isPlayable
        get() = this == PlaybackStateCompat.STATE_PLAYING || this == PlaybackStateCompat.STATE_STOPPED || this == PlaybackStateCompat.STATE_NONE

    /**
     * Handle a request to play music
     */
    private fun handlePlayRequest() {
        LogHelper.d(TAG, "handlePlayRequest: state=" + playback.state)
        queueManager.currentMusic?.let {
            if (playback.state.isPlayable) {
                mediaLogger.onStart()
            }
            serviceCallback.onPlaybackStart()
            playback.play(it)
        }
    }

    /**
     * Handle a request to pause music
     */
    private fun handlePauseRequest() {
        LogHelper.d(TAG, "handlePauseRequest: state=" + playback.state)
        if (playback.isPlaying) {
            playback.pause()
            serviceCallback.onPlaybackStop()
        }
    }

    /**
     * Handle a request to stop music
     *
     * @param withError Error message in case the stop has an unexpected cause. The error
     * message will be set in the PlaybackState and will be visible to
     * MediaController clients.
     */
    private fun handleStopRequest(withError: String?) {
        LogHelper.d(TAG, "handleStopRequest: state=" + playback.state + " error=", withError)
        playback.stop(true)
        serviceCallback.onPlaybackStop()
        updatePlaybackState(withError)
    }

    /**
     * Switch to a different Playback instance, maintaining all playback state, if possible.
     */
    private fun switchToPlayback(playbackType: Playback.Type, resumePlaying: Boolean) {
        // suspend the current one.
        val oldState = playback.state
        val currentMediaId = playback.currentMediaId
        playback.stop(false)
        playback = playbackType.createInstance(service, this, Math.max(playback.currentStreamPosition, 0), currentMediaId)
        playback.start()
        when (oldState) {
            PlaybackStateCompat.STATE_BUFFERING, PlaybackStateCompat.STATE_CONNECTING, PlaybackStateCompat.STATE_PAUSED -> this.playback.pause()
            PlaybackStateCompat.STATE_PLAYING -> {
                val currentMusic = queueManager.currentMusic
                if (resumePlaying && currentMusic != null) {
                    this.playback.play(currentMusic)
                } else if (!resumePlaying) {
                    this.playback.pause()
                } else {
                    this.playback.stop(true)
                }
            }
            PlaybackStateCompat.STATE_NONE -> {
            }
            else -> LogHelper.d(TAG, "Default called. Old state is ", oldState)
        }
    }

    interface PlaybackServiceCallback {
        fun onPlaybackStart()

        fun onNotificationRequired()

        fun onPlaybackStop()

        fun onPlaybackStateUpdated(newState: PlaybackStateCompat)
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(PlaybackManager::class.java)
    }
}
