package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.utils.*
import org.hibernate.*
import org.hibernate.boot.registry.*
import org.hibernate.cfg.*
import org.hibernate.dialect.function.*
import org.hibernate.query.criteria.internal.*
import org.hibernate.query.criteria.internal.expression.function.*
import org.hibernate.type.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.io.*
import java.sql.*
import javax.persistence.*
import javax.persistence.criteria.*
import kotlin.streams.*

private val Entities = listOf(
    UserBaseInfo::class.java,
    ArtWorkInfo::class.java,
    TagBaseInfo::class.java,
    FileInfo::class.java,
    PixivSearchResult::class.java,
    StatisticEroInfo::class.java,
    StatisticTagInfo::class.java,
    StatisticTaskInfo::class.java,
    AliasSetting::class.java,
    Twitter::class.java
)

private val PluginClassLoader get() = PixivHelperPlugin::class.java.classLoader

object HelperSqlConfiguration :
    Configuration(BootstrapServiceRegistryBuilder().applyClassLoader(PluginClassLoader).build()) {

    private val DefaultProperties = """
                hibernate.connection.url=jdbc:sqlite:pixiv.sqlite
                hibernate.connection.driver_class=org.sqlite.JDBC
                hibernate.dialect=org.sqlite.hibernate.dialect.SQLiteDialect
                hibernate.connection.provider_class=org.hibernate.connection.C3P0ConnectionProvider
                hibernate.connection.isolation=${Connection.TRANSACTION_READ_UNCOMMITTED}
                hibernate.hbm2ddl.auto=none
                hibernate-connection-autocommit=${true}
                hibernate.connection.show_sql=${false}
                hibernate.autoReconnect=${true}
            """.trimIndent()

    init {
        Entities.forEach { addAnnotatedClass(it) }
        setProperty("hibernate.connection.provider_class", "org.hibernate.connection.C3P0ConnectionProvider")
        setProperty("hibernate.connection.isolation", "${Connection.TRANSACTION_READ_UNCOMMITTED}")
    }

    fun load(dir: File = File(".")) {
        dir.resolve("hibernate.properties")
            .apply { if (exists().not()) writeText(DefaultProperties) }
            .reader().use(properties::load)
        if (getProperty("hibernate.connection.url").orEmpty().startsWith("jdbc:sqlite")) {
            // SQLite 是单文件数据库，最好只有一个连接
            setProperty("hibernate.c3p0.min_size", "${1}")
            setProperty("hibernate.c3p0.max_size", "${1}")
            addSqlFunction("rand", NoArgSQLFunction("random", StandardBasicTypes.LONG))
        }
    }
}

private val factory by lazy { HelperSqlConfiguration.buildSessionFactory().apply { init() } }

private fun SessionFactory.init(): Unit = openSession().use { session ->
    // 创建表
    session.transaction.begin()
    kotlin.runCatching {
        val meta = session.doReturningWork { it.metaData }
        val name = meta.databaseProductName
        val sql = when {
            name.contains(other = "SQLite", ignoreCase = true) -> "create.sqlite.sql"
            name.contains(other = "MariaDB", ignoreCase = true) ||
                name.contains(other = "MySql", ignoreCase = true) -> "create.mysql.sql"
            name.contains(other = "SQL Server", ignoreCase = true) -> "create.sqlserver.sql"
            else -> "create.default.sql"
        }
        requireNotNull(PluginClassLoader.getResourceAsStream("xyz/cssxsh/mirai/plugin/model/${sql}")) { "读取 Create Sql 失败" }
            .use { it.reader().readText() }
            .split(';').filter { it.isNotBlank() }
            .forEach { session.createNativeQuery(it).executeUpdate() }
        meta
    }.onSuccess {
        session.transaction.commit()
        logger.info("数据库 ${it.url} by ${it.driverName} 初始化完成")
    }.onFailure {
        session.transaction.rollback()
        logger.error("数据库初始化失败", it)
    }
}

private fun <R> useSession(lock: Any? = null, block: (session: Session) -> R): R {
    return if (lock == null) {
        factory.openSession().use(block)
    } else {
        synchronized(lock) {
            factory.openSession().use(block)
        }
    }
}

internal val SqlMetaData get() = useSession { session -> session.doReturningWork { it.metaData } }

