package xyz.cssxsh.mirai.plugin.model

import net.mamoe.mirai.utils.*
import org.hibernate.*
import org.hibernate.boot.registry.*
import org.hibernate.cfg.*
import org.hibernate.dialect.function.*
import org.hibernate.query.criteria.internal.*
import org.hibernate.query.criteria.internal.expression.function.*
import org.hibernate.type.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.io.*
import java.sql.*
import javax.persistence.*
import javax.persistence.criteria.*
import kotlin.reflect.full.*
import kotlin.streams.*

// region SqlConfiguration

private val PixivEntities = PixivEntity::class.sealedSubclasses.map { it.java }

private val PluginClassLoader: ClassLoader get() = PixivHelperPlugin::class.java.classLoader

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
        PixivEntities.forEach(::addAnnotatedClass)
        setProperty("hibernate.connection.provider_class", "org.hibernate.connection.C3P0ConnectionProvider")
        setProperty("hibernate.connection.isolation", "${Connection.TRANSACTION_READ_UNCOMMITTED}")
    }

    fun load(hibernate: File = PixivHelperPlugin.configFolder.resolve("hibernate.properties")) {
        hibernate.apply { if (exists().not()) writeText(DefaultProperties) }.reader().use(properties::load)
        val url = getProperty("hibernate.connection.url").orEmpty()
        when {
            url.startsWith("jdbc:sqlite") -> {
                // SQLite 是单文件数据库，最好只有一个连接
                setProperty("hibernate.c3p0.min_size", "${1}")
                setProperty("hibernate.c3p0.max_size", "${1}")
                // 设置 rand 别名
                addSqlFunction("rand", NoArgSQLFunction("random", StandardBasicTypes.LONG))
            }
            url.startsWith("jdbc:mysql") -> {
                addAnnotatedClass(MySqlVariable::class.java)
            }
        }
    }
}

private val factory: SessionFactory by lazy { HelperSqlConfiguration.buildSessionFactory().apply { init() } }

private fun SessionFactory.init(): Unit = openSession().use { session ->
    // 创建表
    session.transaction.begin()
    try {
        val meta = session.doReturningWork { it.metaData }
        val name = meta.databaseProductName
        val sql = when {
            name.contains(other = "SQLite", ignoreCase = true) -> "create.sqlite.sql"
            name.contains(other = "MariaDB", ignoreCase = true) ||
                name.contains(other = "MySql", ignoreCase = true) -> "create.mysql.sql"
            name.contains(other = "SQL Server", ignoreCase = true) -> "create.sqlserver.sql"
            else -> "create.default.sql"
        }
        logger.info { "Create Table by $sql with $name" }
        requireNotNull(PluginClassLoader.getResourceAsStream("xyz/cssxsh/mirai/plugin/model/${sql}")) { "Read Create Sql 失败" }
            .use { it.reader().readText() }
            .split(';').filter { it.isNotBlank() }
            .forEach { session.createNativeQuery(it).executeUpdate() }
        session.transaction.commit()
        logger.info { "数据库 ${meta.url} by ${meta.driverName} 初始化完成" }
    } catch (cause: Throwable) {
        session.transaction.rollback()
        logger.error("数据库初始化失败", cause)
        throw cause
    }

    var count = 0
    while (true) {
        System.gc()

        val olds: List<TagBaseInfo> = session.withCriteria<TagBaseInfo> { criteria ->
            val tag = criteria.from(TagBaseInfo::class.java)
            criteria.select(tag)
        }.setMaxResults(8196).list().orEmpty()

        if (olds.isEmpty()) break

        logger.info { "TAG 数据迁移中 ${count}，请稍候" }

        count += olds.size

        session.transaction.begin()
        val set = HashSet<String>()
        try {
            for (old in olds) {
                if (set.add(old.name)) {
                    val tag = TagRecord(name = old.name, translated = old.translated)
                    session.replicate(tag, ReplicationMode.IGNORE)
                }
            }
            session.transaction.commit()
        } catch (cause: Throwable) {
            session.transaction.rollback()
            throw cause
        }

        session.clear()

        session.transaction.begin()
        try {
            for (old in olds) {
                val record = session.get(TagRecord::class.java, old.name)
                session.replicate(ArtworkTag(pid = old.pid, tag = record), ReplicationMode.IGNORE)
                session.delete(old)
            }
            session.transaction.commit()
        } catch (cause: Throwable) {
            session.transaction.rollback()
            throw cause
        }
    }
    if (count > 0) {
        logger.info { "TAG 数据迁移完成 ${count}." }
    }
}

