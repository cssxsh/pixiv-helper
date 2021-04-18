package xyz.cssxsh.pixiv.dao

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
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
        val file = File("D:\\Users\\CSSXSH\\IdeaProjects\\pixiv-helper\\test\\data\\pixiv-helper\\PixivAlias.json")
        val list = Json.decodeFromString<JsonObject>(file.readText()).getValue("aliases").jsonObject.map { (a, id) ->
            AliasSetting(a, id.jsonPrimitive.long)
        }
        sqlSessionFactory.openSession().use { session ->
            session.getMapper(StatisticInfoMapper::class.java).let { mapper ->
                list.forEach {
                    mapper.replaceAliasSetting(it)
                }
            }
            session.commit()
        }
    }

    @Test
    fun statistic() {
        sqlSessionFactory.openSession(true).use {
            it.getMapper(StatisticInfoMapper::class.java).let { mapper ->
                mapper.findSearchResult("78fcde93917b98823b20a9e553cda0b1").let {
                    println(it)
                }
                mapper.noCacheSearchResult().let {
                    println(it)
                }
            }
        }
    }
}