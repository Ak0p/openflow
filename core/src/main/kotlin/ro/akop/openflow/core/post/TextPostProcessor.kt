package ro.akop.openflow.core.post

/**
 * Turns a raw engine output into something the user would actually send.
 *
 * The demo ships a rule based implementation. A small transformer / on-device LLM can be
 * dropped in behind the same interface later (formatting, tone, voice commands).
 */
interface TextPostProcessor {
    suspend fun process(raw: String): String
}

class NoOpTextPostProcessor : TextPostProcessor {
    override suspend fun process(raw: String): String = raw.trim()
}
