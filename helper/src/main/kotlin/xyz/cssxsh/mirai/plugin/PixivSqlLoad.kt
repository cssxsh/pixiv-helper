package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import okio.ByteString.Companion.toByteString
import org.hibernate.Session
import org.hibernate.cfg.Configuration
import org.hibernate.query.criteria.internal.*
import org.hibernate.query.criteria.internal.expression.function.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.apps.*
import java.io.File
import java.io.Serializable
import java.util.logging.Level
import java.util.logging.Logger
import javax.persistence.criteria.*

object HelperSqlConfiguration : Configuration() {

    private val DefaultProperties = """
                hibernate.connection.url=jdbc:sqlite:pixiv.sqlite
                hibernate.connection.driver_class=org.sqlite.JDBC
                hibernate.dialect=org.sqlite.hibernate.dialect.SQLiteDialect
                hibernate-connection-autocommit=true
                show_sql=false
            """.trimIndent()

    init {
        Logger.getLogger("org.hibernate").level = Level.OFF
        addAnnotatedClass(ArtWorkInfo::class.java)
        addAnnotatedClass(FileInfo::class.java)
        addAnnotatedClass(TagBaseInfo::class.java)
        addAnnotatedClass(UserBaseInfo::class.java)
        addAnnotatedClass(PixivSearchResult::class.java)
        addAnnotatedClass(StatisticEroInfo::class.java)
        addAnnotatedClass(StatisticTagInfo::class.java)
        addAnnotatedClass(StatisticTaskInfo::class.java)
        addAnnotatedClass(AliasSetting::class.java)
    }

    fun load(dir: File = File(".")) {
        dir.resolve("hibernate.properties")
            .apply { if (exists().not()) writeText(DefaultProperties) }
            .reader().use(properties::load)
    }
}

internal val factory by lazy { HelperSqlConfiguration.buildSessionFactory() }

private val session by lazy {
    factory.openSession().apply {
        transaction.begin()
        requireNotNull(ArtWorkInfo::class.java.getResourceAsStream("create.sql")) { "读取 创建表 SQL 失败" }
            .use { it.reader().readText() }
            .split(';').filter { it.isNotBlank() }
            .forEach { createNativeQuery(it).executeUpdate() }
        transaction.commit()
    }
}

internal fun <R> useSession(block: (session: Session) -> R) = synchronized(factory) { session.let(block) }

internal class RandomFunction(criteriaBuilder: CriteriaBuilderImpl) :
    BasicFunctionExpression<Double>(criteriaBuilder, Double::class.java, "RANDOM"), Serializable

internal fun CriteriaBuilder.random() = RandomFunction(this as CriteriaBuilderImpl)

private inline fun <reified T> Session.withCriteria(
    block: CriteriaBuilder.(criteria: CriteriaQuery<T>) -> Unit
) = createQuery(criteriaBuilder.run { createQuery(T::class.java).also { block(it) } })

private inline fun <reified T> Session.withCriteriaUpdate(
    block: CriteriaBuilder.(criteria: CriteriaUpdate<T>) -> Unit
) = createQuery(criteriaBuilder.run { createCriteriaUpdate(T::class.java).also { block(it) } })

internal fun ArtWorkInfo.Companion.count(): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
    }.uniqueResult() ?: 0
}

internal fun ArtWorkInfo.Companion.eros(r18: Boolean): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(
                and(
                    isFalse(artwork.get("deleted")),
                    isTrue(artwork.get("isEro")),
                    equal(artwork.get<Boolean>("age"), r18)
                )
            )
    }.uniqueResult() ?: 0
}

internal operator fun ArtWorkInfo.Companion.contains(pid: Long): Boolean = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(equal(artwork.get<Long>("pid"), pid))
    }.uniqueResult() > 0
}

internal fun ArtWorkInfo.Companion.list(ids: List<Long>): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(artwork.get<Long>("pid").`in`(ids))
    }.resultList.orEmpty()
}

internal fun ArtWorkInfo.Companion.interval(range: LongRange, bookmarks: Long, pages: Int) = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(
                and(
                    between(artwork.get("pid"), range.first, range.last),
                    lt(artwork.get("totalBookmarks"), bookmarks),
                    gt(artwork.get("pageCount"), pages)
                )
            )
    }.resultList.orEmpty()
}

fun ArtWorkInfo.Companion.user(uid: Long): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(
                and(
                    isFalse(artwork.get("deleted")),
                    equal(artwork.get<Long>("uid"), uid)
                )
            )
    }.resultList.orEmpty()
}

