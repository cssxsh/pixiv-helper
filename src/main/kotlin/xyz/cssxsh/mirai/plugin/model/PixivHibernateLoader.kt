package xyz.cssxsh.mirai.plugin.model

import net.mamoe.mirai.utils.*
import org.hibernate.*
import org.hibernate.cfg.*
import xyz.cssxsh.hibernate.*
import xyz.cssxsh.mirai.plugin.*
import java.io.*
import java.sql.*
import javax.persistence.*
import kotlin.reflect.full.*
import kotlin.streams.*

object PixivHibernateLoader : MiraiHibernateLoader {
    override val autoScan: Boolean = true
    override val classLoader: ClassLoader = this::class.java.classLoader
    override val configuration: File
        get() {
            return try {
                PixivHelperPlugin.configFolder.resolve("hibernate.properties")
            } catch (_: Throwable) {
                File("hibernate.properties")
            }
        }
    override val default: String = """
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
    override val packageName: String = this::class.java.packageName
    val factory: SessionFactory
        get() {
            return try {
                PixivHelperPlugin.factory
            } catch (_: Throwable) {
                MiraiHibernateConfiguration(loader = this).buildSessionFactory()
            }
        }

    fun init() {
        PixivHelperPlugin.factory.openSession().use { session ->
            create(session)
            tag(session)
        }
    }

    private fun create(session: Session) {
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
            requireNotNull(this::class.java.getResourceAsStream(sql)) { "Read Create Sql 失败" }
                .use { it.reader().readText() }
                .split(';').filter { it.isNotBlank() }
                .forEach { session.createNativeQuery(it).executeUpdate() }
            session.transaction.commit()
            logger.info { "数据库 ${meta.url} by ${meta.driverName} 初始化完成" }
        } catch (cause: Throwable) {
            session.transaction.rollback()
            logger.error({ "数据库初始化失败" }, cause.findSQLException() ?: cause)
            throw cause
        }
    }

    private fun tag(session: Session) {
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
                logger.warning({ "TAG data move failure." }, cause.findSQLException() ?: cause)
                throw cause
            }

            session.transaction.begin()
            try {
                for (old in olds) {
                    val record0 = session.get(TagRecord::class.java, old.name)
                    val record = if (record0.tid != 0L) {
                        record0
                    } else {
                        session.detach(record0)
                        session.get(TagRecord::class.java, old.name)
                    }
                    session.replicate(ArtworkTag(pid = old.pid, tag = record), ReplicationMode.IGNORE)
                    session.delete(old)
                }
                session.transaction.commit()
            } catch (cause: Throwable) {
                session.transaction.rollback()
                logger.warning({ "TAG data move failure." }, cause.findSQLException() ?: cause)
                throw cause
            }
        }
        if (count > 0) {
            logger.info { "TAG 数据迁移完成 ${count}." }
        }
    }

    fun reload(path: String, mode: ReplicationMode, chunk: Int, callback: (Result<Pair<Table, Long>>) -> Unit) {
        val sqlite = File(path).apply { check(exists()) { "文件不存在" } }
        val entities = PixivEntity::class.sealedSubclasses.map { it.java }
        val config = Configuration().apply { entities.forEach(::addAnnotatedClass) }
        config.setProperty("hibernate.connection.url", "jdbc:sqlite:${sqlite.toURI().toASCIIString()}")
        config.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC")
        config.setProperty("hibernate.dialect", "org.sqlite.hibernate.dialect.SQLiteDialect")
        config.setProperty("hibernate.connection.provider_class", "org.hibernate.connection.C3P0ConnectionProvider")
        config.setProperty("hibernate.c3p0.min_size", "${1}")
        config.setProperty("hibernate.c3p0.max_size", "${1}")
        val other = config.buildSessionFactory().openSession().apply { isDefaultReadOnly = true }
        for (entity in entities) {
            val annotation = entity.getAnnotation(Table::class.java)
            var count = 0L
            other.withCriteria<PixivEntity> { it.select(it.from(entity)) }
                .setReadOnly(true)
                .setCacheable(false)
                .stream()
                .asSequence()
                .chunked(chunk)
                .forEach { list ->
                    useSession(entity::class.companionObjectInstance) { session ->
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
                    }
                    System.gc()
                }
        }
        other.close()
    }
}