internal fun reload(path: String, mode: ReplicationMode, chunk: Int, callback: (Result<Pair<Table, Long>>) -> Unit) {
    val sqlite = File(path).apply { check(exists()) { "文件不存在" } }
    val config = Configuration().apply { Entities.forEach { addAnnotatedClass(it) } }
    config.setProperty("hibernate.connection.url", "jdbc:sqlite:${sqlite.absolutePath}")
    config.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC")
    config.setProperty("hibernate.dialect", "org.sqlite.hibernate.dialect.SQLiteDialect")
    config.setProperty("hibernate.connection.provider_class", "org.hibernate.connection.C3P0ConnectionProvider")
    config.setProperty("hibernate.c3p0.min_size", "${1}")
    config.setProperty("hibernate.c3p0.max_size", "${1}")
    val new = config.buildSessionFactory().openSession().apply { isDefaultReadOnly = true }
    useSession { session ->
        Entities.onEach { clazz ->
            val annotation = clazz.getAnnotation(Table::class.java)
            var count = 0L
            new.withCriteria<Any> { it.select(it.from(clazz)) }
                .setReadOnly(true)
                .setCacheable(false)
                .resultStream
                .asSequence()
                .chunked(chunk)
                .forEach { list ->
                    session.transaction.begin()
                    runCatching {
                        list.forEach { session.replicate(it, mode) }
                        count += list.size
                        annotation to count
                    }.onSuccess {
                        session.transaction.commit()
                    }.onFailure {
                        session.transaction.rollback()
                    }.let(callback)
                    session.clear()
                    System.gc()
                }
        }
    }
}

internal class RandomFunction(criteriaBuilder: CriteriaBuilderImpl) :
    BasicFunctionExpression<Double>(criteriaBuilder, Double::class.java, "rand"), Serializable

internal fun CriteriaBuilder.rand() = RandomFunction(this as CriteriaBuilderImpl)

internal inline fun <reified T> Session.withCriteria(block: CriteriaBuilder.(criteria: CriteriaQuery<T>) -> Unit) =
    createQuery(with(criteriaBuilder) { createQuery(T::class.java).also { block(it) } })

internal inline fun <reified T> Session.withCriteriaUpdate(block: CriteriaBuilder.(criteria: CriteriaUpdate<T>) -> Unit) =
    createQuery(with(criteriaBuilder) { createCriteriaUpdate(T::class.java).also { block(it) } })

internal fun ArtWorkInfo.SQL.count(): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
    }.singleResult
}

internal fun ArtWorkInfo.SQL.eros(age: AgeLimit): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(
                isFalse(artwork.get("deleted")),
                isTrue(artwork.get("ero")),
                equal(artwork.get<Int>("age"), age.ordinal)
            )
    }.singleResult
}

internal operator fun ArtWorkInfo.SQL.contains(pid: Long): Boolean = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(equal(artwork.get<Long>("pid"), pid))
    }.singleResult > 0
}

internal fun ArtWorkInfo.SQL.list(ids: List<Long>): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(artwork.get<Long>("pid").`in`(ids))
    }.resultList.orEmpty()
}

internal fun ArtWorkInfo.SQL.interval(
    range: LongRange,
    marks: Long,
    pages: Int
): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(
                isFalse(artwork.get("deleted")),
                between(artwork.get("pid"), range.first, range.last),
                lt(artwork.get("bookmarks"), marks),
                gt(artwork.get("pages"), pages)
            )
    }.resultList.orEmpty()
}

internal fun ArtWorkInfo.SQL.type(
    range: LongRange,
    vararg types: WorkContentType
): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(
                isFalse(artwork.get("deleted")),
                between(artwork.get("pid"), range.first, range.last),
                artwork.get<Int>("type").`in`(types.map { it.ordinal })
            )
    }.resultList.orEmpty()
}

internal fun ArtWorkInfo.SQL.user(uid: Long): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(
                isFalse(artwork.get("deleted")),
                equal(artwork.get<UserBaseInfo>("author").get<Long>("uid"), uid)
            )
    }.resultList.orEmpty()
}

internal fun ArtWorkInfo.SQL.tag(
    vararg names: String,
    marks: Long,
    fuzzy: Boolean,
    age: AgeLimit,
    limit: Int
): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        val tag = { name: String, pid: Path<Long> ->
            criteria.subquery(TagBaseInfo::class.java).also { sub ->
                val info = sub.from(TagBaseInfo::class.java)
                sub.select(info)
                    .where(
                        equal(info.get<Long>("pid"), pid),
                        or(
                            like(info.get("name"), if (fuzzy) "%$name%" else name),
                            like(info.get("translated"), if (fuzzy) "%$name%" else name)
                        )
                    )
            }
        }
        criteria.select(artwork)
            .where(
                isFalse(artwork.get("deleted")),
                le(artwork.get<Int>("age"), age.ordinal),
                gt(artwork.get<Long>("bookmarks"), marks),
                *names.map { name -> exists(tag(name, artwork.get("pid"))) }.toTypedArray()
            )
            .orderBy(asc(rand()))
            .distinct(true)
    }.setMaxResults(limit).resultList.orEmpty()
}

