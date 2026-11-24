package com.auracurve.app.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.audio.AudioDeviceType
import com.auracurve.app.audio.AudioEngineState
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.context.SoundContext
import com.auracurve.app.correction.BuiltInCorrectionProfiles
import com.auracurve.app.discovery.DiscoveryState
import com.auracurve.app.discovery.GenreMode
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.eq.ActivePlaybackSource
import com.auracurve.app.integration.PlayerController
import com.auracurve.app.player.catalog.RadioGenre
import com.auracurve.app.player.catalog.RadioStation
import com.auracurve.app.player.model.LibraryOverview
import com.auracurve.app.player.model.PlaylistItem
import com.auracurve.app.player.playback.NowPlaying
import com.auracurve.app.playlist.PlaylistRepository
import com.auracurve.app.preset.BuiltInGenrePresets
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.SoundGoal
import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.text.FakeTextProvider
import com.auracurve.app.ui.eq.EqStatusKind
import com.auracurve.app.ui.eq.EqStatusLine
import com.auracurve.app.ui.equalizer.EqualizerScreen
import com.auracurve.app.ui.navigation.AppBottomBar
import com.auracurve.app.ui.navigation.ROUTE_HOME
import com.auracurve.app.ui.navigation.ROUTE_PLAYER
import com.auracurve.app.ui.player.DiscoveryUiState
import com.auracurve.app.ui.player.ImportUiState
import com.auracurve.app.ui.player.LibraryHeader
import com.auracurve.app.ui.player.LibraryUiState
import com.auracurve.app.ui.player.PlayerTab
import com.auracurve.app.ui.player.PlayerTabs
import com.auracurve.app.ui.player.PlayerViewModel
import com.auracurve.app.ui.player.SearchUiState
import com.auracurve.app.ui.player.catalog.CatalogUiState
import com.auracurve.app.ui.player.catalog.radioItems
import com.auracurve.app.ui.player.discoveryItems
import com.auracurve.app.ui.player.library.playlistsTabItems
import com.auracurve.app.ui.player.nowplaying.NowPlayingActions
import com.auracurve.app.ui.player.nowplaying.NowPlayingContext
import com.auracurve.app.ui.player.nowplaying.NowPlayingScreen
import com.auracurve.app.ui.player.nowplaying.NowPlayingUi
import com.auracurve.app.ui.player.searchItems
import com.auracurve.app.ui.theme.AuraCurveTheme
import com.auracurve.app.ui.theme.SonicPaper
import com.auracurve.app.ui.theme.cyberBackdrop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Proxy

