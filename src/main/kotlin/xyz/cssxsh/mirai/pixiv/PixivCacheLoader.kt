package xyz.cssxsh.mirai.pixiv

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.mirai.pixiv.task.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.io.File
import kotlin.coroutines.*

public object PixivCacheLoader : CoroutineScope {
    private val logger by lazy { MiraiLogger.Factory.create(this::class, identity = "pixiv-cache-loader") }

    override val coroutineContext: CoroutineContext =
        CoroutineName(name = "pixiv-cache-loader") + SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
            logger.warning({ "$throwable in $context" }, throwable)
        }

    private val jobs: MutableMap<String, Job> = HashMap()

    private val write = Mutex()

    private suspend fun IllustInfo.downalod() {
        try {
            when (type) {
                WorkContentType.ILLUST -> images(illust = this)
                WorkContentType.UGOIRA -> ugoira(illust = this)
                WorkContentType.MANGA -> Unit
            }
        } catch (cause: Exception) {
            logger.warning({ "作品(${pid})<${createAt}>[${user.id}]下载失败" }, cause)
        }
    }

    public suspend fun images(illust: IllustInfo): List<File> {
        val folder = images(pid = illust.pid).apply { mkdirs() }

        /**
         * 全部Url
         */
        val urls = illust.getOriginImageUrls().filter { "${illust.pid}" in it.encodedPath }

        /**
         * 需要下载的 Url
         */
        val downloads = mutableListOf<Url>()

        /**
         * 过滤Url
         */
        val files = urls.map { url ->
            folder.resolve(url.filename).apply {
                if (exists().not()) {
                    downloads.add(url)
                }
            }
        }

        if (downloads.isNotEmpty()) {
            fun FileInfo(url: Url, bytes: ByteArray) = FileInfo(
                pid = illust.pid,
                index = with(url.encodedPath) {
                    val end = lastIndexOf('.')
                    val start = lastIndexOf('p', end) + 1
                    substring(start, end)
                        .toIntOrNull() ?: throw IllegalArgumentException(url.encodedPath)
                },
                md5 = bytes.md5().toUHexString(""),
                url = url.toString(),
                size = bytes.size
            )

            val results = mutableListOf<FileInfo>()
            var size = 0L

            downloads.removeIf { url ->
                val file = TempFolder.resolve(url.filename)
                val exists = file.exists()
                if (exists) {
                    logger.info { "从[${file}]移动文件" }
                    results.add(FileInfo(url = url, bytes = file.readBytes()))
                    file.renameTo(folder.resolve(url.filename))
                } else {
                    false
                }
            }

            PixivHelperDownloader.downloadImageUrls(urls = downloads) { url, deferred ->
                try {
                    val bytes = deferred.await()
                    TempFolder.resolve(url.filename).writeBytes(bytes)
                    size += bytes.size
                    results += FileInfo(url = url, bytes = bytes)
                } catch (cause: Exception) {
                    logger.warning({ "[$url]下载失败" }, cause)
                }
            }

            try {
                results.merge()
            } catch (cause: Exception) {
                logger.warning({ "记录数据失败" }, cause)
            }

            with(illust) {
                logger.debug {
                    "作品(${pid})<${createAt}>[${user.id}][${type}][${title}][${bytes(size)}]{${totalBookmarks}}下载完成"
                }
            }

            for (url in downloads) {
                TempFolder.resolve(url.filename).apply {
                    if (exists()) renameTo(folder.resolve(url.filename))
                }
            }
        }

        return files
    }

    public suspend fun ugoira(illust: IllustInfo, flush: Boolean = false): File {
        val json = ugoira(pid = illust.pid)
        val metadata: UgoiraMetadata
        if (flush || !json.exists()) {
            metadata = PixivClientPool.free().ugoiraMetadata(pid = illust.pid).ugoira
            metadata.write(json)
        } else {
            metadata = json.readUgoiraMetadata()
        }
        return try {
            PixivSkikoGifEncoder.build(illust = illust, metadata = metadata, flush = flush)
        } catch (_: NoClassDefFoundError) {
            PixivHelperGifEncoder.build(illust = illust, metadata = metadata, flush = flush)
        }
    }

    public fun cache(task: PixivCacheTask, handler: TaskCompletionHandler = { _, _ -> }) {
        if (jobs[task.name]?.isActive == true) throw IllegalArgumentException("${task.name} 任务已存在")

        jobs[task.name] = launch {
            var cause: Throwable? = null
            val users: MutableMap<Long, UserBaseInfo> = HashMap()
            try {
                task.flow.collect { page ->
                    val artworks = page.merge(users = users)

                    val downloads: MutableList<Deferred<*>> = ArrayList()
                    for (illust in page) {
                        if (artworks[illust.pid]?.deleted == true) continue
                        if (!illust.isEro()) continue
                        if (task.write) write.withLock {
                            illust.write()
                        }
                        downloads.add(async(Dispatchers.IO) { illust.downalod() })
                    }

                    downloads.awaitAll()
                }
            } catch (exception: CancellationException) {
                cause = exception
            } catch (exception: Exception) {
                logger.warning({ "缓存加载错误" }, exception)
                cause = exception
            } finally {
                jobs.remove(task.name)
                handler.invoke(task, cause)
            }
        }
    }

    public fun detail(): String {
        return jobs.entries.joinToString("\n") { (name, job) ->
            "$name -> $job"
        }
    }

    public fun stop(name: String) {
        jobs.remove(name)?.cancel() ?: throw NoSuchElementException("任务 $name 不存在")
    }
}