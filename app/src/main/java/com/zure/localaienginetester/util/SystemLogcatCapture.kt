package com.zure.localaienginetester.util

import com.zure.localaienginetester.util.AppLog.Level
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * 系统日志采集器。
 *
 * 在 Release 模式下启动守护线程读取 `logcat --pid -v threadtime`，
 * 经过滤/折叠/限流/噪声规则处理后，通过 [AppLog] 混入业务日志文件。
 *
 * 移植自 C2Android LogUtil.java，适配 Kotlin + BqLog 架构。
 */
object SystemLogcatCapture {

    // ── 配置常量 ──

    /** 系统日志 Tag 标识，用于在日志文件中区分业务日志和系统采集日志。 */
    private const val SYSTEM_LOGCAT_TAG = "SystemLogcat"

    /** 相似日志聚合窗口（ms），同类日志在窗口内首条保留、后续折叠。 */
    private const val SIMILAR_LOG_WINDOW_MS = 5_000L

    /** 全局速率保护窗口（ms），避免大量不同系统日志同时刷屏。 */
    private const val RATE_WINDOW_MS = 5_000L

    /** 单个速率窗口内最多写入的非错误级别系统日志行数。 */
    private const val MAX_LINES_PER_WINDOW = 50

    /** 相似日志聚合桶数量上限，限制多 key 折叠策略的常驻内存占用。 */
    private const val MAX_SIMILAR_BUCKETS = 64

    /** 输出折叠摘要的最低重复数，避免"只折叠 1 条"的摘要反而制造噪声。 */
    private const val MIN_SUPPRESSED_TO_SUMMARIZE = 2

    /** 折叠摘要中 sample 的最大长度，防止超长系统日志借摘要刷屏。 */
    private const val MAX_SAMPLE_LENGTH = 240

    // ── 正则规则 ──

    /** threadtime 格式前缀匹配，时间/pid/tid/级别等动态字段不参与相似判断。 */
    private val THREADTIME_PREFIX_PATTERN: Pattern = Pattern.compile(
        "^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+\\d+\\s+\\d+\\s+[VDIWEF]\\s+"
    )

    /** 完整 threadtime 行解析，拆出级别、tag 和正文。 */
    private val THREADTIME_LINE_PATTERN: Pattern = Pattern.compile(
        "^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+\\d+\\s+\\d+\\s+([VDIWEF])\\s+([^:]+):\\s?(.*)$"
    )

    /** 十六进制地址归一化，把运行时变化的内存地址/buffer id 统一。 */
    private val HEX_PATTERN: Pattern = Pattern.compile("0x[0-9a-fA-F]+")

    /** 普通数字归一化，帧号/计数/尺寸/pid 等频繁变化的字段统一。 */
    private val NUMBER_PATTERN: Pattern = Pattern.compile("\\b\\d+\\b")

    /** 空白字符归一化，避免因空格或换行差异导致同类日志无法折叠。 */
    private val WHITESPACE_PATTERN: Pattern = Pattern.compile("\\s+")

    // ── 噪声规则表 ──

    /**
     * 可扩展的系统噪声规则表。
     * 后续遇到明确无排查价值但高频刷屏的系统 tag 时，在此追加。
     * 规则只影响 SystemLogcat 捕获链路，不影响业务侧 AppLog.v/d/i/w/e。
     */
    private val NOISE_RULES: Array<NoiseRule> = arrayOf(
        NoiseRule(
            "BufferQueueProducer",
            Pattern.compile(".*waitForFreeSlotThenRelock\\s+TIMED_OUT.*"),
            "BufferQueueProducer:waitForFreeSlotThenRelock"
        ),
        NoiseRule(
            "BufferQueueProducer",
            Pattern.compile(".*BQDUMP.*"),
            "BufferQueueProducer:BQDUMP"
        ),
        NoiseRule(
            "BLASTBufferQueue",
            Pattern.compile(".*releaseBufferCallbackThunk.*blastBufferQueue is dead.*"),
            "BLASTBufferQueue:deadReleaseCallback"
        )
    )

    // ── 过滤规则表 ──

