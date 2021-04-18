package xyz.cssxsh.pixiv.dao

import org.apache.ibatis.session.SqlSessionFactory
import org.apache.ibatis.session.SqlSessionFactoryBuilder
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.pixiv.model.*
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class MapperTest {

    private val sqlSessionFactory: SqlSessionFactory by lazy {
        SqlSessionFactoryBuilder().build(InitSqlConfiguration)
    }

    private fun imagesFolder(pid: Long): File = File("F:\\PixivCache")
        .resolve("%03d______".format(pid / 1_000_000))
        .resolve("%06d___".format(pid / 1_000))
        .resolve("$pid")

    private val sqlite get() = File("../test/pixiv.sqlite")

    @BeforeAll
    @Suppress("unused")
    fun initSqlSession() {
        sqlSessionFactory.configuration.init(sqlite)
    }

    @Test
    fun useArtWorkInfoMapper(): Unit = sqlSessionFactory.openSession(true).use { session ->
        val pid = 2086L
        val interval = 0 until 1_000_000L
        session.getMapper(ArtWorkInfoMapper::class.java).count().let {
            println(it)
        }
        session.getMapper(ArtWorkInfoMapper::class.java).findByPid(pid).let {
            println(it)
        }
        session.getMapper(ArtWorkInfoMapper::class.java).artWorks(interval).let {
            println(it.size)
        }
        session.getMapper(ArtWorkInfoMapper::class.java).findByTag("淫纹").let {
            println(it.size)
        }
    }

    @Test
    fun saveIllustInfo() {
        val pid = 24924L
        imagesFolder(pid).resolve("${pid}.json").readIllustInfo().run {
            sqlSessionFactory.openSession().use { session ->
                session.getMapper(UserInfoMapper::class.java).replaceUser(user.toUserBaseInfo())
                session.getMapper(ArtWorkInfoMapper::class.java).replaceArtWork(getArtWorkInfo())
                session.getMapper(FileInfoMapper::class.java).replaceFiles(getFileInfos())
                session.getMapper(TagInfoMapper::class.java).replaceTags(getTagInfo())
            }
        }
    }

    @Test
    fun saveStatisticInfo() {
        sqlSessionFactory.openSession().use { session ->
            session.getMapper(StatisticInfoMapper::class.java).let { mapper ->
                mapper.replaceEroInfo(StatisticEroInfo(
                    sender = 0,
                    group = 1,
                    pid = (1 .. 10000L).random(),
                    timestamp = System.currentTimeMillis() / 1_000
                ))
                mapper.replaceEroInfo(StatisticEroInfo(
                    sender = 0,
                    group = 1,
                    pid = (1 .. 10000L).random(),
                    timestamp = System.currentTimeMillis() / 1_000
                ))
                mapper.senderEroInfos(0).forEach {
                    println(it)
                }
                mapper.groupEroInfos(1).forEach {
                    println(it)
                }
                mapper.replaceTagInfo(StatisticTagInfo(
                    sender = 0,
                    group = 1,
                    pid = (1 .. 10000L).random(),
                    tag = "test",
                    timestamp = System.currentTimeMillis() / 1_000
                ))
                mapper.replaceTagInfo(StatisticTagInfo(
                    sender = 0,
                    group = 1,
                    pid = (1 .. 10000L).random(),
                    tag = "test",
                    timestamp = System.currentTimeMillis() / 1_000
                ))
                mapper.senderTagInfos(0).forEach {
                    println(it)
                }
                mapper.groupTagInfos(1).forEach {
                    println(it)
                }
            }
            session.commit()
        }
    }
}