internal fun ArtWorkInfo.Companion.tag(name: String, bookmarks: Long, fuzzy: Boolean, limit: Int) =
    useSession { session ->
        session.withCriteria<ArtWorkInfo> { criteria ->
            val artwork = criteria.from(ArtWorkInfo::class.java)
            val tag = criteria.from(TagBaseInfo::class.java)
            criteria.select(artwork)
                .where(
                    and(
                        isFalse(artwork.get("deleted")),
                        gt(artwork.get<Long>("totalBookmarks"), bookmarks),
                        artwork.get<Long>("pid").`in`(
                            criteria.select(tag.get("pid"))
                                .where(
                                    if (fuzzy) {
                                        or(
                                            like(tag.get("name"), "%$name%"),
                                            like(tag.get("translated_name"), "%$name%")
                                        )
                                    } else {
                                        or(
                                            like(tag.get("name"), name),
                                            like(tag.get("translated_name"), name)
                                        )
                                    }
                                )
                        )
                    )
                )
                .orderBy(asc(random()))
        }.setMaxResults(limit).resultList.orEmpty()
    }

internal fun ArtWorkInfo.Companion.random(level: Int, bookmarks: Long, limit: Int) = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(
                and(
                    isFalse(artwork.get("deleted")),
                    isTrue(artwork.get("isEro")),
                    gt(artwork.get<Int>("sanityLevel"), level),
                    gt(artwork.get<Long>("totalBookmarks"), bookmarks)
                )
            )
            .orderBy(asc(random()))
    }.setMaxResults(limit).resultList.orEmpty()
}

