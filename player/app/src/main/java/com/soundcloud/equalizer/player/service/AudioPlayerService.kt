package com.soundcloud.equalizer.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.soundcloud.equalizer.player.PlayerActivity
import com.soundcloud.equalizer.player.playback.NowPlaying
import com.soundcloud.equalizer.player.playback.NowPlayingState

class AudioPlayerService : Service() {

    companion object {
        const val CHANNEL_ID = "sound_cloud_equalizer_player_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.soundcloud.equalizer.player.PLAY"
        const val ACTION_PAUSE = "com.soundcloud.equalizer.player.PAUSE"
        const val ACTION_STOP = "com.soundcloud.equalizer.player.STOP"
        const val ACTION_TOGGLE_PLAYBACK = "com.soundcloud.equalizer.player.TOGGLE_PLAYBACK"
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_TRACK_TITLE = "extra_track_title"
        const val EXTRA_ARTIST_NAME = "extra_artist_name"

    }

    private val binder = LocalBinder()
    private var exoPlayer: ExoPlayer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var isAudioSessionActive = false
    private var currentTitle: String = ""
    private var currentArtist: String = ""

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initExoPlayer()
    }

    private fun initExoPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        super.onPlaybackStateChanged(playbackState)
                        if (playbackState == Player.STATE_READY && isPlaying) {
                            openAudioSession()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        super.onIsPlayingChanged(isPlaying)
                        if (isPlaying) {
                            openAudioSession()
                        }
                        // Covers every reason isPlaying can flip, not just taps on our
                        // own play/pause controls - audio focus loss, headphones
                        // unplugged, playback reaching the end - so the mini-player
                        // bar HardBass EQ's main screen shows never goes stale.
                        if (currentTitle.isNotEmpty()) {
                            NowPlayingState.update(NowPlaying(currentTitle, currentArtist, isPlaying))
                        }
                    }
                })
            }

        currentAudioSessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
    }

    fun openAudioSession() {
        val sessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && (!isAudioSessionActive || currentAudioSessionId != sessionId)) {
            currentAudioSessionId = sessionId

            // Must match the action AudioSessionRepository actually listens for
            // (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) - this used to
            // broadcast a custom "OPEN_AUDIO_EFFECT_SESSION" action (missing
            // "_CONTROL_") that nothing was ever listening for, so HardBass EQ never
            // saw this player's session at all.
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentAudioSessionId)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)

            sendBroadcast(intent)
            isAudioSessionActive = true
        }
    }

    fun closeAudioSession() {
        if (isAudioSessionActive && currentAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            val intent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentAudioSessionId)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)

            sendBroadcast(intent)
            isAudioSessionActive = false
        }
    }

    fun playTrack(streamUrl: String, title: String, artist: String) {
        val player = exoPlayer ?: return
        val mediaItem = MediaItem.fromUri(streamUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        currentTitle = title
        currentArtist = artist
        NowPlayingState.update(NowPlaying(title, artist, isPlaying = true))

        startForeground(NOTIFICATION_ID, buildNotification(title, artist, true))
        openAudioSession()
    }

    fun pauseTrack() {
        exoPlayer?.pause()
    }

    fun resumeTrack() {
        exoPlayer?.play()
        openAudioSession()
    }

    fun stopPlayer() {
        exoPlayer?.stop()
        closeAudioSession()
        currentTitle = ""
        currentArtist = ""
        NowPlayingState.update(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    fun togglePlayback() {
        if (isPlaying()) pauseTrack() else resumeTrack()
    }

    fun getAudioSessionId(): Int {
        return exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
    }

    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying == true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_STREAM_URL)
                val title = intent.getStringExtra(EXTRA_TRACK_TITLE) ?: "SoundCloud Track"
                val artist = intent.getStringExtra(EXTRA_ARTIST_NAME) ?: "Artist"
                if (!url.isNullOrEmpty()) {
                    playTrack(url, title, artist)
                }
            }
            ACTION_PAUSE -> pauseTrack()
            ACTION_STOP -> stopPlayer()
            ACTION_TOGGLE_PLAYBACK -> togglePlayback()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        closeAudioSession()
        NowPlayingState.update(null)
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SoundCloud Equalizer Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, artist: String, isPlaying: Boolean): Notification {
        val intent = Intent(this, PlayerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(isPlaying)
            .build()
    }
}