internal fun ArtWorkInfo.SQL.random(
    level: Int,
    marks: Long,
    age: AgeLimit,
    limit: Int
): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(
                isFalse(artwork.get("deleted")),
                isTrue(artwork.get("ero")),
                le(artwork.get<Int>("age"), age.ordinal),
                gt(artwork.get<Int>("sanity"), level),
                gt(artwork.get<Long>("bookmarks"), marks)
            )
            .orderBy(asc(rand()))
    }.setMaxResults(limit).resultList.orEmpty()
}

internal fun ArtWorkInfo.SQL.delete(pid: Long, comment: String): Int = useSession { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.withCriteriaUpdate<ArtWorkInfo> { criteria ->
            val artwork = criteria.from(ArtWorkInfo::class.java)
            criteria.set("caption", comment).set("deleted", true)
                .where(
                    isFalse(artwork.get("deleted")),
                    equal(artwork.get<Long>("pid"), pid)
                )
        }.executeUpdate()
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun ArtWorkInfo.SQL.deleteUser(uid: Long, comment: String): Int = useSession { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.withCriteriaUpdate<ArtWorkInfo> { criteria ->
            val artwork = criteria.from(ArtWorkInfo::class.java)
            criteria.set("caption", comment).set("deleted", true)
                .where(
                    isFalse(artwork.get("deleted")),
                    equal(artwork.get<UserBaseInfo>("author").get<Long>("uid"), uid)
                )
        }.executeUpdate()
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun ArtWorkInfo.replicate(): Unit = useSession(ArtWorkInfo) { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.replicate(this, ReplicationMode.IGNORE)
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun SimpleArtworkInfo.toArtWorkInfo(caption: String = "") = ArtWorkInfo(
    pid = pid,
    title = title,
    caption = caption,
    author = UserBaseInfo(uid, name, "")
)

internal fun IllustInfo.toArtWorkInfo(author: UserBaseInfo = user.toUserBaseInfo()) = ArtWorkInfo(
    pid = pid,
    title = title,
    caption = caption,
    created = createAt.toEpochSecond(),
    pages = pageCount,
    sanity = sanityLevel.ordinal,
    type = type.ordinal,
    width = width,
    height = height,
    bookmarks = totalBookmarks ?: 0,
    comments = totalComments ?: 0,
    view = totalView ?: 0,
    age = age.ordinal,
    ero = isEro(),
    deleted = false,
    author = author
)

internal fun IllustInfo.toTagBaseInfos(): List<TagBaseInfo> = tags.distinctBy { it.name }.map {
    TagBaseInfo(pid, it.name, it.translatedName)
}

internal fun IllustInfo.replicate(): Unit = useSession(ArtWorkInfo) { session ->
    if (pid == 0L) return@useSession
    kotlin.runCatching {
        // XXX Save twitter
        user.twitter()
    }
    session.transaction.begin()
    kotlin.runCatching {
        session.replicate(toArtWorkInfo(), ReplicationMode.OVERWRITE)
        toTagBaseInfos().forEach { session.replicate(it, ReplicationMode.IGNORE) }
    }.onSuccess {
        session.transaction.commit()
        logger.info { "作品(${pid})<${createAt}>[${user.id}][${type}][${title}][${pageCount}]{${totalBookmarks}}信息已记录" }
    }.onFailure {
        session.transaction.rollback()
        logger.warning({ "作品(${pid})信息记录失败" }, it)
    }.getOrThrow()
}

internal fun Collection<IllustInfo>.replicate(): Unit = useSession(ArtWorkInfo) { session ->
    if (isEmpty()) return@useSession
    kotlin.runCatching {
        // XXX Save twitter
        for (info in this) {
            info.user.twitter()
        }
    }
    logger.verbose { "作品(${first().pid..last().pid})[${size}]信息即将更新" }
    session.transaction.begin()
    kotlin.runCatching {
        val users = mutableMapOf<Long, UserBaseInfo>()

        for (info in this) {
            if (info.pid == 0L) continue
            val author = users.getOrPut(info.user.id) { info.user.toUserBaseInfo() }
            session.replicate(info.toArtWorkInfo(author), ReplicationMode.OVERWRITE)
            info.toTagBaseInfos().forEach { session.replicate(it, ReplicationMode.IGNORE) }
        }
    }.onSuccess {
        session.transaction.commit()
        logger.verbose { "作品{${first().pid..last().pid}}[${size}]信息已更新" }
    }.onFailure {
        session.transaction.rollback()
        logger.warning({ "作品{${first().pid..last().pid}}[${size}]信息记录失败" }, it)
    }.getOrThrow()
}

internal fun UserInfo.toUserBaseInfo() = UserBaseInfo(id, name, account)

internal fun UserInfo.count(): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(
                isFalse(artwork.get("deleted")),
                equal(artwork.get<UserBaseInfo>("author").get<Long>("uid"), id)
            )
    }.singleResult
}

internal fun UserBaseInfo.SQL.account(account: String): UserBaseInfo? = useSession { session ->
    session.withCriteria<UserBaseInfo> { criteria ->
        val user = criteria.from(UserBaseInfo::class.java)
        criteria.select(user)
            .where(like(user.get("account"), account))
    }.list().singleOrNull()
}

internal fun UserBaseInfo.SQL.name(name: String): UserBaseInfo? = useSession { session ->
    session.withCriteria<UserBaseInfo> { criteria ->
        val user = criteria.from(UserBaseInfo::class.java)
        criteria.select(user)
            .where(like(user.get("name"), name))
    }.list().singleOrNull()
}

internal val ScreenRegex = """(?<=twitter\.com/(#!/)?)\w{4,15}""".toRegex()

internal val ScreenError = listOf("", "https", "http")

internal fun UserDetail.twitter(): String? {
    val screen = with(profile) {
        twitterAccount?.takeUnless { it in ScreenError }
            ?: twitterUrl?.let { ScreenRegex.find(it) }?.value?.takeUnless { it in ScreenError }
            ?: webpage?.let { ScreenRegex.find(it) }?.value
    } ?: user.comment?.let { ScreenRegex.find(it) }?.value ?: return null

    Twitter(screen, user.id).replicate()

    return screen
}

internal fun UserInfo.twitter(): String? {
    val screen = comment?.let { ScreenRegex.find(it) }?.value ?: return null

    Twitter(screen, id).replicate()

    return null
}

internal fun Twitter.replicate(): Unit = useSession(Twitter) { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.replicate(this@replicate, ReplicationMode.OVERWRITE)
    }.onSuccess {
        session.transaction.commit()
        logger.info { "uid: $uid -> screen: $screen 信息已记录" }
    }.onFailure {
        session.transaction.rollback()
        logger.warning({ "uid: $uid -> screen: $screen 信息记录失败" }, it)
    }.getOrThrow()
}

