package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import java.time.temporal.*
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

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

object TemporalCommandArgumentContext : CommandArgumentContext {
    private val cache = WeakHashMap<Class<*>, CommandValueArgumentParser<*>>()
    private val temporalKlass = Temporal::class

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(kClass: KClass<T>): CommandValueArgumentParser<T>? {
        if (kClass.isSubclassOf(temporalKlass).not()) return null

        var parser = cache[kClass.java] as? CommandValueArgumentParser<T>

        if (parser != null) return parser

        val parse = kClass.staticFunctions.find { function ->
            function.name == "parse" && function.returnType.jvmErasure == kClass && function.parameters.size == 1
        } ?: return null

        parser = RawValueArgumentParser(kClass) { raw -> parse.call(raw) as T }

        cache[kClass.java] = parser

        return parser
    }

    override fun toList(): List<CommandArgumentContext.ParserPair<*>> = emptyList()
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

internal val PixivCommandArgumentContext = TemporalCommandArgumentContext + RangeCommandArgumentContext