package ro.akop.openflow.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ro.akop.openflow.AppContainer
import ro.akop.openflow.core.data.Transcript
import ro.akop.openflow.core.data.TranscriptRepository

data class HomeUiState(
    val transcripts: List<Transcript> = emptyList(),
    val isLoading: Boolean = true,
)

class HomeViewModel(
    private val repository: TranscriptRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = repository.transcripts
        .map { HomeUiState(transcripts = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(container.transcripts) }
        }
    }
}
