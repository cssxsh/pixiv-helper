package xyz.cssxsh.mirai.plugin.dao

import xyz.cssxsh.mirai.plugin.model.UserBaseInfo

interface UserInfoMapper {
    fun findByUid(uid: Long): UserBaseInfo?
    fun findByName(name: String): List<UserBaseInfo>
    fun replaceUser(info: UserBaseInfo): Boolean
    fun replaceUsers(list: List<UserBaseInfo>): Boolean
    fun updateUser(info: UserBaseInfo): Boolean
    fun updateUsers(list: List<UserBaseInfo>): Boolean
}