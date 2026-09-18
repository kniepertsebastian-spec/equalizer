package com.hardbasseq.eq.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardbasseq.eq.audio.AudioEffectDescriptor
import com.hardbasseq.eq.audio.AudioEffectRepository
import com.hardbasseq.eq.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the start screen's debug-effects-list state. Roadmap M1 acceptance
 * criterion: "Keine Geschäftslogik lebt in Composables" - this moves the
 * `AudioEffectRepository` call and its dispatcher-switching out of
 * `MainScreen` and into a testable, lifecycle-aware owner.
 *
 * The session-attach spike keeps its own state in
 * [com.hardbasseq.eq.audio.spike.SessionAttachSpikeController], which is
 * already a plain, testable class driven by simple UI event handlers - that
 * is not the kind of embedded business logic this criterion targets.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AudioEffectRepository,
    @DefaultDispatcher private val backgroundDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _showDebugEffects = MutableStateFlow(false)
    val showDebugEffects: StateFlow<Boolean> = _showDebugEffects.asStateFlow()

    private val _effectDescriptors = MutableStateFlow<List<AudioEffectDescriptor>>(emptyList())
    val effectDescriptors: StateFlow<List<AudioEffectDescriptor>> = _effectDescriptors.asStateFlow()

    fun toggleDebugEffects() {
        val showing = !_showDebugEffects.value
        _showDebugEffects.value = showing
        if (showing) {
            viewModelScope.launch {
                _effectDescriptors.value = withContext(backgroundDispatcher) {
                    repository.queryAvailableEffects()
                }
            }
        }
    }
}