/**
 * @see [Throwable.cause]
 */
internal inline fun <reified T> Throwable.findIsInstance(): T? {
    var next: Throwable? = this
    while (next !is T?) {
        next = next?.cause
    }
    return next
}

/**
 * @see [Throwable.findIsInstance]
 */
internal fun Throwable.findSQLException() = findIsInstance<SQLException>()

private fun <R> useSession(lock: Any? = null, block: (session: Session) -> R): R {
    return if (lock == null) {
        factory.openSession().use(block)
    } else {
        synchronized(lock) {
            factory.openSession().use(block)
        }
    }
}

internal fun DatabaseMetaData(): DatabaseMetaData = useSession { session -> session.doReturningWork { it.metaData } }

/**
 * Only with MySql
 */
internal fun variables(): List<MySqlVariable> = useSession { session ->
    session.createNativeQuery<MySqlVariable>("""SHOW VARIABLES""", MySqlVariable::class.java).list()
}

internal fun reload(path: String, mode: ReplicationMode, chunk: Int, callback: (Result<Pair<Table, Long>>) -> Unit) {
    val sqlite = File(path).apply { check(exists()) { "文件不存在" } }
    val config = Configuration().apply { PixivEntities.forEach(::addAnnotatedClass) }
    config.setProperty("hibernate.connection.url", "jdbc:sqlite:${sqlite.absolutePath}")
    config.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC")
    config.setProperty("hibernate.dialect", "org.sqlite.hibernate.dialect.SQLiteDialect")
    config.setProperty("hibernate.connection.provider_class", "org.hibernate.connection.C3P0ConnectionProvider")
    config.setProperty("hibernate.c3p0.min_size", "${1}")
    config.setProperty("hibernate.c3p0.max_size", "${1}")
    val new = config.buildSessionFactory().openSession().apply { isDefaultReadOnly = true }
    useSession { session ->
        for (clazz in PixivEntities) {
            val annotation = clazz.getAnnotation(Table::class.java)
            var count = 0L
            new.withCriteria<PixivEntity> { it.select(it.from(clazz)) }
                .setReadOnly(true)
                .setCacheable(false)
                .stream()
                .asSequence()
                .chunked(chunk)
                .forEach { list ->
                    session.transaction.begin()
                    session.runCatching {
                        for (item in list) replicate(item, mode)
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

internal fun PixivEntity.replicate(mode: ReplicationMode = ReplicationMode.OVERWRITE) {
    val entity = this
    useSession(entity::class.companionObjectInstance) { session ->
        session.transaction.begin()
        try {
            session.replicate(entity, mode)
            session.transaction.commit()
        } catch (cause: Throwable) {
            session.transaction.rollback()
            throw cause
        }
    }
}

// endregion

// region ArtWorkInfo

internal fun ArtWorkInfo.SQL.count(): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
    }.uniqueResult()
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
    }.uniqueResult()
}

internal fun ArtWorkInfo.SQL.eros(type: WorkContentType): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(
                isFalse(artwork.get("deleted")),
                isTrue(artwork.get("ero")),
                equal(artwork.get<Int>("type"), type.ordinal)
            )
    }.uniqueResult()
}

internal fun ArtWorkInfo.SQL.eros(sanity: SanityLevel): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(
                isFalse(artwork.get("deleted")),
                isTrue(artwork.get("ero")),
                equal(artwork.get<Int>("sanity"), sanity.ordinal)
            )
    }.uniqueResult()
}

internal fun StatisticUserInfo.SQL.list(range: LongRange): List<StatisticUserInfo> = useSession { session ->
    session.withCriteria<StatisticUserInfo> { criteria ->
        val record = criteria.from(StatisticUserInfo::class.java)
        criteria.select(record)
            .where(
                gt(record.get<Long>("ero"), range.first),
                lt(record.get<Long>("count"), range.last)
            )
            .orderBy(asc(record.get<Long>("uid")))
    }.list()
}

