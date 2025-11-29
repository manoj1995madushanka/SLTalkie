package com.floodcomms

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.AudioManager
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class AudioHelper {
    private val TAG = "AudioHelper"
    private val SAMPLE_RATE = 16000 // 16kHz is good for voice
    private val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
    private val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    
    private var fileOutputStream: FileOutputStream? = null
    private var currentFilePath: String? = null

    fun startRecording(onAudioData: (ByteArray) -> Unit) {
        if (isRecording) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                BUFFER_SIZE
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread {
                val buffer = ByteArray(BUFFER_SIZE)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, BUFFER_SIZE) ?: 0
                    if (read > 0) {
                        // TODO: Compress with Opus here
                        val data = buffer.copyOf(read)
                        onAudioData(data)
                    }
                }
            }
            recordingThread?.start()
            Log.d(TAG, "Recording started")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
        }
    }

    fun stopRecording() {
        isRecording = false
        try {
            recordingThread?.join()
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    fun startSaving(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                file.createNewFile()
            }
            fileOutputStream = FileOutputStream(file)
            currentFilePath = filePath
        } catch (e: Exception) {
            Log.e(TAG, "Error creating audio file", e)
        }
    }

    fun saveChunk(data: ByteArray) {
        try {
            fileOutputStream?.write(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to audio file", e)
        }
    }

    fun stopSaving() {
        try {
            fileOutputStream?.close()
            fileOutputStream = null
            currentFilePath = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing audio file", e)
        }
    }

    fun playAudio(audioData: ByteArray) {
        // TODO: Decompress Opus here
        try {
            if (audioTrack == null) {
                audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_OUT,
                    AUDIO_FORMAT,
                    BUFFER_SIZE,
                    AudioTrack.MODE_STREAM
                )
                audioTrack?.play()
            }
            audioTrack?.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
        }
    }

    fun playFile(filePath: String, onComplete: () -> Unit) {
        Thread {
            try {
                val file = File(filePath)
                if (!file.exists()) return@Thread

                val inputStream = FileInputStream(file)
                val buffer = ByteArray(BUFFER_SIZE)
                
                if (audioTrack == null) {
                     audioTrack = AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG_OUT,
                        AUDIO_FORMAT,
                        BUFFER_SIZE,
                        AudioTrack.MODE_STREAM
                    )
                    audioTrack?.play()
                }

                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    audioTrack?.write(buffer, 0, read)
                }
                inputStream.close()
                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing file", e)
            }
        }.start()
    }
    
    fun release() {
        stopRecording()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
