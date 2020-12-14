package xyz.cssxsh.pixiv.dao

import xyz.cssxsh.pixiv.model.UserInfo

interface UserInfoMapper {
    fun findByUid(uid: Long): UserInfo?
    fun insertUser(info: UserInfo): Boolean
    fun insertUsers(list: List<UserInfo>): Boolean
}