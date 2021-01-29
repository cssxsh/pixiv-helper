package xyz.cssxsh.pixiv.dao

import org.apache.ibatis.io.Resources
import org.apache.ibatis.mapping.Environment
import org.apache.ibatis.session.SqlSessionFactory
import org.apache.ibatis.session.SqlSessionFactoryBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import xyz.cssxsh.mirai.plugin.*
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class MapperTest {

    private val sqlSessionFactory: SqlSessionFactory by lazy {
        Resources.getResourceAsStream(config).use {
            SqlSessionFactoryBuilder().build(it)
        }
    }

    private fun imagesFolder(pid: Long): File = File("F:\\PixivCache")
        .resolve("%03d______".format(pid / 1_000_000))
        .resolve("%06d___".format(pid / 1_000))
        .resolve("$pid")

    private val config = "mybatis-config.xml"

    private val sqliteUrl get() = "jdbc:sqlite:${File("../test/pixiv.sqlite").absolutePath}"

    @BeforeAll
    @Suppress("unused")
    fun initSqlSession() {
        sqlSessionFactory.configuration.apply {
            environment =
                Environment(environment.id, environment.transactionFactory, SQLiteConnectionPoolDataSource().apply {
                    println(sqliteUrl)
                    url = sqliteUrl
                })
        }
    }

    @Test
    fun useArtWorkInfoMapper(): Unit = sqlSessionFactory.openSession(true).use { session ->
        val pid = 24924L
        val uid = 464L
        session.getMapper(ArtWorkInfoMapper::class.java).count().let {
            println(it)
        }
        session.getMapper(ArtWorkInfoMapper::class.java).findByPid(pid).let {
            println(it)
        }
        session.getMapper(TagInfoMapper::class.java).findByPid(pid).let {
            println(it)
        }
        session.getMapper(TagInfoMapper::class.java).findByName("明日方舟").let {
            println(it)
        }
        session.getMapper(FileInfoMapper::class.java).fileInfos(pid).let {
            println(it)
        }
        session.getMapper(ArtWorkInfoMapper::class.java).userArtWork(uid).let {
            println(it)
        }
        session.getMapper(ArtWorkInfoMapper::class.java).contains(643077).let {
            println(it)
        }
        session.getMapper(FileInfoMapper::class.java).findByMd5("103340dedca009c7228e0b0c9492ea97").let {
            println(it)
        }
        session.getMapper(ArtWorkInfoMapper::class.java).keys(0..1_000_000L).let {
            println(it.size)
        }
    }

    @Test
    fun saveInfo() {
        val dir = imagesFolder(24924)
        dir.resolve("24924.json").readIllustInfo().run {
            sqlSessionFactory.openSession().use { session ->
                session.getMapper(UserInfoMapper::class.java).replaceUser(getUserInfo())
                session.getMapper(ArtWorkInfoMapper::class.java).replaceArtWork(getArtWorkInfo())
                session.getMapper(FileInfoMapper::class.java).replaceFiles(getFileInfos())
                session.getMapper(TagInfoMapper::class.java).replaceTags(getTagInfo())
            }
        }
    }
}