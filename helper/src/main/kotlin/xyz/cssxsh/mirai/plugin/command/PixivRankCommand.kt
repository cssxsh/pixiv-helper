package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.mirai.plugin.tools.NaviRank
import java.time.Year
import java.time.YearMonth

object PixivRankCommand: CompositeCommand(
    owner = PixivHelperPlugin,
    "rank", "排行",
    description = "PIXIV排行指令，通过http://pixiv.navirank.com/",
    overrideContext = PixivCommandArgumentContext
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private fun List<NaviRankRecord>.cached() = useMappers { mappers ->
        mapNotNull { record ->
            mappers.artwork.findByPid(record.pid)
        }
    }

    private suspend fun NaviRank.rank(year: Year, vararg words: String) =
        if (words.isNotEmpty()) getTagRank(year = year, words = words) else getAllRank(year = year)

    private suspend fun NaviRank.rank(month: YearMonth, vararg words: String) =
        if (words.isNotEmpty()) getTagRank(month = month, words = words) else getAllRank(month = month)

    private suspend fun NaviRankAllTime.cached(helper: PixivHelper): ArtWorkInfo {
        helper.addCacheJob(name = "NAVIRANK[${title}]", reply = false) {
            getListIllusts(info = records)
        }
        return records.cached().random()
    }

    @SubCommand("year", "年", "年榜")
    @Description("获取NaviRank年榜并随机")
    suspend fun CommandSenderOnMessage<*>.year(year: Year, vararg words: String) = sendIllust {
        NaviRank.rank(year = year, words = words).cached(helper = this)
    }

    @SubCommand("month", "月", "月榜")
    @Description("获取NaviRank月榜并随机")
    suspend fun CommandSenderOnMessage<*>.month(month: YearMonth, vararg words: String) = sendIllust {
        NaviRank.rank(month = month, words = words).cached(helper = this)
    }

    @SubCommand("tag", "标签")
    @Description("获取NaviRank标签榜并随机")
    suspend fun CommandSenderOnMessage<*>.tag(vararg words: String) = sendIllust {
        NaviRank.getTagRank(words = words).cached(helper = this)
    }
}