package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.md5
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.verbose
import okio.ByteString.Companion.toByteString
import org.hibernate.Session
import org.hibernate.cfg.Configuration
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.apps.*
import java.io.File

class HelperSqlConfiguration(dir: File = PixivHelperPlugin.configFolder) : Configuration() {
    init {
        val file = dir.resolve("hibernate.properties").apply {
            if (exists().not()) writeText(
                """
                hibernate.connection.url=jdbc:sqlite:pixiv.sqlite
                hibernate.connection.driver_class=org.sqlite.JDBC
                show_sql=false
            """.trimIndent()
            )
        }

        file.reader().use(properties::load)

        addClass(ArtWorkInfo::class.java)
        addClass(FileInfo::class.java)
        addClass(TagBaseInfo::class.java)
        addClass(UserBaseInfo::class.java)
    }
}

private fun <T> useSession(block: (session: Session) -> T) = synchronized(PixivHelperPlugin.factory) {
    PixivHelperPlugin.session.let(block)
}

internal fun ArtWorkInfo.Companion.contains(pid: Long) = useSession { session ->
    session.find(ArtWorkInfo::class.java, pid) != null
}

internal fun ArtWorkInfo.Companion.list(ids: List<Long>): List<ArtWorkInfo> = useSession { session ->
    session.createQuery(
        """from ArtWorkInfo where uid in (${ids.joinToString(",")}) and not deleted""",
        ArtWorkInfo::class.java
    ).resultList
}

internal fun ArtWorkInfo.Companion.user(uid: Long): List<ArtWorkInfo> = useSession { session ->
    session.createQuery("""from ArtWorkInfo where uid=$uid and not deleted""", ArtWorkInfo::class.java).resultList
}

internal fun ArtWorkInfo.Companion.tag(tag: String, bookmark: Long, fuzzy: Boolean): List<ArtWorkInfo> = useSession { session ->
    TODO()
}

internal fun ArtWorkInfo.saveOrUpdate() = useSession { session -> session.saveOrUpdate(this) }

internal fun SimpleArtworkInfo.toUserBaseInfo() = UserBaseInfo(uid = uid, name = name, account = "")

internal fun SimpleArtworkInfo.toArtWorkInfo() = EmptyArtWorkInfo.copy(pid = pid, uid = uid, title = title)

internal fun IllustInfo.toArtWorkInfo() = ArtWorkInfo(
    pid = pid,
    uid = user.id,
    title = title,
    caption = caption,
    createAt = createAt.toEpochSecond(),
    pageCount = pageCount,
    sanityLevel = sanityLevel.ordinal,
    type = type.ordinal,
    width = width,
    height = height,
    totalBookmarks = totalBookmarks ?: 0,
    totalComments = totalComments ?: 0,
    totalView = totalView ?: 0,
    age = age.ordinal,
    isEro = isEro(),
    deleted = false
)

internal fun IllustInfo.toTagInfo() = tags.map {
    TagBaseInfo(pid = pid, name = it.name, translatedName = it.translatedName)
}

internal fun IllustInfo.saveOrUpdate(): Unit = useSession { session ->
    session.saveOrUpdate(user.toUserBaseInfo())
    session.saveOrUpdate(toArtWorkInfo())
    toTagInfo().forEach { session.saveOrUpdate(it) }
    logger.info { "作品(${pid})<${createAt}>[${user.id}][${type}][${title}][${pageCount}]{${totalBookmarks}}信息已记录" }
}

internal fun Collection<IllustInfo>.saveOrUpdate(): Unit = useSession { session ->
    logger.verbose { "作品(${first().pid..last().pid})[${size}]信息即将更新" }

    forEach { info ->
        session.saveOrUpdate(info.user.toUserBaseInfo())
        session.saveOrUpdate(info.toArtWorkInfo())
        info.toTagInfo().forEach { session.saveOrUpdate(it) }
    }

    logger.verbose { "作品{${first().pid..last().pid}}[${size}]信息已更新" }
}

internal fun UserInfo.toUserBaseInfo() = UserBaseInfo(uid = id, name = name, account = account)

internal fun UserInfo.count() = useSession { session ->
    session.createQuery("""select count(*) from ArtWorkInfo where uid = $id""").uniqueResult() as Long
}

internal fun UserBaseInfo.save(): Unit = useSession { session ->
    if (session.find(UserBaseInfo::class.java, uid) != null) return@useSession
    session.saveOrUpdate(this)
}

internal fun UserBaseInfo.Companion.account(account: String) = useSession { session ->
    session.createQuery("""from UserBaseInfo where account = $account""", UserBaseInfo::class.java).uniqueResult()
}

internal fun List<FileInfo>.saveOrUpdate(): Unit = useSession { session -> forEach { session.saveOrUpdate(it) } }

internal fun StatisticTaskInfo.saveOrUpdate(): Unit = useSession { session -> session.saveOrUpdate(this) }

internal fun StatisticTagInfo.saveOrUpdate(): Unit = useSession { session -> session.saveOrUpdate(this) }

internal fun StatisticEroInfo.saveOrUpdate(): Unit = useSession { session -> session.saveOrUpdate(this) }

internal fun UserPreview.isLoaded() = useSession { session ->
    illusts.all { session.find(ArtWorkInfo::class.java, it.pid) != null }
}

internal fun AliasSetting.Companion.all(): List<AliasSetting> = useSession { session ->
    session.createQuery("", AliasSetting::class.java).resultList
}

internal fun Image.findSearchResult() = useSession { session ->
    session.find(PixivSearchResult::class.java, md5.toByteString().hex())
}

internal fun PixivSearchResult.save(image: Image): Unit = useSession { session ->
    session.saveOrUpdate(copy(md5 = image.md5.toByteString().hex()))
}

