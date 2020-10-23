package xyz.cssxsh.mirai.plugin.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateResultData(
    @SerialName("category")
    val category: Int,
    @SerialName("ctime")
    val ctime: Int,
    @SerialName("errno")
    val errno: Int,
    @SerialName("fs_id")
    val fsId: Long,
    @SerialName("isdir")
    val isDir: Int,
    @SerialName("md5")
    val md5: String,
    @SerialName("mtime")
    val mtime: Int,
    @SerialName("name")
    val name: String,
    @SerialName("path")
    val path: String,
    @SerialName("server_filename")
    val serverFilename: String? = null,
    @SerialName("size")
    val size: Int
)