package ro.akop.openflow.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

interface TranscriptRepository {
    val transcripts: StateFlow<List<Transcript>>

    fun observe(id: String): Flow<Transcript?>

    suspend fun add(transcript: Transcript)

    suspend fun delete(id: String)
}

/**
 * Demo implementation. Persistence (Room) comes later; the UI only ever talks to this
 * interface, so swapping the storage does not touch the screens.
 */
class InMemoryTranscriptRepository : TranscriptRepository {
    private val state = MutableStateFlow<List<Transcript>>(emptyList())

    override val transcripts: StateFlow<List<Transcript>> = state.asStateFlow()

    override fun observe(id: String): Flow<Transcript?> =
        state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun add(transcript: Transcript) {
        state.update { current ->
            (current + transcript).sortedByDescending(Transcript::createdAt)
        }
    }

    override suspend fun delete(id: String) {
        state.update { current -> current.filterNot { it.id == id } }
    }
}
