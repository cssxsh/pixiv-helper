package xyz.cssxsh.mirai.plugin.dao

import xyz.cssxsh.mirai.plugin.model.*

interface StatisticInfoMapper {
    fun replaceEroInfo(info: StatisticEroInfo): Boolean
    fun senderEroInfos(sender: Long): List<StatisticEroInfo>
    fun groupEroInfos(group: Long): List<StatisticEroInfo>
    fun replaceTagInfo(info: StatisticTagInfo): Boolean
    fun senderTagInfos(sender: Long): List<StatisticTagInfo>
    fun groupTagInfos(group: Long): List<StatisticTagInfo>
    fun replaceSearchResult(result: SearchResult): Boolean
    fun findSearchResult(md5: String): SearchResult?
    fun noCacheSearchResult(): List<SearchResult>
    fun replaceAliasSetting(result: AliasSetting): Boolean
    fun alias(): List<AliasSetting>
}