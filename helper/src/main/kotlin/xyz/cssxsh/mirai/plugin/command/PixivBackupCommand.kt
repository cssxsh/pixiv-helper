package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.message.nextMessage
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.RemoteFile.Companion.sendFile
import xyz.cssxsh.baidu.disk.getUserInfo
import xyz.cssxsh.baidu.oauth.*
import xyz.cssxsh.baidu.getRapidUploadInfo
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.tools.*
import java.io.File
import java.lang.IllegalStateException
import kotlin.time.minutes

object PixivBackupCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "backup",
    description = "PIXIV备份指令"
) {

    private val compress = Mutex()

    private val upload = Mutex()

    private val bit: (Int) -> Long = { 1L shl it }

    private fun File.size() = length().let { size ->
        when (size) {
            0L -> "0"
            in bit(0) until bit(10) -> "%0.2dB".format(size)
            in bit(10) until bit(20) -> "%0.2dMB".format(size)
            in bit(20) until bit(30) -> "%0.2dGB".format(size)
            else -> throw IllegalStateException("File(${absolutePath}) Too Big")
        }
    }

    private fun CommandSender.compress(block: PixivZipper.() -> List<File>) = PixivHelperPlugin.launch(Dispatchers.IO) {
        compress.withLock { PixivZipper.block() }.forEach { file ->
            if (this@compress is MemberCommandSenderOnMessage && file.length() <= bit(30)) {
                sendMessage("${file.name} ${file.size()} 压缩完毕，开始上传到群文件")
                runCatching {
                    group.sendFile(path = file.name, file = file)
                }.onFailure {
                    sendMessage("[${file.name}]上传失败: ${it.message}")
                }
            } else {
                upload {
                    sendMessage("${file.name} ${file.size()} 压缩完毕，开始上传到百度云")
                    val code = file.getRapidUploadInfo()
                    runCatching {
                        uploadFile(file)
                    }.onSuccess {
                        logger.info { "[${file.name}]上传成功，百度云标准码: ${code.format()} " }
                        sendMessage("[${file.name}]上传成功，百度云标准码: ${code.format()}")
                    }.onFailure {
                        logger.warning({ "[${file.name}]上传失败" }, it)
                        sendMessage("[${file.name}]上传失败, ${it.message}")
                    }
                }
            }
        }
    }

    private fun upload(block: suspend BaiduNetDiskUpdater.() -> Unit) = PixivHelperPlugin.launch(Dispatchers.IO) {
        upload.withLock {
            BaiduNetDiskUpdater.block()
        }
    }

    @SubCommand
    @Description("备份指定用户的作品")
    fun CommandSender.user(uid: Long) = compress {
        compressArtWorks(list = useMappers { it.artwork.userArtWork(uid) }, basename = "USER[${uid}]").let {
            listOf(it)
        }
    }

    @SubCommand
    @Description("备份已设定别名用户的作品")
    fun CommandSender.alias() = compress {
        useMappers { it.statistic.alias() }.associateBy { it.uid }.map { (uid, _) ->
            compressArtWorks(list = useMappers { it.artwork.userArtWork(uid) }, basename = "USER[${uid}]")
        }
    }

    @SubCommand
    @Description("备份指定标签的作品")
    fun CommandSender.tag(tag: String, bookmark: Long = 0) = compress {
        compressArtWorks(list = useMappers { it.artwork.findByTag(tag, bookmark) }, basename = "TAG[${tag}]").let {
            listOf(it)
        }
    }

    @SubCommand
    @Description("备份插件数据")
    fun CommandSender.data() = compress {
        compressData(list = getBackupList())
    }

    @SubCommand
    @Description("列出备份目录")
    suspend fun CommandSender.list() {
        sendMessage(PixivZipper.list().joinToString("\n") { file ->
            "${file.name} ${file.size()}"
        })
    }

    @SubCommand
    @Description("获取备份文件")
    suspend fun MemberCommandSenderOnMessage.get(name: String) {
        runCatching {
            requireNotNull(PixivZipper.find(name = name)) { "文件不存在" }.let { file ->
                group.sendFile(path = file.name, file = file)
            }
        }.onFailure {
            sendMessage("上传失败: ${it.message}")
        }
    }

    @SubCommand
    @Description("上传插件数据到百度云")
    fun CommandSender.upload(name: String) = upload {
        val file = requireNotNull(PixivZipper.find(name = name)) { "文件不存在" }
        val code = file.getRapidUploadInfo().format()
        runCatching {
            uploadFile(file = file)
        }.onSuccess { info ->
            logger.info { "[${name}]($code)上传成功: $info" }
            sendMessage("[${name}]($code)上传成功: $info")
        }.onFailure {
            logger.warning({ "[${name}]上传失败" }, it)
            sendMessage("[${name}]上传失败, ${it.message}")
        }
    }

    @SubCommand
    @Description("百度云用户认证")
    fun CommandSenderOnMessage<*>.auth() = upload {
        sendMessage("请打开连接，然后在十分钟内输入获得的认证码: ${getWebAuthorizeUrl(type = AuthorizeType.AUTHORIZATION)}")
        runCatching {
            val code = fromEvent.nextMessage(((10).minutes.toLongMilliseconds())).content
            getAuthorizeToken(code = code).also { saveToken(token = it) } to getUserInfo()
        }.onSuccess { (token, user) ->
            logger.info { "百度云用户认证成功, ${user.baiduName} by $token" }
            sendMessage("百度云用户认证成功, ${user.baiduName} by $token")
        }.onFailure {
            logger.warning({ "认证失败" }, it)
            sendMessage("百度云用户认证失败, ${it.message}")
        }
    }
}