    /**
     * 系统日志静默过滤规则。
     * 仅丢弃 V/D/I/W 中明确与业务排查无关的框架/ROM/媒体/渲染流水日志，
     * 不影响错误级别日志，也不写过滤统计摘要。
     */
    private val FILTER_RULES: Array<FilterRule> = arrayOf(
        // 多媒体渲染相关
        FilterRule(
            Pattern.compile("BufferQueueProducer|BufferQueueConsumer|BLASTBufferQueue|CCodecBuffers"),
            Pattern.compile(".*")
        ),
        // View/渲染相关
        FilterRule(
            Pattern.compile("ViewRootImpl\\[.*\\]|View|OpenGLRenderer"),
            Pattern.compile(".*")
        ),
        // Surface/图形/电源相关
        FilterRule(
            Pattern.compile("SurfaceUtils|SurfaceFactory|MsyncFactory|OpenMsyncAppList|PowerHalWrapper|GraphicsEnvironment"),
            Pattern.compile(".*")
        ),
        // 音视频编解码相关
        FilterRule(
            Pattern.compile("AudioTrack|AudioTrackShared|AudioCapabilities|VideoCapabilities|MediaPlayer"),
            Pattern.compile(".*")
        ),
        // MediaCodec/CCodec 相关
        FilterRule(
            Pattern.compile("MediaCodec|CCodec|CCodecConfig|Codec2Client|Codec2-OutputBufferQueue|CCodecBufferChannel|DMCodecAdapterFactory|ReflectedParamUpdater|BufferPoolAccessor2\\.0"),
            Pattern.compile(".*")
        ),
        // 兼容性/安全/安装相关
        FilterRule(
            Pattern.compile("CompatibilityChangeReporter|NetworkSecurityConfig|AppCompatDelegate|ProfileInstaller|MetadataUtil"),
            Pattern.compile(".*")
        ),
        // GPU/Skia/native 相关
        FilterRule(
            Pattern.compile("libMEOW|mali|skia|libjpeg-alpha|DMABUFHEAPS|nativeloader"),
            Pattern.compile(".*")
        ),
        // OkHttp via System.out
        FilterRule(
            Pattern.compile("System\\.out"),
            Pattern.compile(".*\\[(okhttp|OkHttp)].*")
        ),
        // WebView/Chromium 相关
        FilterRule(
            Pattern.compile("WebViewFactory|cr_LibraryLoader|cr_CachingUmaRecorder|cr_media|cr_AwContents"),
            Pattern.compile(".*")
        ),
        // 本 App hidden API 访问日志
        FilterRule(
            Pattern.compile("zure\\.localaiengine"),
            Pattern.compile(".*Accessing hidden .*")
        )
    )

    // ── 状态 ──

    private val started = AtomicBoolean(false)
    private var captureThread: Thread? = null

    /**
     * 静默过滤开关，后续可由服务器指令动态控制。
     */
    @Volatile
    var filterEnabled = true

    /**
     * 折叠/限流开关，后续可由服务器指令动态控制。
     */
    @Volatile
    var foldEnabled = true

    /**
     * 启动系统日志采集守护线程。
     * 仅在 Release 模式且未启动过时生效。
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return

        captureThread = Thread({ captureSystemLogcat() }, "SystemLogcatCapture").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * 停止系统日志采集。
     */
    fun stop() {
        if (!started.compareAndSet(true, false)) return
        // 守护线程会在 logcat 进程销毁后自行退出
        captureThread = null
    }

    // ── 采集主循环 ──

    private fun captureSystemLogcat() {
        var logcatProcess: java.lang.Process? = null
        val limiter = SystemLogcatLimiter()

        try {
            logcatProcess = ProcessBuilder("logcat", "--pid=${android.os.Process.myPid()}", "-v", "threadtime")
                .redirectErrorStream(true)
                .start()

            BufferedReader(InputStreamReader(logcatProcess.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    for (entry in limiter.accept(currentLine)) {
                        printSystemLogcat(entry.level, entry.message)
                    }
                }
            }
        } catch (e: Exception) {
            // 部分设备 ROM 可能限制 logcat 命令，失败时只记录说明，不影响 App 主流程。
            printSystemLogcat(Level.WARN, "System logcat capture unavailable: ${e.message}")
        } finally {
            for (entry in limiter.flush()) {
                printSystemLogcat(entry.level, entry.message)
            }
            logcatProcess?.destroy()
            started.set(false)
        }
    }

