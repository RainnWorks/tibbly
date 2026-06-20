package co.rowm.osrsllm

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy
import ch.qos.logback.core.util.FileSize
import net.runelite.client.RuneLite
import org.slf4j.LoggerFactory
import java.io.File

object LoggingSetup {

    private const val APPENDER_NAME = "osrs-llm-helper-file"
    private const val LOGGER_NAME = "co.rowm.osrsllm"

    /**
     * Attaches a rolling file appender to our package logger. Logs also propagate
     * to RuneLite's root logger so they show up in client.log too.
     *
     * Safe to call multiple times — second call is a no-op.
     */
    fun configure(): File? {
        return try {
            val context = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return null
            val targetLogger = context.getLogger(LOGGER_NAME)
            if (targetLogger.getAppender(APPENDER_NAME) != null) {
                return null
            }

            val logDir = File(RuneLite.RUNELITE_DIR, "osrs-llm-helper/logs").apply { mkdirs() }
            val logFile = File(logDir, "plugin.log")

            val encoder = PatternLayoutEncoder().apply {
                this.context = context
                pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{32} - %msg%n"
                start()
            }

            val appender = RollingFileAppender<ILoggingEvent>().apply {
                this.context = context
                name = APPENDER_NAME
                file = logFile.absolutePath
                this.encoder = encoder
                isAppend = true
            }

            val rollingPolicy = FixedWindowRollingPolicy().apply {
                this.context = context
                setParent(appender)
                fileNamePattern = File(logDir, "plugin.%i.log").absolutePath
                minIndex = 1
                maxIndex = 5
                start()
            }
            appender.rollingPolicy = rollingPolicy

            val triggerPolicy = SizeBasedTriggeringPolicy<ILoggingEvent>().apply {
                this.context = context
                setMaxFileSize(FileSize.valueOf("2MB"))
                start()
            }
            appender.triggeringPolicy = triggerPolicy

            appender.start()
            targetLogger.addAppender(appender)
            targetLogger.level = Level.DEBUG
            targetLogger.isAdditive = true

            targetLogger.info("Dedicated log file: {}", logFile.absolutePath)
            logFile
        } catch (t: Throwable) {
            LoggerFactory.getLogger(LoggingSetup::class.java)
                .warn("Failed to set up dedicated log appender", t)
            null
        }
    }
}
