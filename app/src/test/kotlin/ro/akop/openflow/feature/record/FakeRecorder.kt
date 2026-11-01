package ro.akop.openflow.feature.record

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ro.akop.openflow.core.audio.Recorder
import ro.akop.openflow.core.audio.RecordingStatus
import ro.akop.openflow.core.stt.PcmAudio

class FakeRecorder(
    private val recorded: PcmAudio?,
) : Recorder {

    private val _status = MutableStateFlow(RecordingStatus())
    override val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    var startCalls = 0
        private set

    override fun start(): Boolean {
        startCalls++
        _status.value = RecordingStatus(isRecording = true)
        return true
    }

    override fun stop(): PcmAudio? {
        _status.value = RecordingStatus(isRecording = false)
        return recorded
    }
}
