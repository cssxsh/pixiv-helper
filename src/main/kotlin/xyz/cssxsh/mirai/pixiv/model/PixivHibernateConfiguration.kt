package xyz.cssxsh.mirai.pixiv.model

import net.mamoe.mirai.utils.*
import org.hibernate.boot.registry.*
import org.hibernate.cfg.*
import xyz.cssxsh.hibernate.*
import xyz.cssxsh.mirai.pixiv.*
import java.io.*
import java.sql.*

public object PixivHibernateConfiguration :
    Configuration(
        BootstrapServiceRegistryBuilder()
            .applyClassLoader(PixivHelperPlugin::class.java.classLoader)
            .build()
    ) {

    init {
        setProperty("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider")
        setProperty("hibernate.connection.isolation", "${Connection.TRANSACTION_READ_UNCOMMITTED}")
        load()
    }

    private val configuration: File
        get() {
            return try {
                PixivHelperPlugin.configFolder.resolve("hibernate.properties")
            } catch (_: Throwable) {
                File("hibernate.properties")
            }
        }

    private val default: String
        get() = """
                hibernate.connection.url=jdbc:sqlite:file:./data/xyz.cssxsh.mirai.plugin.pixiv-helper/pixiv.sqlite
                hibernate.connection.driver_class=org.sqlite.JDBC
                hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect
                hibernate.connection.provider_class=org.hibernate.hikaricp.internal.HikariCPConnectionProvider
                hibernate.connection.isolation=${Connection.TRANSACTION_READ_UNCOMMITTED}
                hibernate-connection-autocommit=${true}
                hibernate.connection.show_sql=${false}
                hibernate.autoReconnect=${true}
                hibernate.current_session_context_class=thread
            """.trimIndent()

    private fun load() {
        PixivEntity::class.sealedSubclasses.forEach { addAnnotatedClass(it.java) }
        configuration.apply { if (exists().not()) writeText(default) }.reader().use(properties::load)
        val url = requireNotNull(getProperty("hibernate.connection.url")) { "hibernate.connection.url cannot is null" }
        if (getProperty("hibernate.connection.provider_class") == "org.hibernate.connection.C3P0ConnectionProvider") {
            setProperty(
                "hibernate.connection.provider_class",
                "org.hibernate.hikaricp.internal.HikariCPConnectionProvider"
            )
            logger.warning { "已经自动将 C3P0ConnectionProvider 替换为 HikariCPConnectionProvider" }
        }
        if (getProperty("hibernate.dialect") == "org.sqlite.hibernate.dialect.SQLiteDialect") {
            setProperty(
                "hibernate.dialect",
                "org.hibernate.community.dialect.SQLiteDialect"
            )
            logger.warning { "已经自动将 org.sqlite.hibernate.dialect.SQLiteDialect 替换为 org.hibernate.community.dialect.SQLiteDialect" }
        }
        when {
            url.startsWith("jdbc:sqlite") -> {
                // SQLite 是单文件数据库，最好只有一个连接
                setProperty("hibernate.c3p0.min_size", "${1}")
                setProperty("hibernate.c3p0.max_size", "${1}")
                setProperty(
                    "hibernate.c3p0.timeout",
                    System.getProperty("hibernate.c3p0.timeout", "${60_000}")
                )
                setProperty("hibernate.hikari.minimumIdle", "${1}")
                setProperty("hibernate.hikari.maximumPoolSize", "${1}")
                setProperty(
                    "hibernate.hikari.connectionTimeout",
                    System.getProperty("hibernate.hikari.connectionTimeout", "${60_000}")
                )
                setProperty("hibernate.hbm2ddl.auto", "none")
                // 设置 rand 别名
                addRandFunction()
            }
            url.startsWith("jdbc:mysql") -> {
                //
            }
        }
    }
}