internal operator fun ArtWorkInfo.SQL.contains(pid: Long): Boolean = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(equal(artwork.get<Long>("pid"), pid))
    }.uniqueResult() > 0
}

internal fun ArtWorkInfo.SQL.find(id: Long): ArtWorkInfo? = useSession { session ->
    session.find(ArtWorkInfo::class.java, id)
}

internal fun ArtWorkInfo.SQL.list(ids: List<Long>): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(artwork.get<Long>("pid").`in`(ids))
    }.list().orEmpty()
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
    }.list().orEmpty()
}

internal fun ArtWorkInfo.SQL.deleted(range: LongRange): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(
                isTrue(artwork.get("deleted")),
                between(artwork.get("pid"), range.first, range.last)
            )
    }.list().orEmpty()
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
    }.list().orEmpty()
}

internal fun ArtWorkInfo.SQL.user(uid: Long): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(
                isFalse(artwork.get("deleted")),
                equal(artwork.get<UserBaseInfo>("author").get<Long>("uid"), uid)
            )
    }.list().orEmpty()
}

internal fun ArtWorkInfo.SQL.tag(
    word: String,
    marks: Long,
    fuzzy: Boolean,
    age: AgeLimit,
    limit: Int
): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        val names = word.split(delimiters = TAG_DELIMITERS).filter { it.isNotBlank() }
        val records = artwork.joinList<ArtWorkInfo, TagRecord>("tags")
        criteria.select(artwork)
            .where(
                isFalse(artwork.get("deleted")),
                le(artwork.get<Int>("age"), age.ordinal),
                gt(artwork.get<Long>("bookmarks"), marks),
                *names.map { name ->
                    or(
                        like(records.get("name"), if (fuzzy) "%$name%" else name),
                        like(records.get("translated"), if (fuzzy) "%$name%" else name)
                    )
                }.toTypedArray()
            )
            .orderBy(asc(rand()))
            .distinct(true)
    }.setMaxResults(limit).list().orEmpty()
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
    }.setMaxResults(limit).list().orEmpty()
}

internal fun ArtWorkInfo.SQL.delete(pid: Long, comment: String): Int = useSession { session ->
    session.transaction.begin()
    try {
        val total = session.withCriteriaUpdate<ArtWorkInfo> { criteria ->
            val artwork = criteria.from(ArtWorkInfo::class.java)
            criteria.set("caption", comment).set("deleted", true)
                .where(
                    isFalse(artwork.get("deleted")),
                    equal(artwork.get<Long>("pid"), pid)
                )
        }.executeUpdate()
        session.transaction.commit()
        total
    } catch (cause: Throwable) {
        session.transaction.rollback()
        throw cause
    }
}

internal fun ArtWorkInfo.SQL.deleteUser(uid: Long, comment: String): Int = useSession { session ->
    session.transaction.begin()
    try {
        val total = session.withCriteriaUpdate<ArtWorkInfo> { criteria ->
            val artwork = criteria.from(ArtWorkInfo::class.java)
            criteria.set("caption", comment).set("deleted", true)
                .where(
                    isFalse(artwork.get("deleted")),
                    equal(artwork.get<UserBaseInfo>("author").get<Long>("uid"), uid)
                )
        }.executeUpdate()
        session.transaction.commit()
        total
    } catch (cause: Throwable) {
        session.transaction.rollback()
        throw cause
    }
}

