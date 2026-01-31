package com.example.arduinousbpoc.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.webrtc.*
import java.util.concurrent.TimeUnit

private const val TAG = "CameraStreamScreen"

@Composable
fun CameraStreamScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var serverIp by remember { mutableStateOf("192.168.1.110") }
    var serverPort by remember { mutableStateOf("8080") }
    var wsStatus by remember { mutableStateOf("연결 안됨") }
    var rtcStatus by remember { mutableStateOf("대기 중") }
    var isWsConnected by remember { mutableStateOf(false) }
    var isRtcConnected by remember { mutableStateOf(false) }
    var myId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var webRtcManager by remember { mutableStateOf<WebRtcManager?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webRtcManager?.dispose()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "카메라 스트리밍 (WebRTC)",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Server connection settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = serverIp,
                onValueChange = { serverIp = it },
                label = { Text("서버 IP") },
                modifier = Modifier.weight(2f),
                singleLine = true,
                enabled = !isWsConnected
            )
            OutlinedTextField(
                value = serverPort,
                onValueChange = { serverPort = it },
                label = { Text("포트") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isWsConnected
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatusChip(
                label = "WebSocket",
                status = wsStatus,
                isConnected = isWsConnected
            )
            StatusChip(
                label = "WebRTC",
                status = rtcStatus,
                isConnected = isRtcConnected
            )
        }

        if (myId != null) {
            Text(
                text = "내 ID: $myId",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4CAF50),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Error message
        errorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF44336),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    errorMessage = null
                    webRtcManager = WebRtcManager(
                        context = context,
                        serverUrl = "ws://$serverIp:$serverPort",
                        onWsStatusChange = { status, connected ->
                            wsStatus = status
                            isWsConnected = connected
                            if (!connected) {
                                isRtcConnected = false
                            }
                        },
                        onRtcStatusChange = { status ->
                            rtcStatus = status
                            isRtcConnected = status == "connected"
                        },
                        onIdReceived = { id ->
                            myId = id
                        },
                        onError = { error ->
                            errorMessage = error
                        }
                    )
                    webRtcManager?.connect()
                },
                enabled = !isWsConnected && hasCameraPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier.weight(1f)
            ) {
                Text("연결")
            }

            Button(
                onClick = {
                    webRtcManager?.dispose()
                    webRtcManager = null
                    isWsConnected = false
                    isRtcConnected = false
                    wsStatus = "연결 안됨"
                    rtcStatus = "대기 중"
                    myId = null
                    errorMessage = null
                },
                enabled = isWsConnected,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                modifier = Modifier.weight(1f)
            ) {
                Text("연결 해제")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Camera preview - only show when WebSocket is connected
        if (isWsConnected && hasCameraPermission && webRtcManager != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Color.Black, RoundedCornerShape(8.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            webRtcManager?.initLocalView(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "로컬 카메라 (서버 연결됨)",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(4.dp)
                )
            }
        } else {
            // Placeholder when not connected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Color.DarkGray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when {
                            !hasCameraPermission -> "카메라 권한 필요"
                            !isWsConnected -> "서버에 연결하면 카메라가 표시됩니다"
                            else -> "카메라 초기화 중..."
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!hasCameraPermission) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        ) {
                            Text("카메라 권한 요청")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    status: String,
    isConnected: Boolean
) {
    Row(
        modifier = Modifier
            .background(
                if (isConnected) Color(0xFF4CAF50).copy(alpha = 0.2f)
                else Color(0xFFF44336).copy(alpha = 0.2f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .background(
                    if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                    RoundedCornerShape(4.dp)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: $status",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

class WebRtcManager(
    private val context: Context,
    private val serverUrl: String,
    private val onWsStatusChange: (String, Boolean) -> Unit,
    private val onRtcStatusChange: (String) -> Unit,
    private val onIdReceived: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webSocket: WebSocket? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var eglBase: EglBase? = null
    private var localView: SurfaceViewRenderer? = null
    private var myId: String? = null
    private var isWebRtcInitialized = false

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    fun connect() {
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        Log.d(TAG, "Connecting to $serverUrl")

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                mainHandler.post {
                    onWsStatusChange("연결됨", true)

                    // Initialize WebRTC after WebSocket connected
                    initWebRtc()
                }

                // Register as Android client
                val registerMsg = JSONObject().apply {
                    put("type", "register")
                    put("clientType", "android")
                }
                webSocket.send(registerMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message: $text")
                mainHandler.post {
                    handleMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                mainHandler.post {
                    onWsStatusChange("연결 끊김", false)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error", t)
                mainHandler.post {
                    onWsStatusChange("연결 실패", false)
                    onError("연결 실패: ${t.message}")
                }
            }
        })
    }

    private fun initWebRtc() {
        if (isWebRtcInitialized) return

        try {
            eglBase = EglBase.create()

            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .createInitializationOptions()
            )

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(
                    DefaultVideoEncoderFactory(
                        eglBase!!.eglBaseContext,
                        true,
                        true
                    )
                )
                .setVideoDecoderFactory(
                    DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)
                )
                .createPeerConnectionFactory()

            // Create video capturer
            videoCapturer = createCameraCapturer()
            if (videoCapturer == null) {
                onError("카메라를 찾을 수 없습니다")
                return
            }

            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase!!.eglBaseContext)

            val videoSource = peerConnectionFactory!!.createVideoSource(videoCapturer!!.isScreencast)
            videoCapturer!!.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
            videoCapturer!!.startCapture(1280, 720, 30)

            localVideoTrack = peerConnectionFactory!!.createVideoTrack("video0", videoSource)
            localVideoTrack!!.setEnabled(true)

            isWebRtcInitialized = true
            Log.d(TAG, "WebRTC initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "WebRTC initialization failed", e)
            onError("WebRTC 초기화 실패: ${e.message}")
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    fun initLocalView(view: SurfaceViewRenderer) {
        if (!isWebRtcInitialized || eglBase == null) {
            Log.w(TAG, "WebRTC not initialized yet")
            return
        }

        localView = view
        view.init(eglBase!!.eglBaseContext, null)
        view.setMirror(false)
        localVideoTrack?.addSink(view)
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.getString("type")) {
                "id" -> {
                    myId = json.getString("id")
                    onIdReceived(myId!!)
                }
                "offer" -> {
                    val from = json.getString("from")
                    val sdp = json.getString("sdp")
                    handleOffer(from, sdp)
                }
                "answer" -> {
                    val sdp = json.getString("sdp")
                    handleAnswer(sdp)
                }
                "ice-candidate" -> {
                    val candidate = json.getJSONObject("candidate")
                    handleIceCandidate(candidate)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }

    private fun createPeerConnection(): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        return peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "Signaling state: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE connection state: $state")
                mainHandler.post {
                    onRtcStatusChange(state?.name?.lowercase() ?: "unknown")
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "ICE gathering state: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val msg = JSONObject().apply {
                        put("type", "ice-candidate")
                        put("candidate", JSONObject().apply {
                            put("sdpMid", it.sdpMid)
                            put("sdpMLineIndex", it.sdpMLineIndex)
                            put("candidate", it.sdp)
                        })
                    }
                    webSocket?.send(msg.toString())
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })
    }

    private fun handleOffer(from: String, sdp: String) {
        peerConnection = createPeerConnection()

        // Add local video track
        localVideoTrack?.let {
            peerConnection?.addTrack(it, listOf("stream0"))
        }

        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                // Create answer
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        desc?.let {
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(d: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    val msg = JSONObject().apply {
                                        put("type", "answer")
                                        put("target", from)
                                        put("sdp", it.description)
                                    }
                                    webSocket?.send(msg.toString())
                                }
                                override fun onCreateFailure(error: String?) {}
                                override fun onSetFailure(error: String?) {}
                            }, it)
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "Create answer failed: $error")
                    }
                    override fun onSetFailure(error: String?) {}
                }, MediaConstraints())
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Set remote description failed: $error")
            }
        }, sessionDescription)
    }

    private fun handleAnswer(sdp: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Answer set successfully")
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Set answer failed: $error")
            }
        }, sessionDescription)
    }

    private fun handleIceCandidate(candidate: JSONObject) {
        val iceCandidate = IceCandidate(
            candidate.getString("sdpMid"),
            candidate.getInt("sdpMLineIndex"),
            candidate.getString("candidate")
        )
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun dispose() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null

        localVideoTrack?.removeSink(localView)
        localView?.release()
        localView = null

        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture", e)
        }
        videoCapturer?.dispose()
        videoCapturer = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        localVideoTrack?.dispose()
        localVideoTrack = null

        peerConnection?.close()
        peerConnection = null

        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        eglBase?.release()
        eglBase = null

        isWebRtcInitialized = false
    }
}
