package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.NaviRankRecord
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

    @SubCommand("year", "年", "年榜")
    @Description("获取NaviRank年榜并随机")
    suspend fun CommandSenderOnMessage<*>.year(year: Year) = sendIllust {
        NaviRank.getAllRank(year = year).records.also {
            addCacheJob(name = "NAVIRANK[${year}]", reply = false) {
                getListIllusts(info = it)
            }
        }.cached().random()
    }

    @SubCommand("month", "月", "月榜")
    @Description("获取NaviRank月榜并随机")
    suspend fun CommandSenderOnMessage<*>.month(month: YearMonth) = sendIllust {
        NaviRank.getAllRank(month = month).records.also {
            addCacheJob(name = "NAVIRANK[${month}]", reply = false) {
                getListIllusts(info = it)
            }
        }.cached().random()
    }

    @SubCommand("tag", "标签")
    @Description("获取NaviRank标签榜并随机")
    suspend fun CommandSenderOnMessage<*>.tag(vararg words: String) = sendIllust {
        NaviRank.getTagRank(words = words).records.also {
            addCacheJob(name = "NAVIRANK[${words}]", reply = false) {
                getListIllusts(info = it)
            }
        }.cached().random()
    }
}