internal fun SimpleArtworkInfo.toArtWorkInfo(caption: String = "") = ArtWorkInfo(
    pid = pid,
    title = title,
    caption = caption,
    author = UserBaseInfo(uid, name)
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

internal fun IllustInfo.toTagRecords(): List<TagRecord> = tags.map { info ->
    TagRecord(name = info.name, translated = info.translatedName).apply {
        replicate(ReplicationMode.IGNORE)
    }
}

internal fun IllustInfo.replicate(): Unit = useSession(ArtWorkInfo) { session ->
    if (pid == 0L) return@useSession
    try {
        user.twitter()
    } catch (e: Throwable) {
        logger.warning({ "Save twitter" }, e)
    }
    toTagRecords()
    session.transaction.begin()
    try {
        val artwork = toArtWorkInfo()
        artwork.tags = tags.mapNotNull { session.get(TagRecord::class.java, it.name) }
        session.replicate(artwork, ReplicationMode.OVERWRITE)
        session.transaction.commit()
        logger.info { "作品(${pid})<${createAt}>[${user.id}][${type}][${title}][${pageCount}]{${totalBookmarks}}信息已记录" }
    } catch (cause: Throwable) {
        session.transaction.rollback()
        logger.warning({ "作品(${pid})信息记录失败" }, cause)
        throw cause
    }
}

internal fun Collection<IllustInfo>.replicate(): Unit = useSession(ArtWorkInfo) { session ->
    if (isEmpty()) return@useSession
    try {
        for (info in this) {
            info.user.twitter()
        }
    } catch (e: Throwable) {
        logger.warning({ "Save twitter" }, e)
    }
    for (info in this) {
        info.toTagRecords()
    }
    logger.verbose { "作品(${first().pid..last().pid})[${size}]信息即将更新" }
    session.transaction.begin()
    try {
        val users = HashMap<Long, UserBaseInfo>()

        for (info in this@replicate) {
            if (info.pid == 0L) continue
            val author = users.getOrPut(info.user.id) { info.user.toUserBaseInfo() }
            val artwork = info.toArtWorkInfo(author)
            artwork.tags = info.tags.mapNotNull { session.get(TagRecord::class.java, it.name) }
            session.replicate(artwork, ReplicationMode.OVERWRITE)
        }
        session.transaction.commit()
        logger.verbose { "作品{${first().pid..last().pid}}[${size}]信息已更新" }
    } catch (cause: Throwable) {
        session.transaction.rollback()
        logger.warning({ "作品{${first().pid..last().pid}}[${size}]信息记录失败" }, cause)
        throw cause
    }
}

// endregion

// region UserInfo

internal fun UserInfo.toUserBaseInfo() = UserBaseInfo(id, name, account)

internal fun UserInfo.count(): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(equal(artwork.get<UserBaseInfo>("author").get<Long>("uid"), id))
    }.uniqueResult()
}

internal fun UserBaseInfo.SQL.account(account: String): UserBaseInfo? = useSession { session ->
    session.withCriteria<UserBaseInfo> { criteria ->
        val user = criteria.from(UserBaseInfo::class.java)
        criteria.select(user)
            .where(equal(user.get<String?>("account"), account))
    }.uniqueResult()
}

internal fun UserBaseInfo.SQL.name(name: String): UserBaseInfo? = useSession { session ->
    session.withCriteria<UserBaseInfo> { criteria ->
        val user = criteria.from(UserBaseInfo::class.java)
        criteria.select(user)
            .where(like(user.get("name"), name))
    }.list().singleOrNull()
}

private val ScreenRegex = """(?<=twitter\.com/(#!/)?)\w{4,15}""".toRegex()

private val ScreenError = listOf("", "https", "http")

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

    return screen
}

internal fun Twitter.SQL.find(screen: String): Twitter? = useSession { session ->
    session.find(Twitter::class.java, screen)
}

internal fun Twitter.SQL.find(uid: Long): List<Twitter> = useSession { session ->
    session.withCriteria<Twitter> { criteria ->
        val twitter = criteria.from(Twitter::class.java)
        criteria.select(twitter)
            .where(equal(twitter.get<Long>("uid"), uid))
    }.list().orEmpty()
}

// endregion

// region FileInfo

internal fun FileInfo.SQL.find(hash: String): List<FileInfo> = useSession { session ->
    session.withCriteria<FileInfo> { criteria ->
        val file = criteria.from(FileInfo::class.java)
        criteria.select(file)
            .where(like(file.get("md5"), hash))
    }.list().orEmpty()
}

internal fun List<FileInfo>.replicate(): Unit = useSession(FileInfo) { session ->
    session.transaction.begin()
    try {
        for (item in this) session.replicate(item, ReplicationMode.OVERWRITE)
        session.transaction.commit()
    } catch (cause: Throwable) {
        session.transaction.rollback()
        throw cause
    }
}

// endregion

// region Statistic

