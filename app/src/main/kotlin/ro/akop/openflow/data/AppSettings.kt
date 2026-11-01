package ro.akop.openflow.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EngineChoice {
    DEMO,
    ON_DEVICE,
}

class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _engineChoice = MutableStateFlow(
        runCatching { EngineChoice.valueOf(prefs.getString(KEY_ENGINE, null) ?: "") }
            .getOrDefault(EngineChoice.DEMO),
    )
    val engineChoice: StateFlow<EngineChoice> = _engineChoice.asStateFlow()

    private val _postProcessing = MutableStateFlow(prefs.getBoolean(KEY_POST_PROCESSING, true))
    val postProcessing: StateFlow<Boolean> = _postProcessing.asStateFlow()

    fun setEngineChoice(choice: EngineChoice) {
        prefs.edit().putString(KEY_ENGINE, choice.name).apply()
        _engineChoice.value = choice
    }

    fun setPostProcessing(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_POST_PROCESSING, enabled).apply()
        _postProcessing.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "openflow.settings"
        const val KEY_ENGINE = "engine"
        const val KEY_POST_PROCESSING = "post_processing"
    }
}
