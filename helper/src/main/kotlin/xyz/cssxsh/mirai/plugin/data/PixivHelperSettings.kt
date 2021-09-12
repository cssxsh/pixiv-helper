package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.pixiv.*
import java.io.File

object PixivHelperSettings : ReadOnlyPluginConfig("PixivHelperSettings"), EroStandardConfig {
    @ValueName("cache_path")
    @ValueDescription("缓存目录")
    private val cachePath: String by value("")

    @ValueName("backup_path")
    @ValueDescription("备份目录")
    private val backupPath: String by value("")

    @ValueName("temp_path")
    @ValueDescription("临时目录")
    private val tempPath: String by value("")

    @ValueName("ero_chunk")
    @ValueDescription("色图分块大小")
    val eroChunk: Int by value(ERO_CHUNK)

    @ValueName("ero_up_expire")
    @ValueDescription("色图自动触发更高收藏数的最大时间，单位毫秒")
    val eroUpExpire: Long by value(ERO_UP_EXPIRE)

    @ValueName("ero_work_types")
    @ValueDescription("涩图标准 内容类型 ILLUST, UGOIRA, MANGA, 为空则全部符合")
    private val types_: Set<String> by value(setOf())
    override val types: Set<WorkContentType> by lazy { types_.map { WorkContentType.valueOf(it.uppercase()) }.toSet() }

    @ValueName("ero_bookmarks")
    @ValueDescription("涩图标准 收藏")
    override val bookmarks: Long by value(ERO_BOOKMARKS)

    @ValueName("ero_page_count")
    @ValueDescription("涩图标准 页数")
    override val pages: Int by value(ERO_PAGE_COUNT)

    @ValueName("ero_tag_exclude")
    @ValueDescription("涩图标准 排除的正则表达式")
    private val tagExclude0: String by value(ERO_TAG_EXCLUDE.pattern)
    override val tagExclude: Regex by lazy { tagExclude0.toRegex() }

    @ValueName("ero_user_exclude")
    @ValueDescription("涩图标准 排除的UID")
    override val userExclude: Set<Long> by value(emptySet())

    @ValueName("pximg")
    @ValueDescription("i.pximg.net 反向代理，若非特殊情况不要修改这个配置，保持留空，可以使用 $PixivMirrorHost")
    val pximg: String by value("")

    @ValueName("proxy")
    @ValueDescription("代理 格式 http://127.0.0.1:8080 or socks://127.0.0.1:1080")
    val proxy: String by value("")

    @ValueName("timeout_api")
    @ValueDescription("API超时时间, 单位ms")
    val timeout: Long by value(10_000L)

    @ValueName("timeout_download")
    @ValueDescription("DOWNLOAD超时时间, 单位ms")
    val download: Long by value(30_000L)

    @ValueName("block_size")
    @ValueDescription("DOWNLOAD分块大小, 单位B, 默认 523264, 为零时, 不会分块下载")
    val blockSize: Int by value(512 * HTTP_KILO)

    @ValueName("tag_cooling")
    @ValueDescription("tag 指令冷却时间，检索失败时触发，单位毫秒")
    val tagCooling: Int by value(600_000)

    @ValueName("tag_sfw")
    @ValueDescription("tag 是否过滤r18（依旧不会放出图片的")
    val tagSFW: Boolean by value(false)

    @ValueName("ero_sfw")
    @ValueDescription("ero 是否过滤r18（依旧不会放出图片的")
    val eroSFW: Boolean by value(true)

    private fun getPath(path: String, default: String) =
        if (path.isEmpty()) PixivHelperPlugin.dataFolder.resolve(default) else File(".").resolve(path)

    /**
     * 压缩文件保存目录
     */
    val backupFolder: File get() = getPath(path = backupPath, default = "backup")

    /**
     * 图片缓存保存目录
     */
    val cacheFolder: File get() = getPath(path = cachePath, default = "cache")

    /**
     * 临时文件保存目录
     */
    val tempFolder: File get() = getPath(path = tempPath, default = "temp")

    /**
     * 用户文件保存目录
     */
    val profilesFolder: File get() = cacheFolder.resolve("profile")

    /**
     * 特辑文件保存目录
     */
    val articlesFolder: File get() = cacheFolder.resolve("article")

    /**
     * 图片目录
     */
    fun imagesFolder(pid: Long): File = cacheFolder
        .resolve("%03d______".format(pid / 1_000_000))
        .resolve("%06d___".format(pid / 1_000))
        .resolve("$pid")
}