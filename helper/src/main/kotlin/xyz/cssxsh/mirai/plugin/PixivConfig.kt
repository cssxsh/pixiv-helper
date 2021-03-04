package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.utils.info
import org.apache.ibatis.mapping.Environment
import org.apache.ibatis.session.Configuration
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory
import org.sqlite.JDBC
import org.sqlite.SQLiteConfig
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.pixiv.client.PixivConfig
import xyz.cssxsh.pixiv.dao.*
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.time.minutes

private val BAD_IP = listOf("210.140.131.224", "210.140.131.225")

private val PIXIV_IMAGE_IP: List<String> = (134..147).map { "210.140.92.${it}" } - BAD_IP

private val PIXIV_NET_IP: List<String> = (199..229).map { "210.140.131.${it}" } - BAD_IP

internal val PIXIV_HOST = mapOf(
    "i.pximg.net" to PIXIV_IMAGE_IP,
    "s.pximg.net" to PIXIV_IMAGE_IP,
    "oauth.secure.pixiv.net" to PIXIV_NET_IP,
    "app-api.pixiv.net" to PIXIV_NET_IP,
    "public-api.secure.pixiv.net" to PIXIV_NET_IP,
    "public.pixiv.net" to PIXIV_NET_IP,
    "www.pixiv.net" to PIXIV_NET_IP,
    "pixiv.me" to PIXIV_NET_IP
)

internal val DEFAULT_PIXIV_CONFIG = PixivConfig(
    host = PIXIV_HOST
)

internal val InitSqlConfiguration = Configuration()

internal fun Configuration.init() = apply {
    environment = Environment("development", JdbcTransactionFactory(), SQLiteConnectionPoolDataSource().apply {
        config.apply {
            enforceForeignKeys(true)
            setCacheSize(8196)
            setPageSize(8196)
            setJournalMode(SQLiteConfig.JournalMode.MEMORY)
            enableCaseSensitiveLike(true)
            setTempStore(SQLiteConfig.TempStore.MEMORY)
            setSynchronous(SQLiteConfig.SynchronousMode.OFF)
            setEncoding(SQLiteConfig.Encoding.UTF8)
            PixivHelperSettings.sqliteConfig.forEach { (pragma, value) ->
                setPragma(pragma, value)
            }
        }
        url = "${JDBC.PREFIX}${PixivHelperSettings.sqlite.absolutePath}"
    })
    addMapper(ArtWorkInfoMapper::class.java)
    addMapper(FileInfoMapper::class.java)
    addMapper(StatisticInfoMapper::class.java)
    addMapper(TagInfoMapper::class.java)
    addMapper(UserInfoMapper::class.java)
}

internal fun PixivHelperSettings.init() {
    cacheFolder.mkdirs()
    backupFolder.mkdirs()
    tempFolder.mkdirs()
    profilesFolder.mkdirs()
    if (sqlite.exists().not()) {
        this::class.java.getResourceAsStream("pixiv.sqlite")?.use {
            sqlite.writeBytes(it.readAllBytes())
        }
    }
    PixivHelperPlugin.logger.info { "CacheFolder: ${cacheFolder.absolutePath}" }
    PixivHelperPlugin.logger.info { "BackupFolder: ${backupFolder.absolutePath}" }
    PixivHelperPlugin.logger.info { "TempFolder: ${tempFolder.absolutePath}" }
    PixivHelperPlugin.logger.info { "Sqlite: ${sqlite.absolutePath}" }
}