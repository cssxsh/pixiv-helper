package xyz.cssxsh.pixiv.dao

import xyz.cssxsh.pixiv.model.UserInfo

interface UserInfoMapper {
    fun findByUid(uid: Long): UserInfo?
    fun replaceUser(info: UserInfo): Boolean
    fun replaceUsers(list: List<UserInfo>): Boolean
}