    /**
     * 系统日志经 AppLog 写入文件，与业务日志混入同一文件。
     */
    private fun printSystemLogcat(level: Level, msg: String) {
        when (level) {
            Level.VERBOSE -> AppLog.v(SYSTEM_LOGCAT_TAG, msg)
            Level.DEBUG -> AppLog.d(SYSTEM_LOGCAT_TAG, msg)
            Level.INFO -> AppLog.i(SYSTEM_LOGCAT_TAG, msg)
            Level.WARN -> AppLog.w(SYSTEM_LOGCAT_TAG, msg)
            Level.ERROR -> AppLog.e(SYSTEM_LOGCAT_TAG, msg)
        }
    }

    // ── 解析与归一化 ──

    private fun parseLogcatLine(line: String): LogcatLine {
        val matcher = THREADTIME_LINE_PATTERN.matcher(line)
        if (matcher.matches()) {
            return LogcatLine(line, parseLevel(matcher.group(1)), matcher.group(2)?.trim().orEmpty(), matcher.group(3) ?: "")
        }
        return LogcatLine(line, Level.DEBUG, "", line)
    }

    private fun parseLevel(level: String?): Level {
        return when (level) {
            "V" -> Level.VERBOSE
            "D" -> Level.DEBUG
            "I" -> Level.INFO
            "W" -> Level.WARN
            "E", "F" -> Level.ERROR
            else -> Level.DEBUG
        }
    }

    private fun isErrorLevel(level: Level): Boolean = level == Level.ERROR

    /**
     * 归一化系统日志行：去掉易变化字段，使同类日志能匹配到相同 key。
     */
    private fun normalizeLine(line: String): String {
        var normalized = THREADTIME_PREFIX_PATTERN.matcher(line).replaceFirst("")
        normalized = HEX_PATTERN.matcher(normalized).replaceAll("0x#")
        normalized = NUMBER_PATTERN.matcher(normalized).replaceAll("#")
        return WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ").trim()
    }

    /**
     * 构建系统日志聚合 key：噪声规则用稳定 key，其他用归一化后的文本。
     */
    private fun buildLogcatKey(line: LogcatLine): String {
        for (rule in NOISE_RULES) {
            if (rule.matches(line)) {
                return "noisy:${rule.key}"
            }
        }
        val key = normalizeLine(line.raw)
        return key.ifEmpty { line.raw }
    }

    /**
     * 判断是否应静默过滤该系统日志（仅过滤非错误级别）。
     */
    private fun shouldFilter(line: LogcatLine): Boolean {
        if (isErrorLevel(line.level)) return false
        return FILTER_RULES.any { it.matches(line) }
    }

    private fun limitSample(sample: String): String {
        if (sample.length <= MAX_SAMPLE_LENGTH) return sample
        return sample.substring(0, MAX_SAMPLE_LENGTH) + "..."
    }

    // ── 内部数据类 ──

    private class LogcatLine(
        val raw: String,
        val level: Level,
        val tag: String,
        val message: String
    )

    private class NoiseRule(
        private val tag: String,
        private val messagePattern: Pattern,
        val key: String
    ) {
        fun matches(line: LogcatLine): Boolean {
            return tag == line.tag && messagePattern.matcher(line.message).matches()
        }
    }

    private class FilterRule(
        private val tagPattern: Pattern,
        private val messagePattern: Pattern
    ) {
        fun matches(line: LogcatLine): Boolean {
            return tagPattern.matcher(line.tag).matches()
                    && messagePattern.matcher(line.message).matches()
        }
    }

    private class LogcatEntry(
        val level: Level,
        val message: String
    )

    private class SimilarBucket(
        val sample: String,
        val startMs: Long
    ) {
        var suppressedCount: Int = 0
    }

    // ── 限流器 ──

