package xyz.cssxsh.mirai.plugin.dao

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.apache.ibatis.session.SqlSessionFactory
import org.apache.ibatis.session.SqlSessionFactoryBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.sqlite.JDBC
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*
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
        sqlSessionFactory.configuration.apply {
            init()
            (environment.dataSource as SQLiteConnectionPoolDataSource).apply {
                url =  "${JDBC.PREFIX}${sqlite.absolutePath}"
            }
        }
    }

    @Test
    fun useArtWorkInfoMapper(): Unit = sqlSessionFactory.openSession(true).use { session ->
        val pid = 2086L
        val interval = 0 until 1_000_000L
        session.getMapper(ArtWorkInfoMapper::class.java).userArtWork(464).let {
            println(it)
        }
        session.getMapper(ArtWorkInfoMapper::class.java).count().let {
            println(it)
        }
        session.getMapper(ArtWorkInfoMapper::class.java).findByPid(pid).let {
            println(it)
        }
        session.getMapper(ArtWorkInfoMapper::class.java).artworks(interval).let {
            println(it.size)
        }
        session.getMapper(ArtWorkInfoMapper::class.java).findByTag("淫纹", 0).let {
            println(it.size)
        }
    }

    @Test
    fun saveIllustInfo() {
        val pid = 24924L
        imagesFolder(pid).resolve("${pid}.json").readIllustInfo().run {
            sqlSessionFactory.openSession().use { session ->
                session.getMapper(UserInfoMapper::class.java).replaceUser(user.toUserBaseInfo())
                session.getMapper(ArtWorkInfoMapper::class.java).replaceArtWork(toArtWorkInfo())
                session.getMapper(TagInfoMapper::class.java).replaceTags(toTagInfo())
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
        sqlSessionFactory.openSession(true).use { session ->
            session.getMapper(StatisticInfoMapper::class.java).let { mapper ->
                buildString {
                    appendLine("| count\t | name  ")
                    appendLine("| -----\t | ----- ")
                    mapper.top(10).forEach { (name, count) ->
                        appendLine("| $count\t | $name ")
                    }
                }.let {
                    println(it)
                }
            }
        }
    }
}