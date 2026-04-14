package com.example.hrhostclone

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.EnumSet
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeoutException
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val APP_NAME = "HX HOST"
private const val APP_VERSION = "1.0.0"
private const val MODEL_DIR = "models"
private const val USB_PERMISSION_ACTION = "com.example.hrhostclone.USB_PERMISSION"
private const val USB_TEST_COMMAND_TIMEOUT_MS = 2000
private const val MAKCU_VENDOR_ID = 0x1A86
private const val MAKCU_PID_CH343 = 0x55D3
private const val MAKCU_SERIAL_BAUD = 4_000_000
private const val MAKCU_FALLBACK_BAUD = 115_200
private const val MAKCU_PROBE_TIMEOUT_MS = 280
private const val MAKCU_BUTTON_STREAM_PERIOD_MS = 4
private const val MAKCU_USB_READ_TIMEOUT_MS = 24
private const val MAKCU_HOLD_POLL_INTERVAL = 3
private const val MAKCU_V2_FRAME_HEAD = 0x50
private const val MAKCU_V2_CMD_BUTTONS = 0x02
private const val MAKCU_V2_CMD_MOVE = 0x0D
private const val MAKCU_V2_CMD_BAUD = 0xB1
private const val MAKCU_V2_CMD_BYPASS = 0xB2
private const val MAKCU_V2_CMD_ECHO = 0xB4
private const val MAKCU_V2_CMD_VERSION = 0xBF
private val MAKCU_BAUD_CANDIDATES = intArrayOf(
    MAKCU_SERIAL_BAUD,
    3_000_000,
    2_000_000,
    1_500_000,
    1_000_000,
    921_600,
    460_800,
    230_400,
    MAKCU_FALLBACK_BAUD
)
private val MAKCU_BAUD_CANDIDATES_CH343 = intArrayOf(
    MAKCU_FALLBACK_BAUD,
    230_400,
    460_800,
    921_600,
    1_000_000,
    1_500_000,
    2_000_000,
    3_000_000,
    MAKCU_SERIAL_BAUD
)
private const val AUTO_CONFIG_PREFS = "hx_host_prefs"
private const val AUTO_CONFIG_KEY = "app_auto_config_json"
private const val ONNX_LOG_TAG = "HXHostOnnx"
private const val ONNX_RUNTIME_LOG_LIMIT = 48
private const val ONNX_INFERENCE_LOG_INTERVAL_MS = 3000L

enum class ModelKind { NCNN, ONNX, FILE }

data class ModelEntry(
    val name: String,
    val kind: ModelKind,
    val paramPath: String? = null,
    val binPath: String? = null,
    val filePath: String? = null
)

data class HotkeyConfig(
    val name: String,
    val enabled: Boolean = false,
    val trigger: String = "右",
    val autoAim: Boolean = false,
    val aimMode: String = "class_priority",
    val aimByCategory: Boolean = false,
    val enabledCategories: List<Boolean> = listOf(true, true, true, true),
    val categoryPriorityEnabled: Boolean = false,
    val categoryOrder: List<Int> = listOf(0, 1, 2, 3),
    val yOffset: List<Int> = listOf(79, 50, 0, 0),
    val autoFire: Boolean = false,
    val fireRangePx: Float = 5f,
    val initialDelayMs: Int = 50,
    val minClick: Int = 1,
    val maxClick: Int = 1,
    val minIntervalMs: Int = 50,
    val maxIntervalMs: Int = 100,
    val burstIntervalMs: Int = 200,
    val sensitivity: Float = 0.5f
)

enum class AppTab(val label: String, val icon: ImageVector) {
    Monitor("来源与监控", Icons.Outlined.Visibility),
    Input("输入控制", Icons.Outlined.Gamepad),
    Function("功能", Icons.Outlined.Extension),
    About("关于", Icons.Outlined.Info),
    Settings("设置", Icons.Outlined.Settings)
}

data class StreamStats(
    val connected: Boolean = false,
    val protocol: String = "UDP",
    val receiveFps: Int = 0,
    val bytesPerSec: Long = 0,
    val targetCount: Int = 0,
    val latencyMs: Int = 0
)

data class NcnnObject(val x: Float, val y: Float, val w: Float, val h: Float, val label: Int, val prob: Float)

private data class RuntimeAimbotConfig(
    val enabled: Boolean,
    val autoAimEnabled: Boolean,
    val autoFireEnabled: Boolean,
    val sensitivity: Float,
    val yOffsetPercents: List<Int>,
    val aimMode: String,
    val aimByCategory: Boolean,
    val enabledCategories: List<Boolean>,
    val categoryPriorityEnabled: Boolean,
    val categoryOrder: List<Int>,
    val triggerMask: Int,
    val fireRangePx: Float,
    val initialDelayMs: Int,
    val minClick: Int,
    val maxClick: Int,
    val minIntervalMs: Int,
    val maxIntervalMs: Int,
    val burstIntervalMs: Int
) {
    fun yOffsetFor(label: Int): Int {
        val fallback = yOffsetPercents.firstOrNull() ?: 50
        if (label < 0) return fallback
        return yOffsetPercents.getOrElse(label) { fallback }
    }

    fun isCategoryEnabled(label: Int): Boolean {
        if (!aimByCategory) return true
        if (label < 0) return true
        val categories = if (enabledCategories.size == 4) enabledCategories else listOf(true, true, true, true)
        return categories.getOrElse(label) { true }
    }

    fun priorityRank(label: Int): Int {
        if (label < 0) return Int.MAX_VALUE
        if (!categoryPriorityEnabled) return label
        val order = normalizeCategoryOrder(categoryOrder)
        val idx = order.indexOf(label)
        return if (idx >= 0) idx else Int.MAX_VALUE
    }
}

private data class PdRuntimeSnapshot(
    val active: Boolean = false,
    val targetId: Int = -1,
    val errX: Float = 0f,
    val errY: Float = 0f,
    val pdOutX: Float = 0f,
    val pdOutY: Float = 0f,
    val smoothOutX: Float = 0f,
    val smoothOutY: Float = 0f,
    val moveX: Int = 0,
    val moveY: Int = 0,
    val clampX: Boolean = false,
    val clampY: Boolean = false,
    val inFireRange: Boolean = false,
    val autoFireState: String = "idle"
)

private data class MakcuButtonsParseState(
    val pendingBytes: ByteArrayOutputStream = ByteArrayOutputStream(),
    var snapshotMaskCache: Int = 0
)

private data class MakcuButtonsParseResult(
    val mask: Int,
    val source: String,
    val dataPreview: ByteArray
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NcnnEngine.tryLoadLibrary()
        setContent { MainApp() }
    }
}

object NcnnEngine {
    var isLoaded: Boolean = false
        private set
    var isInitialized: Boolean = false
        private set

    fun tryLoadLibrary() {
        isLoaded = try {
            System.loadLibrary("hxhost_core")
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun safeInit(param: String, bin: String): Boolean {
        if (!isLoaded) return false
        val ok = runCatching { init(param, bin) }.getOrDefault(false)
        isInitialized = ok
        return ok
    }

    fun safeDetect(bitmap: Bitmap): Array<NcnnObject>? {
        if (!isLoaded || !isInitialized) return null
        return runCatching { detect(bitmap) }.getOrNull()
    }

    external fun init(paramPath: String, binPath: String): Boolean
    external fun detect(bitmap: Bitmap): Array<NcnnObject>?
}

object OnnxEngine {
    private data class SessionInitResult(
        val session: OrtSession,
        val options: OrtSession.SessionOptions,
        val providerLabel: String,
        val providerNote: String? = null
    )

    private val lock = Any()
    private val _runtimeLogs = MutableStateFlow<List<String>>(emptyList())
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var sessionOptions: OrtSession.SessionOptions? = null
    private var lastInferenceLogAtMs: Long = 0L
    private var inputName: String = "images"
    private var outputName: String = ""
    private var outputType: ai.onnxruntime.OnnxJavaType = ai.onnxruntime.OnnxJavaType.FLOAT
    private var inputW: Int = 640
    private var inputH: Int = 640
    private var inputChannels: Int = 3
    private var inputNchw: Boolean = true
    private var inputType: ai.onnxruntime.OnnxJavaType = ai.onnxruntime.OnnxJavaType.FLOAT

    @Volatile
    var isInitialized: Boolean = false
        private set
    @Volatile
    var lastStatus: String = "ONNX 未初始化"
        private set
    @Volatile
    var nnapiEnabled: Boolean = true
        private set
    @Volatile
    var activeExecutionProvider: String = "未加载"
        private set

    val runtimeLogs: StateFlow<List<String>> = _runtimeLogs.asStateFlow()

    fun setNnapiEnabled(enabled: Boolean) {
        if (nnapiEnabled == enabled) return
        nnapiEnabled = enabled
        appendRuntimeLog("I", "NNAPI 加速已${if (enabled) "开启" else "关闭"}")
    }

    fun clearRuntimeLogs() {
        _runtimeLogs.value = emptyList()
        appendRuntimeLog("I", "运行日志已清空")
    }

    fun init(modelPath: String, threads: Int, enableNnapi: Boolean = nnapiEnabled): Boolean {
        return synchronized(lock) {
            nnapiEnabled = enableNnapi
            runCatching { session?.close() }
            runCatching { sessionOptions?.close() }
            runCatching { env?.close() }
            session = null
            sessionOptions = null
            env = null
            activeExecutionProvider = "未加载"
            isInitialized = false
            outputName = ""
            lastStatus = "正在加载 ONNX..."
            lastInferenceLogAtMs = 0L
            appendRuntimeLog(
                "I",
                "初始化模型 ${File(modelPath).name} threads=${threads.coerceIn(1, 8)} nnapi=${if (enableNnapi) "on" else "off"}"
            )

            val initResult = runCatching {
                val e = OrtEnvironment.getEnvironment()
                val init = createSessionWithFallback(e, modelPath, threads, enableNnapi)
                val s = init.session
                val firstInput = s.inputInfo.entries.firstOrNull()
                if (firstInput != null) {
                    inputName = firstInput.key
                    val info = firstInput.value.info as? ai.onnxruntime.TensorInfo
                    inputType = info?.type ?: ai.onnxruntime.OnnxJavaType.FLOAT
                    val shape = info?.shape
                    if (shape != null && shape.size >= 4) {
                        val dim1 = shape[1].toIntOrNullPositive()
                        val dim2 = shape[2].toIntOrNullPositive()
                        val dim3 = shape[3].toIntOrNullPositive()

                        when {
                            dim1 == 1 || dim1 == 3 -> {
                                inputNchw = true
                                inputChannels = dim1
                                if (dim2 != null && dim3 != null) {
                                    inputH = dim2
                                    inputW = dim3
                                }
                            }
                            dim3 == 1 || dim3 == 3 -> {
                                inputNchw = false
                                inputChannels = dim3
                                if (dim1 != null && dim2 != null) {
                                    inputH = dim1
                                    inputW = dim2
                                }
                            }
                            else -> {
                                inputNchw = true
                                inputChannels = 3
                                if (dim2 != null && dim3 != null) {
                                    inputH = dim2
                                    inputW = dim3
                                }
                            }
                        }
                    }
                }
                outputName = s.outputInfo.entries.firstOrNull()?.key.orEmpty()
                outputType = (s.outputInfo[outputName]?.info as? ai.onnxruntime.TensorInfo)?.type
                    ?: ai.onnxruntime.OnnxJavaType.FLOAT
                env = e
                session = s
                sessionOptions = init.options
                activeExecutionProvider = init.providerLabel
                isInitialized = true
                val outputShape = runCatching {
                    val info = s.outputInfo[outputName]?.info as? ai.onnxruntime.TensorInfo
                    info?.shape?.joinToString("x")
                }.getOrNull()
                val providerSuffix = buildString {
                    append("ep=${init.providerLabel}")
                    if (!init.providerNote.isNullOrBlank()) {
                        append(" ")
                        append(init.providerNote)
                    }
                }
                lastStatus = if (outputShape.isNullOrBlank()) {
                    "ONNX 已加载: $providerSuffix in=${inputW}x$inputH c=$inputChannels ${if (inputNchw) "NCHW" else "NHWC"} ${inputType.name.lowercase(Locale.ROOT)} out=$outputName:${outputType.name.lowercase(Locale.ROOT)}"
                } else {
                    "ONNX 已加载: $providerSuffix in=${inputW}x$inputH c=$inputChannels ${if (inputNchw) "NCHW" else "NHWC"} ${inputType.name.lowercase(Locale.ROOT)} out=$outputName:${outputType.name.lowercase(Locale.ROOT)}($outputShape)"
                }
                appendRuntimeLog("I", lastStatus)
            }
            if (initResult.isFailure) {
                val detail = initResult.exceptionOrNull()?.message?.take(120)?.ifBlank { null } ?: "未知错误"
                lastStatus = "ONNX 初始化失败: $detail"
                appendRuntimeLog("E", lastStatus)
            }
            initResult.isSuccess
        }
    }

    private fun createSessionWithFallback(
        environment: OrtEnvironment,
        modelPath: String,
        threads: Int,
        enableNnapi: Boolean
    ): SessionInitResult {
        if (!enableNnapi) {
            appendRuntimeLog("I", "NNAPI 开关关闭，使用 CPU 会话")
            return createCpuSession(environment, modelPath, threads, "manual cpu-only")
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            appendRuntimeLog(
                "W",
                "设备 Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT} 不支持 NNAPI，回退 CPU"
            )
            return createCpuSession(environment, modelPath, threads, "Android 8.1 以下不支持 NNAPI")
        }

        var acceleratorOnlyError: Throwable? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appendRuntimeLog("I", "尝试 NNAPI accelerator-only 会话")
            val acceleratorOnly = runCatching {
                createSession(environment, modelPath, threads) { options ->
                    options.addNnapi(EnumSet.of(NNAPIFlags.CPU_DISABLED))
                }
            }
            if (acceleratorOnly.isSuccess) {
                return acceleratorOnly.getOrThrow().copy(
                    providerLabel = "NNAPI",
                    providerNote = "accelerator-only"
                )
            }
            acceleratorOnlyError = acceleratorOnly.exceptionOrNull()
            appendRuntimeLog(
                "W",
                "NNAPI accelerator-only 初始化失败: ${formatProviderError(acceleratorOnlyError)}"
            )
        }

        appendRuntimeLog("I", "尝试标准 NNAPI 会话")
        val nnapiAuto = runCatching {
            createSession(environment, modelPath, threads) { options ->
                options.addNnapi()
            }
        }
        if (nnapiAuto.isSuccess) {
            val note = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "auto-fallback-enabled"
            } else {
                "android-api=${Build.VERSION.SDK_INT}"
            }
            return nnapiAuto.getOrThrow().copy(
                providerLabel = "NNAPI",
                providerNote = note
            )
        }

        appendRuntimeLog(
            "W",
            "标准 NNAPI 初始化失败，回退 CPU: ${formatProviderError(nnapiAuto.exceptionOrNull())}"
        )
        val detail = buildString {
            val acceleratorError = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "accelerator-only=${formatProviderError(acceleratorOnlyError)}"
            } else {
                null
            }
            val autoError = "nnapi=${formatProviderError(nnapiAuto.exceptionOrNull())}"
            val parts = listOfNotNull(acceleratorError, autoError)
            append(parts.joinToString(", "))
        }.ifBlank { "NNAPI 不可用" }

        return createCpuSession(environment, modelPath, threads, "fallback $detail")
    }

    private fun createSession(
        environment: OrtEnvironment,
        modelPath: String,
        threads: Int,
        configureProvider: (OrtSession.SessionOptions) -> Unit
    ): SessionInitResult {
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads.coerceIn(1, 8))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        return try {
            configureProvider(options)
            SessionInitResult(
                session = environment.createSession(modelPath, options),
                options = options,
                providerLabel = "CPU"
            )
        } catch (t: Throwable) {
            runCatching { options.close() }
            throw t
        }
    }

    private fun createCpuSession(
        environment: OrtEnvironment,
        modelPath: String,
        threads: Int,
        note: String? = null
    ): SessionInitResult {
        return createSession(environment, modelPath, threads) { _ -> }
            .copy(providerLabel = "CPU", providerNote = note)
    }

    private fun formatProviderError(error: Throwable?): String {
        return error?.message?.take(80)?.ifBlank { null } ?: "unknown"
    }

    fun detect(bitmap: Bitmap, confThreshold: Float, nmsThreshold: Float): List<DetectionBox> {
        val localSession = synchronized(lock) { session } ?: return emptyList()
        val localEnv = synchronized(lock) { env } ?: return emptyList()
        val dstW = inputW.coerceAtLeast(1)
        val dstH = inputH.coerceAtLeast(1)

        val scaled = Bitmap.createScaledBitmap(bitmap, dstW, dstH, true)
        return try {
            val area = dstW * dstH
            val pixels = IntArray(area)
            scaled.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH)
            val lumaStats = sampleLumaStats(pixels, dstW, dstH)
            val tensor = createInputTensor(localEnv, pixels, dstW, dstH)

            var result: OrtSession.Result? = null
            try {
                result = localSession.run(mapOf(inputName to tensor))
                val outputValue = result.get(0).value
                val modelBoxes = parseOutput(outputValue, confThreshold)
                val scaledBoxes = scaleAndClipBoxes(
                    boxes = modelBoxes,
                    bitmapW = bitmap.width,
                    bitmapH = bitmap.height,
                    inputW = dstW,
                    inputH = dstH
                )
                val filtered = filterCandidates(
                    boxes = scaledBoxes,
                    bitmapW = bitmap.width,
                    bitmapH = bitmap.height,
                    confThreshold = confThreshold
                )
                val quantizedOutput = outputType == ai.onnxruntime.OnnxJavaType.UINT8 || outputType == ai.onnxruntime.OnnxJavaType.INT8
                val nmsed = applyNms(
                    boxes = filtered,
                    nmsThreshold = nmsThreshold.coerceIn(0f, 1f),
                    classAgnostic = quantizedOutput
                )
                val finalBoxes = capDetections(nmsed, limit = 120)
                val suppressed = suppressLowTextureFalsePositives(
                    boxes = finalBoxes,
                    luma = lumaStats,
                    bitmapW = bitmap.width,
                    bitmapH = bitmap.height
                )
                lastStatus = "ONNX 推理完成: raw=${scaledBoxes.size} filtered=${filtered.size} out=${suppressed.size}"
                val now = System.currentTimeMillis()
                if (now - lastInferenceLogAtMs >= ONNX_INFERENCE_LOG_INTERVAL_MS) {
                    lastInferenceLogAtMs = now
                    appendRuntimeLog("D", "${lastStatus} ep=$activeExecutionProvider")
                }
                suppressed
            } finally {
                runCatching { result?.close() }
                runCatching { tensor.close() }
            }
        } catch (t: Throwable) {
            val detail = t.message?.take(120)?.ifBlank { null } ?: t.javaClass.simpleName
            lastStatus = "ONNX 推理失败: $detail"
            appendRuntimeLog("E", lastStatus)
            emptyList()
        } finally {
            if (scaled !== bitmap) {
                runCatching { scaled.recycle() }
            }
        }
    }

    private fun appendRuntimeLog(level: String, message: String) {
        val line = "${runtimeTimestamp()} [$level] $message"
        _runtimeLogs.value = (_runtimeLogs.value + line).takeLast(ONNX_RUNTIME_LOG_LIMIT)
        when (level) {
            "E" -> Log.e(ONNX_LOG_TAG, message)
            "W" -> Log.w(ONNX_LOG_TAG, message)
            "D" -> Log.d(ONNX_LOG_TAG, message)
            else -> Log.i(ONNX_LOG_TAG, message)
        }
    }

    private fun runtimeTimestamp(): String {
        return String.format(Locale.ROOT, "%1\$tT.%1\$tL", System.currentTimeMillis())
    }

    private fun Long.toIntOrNullPositive(): Int? {
        return if (this in 1..4096) this.toInt() else null
    }

    private fun createInputTensor(
        localEnv: OrtEnvironment,
        pixels: IntArray,
        width: Int,
        height: Int
    ): OnnxTensor {
        val channels = inputChannels.coerceIn(1, 3)
        val shape = if (inputNchw) {
            longArrayOf(1, channels.toLong(), height.toLong(), width.toLong())
        } else {
            longArrayOf(1, height.toLong(), width.toLong(), channels.toLong())
        }
        return when (inputType) {
            ai.onnxruntime.OnnxJavaType.UINT8,
            ai.onnxruntime.OnnxJavaType.INT8 -> {
                val bytes = ByteBuffer.allocateDirect(width * height * channels)
                bytes.order(ByteOrder.nativeOrder())
                fillByteInput(bytes, pixels, width, height, channels, inputNchw, inputType == ai.onnxruntime.OnnxJavaType.INT8)
                bytes.rewind()
                OnnxTensor.createTensor(localEnv, bytes, shape, inputType)
            }
            else -> {
                val chw = FloatArray(width * height * channels)
                fillFloatInput(chw, pixels, width, height, channels, inputNchw)
                OnnxTensor.createTensor(localEnv, FloatBuffer.wrap(chw), shape)
            }
        }
    }

    private fun fillFloatInput(
        out: FloatArray,
        pixels: IntArray,
        width: Int,
        height: Int,
        channels: Int,
        nchw: Boolean
    ) {
        val area = width * height
        for (i in 0 until area) {
            val c = pixels[i]
            val r = ((c shr 16) and 0xFF) / 255.0f
            val g = ((c shr 8) and 0xFF) / 255.0f
            val b = (c and 0xFF) / 255.0f
            if (nchw) {
                when (channels) {
                    1 -> out[i] = 0.299f * r + 0.587f * g + 0.114f * b
                    else -> {
                        out[i] = r
                        out[area + i] = g
                        out[area * 2 + i] = b
                    }
                }
            } else {
                val base = i * channels
                when (channels) {
                    1 -> out[base] = 0.299f * r + 0.587f * g + 0.114f * b
                    else -> {
                        out[base] = r
                        out[base + 1] = g
                        out[base + 2] = b
                    }
                }
            }
        }
    }

    private fun fillByteInput(
        out: ByteBuffer,
        pixels: IntArray,
        width: Int,
        height: Int,
        channels: Int,
        nchw: Boolean,
        signedInt8: Boolean
    ) {
        val area = width * height
        fun pack(value: Int): Byte {
            val v = value.coerceIn(0, 255)
            return if (signedInt8) (v - 128).toByte() else v.toByte()
        }

        if (nchw) {
            if (channels == 1) {
                for (i in 0 until area) {
                    val c = pixels[i]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt()
                    out.put(i, pack(gray))
                }
            } else {
                for (i in 0 until area) {
                    val c = pixels[i]
                    out.put(i, pack((c shr 16) and 0xFF))
                    out.put(area + i, pack((c shr 8) and 0xFF))
                    out.put(area * 2 + i, pack(c and 0xFF))
                }
            }
        } else {
            for (i in 0 until area) {
                val c = pixels[i]
                val base = i * channels
                if (channels == 1) {
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt()
                    out.put(base, pack(gray))
                } else {
                    out.put(base, pack((c shr 16) and 0xFF))
                    out.put(base + 1, pack((c shr 8) and 0xFF))
                    out.put(base + 2, pack(c and 0xFF))
                }
            }
        }
    }

    private fun parseOutput(raw: Any?, confThreshold: Float): List<DetectionBox> {
        val matrix = extract2dMatrix(raw) ?: return emptyList()
        if (matrix.isEmpty()) return emptyList()
        val threshold = confThreshold.coerceIn(0f, 1f)

        parseNmsLikeOutput(matrix, threshold)?.let { return it }
        return parseYoloLikeOutput(matrix, threshold)
    }

    private fun extract2dMatrix(raw: Any?): Array<FloatArray>? {
        return when (raw) {
            is Array<*> -> {
                if (raw.isEmpty()) return null
                when (val first = raw[0]) {
                    is Array<*> -> {
                        val level2 = first
                        if (level2.isEmpty()) return null
                        val rows = level2.mapNotNull { toFloatRow(it) }
                        if (rows.isEmpty()) null else rows.toTypedArray()
                    }
                    else -> {
                        val rows = raw.mapNotNull { toFloatRow(it) }
                        if (rows.isEmpty()) null else rows.toTypedArray()
                    }
                }
            }
            else -> null
        }
    }

    private fun toFloatRow(value: Any?): FloatArray? {
        return when (value) {
            is FloatArray -> value
            is DoubleArray -> FloatArray(value.size) { i -> value[i].toFloat() }
            is IntArray -> FloatArray(value.size) { i -> value[i].toFloat() }
            is LongArray -> FloatArray(value.size) { i -> value[i].toFloat() }
            is ShortArray -> FloatArray(value.size) { i -> value[i].toFloat() }
            is ByteArray -> {
                if (outputType == ai.onnxruntime.OnnxJavaType.INT8) {
                    FloatArray(value.size) { i -> value[i].toFloat() }
                } else {
                    FloatArray(value.size) { i -> (value[i].toInt() and 0xFF).toFloat() }
                }
            }
            is Array<*> -> {
                if (value.isEmpty()) return null
                val out = FloatArray(value.size)
                for (i in value.indices) {
                    val n = value[i] as? Number ?: return null
                    out[i] = n.toFloat()
                }
                out
            }
            else -> null
        }
    }

    private fun parseNmsLikeOutput(matrix: Array<FloatArray>, threshold: Float): List<DetectionBox>? {
        val cols = matrix.firstOrNull()?.size ?: return null
        if (cols < 6 || cols > 8) return null
        if (matrix.size > 2000) return null

        val candidateA = mutableListOf<DetectionBox>()
        matrix.forEach { row ->
            if (row.size < 6) return@forEach
            val score = decodeProb(row[4])
            if (score < threshold) return@forEach
            val cls = row[5].roundToInt().coerceAtLeast(0)
            candidateA += if (row[2] > row[0] && row[3] > row[1]) {
                DetectionBox(
                    x = row[0],
                    y = row[1],
                    w = (row[2] - row[0]).coerceAtLeast(0f),
                    h = (row[3] - row[1]).coerceAtLeast(0f),
                    label = cls,
                    score = score
                )
            } else {
                DetectionBox(
                    x = row[0] - row[2] * 0.5f,
                    y = row[1] - row[3] * 0.5f,
                    w = row[2].coerceAtLeast(0f),
                    h = row[3].coerceAtLeast(0f),
                    label = cls,
                    score = score
                )
            }
        }

        val candidateB = mutableListOf<DetectionBox>()
        if (cols >= 7) {
            matrix.forEach { row ->
                if (row.size < 7) return@forEach
                val score = decodeProb(row[2])
                if (score < threshold) return@forEach
                val cls = row[1].roundToInt().coerceAtLeast(0)
                val x1 = row[3]
                val y1 = row[4]
                val x2 = row[5]
                val y2 = row[6]
                if (x2 <= x1 || y2 <= y1) return@forEach
                candidateB += DetectionBox(
                    x = x1,
                    y = y1,
                    w = x2 - x1,
                    h = y2 - y1,
                    label = cls,
                    score = score
                )
            }
        }

        return if (candidateB.size > candidateA.size) candidateB else candidateA
    }

    private fun parseYoloLikeOutput(matrix: Array<FloatArray>, threshold: Float): List<DetectionBox> {
        val rows = matrix.size
        val cols = matrix.firstOrNull()?.size ?: return emptyList()
        if (rows < 5 || cols < 5) return emptyList()
        val channelFirst = rows < cols
        return if (channelFirst) {
            val noObj = parseChannelFirstYolo(matrix, threshold, hasObjectness = false)
            val withObj = parseChannelFirstYolo(matrix, threshold, hasObjectness = true)
            pickBest(noObj, withObj)
        } else {
            val noObj = parseRowFirstYolo(matrix, threshold, hasObjectness = false)
            val withObj = parseRowFirstYolo(matrix, threshold, hasObjectness = true)
            pickBest(noObj, withObj)
        }
    }

    private fun parseChannelFirstYolo(
        matrix: Array<FloatArray>,
        threshold: Float,
        hasObjectness: Boolean
    ): List<DetectionBox> {
        val channels = matrix.size
        val anchors = matrix[0].size
        val classStart = if (hasObjectness) 5 else 4
        val classes = channels - classStart
        if (classes <= 0) return emptyList()
        if (hasObjectness && channels < 6) return emptyList()

        val out = ArrayList<DetectionBox>(anchors.coerceAtMost(1024))
        for (i in 0 until anchors) {
            val cx = matrix[0][i]
            val cy = matrix[1][i]
            val w = matrix[2][i]
            val h = matrix[3][i]
            if (w <= 0f || h <= 0f) continue
            val obj = if (hasObjectness) decodeProb(matrix[4][i]) else 1f

            var bestScore = 0f
            var bestClass = -1
            for (c in 0 until classes) {
                val clsScore = decodeProb(matrix[classStart + c][i])
                val conf = if (hasObjectness) obj * clsScore else clsScore
                if (conf > bestScore) {
                    bestScore = conf
                    bestClass = c
                }
            }
            if (bestClass >= 0 && bestScore >= threshold) {
                out += DetectionBox(
                    x = cx - w * 0.5f,
                    y = cy - h * 0.5f,
                    w = w,
                    h = h,
                    label = bestClass,
                    score = bestScore
                )
            }
        }
        return out
    }

    private fun parseRowFirstYolo(
        matrix: Array<FloatArray>,
        threshold: Float,
        hasObjectness: Boolean
    ): List<DetectionBox> {
        val channels = matrix[0].size
        val classStart = if (hasObjectness) 5 else 4
        val classes = channels - classStart
        if (classes <= 0) return emptyList()
        if (hasObjectness && channels < 6) return emptyList()

        val out = ArrayList<DetectionBox>(matrix.size.coerceAtMost(1024))
        matrix.forEach { row ->
            if (row.size < classStart + 1) return@forEach
            val cx = row[0]
            val cy = row[1]
            val w = row[2]
            val h = row[3]
            if (w <= 0f || h <= 0f) return@forEach
            val obj = if (hasObjectness) decodeProb(row[4]) else 1f

            var bestScore = 0f
            var bestClass = -1
            for (c in 0 until classes) {
                val clsScore = decodeProb(row[classStart + c])
                val conf = if (hasObjectness) obj * clsScore else clsScore
                if (conf > bestScore) {
                    bestScore = conf
                    bestClass = c
                }
            }
            if (bestClass >= 0 && bestScore >= threshold) {
                out += DetectionBox(
                    x = cx - w * 0.5f,
                    y = cy - h * 0.5f,
                    w = w,
                    h = h,
                    label = bestClass,
                    score = bestScore
                )
            }
        }
        return out
    }

    private fun pickBest(a: List<DetectionBox>, b: List<DetectionBox>): List<DetectionBox> {
        if (a.isNotEmpty() && b.isNotEmpty()) {
            val aLarge = a.size > 450
            val bLarge = b.size > 450
            if (aLarge xor bLarge) {
                return if (aLarge) b else a
            }
            val aPreferred = a.size in 1..320
            val bPreferred = b.size in 1..320
            if (aPreferred xor bPreferred) {
                return if (aPreferred) a else b
            }
        }
        return when {
            a.isEmpty() -> b
            b.isEmpty() -> a
            b.size > a.size -> b
            a.size > b.size -> a
            else -> {
                val topA = a.maxOf { it.score }
                val topB = b.maxOf { it.score }
                if (topB > topA) b else a
            }
        }
    }

    private fun decodeProb(v: Float): Float {
        if (v in 0f..1f) return v
        return when (outputType) {
            ai.onnxruntime.OnnxJavaType.UINT8 -> (v / 255f).coerceIn(0f, 1f)
            ai.onnxruntime.OnnxJavaType.INT8 -> ((v + 128f) / 255f).coerceIn(0f, 1f)
            else -> {
                val clamped = v.coerceIn(-20f, 20f)
                (1.0 / (1.0 + exp((-clamped).toDouble()))).toFloat()
            }
        }
    }

    private fun scaleAndClipBoxes(
        boxes: List<DetectionBox>,
        bitmapW: Int,
        bitmapH: Int,
        inputW: Int,
        inputH: Int
    ): List<DetectionBox> {
        if (boxes.isEmpty()) return emptyList()
        val maxCoord = boxes.maxOf { box ->
            maxOf(maxOf(box.x + box.w, box.y + box.h), maxOf(box.w, box.h))
        }
        val normalized = maxCoord <= 2.5f
        val sx = if (normalized) bitmapW.toFloat() else bitmapW / inputW.toFloat()
        val sy = if (normalized) bitmapH.toFloat() else bitmapH / inputH.toFloat()
        val maxX = bitmapW.toFloat()
        val maxY = bitmapH.toFloat()

        val out = ArrayList<DetectionBox>(boxes.size)
        boxes.forEach { box ->
            val x = box.x * sx
            val y = box.y * sy
            val w = box.w * sx
            val h = box.h * sy
            val left = x.coerceIn(0f, maxX)
            val top = y.coerceIn(0f, maxY)
            val right = (x + w).coerceIn(0f, maxX)
            val bottom = (y + h).coerceIn(0f, maxY)
            val clippedW = (right - left).coerceAtLeast(0f)
            val clippedH = (bottom - top).coerceAtLeast(0f)
            if (clippedW < 1f || clippedH < 1f) return@forEach
            out += DetectionBox(
                x = left,
                y = top,
                w = clippedW,
                h = clippedH,
                label = box.label,
                score = box.score
            )
        }
        return out
    }

    private fun filterCandidates(
        boxes: List<DetectionBox>,
        bitmapW: Int,
        bitmapH: Int,
        confThreshold: Float
    ): List<DetectionBox> {
        if (boxes.isEmpty()) return emptyList()
        val frameArea = (bitmapW * bitmapH).coerceAtLeast(1).toFloat()
        val quantizedOutput = outputType == ai.onnxruntime.OnnxJavaType.UINT8 || outputType == ai.onnxruntime.OnnxJavaType.INT8
        val minConf = if (quantizedOutput) {
            confThreshold.coerceIn(0f, 1f).coerceAtLeast(0.38f)
        } else {
            confThreshold.coerceIn(0f, 1f).coerceAtLeast(0.10f)
        }
        val cleaned = boxes.asSequence()
            .filter { it.score >= minConf }
            .filter { it.w >= 3f && it.h >= 3f }
            .filter {
                val areaRatio = (it.w * it.h) / frameArea
                areaRatio in 0.00008f..0.82f
            }
            .filter {
                val ratio = maxOf(it.w / it.h, it.h / it.w)
                ratio <= 9.5f
            }
            .toList()

        if (cleaned.isEmpty()) return emptyList()
        var refined = cleaned
        if (cleaned.size > 320) {
            val boostThreshold = when {
                cleaned.size > 1200 -> 0.82f
                cleaned.size > 700 -> 0.76f
                cleaned.size > 450 -> 0.70f
                else -> 0.62f
            }
            val boosted = cleaned.filter { it.score >= maxOf(minConf, boostThreshold) }
            if (boosted.isNotEmpty()) refined = boosted
        }

        val perClassLimit = when {
            refined.size > 600 -> 6
            refined.size > 320 -> 8
            else -> 12
        }
        val classCapped = refined
            .groupBy { it.label }
            .values
            .flatMap { perClass ->
                perClass.sortedByDescending { it.score }.take(perClassLimit)
            }

        if (classCapped.size <= 320) return classCapped
        val preNmsLimit = when {
            classCapped.size > 1600 -> 220
            classCapped.size > 900 -> 260
            classCapped.size > 500 -> 300
            else -> 380
        }
        return classCapped
            .sortedByDescending { it.score }
            .take(preNmsLimit)
    }

    private fun capDetections(boxes: List<DetectionBox>, limit: Int): List<DetectionBox> {
        if (boxes.size <= limit) return boxes
        return boxes.sortedByDescending { it.score }.take(limit)
    }

    private data class LumaStats(val mean: Float, val stdDev: Float)

    private fun sampleLumaStats(pixels: IntArray, width: Int, height: Int): LumaStats {
        val area = (width * height).coerceAtLeast(1)
        val step = (area / 4096).coerceAtLeast(1)
        var count = 0
        var sum = 0.0
        var sumSq = 0.0
        var i = 0
        while (i < area) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val y = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            sum += y
            sumSq += y * y
            count++
            i += step
        }
        if (count <= 0) return LumaStats(mean = 0f, stdDev = 0f)
        val mean = (sum / count).toFloat()
        val variance = ((sumSq / count) - mean * mean).coerceAtLeast(0.0)
        return LumaStats(mean = mean, stdDev = sqrt(variance).toFloat())
    }

    private fun suppressLowTextureFalsePositives(
        boxes: List<DetectionBox>,
        luma: LumaStats,
        bitmapW: Int,
        bitmapH: Int
    ): List<DetectionBox> {
        if (boxes.isEmpty()) return boxes
        val veryFlat = luma.stdDev < 0.035f && (luma.mean > 0.93f || luma.mean < 0.07f)
        if (!veryFlat) return boxes

        val frameArea = (bitmapW * bitmapH).coerceAtLeast(1).toFloat()
        val strict = boxes.filter { box ->
            val areaRatio = (box.w * box.h) / frameArea
            box.score >= 0.92f && areaRatio >= 0.01f
        }
        return strict
    }

    private fun applyNms(
        boxes: List<DetectionBox>,
        nmsThreshold: Float,
        classAgnostic: Boolean = false
    ): List<DetectionBox> {
        if (boxes.isEmpty()) return emptyList()
        val sorted = boxes.sortedByDescending { it.score }.toMutableList()
        val kept = mutableListOf<DetectionBox>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept += best
            val iter = sorted.iterator()
            while (iter.hasNext()) {
                val cur = iter.next()
                if (!classAgnostic && cur.label != best.label) continue
                val iou = calcIou(best, cur)
                if (iou >= nmsThreshold) {
                    iter.remove()
                }
            }
        }
        return kept
    }

    private fun calcIou(a: DetectionBox, b: DetectionBox): Float {
        val ax2 = a.x + a.w
        val ay2 = a.y + a.h
        val bx2 = b.x + b.w
        val by2 = b.y + b.h
        val ix1 = maxOf(a.x, b.x)
        val iy1 = maxOf(a.y, b.y)
        val ix2 = minOf(ax2, bx2)
        val iy2 = minOf(ay2, by2)
        val iw = (ix2 - ix1).coerceAtLeast(0f)
        val ih = (iy2 - iy1).coerceAtLeast(0f)
        if (iw <= 0f || ih <= 0f) return 0f
        val inter = iw * ih
        val union = a.w * a.h + b.w * b.h - inter
        return if (union <= 0f) 0f else inter / union
    }
}

