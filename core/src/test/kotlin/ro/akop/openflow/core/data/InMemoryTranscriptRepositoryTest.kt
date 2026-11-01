package ro.akop.openflow.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryTranscriptRepositoryTest {

    @Test
    fun `keeps newest transcripts first`() = runTest {
        val repository = InMemoryTranscriptRepository()

        repository.add(transcript(id = "older", createdAt = 1_000))
        repository.add(transcript(id = "newer", createdAt = 2_000))

        assertEquals(listOf("newer", "older"), repository.transcripts.value.map { it.id })
    }

    @Test
    fun `deletes by id`() = runTest {
        val repository = InMemoryTranscriptRepository()
        repository.add(transcript(id = "a", createdAt = 1))
        repository.add(transcript(id = "b", createdAt = 2))

        repository.delete("b")

        assertEquals(listOf("a"), repository.transcripts.value.map { it.id })
        assertNull(repository.observe("b").first())
    }

    private fun transcript(id: String, createdAt: Long) = Transcript(
        id = id,
        text = "text $id",
        createdAt = createdAt,
        durationMs = 1_000,
    )
}