// The seven screens of the design mockups (Design/10881-10887.png, see Design/DESIGN_SPEC.md §1),
// drawn with data close to the mockups' sample content so the current look can be put next to
// them. Writes app/build/mockup-screenshots/ (not compared, nothing is checked in). A plain
// Application: the Hilt app is not needed. The player screens are put together from the same
// parts PlayerScreen uses, since PlayerScreen itself needs its Hilt view models.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h873dp-xxhdpi")
class MockupScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    // The mockups' preset: "Car Uptempo – Kick Snap Max".
    private val kickSnapMax =
        Preset(
            id = "custom_kick_snap_max",
            name = "Uptempo – Kick Snap Max",
            targetCurve =
                listOf(
                    TargetPoint(60f, 3f),
                    TargetPoint(110f, 3f),
                    TargetPoint(300f, -4.5f),
                    TargetPoint(1200f, -1f),
                    TargetPoint(3500f, 2f),
                    TargetPoint(7000f, 0.5f),
                    TargetPoint(12000f, -1f),
                ),
            macroBassDb = 4.5f,
            macroPunchDb = 6.5f,
            macroHaerteDb = 1f,
        )

    private val speaker = AudioRoute(AudioDeviceType.SPEAKER, "Built-in Speaker", "speaker_internal")

    // ---- Equalizer (10881-10883): waiting for playback, no bands yet, as in the mockups.

    private fun showEqualizer(tabLabel: Int) {
        compose.setContent {
            Shell(currentRoute = ROUTE_HOME) {
                EqualizerScreen(
                    state = AudioEngineState.Listening,
                    route = speaker,
                    settings = ProcessingSettings(macroBassDb = 4.5f, macroPunchDb = 6.5f, macroHaerteDb = 1f),
                    bands = emptyList(),
                    activePreset = kickSnapMax,
                    customPresets = listOf(kickSnapMax),
                    genrePresets = BuiltInGenrePresets.all,
                    moreIntensity = false,
                    activeCorrectionProfile = BuiltInCorrectionProfiles.None,
                    allCorrectionProfiles = listOf(BuiltInCorrectionProfiles.None),
                    suggestedCorrectionProfile = null,
                    effectiveHeadphoneAcoustics = true,
                    onOpenServices = {},
                    activeGoal = SoundGoal.entries.first(),
                    onGoalSelected = {},
                    pathLabel = null,
                    compare = null,
                    onStartCompare = {},
                    onCompareSide = {},
                    onEndCompare = {},
                    headphonePower = HeadphonePower(),
                    activeContext = SoundContext.CAR,
                    currentLevelDb = -12f,
                    isDirty = false,
                    onMasterToggled = {},
                    onOpenSourcePicker = {},
                    onBypassToggled = {},
                    onPresetSelected = {},
                    onMoreIntensityChanged = {},
                    onCorrectionProfileSelected = {},
                    onImportCorrectionProfileRequested = {},
                    onExportCorrectionProfile = {},
                    onAcceptSuggestedCorrectionProfile = {},
                    onDismissSuggestedCorrectionProfile = {},
                    onHeadphoneAcousticsChanged = {},
                    onHeadphonePowerChanged = {},
                    onContextModeChanged = {},
                    onLoudnessCompensationChanged = {},
                    onSubsonicFilterChanged = {},
                    onVirtualBassChanged = {},
                    onResetToActivePreset = {},
                    onSaveAsNewRequest = {},
                    onDuplicatePreset = {},
                    onRenamePresetRequest = {},
                    onDeletePresetRequest = {},
                    onSharePreset = {},
                    onImportPresetLinkRequested = {},
                    onMacroBassChanged = {},
                    onMacroPunchChanged = {},
                    onMacroHaerteChanged = {},
                    onBandGainChanged = { _, _ -> },
                    onRetryAttach = {},
                    deviceTabFooter = null,
                    playbackSource = ActivePlaybackSource.None,
                )
            }
        }
        compose.onNodeWithText(text(tabLabel)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun eqSound() {
        showEqualizer(R.string.eq_tab_sound)
        save("10881-eq-sound")
    }

    @Test
    fun eqFineTune() {
        showEqualizer(R.string.eq_tab_fine_tune)
        save("10882-eq-fine-tune")
    }

    @Test
    fun eqDevice() {
        showEqualizer(R.string.eq_tab_device)
        save("10883-eq-device")
    }

    // ---- Now Playing (10884): the full-screen layer, no navigation bar below it.

    @Test
    fun nowPlaying() {
        val track =
            NowPlaying(
                title = "Eight In The Morning",
                artist = "NOSAM",
                isPlaying = true,
                durationMs = 164_000L,
                positionMs = 0L,
                queueIndex = 0,
                isPreview = false,
                notice = null,
            )
        val context =
            NowPlayingContext(
                eqStatus = EqStatusKind.WAITING,
                soundName = "Car Uptempo – Kick Snap Max",
                soundCurve = kickSnapMax.targetCurve,
                contextName = "Car",
                route = speaker,
                queueCount = 80,
                signedIn = true,
            )
        val actions =
            NowPlayingActions(
                onCollapse = {},
                onDrag = {},
                onDragStopped = {},
                onTogglePlayback = {},
                onPrevious = {},
                onNext = {},
                onSeek = {},
                onToggleShuffle = {},
                onCycleRepeat = {},
                onAddToPlaylist = {},
                onOpenSound = {},
                onOpenQueue = {},
            )
        compose.setContent {
            AuraCurveTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NowPlayingScreen(ui = NowPlayingUi.from(track), context = context, actions = actions)
                }
            }
        }
        compose.waitForIdle()
        save("10884-now-playing")
    }

    // ---- Player tabs (10885-10887): the head of PlayerScreen and one tab's items.

    private val tabs = listOf(PlayerTab.SEARCH, PlayerTab.PLAYLISTS, PlayerTab.RADIO, PlayerTab.AUDIUS)

    private fun showPlayer(
        tab: PlayerTab,
        items: LazyListScope.() -> Unit,
    ) {
        compose.setContent {
            Shell(currentRoute = ROUTE_PLAYER) {
                CompositionLocalProvider(LocalContentColor provides SonicPaper) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item { LibraryHeader(onlineSources = true, onOpenServices = {}, onSelectSoundCloud = {}) }
                        item {
                            EqStatusLine(kind = EqStatusKind.WAITING, pathLabel = null, onClick = {}, modifier = Modifier.fillMaxWidth())
                        }
                        item { PlayerTabs(selected = tab, onSelect = {}, tabs = tabs) }
                        items()
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun station(
        name: String,
        country: String,
        codec: String,
        kbps: Int,
    ) = RadioStation(
        uuid = name.hashCode().toString(),
        name = name,
        streamUrl = "https://stream.example.com/${name.hashCode()}",
        faviconUrl = null,
        tags = listOf("electronic"),
        countryCode = country,
        codec = codec,
        bitrateKbps = kbps,
        isHls = false,
        clickCount = 0,
    )

    @Test
    fun playerRadio() {
        val stations =
            listOf(
                station("Anon.FM", "RU", "MP3", 192),
                station("TOP 100 DJ CHARTS - DJ REMIX & CHARTS", "DE", "MP3", 192),
                station("Intense Radio - We love Dance 256K", "NL", "MP3", 256),
                station("IBIZA CHILLOUT LOUNGE - Ambient, Deep House", "DE", "MP3", 192),
                station("DJ & CLUB CHARTS ---> Club Classics, Festival", "DE", "MP3", 192),
                station(".DEEJAY REMIXE.. Your Radio for finest House", "DE", "MP3", 192),
                station("TOP 100 CHARTS -- DJ MIXES - Pop, Rock, Dance", "DE", "MP3", 192),
                station("KISS - The Best Vibes & Energy", "GB", "AAC+", 47),
            )
        showPlayer(PlayerTab.RADIO) {
            radioItems(
                genre = RadioGenre.ALL,
                state = CatalogUiState(items = stations, loaded = true),
                onShown = {},
                onGenre = {},
                onRetry = {},
                onPlay = {},
                onPlayNext = {},
                onAddToQueue = {},
            )
        }
        save("10885-player-radio")
    }

    @Test
    fun playerPlaylists() {
        val viewModel = PlayerViewModel(fakeController(), fakePlaylists(), FakeTextProvider())
        val likes = PlaylistItem(id = 1L, title = "Likes", trackCount = 0, artworkUrl = null)
        showPlayer(PlayerTab.PLAYLISTS) {
            playlistsTabItems(
                viewModel = viewModel,
                playlists = emptyList(),
                importState =
                    ImportUiState(
                        message = "That did not work: SoundCloud returned HTTP 401 for this request",
                        isError = true,
                    ),
                pendingImport = null,
                bridgeState = null,
                signedIn = true,
                libraryState = LibraryUiState(library = LibraryOverview(own = emptyList(), liked = listOf(likes))),
                onNewPlaylist = {},
                onMerge = {},
                onlineSources = true,
            )
        }
        save("10886-player-playlists")
    }

    @Test
    fun playerSearch() {
        showPlayer(PlayerTab.SEARCH) {
            searchItems(
                text = "",
                onTextChange = {},
                state = SearchUiState(),
                onSearch = {},
                onPlay = {},
                onPlayNext = {},
                onAddToQueue = {},
            )
            discoveryItems(
                state = DiscoveryState(artists = listOf("unproven"), genreMode = GenreMode.AUTO, autoGenres = listOf("uptempo")),
                ui = DiscoveryUiState(),
                nowMs = 0L,
                currentTrackId = null,
                playedIds = emptySet(),
                onAddArtist = {},
                onRemoveArtist = {},
                onGenreMode = {},
                onAddGenre = {},
                onRemoveGenre = {},
                onRefresh = {},
                onPlayAll = {},
                onPlay = {},
                onDismiss = {},
            )
        }
        save("10887-player-search")
    }

    // ---- Helpers

    // The app shell around a main tab: the screen above, the navigation bar below (AppNavHost).
    @Composable
    private fun Shell(
        currentRoute: String,
        content: @Composable () -> Unit,
    ) {
        AuraCurveTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                // As in AppNavHost: the backdrop below the screen and the navigation bar.
                Column(modifier = Modifier.fillMaxSize().cyberBackdrop()) {
                    Box(modifier = Modifier.weight(1f)) { content() }
                    AppBottomBar(currentRoute = currentRoute, onNavigate = {})
                }
            }
        }
    }

    // The player screen only calls the view model on clicks; constructing it reads a few states.
    private fun fakeController(): PlayerController =
        fake(
            "getNowPlaying" to MutableStateFlow<NowPlaying?>(null),
            "getQueue" to MutableStateFlow(emptyList<Any>()),
            "isSoundCloudSignedIn" to true,
        )

    private fun fakePlaylists(): PlaylistRepository = fake("getPlaylists" to flowOf(emptyList<Any>()))

    private inline fun <reified T : Any> fake(vararg answers: Pair<String, Any?>): T {
        val byName = answers.toMap()
        return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
            when {
                method.name in byName -> byName[method.name]
                method.name == "toString" -> "fake ${T::class.java.simpleName}"
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "equals" -> proxy === args?.get(0)
                else ->
                    when (method.returnType) {
                        java.lang.Boolean.TYPE -> false
                        Integer.TYPE -> 0
                        java.lang.Long.TYPE -> 0L
                        java.lang.Float.TYPE -> 0f
                        String::class.java -> ""
                        else -> null
                    }
            }
        } as T
    }

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/mockup-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
