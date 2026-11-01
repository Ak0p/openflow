package ro.akop.openflow.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ro.akop.openflow.AppContainer
import ro.akop.openflow.core.data.Transcript
import ro.akop.openflow.core.data.TranscriptRepository
import ro.akop.openflow.ui.nav.DetailRoute
import java.io.File

data class DetailUiState(
    val transcript: Transcript? = null,
    val isLoading: Boolean = true,
)

class DetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: TranscriptRepository,
) : ViewModel() {

    private val transcriptId: String = savedStateHandle.toRoute<DetailRoute>().transcriptId

    val state: StateFlow<DetailUiState> = repository.observe(transcriptId)
        .map { DetailUiState(transcript = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetailUiState(),
        )

    private val _deleted = Channel<Unit>(Channel.BUFFERED)
    val deleted = _deleted.receiveAsFlow()

    fun delete() {
        val audioPath = state.value.transcript?.audioPath
        viewModelScope.launch {
            repository.delete(transcriptId)
            audioPath?.let { runCatching { File(it).delete() } }
            _deleted.send(Unit)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DetailViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    repository = container.transcripts,
                )
            }
        }
    }
}
