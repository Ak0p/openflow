package ro.akop.openflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ro.akop.openflow.AppContainer
import ro.akop.openflow.core.stt.whisper.ModelStore
import ro.akop.openflow.core.stt.whisper.SpeechModel
import ro.akop.openflow.core.stt.whisper.WhisperTiny
import ro.akop.openflow.data.AppSettings
import ro.akop.openflow.data.EngineChoice

sealed interface ModelUiState {
    data object Missing : ModelUiState
    data class Downloading(val progress: Float) : ModelUiState
    data object Ready : ModelUiState
    data class Failed(val message: String) : ModelUiState
}

class SettingsViewModel(
    private val modelStore: ModelStore,
    private val model: SpeechModel,
    private val settings: AppSettings,
) : ViewModel() {

    private val _modelState = MutableStateFlow(initialState())
    val modelState: StateFlow<ModelUiState> = _modelState.asStateFlow()

    fun downloadModel() {
        if (_modelState.value is ModelUiState.Downloading) return

        _modelState.value = ModelUiState.Downloading(0f)
        viewModelScope.launch {
            modelStore.download(model) { progress ->
                _modelState.value = ModelUiState.Downloading(progress)
            }.onSuccess {
                _modelState.value = ModelUiState.Ready
                settings.setEngineChoice(EngineChoice.ON_DEVICE)
            }.onFailure { failure ->
                _modelState.value = ModelUiState.Failed(
                    failure.message ?: "The download did not finish.",
                )
            }
        }
    }

    fun removeModel() {
        modelStore.remove(model)
        settings.setEngineChoice(EngineChoice.DEMO)
        _modelState.value = ModelUiState.Missing
    }

    private fun initialState(): ModelUiState =
        if (modelStore.isComplete(model)) ModelUiState.Ready else ModelUiState.Missing

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    modelStore = container.modelStore,
                    model = WhisperTiny.model,
                    settings = container.settings,
                )
            }
        }
    }
}
