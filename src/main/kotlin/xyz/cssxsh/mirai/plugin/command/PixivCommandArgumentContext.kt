package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import kotlin.reflect.*

class RawValueArgumentParser<T : Any>(private val kClass: KClass<T>, val parse: (raw: String) -> T) :
    AbstractCommandValueArgumentParser<T>() {
    override fun parse(raw: String, sender: CommandSender): T {
        return try {
            parse(raw)
        } catch (e: Throwable) {
            illegalArgument("无法解析 $raw 为${kClass.simpleName}", e)
        }
    }
}

val RangeCommandArgumentContext = buildCommandArgumentContext {
    val regex = """(\d+)(?:\.{2,4}|-|~)(\d+)""".toRegex()
    LongRange::class with RawValueArgumentParser(LongRange::class) { raw ->
        requireNotNull(regex.find(raw)) { "未找到$regex" }.destructured.let { (start, end) -> start.toLong()..end.toLong() }
    }
    IntRange::class with RawValueArgumentParser(IntRange::class) { raw ->
        requireNotNull(regex.find(raw)) { "未找到$regex" }.destructured.let { (start, end) -> start.toInt()..end.toInt() }
    }

}

internal val PixivCommandArgumentContext = RangeCommandArgumentContext