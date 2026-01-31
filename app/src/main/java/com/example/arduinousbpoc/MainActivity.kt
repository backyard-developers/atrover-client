package com.example.arduinousbpoc

import android.app.PendingIntent
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.arduinousbpoc.screen.CameraPreviewScreen
import com.example.arduinousbpoc.screen.CameraStreamScreen
import com.example.arduinousbpoc.screen.LedControlScreen
import com.example.arduinousbpoc.screen.MotorControlScreen
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
    private var motor1Command by mutableStateOf(0)  // 마지막 명령 저장
    private var motor2Command by mutableStateOf(0)
    private var motor3Command by mutableStateOf(0)
    private var motor4Command by mutableStateOf(0)
    private var lastResponse by mutableStateOf("-")
    private val usbMutex = Mutex()

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
                    onMotorCommand = { motorId, command -> sendMotorCommand(motorId, command) }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        unregisterReceiver(usbReceiver)
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
                    usbSerialPort?.write(message, 1000)

                    // 응답 대기
                    delay(50)

                    // 응답 읽기
                    val responseBuffer = ByteArray(20)
                    val bytesRead = usbSerialPort?.read(responseBuffer, 500) ?: 0
                    if (bytesRead > 0) {
                        val response = String(responseBuffer, 0, bytesRead).trim()
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
                        }
                    } else {
                        lastResponse = "No response"
                    }
                } catch (e: Exception) {
                    connectionStatus = "전송 오류: ${e.message}"
                    lastResponse = "Error: ${e.message}"
                }
            }
        }
    }
}

enum class TestTab(val title: String) {
    LED_CONTROL("LED"),
    MOTOR_CONTROL("모터"),
    CAMERA_PREVIEW("카메라"),
    CAMERA_STREAM("스트리밍")
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
    onMotorCommand: (Int, Int) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = TestTab.entries

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
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
            }
        }
    }
}
