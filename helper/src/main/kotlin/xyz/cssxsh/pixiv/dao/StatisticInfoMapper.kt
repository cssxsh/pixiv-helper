package xyz.cssxsh.pixiv.dao

import xyz.cssxsh.pixiv.model.StatisticEroInfo
import xyz.cssxsh.pixiv.model.StatisticTagInfo

interface StatisticInfoMapper {
    fun replaceEroInfo(info: StatisticEroInfo): Boolean
    fun senderEroInfos(sender: Long): List<StatisticEroInfo>
    fun groupEroInfos(group: Long): List<StatisticEroInfo>
    fun replaceTagInfo(info: StatisticTagInfo): Boolean
    fun senderTagInfos(sender: Long): List<StatisticTagInfo>
    fun groupTagInfos(group: Long): List<StatisticTagInfo>
}