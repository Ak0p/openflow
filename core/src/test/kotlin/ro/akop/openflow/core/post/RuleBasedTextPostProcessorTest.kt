package ro.akop.openflow.core.post

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleBasedTextPostProcessorTest {

    private val processor = RuleBasedTextPostProcessor()

    @Test
    fun `capitalises and terminates a bare sentence`() {
        assertEquals("Hello there.", processor.format("hello there"))
    }

    @Test
    fun `turns spoken punctuation into symbols`() {
        assertEquals(
            "First, second.",
            processor.format("first comma second"),
        )
    }

    @Test
    fun `supports spoken new lines`() {
        assertEquals(
            "One.\nTwo.",
            processor.format("one new line two"),
        )
    }

    @Test
    fun `removes english filler words`() {
        assertEquals(
            "I think this works.",
            processor.format("I um think uh this works"),
        )
    }

    @Test
    fun `keeps terminal punctuation that already exists`() {
        assertEquals("Done!", processor.format("done!"))
        assertEquals("Really?", processor.format("really?"))
    }

    @Test
    fun `never starts with punctuation`() {
        assertEquals("Hello, world.", processor.format(", hello, world"))
    }

    @Test
    fun `returns empty for blank input`() {
        assertEquals("", processor.format("   "))
    }
}
