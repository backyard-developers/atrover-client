package com.example.arduinousbpoc

import android.app.PendingIntent
import android.util.Log
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.arduinousbpoc.network.BackendConfig
import com.example.arduinousbpoc.network.CommandSocketManager
import com.example.arduinousbpoc.network.ConnectionState
import com.example.arduinousbpoc.network.MediaSocketManager
import com.example.arduinousbpoc.network.MotorMapping
import com.example.arduinousbpoc.network.RoverCommand
import com.example.arduinousbpoc.network.StreamingState
import com.example.arduinousbpoc.screen.CameraPreviewScreen
import com.example.arduinousbpoc.screen.CameraStreamScreen
import com.example.arduinousbpoc.screen.LedControlScreen
import com.example.arduinousbpoc.screen.MotorControlScreen
import com.example.arduinousbpoc.screen.RemoteControlScreen
import com.example.arduinousbpoc.ui.theme.ArduinoUsbPocTheme
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ATRover"
        private const val ACTION_USB_PERMISSION = "com.example.arduinousbpoc.USB_PERMISSION"
    }

    private lateinit var usbManager: UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private var connectionStatus by mutableStateOf("연결 안됨")
    private var isConnected by mutableStateOf(false)
    private var motor1Status by mutableStateOf("STOPPED")
    private var motor2Status by mutableStateOf("STOPPED")
    private var motor3Status by mutableStateOf("STOPPED")
    private var motor4Status by mutableStateOf("STOPPED")
    private var motor1Speed by mutableStateOf(255)
    private var motor2Speed by mutableStateOf(255)
    private var motor3Speed by mutableStateOf(255)
    private var motor4Speed by mutableStateOf(255)
    private var motor1Command by mutableStateOf(0)
    private var motor2Command by mutableStateOf(0)
    private var motor3Command by mutableStateOf(0)
    private var motor4Command by mutableStateOf(0)
    private var lastResponse by mutableStateOf("-")
    private val usbMutex = Mutex()

    // Motor config
    private lateinit var motorConfigPrefs: MotorConfigPreferences
    private var leftMotor by mutableStateOf(3)
    private var rightMotor by mutableStateOf(4)

    // Backend managers
    private lateinit var commandSocketManager: CommandSocketManager
    private lateinit var mediaSocketManager: MediaSocketManager
    private var backendConfig: BackendConfig? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let { connectToDevice(it) }
                        } else {
                            connectionStatus = "권한 거부됨"
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    findAndConnectDevice()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    disconnect()
                    connectionStatus = "디바이스 분리됨"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        usbManager = getSystemService(USB_SERVICE) as UsbManager

        // Initialize motor config
        motorConfigPrefs = MotorConfigPreferences(this)
        leftMotor = motorConfigPrefs.getLeftMotor()
        rightMotor = motorConfigPrefs.getRightMotor()

        // Initialize backend managers
        commandSocketManager = CommandSocketManager(
            onCommandReceived = { command -> handleRoverCommand(command) },
            onMotorConfigReceived = { mapping ->
                leftMotor = mapping.left
                rightMotor = mapping.right
                motorConfigPrefs.save(mapping.left, mapping.right)
            }
        )
        mediaSocketManager = MediaSocketManager()

        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }

        findAndConnectDevice()

        setContent {
            ArduinoUsbPocTheme {
                val cmdState by commandSocketManager.connectionState.collectAsState()
                val mediaState by mediaSocketManager.connectionState.collectAsState()
                val streamState by mediaSocketManager.streamingState.collectAsState()
                val roverId by commandSocketManager.roverId.collectAsState()
                val lastCmd by commandSocketManager.lastCommand.collectAsState()
                val cmdError by commandSocketManager.errorLog.collectAsState()
                val mediaError by mediaSocketManager.errorLog.collectAsState()

                // Auto-connect media server when command server registers
                androidx.compose.runtime.LaunchedEffect(roverId) {
                    val id = roverId
                    val cfg = backendConfig
                    if (id != null && cfg != null && mediaState == ConnectionState.Disconnected) {
                        mediaSocketManager.connect(cfg, id)
                    }
                }

                MainScreen(
                    connectionStatus = connectionStatus,
                    isConnected = isConnected,
                    onLedOn = { sendCommand("1") },
                    onLedOff = { sendCommand("0") },
                    onConnect = { findAndConnectDevice() },
                    motor1Status = motor1Status,
                    motor2Status = motor2Status,
                    motor3Status = motor3Status,
                    motor4Status = motor4Status,
                    motor1Speed = motor1Speed,
                    motor2Speed = motor2Speed,
                    motor3Speed = motor3Speed,
                    motor4Speed = motor4Speed,
                    onSpeedChange = { motorId, speed ->
                        when (motorId) {
                            1 -> { motor1Speed = speed; sendMotorCommand(1, motor1Command) }
                            2 -> { motor2Speed = speed; sendMotorCommand(2, motor2Command) }
                            3 -> { motor3Speed = speed; sendMotorCommand(3, motor3Command) }
                            4 -> { motor4Speed = speed; sendMotorCommand(4, motor4Command) }
                        }
                    },
                    lastResponse = lastResponse,
                    onMotorCommand = { motorId, command -> sendMotorCommand(motorId, command) },
                    // Remote control props
                    commandConnectionState = cmdState,
                    mediaConnectionState = mediaState,
                    streamingState = streamState,
                    roverId = roverId,
                    lastCommand = lastCmd,
                    onBackendConnect = { ip, cmdPort, mdPort, name ->
                        val config = BackendConfig(ip, cmdPort, mdPort, name)
                        backendConfig = config
                        commandSocketManager.connect(config)
                    },
                    onBackendDisconnect = {
                        mediaSocketManager.disconnect()
                        commandSocketManager.disconnect()
                        backendConfig = null
                    },
                    onStartStreaming = {
                        mediaSocketManager.startStreaming(this@MainActivity, this@MainActivity)
                    },
                    onStopStreaming = {
                        mediaSocketManager.stopStreaming()
                    },
                    errorLog = listOfNotNull(cmdError, mediaError).joinToString("\n"),
                    leftMotor = leftMotor,
                    rightMotor = rightMotor,
                    onMotorConfigChange = { left, right ->
                        leftMotor = left
                        rightMotor = right
                        motorConfigPrefs.save(left, right)
                        commandSocketManager.sendMotorConfig(MotorMapping(left, right))
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        commandSocketManager.destroy()
        mediaSocketManager.destroy()
        disconnect()
        unregisterReceiver(usbReceiver)
    }

    /**
     * Convert backend RoverCommand to USB motor commands.
     * 2WD layout: uses configurable leftMotor/rightMotor assignment.
     * Command: 0=stop, 1=forward, 2=backward
     */
    private fun handleRoverCommand(command: RoverCommand) {
        val speedValue = ((command.speed ?: 50) * 2.55).toInt().coerceIn(0, 255)
        val lm = leftMotor
        val rm = rightMotor
        Log.d(TAG, "handleRoverCommand: action=${command.action}, direction=${command.direction}, speed=${command.speed}→$speedValue, leftMotor=M$lm, rightMotor=M$rm")

        when (lm) {
            1 -> motor1Speed = speedValue
            2 -> motor2Speed = speedValue
            3 -> motor3Speed = speedValue
            4 -> motor4Speed = speedValue
        }
        when (rm) {
            1 -> motor1Speed = speedValue
            2 -> motor2Speed = speedValue
            3 -> motor3Speed = speedValue
            4 -> motor4Speed = speedValue
        }

        val cmds: List<Pair<Int, Int>> = when (command.action) {
            "move" -> when (command.direction) {
                "forward" -> listOf(lm to 1, rm to 1)
                "backward" -> listOf(lm to 2, rm to 2)
                "left" -> listOf(lm to 2, rm to 1)
                "right" -> listOf(lm to 1, rm to 2)
                else -> return
            }
            "stop", "calibrate" -> listOf(lm to 0, rm to 0)
            "rotate" -> {
                val degrees = command.degrees ?: 90
                if (degrees > 0) listOf(lm to 1, rm to 2)
                else listOf(lm to 2, rm to 1)
            }
            else -> return
        }

        val cmdDesc = cmds.joinToString(", ") { (m, c) ->
            val dir = when(c) { 0 -> "STOP"; 1 -> "FWD"; 2 -> "BWD"; else -> "?$c" }
            "M$m→$dir"
        }
        Log.d(TAG, "handleRoverCommand: sending [$cmdDesc] speed=$speedValue")
        sendMotorCommands(cmds)
    }

    /**
     * Send direct motor command with explicit motor IDs and per-motor speed.
     * Protocol: <D[n][id1][cmd1][spd1]...[idn][cmdn][spdn][checksum]>
     * Response: <O[n]D> (success) or <E[n]D> (failure)
     */
    private fun sendMotorCommands(commands: List<Pair<Int, Int>>) {
        if (!isConnected || usbSerialPort == null || commands.isEmpty()) return

        // Update saved state
        for ((motorId, command) in commands) {
            when (motorId) {
                1 -> motor1Command = command
                2 -> motor2Command = command
                3 -> motor3Command = command
                4 -> motor4Command = command
            }
        }

        val sortedCmds = commands.sortedBy { it.first }
        val n = sortedCmds.size
        if (n < 2 || n > 4) return

        // Per-motor speed lookup
        fun getSpeed(motorId: Int): Int = when (motorId) {
            1 -> motor1Speed; 2 -> motor2Speed
            3 -> motor3Speed; 4 -> motor4Speed
            else -> 255
        }

        CoroutineScope(Dispatchers.IO).launch {
            usbMutex.withLock {
                try {
                    val port = usbSerialPort ?: return@withLock

                    // Build direct packet: <D[n][id1][cmd1][spd1]...[idn][cmdn][spdn][checksum]>
                    val nChar = n.toString()[0]

                    // Checksum: D ^ n ^ id1 ^ cmd1 ^ spd1 ^ ... ^ idn ^ cmdn ^ spdn
                    var checksum = 'D'.code xor nChar.code
                    for ((motorId, cmd) in sortedCmds) {
                        val spd = getSpeed(motorId)
                        checksum = checksum xor motorId.toString()[0].code
                        checksum = checksum xor cmd.toString()[0].code
                        checksum = checksum xor spd
                    }

                    // Assemble: STX + D + n + (id+cmd+spd)*n + checksum + ETX
                    val message = ByteArray(3 + n * 3 + 2)  // 1(STX)+1(D)+1(n) + n*3 + 1(chk)+1(ETX)
                    message[0] = '<'.code.toByte()
                    message[1] = 'D'.code.toByte()
                    message[2] = nChar.code.toByte()
                    for (i in sortedCmds.indices) {
                        val spd = getSpeed(sortedCmds[i].first)
                        message[3 + i * 3] = sortedCmds[i].first.toString()[0].code.toByte()
                        message[4 + i * 3] = sortedCmds[i].second.toString()[0].code.toByte()
                        message[5 + i * 3] = spd.toByte()
                    }
                    message[3 + n * 3] = checksum.toByte()
                    message[4 + n * 3] = '>'.code.toByte()

                    val hexStr = message.joinToString(" ") { "%02X".format(it) }
                    val cmdDesc = sortedCmds.joinToString(", ") { (m, c) ->
                        val spd = getSpeed(m)
                        val dir = when(c) { 0 -> "STOP"; 1 -> "FWD"; 2 -> "BWD"; else -> "?$c" }
                        "M$m→$dir@$spd"
                    }
                    Log.d(TAG, "USB TX (direct): [$cmdDesc] packet=[$hexStr] (${message.size} bytes)")
                    port.write(message, 1000)

                    // Read response
                    delay(30)
                    val responseBuffer = ByteArray(20)
                    val bytesRead = try {
                        port.read(responseBuffer, 200)
                    } catch (e: Exception) { 0 }

                    if (bytesRead > 0) {
                        val response = String(responseBuffer, 0, bytesRead).trim()
                        val rxHex = responseBuffer.take(bytesRead).joinToString(" ") { "%02X".format(it) }
                        Log.d(TAG, "USB RX (direct): \"$response\" hex=[$rxHex] ($bytesRead bytes)")
                        lastResponse = response

                        if (response.contains("O")) {
                            for ((motorId, command) in commands) {
                                val statusText = when (command) {
                                    0 -> "STOPPED"; 1 -> "FORWARD"; 2 -> "BACKWARD"
                                    else -> "UNKNOWN"
                                }
                                when (motorId) {
                                    1 -> motor1Status = statusText
                                    2 -> motor2Status = statusText
                                    3 -> motor3Status = statusText
                                    4 -> motor4Status = statusText
                                }
                            }
                            Log.d(TAG, "USB RX (direct): OK - motors updated")
                        } else {
                            Log.w(TAG, "USB RX (direct): unexpected response \"$response\"")
                        }
                    } else {
                        Log.w(TAG, "USB RX (direct): no response from Arduino")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "USB TX (direct) error: ${e.message}")
                    lastResponse = "Error"
                    if (e.message?.contains("write") == true) {
                        isConnected = false
                        connectionStatus = "연결 끊김"
                    }
                }
            }
        }
    }

    private fun findAndConnectDevice() {
        val availableDrivers: List<UsbSerialDriver> = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        if (availableDrivers.isEmpty()) {
            connectionStatus = "Arduino를 찾을 수 없음"
            return
        }

        val driver = availableDrivers[0]
        val device = driver.device

        if (!usbManager.hasPermission(device)) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val permissionIntent = PendingIntent.getBroadcast(
                this, 0, Intent(ACTION_USB_PERMISSION), flags
            )
            usbManager.requestPermission(device, permissionIntent)
            connectionStatus = "권한 요청 중..."
            return
        }

        connectToDevice(device)
    }

    private fun connectToDevice(device: UsbDevice) {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val driver = availableDrivers.find { it.device == device } ?: run {
            connectionStatus = "드라이버를 찾을 수 없음"
            return
        }

        val connection = usbManager.openDevice(device) ?: run {
            connectionStatus = "연결 실패"
            return
        }

        usbSerialPort = driver.ports[0]
        try {
            usbSerialPort?.open(connection)
            usbSerialPort?.setParameters(
                9600,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            usbSerialPort?.dtr = true
            usbSerialPort?.rts = true
            isConnected = true
            connectionStatus = "연결됨: ${device.productName ?: "Arduino"}"

            // 아두이노 리셋 대기
            CoroutineScope(Dispatchers.IO).launch {
                delay(2000)
            }
        } catch (e: Exception) {
            connectionStatus = "연결 오류: ${e.message}"
            isConnected = false
        }
    }

    private fun disconnect() {
        try {
            usbSerialPort?.close()
        } catch (_: Exception) { }
        usbSerialPort = null
        isConnected = false
    }

    private fun sendCommand(command: String) {
        if (!isConnected || usbSerialPort == null) {
            connectionStatus = "연결되지 않음"
            return
        }

        try {
            usbSerialPort?.write(command.toByteArray(), 1000)
        } catch (e: Exception) {
            connectionStatus = "전송 오류: ${e.message}"
        }
    }

    private fun sendMotorCommand(motorId: Int, command: Int) {
        if (!isConnected || usbSerialPort == null) {
            connectionStatus = "연결되지 않음"
            return
        }

        // 마지막 명령 저장
        when (motorId) {
            1 -> motor1Command = command
            2 -> motor2Command = command
            3 -> motor3Command = command
            4 -> motor4Command = command
        }

        val speed = when (motorId) {
            1 -> motor1Speed
            2 -> motor2Speed
            3 -> motor3Speed
            4 -> motor4Speed
            else -> 255
        }

        CoroutineScope(Dispatchers.IO).launch {
            usbMutex.withLock {
                try {
                    val port = usbSerialPort ?: return@withLock

                    val motorChar = motorId.toString()[0]
                    val cmdChar = command.toString()[0]
                    val speedByte = speed.toByte()
                    val checksum = (motorChar.code xor cmdChar.code xor speed).toChar()

                    val message = byteArrayOf(
                        '<'.code.toByte(),
                        motorChar.code.toByte(),
                        cmdChar.code.toByte(),
                        speedByte,
                        checksum.code.toByte(),
                        '>'.code.toByte()
                    )

                    val dir = when(command) { 0 -> "STOP"; 1 -> "FWD"; 2 -> "BWD"; else -> "?$command" }
                    val hexStr = message.joinToString(" ") { "%02X".format(it) }
                    Log.d(TAG, "USB TX (single): M$motorId→$dir speed=$speed packet=[$hexStr] (${message.size} bytes)")

                    // 재시도 로직
                    var success = false
                    for (retry in 0..2) {
                        try {
                            port.write(message, 1000)
                            success = true
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "USB TX (single): retry $retry failed: ${e.message}")
                            if (retry < 2) {
                                delay(50)
                            } else {
                                throw e
                            }
                        }
                    }

                    if (!success) return@withLock

                    // 응답 대기
                    delay(30)

                    // 응답 읽기
                    val responseBuffer = ByteArray(20)
                    val bytesRead = try {
                        port.read(responseBuffer, 200)
                    } catch (e: Exception) {
                        0
                    }

                    if (bytesRead > 0) {
                        val response = String(responseBuffer, 0, bytesRead).trim()
                        val rxHex = responseBuffer.take(bytesRead).joinToString(" ") { "%02X".format(it) }
                        Log.d(TAG, "USB RX (single): M$motorId \"$response\" hex=[$rxHex] ($bytesRead bytes)")
                        lastResponse = response

                        if (response.contains("O")) {
                            val statusText = when (command) {
                                0 -> "STOPPED"
                                1 -> "FORWARD"
                                2 -> "BACKWARD"
                                else -> "UNKNOWN"
                            }
                            when (motorId) {
                                1 -> motor1Status = statusText
                                2 -> motor2Status = statusText
                                3 -> motor3Status = statusText
                                4 -> motor4Status = statusText
                            }
                            Log.d(TAG, "USB RX (single): M$motorId OK → $statusText")
                        } else {
                            Log.w(TAG, "USB RX (single): M$motorId unexpected \"$response\"")
                        }
                    } else {
                        Log.w(TAG, "USB RX (single): M$motorId no response")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "USB TX (single) error: M$motorId ${e.message}")
                    lastResponse = "Error"
                    // 연결 끊김 감지 시 상태 업데이트
                    if (e.message?.contains("write") == true) {
                        isConnected = false
                        connectionStatus = "연결 끊김"
                    }
                }
            }
        }
    }
}

enum class TestTab(val title: String) {
    LED_CONTROL("LED"),
    MOTOR_CONTROL("모터"),
    CAMERA_PREVIEW("카메라"),
    CAMERA_STREAM("스트리밍"),
    REMOTE_CONTROL("원격")
}

@Composable
fun MainScreen(
    connectionStatus: String,
    isConnected: Boolean,
    onLedOn: () -> Unit,
    onLedOff: () -> Unit,
    onConnect: () -> Unit,
    motor1Status: String,
    motor2Status: String,
    motor3Status: String,
    motor4Status: String,
    motor1Speed: Int,
    motor2Speed: Int,
    motor3Speed: Int,
    motor4Speed: Int,
    onSpeedChange: (Int, Int) -> Unit,
    lastResponse: String,
    onMotorCommand: (Int, Int) -> Unit,
    // Remote control props
    commandConnectionState: ConnectionState,
    mediaConnectionState: ConnectionState,
    streamingState: StreamingState,
    roverId: String?,
    lastCommand: String?,
    onBackendConnect: (String, Int, Int, String) -> Unit,
    onBackendDisconnect: () -> Unit,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
    errorLog: String,
    leftMotor: Int = 3,
    rightMotor: Int = 4,
    onMotorConfigChange: (Int, Int) -> Unit = { _, _ -> }
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = TestTab.entries

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    )
                }
            }

            when (tabs[selectedTab]) {
                TestTab.LED_CONTROL -> {
                    LedControlScreen(
                        connectionStatus = connectionStatus,
                        isConnected = isConnected,
                        onLedOn = onLedOn,
                        onLedOff = onLedOff,
                        onConnect = onConnect
                    )
                }
                TestTab.MOTOR_CONTROL -> {
                    MotorControlScreen(
                        connectionStatus = connectionStatus,
                        isConnected = isConnected,
                        motor1Status = motor1Status,
                        motor2Status = motor2Status,
                        motor3Status = motor3Status,
                        motor4Status = motor4Status,
                        motor1Speed = motor1Speed,
                        motor2Speed = motor2Speed,
                        motor3Speed = motor3Speed,
                        motor4Speed = motor4Speed,
                        onSpeedChange = onSpeedChange,
                        lastResponse = lastResponse,
                        onMotorCommand = onMotorCommand,
                        onConnect = onConnect
                    )
                }
                TestTab.CAMERA_PREVIEW -> {
                    CameraPreviewScreen()
                }
                TestTab.CAMERA_STREAM -> {
                    CameraStreamScreen()
                }
                TestTab.REMOTE_CONTROL -> {
                    RemoteControlScreen(
                        commandConnectionState = commandConnectionState,
                        mediaConnectionState = mediaConnectionState,
                        streamingState = streamingState,
                        roverId = roverId,
                        lastCommand = lastCommand,
                        usbConnectionStatus = connectionStatus,
                        isUsbConnected = isConnected,
                        onConnect = onBackendConnect,
                        onDisconnect = onBackendDisconnect,
                        onStartStreaming = onStartStreaming,
                        onStopStreaming = onStopStreaming,
                        errorLog = errorLog,
                        leftMotor = leftMotor,
                        rightMotor = rightMotor,
                        onMotorConfigChange = onMotorConfigChange
                    )
                }
            }
        }
    }
}
