package xyz.cssxsh.mirai.plugin.tools

import com.soywiz.klock.DateFormat
import com.soywiz.klock.wrapped.WDateTimeTz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbookFactory
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.data.BaseInfo
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.mirai.plugin.data.PixivStatisticalData
import xyz.cssxsh.mirai.plugin.isR18
import java.io.File

@Suppress("unused")
object PoiTool {
    private fun xlsxFile() = File(
        PixivHelperSettings.zipFolder,
        "PixivData(${WDateTimeTz.nowLocal().format("yyyy-MM-dd-HH-mm-ss")}).xlsx"
    )

    private val headerTexts = listOf(
        "PID",
        "TITLE",
        "CREATE_DATE",
        "PAGE_COUNT",
        "SANITY_LEVEL",
        "TYPE",
        "IS_R18",
        "WIDTH",
        "HEIGHT",
        "USER_ID",
        "USER_NAME",
        "TOTAL_BOOKMARKS"
    )

    private fun Row.writeInfo(info: BaseInfo) {
        createCell(headerTexts.indexOf("PID")).setCellValue(info.pid.toDouble())
        createCell(headerTexts.indexOf("TITLE")).setCellValue(info.title)
        createCell(headerTexts.indexOf("CREATE_DATE")).setCellValue(info.createDate.toString(DateFormat.FORMAT_DATE))
        createCell(headerTexts.indexOf("PAGE_COUNT")).setCellValue(info.pageCount.toDouble())
        createCell(headerTexts.indexOf("SANITY_LEVEL")).setCellValue(info.sanityLevel.toDouble())
        createCell(headerTexts.indexOf("TYPE")).setCellValue(info.type.name)
        createCell(headerTexts.indexOf("IS_R18")).setCellValue(info.isR18())
        createCell(headerTexts.indexOf("WIDTH")).setCellValue(info.width.toDouble())
        createCell(headerTexts.indexOf("HEIGHT")).setCellValue(info.height.toDouble())
        createCell(headerTexts.indexOf("USER_ID")).setCellValue(info.uid.toDouble())
        createCell(headerTexts.indexOf("USER_NAME")).setCellValue(info.uname)
        createCell(headerTexts.indexOf("TOTAL_BOOKMARKS")).setCellValue(info.totalBookmarks.toDouble())
    }

    private fun Sheet.writeInfos() = apply {
        createRow(0).apply {
            headerTexts.forEachIndexed { column, text ->
                createCell(column).setCellValue(text)
            }
        }
        PixivCacheData.caches().values.forEachIndexed { row, info ->
            createRow(row + 1).writeInfo(info)
        }
    }

    private fun Sheet.writeTags() = apply {
        createRow(0).apply {
            createCell(0).setCellValue("TAG")
            createCell(1).setCellValue("TOTAL")
        }
        buildMap<String, Int> {
            PixivCacheData.caches().values.flatMap {
                it.tags
            }.forEach { tag ->
                tag.name.let {
                    put(it, getOrDefault(it, 0) + 1)
                }
                tag.translatedName?.let {
                    put(it, getOrDefault(it, 0) + 1)
                }
            }
        }.entries.forEachIndexed { row, (tag, num) ->
            createRow(row + 1).apply {
                createCell(0).setCellValue(tag)
                createCell(1).setCellValue(num.toDouble())
            }
        }
    }

    private fun Sheet.writeStatistical() = apply {
        createRow(0).apply {
            createCell(0).setCellValue("QQ")
            createCell(1).setCellValue("ERO")
            createCell(2).setCellValue("TAG")
        }
        PixivStatisticalData.getMap().entries.forEachIndexed { row, (qq, data) ->
            createRow(row + 1).apply {
                createCell(0).setCellValue(qq.toDouble())
                createCell(1).setCellValue(data.eroCount.toDouble())
                data.tagCount.entries.forEachIndexed { row, (tag, total) ->
                    createCell(row + 2).setCellValue("$tag: $total")
                }
            }
        }
    }

    fun saveCacheToXlsxAsync() = PixivHelperPlugin.async(Dispatchers.IO) {
        XSSFWorkbookFactory.createWorkbook().use { workbook ->
            workbook.createSheet("PIXIV_CACHE_DATA").writeInfos()
            workbook.createSheet("PIXIV_TAG_DATA").writeTags()
            workbook.createSheet("PIXIV_STATISTICAL_DATA").writeStatistical()
            xlsxFile().apply {
                outputStream().use {
                    workbook.write(it)
                }
            }
        }
    }
}