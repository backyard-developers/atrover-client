package com.example.arduinousbpoc.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.arduinousbpoc.network.RoverCommand
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UsbMotorController(private val context: Context) {

    companion object {
        private const val TAG = "ATRover"
        private const val ACTION_USB_PERMISSION = "com.example.arduinousbpoc.USB_PERMISSION"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private val usbMutex = Mutex()

    // Observable state for Compose
    var connectionStatus by mutableStateOf("연결 안됨")
        private set
    var isConnected by mutableStateOf(false)
        private set
    var lastResponse by mutableStateOf("-")
        private set

    var motor1Status by mutableStateOf("STOPPED"); private set
    var motor2Status by mutableStateOf("STOPPED"); private set
    var motor3Status by mutableStateOf("STOPPED"); private set
    var motor4Status by mutableStateOf("STOPPED"); private set

    var motor1Speed by mutableStateOf(255); private set
    var motor2Speed by mutableStateOf(255); private set
    var motor3Speed by mutableStateOf(255); private set
    var motor4Speed by mutableStateOf(255); private set

    var motor1Command = 0; private set
    var motor2Command = 0; private set
    var motor3Command = 0; private set
    var motor4Command = 0; private set

    // Motor config
    var leftMotor by mutableStateOf(3)
    var rightMotor by mutableStateOf(4)

    val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
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

    fun createIntentFilter(): IntentFilter = IntentFilter().apply {
        addAction(ACTION_USB_PERMISSION)
        addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
    }

    fun findAndConnectDevice() {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

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
                context, 0, Intent(ACTION_USB_PERMISSION), flags
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

            CoroutineScope(Dispatchers.IO).launch {
                delay(2000)
            }
        } catch (e: Exception) {
            connectionStatus = "연결 오류: ${e.message}"
            isConnected = false
        }
    }

    fun disconnect() {
        try {
            usbSerialPort?.close()
        } catch (_: Exception) { }
        usbSerialPort = null
        isConnected = false
    }

    fun setSpeed(motorId: Int, speed: Int) {
        when (motorId) {
            1 -> motor1Speed = speed
            2 -> motor2Speed = speed
            3 -> motor3Speed = speed
            4 -> motor4Speed = speed
        }
    }

    fun sendMotorCommand(motorId: Int, command: Int) {
        if (!isConnected || usbSerialPort == null) {
            connectionStatus = "연결되지 않음"
            return
        }

        when (motorId) {
            1 -> motor1Command = command
            2 -> motor2Command = command
            3 -> motor3Command = command
            4 -> motor4Command = command
        }

        val speed = getSpeed(motorId)

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

                    delay(30)

                    val responseBuffer = ByteArray(20)
                    val bytesRead = try {
                        port.read(responseBuffer, 200)
                    } catch (e: Exception) { 0 }

                    if (bytesRead > 0) {
                        val response = String(responseBuffer, 0, bytesRead).trim()
                        val rxHex = responseBuffer.take(bytesRead).joinToString(" ") { "%02X".format(it) }
                        Log.d(TAG, "USB RX (single): M$motorId \"$response\" hex=[$rxHex] ($bytesRead bytes)")
                        lastResponse = response

                        if (response.contains("O")) {
                            val statusText = when (command) {
                                0 -> "STOPPED"; 1 -> "FORWARD"; 2 -> "BACKWARD"
                                else -> "UNKNOWN"
                            }
                            updateMotorStatus(motorId, statusText)
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
                    if (e.message?.contains("write") == true) {
                        isConnected = false
                        connectionStatus = "연결 끊김"
                    }
                }
            }
        }
    }

    fun handleRoverCommand(command: RoverCommand) {
        val speedValue = ((command.speed ?: 50) * 2.55).toInt().coerceIn(0, 255)
        val lm = leftMotor
        val rm = rightMotor
        Log.d(TAG, "handleRoverCommand: action=${command.action}, direction=${command.direction}, speed=${command.speed}→$speedValue, leftMotor=M$lm, rightMotor=M$rm")

        setSpeed(lm, speedValue)
        setSpeed(rm, speedValue)

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

    private fun sendMotorCommands(commands: List<Pair<Int, Int>>) {
        if (!isConnected || usbSerialPort == null || commands.isEmpty()) return

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

        CoroutineScope(Dispatchers.IO).launch {
            usbMutex.withLock {
                try {
                    val port = usbSerialPort ?: return@withLock

                    val nChar = n.toString()[0]
                    var checksum = 'D'.code xor nChar.code
                    for ((motorId, cmd) in sortedCmds) {
                        val spd = getSpeed(motorId)
                        checksum = checksum xor motorId.toString()[0].code
                        checksum = checksum xor cmd.toString()[0].code
                        checksum = checksum xor spd
                    }

                    val message = ByteArray(3 + n * 3 + 2)
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
                                updateMotorStatus(motorId, statusText)
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

    private fun getSpeed(motorId: Int): Int = when (motorId) {
        1 -> motor1Speed; 2 -> motor2Speed
        3 -> motor3Speed; 4 -> motor4Speed
        else -> 255
    }

    private fun updateMotorStatus(motorId: Int, status: String) {
        when (motorId) {
            1 -> motor1Status = status
            2 -> motor2Status = status
            3 -> motor3Status = status
            4 -> motor4Status = status
        }
    }
}
