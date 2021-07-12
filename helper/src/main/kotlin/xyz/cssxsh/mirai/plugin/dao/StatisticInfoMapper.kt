package xyz.cssxsh.mirai.plugin.dao

import xyz.cssxsh.mirai.plugin.model.*

interface StatisticInfoMapper {
    fun replaceEroInfo(info: StatisticEroInfo): Boolean
    fun senderEroInfos(sender: Long): List<StatisticEroInfo>
    fun groupEroInfos(group: Long): List<StatisticEroInfo>
    fun replaceTagInfo(info: StatisticTagInfo): Boolean
    fun senderTagInfos(sender: Long): List<StatisticTagInfo>
    fun groupTagInfos(group: Long): List<StatisticTagInfo>
    fun replaceSearchResult(result: PixivSearchResult): Boolean
    fun findSearchResult(md5: String): PixivSearchResult?
    fun noCacheSearchResult(): List<PixivSearchResult>
    fun replaceAliasSetting(result: AliasSetting): Boolean
    fun alias(): List<AliasSetting>
    fun top(limit: Long): List<Pair<String, Long>>
    fun addHistory(history: StatisticTaskInfo): Boolean
    fun histories(name: String): List<StatisticTaskInfo>
}