    /**
     * 系统日志限流器。
     *
     * 三重降噪机制：
     * 1. 相似日志折叠：5s 窗口内同类日志首条保留，后续计数，窗口结束输出摘要
     * 2. 全局限流：5s 窗口内非错误级别最多 50 条，超出计入限流摘要
     * 3. 噪声规则：特定 tag+msg 使用稳定 key 聚合，把动态字段多的多行 dump 归成一类
     */
    private class SystemLogcatLimiter {
        private val similarBuckets = LinkedHashMap<String, SimilarBucket>()
        private var rateWindowStartMs = System.currentTimeMillis()
        private var emittedInRateWindow = 0
        private var rateSuppressedCount = 0

        fun accept(line: String): List<LogcatEntry> {
            val output = mutableListOf<LogcatEntry>()
            val now = System.currentTimeMillis()

            val parsedLine = parseLogcatLine(line)

            // 静默过滤
            if (filterEnabled && shouldFilter(parsedLine)) {
                return output
            }

            // 折叠关闭时直接放行
            if (!foldEnabled) {
                output.add(LogcatEntry(parsedLine.level, line))
                return output
            }

            flushRateIfExpired(now, output)
            flushSimilarIfExpired(now, output)

            // 归一化后按相似 key 聚合
            val logcatKey = buildLogcatKey(parsedLine)
            val existingBucket = similarBuckets[logcatKey]
            if (existingBucket != null) {
                // 首条完整日志已保留，后续只计数
                existingBucket.suppressedCount++
                return output
            }

            // 全局限流（错误级别不受限）
            if (!isErrorLevel(parsedLine.level) && emittedInRateWindow >= MAX_LINES_PER_WINDOW) {
                rateSuppressedCount++
                return output
            }

            ensureBucketCapacity(output)

            // 新 key 首次出现时完整保留
            similarBuckets[logcatKey] = SimilarBucket(line, now)
            emittedInRateWindow++
            output.add(LogcatEntry(parsedLine.level, line))
            return output
        }

        fun flush(): List<LogcatEntry> {
            val output = mutableListOf<LogcatEntry>()
            if (!foldEnabled) return output
            flushAllSimilar(output)
            flushRate(output)
            return output
        }

        private fun flushSimilarIfExpired(now: Long, output: MutableList<LogcatEntry>) {
            val iterator = similarBuckets.entries.iterator()
            while (iterator.hasNext()) {
                val bucket = iterator.next().value
                if (now - bucket.startMs >= SIMILAR_LOG_WINDOW_MS) {
                    flushBucket(bucket, output)
                    iterator.remove()
                }
            }
        }

        private fun flushAllSimilar(output: MutableList<LogcatEntry>) {
            for (bucket in similarBuckets.values) {
                flushBucket(bucket, output)
            }
            similarBuckets.clear()
        }

        private fun flushBucket(bucket: SimilarBucket, output: MutableList<LogcatEntry>) {
            if (bucket.suppressedCount >= MIN_SUPPRESSED_TO_SUMMARIZE) {
                val message = String.format(
                    Locale.US,
                    "[logcat suppressed] %d similar lines in %dms, sample=%s",
                    bucket.suppressedCount,
                    SIMILAR_LOG_WINDOW_MS,
                    limitSample(bucket.sample)
                )
                output.add(LogcatEntry(Level.WARN, message))
            }
        }

        private fun ensureBucketCapacity(output: MutableList<LogcatEntry>) {
            while (similarBuckets.size >= MAX_SIMILAR_BUCKETS) {
                val iterator = similarBuckets.entries.iterator()
                if (!iterator.hasNext()) return
                val oldestBucket = iterator.next().value
                flushBucket(oldestBucket, output)
                iterator.remove()
            }
        }

        private fun flushRateIfExpired(now: Long, output: MutableList<LogcatEntry>) {
            if (now - rateWindowStartMs >= RATE_WINDOW_MS) {
                flushRate(output)
                rateWindowStartMs = now
                emittedInRateWindow = 0
            }
        }

        private fun flushRate(output: MutableList<LogcatEntry>) {
            if (rateSuppressedCount > 0) {
                val message = String.format(
                    Locale.US,
                    "[logcat rate limited] %d lines suppressed in %dms",
                    rateSuppressedCount,
                    RATE_WINDOW_MS
                )
                output.add(LogcatEntry(Level.WARN, message))
                rateSuppressedCount = 0
            }
        }
    }
}
