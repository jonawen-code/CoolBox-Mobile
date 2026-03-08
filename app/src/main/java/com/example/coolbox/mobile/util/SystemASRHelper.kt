package com.example.coolbox.mobile.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SystemASRHelper(private val context: Context) {
    private companion object {
        const val TAG = "SystemASRHelper"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val _isReady = MutableStateFlow(true) // System engine is almost always ready
    val isReady = _isReady.asStateFlow()

    private var onResultCallback: ((String) -> Unit)? = null
    private var onPartialResultCallback: ((String) -> Unit)? = null

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "onReadyForSpeech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "onBeginningOfSpeech")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "onEndOfSpeech")
                }

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "录音设备异常 (ERROR_AUDIO)"
                        SpeechRecognizer.ERROR_CLIENT -> "客户端连接错误 (ERROR_CLIENT)"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限 (ERROR_INSUFFICIENT_PERMISSIONS)"
                        SpeechRecognizer.ERROR_NETWORK -> "网络连接失败 (ERROR_NETWORK)"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "解析超时 (ERROR_NETWORK_TIMEOUT)"
                        SpeechRecognizer.ERROR_NO_MATCH -> "未匹配到任何文字 (ERROR_NO_MATCH)"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音引擎正忙，请稍后再试 (ERROR_RECOGNIZER_BUSY)"
                        SpeechRecognizer.ERROR_SERVER -> "语音服务器错误 (ERROR_SERVER)"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到输入 (ERROR_SPEECH_TIMEOUT)"
                        else -> "语音识别未知错误 ($error)"
                    }
                    Log.e(TAG, "ASR Error: $message")
                    android.widget.Toast.makeText(context, "语音助手：$message", android.widget.Toast.LENGTH_SHORT).show()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val result = matches[0]
                        Log.d(TAG, "onResults: $result")
                        onResultCallback?.invoke(result)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onPartialResultCallback?.invoke(matches[0])
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            Log.e(TAG, "Speech recognition not available")
            _isReady.value = false
        }
    }

    fun startListening(onPartial: (String) -> Unit, onResult: (String) -> Unit) {
        this.onPartialResultCallback = onPartial
        this.onResultCallback = onResult

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (speechRecognizer == null) {
                    initRecognizer()
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(intent)
                Log.d(TAG, "ASR started")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting ASR", e)
                android.widget.Toast.makeText(context, "启动识别失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun stopListening() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                speechRecognizer?.stopListening()
                Log.d(TAG, "ASR stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping ASR", e)
            }
        }
    }

    fun release() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }
}