internal fun ArtWorkInfo.Companion.delete(pid: Long, comment: String): Int = useSession { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.withCriteriaUpdate<ArtWorkInfo> { criteria ->
            val artwork = criteria.from(ArtWorkInfo::class.java)
            criteria.set("caption", comment)
                .where(gt(artwork.get<Long>("pid"), pid))
        }.executeUpdate()
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun ArtWorkInfo.Companion.deleteUser(uid: Long, comment: String): Int = useSession { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.withCriteriaUpdate<ArtWorkInfo> { criteria ->
            val artwork = criteria.from(ArtWorkInfo::class.java)
            criteria.set("caption", comment)
                .where(gt(artwork.get<Long>("uid"), uid))
        }.executeUpdate()
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun ArtWorkInfo.saveOrUpdate() = useSession { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.saveOrUpdate(this)
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun SimpleArtworkInfo.toUserBaseInfo() = UserBaseInfo(uid, name, "")

internal fun SimpleArtworkInfo.toArtWorkInfo() = ArtWorkInfo(pid, uid, title)

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

internal fun IllustInfo.toTagInfo() = tags.map { TagBaseInfo(pid, it.name, it.translatedName) }

internal fun IllustInfo.saveOrUpdate(): Unit = useSession { session ->
    session.transaction.begin()
    runCatching {
        session.saveOrUpdate(user.toUserBaseInfo())
        session.saveOrUpdate(toArtWorkInfo())
        toTagInfo().forEach { session.saveOrUpdate(it) }
    }.onSuccess {
        session.transaction.commit()
        logger.info { "作品(${pid})<${createAt}>[${user.id}][${type}][${title}][${pageCount}]{${totalBookmarks}}信息已记录" }
    }.onFailure {
        session.transaction.rollback()
        logger.info { "作品(${pid})信息记录失败" }
    }
}

internal fun Collection<IllustInfo>.saveOrUpdate(): Unit = useSession { session ->
    logger.verbose { "作品(${first().pid..last().pid})[${size}]信息即将更新" }
    session.transaction.begin()

    runCatching {
        forEach { info ->
            session.saveOrUpdate(info.user.toUserBaseInfo())
            session.saveOrUpdate(info.toArtWorkInfo())
            info.toTagInfo().forEach { session.saveOrUpdate(it) }
        }
    }.onSuccess {
        session.transaction.commit()
        logger.verbose { "作品{${first().pid..last().pid}}[${size}]信息已更新" }
    }.onFailure {
        session.transaction.rollback()
        logger.info { "作品{${first().pid..last().pid}}[${size}]信息记录失败" }
    }
}

internal fun UserInfo.toUserBaseInfo() = UserBaseInfo(id, name, account)

internal fun UserInfo.count(): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(equal(artwork.get<Long>("uid"), id))
    }.uniqueResult() ?: 0
}

internal fun UserBaseInfo.save(): Unit = useSession { session ->
    if (session.find(UserBaseInfo::class.java, uid) != null) return@useSession
    session.transaction.begin()
    kotlin.runCatching {
        session.saveOrUpdate(this)
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun UserBaseInfo.Companion.account(account: String): UserBaseInfo? = useSession { session ->
    session.withCriteria<UserBaseInfo> { criteria ->
        val user = criteria.from(UserBaseInfo::class.java)
        criteria.select(user)
            .where(equal(user.get<String>("account"), account))
    }.uniqueResult()
}

internal fun UserBaseInfo.Companion.name(name: String): UserBaseInfo? = useSession { session ->
    session.withCriteria<UserBaseInfo> { criteria ->
        val user = criteria.from(UserBaseInfo::class.java)
        criteria.select(user)
            .where(equal(user.get<String>("name"), name))
    }.uniqueResult()
}

internal fun List<FileInfo>.saveOrUpdate(): Unit = useSession { session ->
    session.transaction.begin()
    kotlin.runCatching {
        forEach { session.saveOrUpdate(it) }
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun StatisticTaskInfo.saveOrUpdate(): Unit = useSession { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.saveOrUpdate(this)
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal operator fun StatisticTaskInfo.Companion.contains(pair: Pair<String, Long>): Boolean = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val (name, pid) = pair
        val task = criteria.from(StatisticTaskInfo::class.java)
        criteria.select(count(task))
            .where(
                and(
                    equal(task.get<Long>("pid"), pid),
                    equal(task.get<String>("task"), name)
                )
            )
    }.uniqueResult() > 0
}

internal fun StatisticTaskInfo.Companion.last(name: String): StatisticTaskInfo? = useSession { session ->
    session.withCriteria<StatisticTaskInfo> { criteria ->
        val task = criteria.from(StatisticTaskInfo::class.java)
        criteria.select(task)
            .where(equal(task.get<String>("task"), name))
            .orderBy(desc(task.get<Long>("timestamp")))
    }.uniqueResult()
}

internal fun StatisticTagInfo.saveOrUpdate(): Unit = useSession { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.saveOrUpdate(this)
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun StatisticTagInfo.Companion.user(id: Long): List<StatisticTagInfo> = useSession { session ->
    session.withCriteria<StatisticTagInfo> { criteria ->
        val tag = criteria.from(StatisticTagInfo::class.java)
        criteria.select(tag)
            .where(equal(tag.get<Long>("sender"), id))
    }.resultList.orEmpty()
}

internal fun StatisticTagInfo.Companion.group(id: Long): List<StatisticTagInfo> = useSession { session ->
    session.withCriteria<StatisticTagInfo> { criteria ->
        val tag = criteria.from(StatisticTagInfo::class.java)
        criteria.select(tag)
            .where(equal(tag.get<Long>("group"), id))
    }.resultList.orEmpty()
}

@Suppress("UNCHECKED_CAST")
internal fun StatisticTagInfo.Companion.top(limit: Int): List<Pair<String, Int>> = useSession { session ->
    session.withCriteria<Pair<*, *>> { criteria ->
        val tag = criteria.from(StatisticTagInfo::class.java)
        criteria.select(construct(Pair::class.java, tag.get<String>("tag"), count(tag)))
            .groupBy(tag.get<Boolean>("tag"))
            .orderBy(desc(count(tag)))
    }.setMaxResults(limit).resultList.orEmpty() as List<Pair<String, Int>>
}

internal fun StatisticEroInfo.saveOrUpdate(): Unit = useSession { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.saveOrUpdate(this)
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun StatisticEroInfo.Companion.user(id: Long): List<StatisticEroInfo> = useSession { session ->
    session.withCriteria<StatisticEroInfo> { criteria ->
        val ero = criteria.from(StatisticEroInfo::class.java)
        criteria.select(ero)
            .where(equal(ero.get<Long>("sender"), id))
    }.resultList.orEmpty()
}

internal fun StatisticEroInfo.Companion.group(id: Long): List<StatisticEroInfo> = useSession { session ->
    session.withCriteria<StatisticEroInfo> { criteria ->
        val ero = criteria.from(StatisticEroInfo::class.java)
        criteria.select(ero)
            .where(equal(ero.get<Long>("group"), id))
    }.resultList.orEmpty()
}

internal fun UserPreview.isLoaded() = illusts.all { it.pid in ArtWorkInfo }

internal fun AliasSetting.saveOrUpdate(): Unit = useSession { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.saveOrUpdate(this)
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun AliasSetting.Companion.all(): List<AliasSetting> = useSession { session ->
    session.withCriteria<AliasSetting> { criteria ->
        val alias = criteria.from(AliasSetting::class.java)
        criteria.select(alias)
    }.resultList.orEmpty()
}

internal fun PixivSearchResult.save(image: Image): Unit = useSession { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.saveOrUpdate(copy(md5 = image.md5.toByteString().hex()))
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun PixivSearchResult.Companion.find(image: Image): PixivSearchResult? = useSession { session ->
    session.find(PixivSearchResult::class.java, image.md5.toByteString().hex())
}

internal fun PixivSearchResult.Companion.noCached(): List<PixivSearchResult> = useSession { session ->
    session.withCriteria<PixivSearchResult> { criteria ->
        val search = criteria.from(PixivSearchResult::class.java)
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(search)
            .where(
                search.get<Long>("pid").`in`(
                    criteria.select(artwork.get("pid"))
                ).not()
            )
    }.resultList.orEmpty()
}

