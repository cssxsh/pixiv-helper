package xyz.cssxsh.mirai.pixiv.command

import com.cronutils.model.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import xyz.cssxsh.mirai.pixiv.task.*
import java.time.*
import kotlin.reflect.*

public class RawValueArgumentParser<T : Any>(private val kClass: KClass<T>, public val parse: (raw: String) -> T) :
    AbstractCommandValueArgumentParser<T>() {
    override fun parse(raw: String, sender: CommandSender): T {
        return try {
            parse(raw)
        } catch (e: Throwable) {
            illegalArgument("无法解析 $raw 为${kClass.simpleName}", e)
        }
    }
}


internal val RANGE_REGEX = """(\d+)(?:\.{2,4}|-|~)(\d+)""".toRegex()

public val PixivCommandArgumentContext: CommandArgumentContext = buildCommandArgumentContext {
    LongRange::class with RawValueArgumentParser(LongRange::class) { raw ->
        requireNotNull(RANGE_REGEX.find(raw)) { "未找到$RANGE_REGEX" }
            .destructured.let { (start, end) -> start.toLong()..end.toLong() }
    }
    IntRange::class with RawValueArgumentParser(IntRange::class) { raw ->
        requireNotNull(RANGE_REGEX.find(raw)) { "未找到$RANGE_REGEX" }
            .destructured.let { (start, end) -> start.toInt()..end.toInt() }
    }
    Cron::class with { text ->
        try {
            DefaultCronParser.parse(text)
        } catch (cause: Throwable) {
            throw CommandArgumentParserException(
                message = cause.message ?: "Cron 表达式读取错误，建议找在线表达式生成器生成",
                cause = cause
            )
        }
    }
    Duration::class with { text ->
        try {
            Duration.parse(text)
        } catch (cause: Throwable) {
            throw CommandArgumentParserException(
                message = cause.message ?: "Duration 表达式格式为 PnDTnHnMn.nS",
                cause = cause
            )
        }
    }
}