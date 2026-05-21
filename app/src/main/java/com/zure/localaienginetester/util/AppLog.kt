package com.zure.localaienginetester.util

import com.zure.localaienginetester.BuildConfig
import bq.log

/**
 * 全局日志工具。
 *
 * 通过 BuildConfig 编译期常量控制输出目标，由 BqLog appender 机制分发：
 * - [BuildConfig.LOGCAT_ENABLED]：是否输出 logcat（BqLog Console appender）
 * - [BuildConfig.LOG_FILE_ENABLED]：是否写入文件（Debug → TextFile，Release → CompressedFile）
 * - [BuildConfig.SYSTEM_LOGCAT_FILE_ENABLED]：是否采集系统日志（仅文件模式有意义）
 */
object AppLog {

    // ── 配置常量 ──

    /** 全局 Tag 前缀，业务 Tag 会拼接在其后，便于 Logcat 快速筛选。 */
    const val GLOBAL_TAG = "LocalAIEngine"

    /** 单个日志文件大小上限（10MB），达到后 BqLog 自动切分新文件。 */
    const val LOG_FILE_MAX_SIZE_BYTES = 10L * 1024 * 1024

    /** 日志文件保留天数，超期由 BqLog 自动清理。 */
    const val LOG_RETENTION_DAYS = 7

    /** 日志目录总容量上限（100MB），超出后 BqLog 自动删除最旧文件。 */
    const val LOG_DIR_CAPACITY_BYTES = 100L * 1024 * 1024

    /** 日志文件相对路径（由 BqLog base_dir_type=0 解析到 getFilesDir() 下）。 */
    private const val LOG_FILE_DIR = "bqLog/app"

    private var bqLog: log? = null

    /**
     * 初始化日志系统。
     *
     * 内部直接读取 [BuildConfig] 编译期常量决定 BqLog appender 组合，
     * 无需调用方传参，编译期即可确定所有输出行为。
     */
    fun init() {
        if (bqLog != null) return

        val logcatEnabled = BuildConfig.LOGCAT_ENABLED
        val fileEnabled = BuildConfig.LOG_FILE_ENABLED

        val config = buildString {
            // Console appender：Logcat 输出
            if (logcatEnabled) {
                appendLine("appenders_config.console.type=console")
                appendLine("appenders_config.console.levels=[all]")
            }

            // File appender：Debug 用 TextFile，Release 用 CompressedFile
            if (fileEnabled) {
                val fileType = if (BuildConfig.DEBUG) "text_file" else "compressed_file"
                appendLine("appenders_config.file.type=$fileType")
                appendLine("appenders_config.file.file_name=$LOG_FILE_DIR")
                appendLine("appenders_config.file.levels=[all]")
                appendLine("appenders_config.file.max_file_size=$LOG_FILE_MAX_SIZE_BYTES")
                appendLine("appenders_config.file.expire_time_days=$LOG_RETENTION_DAYS")
                appendLine("appenders_config.file.capacity_limit=$LOG_DIR_CAPACITY_BYTES")
                appendLine("appenders_config.file.enable_rolling_log_file=true")
                appendLine("appenders_config.file.always_create_new_file=true")
                appendLine("appenders_config.file.base_dir_type=0")
            }

            // 兜底：两个开关都关时至少保证 console 输出
            if (!logcatEnabled && !fileEnabled) {
                appendLine("appenders_config.console.type=console")
                appendLine("appenders_config.console.levels=[all]")
            }
        }.trimIndent()

        bqLog = log.create_log(GLOBAL_TAG, config)

        // 系统日志采集：仅在文件写入开启时启动，否则采集结果无处写入
        if (BuildConfig.SYSTEM_LOGCAT_FILE_ENABLED && fileEnabled) {
            SystemLogcatCapture.start()
        }
    }

    // ── 业务日志 API ──

    fun v(tag: String, msg: String) {
        print(Level.VERBOSE, tag, msg)
    }

    fun d(tag: String, msg: String) {
        print(Level.DEBUG, tag, msg)
    }

    fun i(tag: String, msg: String) {
        print(Level.INFO, tag, msg)
    }

    fun w(tag: String, msg: String) {
        print(Level.WARN, tag, msg)
    }

    fun e(tag: String, msg: String) {
        print(Level.ERROR, tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        val outputMsg = msg + "\n" + android.util.Log.getStackTraceString(tr)
        print(Level.ERROR, tag, outputMsg)
    }

    /**
     * 强制刷盘，确保缓冲区日志写入磁盘。
     * 仅在文件写入开启时有效（Console appender 不需要刷盘）。
     */
    fun flush() {
        if (BuildConfig.LOG_FILE_ENABLED) {
            bqLog?.force_flush()
        }
    }

    // ── 内部方法 ──

    /**
     * 统一输出入口：所有日志经 BqLog 分发，由 appender 配置决定实际输出目标。
     */
    private fun print(level: Level, tag: String, msg: String) {
        if (msg.isEmpty()) return
        val logger = bqLog ?: return
        val taggedMsg = "[$GLOBAL_TAG-$tag] $msg"
        when (level) {
            Level.VERBOSE -> logger.verbose(taggedMsg)
            Level.DEBUG -> logger.debug(taggedMsg)
            Level.INFO -> logger.info(taggedMsg)
            Level.WARN -> logger.warning(taggedMsg)
            Level.ERROR -> logger.error(taggedMsg)
        }
    }

    /**
     * 日志级别，与 BqLog 和 android.util.Log 对齐。
     */
    internal enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
}