private object RuntimeBridge {
    @Volatile var confidence: Float = 0.25f
    @Volatile var nms: Float = 0.45f
    @Volatile var selectedModelKind: ModelKind = ModelKind.FILE
    @Volatile var hotkeys: List<HotkeyConfig> = emptyList()

    private val moveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val autoFireScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val moveQueue = Channel<Pair<Int, Int>>(Channel.CONFLATED)
    private val sessionRef = AtomicReference<MakcuUsbSession?>(null)
    private val lastMoveErrorMs = AtomicLong(0L)
    private val lastButtonErrorMs = AtomicLong(0L)
    private val autoFireLock = Any()
    private var autoFireWanted = false
    private var autoFireConfig: RuntimeAimbotConfig? = null
    private var autoFireArmedSinceMs = 0L
    @Volatile private var autoFireStatus: String = "idle"
    @Volatile private var fireButtonPressed: Boolean = false
    private val autoFireRandom = Random(System.currentTimeMillis())

    init {
        moveScope.launch {
            while (isActive) {
                val (dx, dy) = moveQueue.receive()
                if (dx == 0 && dy == 0) continue
                runCatching {
                    val session = sessionRef.get()
                    val result = when {
                        session != null -> session.sendMoveSmart(dx, dy)
                        MakcuSerialEngine.isConnected() -> MakcuSerialEngine.sendMove(dx, dy)
                        else -> UsbSendResult(success = false, message = "未连接")
                    }
                    if (!result.success) {
                        val now = System.currentTimeMillis()
                        val last = lastMoveErrorMs.get()
                        if (now - last > 1200 && lastMoveErrorMs.compareAndSet(last, now)) {
                            MakcuLinkRuntime.updateStatus("联动写入失败: ${result.message}")
                        }
                    }
                }
            }
        }
        autoFireScope.launch {
            while (isActive) {
                val snapshot = synchronized(autoFireLock) {
                    Triple(autoFireWanted, autoFireConfig, autoFireArmedSinceMs)
                }
                val wanted = snapshot.first
                val cfg = snapshot.second
                if (!wanted || cfg == null || !cfg.autoFireEnabled) {
                    autoFireStatus = "idle"
                    synchronized(autoFireLock) {
                        autoFireArmedSinceMs = 0L
                    }
                    if (fireButtonPressed) {
                        sendFireButton(pressed = false)
                    }
                    delay(18)
                    continue
                }

                val now = System.currentTimeMillis()
                if (snapshot.third <= 0L) {
                    synchronized(autoFireLock) {
                        autoFireArmedSinceMs = now
                    }
                    autoFireStatus = "arming"
                    delay(8)
                    continue
                }

                val waitMs = cfg.initialDelayMs.toLong() - (now - snapshot.third)
                if (waitMs > 0L) {
                    autoFireStatus = "arming"
                    delay(waitMs.coerceAtMost(18L))
                    continue
                }

                autoFireStatus = "firing"
                val clickCount = autoFireRandom.nextInt(cfg.minClick, cfg.maxClick + 1)
                for (i in 0 until clickCount) {
                    val stillWanted = synchronized(autoFireLock) { autoFireWanted && autoFireConfig?.autoFireEnabled == true }
                    if (!stillWanted) break

                    val pressResult = sendFireButton(pressed = true)
                    if (pressResult.success) {
                        delay(18)
                    } else {
                        delay(30)
                    }
                    sendFireButton(pressed = false)

                    if (i < clickCount - 1) {
                        val interval = autoFireRandom.nextInt(cfg.minIntervalMs, cfg.maxIntervalMs + 1)
                        delay(interval.toLong())
                    }
                }
                autoFireStatus = "cooldown"
                delay(cfg.burstIntervalMs.toLong().coerceAtLeast(10L))
            }
        }
    }

    fun bindSession(session: MakcuUsbSession?) {
        sessionRef.set(session)
    }

    fun sendMove(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        if (sessionRef.get() == null && !MakcuSerialEngine.isConnected()) return
        moveQueue.trySend(dx to dy)
    }

    fun updateAutoFire(enabled: Boolean, cfg: RuntimeAimbotConfig?) {
        synchronized(autoFireLock) {
            val nextEnabled = enabled && cfg?.autoFireEnabled == true
            if (!nextEnabled) {
                autoFireWanted = false
                autoFireConfig = null
                autoFireArmedSinceMs = 0L
            } else {
                if (!autoFireWanted) {
                    autoFireArmedSinceMs = 0L
                }
                autoFireWanted = true
                autoFireConfig = cfg
            }
        }
        if (!enabled) autoFireStatus = "idle"
    }

    fun autoFireState(): String = autoFireStatus

    private suspend fun sendFireButton(pressed: Boolean): UsbSendResult {
        val session = sessionRef.get()
        val result = when {
            session != null -> session.sendMouseButton(0x01, pressed)
            MakcuSerialEngine.isConnected() -> MakcuSerialEngine.sendMouseButton(0x01, pressed)
            else -> UsbSendResult(success = false, message = "未连接")
        }
        if (result.success) {
            fireButtonPressed = pressed
        }
        if (!result.success && pressed) {
            val now = System.currentTimeMillis()
            val last = lastButtonErrorMs.get()
            if (now - last > 1500 && lastButtonErrorMs.compareAndSet(last, now)) {
                MakcuLinkRuntime.updateStatus("自动射击写入失败: ${result.message}")
            }
        }
        return result
    }

    fun pickAimbotConfig(pressedMask: Int): RuntimeAimbotConfig? {
        val activeHotkeys = hotkeys.asSequence()
            .filter { it.enabled && (it.autoAim || it.autoFire) }
            .toList()
        if (activeHotkeys.isEmpty()) return null
        val normalizedPressed = pressedMask and 0x1F
        val hotkey = activeHotkeys.firstOrNull { cfg ->
            val triggerMask = hotkeyTriggerMask(cfg.trigger).takeIf { it != 0 } ?: 0x02
            (normalizedPressed and triggerMask) != 0
        } ?: return null

        val yOffsets = hotkey.yOffset.ifEmpty { listOf(50) }.map { it.coerceIn(0, 100) }
        val categories = if (hotkey.enabledCategories.size == 4) {
            hotkey.enabledCategories
        } else {
            listOf(true, true, true, true)
        }
        return RuntimeAimbotConfig(
            enabled = true,
            autoAimEnabled = hotkey.autoAim,
            autoFireEnabled = hotkey.autoFire,
            sensitivity = hotkey.sensitivity.coerceIn(0.05f, 1.0f),
            yOffsetPercents = yOffsets,
            aimMode = if (hotkey.aimMode == "recent_crosshair") "recent_crosshair" else "class_priority",
            aimByCategory = hotkey.aimByCategory,
            enabledCategories = categories,
            categoryPriorityEnabled = hotkey.categoryPriorityEnabled,
            categoryOrder = normalizeCategoryOrder(hotkey.categoryOrder),
            triggerMask = hotkeyTriggerMask(hotkey.trigger).takeIf { it != 0 } ?: 0x02,
            fireRangePx = hotkey.fireRangePx.coerceIn(0f, 30f),
            initialDelayMs = hotkey.initialDelayMs.coerceIn(0, 500),
            minClick = hotkey.minClick.coerceIn(1, 10),
            maxClick = hotkey.maxClick.coerceIn(hotkey.minClick.coerceIn(1, 10), 10),
            minIntervalMs = hotkey.minIntervalMs.coerceIn(10, 500),
            maxIntervalMs = hotkey.maxIntervalMs.coerceIn(hotkey.minIntervalMs.coerceIn(10, 500), 500),
            burstIntervalMs = hotkey.burstIntervalMs.coerceIn(10, 1000)
        )
    }
}

private data class MakcuButtonsParserOptions(
    val allowBareButtonsToken: Boolean,
    val strictBinaryZeroGuard: Boolean
)