internal operator fun StatisticTaskInfo.SQL.contains(pair: Pair<String, Long>): Boolean = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val (name, pid) = pair
        val task = criteria.from(StatisticTaskInfo::class.java)
        criteria.select(count(task))
            .where(
                equal(task.get<Long>("pid"), pid),
                like(task.get("task"), name)
            )
    }.uniqueResult() > 0
}

internal fun StatisticTaskInfo.SQL.last(name: String): StatisticTaskInfo? = useSession { session ->
    session.withCriteria<StatisticTaskInfo> { criteria ->
        val task = criteria.from(StatisticTaskInfo::class.java)
        criteria.select(task)
            .where(like(task.get("task"), name))
            .orderBy(desc(task.get<Long>("timestamp")))
    }.setMaxResults(1).uniqueResult()
}

internal fun StatisticTagInfo.SQL.user(id: Long): List<StatisticTagInfo> = useSession { session ->
    session.withCriteria<StatisticTagInfo> { criteria ->
        val tag = criteria.from(StatisticTagInfo::class.java)
        criteria.select(tag)
            .where(equal(tag.get<Long>("sender"), id))
    }.list().orEmpty()
}

internal fun StatisticTagInfo.SQL.group(id: Long): List<StatisticTagInfo> = useSession { session ->
    session.withCriteria<StatisticTagInfo> { criteria ->
        val tag = criteria.from(StatisticTagInfo::class.java)
        criteria.select(tag)
            .where(equal(tag.get<Long>("group"), id))
    }.list().orEmpty()
}

@Suppress("UNCHECKED_CAST")
internal fun StatisticTagInfo.SQL.top(limit: Int): List<Pair<String, Int>> = useSession { session ->
    session.withCriteria<Pair<*, *>> { criteria ->
        val tag = criteria.from(StatisticTagInfo::class.java)
        criteria.select(construct(Pair::class.java, tag.get<String>("tag"), count(tag)))
            .groupBy(tag.get<String>("tag"))
            .orderBy(desc(count(tag)))
    }.setMaxResults(limit).list().orEmpty() as List<Pair<String, Int>>
}

internal fun StatisticEroInfo.SQL.user(id: Long): List<StatisticEroInfo> = useSession { session ->
    session.withCriteria<StatisticEroInfo> { criteria ->
        val ero = criteria.from(StatisticEroInfo::class.java)
        criteria.select(ero)
            .where(equal(ero.get<Long>("sender"), id))
    }.list().orEmpty()
}

internal fun StatisticEroInfo.SQL.group(id: Long): List<StatisticEroInfo> = useSession { session ->
    session.withCriteria<StatisticEroInfo> { criteria ->
        val ero = criteria.from(StatisticEroInfo::class.java)
        criteria.select(ero)
            .where(equal(ero.get<Long>("group"), id))
    }.list().orEmpty()
}

internal fun AliasSetting.SQL.delete(alias: String): Unit = useSession(AliasSetting) { session ->
    val record = session.get(AliasSetting::class.java, alias) ?: return@useSession
    session.transaction.begin()
    try {
        session.delete(record)
        session.transaction.commit()
    } catch (cause: Throwable) {
        session.transaction.rollback()
    }
}

internal fun AliasSetting.SQL.all(): List<AliasSetting> = useSession { session ->
    session.withCriteria<AliasSetting> { criteria ->
        val alias = criteria.from(AliasSetting::class.java)
        criteria.select(alias)
    }.list().orEmpty()
}

internal fun AliasSetting.SQL.find(name: String): AliasSetting? = useSession { session ->
    session.find(AliasSetting::class.java, name)
}

internal fun PixivSearchResult.associate(): Unit = useSession { session ->
    session.transaction.begin()
    try {
        if (uid == 0L && pid in ArtWorkInfo) {
            val info = session.find(ArtWorkInfo::class.java, pid)
            title = info.title
            uid = info.author.uid
            name = info.author.name
            session.replicate(this@associate, ReplicationMode.OVERWRITE)
        } else {
            session.replicate(this@associate, ReplicationMode.IGNORE)
        }
        session.transaction.commit()
    } catch (cause: Throwable) {
        session.transaction.rollback()
        throw cause
    }
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
    }.list().orEmpty()
}

// endregion
