package com.example.arduinousbpoc.network

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TAG = "MediaSocketManager"

enum class StreamingState {
    Idle, Streaming, Error
}

class MediaSocketManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _streamingState = MutableStateFlow(StreamingState.Idle)
    val streamingState: StateFlow<StreamingState> = _streamingState

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered

    private var webSocket: WebSocket? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var lastFrameTime = 0L
    private var jpegQuality = 50
    private val frameIntervalMs = 66L // ~15fps

    private val mainHandler = Handler(Looper.getMainLooper())

    // Deferred streaming: context/lifecycle saved to start after registered
    private var pendingStreamContext: Context? = null
    private var pendingStreamLifecycle: LifecycleOwner? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect(config: BackendConfig, roverId: String) {
        _connectionState.value = ConnectionState.Connecting

        val request = Request.Builder()
            .url(config.mediaWsUrl)
            .build()

        Log.d(TAG, "Connecting to ${config.mediaWsUrl}")

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to Media Server")
                _connectionState.value = ConnectionState.Connected

                // Register with roverId
                val registerMsg = backendJson.encodeToString(
                    MediaRegisterMessage.serializer(),
                    MediaRegisterMessage(roverId = roverId)
                )
                webSocket.send(registerMsg)
                Log.d(TAG, "Sent media register: $registerMsg")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Media message: $text")
                mainHandler.post { handleMessage(text) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Media closing: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Media closed: $code $reason")
                _connectionState.value = ConnectionState.Disconnected
                _streamingState.value = StreamingState.Idle
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Media connection failed", t)
                _connectionState.value = ConnectionState.Error
                _streamingState.value = StreamingState.Error
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val envelope = backendJson.decodeFromString(MessageEnvelope.serializer(), text)
            when (envelope.type) {
                "registered" -> {
                    Log.d(TAG, "Media server registered successfully")
                    _isRegistered.value = true
                    // Start deferred streaming if requested before registration
                    val ctx = pendingStreamContext
                    val lifecycle = pendingStreamLifecycle
                    if (ctx != null && lifecycle != null) {
                        pendingStreamContext = null
                        pendingStreamLifecycle = null
                        doStartStreaming(ctx, lifecycle)
                    }
                }
                "error" -> {
                    Log.e(TAG, "Media server error: $text")
                    _streamingState.value = StreamingState.Error
                }
                "start_stream" -> {
                    Log.d(TAG, "Start stream requested")
                    _streamingState.value = StreamingState.Streaming
                }
                "stop_stream" -> {
                    Log.d(TAG, "Stop stream requested")
                    _streamingState.value = StreamingState.Idle
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing media message", e)
        }
    }

    fun startStreaming(context: Context, lifecycleOwner: LifecycleOwner) {
        if (_isRegistered.value) {
            doStartStreaming(context, lifecycleOwner)
        } else {
            // Defer until registered response
            Log.d(TAG, "Deferring streaming start until registered")
            pendingStreamContext = context
            pendingStreamLifecycle = lifecycleOwner
        }
    }

    private fun doStartStreaming(context: Context, lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                _streamingState.value = StreamingState.Streaming
                Log.d(TAG, "Camera streaming started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera", e)
                _streamingState.value = StreamingState.Error
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastFrameTime < frameIntervalMs) {
                imageProxy.close()
                return
            }
            lastFrameTime = now

            if (_streamingState.value != StreamingState.Streaming ||
                _connectionState.value != ConnectionState.Connected) {
                imageProxy.close()
                return
            }

            val jpeg = imageProxyToJpeg(imageProxy)
            if (jpeg != null) {
                // Binary frame: [0x01][JPEG data]
                val frame = ByteArray(1 + jpeg.size)
                frame[0] = 0x01
                System.arraycopy(jpeg, 0, frame, 1, jpeg.size)
                webSocket?.send(frame.toByteString(0, frame.size))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToJpeg(imageProxy: ImageProxy): ByteArray? {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            jpegQuality,
            out
        )
        return out.toByteArray()
    }

    fun stopStreaming() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageAnalysis = null
        _streamingState.value = StreamingState.Idle
        Log.d(TAG, "Camera streaming stopped")
    }

    fun disconnect() {
        stopStreaming()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        _isRegistered.value = false
        pendingStreamContext = null
        pendingStreamLifecycle = null
    }

    fun destroy() {
        disconnect()
        analysisExecutor.shutdown()
        scope.cancel()
    }
}