internal fun Twitter.SQL.find(screen: String): Twitter? = useSession { session ->
    session.find(Twitter::class.java, screen)
}

internal fun Twitter.SQL.find(uid: Long): List<Twitter> = useSession { session ->
    session.withCriteria<Twitter> { criteria ->
        val twitter = criteria.from(Twitter::class.java)
        criteria.select(twitter)
            .where(equal(twitter.get<Long>("uid"), uid))
    }.resultList.orEmpty()
}

internal fun FileInfo.SQL.find(hash: String): List<FileInfo> = useSession { session ->
    session.withCriteria<FileInfo> { criteria ->
        val file = criteria.from(FileInfo::class.java)
        criteria.select(file)
            .where(like(file.get("md5"), hash))
    }.resultList.orEmpty()
}

internal fun List<FileInfo>.replicate(): Unit = useSession(FileInfo) { session ->
    session.transaction.begin()
    kotlin.runCatching {
        forEach { session.replicate(it, ReplicationMode.OVERWRITE) }
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun StatisticTaskInfo.replicate(): Unit = useSession(StatisticTaskInfo) { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.replicate(this, ReplicationMode.OVERWRITE)
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal operator fun StatisticTaskInfo.SQL.contains(pair: Pair<String, Long>): Boolean = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val (name, pid) = pair
        val task = criteria.from(StatisticTaskInfo::class.java)
        criteria.select(count(task))
            .where(
                equal(task.get<Long>("pid"), pid),
                like(task.get("task"), name)
            )
    }.singleResult > 0
}

internal fun StatisticTaskInfo.SQL.last(name: String): StatisticTaskInfo? = useSession { session ->
    session.withCriteria<StatisticTaskInfo> { criteria ->
        val task = criteria.from(StatisticTaskInfo::class.java)
        criteria.select(task)
            .where(like(task.get("task"), name))
            .orderBy(desc(task.get<Long>("timestamp")))
    }.setMaxResults(1).resultList.singleOrNull()
}

internal fun StatisticTagInfo.replicate(): Unit = useSession(StatisticTagInfo) { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.replicate(this, ReplicationMode.OVERWRITE)
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun StatisticTagInfo.SQL.user(id: Long): List<StatisticTagInfo> = useSession { session ->
    session.withCriteria<StatisticTagInfo> { criteria ->
        val tag = criteria.from(StatisticTagInfo::class.java)
        criteria.select(tag)
            .where(equal(tag.get<Long>("sender"), id))
    }.resultList.orEmpty()
}

internal fun StatisticTagInfo.SQL.group(id: Long): List<StatisticTagInfo> = useSession { session ->
    session.withCriteria<StatisticTagInfo> { criteria ->
        val tag = criteria.from(StatisticTagInfo::class.java)
        criteria.select(tag)
            .where(equal(tag.get<Long>("group"), id))
    }.resultList.orEmpty()
}

@Suppress("UNCHECKED_CAST")
internal fun StatisticTagInfo.SQL.top(limit: Int): List<Pair<String, Int>> = useSession { session ->
    session.withCriteria<Pair<*, *>> { criteria ->
        val tag = criteria.from(StatisticTagInfo::class.java)
        criteria.select(construct(Pair::class.java, tag.get<String>("tag"), count(tag)))
            .groupBy(tag.get<String>("tag"))
            .orderBy(desc(count(tag)))
    }.setMaxResults(limit).resultList.orEmpty() as List<Pair<String, Int>>
}

internal fun StatisticEroInfo.replicate(): Unit = useSession(StatisticEroInfo) { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.replicate(this, ReplicationMode.OVERWRITE)
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun StatisticEroInfo.SQL.user(id: Long): List<StatisticEroInfo> = useSession { session ->
    session.withCriteria<StatisticEroInfo> { criteria ->
        val ero = criteria.from(StatisticEroInfo::class.java)
        criteria.select(ero)
            .where(equal(ero.get<Long>("sender"), id))
    }.resultList.orEmpty()
}

internal fun StatisticEroInfo.SQL.group(id: Long): List<StatisticEroInfo> = useSession { session ->
    session.withCriteria<StatisticEroInfo> { criteria ->
        val ero = criteria.from(StatisticEroInfo::class.java)
        criteria.select(ero)
            .where(equal(ero.get<Long>("group"), id))
    }.resultList.orEmpty()
}

internal fun UserPreview.isLoaded(): Boolean = illusts.all { it.pid in ArtWorkInfo }

internal fun AliasSetting.replicate(): Unit = useSession(AliasSetting) { session ->
    session.transaction.begin()
    kotlin.runCatching {
        session.replicate(this, ReplicationMode.OVERWRITE)
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun AliasSetting.SQL.all(): List<AliasSetting> = useSession { session ->
    session.withCriteria<AliasSetting> { criteria ->
        val alias = criteria.from(AliasSetting::class.java)
        criteria.select(alias)
    }.resultList.orEmpty()
}

internal fun AliasSetting.SQL.find(name: String): AliasSetting? = useSession { session ->
    session.find(AliasSetting::class.java, name)
}

internal fun PixivSearchResult.associate(): Unit = useSession { session ->
    session.transaction.begin()
    kotlin.runCatching {
        val info by lazy { session.find(ArtWorkInfo::class.java, pid) }
        if (uid == 0L && info?.pid != null) {
            title = info.title
            uid = info.author.uid
            name = info.author.name
            session.replicate(this, ReplicationMode.OVERWRITE)
        } else {
            session.replicate(this, ReplicationMode.IGNORE)
        }
    }.onSuccess {
        session.transaction.commit()
    }.onFailure {
        session.transaction.rollback()
    }.getOrThrow()
}

internal fun PixivSearchResult.SQL.find(hash: String): PixivSearchResult? = useSession { session ->
    session.find(PixivSearchResult::class.java, hash)
}

internal fun PixivSearchResult.SQL.noCached(): List<PixivSearchResult> = useSession { session ->
    session.withCriteria<PixivSearchResult> { criteria ->
        val search = criteria.from(PixivSearchResult::class.java)
        val artwork = search.join<PixivSearchResult, ArtWorkInfo?>("artwork", JoinType.LEFT)
        criteria.select(search)
            .where(artwork.isNull)
    }.resultList.orEmpty()
}

