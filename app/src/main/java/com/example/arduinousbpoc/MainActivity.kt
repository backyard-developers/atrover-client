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
import com.example.arduinousbpoc.ui.theme.ArduinoUsbPocTheme
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class MainActivity : ComponentActivity() {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.arduinousbpoc.USB_PERMISSION"
    }

    private lateinit var usbManager: UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private var connectionStatus by mutableStateOf("연결 안됨")
    private var isConnected by mutableStateOf(false)

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
                    onConnect = { findAndConnectDevice() }
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
            isConnected = true
            connectionStatus = "연결됨: ${device.productName ?: "Arduino"}"
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
}

enum class TestTab(val title: String) {
    LED_CONTROL("LED 제어"),
    CAMERA_PREVIEW("카메라"),
    CAMERA_STREAM("스트리밍")
}

@Composable
fun MainScreen(
    connectionStatus: String,
    isConnected: Boolean,
    onLedOn: () -> Unit,
    onLedOff: () -> Unit,
    onConnect: () -> Unit
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
