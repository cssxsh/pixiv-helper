package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.descriptor.CommandValueArgumentParser
import net.mamoe.mirai.console.command.descriptor.buildCommandArgumentContext
import xyz.cssxsh.pixiv.*
import java.time.*

internal val PixivCommandArgumentContext = buildCommandArgumentContext {
    RankMode::class with object : CommandValueArgumentParser<RankMode> {
        override fun parse(raw: String, sender: CommandSender): RankMode = enumValueOf(raw.toUpperCase())
    }
    WorkContentType::class with object : CommandValueArgumentParser<WorkContentType> {
        override fun parse(raw: String, sender: CommandSender): WorkContentType = enumValueOf(raw.toUpperCase())
    }
    LocalDate::class with object : CommandValueArgumentParser<LocalDate> {
        override fun parse(raw: String, sender: CommandSender): LocalDate = LocalDate.parse(raw)
    }
    Year::class with object : CommandValueArgumentParser<Year> {
        override fun parse(raw: String, sender: CommandSender): Year = Year.parse(raw)
    }
    YearMonth::class with object : CommandValueArgumentParser<YearMonth> {
        override fun parse(raw: String, sender: CommandSender): YearMonth = YearMonth.parse(raw)
    }
    LongRange::class with object : CommandValueArgumentParser<LongRange> {

        private val RANGE_REGEX = """(\d+)\.{2,4}(\d+)""".toRegex()

        override fun parse(raw: String, sender: CommandSender): LongRange =
            requireNotNull(RANGE_REGEX.find(raw)).destructured.let { (start, end) -> start.toLong()..end.toLong() }
    }
}