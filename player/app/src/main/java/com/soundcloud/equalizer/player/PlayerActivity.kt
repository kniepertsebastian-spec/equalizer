package com.soundcloud.equalizer.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.soundcloud.equalizer.player.auth.SoundCloudLoginActivity
import com.soundcloud.equalizer.player.databinding.ActivityPlayerBinding
import com.soundcloud.equalizer.player.model.TrackItem
import com.soundcloud.equalizer.player.service.AudioPlayerService
import com.soundcloud.equalizer.player.soundcloud.SoundCloudClient
import com.soundcloud.equalizer.player.ui.TrackAdapter
import kotlinx.coroutines.launch

/**
 * Started explicitly by HardBass EQ (via PlayerBridge) once the user switches the
 * master bar on and picks a source - not a launcher entry point of its own. Only
 * [SOURCE_SOUNDCLOUD] is implemented; [SOURCE_YOUTUBE] is a placeholder until that
 * source gets its own extractor.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SOURCE = "com.hardbasseq.eq.EXTRA_PLAYER_SOURCE"
        const val SOURCE_SOUNDCLOUD = "soundcloud"
        const val SOURCE_YOUTUBE = "youtube"
    }

    private lateinit var binding: ActivityPlayerBinding
    private val soundCloudClient = SoundCloudClient()
    private var audioService: AudioPlayerService? = null
    private var isBound = false

    private val trackAdapter = TrackAdapter { track ->
        onTrackSelected(track)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlayerService.LocalBinder
            audioService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.getStringExtra(EXTRA_SOURCE) == SOURCE_YOUTUBE) {
            Toast.makeText(this, "YouTube isn't supported yet - pick SoundCloud for now", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Android 15+ (targetSdk 36 here, via :app) enforces edge-to-edge, so
        // content draws under the status/nav bars by default unless something
        // consumes those insets - equalizer's other (Compose) screens get this for
        // free from Scaffold, this plain-View one doesn't.
        val basePaddingPx = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = basePaddingPx + bars.left,
                top = basePaddingPx + bars.top,
                right = basePaddingPx + bars.right,
                bottom = basePaddingPx + bars.bottom,
            )
            insets
        }

        val serviceIntent = Intent(this, AudioPlayerService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        startService(serviceIntent)

        binding.rvItems.layoutManager = LinearLayoutManager(this)
        binding.rvItems.adapter = trackAdapter

        val savedToken = SoundCloudLoginActivity.getSavedToken(this)
        if (savedToken != null) {
            soundCloudClient.setUserAuthToken(savedToken)
            binding.btnLogin.text = "Logged In (Go)"
        }

        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, SoundCloudLoginActivity::class.java))
        }

        binding.btnSearch.setOnClickListener {
            val query = binding.etSearchQuery.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
        }

        binding.btnPlayPause.setOnClickListener {
            audioService?.let { service ->
                if (service.isPlaying()) {
                    service.pauseTrack()
                    binding.btnPlayPause.text = "Play"
                } else {
                    service.resumeTrack()
                    binding.btnPlayPause.text = "Pause"
                }
            }
        }

        performSearch("chill")
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        val savedToken = SoundCloudLoginActivity.getSavedToken(this)
        if (savedToken != null) {
            soundCloudClient.setUserAuthToken(savedToken)
            binding.btnLogin.text = "Logged In (Go)"
        }
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            try {
                val tracks = soundCloudClient.searchTracks(query)
                if (tracks.isNotEmpty()) {
                    trackAdapter.submitList(tracks)
                } else {
                    Toast.makeText(this@PlayerActivity, "No tracks found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PlayerActivity, "Search error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onTrackSelected(track: TrackItem) {
        if (!track.streamUrl.isNullOrEmpty()) {
            audioService?.playTrack(track.streamUrl, track.title, track.artist)
            binding.cardNowPlaying.visibility = View.VISIBLE
            binding.tvNowPlayingTitle.text = track.title
            binding.tvNowPlayingArtist.text = track.artist
            binding.btnPlayPause.text = "Pause"
        } else {
            lifecycleScope.launch {
                try {
                    val refreshed = soundCloudClient.searchTracks(track.title, limit = 1)
                    val target = refreshed.firstOrNull()
                    if (target?.streamUrl != null) {
                        audioService?.playTrack(target.streamUrl, target.title, target.artist)
                        binding.cardNowPlaying.visibility = View.VISIBLE
                        binding.tvNowPlayingTitle.text = target.title
                        binding.tvNowPlayingArtist.text = target.artist
                        binding.btnPlayPause.text = "Pause"
                    } else {
                        Toast.makeText(this@PlayerActivity, "Unable to stream this track", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@PlayerActivity, "Playback error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }
}
