package com.example.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Audio Reactive Engine using real-time PCM audio capture to modulate shaders and ripples.
 */
object AudioReactiveManager {
    private const val TAG = "AudioReactiveManager"
    private const val SAMPLE_RATE = 22050

    private val _energy = MutableStateFlow(0.0f)
    val energy: StateFlow<Float> = _energy.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRunning = false

    fun isPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startListening(context: Context) {
        if (isRunning) return
        if (!isPermissionGranted(context)) {
            Log.w(TAG, "Audio permission not granted")
            return
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufferSize * 2

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord could not initialize")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isRunning = true

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ShortArray(512)
                var smoothed = 0.0f

                while (isActive && isRunning) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = sqrt(sum / read).toFloat() / 15000.0f // Normalized 0..1
                        val clamped = min(1.0f, rms * 1.8f)
                        smoothed = smoothed * 0.65f + clamped * 0.35f
                        _energy.value = smoothed
                    }
                    delay(30)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting audio record: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio record: ${e.message}")
        }
    }

    fun stopListening() {
        isRunning = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord = null
            _energy.value = 0.0f
        }
    }
}