private object MakcuButtonsParser {
    private val buttonsCallRegex = Regex(
        """(?:k[mM]\.|[mM]\.)?buttons\(\s*([0-9a-fbx]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )
    private val buttonsKvRegex = Regex(
        """(?:k[mM]\.|[mM]\.)?buttons\s*[:=]\s*([0-9a-fbx]+)""",
        RegexOption.IGNORE_CASE
    )
    private val buttonAliasRegexes = listOf(
        0x01 to Regex("""(?:km\.|kM\.|m\.|M\.)?(?:left|lbutton)\s*[:=\(]\s*([01])\s*\)?""", RegexOption.IGNORE_CASE),
        0x02 to Regex("""(?:km\.|kM\.|m\.|M\.)?(?:right|rbutton)\s*[:=\(]\s*([01])\s*\)?""", RegexOption.IGNORE_CASE),
        0x04 to Regex("""(?:km\.|kM\.|m\.|M\.)?(?:middle|mbutton)\s*[:=\(]\s*([01])\s*\)?""", RegexOption.IGNORE_CASE),
        0x08 to Regex("""(?:km\.|kM\.|m\.|M\.)?(?:side1|x1|xbutton1)\s*[:=\(]\s*([01])\s*\)?""", RegexOption.IGNORE_CASE),
        0x10 to Regex("""(?:km\.|kM\.|m\.|M\.)?(?:side2|x2|xbutton2)\s*[:=\(]\s*([01])\s*\)?""", RegexOption.IGNORE_CASE)
    )
    private val binaryTokens = listOf(
        "km.buttons".toByteArray(Charsets.US_ASCII),
        "kM.buttons".toByteArray(Charsets.US_ASCII),
        "m.buttons".toByteArray(Charsets.US_ASCII),
        "M.buttons".toByteArray(Charsets.US_ASCII)
    )
    private val bareButtonsToken = "buttons".toByteArray(Charsets.US_ASCII)

    fun reset(state: MakcuButtonsParseState) {
        state.snapshotMaskCache = 0
        state.pendingBytes.reset()
    }

    fun parse(
        state: MakcuButtonsParseState,
        buffer: ByteArray,
        count: Int,
        options: MakcuButtonsParserOptions
    ): MakcuButtonsParseResult? {
        if (count > 0) {
            state.pendingBytes.write(buffer, 0, count)
        }
        val data = state.pendingBytes.toByteArray()
        if (data.isEmpty()) return null

        fun emit(mask: Int, consumed: Int, source: String): MakcuButtonsParseResult {
            trimPending(state, data, consumed)
            return MakcuButtonsParseResult(mask = mask and 0xFFFF, source = source, dataPreview = data.copyOf())
        }

        parseV2ButtonsFrame(data)?.let { return emit(it.first, it.second, "v2") }

        val text = data.toString(Charsets.ISO_8859_1)
        buttonsCallRegex.findAll(text).lastOrNull()?.let { match ->
            val mask = parseMaskToken(match.groupValues.getOrNull(1)) ?: return@let
            return emit(mask, match.range.last + 1, "text()")
        }
        buttonsKvRegex.findAll(text).lastOrNull()?.let { match ->
            val mask = parseMaskToken(match.groupValues.getOrNull(1)) ?: return@let
            return emit(mask, match.range.last + 1, "text:=")
        }

        parsePerButtonEvents(text, state)?.let { return emit(it.first, it.second, "event") }
        parseBinaryAfterButtonsToken(data, options.allowBareButtonsToken)?.let {
            return emit(it.first, it.second, "token")
        }
        parseBinaryButtonsFrameStrict(data, options.strictBinaryZeroGuard)?.let {
            return emit(it.first, it.second, "binary")
        }
        parseHidStyleButtonsReport(data)?.let { return emit(it.first, it.second, "hid") }

        if (data.size > 64) {
            trimPending(state, data, data.size - 64)
        }
        return null
    }

    private fun parsePerButtonEvents(text: String, state: MakcuButtonsParseState): Pair<Int, Int>? {
        var consumed = -1
        var matched = false
        buttonAliasRegexes.forEach { (bit, regex) ->
            regex.findAll(text).forEach { match ->
                matched = true
                val pressed = match.groupValues.getOrNull(1) == "1"
                state.snapshotMaskCache = if (pressed) {
                    state.snapshotMaskCache or bit
                } else {
                    state.snapshotMaskCache and bit.inv()
                }
                consumed = maxOf(consumed, match.range.last + 1)
            }
        }
        if (!matched || consumed <= 0) return null
        return (state.snapshotMaskCache and 0x1F) to consumed
    }

    private fun parseBinaryAfterButtonsToken(data: ByteArray, allowBareButtonsToken: Boolean): Pair<Int, Int>? {
        val tokens = if (allowBareButtonsToken) binaryTokens + listOf(bareButtonsToken) else binaryTokens
        tokens.forEach { token ->
            val tokenIndex = indexOfSubArray(data, token)
            if (tokenIndex < 0) return@forEach
            val nextIndex = tokenIndex + token.size
            if (nextIndex >= data.size) return@forEach
            val value = data[nextIndex].toInt() and 0xFF
            if (value > 0x1F) return@forEach
            return value to (nextIndex + 1)
        }
        return null
    }

    private fun parseBinaryButtonsFrameStrict(data: ByteArray, strictZeroGuard: Boolean): Pair<Int, Int>? {
        for (i in 0 until (data.size - 2)) {
            if ((data[i].toInt() and 0xFF) != 0x02) continue
            val prev = if (i > 0) (data[i - 1].toInt() and 0xFF) else -1
            val prevLooksDelimiter = (i == 0) || prev == 0x0A || prev == 0x0D || prev == 0x3E || prev < 0x20
            if (!prevLooksDelimiter) continue

            val lo = data[i + 1].toInt() and 0xFF
            val hi = data[i + 2].toInt() and 0xFF
            if (hi != 0x00 || lo !in 0..0x1F) continue

            if (strictZeroGuard && lo == 0) {
                val next = if (i + 3 < data.size) (data[i + 3].toInt() and 0xFF) else 0x0A
                val nextOk = (i + 3 >= data.size) || next == 0x02 || next == 0x0A || next == 0x0D || next == 0x3E || next < 0x20
                if (!nextOk) continue
            }
            return (lo and 0x1F) to (i + 3)
        }
        return null
    }

    private fun parseHidStyleButtonsReport(data: ByteArray): Pair<Int, Int>? {
        if (data.size < 4) return null
        val start = (data.size - 32).coerceAtLeast(0)
        for (i in (data.size - 4) downTo start) {
            val b0 = data[i].toInt() and 0xFF
            if (b0 !in 0..0x1F) continue
            val b1 = data[i + 1].toInt() and 0xFF
            val b2 = data[i + 2].toInt() and 0xFF
            val b3 = data[i + 3].toInt() and 0xFF
            val tailPrintable = (b1 in 0x20..0x7E) && (b2 in 0x20..0x7E) && (b3 in 0x20..0x7E)
            if (tailPrintable) continue
            val promptEcho = (b1 == 0x3E && b2 == 0x3E) || (b2 == 0x3E && b3 == 0x3E)
            if (promptEcho) continue
            val lineBreakEcho = (b0 == 0x0A || b0 == 0x0D || b0 == 0x09) &&
                (b1 in 0x20..0x7E || b2 in 0x20..0x7E || b3 in 0x20..0x7E)
            if (lineBreakEcho) continue
            return b0 to (i + 4)
        }
        return null
    }

    private fun parseV2ButtonsFrame(data: ByteArray): Pair<Int, Int>? {
        var index = 0
        while (index + 4 <= data.size) {
            if ((data[index].toInt() and 0xFF) != MAKCU_V2_FRAME_HEAD) {
                index++
                continue
            }
            val cmd = data[index + 1].toInt() and 0xFF
            val len = (data[index + 2].toInt() and 0xFF) or ((data[index + 3].toInt() and 0xFF) shl 8)
            if (len < 0 || len > 1024) {
                index++
                continue
            }
            val frameEnd = index + 4 + len
            if (frameEnd > data.size) return null
            if (cmd == MAKCU_V2_CMD_BUTTONS && len > 0) {
                val b0 = data[index + 4].toInt() and 0xFF
                val b1 = if (len > 1) (data[index + 5].toInt() and 0xFF) else 0
                val looksConfigAck = len >= 2 && b0 in 0..2 && b1 in 1..255
                if (!looksConfigAck) {
                    if (len == 1 && b0 <= 0x1F) {
                        return b0 to frameEnd
                    }
                    if (len >= 2) {
                        val mask16 = (b0 or (b1 shl 8)) and 0xFFFF
                        if ((b1 == 0 && b0 <= 0x1F) || (mask16 != 0 && (b1 == 0 || b0 == 0))) {
                            return mask16 to frameEnd
                        }
                    }
                }
            }
            index = frameEnd
        }
        return null
    }

    private fun trimPending(state: MakcuButtonsParseState, data: ByteArray, consumed: Int) {
        val safe = consumed.coerceIn(0, data.size)
        state.pendingBytes.reset()
        if (safe < data.size) {
            state.pendingBytes.write(data, safe, data.size - safe)
        }
    }
}

private object MakcuSerialEngine {
    private data class ButtonProbe(
        val aliases: List<String>,
        val bit: Int
    )

    private val writeLock = Mutex()
    private val parseLock = Any()
    private val parserState = MakcuButtonsParseState()
    private var serialPort: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null
    private val rawClaimedInterfaces = mutableListOf<UsbInterface>()
    private val rawOutEndpoints = mutableListOf<UsbEndpoint>()
    private val rawInEndpoints = mutableListOf<UsbEndpoint>()
    @Volatile private var lastMoveSendMs: Long = 0L
    private var snapshotCursor: Int = 0
    @Volatile private var debugLastHex: String = "--"
    @Volatile private var debugLastParser: String = "init"
    @Volatile private var debugLastMask: Int = -1
    @Volatile private var debugLastCount: Int = 0

    // 标准鼠标按键掩码 (Windows通用标准)
    private val buttonProbes = listOf(
        ButtonProbe(listOf("left", "lbutton"), 0x01),
        ButtonProbe(listOf("right", "rbutton"), 0x02),
        ButtonProbe(listOf("middle", "mbutton"), 0x04),
        ButtonProbe(listOf("side1", "x1", "xbutton1"), 0x08),
        ButtonProbe(listOf("side2", "x2", "xbutton2"), 0x10)
    )
    private val buttonAliasRegexes: List<Pair<Int, Regex>> = buttonProbes.flatMap { probe ->
        probe.aliases.map { alias ->
            probe.bit to Regex(
                """(?:km\.|kM\.|m\.|M\.)?$alias\s*[:=\(]\s*([01])\s*\)?""",
                RegexOption.IGNORE_CASE
            )
        }
    }
    private val snapshotCommands = listOf(
        ".buttons()",
        "km.buttons()",
        "buttons()",
        ".buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)",
        "km.buttons(0)",
        "buttons(0)",
        "km.buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)",
        "buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)",
        "kM.buttons()",
        "m.buttons()",
        "M.buttons()"
    )

    @Volatile private var deviceId: Int = -1
    @Volatile var lastStatus: String = "未连接"
        private set

    fun isConnected(): Boolean = serialPort != null || (usbConnection != null && rawOutEndpoints.isNotEmpty())
    fun connectedDeviceId(): Int = deviceId
    fun canReadButtons(): Boolean = serialPort != null || rawInEndpoints.isNotEmpty()
    private fun isRawMode(): Boolean = serialPort == null && usbConnection != null && rawOutEndpoints.isNotEmpty()

    fun debugSummary(): String {
        val maskText = if (debugLastMask >= 0) {
            "0x${(debugLastMask and 0xFFFF).toString(16).uppercase(Locale.US)}"
        } else {
            "--"
        }
        return "SER c=$debugLastCount src=$debugLastParser m=$maskText rx=$debugLastHex"
    }

    fun hasRecentMove(withinMs: Long = 260): Boolean {
        val last = lastMoveSendMs
        if (last <= 0L) return false
        return System.currentTimeMillis() - last < withinMs
    }

    private fun updateDebugRx(buffer: ByteArray, count: Int) {
        debugLastCount = count.coerceAtLeast(0)
        if (count > 0) {
            debugLastHex = hexPreview(buffer, count)
        }
    }

    private fun updateDebugParse(source: String, mask: Int?, data: ByteArray) {
        debugLastParser = source
        debugLastMask = mask ?: -1
        debugLastHex = hexPreview(data, data.size)
    }

    private fun hexPreview(buffer: ByteArray, count: Int, maxBytes: Int = 24): String {
        if (count <= 0) return "--"
        val end = count.coerceAtMost(buffer.size)
        val start = (end - maxBytes).coerceAtLeast(0)
        val sb = StringBuilder()
        for (i in start until end) {
            if (sb.isNotEmpty()) sb.append(' ')
            val v = buffer[i].toInt() and 0xFF
            if (v < 0x10) sb.append('0')
            sb.append(v.toString(16).uppercase(Locale.US))
        }
        return sb.toString()
    }

    suspend fun connect(usbManager: UsbManager, device: UsbDevice): Boolean {
        disconnect()
        if (!usbManager.hasPermission(device)) {
            lastStatus = "未获得 USB 权限"
            return false
        }

        val customTable = ProbeTable().apply {
            addProduct(MAKCU_VENDOR_ID, MAKCU_PID_CH343, Ch34xSerialDriver::class.java)
            addProduct(MAKCU_VENDOR_ID, 0x7523, Ch34xSerialDriver::class.java)
        }
        val cdcTable = ProbeTable().apply {
            addProduct(MAKCU_VENDOR_ID, MAKCU_PID_CH343, CdcAcmSerialDriver::class.java)
            addProduct(MAKCU_VENDOR_ID, 0x7523, CdcAcmSerialDriver::class.java)
        }
        val defaultProber = UsbSerialProber.getDefaultProber()
        val customProber = UsbSerialProber(customTable)
        val cdcProber = UsbSerialProber(cdcTable)

        val driverCandidates = mutableListOf<Pair<String, UsbSerialDriver>>()
        val dedupe = mutableSetOf<String>()

        fun addDriverCandidate(tag: String, driver: UsbSerialDriver?) {
            if (driver == null) return
            val key = "${driver.javaClass.name}:${driver.device.deviceId}"
            if (dedupe.add(key)) driverCandidates += tag to driver
        }

        addDriverCandidate("default", runCatching { defaultProber.probeDevice(device) }.getOrNull())
        addDriverCandidate("custom-cdc", runCatching { cdcProber.probeDevice(device) }.getOrNull())
        addDriverCandidate("custom-ch34x", runCatching { customProber.probeDevice(device) }.getOrNull())

        if (driverCandidates.isEmpty()) {
            lastStatus = "未识别到串口驱动"
            return false
        }

        var lastError = "未知错误"
        for ((tag, driver) in driverCandidates) {
            val port = driver.ports.firstOrNull() ?: continue
            val connection = usbManager.openDevice(device) ?: continue
            try {
                port.open(connection)
                port.setParameters(MAKCU_FALLBACK_BAUD, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                runCatching { port.setDTR(true) }
                runCatching { port.setRTS(true) }

                serialPort = port
                usbConnection = connection
                rawClaimedInterfaces.clear()
                rawOutEndpoints.clear()
                rawInEndpoints.clear()
                deviceId = device.deviceId
                snapshotCursor = 0
                MakcuButtonsParser.reset(parserState)
                debugLastHex = "--"
                debugLastParser = "connected"
                debugLastMask = -1
                debugLastCount = 0

                // 强制解锁并启动按键流，优先恢复命令发送可用性。
                val txResults = mutableListOf<UsbSendResult>()
                txResults += sendCommandExact("km.bypass(0)")
                txResults += sendCommandExact("bypass(0)")
                txResults += sendCommandExact("release()")
                primeButtonsStream()
                txResults += sendCommandExact("km.buttons()")
                txResults += sendCommandExact("buttons()")
                val txOk = txResults.any { it.success }
                lastStatus = if (txOk) {
                    "串口连接成功 (baud=$MAKCU_FALLBACK_BAUD,$tag)"
                } else {
                    "串口连接成功但写入异常 (baud=$MAKCU_FALLBACK_BAUD,$tag)"
                }
                return true
            } catch (t: Throwable) {
                runCatching { port.close() }
                runCatching { connection.close() }
                val detail = t.message?.take(120)?.ifBlank { null } ?: t.javaClass.simpleName
                lastError = "$tag: $detail"
            }
        }

        serialPort = null
        usbConnection = null
        deviceId = -1
        lastStatus = "串口连接异常: $lastError"
        return false
    }

    fun disconnect() {
        runCatching { serialPort?.write("km.bypass(1)\r\n".toByteArray(Charsets.US_ASCII), 100) }
        runCatching { serialPort?.close() }
        runCatching { usbConnection?.close() }
        serialPort = null
        usbConnection = null
        rawClaimedInterfaces.clear()
        rawOutEndpoints.clear()
        rawInEndpoints.clear()
        deviceId = -1
        snapshotCursor = 0
        MakcuButtonsParser.reset(parserState)
        debugLastHex = "--"
        debugLastParser = "disconnected"
        debugLastMask = -1
        debugLastCount = 0
        lastStatus = "已断开"
    }

    suspend fun sendMove(dx: Int, dy: Int): UsbSendResult {
        if (dx == 0 && dy == 0) return UsbSendResult(true, "noop")
        if (serialPort == null) return UsbSendResult(false, "串口未连接")
        lastMoveSendMs = System.currentTimeMillis()
        var res = sendCommandExact("km.move($dx,$dy)")
        if (!res.success) res = sendCommandExact("move($dx,$dy)")
        return res
    }

    suspend fun sendCommandExact(cmd: String): UsbSendResult {
        val normalized = cmd.trim()
        if (normalized.isBlank()) return UsbSendResult(false, "命令为空")
        val payload = "$normalized\r\n".toByteArray(Charsets.US_ASCII)
        return writeLock.withLock {
            val port = serialPort ?: return@withLock UsbSendResult(false, "串口未连接")
            try {
                port.write(payload, 200)
                UsbSendResult(true, "$normalized tx")
            } catch (_: Exception) {
                UsbSendResult(false, "写入异常")
            }
        }
    }

    private suspend fun sendCommandCompatible(rawCommand: String): UsbSendResult {
        val commands = buildMakcuCommandVariants(rawCommand).take(6)
        if (commands.isEmpty()) return UsbSendResult(false, "命令为空")
        commands.forEach { candidate ->
            val result = sendCommandExact(candidate)
            if (result.success) return result
        }
        return UsbSendResult(false, "兼容写入失败")
    }

    suspend fun sendMouseButton(buttonMask: Int, pressed: Boolean): UsbSendResult {
        val aliases = when (buttonMask and 0x1F) {
            0x01 -> listOf("left", "lbutton")
            0x02 -> listOf("right", "rbutton")
            0x04 -> listOf("middle", "mbutton")
            0x08 -> listOf("side1", "x1", "xbutton1")
            0x10 -> listOf("side2", "x2", "xbutton2")
            else -> emptyList()
        }
        if (aliases.isEmpty()) return UsbSendResult(false, "不支持的按键")
        lastMoveSendMs = System.currentTimeMillis()
        val value = if (pressed) 1 else 0
        aliases.forEach { alias ->
            val result = sendCommandCompatible("$alias($value)")
            if (result.success) return result
        }
        return UsbSendResult(false, "按键写入失败")
    }

    suspend fun primeButtonsStream() {
        // Keep prime light-weight to avoid command flood blocking control traffic.
        sendCommandExact(".buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
        sendCommandExact("km.buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
        sendCommandExact("buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
        sendCommandExact(".buttons()")
    }

    // ================= 核心修复：更严谨的按键读取与解析 =================
    suspend fun readButtonsMask(timeoutMs: Int): Int? {
        val port = serialPort ?: return null
        val buf = ByteArray(256)

        return withContext(Dispatchers.IO) {
            val count = runCatching { port.read(buf, timeoutMs.coerceAtLeast(16)) }.getOrDefault(0)
            if (count > 0) {
                synchronized(parseLock) {
                    updateDebugRx(buf, count)
                }
            } else {
                synchronized(parseLock) {
                    debugLastCount = 0
                    if (debugLastParser == "connected" || debugLastParser == "none") {
                        debugLastParser = "timeout"
                    }
                }
            }
            parseButtonsMask(buf, count)
        }
    }

    private fun parseButtonsMask(buffer: ByteArray, count: Int): Int? {
        synchronized(parseLock) {
            val result = MakcuButtonsParser.parse(
                state = parserState,
                buffer = buffer,
                count = count,
                options = MakcuButtonsParserOptions(
                    allowBareButtonsToken = true,
                    strictBinaryZeroGuard = true
                )
            )
            if (result != null) {
                updateDebugParse(result.source, result.mask, result.dataPreview)
                return result.mask
            }
            updateDebugParse("none", null, parserState.pendingBytes.toByteArray())
            return null
        }
    }

    suspend fun queryButtonsMaskSnapshot(timeoutMs: Int = 180): Int? {
        val idx = (snapshotCursor++ and Int.MAX_VALUE) % snapshotCommands.size
        sendCommandExact(snapshotCommands[idx])
        var mask = readButtonsMask(timeoutMs)
        if (mask != null) return mask
        sendCommandExact("buttons()")
        mask = readButtonsMask((timeoutMs / 2).coerceAtLeast(20))
        return mask
    }

    suspend fun queryButtonsMaskFullSnapshot(timeoutMs: Int = 150): Int? {
        sendCommandExact(".buttons()")
        sendCommandExact("km.buttons()")
        sendCommandExact("buttons()")
        sendCommandExact(".buttons(0)")
        sendCommandExact("km.buttons(0)")
        sendCommandExact("buttons(0)")
        var mask = readButtonsMask(timeoutMs)
        if (mask != null) return mask
        sendCommandExact(".buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
        sendCommandExact("km.buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
        sendCommandExact("buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
        return readButtonsMask((timeoutMs + 40).coerceAtLeast(24))
    }
}

private data class MakcuLinkState(
    val connected: Boolean = false,
    val deviceId: Int = -1,
    val rawButtonMask: Int = 0,
    val buttonMask: Int = 0,
    val debugInfo: String = "",
    val status: String = "未连接"
)

private object MakcuLinkRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionRef = AtomicReference<MakcuUsbSession?>(null)
    private var sessionJob: Job? = null
    private val snapshotQueryLock = Mutex()
    private val _state = MutableStateFlow(MakcuLinkState())
    val state: StateFlow<MakcuLinkState> = _state.asStateFlow()

    fun session(): MakcuUsbSession? = sessionRef.get()

    fun updateStatus(status: String) {
        _state.value = _state.value.copy(status = status)
    }

    fun connect(usbManager: UsbManager, device: UsbDevice) {
        if (_state.value.deviceId == device.deviceId && _state.value.connected && sessionJob?.isActive == true) return
        disconnect(keepDeviceId = true, reason = "正在切换通道...")
        _state.value = _state.value.copy(
            connected = false,
            deviceId = device.deviceId,
            rawButtonMask = 0,
            buttonMask = 0,
            debugInfo = "",
            status = "正在连接 MAKCU..."
        )
        sessionJob = scope.launch {
            var opened: MakcuUsbSession? = null
            try {
                if (device.vendorId == MAKCU_VENDOR_ID) {
                    val serialOk = MakcuSerialEngine.connect(usbManager, device)
                    if (serialOk) {
                        sessionRef.set(null)
                        RuntimeBridge.bindSession(null)
                        _state.value = _state.value.copy(
                            connected = true,
                            rawButtonMask = 0,
                            buttonMask = 0,
                            debugInfo = MakcuSerialEngine.debugSummary(),
                            status = "MAKCU ${MakcuSerialEngine.lastStatus}${if (MakcuSerialEngine.canReadButtons()) "" else " (无输入端点，按键监控不可用)"}"
                        )
                        var idleReads = 0
                        var lastMaskUpdateMs = System.currentTimeMillis()
                        while (isActive && MakcuSerialEngine.isConnected()) {
                            val stateSnapshot = _state.value
                            val holding = ((stateSnapshot.rawButtonMask or stateSnapshot.buttonMask) and 0xFFFF) != 0
                            var mask = MakcuSerialEngine.readButtonsMask(MAKCU_USB_READ_TIMEOUT_MS)
                            if (mask == null && !MakcuSerialEngine.hasRecentMove()) {
                                if (holding) {
                                    if (idleReads % MAKCU_HOLD_POLL_INTERVAL == 0) {
                                        mask = MakcuSerialEngine.queryButtonsMaskSnapshot(18)
                                    }
                                    if (mask == null && idleReads % (MAKCU_HOLD_POLL_INTERVAL * 4) == 0) {
                                        mask = MakcuSerialEngine.queryButtonsMaskFullSnapshot(34)
                                    }
                                } else {
                                    if (idleReads % 200 == 0) {
                                        mask = MakcuSerialEngine.queryButtonsMaskSnapshot(20)
                                    }
                                    if (mask == null && idleReads % 600 == 0) {
                                        mask = MakcuSerialEngine.queryButtonsMaskFullSnapshot(48)
                                    }
                                }
                            }
                            if (mask == null) {
                                idleReads++
                                if (idleReads % 400 == 0) {
                                    MakcuSerialEngine.primeButtonsStream()
                                }
                                if (idleReads % 40 == 0) {
                                    _state.value = _state.value.copy(
                                        debugInfo = MakcuSerialEngine.debugSummary()
                                    )
                                }
                                if (
                                    holding &&
                                    HoldSafetyConfig.enableStuckHoldRecovery &&
                                    System.currentTimeMillis() - lastMaskUpdateMs >= HoldSafetyConfig.recoveryTimeoutMs
                                ) {
                                    lastMaskUpdateMs = System.currentTimeMillis()
                                    _state.value = _state.value.copy(
                                        rawButtonMask = 0,
                                        buttonMask = 0,
                                        debugInfo = MakcuSerialEngine.debugSummary(),
                                        status = "安全回退：疑似卡住长按，已清零并重启按键流"
                                    )
                                    MakcuSerialEngine.primeButtonsStream()
                                    idleReads = 0
                                }
                                delay(if (holding) 2 else 5)
                                continue
                            }
                            idleReads = 0
                            lastMaskUpdateMs = System.currentTimeMillis()
                            val rawMask = sanitizeRawMouseButtonMask(mask)
                            val mappedMask = normalizeMouseButtonMask(rawMask)
                            _state.value = _state.value.copy(
                                rawButtonMask = rawMask,
                                buttonMask = mappedMask,
                                debugInfo = MakcuSerialEngine.debugSummary()
                            )
                            delay(2)
                        }
                        return@launch
                    } else {
                        _state.value = _state.value.copy(
                            connected = false,
                            rawButtonMask = 0,
                            buttonMask = 0,
                            debugInfo = MakcuSerialEngine.debugSummary(),
                            status = "串口连接失败: ${MakcuSerialEngine.lastStatus}"
                        )
                        return@launch
                    }
                }

                val candidates = findUsbIoCandidates(device)
                opened = openMakcuUsbSession(usbManager, device)
                if (opened == null) {
                    val candidateSummary = summarizeUsbCandidates(candidates)
                    _state.value = _state.value.copy(
                        connected = false,
                        rawButtonMask = 0,
                        buttonMask = 0,
                        debugInfo = "",
                        status = "MAKCU USB通道初始化失败(out候选=${candidates.size}) $candidateSummary ${usbInterfaceDigest(device)}"
                    )
                    return@launch
                }

                sessionRef.set(opened)
                RuntimeBridge.bindSession(opened)
                _state.value = _state.value.copy(
                    connected = true,
                    rawButtonMask = 0,
                    buttonMask = 0,
                    debugInfo = opened.debugSummary(),
                    status = if (opened.isHandshakeVerified && opened.isWriteReady) {
                        "MAKCU 通道已连接 (if=${opened.dataInterfaceId}, baud=${opened.baudRate}, in=${if (opened.canReadButtons()) "yes" else "no"}) ${opened.outEndpointInfo}"
                    } else if (!opened.isWriteReady) {
                        "MAKCU 通道已连接但写入探测失败(probe=${opened.lastWriteStatus}) (if=${opened.dataInterfaceId}, baud=${opened.baudRate}, in=${if (opened.canReadButtons()) "yes" else "no"}) ${opened.outEndpointInfo}，将尝试原始发送"
                    } else if (!opened.serialConfigured) {
                        "MAKCU 通道已连接 (raw 模式, if=${opened.dataInterfaceId}, in=${if (opened.canReadButtons()) "yes" else "no"}) ${opened.outEndpointInfo}，尝试发送中"
                    } else {
                        "MAKCU 通道已连接但未确认协议响应 (if=${opened.dataInterfaceId}, baud=${opened.baudRate}, in=${if (opened.canReadButtons()) "yes" else "no"}) ${opened.outEndpointInfo}，请检查 USB1/USB3 链路"
                    }
                )

                opened.setEchoV2(enabled = false)
                val bypassResults = mutableListOf<UsbSendResult>()
                bypassResults += opened.setBypassV2(enabled = false)
                bypassResults += opened.sendCommandExact("km.bypass(0)")
                bypassResults += opened.sendCommandExact("bypass(0)")
                bypassResults += opened.sendCommandExact("kM.bypass(0)")
                bypassResults += opened.sendCommandExact("m.bypass(0)")
                bypassResults += opened.sendCommandExact("M.bypass(0)")
                bypassResults += opened.sendCommandExact("release()")
                val bypassResult = bypassResults.firstOrNull { it.success } ?: bypassResults.lastOrNull()

                val streamResults = mutableListOf<UsbSendResult>()
                streamResults += opened.startButtonsStreamV2(mode = 0, periodMs = MAKCU_BUTTON_STREAM_PERIOD_MS)
                streamResults += opened.startButtonsStreamV2(mode = 2, periodMs = MAKCU_BUTTON_STREAM_PERIOD_MS)
                streamResults += opened.startButtonsStreamV2(mode = 1, periodMs = MAKCU_BUTTON_STREAM_PERIOD_MS)
                streamResults += opened.sendCommandExact("km.buttons(0,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                streamResults += opened.sendCommandExact("km.buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                streamResults += opened.sendCommandExact("km.buttons(1,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                streamResults += opened.sendCommandExact("buttons(0,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                streamResults += opened.sendCommandExact("buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                streamResults += opened.sendCommandExact("buttons(1,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                streamResults += opened.sendCommandExact("kM.buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                streamResults += opened.sendCommandExact("kM.buttons(1,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                streamResults += opened.sendCommandExact("m.buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                streamResults += opened.sendCommandExact("m.buttons(1,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                streamResults += opened.sendCommandExact("M.buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                streamResults += opened.sendCommandExact("M.buttons(1,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                streamResults += opened.sendCommandExact("km.buttons(0)")
                streamResults += opened.sendCommandExact("buttons(0)")
                streamResults += opened.sendCommandBurst("buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                val streamResult = streamResults.firstOrNull { it.success } ?: streamResults.lastOrNull()

                if (bypassResult?.success != true) {
                    _state.value = _state.value.copy(status = "bypass 关闭失败: ${bypassResult?.message ?: "未知错误"}")
                }
                if (streamResult?.success != true) {
                    _state.value = _state.value.copy(status = "按键流启动失败: ${streamResult?.message ?: "未知错误"} if=${opened.dataInterfaceId} baud=${opened.baudRate} ${opened.outEndpointInfo}")
                }

                if (!opened.canReadButtons()) {
                    _state.value = _state.value.copy(status = "当前通道无输入端点，仅保留移动发送")
                    while (isActive) delay(1000)
                    return@launch
                }

                var idleReads = 0
                var lastMaskUpdateMs = System.currentTimeMillis()
                while (isActive) {
                    val stateSnapshot = _state.value
                    val holding = ((stateSnapshot.rawButtonMask or stateSnapshot.buttonMask) and 0xFFFF) != 0
                    var mask = opened.readButtonsMask(MAKCU_USB_READ_TIMEOUT_MS)
                    if (mask == null) {
                        if (holding && idleReads % MAKCU_HOLD_POLL_INTERVAL == 0) {
                            opened.sendCommandExact("km.buttons()")
                            opened.sendCommandExact("buttons()")
                            mask = opened.readButtonsMask(18)
                        } else if (!holding && idleReads % 180 == 0) {
                            opened.sendCommandExact("km.buttons()")
                            opened.sendCommandExact("buttons()")
                        }
                    }
                    if (mask == null) {
                        idleReads++
                        if (holding && idleReads % 300 == 0) {
                            opened.sendCommandExact("km.buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                            opened.sendCommandExact("buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                        }
                        if (idleReads % 40 == 0) {
                            _state.value = _state.value.copy(debugInfo = opened.debugSummary())
                        }
                        if (
                            holding &&
                            HoldSafetyConfig.enableStuckHoldRecovery &&
                            System.currentTimeMillis() - lastMaskUpdateMs >= HoldSafetyConfig.recoveryTimeoutMs
                        ) {
                            lastMaskUpdateMs = System.currentTimeMillis()
                            _state.value = _state.value.copy(
                                rawButtonMask = 0,
                                buttonMask = 0,
                                debugInfo = opened.debugSummary(),
                                status = "安全回退：疑似卡住长按，已清零并重启按键流"
                            )
                            opened.startButtonsStreamV2(mode = 2, periodMs = MAKCU_BUTTON_STREAM_PERIOD_MS)
                            opened.sendCommandExact("km.buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                            opened.sendCommandExact("buttons(2,$MAKCU_BUTTON_STREAM_PERIOD_MS)")
                            idleReads = 0
                        }
                        delay(if (holding) 2 else 5)
                        continue
                    }
                    idleReads = 0
                    lastMaskUpdateMs = System.currentTimeMillis()
                    val rawMask = sanitizeRawMouseButtonMask(mask)
                    val mappedMask = normalizeMouseButtonMask(rawMask)
                    _state.value = _state.value.copy(
                        rawButtonMask = rawMask,
                        buttonMask = mappedMask,
                        debugInfo = opened.debugSummary()
                    )
                    delay(2)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                val detail = t.message?.take(80)?.ifBlank { null } ?: t.javaClass.simpleName
                _state.value = _state.value.copy(
                    connected = false,
                    rawButtonMask = 0,
                    buttonMask = 0,
                    debugInfo = "",
                    status = "USB 控制异常: $detail"
                )
            } finally {
                val current = opened
                if (current != null && sessionRef.compareAndSet(current, null)) {
                    RuntimeBridge.bindSession(null)
                    current.close()
                }
                _state.value = _state.value.copy(connected = false, rawButtonMask = 0, buttonMask = 0, debugInfo = "")
            }
        }
    }

    fun requestButtonsSnapshot() {
        scope.launch {
            if (!snapshotQueryLock.tryLock()) return@launch
            try {
                var mask: Int? = null
                if (MakcuSerialEngine.isConnected()) {
                    mask = MakcuSerialEngine.readButtonsMask(18)
                    if (mask == null) {
                        mask = MakcuSerialEngine.queryButtonsMaskSnapshot(24)
                    }
                    if (mask == null) {
                        mask = MakcuSerialEngine.queryButtonsMaskFullSnapshot(42)
                    }
                } else {
                    val session = sessionRef.get()
                    if (session != null && session.canReadButtons()) {
                        mask = session.readButtonsMask(24)
                        if (mask == null) {
                            session.sendCommandExact(".buttons()")
                            session.sendCommandExact("km.buttons()")
                            session.sendCommandExact("buttons()")
                            mask = session.readButtonsMask(42)
                        }
                    }
                }
                if (mask != null) {
                    val rawMask = sanitizeRawMouseButtonMask(mask)
                    _state.value = _state.value.copy(
                        rawButtonMask = rawMask,
                        buttonMask = normalizeMouseButtonMask(rawMask),
                        debugInfo = when {
                            MakcuSerialEngine.isConnected() -> MakcuSerialEngine.debugSummary()
                            sessionRef.get() != null -> sessionRef.get()?.debugSummary().orEmpty()
                            else -> _state.value.debugInfo
                        }
                    )
                } else if (MakcuSerialEngine.isConnected()) {
                    _state.value = _state.value.copy(debugInfo = MakcuSerialEngine.debugSummary())
                } else if (sessionRef.get() != null) {
                    _state.value = _state.value.copy(debugInfo = sessionRef.get()?.debugSummary().orEmpty())
                }
            } finally {
                snapshotQueryLock.unlock()
            }
        }
    }

    fun recoverMakcuAfterCalibration() {
        scope.launch {
            if (!MakcuSerialEngine.isConnected()) return@launch
            MakcuSerialEngine.sendCommandExact("km.bypass(0)")
            MakcuSerialEngine.sendCommandExact("bypass(0)")
            MakcuSerialEngine.primeButtonsStream()
        }
    }

    fun disconnect(reason: String = "已断开", keepDeviceId: Boolean = false) {
        sessionJob?.cancel()
        sessionJob = null
        MakcuSerialEngine.disconnect()
        val old = sessionRef.getAndSet(null)
        RuntimeBridge.bindSession(null)
        old?.close()
        _state.value = if (keepDeviceId) {
            _state.value.copy(connected = false, rawButtonMask = 0, buttonMask = 0, debugInfo = "", status = reason)
        } else {
            MakcuLinkState(status = reason)
        }
    }
}

private object AimPdConfig {
    @Volatile var xKp: Float = 0.2f
    @Volatile var xKd: Float = 0.01f
    @Volatile var yKp: Float = 0.1f
    @Volatile var yKd: Float = 0.01f
    @Volatile var xSmooth: Float = 0.5f
    @Volatile var ySmooth: Float = 0.2f
    @Volatile var xDeadzone: Int = 0
    @Volatile var yDeadzone: Int = 0
    @Volatile var xMaxOut: Int = 200
    @Volatile var yMaxOut: Int = 50
}

private object OverlayRenderConfig {
    @Volatile var showLabelTag: Boolean = true
    @Volatile var showConfidencePercent: Boolean = true
}

private object DebugRenderConfig {
    @Volatile var showPdOverlay: Boolean = true
    @Volatile var showDeviceDebug: Boolean = false
}

private object HoldSafetyConfig {
    @Volatile var enableStuckHoldRecovery: Boolean = false
    @Volatile var recoveryTimeoutMs: Long = 3_500L
}

private object PdRuntimeDiagnostics {
    private val snapshotRef = AtomicReference(PdRuntimeSnapshot())

    fun update(snapshot: PdRuntimeSnapshot) {
        snapshotRef.set(snapshot)
    }

    fun clear(autoFireState: String = "idle") {
        snapshotRef.set(PdRuntimeSnapshot(autoFireState = autoFireState))
    }

    fun snapshot(): PdRuntimeSnapshot = snapshotRef.get()
}

private class PdController(var kp: Float, var kd: Float) {
    private var lastErr: Float = 0f
    fun reset() {
        lastErr = 0f
    }
    fun next(error: Float): Float {
        val derivative = error - lastErr
        lastErr = error
        return kp * error + kd * derivative
    }
}

object AimRangeConfig {
    @Volatile var enabled: Boolean = true
    @Volatile var percent: Float = 50f
}

object TrackingConfig {
    @Volatile var enabled: Boolean = true
    @Volatile var confirmThreshold: Int = 20
    @Volatile var vanishThreshold: Int = 60
    @Volatile var measureNoiseR: Float = 25f
    @Volatile var vanishHeightRatio: Float = 0.2f
    @Volatile var stableBottomThreshold: Float = 10f
    @Volatile var edgeMargin: Float = 20f
}

object TrackingRuntimeStats {
    @Volatile var targetCount: Int = 0
}

data class DetectionBox(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val label: Int,
    val score: Float,
    val trackId: Int = -1
)

private data class TrackState(
    val id: Int,
    var x: Float,
    var y: Float,
    var w: Float,
    var h: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var hits: Int = 1,
    var missed: Int = 0,
    var confirmed: Boolean = false,
    var label: Int = 0,
    var score: Float = 0f,
    var maxHeight: Float = h,
    var lastBottom: Float = y + h * 0.5f
)

private object TargetTracker {
    private val tracks = mutableListOf<TrackState>()
    private var nextTrackId = 1

    fun reset() {
        tracks.clear()
        nextTrackId = 1
        TrackingRuntimeStats.targetCount = 0
    }

    fun update(detections: List<DetectionBox>, frameW: Float, frameH: Float): List<DetectionBox> {
        if (!TrackingConfig.enabled) {
            reset()
            TrackingRuntimeStats.targetCount = detections.size
            return detections
        }

        val edgeMargin = TrackingConfig.edgeMargin.coerceIn(0f, minOf(frameW, frameH) * 0.45f)
        val unmatchedDet = detections.indices.toMutableSet()
        val gain = (1f / (1f + TrackingConfig.measureNoiseR.coerceIn(0f, 100f) / 12f)).coerceIn(0.05f, 0.85f)
        val vanishRatio = TrackingConfig.vanishHeightRatio.coerceIn(0f, 1f)
        val stableBottom = TrackingConfig.stableBottomThreshold.coerceAtLeast(0f)

        tracks.forEach { track ->
            var bestIndex = -1
            var bestDist = Float.MAX_VALUE
            unmatchedDet.forEach { detIndex ->
                val det = detections[detIndex]
                val dx = (det.x + det.w * 0.5f) - (track.x + track.w * 0.5f)
                val dy = (det.y + det.h * 0.5f) - (track.y + track.h * 0.5f)
                val dist2 = dx * dx + dy * dy
                val gate = (maxOf(track.w, track.h) * 1.8f).coerceAtLeast(36f)
                if (dist2 <= gate * gate && dist2 < bestDist) {
                    bestDist = dist2
                    bestIndex = detIndex
                }
            }

            if (bestIndex >= 0) {
                val det = detections[bestIndex]
                unmatchedDet.remove(bestIndex)

                val oldCx = track.x + track.w * 0.5f
                val oldCy = track.y + track.h * 0.5f
                val detCx = det.x + det.w * 0.5f
                val detCy = det.y + det.h * 0.5f

                val newCx = oldCx + gain * (detCx - oldCx)
                var newCy = oldCy + gain * (detCy - oldCy)
                val newW = track.w + gain * (det.w - track.w)
                val newH = track.h + gain * (det.h - track.h)

                val newBottomRaw = newCy + newH * 0.5f
                if (abs(newBottomRaw - track.lastBottom) < stableBottom) {
                    val stabilizedBottom = track.lastBottom * 0.85f + newBottomRaw * 0.15f
                    newCy += stabilizedBottom - newBottomRaw
                    track.lastBottom = stabilizedBottom
                } else {
                    track.lastBottom = newBottomRaw
                }

                val finalX = newCx - newW * 0.5f
                val finalY = newCy - newH * 0.5f

                track.vx = 0.7f * track.vx + 0.3f * (newCx - oldCx)
                track.vy = 0.7f * track.vy + 0.3f * (newCy - oldCy)
                track.x = finalX
                track.y = finalY
                track.w = newW
                track.h = newH
                track.label = det.label
                track.score = det.score
                track.maxHeight = maxOf(track.maxHeight, newH)
                track.hits++
                track.missed = 0
                track.confirmed = track.hits >= TrackingConfig.confirmThreshold.coerceAtLeast(1)
            } else {
                track.missed++
                track.x += track.vx
                track.y += track.vy
            }
        }

        unmatchedDet.forEach { detIndex ->
            val det = detections[detIndex]
            val centerX = det.x + det.w * 0.5f
            val centerY = det.y + det.h * 0.5f
            val nearEdge = centerX < edgeMargin || centerX > (frameW - edgeMargin) || centerY < edgeMargin || centerY > (frameH - edgeMargin)
            if (!nearEdge) {
                tracks += TrackState(
                    id = nextTrackId++,
                    x = det.x,
                    y = det.y,
                    w = det.w,
                    h = det.h,
                    label = det.label,
                    score = det.score,
                    maxHeight = det.h,
                    lastBottom = det.y + det.h * 0.5f
                )
            }
        }

        val vanishTh = TrackingConfig.vanishThreshold.coerceAtLeast(1)
        tracks.removeAll { t ->
            val centerX = t.x + t.w * 0.5f
            val centerY = t.y + t.h * 0.5f
            val nearEdge = centerX < edgeMargin || centerX > (frameW - edgeMargin) || centerY < edgeMargin || centerY > (frameH - edgeMargin)
            val tooSmall = t.maxHeight > 0f && t.h < t.maxHeight * vanishRatio
            t.missed > vanishTh || nearEdge || (tooSmall && t.confirmed)
        }

        val result = tracks.filter { it.confirmed }.map {
            DetectionBox(
                x = it.x,
                y = it.y,
                w = it.w,
                h = it.h,
                label = it.label,
                score = it.score,
                trackId = it.id
            )
        }
        TrackingRuntimeStats.targetCount = result.size
        return result
    }
}

object ReceiverEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiverJob: Job? = null

    @Volatile private var useUdp = true
    @Volatile private var running = false
    @Volatile private var listenPort = 7878
    @Volatile private var connected = false
    @Volatile private var receiveFpsLimit = 240
    @Volatile private var inferenceThreads = 4
    @Volatile private var limitWindowStartMs = System.currentTimeMillis()
    @Volatile private var limitWindowCount = 0

    private val latestFrame = AtomicReference<ByteArray?>(null)
    private val packetCounter = AtomicLong(0)
    private val byteCounter = AtomicLong(0)
    private val pps = AtomicLong(0)
    private val bps = AtomicLong(0)
    @Volatile private var lastAcceptedFrameMs = 0L
    @Volatile private var avgFrameIntervalMs = 0f
    @Volatile private var lastTickMs = System.currentTimeMillis()

    private var udpSocket: DatagramSocket? = null
    private var tcpServerSocket: ServerSocket? = null
    private var tcpClientSocket: Socket? = null

    fun start(port: Int, isUdp: Boolean) {
        val normalized = port.coerceIn(1, 65535)
        val needRestart = !running || normalized != listenPort || isUdp != useUdp
        listenPort = normalized
        useUdp = isUdp
        running = true
        if (needRestart) restart()
    }

    fun stop() {
        running = false
        connected = false
        receiverJob?.cancel()
        receiverJob = null
        closeSockets()
        latestFrame.set(null)
        packetCounter.set(0)
        byteCounter.set(0)
        pps.set(0)
        bps.set(0)
        resetLimitWindow()
        resetLatencyStats()
    }

    fun setPerformance(threadCount: Int, maxReceiveFps: Int) {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        inferenceThreads = threadCount.coerceIn(1, cores)
        receiveFpsLimit = maxReceiveFps.coerceIn(30, 240)
        resetLimitWindow()
    }

    fun snapshot(): StreamStats {
        tickStats()
        val now = System.currentTimeMillis()
        return StreamStats(
            connected = connected,
            protocol = if (useUdp) "UDP" else "TCP",
            receiveFps = pps.get().toInt(),
            bytesPerSec = bps.get(),
            targetCount = TrackingRuntimeStats.targetCount,
            latencyMs = estimateLatencyMs(now)
        )
    }

    fun peekFrame(): ByteArray? = latestFrame.get()

    private fun restart() {
        receiverJob?.cancel()
        closeSockets()
        resetLatencyStats()
        receiverJob = scope.launch {
            if (useUdp) receiveUdpLoop() else receiveTcpLoop()
        }
    }

    private fun receiveUdpLoop() {
        try {
            val socket = DatagramSocket(null).apply {
                reuseAddress = true
                soTimeout = 500
                bind(InetSocketAddress(listenPort))
            }
            udpSocket = socket
            connected = true
            val buf = ByteArray(65535)

            while (running && useUdp) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    if (packet.length > 0) {
                        if (shouldAcceptFrame()) {
                            val now = System.currentTimeMillis()
                            latestFrame.set(packet.data.copyOf(packet.length))
                            packetCounter.incrementAndGet()
                            byteCounter.addAndGet(packet.length.toLong())
                            noteFrameAccepted(now)
                        }
                    }
                    tickStats()
                } catch (_: SocketTimeoutException) {
                    tickStats()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        } finally {
            connected = false
            closeSockets()
        }
    }

    private fun receiveTcpLoop() {
        try {
            val server = ServerSocket().apply {
                reuseAddress = true
                soTimeout = 500
                bind(InetSocketAddress(listenPort))
            }
            tcpServerSocket = server

            while (running && !useUdp) {
                try {
                    connected = false
                    val client = server.accept().apply { soTimeout = 500 }
                    tcpClientSocket = client
                    connected = true
                    readMjpegFrames(client)
                } catch (_: SocketTimeoutException) {
                    tickStats()
                } catch (_: Exception) {
                } finally {
                    connected = false
                    runCatching { tcpClientSocket?.close() }
                    tcpClientSocket = null
                }
            }
        } catch (_: Exception) {
        } finally {
            connected = false
            closeSockets()
        }
    }

    private fun readMjpegFrames(client: Socket) {
        val input = client.getInputStream()
        val readBuf = ByteArray(16 * 1024)
        val cache = ByteArrayOutputStream(128 * 1024)

        while (running && !useUdp) {
            val n = try {
                input.read(readBuf)
            } catch (_: SocketTimeoutException) {
                tickStats(); continue
            } catch (_: Exception) {
                break
            }
            if (n <= 0) break

            cache.write(readBuf, 0, n)
            var bytes = cache.toByteArray()
            var start = indexOf(bytes, SOI, 0)
            if (start < 0) {
                if (bytes.size > MAX_TCP_CACHE) cache.reset()
                continue
            }

            while (start >= 0) {
                val end = indexOf(bytes, EOI, start + 2)
                if (end < 0) {
                    val remain = bytes.copyOfRange(start, bytes.size)
                    cache.reset()
                    cache.write(remain)
                    break
                }

                val frame = bytes.copyOfRange(start, end + 2)
                if (shouldAcceptFrame()) {
                    val now = System.currentTimeMillis()
                    latestFrame.set(frame)
                    packetCounter.incrementAndGet()
                    byteCounter.addAndGet(frame.size.toLong())
                    noteFrameAccepted(now)
                }
                tickStats()

                val nextStart = indexOf(bytes, SOI, end + 2)
                if (nextStart < 0) {
                    cache.reset()
                    break
                }
                start = nextStart
                bytes = bytes.copyOfRange(start, bytes.size)
                start = 0
            }
        }
    }

    private fun tickStats() {
        val now = System.currentTimeMillis()
        if (now - lastTickMs >= 1000L) {
            pps.set(packetCounter.getAndSet(0))
            bps.set(byteCounter.getAndSet(0))
            lastTickMs = now
        }
    }

    private fun shouldAcceptFrame(): Boolean {
        val now = System.currentTimeMillis()
        if (now - limitWindowStartMs >= 1000L) {
            limitWindowStartMs = now
            limitWindowCount = 0
        }
        if (limitWindowCount >= receiveFpsLimit) return false
        limitWindowCount++
        return true
    }

    private fun resetLimitWindow() {
        limitWindowStartMs = System.currentTimeMillis()
        limitWindowCount = 0
    }

    private fun noteFrameAccepted(nowMs: Long) {
        val prev = lastAcceptedFrameMs
        if (prev > 0L) {
            val interval = (nowMs - prev).coerceAtLeast(1L).toFloat()
            avgFrameIntervalMs = if (avgFrameIntervalMs <= 0f) {
                interval
            } else {
                avgFrameIntervalMs * 0.82f + interval * 0.18f
            }
        }
        lastAcceptedFrameMs = nowMs
    }

    private fun estimateLatencyMs(nowMs: Long): Int {
        if (!connected) return 0
        val last = lastAcceptedFrameMs
        if (last <= 0L) return 0
        val frameAge = (nowMs - last).coerceAtLeast(0L).coerceAtMost(2_000L).toInt()
        val interval = avgFrameIntervalMs.roundToInt().coerceAtLeast(0)
        return maxOf(frameAge, interval).coerceAtLeast(1)
    }

    private fun resetLatencyStats() {
        lastAcceptedFrameMs = 0L
        avgFrameIntervalMs = 0f
    }

    private fun closeSockets() {
        runCatching { udpSocket?.close() }
        runCatching { tcpClientSocket?.close() }
        runCatching { tcpServerSocket?.close() }
        udpSocket = null
        tcpClientSocket = null
        tcpServerSocket = null
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int): Int {
        if (pattern.isEmpty() || data.size < pattern.size) return -1
        var i = from.coerceAtLeast(0)
        val last = data.size - pattern.size
        while (i <= last) {
            var ok = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) {
                    ok = false
                    break
                }
            }
            if (ok) return i
            i++
        }
        return -1
    }

    private val SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val EOI = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    private const val MAX_TCP_CACHE = 2 * 1024 * 1024
}

class GameSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val pidX = PdController(kp = 0.2f, kd = 0.01f)
    private val pidY = PdController(kp = 0.1f, kd = 0.01f)
    private var smoothedOutX: Float = 0f
    private var smoothedOutY: Float = 0f
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val boxPaint = Paint().apply {
        color = android.graphics.Color.rgb(235, 70, 70)
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
    }
    private val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 42f
        isAntiAlias = true
    }
    private val tagBgPaint = Paint().apply {
        color = android.graphics.Color.argb(230, 95, 235, 255)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val tagTextPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 20f
        isAntiAlias = true
    }
    private val aimRangePaint = Paint().apply {
        color = android.graphics.Color.rgb(0, 220, 70)
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        isAntiAlias = true
    }
    private val diagBgPaint = Paint().apply {
        color = android.graphics.Color.argb(185, 18, 18, 18)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val diagTextPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 20f
        isAntiAlias = true
    }

    init { holder.addCallback(this) }

    override fun surfaceCreated(holder: SurfaceHolder) {
        job = scope.launch {
            while (isActive) {
                val frame = ReceiverEngine.peekFrame()
                val canvas = runCatching { holder.lockCanvas() }.getOrNull()
                if (canvas != null) {
                    try {
                        canvas.drawColor(android.graphics.Color.BLACK)
                        if (frame != null) {
                            val bmp = runCatching { BitmapFactory.decodeByteArray(frame, 0, frame.size) }.getOrNull()
                            if (bmp != null) {
                                canvas.drawBitmap(bmp, null, Rect(0, 0, width, height), paint)
                                val rangeEnabled = AimRangeConfig.enabled
                                val rangePercent = AimRangeConfig.percent.coerceIn(0f, 100f)
                                val centerX = width / 2f
                                val centerY = height / 2f
                                val rangeRadius = (minOf(width, height) * 0.5f) * (rangePercent / 100f)
                                val detections = mutableListOf<DetectionBox>()
                                val rawDetections = when (RuntimeBridge.selectedModelKind) {
                                    ModelKind.ONNX -> {
                                        OnnxEngine.detect(
                                            bitmap = bmp,
                                            confThreshold = RuntimeBridge.confidence,
                                            nmsThreshold = RuntimeBridge.nms
                                        )
                                    }
                                    ModelKind.NCNN -> {
                                        NcnnEngine.safeDetect(bmp)?.map { obj ->
                                            DetectionBox(
                                                x = obj.x,
                                                y = obj.y,
                                                w = obj.w,
                                                h = obj.h,
                                                label = obj.label,
                                                score = obj.prob
                                            )
                                        }.orEmpty()
                                    }
                                    else -> {
                                        if (OnnxEngine.isInitialized) {
                                            OnnxEngine.detect(
                                                bitmap = bmp,
                                                confThreshold = RuntimeBridge.confidence,
                                                nmsThreshold = RuntimeBridge.nms
                                            )
                                        } else {
                                            NcnnEngine.safeDetect(bmp)?.map { obj ->
                                                DetectionBox(
                                                    x = obj.x,
                                                    y = obj.y,
                                                    w = obj.w,
                                                    h = obj.h,
                                                    label = obj.label,
                                                    score = obj.prob
                                                )
                                            }.orEmpty()
                                        }
                                    }
                                }

                                val sx = width / bmp.width.toFloat()
                                val sy = height / bmp.height.toFloat()
                                rawDetections.forEach { det ->
                                    val x = det.x * sx
                                    val y = det.y * sy
                                    val w = det.w * sx
                                    val h = det.h * sy
                                    val targetX = x + w * 0.5f
                                    val targetY = y + h * 0.5f
                                    if (rangeEnabled) {
                                        val dx = targetX - centerX
                                        val dy = targetY - centerY
                                        val inRange = dx * dx + dy * dy <= rangeRadius * rangeRadius
                                        if (!inRange) return@forEach
                                    }
                                    detections += DetectionBox(x = x, y = y, w = w, h = h, label = det.label, score = det.score)
                                }

                                val tracked = TargetTracker.update(detections, width.toFloat(), height.toFloat())
                                tracked.forEach { box ->
                                    canvas.drawRect(box.x, box.y, box.x + box.w, box.y + box.h, boxPaint)
                                    drawDetectionTag(canvas, box)
                                }
                                if (rangeEnabled) {
                                    canvas.drawCircle(centerX, centerY, rangeRadius, aimRangePaint)
                                }

                                val currentButtonMask = MakcuLinkRuntime.state.value.buttonMask and 0x1F
                                val aimCfg = RuntimeBridge.pickAimbotConfig(currentButtonMask)
                                if (aimCfg?.enabled == true && tracked.isNotEmpty()) {
                                    pidX.kp = AimPdConfig.xKp
                                    pidX.kd = AimPdConfig.xKd
                                    pidY.kp = AimPdConfig.yKp
                                    pidY.kd = AimPdConfig.yKd

                                    val candidates = tracked.filter { box -> aimCfg.isCategoryEnabled(box.label) }
                                    val best = pickAimTarget(candidates, aimCfg, centerX, centerY)
                                    if (best != null) {
                                        val targetX = best.x + best.w * 0.5f
                                        val yOffsetPercent = aimCfg.yOffsetFor(best.label)
                                        val targetY = best.y + best.h * (yOffsetPercent / 100f)
                                        val errX = targetX - centerX
                                        val errY = targetY - centerY
                                        val deadzoneX = AimPdConfig.xDeadzone.coerceAtLeast(0).toFloat()
                                        val deadzoneY = AimPdConfig.yDeadzone.coerceAtLeast(0).toFloat()
                                        val pdOutX = if (abs(errX) <= deadzoneX) 0f else pidX.next(errX) * aimCfg.sensitivity
                                        val pdOutY = if (abs(errY) <= deadzoneY) 0f else pidY.next(errY) * aimCfg.sensitivity
                                        val smoothX = AimPdConfig.xSmooth.coerceIn(0f, 0.98f)
                                        val smoothY = AimPdConfig.ySmooth.coerceIn(0f, 0.98f)
                                        smoothedOutX = if (abs(pdOutX) < 0.001f) {
                                            0f
                                        } else {
                                            smoothedOutX * smoothX + pdOutX * (1f - smoothX)
                                        }
                                        smoothedOutY = if (abs(pdOutY) < 0.001f) {
                                            0f
                                        } else {
                                            smoothedOutY * smoothY + pdOutY * (1f - smoothY)
                                        }
                                        val rawMoveX = smoothedOutX.roundToInt()
                                        val rawMoveY = smoothedOutY.roundToInt()
                                        val moveX = rawMoveX.coerceIn(-AimPdConfig.xMaxOut, AimPdConfig.xMaxOut)
                                        val moveY = rawMoveY.coerceIn(-AimPdConfig.yMaxOut, AimPdConfig.yMaxOut)
                                        if (aimCfg.autoAimEnabled && abs(moveX) + abs(moveY) > 0) {
                                            RuntimeBridge.sendMove(moveX, moveY)
                                        }
                                        val fireDx = targetX - centerX
                                        val fireDy = targetY - centerY
                                        val inFireRange = fireDx * fireDx + fireDy * fireDy <= aimCfg.fireRangePx * aimCfg.fireRangePx
                                        RuntimeBridge.updateAutoFire(
                                            enabled = aimCfg.autoFireEnabled && inFireRange,
                                            cfg = aimCfg
                                        )
                                        val snapshot = PdRuntimeSnapshot(
                                            active = true,
                                            targetId = if (best.trackId > 0) best.trackId else best.label,
                                            errX = errX,
                                            errY = errY,
                                            pdOutX = pdOutX,
                                            pdOutY = pdOutY,
                                            smoothOutX = smoothedOutX,
                                            smoothOutY = smoothedOutY,
                                            moveX = moveX,
                                            moveY = moveY,
                                            clampX = rawMoveX != moveX,
                                            clampY = rawMoveY != moveY,
                                            inFireRange = inFireRange,
                                            autoFireState = RuntimeBridge.autoFireState()
                                        )
                                        PdRuntimeDiagnostics.update(snapshot)
                                        if (DebugRenderConfig.showPdOverlay) {
                                            drawDiagnosticsOverlay(canvas, snapshot)
                                        }
                                    } else {
                                        RuntimeBridge.updateAutoFire(enabled = false, cfg = aimCfg)
                                        pidX.reset()
                                        pidY.reset()
                                        smoothedOutX = 0f
                                        smoothedOutY = 0f
                                        val snapshot = PdRuntimeSnapshot(autoFireState = RuntimeBridge.autoFireState())
                                        PdRuntimeDiagnostics.update(snapshot)
                                        if (DebugRenderConfig.showPdOverlay) {
                                            drawDiagnosticsOverlay(canvas, snapshot)
                                        }
                                    }
                                } else {
                                    RuntimeBridge.updateAutoFire(enabled = false, cfg = aimCfg)
                                    pidX.reset()
                                    pidY.reset()
                                    smoothedOutX = 0f
                                    smoothedOutY = 0f
                                    val snapshot = PdRuntimeSnapshot(autoFireState = RuntimeBridge.autoFireState())
                                    PdRuntimeDiagnostics.update(snapshot)
                                    if (DebugRenderConfig.showPdOverlay) {
                                        drawDiagnosticsOverlay(canvas, snapshot)
                                    }
                                }

                                bmp.recycle()
                            } else {
                                RuntimeBridge.updateAutoFire(enabled = false, cfg = null)
                                PdRuntimeDiagnostics.clear(RuntimeBridge.autoFireState())
                                canvas.drawText("等待画面...", 24f, 64f, textPaint)
                            }
                        } else {
                            RuntimeBridge.updateAutoFire(enabled = false, cfg = null)
                            PdRuntimeDiagnostics.clear(RuntimeBridge.autoFireState())
                            canvas.drawText("等待数据...", 24f, 64f, textPaint)
                        }
                    } finally {
                        holder.unlockCanvasAndPost(canvas)
                    }
                }
                delay(16)
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        job?.cancel()
        job = null
        RuntimeBridge.updateAutoFire(enabled = false, cfg = null)
        pidX.reset()
        pidY.reset()
        smoothedOutX = 0f
        smoothedOutY = 0f
        PdRuntimeDiagnostics.clear(RuntimeBridge.autoFireState())
        scope.cancel()
    }

    private fun pickAimTarget(
        detections: List<DetectionBox>,
        cfg: RuntimeAimbotConfig,
        centerX: Float,
        centerY: Float
    ): DetectionBox? {
        if (detections.isEmpty()) return null
        fun distanceSq(box: DetectionBox): Float {
            val tx = box.x + box.w * 0.5f
            val ty = box.y + box.h * 0.5f
            val dx = tx - centerX
            val dy = ty - centerY
            return dx * dx + dy * dy
        }

        if (cfg.aimMode == "class_priority" && cfg.categoryPriorityEnabled) {
            val sorted = detections.sortedWith(
                compareBy<DetectionBox> { cfg.priorityRank(it.label) }
                    .thenBy { distanceSq(it) }
                    .thenByDescending { it.score }
            )
            return sorted.firstOrNull()
        }

        return detections.minByOrNull { distanceSq(it) }
    }

    private fun drawDetectionTag(canvas: android.graphics.Canvas, box: DetectionBox) {
        val showLabel = OverlayRenderConfig.showLabelTag
        val showPercent = OverlayRenderConfig.showConfidencePercent
        if (!showLabel && !showPercent) return

        val parts = mutableListOf<String>()
        if (showLabel) {
            val idValue = if (box.trackId > 0) box.trackId else box.label.coerceAtLeast(0)
            parts += "ID:$idValue"
        }
        if (showPercent) {
            val pct = (box.score.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)
            parts += "$pct%"
        }
        if (parts.isEmpty()) return

        val text = parts.joinToString(" ")
        val textWidth = tagTextPaint.measureText(text)
        val paddingH = 9f
        val tagHeight = 24f
        val boxLeft = box.x.coerceAtLeast(0f)
        val preferredTop = box.y - tagHeight - 3f
        val tagTop = if (preferredTop >= 0f) preferredTop else (box.y + 2f)
        val tagRect = RectF(
            boxLeft,
            tagTop,
            (boxLeft + textWidth + paddingH * 2f).coerceAtMost(width.toFloat() - 2f),
            (tagTop + tagHeight).coerceAtMost(height.toFloat() - 2f)
        )
        canvas.drawRoundRect(tagRect, 5f, 5f, tagBgPaint)
        val baseline = tagRect.top + tagHeight * 0.72f
        canvas.drawText(text, tagRect.left + paddingH, baseline, tagTextPaint)
    }

    private fun drawDiagnosticsOverlay(canvas: android.graphics.Canvas, snapshot: PdRuntimeSnapshot) {
        val lines = listOf(
            "PD ${if (snapshot.active) "active" else "idle"} target=${if (snapshot.targetId >= 0) snapshot.targetId else "--"} fire=${snapshot.autoFireState}",
            "err x=${"%.1f".format(snapshot.errX)} y=${"%.1f".format(snapshot.errY)}",
            "pd  x=${"%.2f".format(snapshot.pdOutX)} y=${"%.2f".format(snapshot.pdOutY)}",
            "out x=${snapshot.moveX} y=${snapshot.moveY} clamp=${if (snapshot.clampX) "X" else "-"}${if (snapshot.clampY) "Y" else "-"} inRange=${if (snapshot.inFireRange) "yes" else "no"}"
        )
        val padding = 12f
        val lineHeight = 24f
        val textWidth = lines.maxOfOrNull { diagTextPaint.measureText(it) } ?: 0f
        val rect = RectF(
            12f,
            12f,
            12f + textWidth + padding * 2f,
            12f + padding * 2f + lineHeight * lines.size
        )
        canvas.drawRoundRect(rect, 8f, 8f, diagBgPaint)
        lines.forEachIndexed { index, line ->
            val baseline = rect.top + padding + lineHeight * (index + 0.8f)
            canvas.drawText(line, rect.left + padding, baseline, diagTextPaint)
        }
    }
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(AUTO_CONFIG_PREFS, Context.MODE_PRIVATE) }
    var autoConfigLoaded by remember { mutableStateOf(false) }
    var onnxReloadWatcherArmed by remember { mutableStateOf(false) }

    var currentTab by rememberSaveable { mutableStateOf(AppTab.Monitor) }
    var running by rememberSaveable { mutableStateOf(false) }
    var isUdp by rememberSaveable { mutableStateOf(true) }
    var udpPort by rememberSaveable { mutableStateOf("7878") }
    var tcpPort by rememberSaveable { mutableStateOf("7878") }
    var confidence by rememberSaveable { mutableStateOf(0.25f) }
    var nms by rememberSaveable { mutableStateOf(0.45f) }
    var selectedModel by rememberSaveable { mutableStateOf("未选择模型") }
    var onnxNnapiEnabled by rememberSaveable { mutableStateOf(true) }
    var inferenceThreads by rememberSaveable { mutableStateOf(4) }
    var receiveFps by rememberSaveable { mutableStateOf(240) }
    var dynamicColor by rememberSaveable { mutableStateOf(false) }
    var aimRangeEnabled by rememberSaveable { mutableStateOf(true) }
    var aimRangePercent by rememberSaveable { mutableStateOf(50f) }
    var showLabelTag by rememberSaveable { mutableStateOf(true) }
    var showConfidencePercent by rememberSaveable { mutableStateOf(true) }
    var trackingEnabled by rememberSaveable { mutableStateOf(true) }
    var trackingConfirmThreshold by rememberSaveable { mutableStateOf(20) }
    var trackingVanishThreshold by rememberSaveable { mutableStateOf(60) }
    var trackingMeasureNoiseR by rememberSaveable { mutableStateOf(25f) }
    var trackingVanishHeightRatio by rememberSaveable { mutableStateOf(0.2f) }
    var trackingStableBottomThreshold by rememberSaveable { mutableStateOf(10f) }
    var trackingEdgeMargin by rememberSaveable { mutableStateOf(20f) }
    var xKpInput by rememberSaveable { mutableStateOf("0.2000") }
    var xKdInput by rememberSaveable { mutableStateOf("0.010") }
    var xSmoothInput by rememberSaveable { mutableStateOf("0.5000") }
    var xDeadzoneInput by rememberSaveable { mutableStateOf("0") }
    var xMaxOutInput by rememberSaveable { mutableStateOf("200") }
    var yKpInput by rememberSaveable { mutableStateOf("0.1000") }
    var yKdInput by rememberSaveable { mutableStateOf("0.010") }
    var ySmoothInput by rememberSaveable { mutableStateOf("0.2000") }
    var yDeadzoneInput by rememberSaveable { mutableStateOf("0") }
    var yMaxOutInput by rememberSaveable { mutableStateOf("50") }
    var showPdOverlay by rememberSaveable { mutableStateOf(true) }
    var showDeviceDebug by rememberSaveable { mutableStateOf(false) }
    var enableStuckHoldRecovery by rememberSaveable { mutableStateOf(false) }

    val modelOptions = remember { mutableStateListOf<ModelEntry>() }
    val onnxRuntimeLogs by OnnxEngine.runtimeLogs.collectAsState()
    val hotkeys = remember {
        mutableStateListOf(
            HotkeyConfig("热键1"),
            HotkeyConfig("热键2"),
            HotkeyConfig("热键3")
        )
    }

    var stats by remember { mutableStateOf(StreamStats(protocol = if (isUdp) "UDP" else "TCP")) }

    fun refreshModels() {
        modelOptions.clear()
        modelOptions.addAll(scanModelEntries(context))
        if (modelOptions.none { it.name == selectedModel }) {
            selectedModel = modelOptions.firstOrNull()?.name ?: "未选择模型"
        }
    }

    fun initModelIfNeeded(modelName: String, silent: Boolean = false) {
        val entry = modelOptions.firstOrNull { it.name == modelName } ?: return
        when {
            entry.kind == ModelKind.NCNN && entry.paramPath != null && entry.binPath != null -> {
                if (!NcnnEngine.isLoaded) {
                    if (!silent) Toast.makeText(context, "核心库未加载", Toast.LENGTH_SHORT).show()
                    RuntimeBridge.selectedModelKind = ModelKind.FILE
                    return
                }
                val ok = NcnnEngine.safeInit(entry.paramPath, entry.binPath)
                RuntimeBridge.selectedModelKind = if (ok) ModelKind.NCNN else ModelKind.FILE
                if (!silent) {
                    Toast.makeText(context, if (ok) "NCNN 模型加载成功" else "NCNN 模型加载失败", Toast.LENGTH_SHORT).show()
                }
            }
            entry.kind == ModelKind.ONNX && !entry.filePath.isNullOrBlank() -> {
                val ok = OnnxEngine.init(entry.filePath!!, inferenceThreads, onnxNnapiEnabled)
                RuntimeBridge.selectedModelKind = if (ok) ModelKind.ONNX else ModelKind.FILE
                if (!silent) {
                    val msg = if (ok) {
                        "ONNX 模型加载成功"
                    } else {
                        "ONNX 模型加载失败: ${OnnxEngine.lastStatus}"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                RuntimeBridge.selectedModelKind = ModelKind.FILE
                if (!silent) Toast.makeText(context, "该文件不是可用模型", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun buildConfigJson(): JSONObject = JSONObject().apply {
        put("version", APP_VERSION)
        put("isUdp", isUdp)
        put("udpPort", udpPort.toIntOrNull() ?: 7878)
        put("tcpPort", tcpPort.toIntOrNull() ?: 7878)
        put("confidence", confidence.toDouble())
        put("nms", nms.toDouble())
        put("selectedModel", selectedModel)
        put("onnxNnapiEnabled", onnxNnapiEnabled)
        put("inferenceThreads", inferenceThreads)
        put("receiveFps", receiveFps)
        put("dynamicColor", dynamicColor)
        put("aimRangeEnabled", aimRangeEnabled)
        put("aimRangePercent", aimRangePercent.toDouble())
        put("showLabelTag", showLabelTag)
        put("showConfidencePercent", showConfidencePercent)
        put("trackingEnabled", trackingEnabled)
        put("trackingConfirmThreshold", trackingConfirmThreshold)
        put("trackingVanishThreshold", trackingVanishThreshold)
        put("trackingMeasureNoiseR", trackingMeasureNoiseR.toDouble())
        put("trackingVanishHeightRatio", trackingVanishHeightRatio.toDouble())
        put("trackingStableBottomThreshold", trackingStableBottomThreshold.toDouble())
        put("trackingEdgeMargin", trackingEdgeMargin.toDouble())
        put("xKp", parseFloatInputValue(xKpInput, 0.2f, 0f, 5f).toDouble())
        put("xKd", parseFloatInputValue(xKdInput, 0.01f, 0f, 5f).toDouble())
        put("xSmooth", parseFloatInputValue(xSmoothInput, 0.5f, 0f, 0.98f).toDouble())
        put("xDeadzone", parseIntInputValue(xDeadzoneInput, 0, 0, 200))
        put("xMaxOut", parseIntInputValue(xMaxOutInput, 200, 1, 800))
        put("yKp", parseFloatInputValue(yKpInput, 0.1f, 0f, 5f).toDouble())
        put("yKd", parseFloatInputValue(yKdInput, 0.01f, 0f, 5f).toDouble())
        put("ySmooth", parseFloatInputValue(ySmoothInput, 0.2f, 0f, 0.98f).toDouble())
        put("yDeadzone", parseIntInputValue(yDeadzoneInput, 0, 0, 200))
        put("yMaxOut", parseIntInputValue(yMaxOutInput, 50, 1, 800))
        put("showPdOverlay", showPdOverlay)
        put("showDeviceDebug", showDeviceDebug)
        put("enableStuckHoldRecovery", enableStuckHoldRecovery)
        put("hotkeys", JSONArray().apply { hotkeys.forEach { put(hotkeyToJson(it)) } })
    }

    fun applyConfig(root: JSONObject) {
        isUdp = root.optBoolean("isUdp", true)
        udpPort = root.optInt("udpPort", 7878).coerceIn(1, 65535).toString()
        tcpPort = root.optInt("tcpPort", 7878).coerceIn(1, 65535).toString()
        confidence = root.optDouble("confidence", 0.25).toFloat().coerceIn(0f, 1f)
        nms = root.optDouble("nms", 0.45).toFloat().coerceIn(0f, 1f)
        selectedModel = root.optString("selectedModel", selectedModel)
        onnxNnapiEnabled = root.optBoolean("onnxNnapiEnabled", true)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        inferenceThreads = root.optInt("inferenceThreads", 4).coerceIn(1, cores)
        receiveFps = root.optInt("receiveFps", 240).coerceIn(30, 240)
        dynamicColor = root.optBoolean("dynamicColor", false)
        aimRangeEnabled = root.optBoolean("aimRangeEnabled", true)
        aimRangePercent = root.optDouble("aimRangePercent", 50.0).toFloat().coerceIn(0f, 100f)
        showLabelTag = root.optBoolean("showLabelTag", true)
        showConfidencePercent = root.optBoolean("showConfidencePercent", true)
        trackingEnabled = root.optBoolean("trackingEnabled", true)
        trackingConfirmThreshold = root.optInt("trackingConfirmThreshold", 20).coerceIn(1, 120)
        trackingVanishThreshold = root.optInt("trackingVanishThreshold", 60).coerceIn(1, 240)
        trackingMeasureNoiseR = root.optDouble("trackingMeasureNoiseR", 25.0).toFloat().coerceIn(0f, 100f)
        trackingVanishHeightRatio = root.optDouble("trackingVanishHeightRatio", 0.2).toFloat().coerceIn(0f, 1f)
        trackingStableBottomThreshold = root.optDouble("trackingStableBottomThreshold", 10.0).toFloat().coerceIn(0f, 100f)
        trackingEdgeMargin = root.optDouble("trackingEdgeMargin", 20.0).toFloat().coerceIn(0f, 200f)
        xKpInput = formatFloatInput(root.optDouble("xKp", 0.2).toFloat().coerceIn(0f, 5f), 4)
        xKdInput = formatFloatInput(root.optDouble("xKd", 0.01).toFloat().coerceIn(0f, 5f), 4)
        xSmoothInput = formatFloatInput(root.optDouble("xSmooth", 0.5).toFloat().coerceIn(0f, 0.98f), 4)
        xDeadzoneInput = root.optInt("xDeadzone", 0).coerceIn(0, 200).toString()
        xMaxOutInput = root.optInt("xMaxOut", 200).coerceIn(1, 800).toString()
        yKpInput = formatFloatInput(root.optDouble("yKp", 0.1).toFloat().coerceIn(0f, 5f), 4)
        yKdInput = formatFloatInput(root.optDouble("yKd", 0.01).toFloat().coerceIn(0f, 5f), 4)
        ySmoothInput = formatFloatInput(root.optDouble("ySmooth", 0.2).toFloat().coerceIn(0f, 0.98f), 4)
        yDeadzoneInput = root.optInt("yDeadzone", 0).coerceIn(0, 200).toString()
        yMaxOutInput = root.optInt("yMaxOut", 50).coerceIn(1, 800).toString()
        showPdOverlay = root.optBoolean("showPdOverlay", true)
        showDeviceDebug = root.optBoolean("showDeviceDebug", false)
        enableStuckHoldRecovery = root.optBoolean("enableStuckHoldRecovery", false)

        val parsed = parseHotkeys(root.optJSONArray("hotkeys"))
        hotkeys.clear()
        hotkeys.addAll(parsed)
        while (hotkeys.size < 3) hotkeys.add(HotkeyConfig("热键${hotkeys.size + 1}"))
        while (hotkeys.size > 3) hotkeys.removeLast()
        hotkeys.indices.forEach { i -> hotkeys[i] = hotkeys[i].copy(name = "热键${i + 1}") }

        refreshModels()
    }

    val importModelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = copyModelToPrivateDir(context, uri)
        if (fileName == null) {
            Toast.makeText(context, "导入模型失败", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        refreshModels()
        val base = fileName.substringBeforeLast('.', fileName)
        val best = modelOptions.firstOrNull { it.name.startsWith(base) } ?: modelOptions.firstOrNull { it.name == fileName }
        if (best != null) {
            selectedModel = best.name
            initModelIfNeeded(best.name)
        }
        Toast.makeText(context, "已导入模型: $fileName", Toast.LENGTH_SHORT).show()
    }

    val exportConfigLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val root = buildConfigJson()
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) } != null
        }.getOrDefault(false)
        Toast.makeText(context, if (ok) "导出成功" else "导出失败", Toast.LENGTH_SHORT).show()
    }

    val importConfigLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            Toast.makeText(context, "导入失败：文件为空", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val root = runCatching { JSONObject(text) }.getOrNull()
        if (root == null) {
            Toast.makeText(context, "导入失败：格式不正确", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        applyConfig(root)
        Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        val cached = prefs.getString(AUTO_CONFIG_KEY, null)
        if (!cached.isNullOrBlank()) {
            val root = runCatching { JSONObject(cached) }.getOrNull()
            if (root != null) {
                applyConfig(root)
            } else {
                refreshModels()
            }
        } else {
            refreshModels()
        }
        autoConfigLoaded = true
    }

    LaunchedEffect(autoConfigLoaded) {
        if (!autoConfigLoaded) return@LaunchedEffect
        snapshotFlow { buildConfigJson().toString() }
            .distinctUntilChanged()
            .collect { json ->
                prefs.edit().putString(AUTO_CONFIG_KEY, json).apply()
            }
    }

    val activePort = if (isUdp) udpPort.toIntOrNull()?.coerceIn(1, 65535) ?: 7878 else tcpPort.toIntOrNull()?.coerceIn(1, 65535) ?: 7878

    LaunchedEffect(confidence, nms) {
        RuntimeBridge.confidence = confidence.coerceIn(0f, 1f)
        RuntimeBridge.nms = nms.coerceIn(0f, 1f)
    }
    LaunchedEffect(onnxNnapiEnabled) {
        OnnxEngine.setNnapiEnabled(onnxNnapiEnabled)
    }
    LaunchedEffect(hotkeys.toList()) {
        RuntimeBridge.hotkeys = hotkeys.toList()
    }
    LaunchedEffect(inferenceThreads, receiveFps) {
        ReceiverEngine.setPerformance(inferenceThreads, receiveFps)
    }
    LaunchedEffect(aimRangeEnabled, aimRangePercent) {
        AimRangeConfig.enabled = aimRangeEnabled
        AimRangeConfig.percent = aimRangePercent.coerceIn(0f, 100f)
    }
    LaunchedEffect(
        xKpInput,
        xKdInput,
        xSmoothInput,
        xDeadzoneInput,
        xMaxOutInput,
        yKpInput,
        yKdInput,
        ySmoothInput,
        yDeadzoneInput,
        yMaxOutInput
    ) {
        AimPdConfig.xKp = parseFloatInputValue(xKpInput, 0.2f, 0f, 5f)
        AimPdConfig.xKd = parseFloatInputValue(xKdInput, 0.01f, 0f, 5f)
        AimPdConfig.xSmooth = parseFloatInputValue(xSmoothInput, 0.5f, 0f, 0.98f)
        AimPdConfig.xDeadzone = parseIntInputValue(xDeadzoneInput, 0, 0, 200)
        AimPdConfig.xMaxOut = parseIntInputValue(xMaxOutInput, 200, 1, 800)
        AimPdConfig.yKp = parseFloatInputValue(yKpInput, 0.1f, 0f, 5f)
        AimPdConfig.yKd = parseFloatInputValue(yKdInput, 0.01f, 0f, 5f)
        AimPdConfig.ySmooth = parseFloatInputValue(ySmoothInput, 0.2f, 0f, 0.98f)
        AimPdConfig.yDeadzone = parseIntInputValue(yDeadzoneInput, 0, 0, 200)
        AimPdConfig.yMaxOut = parseIntInputValue(yMaxOutInput, 50, 1, 800)
    }
    LaunchedEffect(showLabelTag, showConfidencePercent) {
        OverlayRenderConfig.showLabelTag = showLabelTag
        OverlayRenderConfig.showConfidencePercent = showConfidencePercent
    }
    LaunchedEffect(showPdOverlay, showDeviceDebug, enableStuckHoldRecovery) {
        DebugRenderConfig.showPdOverlay = showPdOverlay
        DebugRenderConfig.showDeviceDebug = showDeviceDebug
        HoldSafetyConfig.enableStuckHoldRecovery = enableStuckHoldRecovery
    }
    LaunchedEffect(
        trackingEnabled,
        trackingConfirmThreshold,
        trackingVanishThreshold,
        trackingMeasureNoiseR,
        trackingVanishHeightRatio,
        trackingStableBottomThreshold,
        trackingEdgeMargin
    ) {
        TrackingConfig.enabled = trackingEnabled
        TrackingConfig.confirmThreshold = trackingConfirmThreshold.coerceAtLeast(1)
        TrackingConfig.vanishThreshold = trackingVanishThreshold.coerceAtLeast(1)
        TrackingConfig.measureNoiseR = trackingMeasureNoiseR.coerceIn(0f, 100f)
        TrackingConfig.vanishHeightRatio = trackingVanishHeightRatio.coerceIn(0f, 1f)
        TrackingConfig.stableBottomThreshold = trackingStableBottomThreshold.coerceIn(0f, 100f)
        TrackingConfig.edgeMargin = trackingEdgeMargin.coerceIn(0f, 200f)
        if (!trackingEnabled) TargetTracker.reset()
    }
    LaunchedEffect(running, isUdp, activePort) {
        if (running) ReceiverEngine.start(activePort, isUdp) else ReceiverEngine.stop()
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            stats = ReceiverEngine.snapshot()
            delay(250)
        }
    }
    LaunchedEffect(autoConfigLoaded, selectedModel, modelOptions.map { it.name }) {
        if (!autoConfigLoaded) return@LaunchedEffect
        if (selectedModel.isBlank() || selectedModel == "未选择模型") return@LaunchedEffect
        initModelIfNeeded(selectedModel, silent = true)
    }
    LaunchedEffect(autoConfigLoaded, inferenceThreads, onnxNnapiEnabled) {
        if (!autoConfigLoaded) return@LaunchedEffect
        if (!onnxReloadWatcherArmed) {
            onnxReloadWatcherArmed = true
            return@LaunchedEffect
        }
        val entry = modelOptions.firstOrNull { it.name == selectedModel } ?: return@LaunchedEffect
        if (entry.kind != ModelKind.ONNX || entry.filePath.isNullOrBlank()) return@LaunchedEffect
        initModelIfNeeded(selectedModel, silent = true)
    }
    DisposableEffect(Unit) { onDispose { ReceiverEngine.stop() } }

    HXHostTheme(dynamicColor = dynamicColor) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFFEEEEEE))
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding).background(Color(0xFFF5F5F5))) {
                when (currentTab) {
                    AppTab.Monitor -> MonitorScreen(
                        running = running,
                        stats = stats,
                        isUdp = isUdp,
                        udpPort = udpPort,
                        tcpPort = tcpPort,
                        activePort = activePort,
                        confidence = confidence,
                        nms = nms,
                        showLabelTag = showLabelTag,
                        showConfidencePercent = showConfidencePercent,
                        selectedModel = selectedModel,
                        onnxNnapiEnabled = onnxNnapiEnabled,
                        onnxRuntimeLogs = onnxRuntimeLogs,
                        modelNames = modelOptions.map { it.name },
                        onToggleRun = { running = it },
                        onProtocolChange = { isUdp = it },
                        onConfidenceChange = { confidence = it },
                        onNmsChange = { nms = it },
                        onShowLabelTagChange = { showLabelTag = it },
                        onShowConfidencePercentChange = { showConfidencePercent = it },
                        onSelectModel = { selectedModel = it; initModelIfNeeded(it) },
                        onOnnxNnapiEnabledChange = { onnxNnapiEnabled = it },
                        onClearOnnxLogs = { OnnxEngine.clearRuntimeLogs() },
                        onImportModel = { importModelLauncher.launch(arrayOf("*/*")) },
                        onExportConfig = { exportConfigLauncher.launch("hx_host_config.json") },
                        onImportConfig = { importConfigLauncher.launch(arrayOf("application/json", "text/plain")) }
                    )
                    AppTab.Input -> InputControlScreen(
                        hotkeys = hotkeys,
                        aimRangeEnabled = aimRangeEnabled,
                        aimRangePercent = aimRangePercent,
                        xKp = xKpInput,
                        xKd = xKdInput,
                        xSmooth = xSmoothInput,
                        xDeadzone = xDeadzoneInput,
                        xMaxOut = xMaxOutInput,
                        yKp = yKpInput,
                        yKd = yKdInput,
                        ySmooth = ySmoothInput,
                        yDeadzone = yDeadzoneInput,
                        yMaxOut = yMaxOutInput,
                        showPdOverlay = showPdOverlay,
                        showDeviceDebug = showDeviceDebug,
                        enableStuckHoldRecovery = enableStuckHoldRecovery,
                        onHotkeyChanged = { index, item -> hotkeys[index] = item },
                        onAimRangeEnabledChange = { aimRangeEnabled = it },
                        onAimRangePercentChange = { aimRangePercent = it.coerceIn(0f, 100f) },
                        onXKpChange = { xKpInput = filterDecimalInput(it) },
                        onXKdChange = { xKdInput = filterDecimalInput(it) },
                        onXSmoothChange = { xSmoothInput = filterDecimalInput(it) },
                        onXDeadzoneChange = { xDeadzoneInput = filterIntInput(it) },
                        onXMaxOutChange = { xMaxOutInput = filterIntInput(it) },
                        onYKpChange = { yKpInput = filterDecimalInput(it) },
                        onYKdChange = { yKdInput = filterDecimalInput(it) },
                        onYSmoothChange = { ySmoothInput = filterDecimalInput(it) },
                        onYDeadzoneChange = { yDeadzoneInput = filterIntInput(it) },
                        onYMaxOutChange = { yMaxOutInput = filterIntInput(it) },
                        onShowPdOverlayChange = { showPdOverlay = it },
                        onShowDeviceDebugChange = { showDeviceDebug = it },
                        onEnableStuckHoldRecoveryChange = { enableStuckHoldRecovery = it }
                    )
                    AppTab.Function -> FunctionScreen(
                        trackingEnabled = trackingEnabled,
                        confirmThreshold = trackingConfirmThreshold,
                        vanishThreshold = trackingVanishThreshold,
                        measureNoiseR = trackingMeasureNoiseR,
                        vanishHeightRatio = trackingVanishHeightRatio,
                        stableBottomThreshold = trackingStableBottomThreshold,
                        edgeMargin = trackingEdgeMargin,
                        onTrackingEnabledChange = { trackingEnabled = it },
                        onConfirmThresholdChange = { trackingConfirmThreshold = it },
                        onVanishThresholdChange = { trackingVanishThreshold = it },
                        onMeasureNoiseRChange = { trackingMeasureNoiseR = it },
                        onVanishHeightRatioChange = { trackingVanishHeightRatio = it },
                        onStableBottomThresholdChange = { trackingStableBottomThreshold = it },
                        onEdgeMarginChange = { trackingEdgeMargin = it }
                    )
                    AppTab.About -> AboutScreen()
                    AppTab.Settings -> SettingsScreen(
                        udpPort = udpPort,
                        tcpPort = tcpPort,
                        inferenceThreads = inferenceThreads,
                        receiveFps = receiveFps,
                        dynamicColor = dynamicColor,
                        onUdpPort = { udpPort = it.filter(Char::isDigit) },
                        onTcpPort = { tcpPort = it.filter(Char::isDigit) },
                        onInferenceThreadsChange = { inferenceThreads = it },
                        onReceiveFpsChange = { receiveFps = it },
                        onDynamicColorChange = { dynamicColor = it }
                    )
                }
            }
        }
    }
}

@Composable
fun MonitorScreen(
    running: Boolean,
    stats: StreamStats,
    isUdp: Boolean,
    udpPort: String,
    tcpPort: String,
    activePort: Int,
    confidence: Float,
    nms: Float,
    showLabelTag: Boolean,
    showConfidencePercent: Boolean,
    selectedModel: String,
    onnxNnapiEnabled: Boolean,
    onnxRuntimeLogs: List<String>,
    modelNames: List<String>,
    onToggleRun: (Boolean) -> Unit,
    onProtocolChange: (Boolean) -> Unit,
    onConfidenceChange: (Float) -> Unit,
    onNmsChange: (Float) -> Unit,
    onShowLabelTagChange: (Boolean) -> Unit,
    onShowConfidencePercentChange: (Boolean) -> Unit,
    onSelectModel: (String) -> Unit,
    onOnnxNnapiEnabledChange: (Boolean) -> Unit,
    onClearOnnxLogs: () -> Unit,
    onImportModel: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var detectExpanded by rememberSaveable { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var runtimeLogExpanded by rememberSaveable { mutableStateOf(false) }
    val cardColor = Color(0xFFEFEFEF)
    val sliderColors = SliderDefaults.colors(
        thumbColor = Color(0xFF6F6F6F),
        activeTrackColor = Color(0xFF7E7E7E),
        inactiveTrackColor = Color(0xFFE2E2E2)
    )
    val localIp = remember { NetworkUtils.getLocalIpAddress() }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(APP_NAME, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("导出配置") }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }, onClick = { menuExpanded = false; onExportConfig() })
                    DropdownMenuItem(text = { Text("导入配置") }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }, onClick = { menuExpanded = false; onImportConfig() })
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF7D7D7D), modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (running) "工作中" else "待机中", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        runtimeBackendBadges().forEach { badge ->
                            ExecutionBackendBadge(
                                label = badge.label,
                                containerColor = badge.containerColor,
                                contentColor = badge.contentColor
                            )
                        }
                    }
                    Text("版本: $APP_VERSION", color = Color(0xFF555555), fontSize = 12.sp)
                    Text("接收: ${stats.receiveFps} fps", color = Color(0xFF555555), fontSize = 12.sp)
                    Text("推理: ${if (running) stats.receiveFps else 0} fps", color = Color(0xFF555555), fontSize = 12.sp)
                    Text("延迟: ${stats.latencyMs}ms", color = Color(0xFF555555), fontSize = 12.sp)
                    Text("目标数: ${stats.targetCount}", color = Color(0xFF555555), fontSize = 12.sp)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).height(172.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black)
            ) {
                if (running) AndroidView(factory = { GameSurfaceView(it) }, modifier = Modifier.fillMaxSize())
                else Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("已停止", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("等待启动", color = Color(0xFFCCCCCC), fontSize = 14.sp)
                }
            }

            Column(Modifier.weight(1f).height(172.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonitorToolButton(
                        label = "△",
                        active = showLabelTag,
                        modifier = Modifier.weight(1f)
                    ) { onShowLabelTagChange(!showLabelTag) }
                    MonitorToolButton(
                        label = "%",
                        active = showConfidencePercent,
                        modifier = Modifier.weight(1f)
                    ) { onShowConfidencePercentChange(!showConfidencePercent) }
                }
                Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterEnd) {
                    Text("256 | INT8", fontWeight = FontWeight.Bold, color = Color(0xFF555555), fontSize = 16.sp)
                }
                Button(
                    onClick = { onToggleRun(!running) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) Color(0xFFC8102E) else Color(0xFF8A8A8A),
                        contentColor = Color.White
                    )
                ) {
                    if (running) {
                        Text("X", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Text("停止", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text("启动推理", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("模型选择", fontWeight = FontWeight.Bold, color = Color(0xFF666666), fontSize = 14.sp)
                    OutlinedButton(
                        onClick = onImportModel,
                        modifier = Modifier.width(62.dp).height(34.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color(0xFFBFBFBF)),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF2F2F2))
                    ) {
                        Text("+", color = Color(0xFF6D6D6D), fontSize = 22.sp, fontWeight = FontWeight.Light)
                    }
                }
                Box {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("模型选项框") }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { modelExpanded = !modelExpanded }) {
                            Icon(
                                if (modelExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                null,
                                tint = Color(0xFF666666)
                            )
                        }
                    }
                    DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        if (modelNames.isEmpty()) {
                            DropdownMenuItem(text = { Text("暂无模型，请先导入") }, onClick = { modelExpanded = false })
                        } else {
                            modelNames.forEach { name ->
                                DropdownMenuItem(text = { Text(name) }, onClick = { modelExpanded = false; onSelectModel(name) })
                            }
                        }
                    }
                }
                Text("支持: .param/.bin (NCNN), .onnx (YOLOv8/v11/INT8)", fontSize = 12.sp, color = Color.Gray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("NNAPI 加速", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4C4C4C))
                        Text(
                            if (onnxNnapiEnabled) "开启后优先尝试 NNAPI/NPU，不支持时自动回退 CPU" else "已固定使用 CPU 推理",
                            fontSize = 11.sp,
                            color = Color(0xFF7A7A7A)
                        )
                    }
                    Switch(
                        checked = onnxNnapiEnabled,
                        onCheckedChange = onOnnxNnapiEnabledChange,
                        enabled = !running
                    )
                }
                Text("当前后端: ${OnnxEngine.activeExecutionProvider}", fontSize = 11.sp, color = Color(0xFF6A6A6A))
                Text("ONNX 状态: ${OnnxEngine.lastStatus}", fontSize = 11.sp, color = Color(0xFF6A6A6A))
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ONNX 运行日志", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF555555))
                        Text("最近 ${onnxRuntimeLogs.size} 条，会同步写入 Logcat/$ONNX_LOG_TAG", fontSize = 11.sp, color = Color(0xFF7A7A7A))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onClearOnnxLogs, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                            Text("清空", fontSize = 12.sp)
                        }
                        IconButton(onClick = { runtimeLogExpanded = !runtimeLogExpanded }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                if (runtimeLogExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                null,
                                tint = Color(0xFF666666)
                            )
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF202226)
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val visibleLogs = onnxRuntimeLogs.asReversed().take(if (runtimeLogExpanded) 12 else 5)
                        if (visibleLogs.isEmpty()) {
                            Text("暂无运行日志", color = Color(0xFFBFC6CF), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        } else {
                            visibleLogs.forEach { line ->
                                Text(
                                    line,
                                    color = Color(0xFFE4E8ED),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Settings, null, tint = Color(0xFF666666), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("检测参数", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF555555))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("置信 ${"%.2f".format(confidence)} | NMS ${"%.2f".format(nms)}", color = Color(0xFF666666), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { detectExpanded = !detectExpanded }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                if (detectExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                null,
                                tint = Color(0xFF666666)
                            )
                        }
                    }
                }
                AnimatedVisibility(visible = detectExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("置信度阈值", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("%.2f".format(confidence), fontWeight = FontWeight.Bold, color = Color(0xFF666666), fontSize = 22.sp)
                        }
                        Slider(
                            value = confidence,
                            onValueChange = onConfidenceChange,
                            valueRange = 0f..1f,
                            colors = sliderColors
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("NMS 阈值", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("%.2f".format(nms), fontWeight = FontWeight.Bold, color = Color(0xFF666666), fontSize = 22.sp)
                        }
                        Slider(
                            value = nms,
                            onValueChange = onNmsChange,
                            valueRange = 0f..1f,
                            colors = sliderColors
                        )
                    }
                }
            }
        }

        Text("传输协议", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = if (running) Color(0xFFE0E0E0) else Color(0xFFE7E7E7)
        ) {
            Row(Modifier.fillMaxWidth().padding(3.dp)) {
                ProtocolSegmentButton(
                    text = "UDP",
                    selected = isUdp,
                    enabled = !running,
                    modifier = Modifier.weight(1f)
                ) { onProtocolChange(true) }
                ProtocolSegmentButton(
                    text = "TCP",
                    selected = !isUdp,
                    enabled = !running,
                    modifier = Modifier.weight(1f)
                ) { onProtocolChange(false) }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("画面传输配置", color = Color(0xFF8A8A8A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("请在电脑端运行HX模拟副机专用TCP推流器", color = Color(0xFF666666), fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("本机 IP", color = Color(0xFF444444), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(localIp, color = Color(0xFF555555), fontWeight = FontWeight.Bold, fontSize = 28.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("监听端口", color = Color(0xFF444444), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(activePort.toString(), color = Color(0xFF555555), fontWeight = FontWeight.Bold, fontSize = 28.sp)
                }
            }
        }
    }
}

private data class BackendBadgeSpec(
    val label: String,
    val containerColor: Color,
    val contentColor: Color = Color.White
)

@Composable
private fun runtimeBackendBadges(): List<BackendBadgeSpec> {
    return when (RuntimeBridge.selectedModelKind) {
        ModelKind.ONNX -> listOf(
            BackendBadgeSpec(label = "ONNX", containerColor = Color(0xFF6E737A)),
            BackendBadgeSpec(
                label = OnnxEngine.activeExecutionProvider.ifBlank { "未加载" },
                containerColor = when (OnnxEngine.activeExecutionProvider.uppercase(Locale.ROOT)) {
                    "NNAPI" -> Color(0xFF2E7D32)
                    "CPU" -> Color(0xFF616161)
                    else -> Color(0xFF546E7A)
                }
            )
        )
        ModelKind.NCNN -> listOf(
            BackendBadgeSpec(label = "NCNN", containerColor = Color(0xFF1565C0)),
            BackendBadgeSpec(label = "NATIVE", containerColor = Color(0xFF546E7A))
        )
        ModelKind.FILE -> listOf(
            BackendBadgeSpec(label = "未加载模型", containerColor = Color(0xFF8A8A8A))
        )
    }
}

@Composable
private fun ExecutionBackendBadge(
    label: String,
    containerColor: Color,
    contentColor: Color = Color.White
) {
    Surface(color = containerColor, shape = RoundedCornerShape(6.dp)) {
        Text(
            label,
            color = contentColor,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MonitorToolButton(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Color(0xFF7F7F7F) else Color(0xFFE1E1E1))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) Color.White else Color(0xFF6A6A6A),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ProtocolSegmentButton(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                when {
                    !enabled && selected -> Color(0xFFBDBDBD)
                    selected -> Color(0xFF858585)
                    else -> Color.Transparent
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = if (enabled) Color.White else Color(0xFFEEEEEE),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            val textColor = when {
                !enabled && selected -> Color(0xFFF5F5F5)
                !enabled -> Color(0xFF9A9A9A)
                selected -> Color.White
                else -> Color(0xFF4A4A4A)
            }
            Text(text, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InputControlScreen(
    hotkeys: List<HotkeyConfig>,
    aimRangeEnabled: Boolean,
    aimRangePercent: Float,
    xKp: String,
    xKd: String,
    xSmooth: String,
    xDeadzone: String,
    xMaxOut: String,
    yKp: String,
    yKd: String,
    ySmooth: String,
    yDeadzone: String,
    yMaxOut: String,
    showPdOverlay: Boolean,
    showDeviceDebug: Boolean,
    enableStuckHoldRecovery: Boolean,
    onHotkeyChanged: (Int, HotkeyConfig) -> Unit,
    onAimRangeEnabledChange: (Boolean) -> Unit,
    onAimRangePercentChange: (Float) -> Unit,
    onXKpChange: (String) -> Unit,
    onXKdChange: (String) -> Unit,
    onXSmoothChange: (String) -> Unit,
    onXDeadzoneChange: (String) -> Unit,
    onXMaxOutChange: (String) -> Unit,
    onYKpChange: (String) -> Unit,
    onYKdChange: (String) -> Unit,
    onYSmoothChange: (String) -> Unit,
    onYDeadzoneChange: (String) -> Unit,
    onYMaxOutChange: (String) -> Unit,
    onShowPdOverlayChange: (Boolean) -> Unit,
    onShowDeviceDebugChange: (Boolean) -> Unit,
    onEnableStuckHoldRecoveryChange: (Boolean) -> Unit
) {
    val tabs = listOf("设备连接", "热键1", "热键2", "热键3", "控制参数")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Text("输入控制", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color(0xFFF5F5F5),
            divider = { HorizontalDivider(color = Color(0xFFE2E2E2)) },
            indicator = { tabPositions ->
                val page = pagerState.currentPage
                if (page < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[page]),
                        color = Color(0xFF737373),
                        height = 2.dp
                    )
                }
            }
        ) {
            tabs.forEachIndexed { i, t ->
                Tab(
                    selected = pagerState.currentPage == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text = {
                        Text(
                            t,
                            fontSize = 15.sp,
                            color = if (pagerState.currentPage == i) Color(0xFF2C2C2C) else Color(0xFF575757),
                            fontWeight = if (pagerState.currentPage == i) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true
        ) { page ->
            Column(
                Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (page) {
                    0 -> DeviceConnectTab(
                        showDeviceDebug = showDeviceDebug,
                        enableStuckHoldRecovery = enableStuckHoldRecovery,
                        onShowDeviceDebugChange = onShowDeviceDebugChange,
                        onEnableStuckHoldRecoveryChange = onEnableStuckHoldRecoveryChange
                    )
                    1, 2, 3 -> {
                        val idx = page - 1
                        val cfg = hotkeys.getOrElse(idx) { HotkeyConfig("热键${idx + 1}") }
                        HotkeyTab(cfg = cfg.copy(name = "热键${idx + 1}")) {
                            onHotkeyChanged(idx, it.copy(name = "热键${idx + 1}"))
                        }
                    }
                    else -> ControlParamTab(
                        aimRangeEnabled = aimRangeEnabled,
                        aimRangePercent = aimRangePercent,
                        xKp = xKp,
                        xKd = xKd,
                        xSmooth = xSmooth,
                        xDeadzone = xDeadzone,
                        xMaxOut = xMaxOut,
                        yKp = yKp,
                        yKd = yKd,
                        ySmooth = ySmooth,
                        yDeadzone = yDeadzone,
                        yMaxOut = yMaxOut,
                        showPdOverlay = showPdOverlay,
                        showDeviceDebug = showDeviceDebug,
                        enableStuckHoldRecovery = enableStuckHoldRecovery,
                        onAimRangeEnabledChange = onAimRangeEnabledChange,
                        onAimRangePercentChange = onAimRangePercentChange,
                        onXKpChange = onXKpChange,
                        onXKdChange = onXKdChange,
                        onXSmoothChange = onXSmoothChange,
                        onXDeadzoneChange = onXDeadzoneChange,
                        onXMaxOutChange = onXMaxOutChange,
                        onYKpChange = onYKpChange,
                        onYKdChange = onYKdChange,
                        onYSmoothChange = onYSmoothChange,
                        onYDeadzoneChange = onYDeadzoneChange,
                        onYMaxOutChange = onYMaxOutChange,
                        onShowPdOverlayChange = onShowPdOverlayChange,
                        onShowDeviceDebugChange = onShowDeviceDebugChange,
                        onEnableStuckHoldRecoveryChange = onEnableStuckHoldRecoveryChange
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceConnectTab(
    showDeviceDebug: Boolean,
    enableStuckHoldRecovery: Boolean,
    onShowDeviceDebugChange: (Boolean) -> Unit,
    onEnableStuckHoldRecoveryChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val usbManager = remember(context) { context.getSystemService(Context.USB_SERVICE) as? UsbManager }
    val linkState by MakcuLinkRuntime.state.collectAsState()
    var usbDevices by remember { mutableStateOf(listOf<UsbDevice>()) }
    var selectedDeviceId by rememberSaveable { mutableIntStateOf(-1) }
    var drawDelayMs by rememberSaveable { mutableFloatStateOf(MakcuTestConfig.drawStepDelayMs.toFloat()) }
    var calibrationRunning by rememberSaveable { mutableStateOf(false) }
    var calibrationStep by rememberSaveable { mutableIntStateOf(0) }
    var calibrationWaitRelease by rememberSaveable { mutableStateOf(true) }
    var calibrationPrevRawMask by rememberSaveable { mutableIntStateOf(0) }
    var calibrationReleaseDeadlineMs by rememberSaveable { mutableLongStateOf(0L) }
    var calibrationMessage by rememberSaveable { mutableStateOf("") }
    var mappingRevision by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val panelColor = Color(0xFFEFEFEF)
    val permissionIntent = remember(context) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // UsbManager.requestPermission may attach extras at send time on some ROMs.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        PendingIntent.getBroadcast(
            context,
            1001,
            Intent(USB_PERMISSION_ACTION).setPackage(context.packageName),
            flags
        )
    }
    LaunchedEffect(context) {
        MouseButtonMapper.ensureLoaded(context)
        mappingRevision++
    }
    LaunchedEffect(drawDelayMs) {
        MakcuTestConfig.drawStepDelayMs = drawDelayMs.roundToInt().coerceIn(2, 22).toLong()
    }

    fun refreshUsbDevices(): List<UsbDevice> {
        val latest = runCatching { usbManager?.deviceList?.values?.toList().orEmpty().sortedBy { it.deviceId } }.getOrDefault(emptyList())
        usbDevices = latest
        val keepCurrent = latest.any { it.deviceId == selectedDeviceId && (usbManager?.hasPermission(it) == true) }
        if (!keepCurrent) {
            selectedDeviceId = latest.firstOrNull { usbManager?.hasPermission(it) == true }?.deviceId ?: -1
        }
        return latest
    }

    fun requestPermission(device: UsbDevice) {
        if (usbManager?.hasPermission(device) == true) {
            selectedDeviceId = device.deviceId
            return
        }
        runCatching {
            usbManager?.requestPermission(device, permissionIntent)
        }.onFailure { t ->
            val detail = t.message?.take(80)?.ifBlank { null } ?: t.javaClass.simpleName
            MakcuLinkRuntime.updateStatus("USB 授权请求异常: $detail")
            Toast.makeText(context, "USB 授权请求异常: $detail", Toast.LENGTH_SHORT).show()
        }
    }

    fun tryAutoPermission(devices: List<UsbDevice>) {
        val target = devices.firstOrNull { usbManager?.hasPermission(it) != true } ?: return
        requestPermission(target)
    }

    fun sendDrawPattern(pattern: MakcuDrawPattern) {
        val manager = usbManager ?: run {
            Toast.makeText(context, "当前设备不支持 USB 管理器", Toast.LENGTH_SHORT).show()
            return
        }
        if (calibrationRunning) {
            calibrationRunning = false
            calibrationWaitRelease = true
            calibrationPrevRawMask = 0
            calibrationMessage = "已停止校准，开始轨迹测试"
            MakcuLinkRuntime.recoverMakcuAfterCalibration()
        }
        val activeDeviceId = if (selectedDeviceId >= 0) selectedDeviceId else linkState.deviceId
        val device = usbDevices.firstOrNull { it.deviceId == activeDeviceId && manager.hasPermission(it) } ?: run {
            Toast.makeText(context, "未连接可发送命令的 USB 设备", Toast.LENGTH_SHORT).show()
            return
        }
        val session = MakcuLinkRuntime.session()
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (MakcuSerialEngine.isConnected()) {
                        MakcuSerialEngine.sendCommandExact("km.bypass(0)")
                        MakcuSerialEngine.sendCommandExact("bypass(0)")
                        MakcuSerialEngine.primeButtonsStream()
                    }
                    if (MakcuSerialEngine.isConnected()) {
                        sendMakcuDrawPatternSerial(pattern)
                    } else if (session != null && session.isHandshakeVerified) {
                        val direct = sendMakcuDrawPattern(session, pattern)
                        if (direct.success) direct else sendMakcuDrawPatternBroadcast(manager, device, pattern, preferred = session)
                    } else {
                        sendMakcuDrawPatternBroadcast(manager, device, pattern, preferred = session)
                    }
                }
                val status = if (result.success) "最近发送: ${result.message}" else "发送失败: ${result.message}"
                MakcuLinkRuntime.updateStatus(status)
                Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                val detail = t.message?.take(80)?.ifBlank { null } ?: t.javaClass.simpleName
                val status = "发送异常: $detail"
                MakcuLinkRuntime.updateStatus(status)
                Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startCalibration() {
        if (!linkState.connected) {
            Toast.makeText(context, "请先连接 MAKCU 设备", Toast.LENGTH_SHORT).show()
            return
        }
        calibrationRunning = true
        calibrationStep = 0
        calibrationWaitRelease = true
        calibrationPrevRawMask = linkState.rawButtonMask and 0xFFFF
        calibrationReleaseDeadlineMs = System.currentTimeMillis() + 1300L
        calibrationMessage = "校准开始: 先松开按键(若无法归零会自动继续)"
        MakcuLinkRuntime.requestButtonsSnapshot()
    }

    fun stopCalibration(msg: String) {
        calibrationRunning = false
        calibrationWaitRelease = true
        calibrationMessage = msg
        calibrationPrevRawMask = 0
        MakcuLinkRuntime.recoverMakcuAfterCalibration()
    }

    fun connectSelectedDevice() {
        val manager = usbManager ?: return
        val device = usbDevices.firstOrNull { it.deviceId == selectedDeviceId && manager.hasPermission(it) }
            ?: usbDevices.firstOrNull { it.deviceId == linkState.deviceId && manager.hasPermission(it) }
            ?: usbDevices.firstOrNull { manager.hasPermission(it) }
            ?: run {
            MakcuLinkRuntime.updateStatus("未找到可连接设备或尚未授权")
            return
        }
        selectedDeviceId = device.deviceId
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    MakcuLinkRuntime.connect(manager, device)
                }
            }.onFailure { t ->
                val detail = t.message?.take(80)?.ifBlank { null } ?: t.javaClass.simpleName
                MakcuLinkRuntime.updateStatus("连接异常: $detail")
                Toast.makeText(context, "连接异常: $detail", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(context, usbManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                runCatching {
                    when (intent?.action) {
                        USB_PERMISSION_ACTION -> {
                            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            val latest = refreshUsbDevices()
                            if (granted) {
                                val preferred = latest.firstOrNull { it.deviceId == selectedDeviceId && (usbManager?.hasPermission(it) == true) }
                                    ?: latest.firstOrNull { usbManager?.hasPermission(it) == true }
                                if (preferred != null) {
                                    selectedDeviceId = preferred.deviceId
                                }
                                MakcuLinkRuntime.updateStatus("USB 授权成功，请点击连接")
                                Toast.makeText(context, "USB 授权成功", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "USB 授权被拒绝", Toast.LENGTH_SHORT).show()
                            }
                        }
                        UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                            val attached = intentUsbDevice(intent)
                            val latest = refreshUsbDevices()
                            if (attached != null && usbManager?.hasPermission(attached) != true) {
                                requestPermission(attached)
                            } else {
                                tryAutoPermission(latest)
                            }
                        }
                        UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                            refreshUsbDevices()
                        }
                    }
                }.onFailure { t ->
                    val detail = t.message?.take(80)?.ifBlank { null } ?: t.javaClass.simpleName
                    MakcuLinkRuntime.updateStatus("USB 广播异常: $detail")
                    Toast.makeText(context, "USB 广播异常: $detail", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(USB_PERMISSION_ACTION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    LaunchedEffect(Unit) {
        val latest = refreshUsbDevices()
        tryAutoPermission(latest)
    }

    val usbCount = usbDevices.size
    LaunchedEffect(linkState.deviceId) {
        if (linkState.deviceId >= 0) selectedDeviceId = linkState.deviceId
    }
    val selectedDevice = usbDevices.firstOrNull { it.deviceId == selectedDeviceId && (usbManager?.hasPermission(it) == true) }
        ?: usbDevices.firstOrNull { it.deviceId == linkState.deviceId && (usbManager?.hasPermission(it) == true) }
    val connected = linkState.connected
    val rawMaskHex = "0x${(linkState.rawButtonMask and 0xFFFF).toString(16).uppercase(Locale.US)}"
    val mappedMaskHex = "0x${(linkState.buttonMask and 0x1F).toString(16).uppercase(Locale.US)}"
    LaunchedEffect(usbDevices.map { it.deviceId }, linkState.deviceId) {
        if (linkState.deviceId >= 0 && usbDevices.none { it.deviceId == linkState.deviceId }) {
            MakcuLinkRuntime.disconnect(reason = "设备已断开")
        }
    }
    LaunchedEffect(connected) {
        if (!connected && calibrationRunning) {
            stopCalibration("连接已断开，校准中止")
        }
    }
    LaunchedEffect(calibrationRunning, connected, calibrationWaitRelease) {
        if (!calibrationRunning || !connected) return@LaunchedEffect
        while (isActive && calibrationRunning && connected) {
            MakcuLinkRuntime.requestButtonsSnapshot()
            delay(if (calibrationWaitRelease) 90 else 55)
        }
    }
    LaunchedEffect(calibrationRunning, calibrationStep, calibrationWaitRelease, linkState.rawButtonMask, connected) {
        if (!calibrationRunning || !connected) return@LaunchedEffect
        if (calibrationStep >= MOUSE_BUTTON_INDICATORS.size) {
            stopCalibration("按键位校准完成")
            return@LaunchedEffect
        }
        val rawMask = linkState.rawButtonMask and 0xFFFF
        if (calibrationWaitRelease) {
            val now = System.currentTimeMillis()
            if (rawMask == 0 || now >= calibrationReleaseDeadlineMs) {
                calibrationWaitRelease = false
                calibrationMessage = "请按下【${MOUSE_BUTTON_INDICATORS[calibrationStep].label}】"
                calibrationPrevRawMask = rawMask
            }
            return@LaunchedEffect
        }
        val risingMask = rawMask and calibrationPrevRawMask.inv()
        calibrationPrevRawMask = rawMask
        if (risingMask == 0) return@LaunchedEffect
        val usedBits = MouseButtonMapper.snapshot()
            .take(calibrationStep)
            .fold(0) { acc, bit -> acc or (bit and 0xFFFF) }
        val capturedBit = pickPrimaryRawButtonBit(risingMask, usedBits)
        if (capturedBit == 0) return@LaunchedEffect
        val saved = MouseButtonMapper.setRawBitForLogical(context, calibrationStep, capturedBit)
        val label = MOUSE_BUTTON_INDICATORS[calibrationStep].label
        if (saved) {
            mappingRevision++
            calibrationStep++
            calibrationWaitRelease = true
            calibrationReleaseDeadlineMs = System.currentTimeMillis() + 1300L
            calibrationMessage = "已记录 $label -> 0x${capturedBit.toString(16).uppercase(Locale.US)}，请松开按键"
            if (calibrationStep >= MOUSE_BUTTON_INDICATORS.size) {
                stopCalibration("按键位校准完成")
            }
        } else {
            calibrationMessage = "记录 $label 失败，请重试"
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = panelColor), shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("●", color = if (connected) Color(0xFF4CAF50) else Color(0xFFC5C5C5), fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            Text(if (connected) "已连接" else "未连接", fontWeight = FontWeight.SemiBold, color = Color(0xFF4A4A4A), fontSize = 16.sp)
        }
    }

    if (selectedDevice != null) {
        Card(colors = CardDefaults.cardColors(containerColor = panelColor), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (connected) "已连接设备" else "当前设备", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF333333))
                    if (connected) {
                        IconButton(onClick = {
                            selectedDeviceId = -1
                            MakcuLinkRuntime.disconnect(reason = "已断开")
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.LinkOff, null, tint = Color(0xFFD32F2F))
                        }
                    } else {
                        TextButton(onClick = { connectSelectedDevice() }) {
                            Text("连接", color = Color(0xFF2E7D32), fontSize = 12.sp)
                        }
                    }
                }
                Text(usbConnectedName(selectedDevice), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF333333))
                Text(usbVidPid(selectedDevice), fontSize = 12.sp, color = Color(0xFF666666))
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = panelColor), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("设备监控与测试", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF333333))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MOUSE_BUTTON_INDICATORS.forEach { item ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = if ((linkState.buttonMask and item.mask) != 0) Color(0xFFCFDDCE) else Color(0xFFF5F5F5),
                                border = BorderStroke(1.dp, Color(0xFFBFBFBF)),
                                modifier = Modifier.size(26.dp)
                            ) {}
                            Spacer(Modifier.height(4.dp))
                            Text(
                                item.label,
                                fontSize = 11.sp,
                                color = if ((linkState.buttonMask and item.mask) != 0) Color(0xFF2E7D32) else Color(0xFF555555)
                            )
                        }
                    }
                }

                val pressedNames = MOUSE_BUTTON_INDICATORS
                    .filter { (linkState.buttonMask and it.mask) != 0 }
                    .joinToString("、") { it.label }
                Text(
                    text = if (pressedNames.isBlank()) "未检测到鼠标按键按下" else "检测到按下: $pressedNames",
                    fontSize = 11.sp,
                    color = Color(0xFF5E5E5E)
                )
                Text(
                    text = "RAW=$rawMaskHex  映射后=$mappedMaskHex",
                    fontSize = 11.sp,
                    color = Color(0xFF616161)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF5F5F5),
                        border = BorderStroke(1.dp, Color(0xFFDDDDDD))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("调试模式", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF454545))
                                Text("显示解析来源和最近 RX 摘要", fontSize = 10.sp, color = Color(0xFF757575))
                            }
                            InputGraySwitch(checked = showDeviceDebug, onCheckedChange = onShowDeviceDebugChange)
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF5F5F5),
                        border = BorderStroke(1.dp, Color(0xFFDDDDDD))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("安全回退", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF454545))
                                Text("长按卡住时尝试自动清零", fontSize = 10.sp, color = Color(0xFF757575))
                            }
                            InputGraySwitch(checked = enableStuckHoldRecovery, onCheckedChange = onEnableStuckHoldRecoveryChange)
                        }
                    }
                }
                if (showDeviceDebug && linkState.debugInfo.isNotBlank()) {
                    Text(
                        text = "解析调试: ${linkState.debugInfo}",
                        fontSize = 10.sp,
                        color = Color(0xFF757575)
                    )
                }

                HorizontalDivider(color = Color(0xFFDADADA), thickness = 1.dp)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { sendDrawPattern(MakcuDrawPattern.Square) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFBDBDBD))
                    ) { Text("画正方形", fontSize = 12.sp, color = Color(0xFF555555)) }
                    OutlinedButton(
                        onClick = { sendDrawPattern(MakcuDrawPattern.Circle) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFBDBDBD))
                    ) { Text("画圆形", fontSize = 12.sp, color = Color(0xFF555555)) }
                }
                LabeledGraySlider(
                    title = "轨迹速度 (越快数值越小)",
                    valueLabel = "${drawDelayMs.roundToInt().coerceIn(2, 22)} ms/步",
                    value = drawDelayMs,
                    onValueChange = { drawDelayMs = it },
                    valueRange = 2f..22f
                )

                HorizontalDivider(color = Color(0xFFDADADA), thickness = 1.dp)

                val mapping = remember(mappingRevision, linkState.rawButtonMask, linkState.buttonMask) {
                    MouseButtonMapper.snapshot()
                }
                val mappingText = MOUSE_BUTTON_INDICATORS.indices.joinToString("  ") { index ->
                    val logical = MOUSE_BUTTON_INDICATORS[index].label
                    val raw = mapping.getOrElse(index) { MOUSE_BUTTON_INDICATORS[index].mask }
                    "$logical->0x${raw.toString(16).uppercase(Locale.US)}"
                }
                Text(
                    text = "按键位映射: $mappingText",
                    fontSize = 11.sp,
                    color = Color(0xFF616161)
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (calibrationRunning) {
                                stopCalibration("已取消按键位校准")
                            } else {
                                startCalibration()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (calibrationRunning) Color(0xFFD32F2F) else Color(0xFF5F6D7A),
                            contentColor = Color.White
                        )
                    ) {
                        val title = if (calibrationRunning) {
                            val progress = (calibrationStep + 1).coerceAtMost(MOUSE_BUTTON_INDICATORS.size)
                            "停止校准($progress/5)"
                        } else {
                            "按键位校准"
                        }
                        Text(title, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            MouseButtonMapper.resetDefault(context)
                            mappingRevision++
                            stopCalibration("已恢复默认映射")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFBDBDBD))
                    ) { Text("恢复默认", fontSize = 12.sp, color = Color(0xFF555555)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { MakcuLinkRuntime.requestButtonsSnapshot() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFBDBDBD))
                    ) { Text("主动轮询按键", fontSize = 12.sp, color = Color(0xFF555555)) }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (calibrationRunning) Color(0xFFE8F2FF) else Color(0xFFF5F5F5),
                        border = BorderStroke(1.dp, Color(0xFFDDDDDD))
                    ) {
                        val info = if (calibrationMessage.isNotBlank()) {
                            calibrationMessage
                        } else {
                            "准备就绪"
                        }
                        Text(
                            info,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            fontSize = 11.sp,
                            color = if (calibrationRunning) Color(0xFF174E8B) else Color(0xFF616161)
                        )
                    }
                }

                if (linkState.status.isNotBlank()) {
                    Text(
                        linkState.status,
                        fontSize = 11.sp,
                        color = if (linkState.status.contains("失败") || linkState.status.contains("异常")) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                    )
                }
            }
        }
        return
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("USB 诊断", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF3A3A3A))
        Button(
            onClick = {
                val latest = refreshUsbDevices()
                tryAutoPermission(latest)
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F7F7F), contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text("刷新", fontSize = 13.sp)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = panelColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✓  USB Host: 支持", fontSize = 14.sp, color = Color(0xFF333333))
            Text("部分手机需在设置中开启 OTG 功能", color = Color(0xFF5F5F5F), fontSize = 12.sp)
            Text("检测到设备: $usbCount 个", fontSize = 14.sp, color = Color(0xFF333333))
            Text("${Build.MODEL} / Android ${Build.VERSION.RELEASE}", fontSize = 12.sp, color = Color(0xFF5E5E5E))
        }
    }

    Text("点击设备进行连接", fontSize = 12.sp, color = Color(0xFF666666))

    usbDevices.forEach { device ->
        val hasPermission = usbManager?.hasPermission(device) == true
        Card(
            colors = CardDefaults.cardColors(containerColor = panelColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().clickable {
                if (hasPermission) {
                    selectedDeviceId = device.deviceId
                } else {
                    requestPermission(device)
                }
            }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(usbListName(device), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF404040))
                    Text(usbVidPid(device), fontSize = 12.sp, color = Color(0xFF666666))
                }
                Text(
                    text = if (hasPermission) "已授权" else "授权",
                    color = if (hasPermission) Color(0xFF7A7A7A) else Color(0xFF2E7D32),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !hasPermission) { requestPermission(device) }
                )
            }
        }
    }
}

@Composable
private fun HotkeyTab(cfg: HotkeyConfig, onChange: (HotkeyConfig) -> Unit) {
    val panelColor = Color(0xFFEFEFEF)
    val triggerOptions = listOf("左", "右", "中", "上侧", "下侧")
    val classColors = listOf(Color(0xFFE57373), Color(0xFF4DB6AC), Color(0xFF64B5F6), Color(0xFFFFB74D))
    val enabledCategories = if (cfg.enabledCategories.size == 4) cfg.enabledCategories else listOf(true, true, true, true)
    val categoryOrder = normalizeCategoryOrder(cfg.categoryOrder)
    val yOffset = if (cfg.yOffset.size == 4) cfg.yOffset.map { it.coerceIn(0, 100) } else listOf(79, 50, 0, 0)

    Card(colors = CardDefaults.cardColors(containerColor = panelColor), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${cfg.name}开关", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF3D3D3D))
                    Text(if (cfg.enabled) "已启用" else "已禁用", color = Color(0xFF717171), fontSize = 12.sp)
                }
                InputGraySwitch(checked = cfg.enabled, onCheckedChange = { onChange(cfg.copy(enabled = it)) })
            }

            Text("触发按键", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF444444))
            InputSegmentedControl(
                options = triggerOptions,
                selectedIndex = triggerOptions.indexOf(cfg.trigger).takeIf { it >= 0 } ?: 1,
                onSelect = { index -> onChange(cfg.copy(trigger = triggerOptions[index])) },
                fontSize = 13.sp
            )

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F3F3)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("自动瞄准", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF3C3C3C))
                        InputGraySwitch(checked = cfg.autoAim, onCheckedChange = { onChange(cfg.copy(autoAim = it)) })
                    }
                    AnimatedVisibility(visible = cfg.autoAim, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(color = Color(0xFFDADADA), thickness = 1.dp)

                            Text("目标选择模式", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF444444))
                            InputSegmentedControl(
                                options = listOf("最近准星", "类别优先"),
                                selectedIndex = if (cfg.aimMode == "recent_crosshair") 0 else 1,
                                onSelect = { index ->
                                    onChange(cfg.copy(aimMode = if (index == 0) "recent_crosshair" else "class_priority"))
                                },
                                fontSize = 13.sp
                            )

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("瞄准类别", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF3C3C3C))
                                InputGraySwitch(checked = cfg.aimByCategory, onCheckedChange = { onChange(cfg.copy(aimByCategory = it)) })
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                repeat(4) { index ->
                                    val color = classColors[index]
                                    val enabled = enabledCategories[index]
                                    Surface(
                                        modifier = Modifier.weight(1f).clickable(enabled = cfg.aimByCategory) {
                                            val next = enabledCategories.toMutableList()
                                            next[index] = !next[index]
                                            onChange(cfg.copy(enabledCategories = next))
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = color.copy(alpha = if (enabled && cfg.aimByCategory) 0.26f else 0.10f),
                                        border = BorderStroke(1.dp, color)
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("●", color = color, fontSize = 16.sp)
                                            Spacer(Modifier.width(4.dp))
                                            Text("类$index", color = Color(0xFF414141), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }

                            if (cfg.aimMode == "class_priority") {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("类别优先级", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF3C3C3C))
                                        Text("长按拖拽调整顺序", color = Color(0xFF757575), fontSize = 12.sp)
                                    }
                                    InputGraySwitch(
                                        checked = cfg.categoryPriorityEnabled,
                                        onCheckedChange = { onChange(cfg.copy(categoryPriorityEnabled = it)) }
                                    )
                                }
                                AnimatedVisibility(visible = cfg.categoryPriorityEnabled, enter = expandVertically(), exit = shrinkVertically()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        categoryOrder.forEachIndexed { index, klass ->
                                            Surface(color = Color(0xFFF8F8F8), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                                Row(
                                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("${index + 1}.  类别 $klass", fontSize = 13.sp, color = Color(0xFF444444))
                                                    Text(
                                                        "☰",
                                                        fontSize = 16.sp,
                                                        color = Color(0xFF5A5A5A),
                                                        modifier = Modifier.clickable {
                                                            if (index > 0) {
                                                                val next = categoryOrder.toMutableList()
                                                                val tmp = next[index - 1]
                                                                next[index - 1] = next[index]
                                                                next[index] = tmp
                                                                onChange(cfg.copy(categoryOrder = next))
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (cfg.aimByCategory) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                        Text("Y轴偏移", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF424242))
                                        Text("0%=头 100%=脚", color = Color(0xFF6E6E6E), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    repeat(4) { index ->
                                        val color = classColors[index]
                                        val value = yOffset[index]
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Row(Modifier.width(78.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text("●", color = color, fontSize = 12.sp)
                                                Spacer(Modifier.width(4.dp))
                                                Text("类别$index", fontSize = 12.sp, color = Color(0xFF4A4A4A))
                                            }
                                            Slider(
                                                value = value.toFloat(),
                                                onValueChange = {
                                                    val next = yOffset.toMutableList()
                                                    next[index] = it.roundToInt().coerceIn(0, 100)
                                                    onChange(cfg.copy(yOffset = next))
                                                },
                                                valueRange = 0f..100f,
                                                modifier = Modifier.weight(1f),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = color,
                                                    activeTrackColor = color.copy(alpha = 0.78f),
                                                    inactiveTrackColor = color.copy(alpha = 0.22f)
                                                )
                                            )
                                            Text(
                                                "${value}%",
                                                modifier = Modifier.width(48.dp),
                                                textAlign = TextAlign.End,
                                                color = Color(0xFF5C5C5C),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F3F3)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("自动射击", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF3C3C3C))
                        InputGraySwitch(checked = cfg.autoFire, onCheckedChange = { onChange(cfg.copy(autoFire = it)) })
                    }
                    AnimatedVisibility(visible = cfg.autoFire, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = Color(0xFFDADADA), thickness = 1.dp)
                            LabeledGraySlider(
                                title = "射击范围 (px)",
                                valueLabel = "%.1f".format(cfg.fireRangePx),
                                value = cfg.fireRangePx,
                                onValueChange = { onChange(cfg.copy(fireRangePx = it.coerceIn(0f, 30f))) },
                                valueRange = 0f..30f
                            )
                            LabeledGraySlider(
                                title = "初始延迟 (ms)",
                                valueLabel = cfg.initialDelayMs.toString(),
                                value = cfg.initialDelayMs.toFloat(),
                                onValueChange = { onChange(cfg.copy(initialDelayMs = it.roundToInt().coerceIn(0, 500))) },
                                valueRange = 0f..500f
                            )

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("最小点击", fontSize = 13.sp, color = Color(0xFF4B4B4B))
                                Text("最大点击", fontSize = 13.sp, color = Color(0xFF4B4B4B))
                            }
                            RangeSlider(
                                value = cfg.minClick.toFloat()..cfg.maxClick.toFloat(),
                                onValueChange = { range ->
                                    val min = range.start.roundToInt().coerceIn(1, 10)
                                    val max = range.endInclusive.roundToInt().coerceIn(min, 10)
                                    onChange(cfg.copy(minClick = min, maxClick = max))
                                },
                                valueRange = 1f..10f,
                                steps = 8
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(cfg.minClick.toString(), fontWeight = FontWeight.Bold, color = Color(0xFF5D5D5D), fontSize = 13.sp)
                                Text(cfg.maxClick.toString(), fontWeight = FontWeight.Bold, color = Color(0xFF5D5D5D), fontSize = 13.sp)
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    LabeledGraySlider(
                                        title = "间隔最小 (ms)",
                                        valueLabel = cfg.minIntervalMs.toString(),
                                        value = cfg.minIntervalMs.toFloat(),
                                        onValueChange = {
                                            val min = it.roundToInt().coerceIn(10, cfg.maxIntervalMs)
                                            onChange(cfg.copy(minIntervalMs = min))
                                        },
                                        valueRange = 10f..500f
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    LabeledGraySlider(
                                        title = "间隔最大 (ms)",
                                        valueLabel = cfg.maxIntervalMs.toString(),
                                        value = cfg.maxIntervalMs.toFloat(),
                                        onValueChange = {
                                            val max = it.roundToInt().coerceIn(cfg.minIntervalMs, 500)
                                            onChange(cfg.copy(maxIntervalMs = max))
                                        },
                                        valueRange = 10f..500f
                                    )
                                }
                            }
                            LabeledGraySlider(
                                title = "连射间隔 (ms)",
                                valueLabel = cfg.burstIntervalMs.toString(),
                                value = cfg.burstIntervalMs.toFloat(),
                                onValueChange = { onChange(cfg.copy(burstIntervalMs = it.roundToInt().coerceIn(10, 1000))) },
                                valueRange = 10f..1000f
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlParamTab(
    aimRangeEnabled: Boolean,
    aimRangePercent: Float,
    onAimRangeEnabledChange: (Boolean) -> Unit,
    onAimRangePercentChange: (Float) -> Unit,
    xKp: String,
    xKd: String,
    xSmooth: String,
    xDeadzone: String,
    xMaxOut: String,
    yKp: String,
    yKd: String,
    ySmooth: String,
    yDeadzone: String,
    yMaxOut: String,
    showPdOverlay: Boolean,
    showDeviceDebug: Boolean,
    enableStuckHoldRecovery: Boolean,
    onXKpChange: (String) -> Unit,
    onXKdChange: (String) -> Unit,
    onXSmoothChange: (String) -> Unit,
    onXDeadzoneChange: (String) -> Unit,
    onXMaxOutChange: (String) -> Unit,
    onYKpChange: (String) -> Unit,
    onYKdChange: (String) -> Unit,
    onYSmoothChange: (String) -> Unit,
    onYDeadzoneChange: (String) -> Unit,
    onYMaxOutChange: (String) -> Unit,
    onShowPdOverlayChange: (Boolean) -> Unit,
    onShowDeviceDebugChange: (Boolean) -> Unit,
    onEnableStuckHoldRecoveryChange: (Boolean) -> Unit
) {
    val panelColor = Color(0xFFEFEFEF)
    var diagnostics by remember { mutableStateOf(PdRuntimeDiagnostics.snapshot()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            diagnostics = PdRuntimeDiagnostics.snapshot()
            delay(120)
        }
    }

    Text("PD 控制参数", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF4A4A4A))
    Text("参数需要由小到大慢慢微调，建议先只调整 Kp 和 Kd", color = Color(0xFF747474), fontSize = 12.sp)

    PdAxisPanel(
        title = "X 轴（水平）",
        panelColor = panelColor,
        kp = xKp,
        kd = xKd,
        smooth = xSmooth,
        deadzone = xDeadzone,
        maxOut = xMaxOut,
        onKpChange = onXKpChange,
        onKdChange = onXKdChange,
        onSmoothChange = onXSmoothChange,
        onDeadzoneChange = onXDeadzoneChange,
        onMaxOutChange = onXMaxOutChange
    )

    PdAxisPanel(
        title = "Y 轴（垂直）",
        panelColor = panelColor,
        kp = yKp,
        kd = yKd,
        smooth = ySmooth,
        deadzone = yDeadzone,
        maxOut = yMaxOut,
        onKpChange = onYKpChange,
        onKdChange = onYKdChange,
        onSmoothChange = onYSmoothChange,
        onDeadzoneChange = onYDeadzoneChange,
        onMaxOutChange = onYMaxOutChange
    )

    Card(colors = CardDefaults.cardColors(containerColor = panelColor), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("实时诊断与安全", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF3F3F3F))
            Text(
                "overlay 用于在画面上直接看误差、PD 输出和自动射击状态；调试模式用于设备解析排查。",
                color = Color(0xFF777777),
                fontSize = 12.sp
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PD 诊断 Overlay", fontWeight = FontWeight.SemiBold, color = Color(0xFF474747))
                    Text("在预览画面叠加 err/pd/move/fire 状态", color = Color(0xFF777777), fontSize = 12.sp)
                }
                InputGraySwitch(checked = showPdOverlay, onCheckedChange = onShowPdOverlayChange)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("设备调试模式", fontWeight = FontWeight.SemiBold, color = Color(0xFF474747))
                    Text("显示 parser 来源和最近 RX 摘要", color = Color(0xFF777777), fontSize = 12.sp)
                }
                InputGraySwitch(checked = showDeviceDebug, onCheckedChange = onShowDeviceDebugChange)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("卡住长按安全回退", fontWeight = FontWeight.SemiBold, color = Color(0xFF474747))
                    Text("持续无新输入时尝试清零并重启按键流", color = Color(0xFF777777), fontSize = 12.sp)
                }
                InputGraySwitch(checked = enableStuckHoldRecovery, onCheckedChange = onEnableStuckHoldRecoveryChange)
            }

            HorizontalDivider(color = Color(0xFFDADADA), thickness = 1.dp)

            val targetLabel = if (diagnostics.targetId >= 0) diagnostics.targetId.toString() else "--"
            val clampLabel = buildString {
                append(if (diagnostics.clampX) "X" else "-")
                append("/")
                append(if (diagnostics.clampY) "Y" else "-")
            }
            Text("运行时快照", fontWeight = FontWeight.SemiBold, color = Color(0xFF505050))
            Text(
                "target=$targetLabel  active=${if (diagnostics.active) "yes" else "no"}  fire=${diagnostics.autoFireState}  inRange=${if (diagnostics.inFireRange) "yes" else "no"}",
                fontSize = 12.sp,
                color = Color(0xFF666666)
            )
            Text(
                "errX=${"%.1f".format(diagnostics.errX)}  errY=${"%.1f".format(diagnostics.errY)}  pdX=${"%.2f".format(diagnostics.pdOutX)}  pdY=${"%.2f".format(diagnostics.pdOutY)}",
                fontSize = 12.sp,
                color = Color(0xFF666666)
            )
            Text(
                "smoothX=${"%.2f".format(diagnostics.smoothOutX)}  smoothY=${"%.2f".format(diagnostics.smoothOutY)}  moveX=${diagnostics.moveX}  moveY=${diagnostics.moveY}  clamp=$clampLabel",
                fontSize = 12.sp,
                color = Color(0xFF666666)
            )
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = panelColor), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("瞄准范围限制", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF3F3F3F))
                    Text("只瞄准屏幕中心圆圈内的目标", fontSize = 12.sp, color = Color(0xFF717171))
                }
                InputGraySwitch(checked = aimRangeEnabled, onCheckedChange = onAimRangeEnabledChange)
            }
            AnimatedVisibility(visible = aimRangeEnabled, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("范围半径", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4A4A4A))
                        Text("${aimRangePercent.roundToInt()}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6D6D6D))
                    }
                    Text("相对于画面短边一半的百分比", color = Color(0xFF777777), fontSize = 12.sp)
                    Slider(
                        value = aimRangePercent,
                        onValueChange = { onAimRangePercentChange(it.coerceIn(0f, 100f)) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF7A7A7A),
                            activeTrackColor = Color(0xFF7A7A7A),
                            inactiveTrackColor = Color(0xFFE4E4E4)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun InputGraySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color(0xFFF7F7F7),
            checkedTrackColor = Color(0xFF848484),
            uncheckedThumbColor = Color(0xFFD3D3D3),
            uncheckedTrackColor = Color(0xFFCACACA)
        )
    )
}

@Composable
private fun InputSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    Surface(color = Color(0xFFE4E4E4), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, text ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (selectedIndex == index) Color(0xFF848484) else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (if (selectedIndex == index) "✓ " else "") + text,
                        color = if (selectedIndex == index) Color.White else Color(0xFF474747),
                        fontSize = fontSize,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledGraySlider(
    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.sp, color = Color(0xFF4A4A4A))
            Text(valueLabel, fontSize = 14.sp, color = Color(0xFF666666), fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7A7A7A),
                activeTrackColor = Color(0xFF7A7A7A),
                inactiveTrackColor = Color(0xFFE4E4E4)
            )
        )
    }
}

@Composable
private fun PdAxisPanel(
    title: String,
    panelColor: Color,
    kp: String,
    kd: String,
    smooth: String,
    deadzone: String,
    maxOut: String,
    onKpChange: (String) -> Unit,
    onKdChange: (String) -> Unit,
    onSmoothChange: (String) -> Unit,
    onDeadzoneChange: (String) -> Unit,
    onMaxOutChange: (String) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = panelColor), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF5D5D5D), fontSize = 15.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PdValueField(label = "比例增益 Kp", value = kp, onValueChange = onKpChange, modifier = Modifier.weight(1f))
                PdValueField(label = "微分增益 Kd", value = kd, onValueChange = onKdChange, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PdValueField(label = "平滑系数", value = smooth, onValueChange = onSmoothChange, modifier = Modifier.weight(1f))
                PdValueField(label = "死区", value = deadzone, onValueChange = onDeadzoneChange, suffix = "px", modifier = Modifier.weight(1f))
            }
            PdValueField(label = "最大输出", value = maxOut, onValueChange = onMaxOutChange, suffix = "px", modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PdValueField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label, fontSize = 15.sp) },
        singleLine = true,
        trailingIcon = if (suffix.isNotBlank()) ({ Text(suffix, color = Color(0xFF757575), fontSize = 14.sp) }) else null
    )
}

private data class MouseButtonIndicator(
    val label: String,
    val mask: Int
)

private val MOUSE_BUTTON_INDICATORS = listOf(
    MouseButtonIndicator("左", 0x01),
    MouseButtonIndicator("右", 0x02),
    MouseButtonIndicator("中", 0x04),
    MouseButtonIndicator("上侧", 0x08),
    MouseButtonIndicator("下侧", 0x10)
)

private object MakcuTestConfig {
    @Volatile var drawStepDelayMs: Long = 4L
}

private fun isValidRawButtonCode(value: Int): Boolean {
    if (value <= 0) return false
    if (value > 0xFFFF) return false
    return true
}

private fun isSingleBitMask(value: Int): Boolean {
    if (value <= 0) return false
    return (value and (value - 1)) == 0
}

private object MouseButtonMapper {
    private const val PREF_KEY = "makcu_button_map_v1"
    private val lock = Any()
    private val defaultRawBits = intArrayOf(0x01, 0x02, 0x04, 0x08, 0x10)
    @Volatile private var logicalToRawBits = defaultRawBits.copyOf()
    @Volatile private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val prefs = context.getSharedPreferences(AUTO_CONFIG_PREFS, Context.MODE_PRIVATE)
            val saved = prefs.getString(PREF_KEY, null)
            val parsed = saved
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.takeIf { values ->
                    values.size == defaultRawBits.size &&
                        values.all { value -> isValidRawButtonCode(value) }
                }
            logicalToRawBits = parsed?.toIntArray() ?: defaultRawBits.copyOf()
            loaded = true
        }
    }

    fun snapshot(): IntArray {
        val mapping = logicalToRawBits
        return mapping.copyOf()
    }

    fun resetDefault(context: Context) {
        synchronized(lock) {
            logicalToRawBits = defaultRawBits.copyOf()
            loaded = true
            saveLocked(context)
        }
    }

    fun setRawBitForLogical(context: Context, logicalIndex: Int, rawBit: Int): Boolean {
        if (logicalIndex !in 0 until defaultRawBits.size) return false
        if (!isValidRawButtonCode(rawBit)) return false
        synchronized(lock) {
            ensureLoaded(context)
            val next = logicalToRawBits.copyOf()
            val existingIndex = next.indexOf(rawBit)
            if (existingIndex >= 0 && existingIndex != logicalIndex) {
                val old = next[logicalIndex]
                next[existingIndex] = old
            }
            next[logicalIndex] = rawBit
            logicalToRawBits = next
            saveLocked(context)
        }
        return true
    }

    fun mapRawToLogical(rawMask: Int): Int {
        val raw = sanitizeRawMouseButtonMask(rawMask)
        val mapping = logicalToRawBits
        // Some firmwares return per-button state as multi-bit code (e.g. 0x0D for right).
        // If user calibrated such code, exact-match has highest priority.
        var exactOutMask = 0
        for (i in mapping.indices) {
            val code = mapping[i]
            if (code > 0 && !isSingleBitMask(code) && raw == code) {
                exactOutMask = exactOutMask or MOUSE_BUTTON_INDICATORS[i].mask
            }
        }
        if (exactOutMask != 0) return exactOutMask and 0x1F

        var outMask = 0
        for (i in mapping.indices) {
            val code = mapping[i]
            if (code > 0 && isSingleBitMask(code) && (raw and code) == code) {
                outMask = outMask or MOUSE_BUTTON_INDICATORS[i].mask
            }
        }
        return outMask and 0x1F
    }

    private fun saveLocked(context: Context) {
        val encoded = logicalToRawBits.joinToString(",")
        context.getSharedPreferences(AUTO_CONFIG_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY, encoded)
            .apply()
    }
}

private fun sanitizeRawMouseButtonMask(rawMask: Int): Int {
    var mask = rawMask and 0xFFFF
    // Some MAKCU firmwares report right-click with an auxiliary side-bit.
    // Keep side buttons intact except the known ghost combination (right+upper only).
    var lowMask = mask and 0x1F
    val right = (lowMask and 0x02) != 0
    val upper = (lowMask and 0x08) != 0
    val others = lowMask and (0x01 or 0x04 or 0x10)
    if (right && upper && others == 0) {
        lowMask = lowMask and 0x17 // clear upper side bit
    }
    mask = (mask and 0xFFE0) or lowMask
    return mask and 0xFFFF
}

private fun normalizeMouseButtonMask(rawMask: Int): Int {
    return MouseButtonMapper.mapRawToLogical(rawMask)
}

private fun hotkeyTriggerMask(trigger: String): Int {
    return when (trigger.trim().lowercase(Locale.ROOT)) {
        "左", "left", "lbutton", "mouse1", "mouseleft" -> 0x01
        "右", "right", "rbutton", "mouse2", "mouseright" -> 0x02
        "中", "middle", "mbutton", "mouse3" -> 0x04
        "上侧", "侧上", "side1", "x1", "xbutton1", "mouse4" -> 0x08
        "下侧", "侧下", "side2", "x2", "xbutton2", "mouse5" -> 0x10
        else -> 0
    }
}

private fun pickPrimaryRawButtonBit(rawMask: Int, usedBits: Int): Int {
    val mask = rawMask and 0xFFFF
    if (mask == 0) return 0
    val used = usedBits and 0xFFFF

    // Prefer full raw code for non-single-bit firmware responses.
    if (!isSingleBitMask(mask) && (used and mask) == 0) return mask

    for (bitIndex in 0..15) {
        val bit = 1 shl bitIndex
        if ((mask and bit) != 0 && (used and bit) == 0) return bit
    }

    if (!isSingleBitMask(mask)) return mask
    return mask
}

private enum class MakcuDrawPattern(val label: String) {
    Square("画正方形"),
    Circle("画圆形")
}

private data class UsbIoPath(
    val dataInterface: UsbInterface,
    val controlInterface: UsbInterface?,
    val outEndpoint: UsbEndpoint,
    val inEndpoint: UsbEndpoint?
)

private data class UsbSendResult(
    val success: Boolean,
    val message: String
)

private data class MakcuLinkNegotiation(
    val baudRate: Int,
    val isHandshakeVerified: Boolean,
    val serialConfigured: Boolean,
    val isWriteReady: Boolean,
    val lastWriteStatus: Int
)

private class MakcuUsbSession(
    private val connection: UsbDeviceConnection,
    private val claimedInterfaces: List<UsbInterface>,
    private val outEndpoint: UsbEndpoint,
    private val inEndpoint: UsbEndpoint?,
    val dataInterfaceId: Int,
    val baudRate: Int,
    val isHandshakeVerified: Boolean,
    val serialConfigured: Boolean,
    val isWriteReady: Boolean,
    val outEndpointInfo: String,
    val outEndpointAddress: Int,
    val lastWriteStatus: Int
) {
    private val writeLock = Mutex()
    private val parseLock = Any()
    private val parserState = MakcuButtonsParseState()
    @Volatile private var debugLastHex: String = "--"
    @Volatile private var debugLastParser: String = "init"
    @Volatile private var debugLastMask: Int = -1
    @Volatile private var debugLastCount: Int = 0

    suspend fun sendCommand(rawCommand: String): UsbSendResult {
        val commands = buildMakcuCommandVariants(rawCommand).take(6)
        if (commands.isEmpty()) return UsbSendResult(success = false, message = "命令为空")
        val lineEndings = arrayOf("\r\n", "\n", "\r")
        return writeLock.withLock {
            var lastError = Int.MIN_VALUE
            commands.forEach { cmd ->
                lineEndings.forEach { ending ->
                    val payload = (cmd + ending).toByteArray(Charsets.US_ASCII)
                    val written = usbTransferOutWithRetry(connection, outEndpoint, payload, USB_TEST_COMMAND_TIMEOUT_MS)
                    if (written == payload.size) {
                        return@withLock UsbSendResult(success = true, message = "$cmd (${payload.size}B)")
                    }
                    lastError = written
                    runCatching { Thread.sleep(8) }
                }
            }
            UsbSendResult(success = false, message = "写入失败($lastError) ${usbEndpointSummary(outEndpoint)}")
        }
    }

    suspend fun sendCommandExact(rawCommand: String): UsbSendResult {
        val command = normalizeMakcuCommand(rawCommand)
        if (command.isBlank()) return UsbSendResult(success = false, message = "命令为空")
        val lineEndings = arrayOf("\r\n", "\n", "\r")
        return writeLock.withLock {
            var lastError = Int.MIN_VALUE
            lineEndings.forEach { ending ->
                val payload = (command + ending).toByteArray(Charsets.US_ASCII)
                val written = usbTransferOutWithRetry(connection, outEndpoint, payload, USB_TEST_COMMAND_TIMEOUT_MS)
                if (written == payload.size) {
                    return@withLock UsbSendResult(success = true, message = "$command (${payload.size}B)")
                }
                lastError = written
                runCatching { Thread.sleep(8) }
            }
            UsbSendResult(success = false, message = "写入失败($lastError) ${usbEndpointSummary(outEndpoint)}")
        }
    }

    suspend fun sendCommandBurst(rawCommand: String): UsbSendResult {
        val commands = buildMakcuCommandVariants(rawCommand).take(4)
        if (commands.isEmpty()) return UsbSendResult(success = false, message = "命令为空")
        val lineEndings = arrayOf("\r\n", "\n", "\r")
        return writeLock.withLock {
            var lastError = Int.MIN_VALUE
            var attempts = 0
            commands.forEach { cmd ->
                lineEndings.forEach { ending ->
                    attempts++
                    val payload = (cmd + ending).toByteArray(Charsets.US_ASCII)
                    val written = usbTransferOutWithRetry(connection, outEndpoint, payload, USB_TEST_COMMAND_TIMEOUT_MS)
                    if (written == payload.size) {
                        return@withLock UsbSendResult(success = true, message = "burst($attempts) $cmd")
                    }
                    lastError = written
                    runCatching { Thread.sleep(10) }
                }
            }
            UsbSendResult(success = false, message = "burst写入失败($lastError) ${usbEndpointSummary(outEndpoint)}")
        }
    }

    fun debugSummary(): String {
        val maskText = if (debugLastMask >= 0) {
            "0x${(debugLastMask and 0xFFFF).toString(16).uppercase(Locale.US)}"
        } else {
            "--"
        }
        return "USB c=$debugLastCount src=$debugLastParser m=$maskText rx=$debugLastHex"
    }

    private fun updateDebugRx(buffer: ByteArray, count: Int) {
        debugLastCount = count.coerceAtLeast(0)
        if (count > 0) {
            debugLastHex = hexPreview(buffer, count)
        }
    }

    private fun updateDebugParse(source: String, mask: Int?, data: ByteArray) {
        debugLastParser = source
        debugLastMask = mask ?: -1
        debugLastHex = hexPreview(data, data.size)
    }

    private fun hexPreview(buffer: ByteArray, count: Int, maxBytes: Int = 24): String {
        if (count <= 0) return "--"
        val end = count.coerceAtMost(buffer.size)
        val start = (end - maxBytes).coerceAtLeast(0)
        val sb = StringBuilder()
        for (i in start until end) {
            if (sb.isNotEmpty()) sb.append(' ')
            val v = buffer[i].toInt() and 0xFF
            if (v < 0x10) sb.append('0')
            sb.append(v.toString(16).uppercase(Locale.US))
        }
        return sb.toString()
    }

    suspend fun sendMouseButton(buttonMask: Int, pressed: Boolean): UsbSendResult {
        val aliases = when (buttonMask and 0x1F) {
            0x01 -> listOf("left", "lbutton")
            0x02 -> listOf("right", "rbutton")
            0x04 -> listOf("middle", "mbutton")
            0x08 -> listOf("side1", "x1", "xbutton1")
            0x10 -> listOf("side2", "x2", "xbutton2")
            else -> emptyList()
        }
        if (aliases.isEmpty()) return UsbSendResult(false, "不支持的按键")
        val value = if (pressed) 1 else 0
        aliases.forEach { alias ->
            val result = sendCommand("$alias($value)")
            if (result.success) return result
        }
        return UsbSendResult(false, "按键写入失败")
    }

    suspend fun sendV2Command(
        cmd: Int,
        payload: ByteArray = byteArrayOf(),
        expectAck: Boolean = false
    ): UsbSendResult {
        val frame = buildMakcuV2Frame(cmd, payload)
        return writeLock.withLock {
            val written = usbTransferOutWithRetry(connection, outEndpoint, frame, USB_TEST_COMMAND_TIMEOUT_MS)
            if (written != frame.size) {
                return@withLock UsbSendResult(success = false, message = "V2写入失败($written) ${usbEndpointSummary(outEndpoint)}")
            }
            if (!expectAck || inEndpoint == null) {
                return@withLock UsbSendResult(success = true, message = "V2 cmd=0x${cmd.toString(16)} tx")
            }
            val deadline = System.currentTimeMillis() + MAKCU_PROBE_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val ack = readNextV2FrameLocked((deadline - System.currentTimeMillis()).coerceAtLeast(1).toInt())
                    ?: break
                if (ack.first != cmd) {
                    continue
                }
                if (ack.second.isNotEmpty() && cmd != MAKCU_V2_CMD_VERSION) {
                    val status = ack.second[0].toInt() and 0xFF
                    if (status != 0) {
                        return@withLock UsbSendResult(success = false, message = "V2状态码=$status")
                    }
                }
                return@withLock UsbSendResult(success = true, message = "V2 cmd=0x${cmd.toString(16)} ok")
            }
            return@withLock UsbSendResult(success = false, message = "V2 cmd=0x${cmd.toString(16)} 无响应")
        }
    }

    suspend fun setEchoV2(enabled: Boolean): UsbSendResult {
        val value = if (enabled) 1 else 0
        return sendV2Command(MAKCU_V2_CMD_ECHO, byteArrayOf(value.toByte()), expectAck = true)
    }

    suspend fun setBypassV2(enabled: Boolean): UsbSendResult {
        val value = if (enabled) 1 else 0
        return sendV2Command(MAKCU_V2_CMD_BYPASS, byteArrayOf(value.toByte()), expectAck = true)
    }

    suspend fun startButtonsStreamV2(mode: Int, periodMs: Int): UsbSendResult {
        val payload = byteArrayOf(
            mode.coerceIn(0, 2).toByte(),
            periodMs.coerceIn(1, 255).toByte()
        )
        return sendV2Command(MAKCU_V2_CMD_BUTTONS, payload, expectAck = true)
    }

    suspend fun sendMoveV2(dx: Int, dy: Int): UsbSendResult {
        val x = dx.coerceIn(-32768, 32767)
        val y = dy.coerceIn(-32768, 32767)
        val compact = byteArrayOf(
            (x and 0xFF).toByte(),
            ((x shr 8) and 0xFF).toByte(),
            (y and 0xFF).toByte(),
            ((y shr 8) and 0xFF).toByte()
        )
        val compactResult = sendV2Command(MAKCU_V2_CMD_MOVE, compact, expectAck = false)
        if (compactResult.success) return compactResult
        val legacy = byteArrayOf(
            (x and 0xFF).toByte(),
            ((x shr 8) and 0xFF).toByte(),
            (y and 0xFF).toByte(),
            ((y shr 8) and 0xFF).toByte(),
            1, // segments
            0, // cx1
            0  // cy1
        )
        return sendV2Command(MAKCU_V2_CMD_MOVE, legacy, expectAck = false)
    }

    suspend fun sendMoveSmart(dx: Int, dy: Int): UsbSendResult {
        val moveCmd = "move($dx,$dy)"
        if (isHandshakeVerified) {
            var result = sendMoveV2(dx, dy)
            if (!result.success) result = sendCommand(moveCmd)
            if (!result.success) result = sendCommandBurst(moveCmd)
            return result
        }

        var anySuccess = false
        var firstSuccessMessage: String? = null
        var lastErrorMessage = "无可用发送通道"
        val exactCommands = listOf(
            ".move($dx,$dy)",
            "km.move($dx,$dy)",
            "move($dx,$dy)"
        )
        exactCommands.forEach { cmd ->
            val result = sendCommandExact(cmd)
            if (result.success) {
                anySuccess = true
                if (firstSuccessMessage == null) firstSuccessMessage = result.message
            } else {
                lastErrorMessage = result.message
            }
        }

        val v2Result = sendMoveV2(dx, dy)
        if (v2Result.success) {
            anySuccess = true
            if (firstSuccessMessage == null) firstSuccessMessage = v2Result.message
        } else {
            lastErrorMessage = v2Result.message
        }

        if (anySuccess) {
            return UsbSendResult(success = true, message = firstSuccessMessage ?: "best-effort move")
        }

        val burst = sendCommandBurst(moveCmd)
        if (burst.success) return burst
        return UsbSendResult(success = false, message = "move失败: $lastErrorMessage; burst=${burst.message}")
    }

    suspend fun readButtonsMask(timeoutMs: Int): Int? {
        val endpoint = inEndpoint ?: return null
        return writeLock.withLock {
            val readBuffer = ByteArray(256)
            val count = usbTransferIn(connection, endpoint, readBuffer, timeoutMs)
            synchronized(parseLock) {
                if (count > 0) {
                    updateDebugRx(readBuffer, count)
                } else {
                    debugLastCount = 0
                    if (debugLastParser == "init" || debugLastParser == "none") {
                        debugLastParser = "timeout"
                    }
                }
            }
            if (count <= 0) return@withLock parseButtonsMaskFallback()
            parseButtonsMask(readBuffer, count) ?: parseButtonsMaskFallback()
        }
    }

    fun canReadButtons(): Boolean = inEndpoint != null

    fun close() {
        runCatching {
            claimedInterfaces.forEach { intf ->
                runCatching { connection.releaseInterface(intf) }
            }
            connection.close()
        }
    }

    private fun parseButtonsMask(buffer: ByteArray, count: Int): Int? {
        synchronized(parseLock) {
            val result = MakcuButtonsParser.parse(
                state = parserState,
                buffer = buffer,
                count = count,
                options = MakcuButtonsParserOptions(
                    allowBareButtonsToken = false,
                    strictBinaryZeroGuard = false
                )
            )
            if (result != null) {
                updateDebugParse(result.source, result.mask, result.dataPreview)
                return result.mask
            }
            updateDebugParse("none", null, parserState.pendingBytes.toByteArray())
            return null
        }
    }

    private fun parseButtonsMaskFallback(): Int? {
        synchronized(parseLock) {
            return parseButtonsMask(byteArrayOf(), 0)
        }
    }

    private fun readNextV2FrameLocked(timeoutMs: Int): Pair<Int, ByteArray>? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            synchronized(parseLock) {
                val ready = parseAnyV2Frame(parserState.pendingBytes.toByteArray())
                if (ready != null) {
                    trimPending(parserState.pendingBytes.toByteArray(), ready.third)
                    return ready.first to ready.second
                }
            }
            val endpoint = inEndpoint ?: return null
            val buffer = ByteArray(256)
            val read = usbTransferIn(connection, endpoint, buffer, 40)
            if (read > 0) {
                synchronized(parseLock) {
                    parserState.pendingBytes.write(buffer, 0, read)
                    updateDebugRx(buffer, read)
                }
            }
        }
        return null
    }

    private fun parseAnyV2Frame(data: ByteArray): Triple<Int, ByteArray, Int>? {
        var index = 0
        while (index + 4 <= data.size) {
            if ((data[index].toInt() and 0xFF) != MAKCU_V2_FRAME_HEAD) {
                index++
                continue
            }
            val cmd = data[index + 1].toInt() and 0xFF
            val len = (data[index + 2].toInt() and 0xFF) or ((data[index + 3].toInt() and 0xFF) shl 8)
            if (len < 0 || len > 2048) {
                index++
                continue
            }
            val frameEnd = index + 4 + len
            if (frameEnd > data.size) return null
            val payload = data.copyOfRange(index + 4, frameEnd)
            return Triple(cmd, payload, frameEnd)
        }
        return null
    }

    private fun trimPending(data: ByteArray, consume: Int) {
        parserState.pendingBytes.reset()
        if (consume < data.size) {
            parserState.pendingBytes.write(data, consume, data.size - consume)
        }
    }
}

private suspend fun sendMakcuDrawPattern(session: MakcuUsbSession, pattern: MakcuDrawPattern): UsbSendResult {
    val stepDelayMs = MakcuTestConfig.drawStepDelayMs.coerceIn(2L, 22L)
    val moves = when (pattern) {
        MakcuDrawPattern.Square -> buildSquareMoves()
        MakcuDrawPattern.Circle -> buildCircleMoves()
    }
    if (moves.isEmpty()) return UsbSendResult(success = false, message = "${pattern.label} 轨迹为空")
    for ((index, move) in moves.withIndex()) {
        var lastResult = UsbSendResult(success = false, message = "未发送")
        repeat(3) { attempt ->
            val result = session.sendMoveSmart(move.first, move.second)
            lastResult = result
            if (result.success) return@repeat
            if (attempt < 2) delay(8)
        }
        if (!lastResult.success) {
            return UsbSendResult(
                success = false,
                message = "${pattern.label} 第${index + 1}步失败: ${lastResult.message}"
            )
        }
        delay(stepDelayMs)
    }
    return UsbSendResult(success = true, message = "${pattern.label} (${moves.size}步)")
}

private suspend fun sendMakcuDrawPatternBroadcast(
    usbManager: UsbManager,
    device: UsbDevice,
    pattern: MakcuDrawPattern,
    preferred: MakcuUsbSession?
): UsbSendResult {
    val attempted = mutableListOf<String>()
    val usedPathKeys = mutableSetOf<String>()
    var anySuccess = false

    if (preferred != null) {
        val preferredResult = sendMakcuDrawPattern(preferred, pattern)
        attempted += "if=${preferred.dataInterfaceId},baud=${preferred.baudRate},${preferred.outEndpointInfo},${preferredResult.message}"
        usedPathKeys += "${preferred.dataInterfaceId}:${preferred.outEndpointAddress}"
        if (preferredResult.success) anySuccess = true
    }

    val candidates = findUsbIoCandidates(device)
    for (path in candidates) {
        val pathKey = "${path.dataInterface.id}:${path.outEndpoint.address and 0xFF}"
        if (usedPathKeys.contains(pathKey)) continue
        val session = openMakcuUsbSessionForPath(usbManager, device, path, requireWriteReady = true)
            ?: openMakcuUsbSessionForPath(usbManager, device, path, requireWriteReady = false)
            ?: continue
        try {
            val result = sendMakcuDrawPattern(session, pattern)
            attempted += "if=${session.dataInterfaceId},baud=${session.baudRate},${session.outEndpointInfo},${result.message}"
            usedPathKeys += pathKey
            if (result.success) anySuccess = true
        } finally {
            session.close()
        }
    }

    return if (attempted.isNotEmpty() && anySuccess) {
        UsbSendResult(success = true, message = "${pattern.label} 广播发送: ${attempted.joinToString(" | ")}")
    } else if (attempted.isNotEmpty()) {
        UsbSendResult(success = false, message = "${pattern.label} 广播失败: ${attempted.joinToString(" | ")}")
    } else {
        UsbSendResult(success = false, message = "未找到可用 USB 发送通道")
    }
}

private fun buildSquareMoves(side: Int = 240, stepsPerEdge: Int = 12): List<Pair<Int, Int>> {
    val step = (side / stepsPerEdge.toFloat()).roundToInt().coerceAtLeast(1)
    return buildList {
        repeat(stepsPerEdge) { add(step to 0) }
        repeat(stepsPerEdge) { add(0 to step) }
        repeat(stepsPerEdge) { add(-step to 0) }
        repeat(stepsPerEdge) { add(0 to -step) }
    }
}

private fun buildCircleMoves(radius: Int = 120, segments: Int = 48): List<Pair<Int, Int>> {
    var prevX = radius
    var prevY = 0
    return buildList {
        for (i in 1..segments) {
            val angle = 2.0 * Math.PI * i / segments
            val x = (radius * cos(angle)).roundToInt()
            val y = (radius * sin(angle)).roundToInt()
            val dx = x - prevX
            val dy = y - prevY
            if (dx != 0 || dy != 0) add(dx to dy)
            prevX = x
            prevY = y
        }
    }
}

private suspend fun sendMakcuDrawPatternSerial(pattern: MakcuDrawPattern): UsbSendResult {
    if (!MakcuSerialEngine.isConnected()) {
        return UsbSendResult(success = false, message = "串口未连接")
    }
    val stepDelayMs = MakcuTestConfig.drawStepDelayMs.coerceIn(2L, 22L)
    val moves = when (pattern) {
        MakcuDrawPattern.Square -> buildSquareMoves()
        MakcuDrawPattern.Circle -> buildCircleMoves()
    }
    if (moves.isEmpty()) return UsbSendResult(success = false, message = "${pattern.label} 轨迹为空")
    for ((index, move) in moves.withIndex()) {
        val result = MakcuSerialEngine.sendMove(move.first, move.second)
        if (!result.success) {
            return UsbSendResult(
                success = false,
                message = "${pattern.label} 第${index + 1}步失败: ${result.message}"
            )
        }
        delay(stepDelayMs)
    }
    return UsbSendResult(success = true, message = "${pattern.label} 串口发送成功(${moves.size}步)")
}

private fun openMakcuUsbSession(usbManager: UsbManager, device: UsbDevice): MakcuUsbSession? {
    if (!usbManager.hasPermission(device)) return null
    val candidates = findUsbIoCandidates(device)
    if (candidates.isEmpty()) return null
    var writeReadyWithInputFallback: MakcuUsbSession? = null
    var writeReadyFallback: MakcuUsbSession? = null
    var weakWithInputFallback: MakcuUsbSession? = null
    var weakFallback: MakcuUsbSession? = null

    fun takeSession(session: MakcuUsbSession): MakcuUsbSession? {
        if (session.isHandshakeVerified) {
            writeReadyWithInputFallback?.close()
            writeReadyFallback?.close()
            weakWithInputFallback?.close()
            weakFallback?.close()
            return session
        }
        val hasInput = session.canReadButtons()
        if (session.isWriteReady && hasInput) {
            if (writeReadyWithInputFallback == null) {
                writeReadyWithInputFallback = session
            } else {
                session.close()
            }
        } else if (session.isWriteReady) {
            if (writeReadyFallback == null) {
                writeReadyFallback = session
            } else {
                session.close()
            }
        } else if (hasInput) {
            if (weakWithInputFallback == null) {
                weakWithInputFallback = session
            } else {
                session.close()
            }
        } else if (weakFallback == null) {
            weakFallback = session
        } else {
            session.close()
        }
        return null
    }

    // Pass 1: full negotiation + write probe (preferred)
    candidates.forEach { path ->
        val session = openMakcuUsbSessionForPath(usbManager, device, path, requireWriteReady = true)
            ?: return@forEach
        val picked = takeSession(session)
        if (picked != null) return picked
    }
    if (writeReadyWithInputFallback != null) {
        writeReadyFallback?.close()
        weakWithInputFallback?.close()
        weakFallback?.close()
        return writeReadyWithInputFallback
    }
    if (writeReadyFallback != null) {
        weakWithInputFallback?.close()
        weakFallback?.close()
        return writeReadyFallback
    }

    // Pass 2: raw fallback path, keep session alive for broadcast command tries.
    candidates.forEach { path ->
        val session = openMakcuUsbSessionForPath(usbManager, device, path, requireWriteReady = false)
            ?: return@forEach
        val picked = takeSession(session)
        if (picked != null) return picked
    }

    if (writeReadyWithInputFallback != null) {
        writeReadyFallback?.close()
        weakWithInputFallback?.close()
        weakFallback?.close()
        return writeReadyWithInputFallback
    }
    if (writeReadyFallback != null) {
        weakWithInputFallback?.close()
        weakFallback?.close()
        return writeReadyFallback
    }
    if (weakWithInputFallback != null) {
        weakFallback?.close()
        return weakWithInputFallback
    }
    return weakFallback
}

private fun openMakcuUsbSessionForPath(
    usbManager: UsbManager,
    device: UsbDevice,
    path: UsbIoPath,
    requireWriteReady: Boolean = true
): MakcuUsbSession? {
    val connection = usbManager.openDevice(device) ?: return null
    return try {
        val isCh343 = device.vendorId == MAKCU_VENDOR_ID && device.productId == MAKCU_PID_CH343
        val claimed = mutableListOf<UsbInterface>()
        val dataClaimed = connection.claimInterface(path.dataInterface, true) ||
            connection.claimInterface(path.dataInterface, false)
        if (!dataClaimed) {
            runCatching { connection.close() }
            return null
        }
        claimed += path.dataInterface
        runCatching { connection.setInterface(path.dataInterface) }
        if (path.controlInterface != null && path.controlInterface.id != path.dataInterface.id) {
            val ctrlClaimed = connection.claimInterface(path.controlInterface, true) ||
                connection.claimInterface(path.controlInterface, false)
            if (ctrlClaimed) {
                claimed += path.controlInterface
                runCatching { connection.setInterface(path.controlInterface) }
            }
        }
        val negotiation = if (requireWriteReady) {
            negotiateMakcuBaud(connection, device, path)
        } else {
            val rawBaud = MAKCU_SERIAL_BAUD
            var configured = runCatching { configureMakcuSerial(connection, device, path, rawBaud) }.getOrDefault(false)
            var selectedBaud = rawBaud
            if (!configured && isCh343) {
                configured = runCatching { configureMakcuSerial(connection, device, path, MAKCU_FALLBACK_BAUD) }.getOrDefault(false)
                if (configured) selectedBaud = MAKCU_FALLBACK_BAUD
            }
            MakcuLinkNegotiation(
                baudRate = selectedBaud,
                isHandshakeVerified = false,
                serialConfigured = configured,
                isWriteReady = dataClaimed,
                lastWriteStatus = if (configured) 0 else -9
            )
        }
        if (requireWriteReady && !negotiation.isWriteReady) {
            claimed.forEach { intf -> runCatching { connection.releaseInterface(intf) } }
            runCatching { connection.close() }
            return null
        }
        MakcuUsbSession(
            connection = connection,
            claimedInterfaces = claimed,
            outEndpoint = path.outEndpoint,
            inEndpoint = path.inEndpoint,
            dataInterfaceId = path.dataInterface.id,
            baudRate = negotiation.baudRate,
            isHandshakeVerified = negotiation.isHandshakeVerified,
            serialConfigured = negotiation.serialConfigured,
            isWriteReady = negotiation.isWriteReady,
            outEndpointInfo = usbEndpointSummary(path.outEndpoint),
            outEndpointAddress = path.outEndpoint.address and 0xFF,
            lastWriteStatus = negotiation.lastWriteStatus
        )
    } catch (_: Throwable) {
        runCatching { connection.releaseInterface(path.dataInterface) }
        if (path.controlInterface != null && path.controlInterface.id != path.dataInterface.id) {
            runCatching { connection.releaseInterface(path.controlInterface) }
        }
        runCatching { connection.close() }
        null
    }
}

private fun findUsbIoCandidates(device: UsbDevice): List<UsbIoPath> {
    data class RankedCandidate(
        val path: UsbIoPath,
        val score: Int
    )

    val allInterfaces = (0 until device.interfaceCount).map { device.getInterface(it) }
    val controlInterface = allInterfaces.firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_COMM }
    val ranked = mutableListOf<RankedCandidate>()

    allInterfaces.forEach { intf ->
        val outEndpoints = mutableListOf<UsbEndpoint>()
        val inEndpoints = mutableListOf<UsbEndpoint>()
        for (idx in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(idx)
            if (ep.direction == UsbConstants.USB_DIR_OUT) {
                outEndpoints += ep
            } else if (ep.direction == UsbConstants.USB_DIR_IN) {
                inEndpoints += ep
            }
        }
        if (outEndpoints.isEmpty()) return@forEach

        val preferredIn = inEndpoints.firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
            ?: inEndpoints.firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_INT }
            ?: inEndpoints.firstOrNull()

        outEndpoints.forEachIndexed { order, outEp ->
            var score = when (outEp.type) {
                UsbConstants.USB_ENDPOINT_XFER_BULK -> 100
                UsbConstants.USB_ENDPOINT_XFER_INT -> 80
                else -> 55
            }
            score += when (preferredIn?.type) {
                UsbConstants.USB_ENDPOINT_XFER_BULK -> 35
                UsbConstants.USB_ENDPOINT_XFER_INT -> 20
                null -> -45
                else -> 10
            }
            if (intf.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) score += 8
            if (intf.interfaceClass == 0x0A) score += 8 // CDC DATA
            if ((outEp.address and 0xFF) == 0x02) score += 3
            score -= order
            ranked += RankedCandidate(
                path = UsbIoPath(
                    dataInterface = intf,
                    controlInterface = controlInterface?.takeIf { it.id != intf.id },
                    outEndpoint = outEp,
                    inEndpoint = preferredIn
                ),
                score = score
            )
        }
    }

    return ranked
        .sortedWith(
            compareByDescending<RankedCandidate> { it.path.inEndpoint != null }
                .thenByDescending { it.score }
        )
        .map { it.path }
}

private fun negotiateMakcuBaud(
    connection: UsbDeviceConnection,
    device: UsbDevice,
    path: UsbIoPath
): MakcuLinkNegotiation {
    val isCh343 = device.vendorId == MAKCU_VENDOR_ID && device.productId == MAKCU_PID_CH343
    val baudCandidates = if (isCh343) MAKCU_BAUD_CANDIDATES_CH343 else MAKCU_BAUD_CANDIDATES
    val probeIn = path.inEndpoint
    var fallbackWritable: MakcuLinkNegotiation? = null
    var lastWriteStatus = Int.MIN_VALUE

    fun preferFallback(current: MakcuLinkNegotiation?, next: MakcuLinkNegotiation): MakcuLinkNegotiation {
        if (current == null) return next
        return when {
            next.isHandshakeVerified && !current.isHandshakeVerified -> next
            next.isHandshakeVerified == current.isHandshakeVerified && next.isHandshakeVerified && next.baudRate > current.baudRate -> next
            next.isHandshakeVerified == current.isHandshakeVerified && !next.isHandshakeVerified && isCh343 -> current
            next.isHandshakeVerified == current.isHandshakeVerified && !next.isHandshakeVerified && !isCh343 && next.baudRate > current.baudRate -> next
            next.isHandshakeVerified == current.isHandshakeVerified && next.baudRate == current.baudRate && next.lastWriteStatus > current.lastWriteStatus -> next
            else -> current
        }
    }

    for (baud in baudCandidates) {
        if (!configureMakcuSerial(connection, device, path, baud)) continue
        val writeStatus = probeMakcuWritableOut(connection, path.outEndpoint)
        lastWriteStatus = writeStatus
        if (writeStatus < 0) continue
        if (probeIn == null) return MakcuLinkNegotiation(baud, false, true, true, writeStatus)
        if (probeMakcuVersion(connection, path.outEndpoint, probeIn)) {
            if (baud != MAKCU_SERIAL_BAUD) {
                val switchSent =
                    writeMakcuAscii(connection, path.outEndpoint, "baud($MAKCU_SERIAL_BAUD)") ||
                    writeMakcuAscii(connection, path.outEndpoint, "km.baud($MAKCU_SERIAL_BAUD)")
                if (switchSent) {
                    runCatching { Thread.sleep(30) }
                    val switchedOk = configureMakcuSerial(connection, device, path, MAKCU_SERIAL_BAUD)
                    val switchedWrite = if (switchedOk) probeMakcuWritableOut(connection, path.outEndpoint) else -1
                    val switchedProbe = switchedOk && switchedWrite >= 0 && probeMakcuVersion(connection, path.outEndpoint, probeIn)
                    if (switchedProbe) return MakcuLinkNegotiation(MAKCU_SERIAL_BAUD, true, true, true, switchedWrite)
                    if (switchedWrite >= 0) {
                        fallbackWritable = preferFallback(
                            fallbackWritable,
                            MakcuLinkNegotiation(MAKCU_SERIAL_BAUD, false, true, true, switchedWrite)
                        )
                    }
                }
            }
            return MakcuLinkNegotiation(baud, true, true, true, writeStatus)
        }
        fallbackWritable = preferFallback(
            fallbackWritable,
            MakcuLinkNegotiation(baud, false, true, true, writeStatus)
        )
    }

    if (fallbackWritable != null) return fallbackWritable

    // Fallback: keep raw channel alive even if setup/probe failed, still try command sends.
    if (configureMakcuSerial(connection, device, path, MAKCU_SERIAL_BAUD)) {
        val writeStatus = probeMakcuWritableOut(connection, path.outEndpoint)
        lastWriteStatus = writeStatus
        if (writeStatus >= 0) return MakcuLinkNegotiation(MAKCU_SERIAL_BAUD, false, true, true, writeStatus)
    }
    if (configureMakcuSerial(connection, device, path, MAKCU_FALLBACK_BAUD)) {
        val writeStatus = probeMakcuWritableOut(connection, path.outEndpoint)
        lastWriteStatus = writeStatus
        if (writeStatus >= 0) return MakcuLinkNegotiation(MAKCU_FALLBACK_BAUD, false, true, true, writeStatus)
    }
    return MakcuLinkNegotiation(MAKCU_FALLBACK_BAUD, false, false, false, lastWriteStatus)
}

private fun configureMakcuSerial(
    connection: UsbDeviceConnection,
    device: UsbDevice,
    path: UsbIoPath,
    baudRate: Int
): Boolean {
    // Safety-first mode for KM/MAKCU boxes:
    // Some ROMs crash in low-level controlTransfer during CH34x/CDC init.
    // Keep raw USB channel alive first; command-level probing will decide usability.
    if (device.vendorId == MAKCU_VENDOR_ID) return true
    return if (path.controlInterface != null) {
        setupCdcAcmSerial(connection, path.controlInterface.id, baudRate)
    } else {
        true
    }
}

private fun probeMakcuVersion(
    connection: UsbDeviceConnection,
    outEndpoint: UsbEndpoint,
    inEndpoint: UsbEndpoint
): Boolean {
    fun isValidVersionResponse(resp: String?): Boolean {
        val text = resp.orEmpty().lowercase(Locale.US)
        if (text.isBlank()) return false
        if (text.contains("version")) return true
        return Regex("""\bv\d+\.\d+(\.\d+)?\b""").containsMatchIn(text)
    }

    clearUsbReadBuffer(connection, inEndpoint)
    if (writeMakcuV2(connection, outEndpoint, MAKCU_V2_CMD_VERSION, byteArrayOf())) {
        val v2 = readMakcuV2(connection, inEndpoint, MAKCU_PROBE_TIMEOUT_MS)
        if (v2 != null && v2.first == MAKCU_V2_CMD_VERSION && v2.second.isNotEmpty()) return true
    }
    if (writeMakcuAscii(connection, outEndpoint, "version()")) {
        val resp = readMakcuAscii(connection, inEndpoint, MAKCU_PROBE_TIMEOUT_MS)
        if (isValidVersionResponse(resp)) return true
    }
    if (writeMakcuAscii(connection, outEndpoint, "km.version()")) {
        val resp = readMakcuAscii(connection, inEndpoint, MAKCU_PROBE_TIMEOUT_MS)
        if (isValidVersionResponse(resp)) return true
    }
    if (writeMakcuAscii(connection, outEndpoint, "m.version()")) {
        val resp = readMakcuAscii(connection, inEndpoint, MAKCU_PROBE_TIMEOUT_MS)
        if (isValidVersionResponse(resp)) return true
    }
    if (writeMakcuAscii(connection, outEndpoint, "M.version()")) {
        val resp = readMakcuAscii(connection, inEndpoint, MAKCU_PROBE_TIMEOUT_MS)
        if (isValidVersionResponse(resp)) return true
    }
    return false
}

private fun probeMakcuWritableOut(
    connection: UsbDeviceConnection,
    outEndpoint: UsbEndpoint
): Int {
    val frame = buildMakcuV2Frame(MAKCU_V2_CMD_VERSION, byteArrayOf())
    val v2Written = usbTransferOutWithRetry(connection, outEndpoint, frame, USB_TEST_COMMAND_TIMEOUT_MS)
    if (v2Written == frame.size) return v2Written

    val ascii = "version()\r".toByteArray(Charsets.US_ASCII)
    val asciiWritten = usbTransferOutWithRetry(connection, outEndpoint, ascii, USB_TEST_COMMAND_TIMEOUT_MS)
    if (asciiWritten == ascii.size) return asciiWritten

    return if (asciiWritten < 0) asciiWritten else v2Written
}

private fun writeMakcuV2(
    connection: UsbDeviceConnection,
    outEndpoint: UsbEndpoint,
    cmd: Int,
    payload: ByteArray
): Boolean {
    val frame = buildMakcuV2Frame(cmd, payload)
    val written = usbTransferOut(connection, outEndpoint, frame, USB_TEST_COMMAND_TIMEOUT_MS)
    return written == frame.size
}

private fun readMakcuV2(
    connection: UsbDeviceConnection,
    inEndpoint: UsbEndpoint,
    timeoutMs: Int
): Pair<Int, ByteArray>? {
    val output = ByteArrayOutputStream()
    val chunk = ByteArray(256)
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        val read = usbTransferIn(connection, inEndpoint, chunk, 40)
        if (read <= 0) continue
        output.write(chunk, 0, read)
        val frame = parseMakcuV2Frame(output.toByteArray())
        if (frame != null) return frame.first to frame.second
    }
    return null
}

private fun parseMakcuV2Frame(data: ByteArray): Triple<Int, ByteArray, Int>? {
    var index = 0
    while (index + 4 <= data.size) {
        if ((data[index].toInt() and 0xFF) != MAKCU_V2_FRAME_HEAD) {
            index++
            continue
        }
        val cmd = data[index + 1].toInt() and 0xFF
        val len = (data[index + 2].toInt() and 0xFF) or ((data[index + 3].toInt() and 0xFF) shl 8)
        if (len < 0 || len > 2048) {
            index++
            continue
        }
        val frameEnd = index + 4 + len
        if (frameEnd > data.size) return null
        val payload = data.copyOfRange(index + 4, frameEnd)
        return Triple(cmd, payload, frameEnd)
    }
    return null
}

private fun usbInterfaceDigest(device: UsbDevice): String {
    if (device.interfaceCount <= 0) return "if=0"
    return buildString {
        append("if=")
        append(device.interfaceCount)
        append(" [")
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (i > 0) append(";")
            var out = 0
            var input = 0
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.direction == UsbConstants.USB_DIR_OUT) out++ else if (ep.direction == UsbConstants.USB_DIR_IN) input++
            }
            append("${intf.id}:${intf.interfaceClass}/${intf.interfaceSubclass} e${intf.endpointCount} o$out i$input")
        }
        append("]")
    }
}

private fun summarizeUsbCandidates(candidates: List<UsbIoPath>, limit: Int = 4): String {
    if (candidates.isEmpty()) return "cand=0"
    return candidates.take(limit).joinToString(" | ") { path ->
        val inPart = path.inEndpoint?.let { " in=0x${(it.address and 0xFF).toString(16)}" } ?: ""
        "if=${path.dataInterface.id} ${usbEndpointSummary(path.outEndpoint)}$inPart"
    }
}

private fun usbTransferOut(
    connection: UsbDeviceConnection,
    endpoint: UsbEndpoint,
    payload: ByteArray,
    timeoutMs: Int
): Int {
    return when (endpoint.type) {
        UsbConstants.USB_ENDPOINT_XFER_BULK -> {
            val bulk = connection.bulkTransfer(endpoint, payload, payload.size, timeoutMs)
            if (bulk >= 0) bulk else usbRequestTransferOut(connection, endpoint, payload, timeoutMs)
        }
        UsbConstants.USB_ENDPOINT_XFER_INT -> {
            usbRequestTransferOut(connection, endpoint, payload, timeoutMs)
        }
        else -> -2
    }
}

private fun usbTransferOutWithRetry(
    connection: UsbDeviceConnection,
    endpoint: UsbEndpoint,
    payload: ByteArray,
    timeoutMs: Int
): Int {
    val first = usbTransferOut(connection, endpoint, payload, timeoutMs)
    if (first >= 0) return first
    clearUsbEndpointHalt(connection, endpoint)
    runCatching { Thread.sleep(12) }
    return usbTransferOut(connection, endpoint, payload, timeoutMs)
}

private fun usbTransferIn(
    connection: UsbDeviceConnection,
    endpoint: UsbEndpoint,
    buffer: ByteArray,
    timeoutMs: Int
): Int {
    return when (endpoint.type) {
        UsbConstants.USB_ENDPOINT_XFER_BULK -> {
            val bulk = connection.bulkTransfer(endpoint, buffer, buffer.size, timeoutMs)
            if (bulk >= 0) bulk else usbRequestTransferIn(connection, endpoint, buffer, timeoutMs)
        }
        UsbConstants.USB_ENDPOINT_XFER_INT -> {
            usbRequestTransferIn(connection, endpoint, buffer, timeoutMs)
        }
        else -> -2
    }
}

private fun usbRequestTransferOut(
    connection: UsbDeviceConnection,
    endpoint: UsbEndpoint,
    payload: ByteArray,
    timeoutMs: Int
): Int {
    val request = UsbRequest()
    if (!request.initialize(connection, endpoint)) return -1
    try {
        val buffer = ByteBuffer.allocateDirect(payload.size)
        buffer.put(payload)
        buffer.flip()
        val queued = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            request.queue(buffer)
        } else {
            @Suppress("DEPRECATION")
            request.queue(buffer, payload.size)
        }
        if (!queued) return -1
        val completed = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                connection.requestWait(timeoutMs.toLong())
            } else {
                connection.requestWait()
            }
        } catch (_: TimeoutException) {
            runCatching { request.cancel() }
            return -110
        }
        return if (completed == request) payload.size else -1
    } finally {
        request.close()
    }
}

private fun usbRequestTransferIn(
    connection: UsbDeviceConnection,
    endpoint: UsbEndpoint,
    buffer: ByteArray,
    timeoutMs: Int
): Int {
    val request = UsbRequest()
    if (!request.initialize(connection, endpoint)) return -1
    try {
        val direct = ByteBuffer.allocateDirect(buffer.size)
        val queued = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            request.queue(direct)
        } else {
            @Suppress("DEPRECATION")
            request.queue(direct, buffer.size)
        }
        if (!queued) return -1
        val completed = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                connection.requestWait(timeoutMs.toLong())
            } else {
                connection.requestWait()
            }
        } catch (_: TimeoutException) {
            runCatching { request.cancel() }
            return 0
        }
        if (completed != request) return -1
        val count = direct.position()
        if (count <= 0) return 0
        direct.flip()
        val copyLen = count.coerceAtMost(buffer.size)
        direct.get(buffer, 0, copyLen)
        return copyLen
    } finally {
        request.close()
    }
}

private fun clearUsbEndpointHalt(connection: UsbDeviceConnection, endpoint: UsbEndpoint) {
    val requestType = UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_STANDARD or 0x02
    val requestClearFeature = 0x01
    val featureEndpointHalt = 0
    runCatching {
        connection.controlTransfer(
            requestType,
            requestClearFeature,
            featureEndpointHalt,
            endpoint.address and 0xFF,
            null,
            0,
            USB_TEST_COMMAND_TIMEOUT_MS
        )
    }
}

private fun usbEndpointSummary(endpoint: UsbEndpoint): String {
    val type = when (endpoint.type) {
        UsbConstants.USB_ENDPOINT_XFER_BULK -> "bulk"
        UsbConstants.USB_ENDPOINT_XFER_INT -> "int"
        UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "ctrl"
        UsbConstants.USB_ENDPOINT_XFER_ISOC -> "isoc"
        else -> "unknown"
    }
    val dir = if (endpoint.direction == UsbConstants.USB_DIR_OUT) "out" else "in"
    return "[ep=0x${(endpoint.address and 0xFF).toString(16)} $dir/$type mps=${endpoint.maxPacketSize}]"
}

private fun writeMakcuAscii(
    connection: UsbDeviceConnection,
    outEndpoint: UsbEndpoint,
    rawCommand: String
): Boolean {
    val commands = buildMakcuCommandVariants(rawCommand).take(3)
    if (commands.isEmpty()) return false
    val endings = arrayOf("\r\n", "\n", "\r")
    commands.forEach { command ->
        endings.forEach { ending ->
            val payload = (command + ending).toByteArray(Charsets.US_ASCII)
            val written = usbTransferOutWithRetry(connection, outEndpoint, payload, USB_TEST_COMMAND_TIMEOUT_MS)
            if (written == payload.size) return true
            runCatching { Thread.sleep(8) }
        }
    }
    return false
}

private fun readMakcuAscii(
    connection: UsbDeviceConnection,
    inEndpoint: UsbEndpoint,
    timeoutMs: Int
): String? {
    val buf = ByteArray(256)
    val output = ByteArrayOutputStream()
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        val read = usbTransferIn(connection, inEndpoint, buf, 40)
        if (read > 0) {
            output.write(buf, 0, read)
            val text = output.toString(Charsets.ISO_8859_1)
            if (text.contains("km.") || text.contains(">>>")) return text
        }
    }
    return if (output.size() > 0) output.toString(Charsets.ISO_8859_1) else null
}

private fun clearUsbReadBuffer(connection: UsbDeviceConnection, inEndpoint: UsbEndpoint) {
    val drain = ByteArray(128)
    repeat(4) {
        val read = usbTransferIn(connection, inEndpoint, drain, 10)
        if (read <= 0) return
    }
}

private fun setupCdcAcmSerial(connection: UsbDeviceConnection, controlInterfaceId: Int, baudRate: Int): Boolean {
    val lineCoding = ByteArray(7).apply {
        this[0] = (baudRate and 0xFF).toByte()
        this[1] = ((baudRate shr 8) and 0xFF).toByte()
        this[2] = ((baudRate shr 16) and 0xFF).toByte()
        this[3] = ((baudRate shr 24) and 0xFF).toByte()
        this[4] = 0
        this[5] = 0
        this[6] = 8
    }
    val setLineCoding = connection.controlTransfer(
        UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_CLASS or 0x01,
        0x20,
        0,
        controlInterfaceId,
        lineCoding,
        lineCoding.size,
        USB_TEST_COMMAND_TIMEOUT_MS
    )
    if (setLineCoding < 0) return false

    val setControlLine = connection.controlTransfer(
        UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_CLASS or 0x01,
        0x22,
        0x03,
        controlInterfaceId,
        null,
        0,
        USB_TEST_COMMAND_TIMEOUT_MS
    )
    return setControlLine >= 0
}

private fun setupCh34xSerial(connection: UsbDeviceConnection, baudRate: Int): Boolean {
    fun controlOut(request: Int, value: Int, index: Int): Boolean {
        return connection.controlTransfer(
            UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_VENDOR,
            request,
            value,
            index,
            null,
            0,
            USB_TEST_COMMAND_TIMEOUT_MS
        ) >= 0
    }

    fun controlIn(request: Int, value: Int, index: Int, buffer: ByteArray): Boolean {
        return connection.controlTransfer(
            UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_VENDOR,
            request,
            value,
            index,
            buffer,
            buffer.size,
            USB_TEST_COMMAND_TIMEOUT_MS
        ) >= 0
    }

    val scratch = ByteArray(8)
    if (!controlIn(0x5F, 0, 0, scratch)) return false
    if (!controlOut(0xA1, 0, 0)) return false
    if (!setCh34xBaudRate(connection, 9_600)) return false
    if (!controlIn(0x95, 0x2518, 0, scratch)) return false
    if (!controlOut(0x9A, 0x2518, 0xC3)) return false
    if (!controlIn(0x95, 0x0706, 0, scratch)) return false
    if (!controlOut(0xA1, 0x501F, 0xD90A)) return false
    if (!setCh34xBaudRate(connection, 9_600)) return false
    val dtrRtsInverted = 0xFFFF
    if (!controlOut(0xA4, dtrRtsInverted, 0)) return false
    if (!controlIn(0x95, 0x0706, 0, scratch)) return false
    if (!setCh34xBaudRate(connection, baudRate)) return false
    return true
}

private fun setCh34xBaudRate(connection: UsbDeviceConnection, baudRate: Int): Boolean {
    if (baudRate <= 0) return false
    var factor = 1_532_620_800L / baudRate
    var divisor = 3L
    while (factor > 0xFFF0L && divisor > 0) {
        factor = factor shr 3
        divisor--
    }
    if (factor > 0xFFF0L) return false
    factor = 0x10000L - factor
    divisor = divisor or 0x80L
    val valueHigh = ((factor and 0xFF00L) or divisor).toInt()
    val valueLow = (factor and 0x00FFL).toInt()
    val step1 = connection.controlTransfer(
        UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_VENDOR,
        0x9A,
        0x1312,
        valueHigh,
        null,
        0,
        USB_TEST_COMMAND_TIMEOUT_MS
    )
    if (step1 < 0) return false
    val step2 = connection.controlTransfer(
        UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_VENDOR,
        0x9A,
        0x0F2C,
        valueLow,
        null,
        0,
        USB_TEST_COMMAND_TIMEOUT_MS
    )
    return step2 >= 0
}

private fun buildMakcuV2Frame(cmd: Int, payload: ByteArray): ByteArray {
    val len = payload.size.coerceAtLeast(0)
    val frame = ByteArray(4 + len)
    frame[0] = MAKCU_V2_FRAME_HEAD.toByte()
    frame[1] = (cmd and 0xFF).toByte()
    frame[2] = (len and 0xFF).toByte()
    frame[3] = ((len shr 8) and 0xFF).toByte()
    if (len > 0) {
        System.arraycopy(payload, 0, frame, 4, len)
    }
    return frame
}

private fun normalizeMakcuCommand(rawCommand: String): String {
    return rawCommand.trim().trimEnd(';')
}

private fun buildMakcuCommandVariants(rawCommand: String): List<String> {
    val base = normalizeMakcuCommand(rawCommand)
    if (base.isBlank()) return emptyList()
    val variants = LinkedHashSet<String>()
    fun add(candidate: String) {
        val cmd = candidate.trim()
        if (cmd.isNotBlank()) variants += cmd
    }

    val noDot = base.removePrefix(".")
    val tail = when {
        noDot.startsWith("km.") -> noDot.removePrefix("km.")
        noDot.startsWith("kM.") -> noDot.removePrefix("kM.")
        noDot.startsWith("m.") -> noDot.removePrefix("m.")
        noDot.startsWith("M.") -> noDot.removePrefix("M.")
        else -> noDot
    }

    // Firmware parser generally expects ".<command>(...)".
    add(".$tail")
    add("km.$tail")
    add("kM.$tail")
    add("m.$tail")
    add("M.$tail")
    add(tail)

    // Keep legacy/original forms as compatibility fallbacks.
    add(base)
    if (!base.startsWith(".")) add(".$base")

    return variants.toList().take(12)
}

private fun indexOfSubArray(source: ByteArray, target: ByteArray): Int {
    if (target.isEmpty() || source.size < target.size) return -1
    for (i in 0..source.size - target.size) {
        var matched = true
        for (j in target.indices) {
            if (source[i + j] != target[j]) {
                matched = false
                break
            }
        }
        if (matched) return i
    }
    return -1
}

private fun parseMaskToken(rawToken: String?): Int? {
    val token = rawToken
        ?.trim()
        ?.trim(*charArrayOf(',', ';', ')', '(', '"', '\''))
        ?.lowercase(Locale.US)
        ?: return null
    if (token.isBlank()) return null
    val value = when {
        token.startsWith("0x") -> token.substring(2).toIntOrNull(16)
        token.startsWith("0b") -> token.substring(2).toIntOrNull(2)
        else -> token.toIntOrNull()
    } ?: return null
    return value and 0xFFFF
}

private fun filterDecimalInput(input: String): String {
    val filtered = input.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot >= 0) {
        val head = filtered.substring(0, firstDot + 1)
        val tail = filtered.substring(firstDot + 1).replace(".", "")
        head + tail
    } else {
        filtered
    }
}

private fun filterIntInput(input: String): String = input.filter { it.isDigit() }

private fun parseFloatInputValue(input: String, fallback: Float, min: Float, max: Float): Float {
    return (input.toFloatOrNull() ?: fallback).coerceIn(min, max)
}

private fun parseIntInputValue(input: String, fallback: Int, min: Int, max: Int): Int {
    return (input.toIntOrNull() ?: fallback).coerceIn(min, max)
}

private fun formatFloatInput(value: Float, digits: Int): String {
    return "%.${digits}f".format(Locale.US, value)
}

private fun intentUsbDevice(intent: Intent?): UsbDevice? {
    if (intent == null) return null
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }
}

private fun usbConnectedName(device: UsbDevice): String {
    val product = device.productName?.trim()
    if (!product.isNullOrBlank()) return product
    if (device.vendorId == 0x1A86) return "USB Single Serial"
    return "USB Device"
}

private fun usbListName(device: UsbDevice): String {
    if (device.vendorId == 0x1A86) return "CH340/CH341/CH9102 (USB Single Serial)"
    return usbConnectedName(device)
}

private fun usbVidPid(device: UsbDevice): String {
    val vid = device.vendorId.toString(16).uppercase(Locale.US).padStart(4, '0')
    val pid = device.productId.toString(16).uppercase(Locale.US).padStart(4, '0')
    return "VID: $vid | PID: $pid"
}

@Composable
fun FunctionScreen(
    trackingEnabled: Boolean,
    confirmThreshold: Int,
    vanishThreshold: Int,
    measureNoiseR: Float,
    vanishHeightRatio: Float,
    stableBottomThreshold: Float,
    edgeMargin: Float,
    onTrackingEnabledChange: (Boolean) -> Unit,
    onConfirmThresholdChange: (Int) -> Unit,
    onVanishThresholdChange: (Int) -> Unit,
    onMeasureNoiseRChange: (Float) -> Unit,
    onVanishHeightRatioChange: (Float) -> Unit,
    onStableBottomThresholdChange: (Float) -> Unit,
    onEdgeMarginChange: (Float) -> Unit
) {
    val panelColor = Color(0xFFEFEFEF)

    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("功能", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = panelColor), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("目标追踪", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 14.sp)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("启用追踪", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF3E3E3E))
                        Text("卡尔曼滤波器多目标追踪", fontSize = 12.sp, color = Color(0xFF6B6B6B))
                    }
                    InputGraySwitch(checked = trackingEnabled, onCheckedChange = onTrackingEnabledChange)
                }

                AnimatedVisibility(visible = trackingEnabled, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabeledGraySlider(
                            title = "确认阈值",
                            valueLabel = confirmThreshold.toString(),
                            value = confirmThreshold.toFloat(),
                            onValueChange = { onConfirmThresholdChange(it.roundToInt().coerceIn(1, 120)) },
                            valueRange = 1f..120f
                        )
                        Text("连续检测 N 帧后确认为有效目标", color = Color(0xFF777777), fontSize = 12.sp)

                        LabeledGraySlider(
                            title = "消失阈值",
                            valueLabel = vanishThreshold.toString(),
                            value = vanishThreshold.toFloat(),
                            onValueChange = { onVanishThresholdChange(it.roundToInt().coerceIn(1, 240)) },
                            valueRange = 1f..240f
                        )
                        Text("丢失后预测 N 帧后删除目标", color = Color(0xFF777777), fontSize = 12.sp)

                        LabeledGraySlider(
                            title = "测量噪声 R",
                            valueLabel = measureNoiseR.roundToInt().toString(),
                            value = measureNoiseR,
                            onValueChange = { onMeasureNoiseRChange(it.coerceIn(0f, 100f)) },
                            valueRange = 0f..100f
                        )
                        Text("值越大检测框越平滑，但响应越慢", color = Color(0xFF777777), fontSize = 12.sp)

                        LabeledGraySlider(
                            title = "消失高度比例",
                            valueLabel = "%.2f".format(vanishHeightRatio),
                            value = vanishHeightRatio,
                            onValueChange = { onVanishHeightRatioChange(it.coerceIn(0f, 1f)) },
                            valueRange = 0f..1f
                        )
                        Text("高度缩小超过此比例判定为消失", color = Color(0xFF777777), fontSize = 12.sp)

                        LabeledGraySlider(
                            title = "消失底部阈值",
                            valueLabel = stableBottomThreshold.roundToInt().toString(),
                            value = stableBottomThreshold,
                            onValueChange = { onStableBottomThresholdChange(it.coerceIn(0f, 100f)) },
                            valueRange = 0f..100f
                        )
                        Text("底部位置变化小于此值视为稳定", color = Color(0xFF777777), fontSize = 12.sp)

                        LabeledGraySlider(
                            title = "边缘检测边距",
                            valueLabel = edgeMargin.roundToInt().toString(),
                            value = edgeMargin,
                            onValueChange = { onEdgeMarginChange(it.coerceIn(0f, 200f)) },
                            valueRange = 0f..200f
                        )
                        Text("距离边缘小于此值视为边缘目标", color = Color(0xFF777777), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("关于", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp))
        Text("X", fontSize = 80.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text("阿呆", fontSize = 14.sp, color = Color.Gray)
        Text(APP_NAME, fontSize = 36.sp, fontWeight = FontWeight.Black)
        Text("v$APP_VERSION", fontSize = 14.sp, color = Color.Gray)
        Spacer(Modifier.height(20.dp))
        Surface(color = Color(0xFFFFE0B2), shape = RoundedCornerShape(50)) {
            Text("QQ 2249531308", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingsScreen(
    udpPort: String,
    tcpPort: String,
    inferenceThreads: Int,
    receiveFps: Int,
    dynamicColor: Boolean,
    onUdpPort: (String) -> Unit,
    onTcpPort: (String) -> Unit,
    onInferenceThreadsChange: (Int) -> Unit,
    onReceiveFpsChange: (Int) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
    val coreCount = remember { Runtime.getRuntime().availableProcessors().coerceAtLeast(1) }
    val threadValue = inferenceThreads.coerceIn(1, coreCount)

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("设置", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("网络配置", fontWeight = FontWeight.Bold)
                Text("端口配置需与推流端一致", color = Color.Gray, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = udpPort, onValueChange = { onUdpPort(it.filter(Char::isDigit)) }, label = { Text("UDP 端口") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(value = tcpPort, onValueChange = { onTcpPort(it.filter(Char::isDigit)) }, label = { Text("TCP 端口") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("性能设置", fontWeight = FontWeight.Bold)
                    Text("$coreCount 核心", color = Color.Gray, fontSize = 12.sp)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("推理线程数", fontWeight = FontWeight.SemiBold)
                    Text(threadValue.toString(), fontWeight = FontWeight.Bold, color = Color.Gray)
                }
                Slider(
                    value = threadValue.toFloat(),
                    onValueChange = { onInferenceThreadsChange(it.roundToInt().coerceIn(1, coreCount)) },
                    valueRange = 1f..coreCount.toFloat(),
                    steps = (coreCount - 2).coerceAtLeast(0)
                )
                Text("提示：线程数并非越多越好，建议根据 CPU 大核数量设置，默认 3-4 即可。", color = Color.Gray, fontSize = 12.sp)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("接收帧率", fontWeight = FontWeight.SemiBold)
                    Text("${receiveFps.coerceIn(30, 240)} Hz", fontWeight = FontWeight.Bold, color = Color.Gray)
                }
                Slider(
                    value = receiveFps.coerceIn(30, 240).toFloat(),
                    onValueChange = {
                        val snapped = ((it.roundToInt() + 5) / 10) * 10
                        onReceiveFpsChange(snapped.coerceIn(30, 240))
                    },
                    valueRange = 30f..240f,
                    steps = 20
                )
                Text("限制接收帧率，超出部分丢弃，为推理节省性能开销。", color = Color.Gray, fontSize = 12.sp)

                Spacer(Modifier.height(4.dp))
                Text("外观设置", fontWeight = FontWeight.Bold, color = Color.Gray)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("动态颜色", fontWeight = FontWeight.SemiBold)
                        Text("跟随系统壁纸配色", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
                }
            }
        }
    }
}

@Composable
fun TechBadge(text: String, size: androidx.compose.ui.unit.TextUnit = 12.sp) {
    Surface(color = Color(0xFFF0F0F0), shape = RoundedCornerShape(4.dp)) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color.Gray, fontSize = size)
    }
}

@Composable
fun HXHostTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(context)
    } else {
        lightColorScheme(background = Color(0xFFF5F5F5), surface = Color.White)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

private fun hotkeyToJson(h: HotkeyConfig): JSONObject = JSONObject().apply {
    put("name", h.name)
    put("enabled", h.enabled)
    put("trigger", h.trigger)
    put("autoAim", h.autoAim)
    put("aimMode", h.aimMode)
    put("aimByCategory", h.aimByCategory)
    put("enabledCategories", JSONArray().apply { h.enabledCategories.forEach { put(it) } })
    put("categoryPriorityEnabled", h.categoryPriorityEnabled)
    put("categoryOrder", JSONArray().apply { h.categoryOrder.forEach { put(it) } })
    put("yOffset", JSONArray().apply { h.yOffset.forEach { put(it) } })
    put("autoFire", h.autoFire)
    put("fireRangePx", h.fireRangePx.toDouble())
    put("initialDelayMs", h.initialDelayMs)
    put("minClick", h.minClick)
    put("maxClick", h.maxClick)
    put("minIntervalMs", h.minIntervalMs)
    put("maxIntervalMs", h.maxIntervalMs)
    put("burstIntervalMs", h.burstIntervalMs)
    put("sensitivity", h.sensitivity.toDouble())
}

private fun parseHotkeys(arr: JSONArray?): MutableList<HotkeyConfig> {
    if (arr == null) return mutableListOf(HotkeyConfig("热键1"), HotkeyConfig("热键2"), HotkeyConfig("热键3"))
    val list = mutableListOf<HotkeyConfig>()
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val enabledCategories = parseBoolArray(obj.optJSONArray("enabledCategories"), listOf(true, true, true, true))
        val categoryOrder = normalizeCategoryOrder(parseIntArray(obj.optJSONArray("categoryOrder"), listOf(0, 1, 2, 3)))
        val yOffset = parseIntArray(obj.optJSONArray("yOffset"), listOf(79, 50, 0, 0)).map { it.coerceIn(0, 100) }
        val aimMode = obj.optString("aimMode", "class_priority")
        val minClick = obj.optInt("minClick", 1).coerceIn(1, 10)
        val maxClick = obj.optInt("maxClick", minClick).coerceIn(minClick, 10)
        val minInterval = obj.optInt("minIntervalMs", 50).coerceIn(10, 500)
        val maxInterval = obj.optInt("maxIntervalMs", 100).coerceIn(minInterval, 500)
        list += HotkeyConfig(
            name = "热键${i + 1}",
            enabled = obj.optBoolean("enabled", false),
            trigger = obj.optString("trigger", "右").ifBlank { "右" },
            autoAim = obj.optBoolean("autoAim", false),
            aimMode = if (aimMode == "recent_crosshair") "recent_crosshair" else "class_priority",
            aimByCategory = obj.optBoolean("aimByCategory", false),
            enabledCategories = enabledCategories,
            categoryPriorityEnabled = obj.optBoolean("categoryPriorityEnabled", false),
            categoryOrder = categoryOrder,
            yOffset = yOffset,
            autoFire = obj.optBoolean("autoFire", false),
            fireRangePx = obj.optDouble("fireRangePx", 5.0).toFloat().coerceIn(0f, 30f),
            initialDelayMs = obj.optInt("initialDelayMs", 50).coerceIn(0, 500),
            minClick = minClick,
            maxClick = maxClick,
            minIntervalMs = minInterval,
            maxIntervalMs = maxInterval,
            burstIntervalMs = obj.optInt("burstIntervalMs", 200).coerceIn(10, 1000),
            sensitivity = obj.optDouble("sensitivity", 0.5).toFloat().coerceIn(0f, 1f)
        )
    }
    while (list.size < 3) list += HotkeyConfig("热键${list.size + 1}")
    while (list.size > 3) list.removeLast()
    return list
}

private fun parseBoolArray(arr: JSONArray?, default: List<Boolean>): List<Boolean> {
    if (arr == null) return default
    val result = MutableList(default.size) { idx ->
        if (idx < arr.length()) arr.optBoolean(idx, default[idx]) else default[idx]
    }
    return result
}

private fun parseIntArray(arr: JSONArray?, default: List<Int>): List<Int> {
    if (arr == null) return default
    val result = MutableList(default.size) { idx ->
        if (idx < arr.length()) arr.optInt(idx, default[idx]) else default[idx]
    }
    return result
}

private fun normalizeCategoryOrder(raw: List<Int>): List<Int> {
    val filtered = raw.filter { it in 0..3 }.distinct().toMutableList()
    for (i in 0..3) if (!filtered.contains(i)) filtered += i
    return filtered.take(4)
}

private fun copyModelToPrivateDir(context: Context, uri: Uri): String? {
    val fileName = resolveDisplayName(context.contentResolver, uri).ifBlank { "model_${System.currentTimeMillis()}" }
    val dir = File(context.filesDir, MODEL_DIR).apply { mkdirs() }
    val target = File(dir, fileName)
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        fileName
    }.getOrNull()
}

private fun resolveDisplayName(resolver: ContentResolver, uri: Uri): String {
    val byQuery = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else ""
        } ?: ""
    }.getOrDefault("")
    if (byQuery.isNotBlank()) return byQuery
    return uri.lastPathSegment?.substringAfterLast('/') ?: ""
}

private fun scanModelEntries(context: Context): List<ModelEntry> {
    val dir = File(context.filesDir, MODEL_DIR).apply { mkdirs() }
    val files = dir.listFiles()?.filter { it.isFile } ?: return emptyList()

    data class PairHolder(var param: File? = null, var bin: File? = null)
    val map = mutableMapOf<String, PairHolder>()
    files.forEach { file ->
        when (file.extension.lowercase()) {
            "param" -> map.getOrPut(file.nameWithoutExtension) { PairHolder() }.param = file
            "bin" -> map.getOrPut(file.nameWithoutExtension) { PairHolder() }.bin = file
        }
    }

    val result = mutableListOf<ModelEntry>()
    val pairedBases = mutableSetOf<String>()
    map.toSortedMap().forEach { (base, holder) ->
        if (holder.param != null && holder.bin != null) {
            result += ModelEntry(
                name = "$base (NCNN)",
                kind = ModelKind.NCNN,
                paramPath = holder.param!!.absolutePath,
                binPath = holder.bin!!.absolutePath
            )
            pairedBases += base
        }
    }

    files.sortedBy { it.name.lowercase() }.forEach { file ->
        val ext = file.extension.lowercase()
        val base = file.nameWithoutExtension
        if ((ext == "param" || ext == "bin") && pairedBases.contains(base)) return@forEach
        val kind = when (ext) {
            "onnx" -> ModelKind.ONNX
            else -> ModelKind.FILE
        }
        result += ModelEntry(name = file.name, kind = kind, filePath = file.absolutePath)
    }

    return result
}

object NetworkUtils {
    fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
            "No IP"
        } catch (_: Exception) {
            "No IP"
        }
    }
}

