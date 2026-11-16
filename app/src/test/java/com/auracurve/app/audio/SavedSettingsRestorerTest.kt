package com.auracurve.app.audio

import com.auracurve.app.diagnostics.InMemoryDiagnosticsRecorder
import com.auracurve.app.settings.AppSettingsRepository
import com.auracurve.app.settings.LiveSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedSettingsRestorerTest {
    private val savedProcessing = ProcessingSettings(bandGainsDb = mapOf(0 to 6f, 1 to 4f), macroBassDb = 3f)
    private val saved = LiveSettings(activePresetId = "custom", isDirty = false, processingSettings = savedProcessing)

    @Test
    fun appliesSavedSettingsWhenEngineIsUntouched() =
        runTest {
            val engine = CountingAudioEngine()
            val restorer = SavedSettingsRestorer(FixedSettingsRepository(flowOf(saved)), engine, InMemoryDiagnosticsRecorder())

            assertTrue(restorer.restoreIfEngineUntouched())
            assertEquals(listOf(savedProcessing), engine.applied)
            assertEquals(savedProcessing, engine.currentSettings.value)
        }

    @Test
    fun doesNotApplyWhenEngineIsAlreadyConfigured() =
        runTest {
            val engine = CountingAudioEngine()
            val fromViewModel = ProcessingSettings(inputGainDb = -2f)
            engine.apply(fromViewModel)
            engine.applied.clear()
            val restorer = SavedSettingsRestorer(FixedSettingsRepository(flowOf(saved)), engine, InMemoryDiagnosticsRecorder())

            assertFalse(restorer.restoreIfEngineUntouched())
            assertTrue(engine.applied.isEmpty())
            assertEquals(fromViewModel, engine.currentSettings.value)
        }

    @Test
    fun doesNotApplyWithoutSavedSettings() =
        runTest {
            val engine = CountingAudioEngine()
            val restorer = SavedSettingsRestorer(FixedSettingsRepository(flowOf(null)), engine, InMemoryDiagnosticsRecorder())

            assertFalse(restorer.restoreIfEngineUntouched())
            assertTrue(engine.applied.isEmpty())
        }

    @Test
    fun readFailureDoesNotCrashOrApply() =
        runTest {
            val engine = CountingAudioEngine()
            val diagnostics = InMemoryDiagnosticsRecorder()
            val failing = flow<LiveSettings?> { throw IllegalStateException("damaged") }
            val restorer = SavedSettingsRestorer(FixedSettingsRepository(failing), engine, diagnostics)

            assertFalse(restorer.restoreIfEngineUntouched())
            assertTrue(engine.applied.isEmpty())
            assertEquals(1, diagnostics.events.value.size)
        }

    private class FixedSettingsRepository(
        override val liveSettings: Flow<LiveSettings?>,
    ) : AppSettingsRepository {
        override suspend fun save(liveSettings: LiveSettings) = Unit
    }

    private class CountingAudioEngine(
        private val delegate: FakeAudioEngine = FakeAudioEngine(),
    ) : AudioEngine by delegate {
        val applied = mutableListOf<ProcessingSettings>()

        override suspend fun apply(settings: ProcessingSettings): Boolean {
            applied += settings
            return delegate.apply(settings)
        }
    }
}
