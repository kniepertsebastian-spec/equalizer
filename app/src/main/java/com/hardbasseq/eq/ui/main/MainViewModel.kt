package com.hardbasseq.eq.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hardbasseq.eq.audio.AudioEffectDescriptor
import com.hardbasseq.eq.audio.AudioEffectRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
class MainViewModel(
    private val repository: AudioEffectRepository,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
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

class MainViewModelFactory(private val repository: AudioEffectRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(repository) as T
    }
}
