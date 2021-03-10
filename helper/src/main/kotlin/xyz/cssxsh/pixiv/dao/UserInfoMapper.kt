package xyz.cssxsh.pixiv.dao

import xyz.cssxsh.pixiv.model.UserBaseInfo

interface UserInfoMapper {
    fun findByUid(uid: Long): UserBaseInfo?
    fun findByName(name: String): UserBaseInfo?
    fun replaceUser(info: UserBaseInfo): Boolean
    fun replaceUsers(list: List<UserBaseInfo>): Boolean
    fun updateUser(info: UserBaseInfo): Boolean
    fun updateUsers(list: List<UserBaseInfo>): Boolean
}