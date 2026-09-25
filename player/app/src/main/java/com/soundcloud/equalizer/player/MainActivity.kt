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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.soundcloud.equalizer.player.auth.SoundCloudLoginActivity
import com.soundcloud.equalizer.player.databinding.ActivityMainBinding
import com.soundcloud.equalizer.player.model.TrackItem
import com.soundcloud.equalizer.player.service.AudioPlayerService
import com.soundcloud.equalizer.player.soundcloud.SoundCloudClient
import com.soundcloud.equalizer.player.ui.TrackAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        // Matches PlayerLauncher.EXTRA_LAUNCHED_FROM_EQUALIZER in the HardBass EQ app.
        const val EXTRA_LAUNCHED_FROM_EQUALIZER = "com.hardbasseq.eq.EXTRA_LAUNCHED_FROM_EQUALIZER"
    }

    private lateinit var binding: ActivityMainBinding
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Bind Service
        val serviceIntent = Intent(this, AudioPlayerService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        startService(serviceIntent)

        // Setup RecyclerView
        binding.rvItems.layoutManager = LinearLayoutManager(this)
        binding.rvItems.adapter = trackAdapter

        // Check login token
        val savedToken = SoundCloudLoginActivity.getSavedToken(this)
        if (savedToken != null) {
            soundCloudClient.setUserAuthToken(savedToken)
            binding.btnLogin.text = "Logged In (Go)"
        }

        binding.btnLogin.setOnClickListener {
            val intent = Intent(this, SoundCloudLoginActivity::class.java)
            startActivity(intent)
        }

        // Equalizer Binding Switch Bar
        binding.switchEqualizerBinding.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                onEqualizerBindingOn()
            } else {
                onEqualizerBindingOff()
            }
        }

        // Search Button
        binding.btnSearch.setOnClickListener {
            val query = binding.etSearchQuery.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
        }

        // Play/Pause Button in Now Playing bar
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

        if (intent?.getBooleanExtra(EXTRA_LAUNCHED_FROM_EQUALIZER, false) == true) {
            binding.switchEqualizerBinding.isChecked = true
        }
    }

    override fun onResume() {
        super.onResume()
        val savedToken = SoundCloudLoginActivity.getSavedToken(this)
        if (savedToken != null) {
            soundCloudClient.setUserAuthToken(savedToken)
            binding.btnLogin.text = "Logged In (Go)"
        }
    }

    private fun onEqualizerBindingOn() {
        binding.tvEqStatusHeader.text = "Equalizer Player Mode: ACTIVE"
        binding.tvEqStatusSub.text = "ON - Equalizer bound to SoundCloud player session"
        binding.layoutActivePlayerContent.visibility = View.VISIBLE
        binding.layoutWaitingState.visibility = View.GONE

        // Broadcast/Trigger open audio session on service
        audioService?.openAudioSession()

        // Fetch default trending/featured playlists and tracks
        performSearch("chill")
    }

    private fun onEqualizerBindingOff() {
        binding.tvEqStatusHeader.text = "Equalizer Player Mode: INACTIVE"
        binding.tvEqStatusSub.text = "OFF - Resuming waiting for external audio..."
        binding.layoutActivePlayerContent.visibility = View.GONE
        binding.layoutWaitingState.visibility = View.VISIBLE

        // Stop playback and release audio effect session
        audioService?.stopPlayer()
        binding.cardNowPlaying.visibility = View.GONE
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            try {
                val tracks = soundCloudClient.searchTracks(query)
                if (tracks.isNotEmpty()) {
                    trackAdapter.submitList(tracks)
                } else {
                    Toast.makeText(this@MainActivity, "No tracks found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Search error: ${e.message}", Toast.LENGTH_SHORT).show()
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
                val refreshed = soundCloudClient.searchTracks(track.title, limit = 1)
                val target = refreshed.firstOrNull()
                if (target?.streamUrl != null) {
                    audioService?.playTrack(target.streamUrl, target.title, target.artist)
                    binding.cardNowPlaying.visibility = View.VISIBLE
                    binding.tvNowPlayingTitle.text = target.title
                    binding.tvNowPlayingArtist.text = target.artist
                    binding.btnPlayPause.text = "Pause"
                } else {
                    Toast.makeText(this@MainActivity, "Unable to stream this track", Toast.LENGTH_SHORT).show()
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
