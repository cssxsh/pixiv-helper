package xyz.cssxsh.mirai.plugin.tools

import com.soywiz.klock.jvm.toDate
import com.soywiz.klock.wrapped.WDateTimeTz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.*
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.data.BaseInfo
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.mirai.plugin.data.PixivStatisticalData
import xyz.cssxsh.mirai.plugin.isR18
import java.io.File

@Suppress("unused")
object PoiTool: PixivHelperLogger {
    private fun xlsxFile() = File(
        PixivHelperSettings.zipFolder,
        "PixivData(${WDateTimeTz.nowLocal().format("yyyy-MM-dd-HH-mm-ss")}).xlsx"
    )

    private val PIXIV_CACHE_DATA_HEADER = listOf(
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

    private fun XSSFRow.writeInfo(info: BaseInfo, dateTimeStyle: XSSFCellStyle) {
        createCell(PIXIV_CACHE_DATA_HEADER.indexOf("PID")).setCellValue(info.pid.toDouble())
        createCell(PIXIV_CACHE_DATA_HEADER.indexOf("TITLE")).setCellValue(info.title)
        createCell(PIXIV_CACHE_DATA_HEADER.indexOf("CREATE_DATE")).apply {
            setCellValue(info.createDate.local.toDate())
            cellStyle = dateTimeStyle
        }
        createCell(PIXIV_CACHE_DATA_HEADER.indexOf("PAGE_COUNT")).setCellValue(info.pageCount.toDouble())
        createCell(PIXIV_CACHE_DATA_HEADER.indexOf("SANITY_LEVEL")).setCellValue(info.sanityLevel.toDouble())
        createCell(PIXIV_CACHE_DATA_HEADER.indexOf("TYPE")).setCellValue(info.type.name)
        createCell(PIXIV_CACHE_DATA_HEADER.indexOf("IS_R18")).setCellValue(info.isR18())
        createCell(PIXIV_CACHE_DATA_HEADER.indexOf("WIDTH")).setCellValue(info.width.toDouble())
        createCell(PIXIV_CACHE_DATA_HEADER.indexOf("HEIGHT")).setCellValue(info.height.toDouble())
        createCell(PIXIV_CACHE_DATA_HEADER.indexOf("USER_ID")).setCellValue(info.uid.toDouble())
        createCell(PIXIV_CACHE_DATA_HEADER.indexOf("USER_NAME")).setCellValue(info.uname)
        createCell(PIXIV_CACHE_DATA_HEADER.indexOf("TOTAL_BOOKMARKS")).setCellValue(info.totalBookmarks.toDouble())
    }

    private fun XSSFSheet.writeInfos(dateTimeStyle: XSSFCellStyle) = apply {
        setDefaultColumnStyle(PIXIV_CACHE_DATA_HEADER.indexOf("CREATE_DATE"), dateTimeStyle)
        PixivCacheData.caches().values.forEachIndexed { row, info ->
            createRow(row + 1).writeInfo(info, dateTimeStyle)
        }
        createRow(0).apply {
            PIXIV_CACHE_DATA_HEADER.forEachIndexed { column, text ->
                createCell(column).setCellValue(text)
                autoSizeColumn(column)
            }
        }
        setDefaultColumnStyle(PIXIV_CACHE_DATA_HEADER.indexOf("CREATE_DATE"), workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss")
        })
    }

    private val PIXIV_TAG_DATA_HEADER = listOf("TAG", "TOTAL")

    private fun XSSFSheet.writeTags() = apply {
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
                createCell(PIXIV_TAG_DATA_HEADER.indexOf("TAG")).setCellValue(tag)
                createCell(PIXIV_TAG_DATA_HEADER.indexOf("TOTAL")).setCellValue(num.toDouble())
            }
        }
        createRow(0).apply {
            PIXIV_TAG_DATA_HEADER.forEachIndexed { column, text ->
                createCell(column).setCellValue(text)
                autoSizeColumn(column)
            }
        }
    }

    private val PIXIV_STATISTICAL_DATA_HEADER = listOf("QQ", "ERO", "TAG")

    private fun XSSFSheet.writeStatistical() = apply {
        PixivStatisticalData.getMap().entries.forEachIndexed { row, (qq, data) ->
            createRow(row + 1).apply {
                createCell(PIXIV_STATISTICAL_DATA_HEADER.indexOf("QQ")).setCellValue(qq.toDouble())
                createCell(PIXIV_STATISTICAL_DATA_HEADER.indexOf("ERO")).setCellValue(data.eroCount.toDouble())
                data.tagCount.entries.forEachIndexed { row, (tag, total) ->
                    createCell(row + 2).setCellValue("$tag: $total")
                }
            }
        }
        createRow(0).apply {
            PIXIV_STATISTICAL_DATA_HEADER.forEachIndexed { column, text ->
                createCell(column).setCellValue(text)
                autoSizeColumn(column)
            }
        }
    }

    fun saveCacheToXlsxAsync() = PixivHelperPlugin.async(Dispatchers.IO) {
        XSSFWorkbookFactory.createWorkbook().use { workbook ->
            workbook.apply {
                createSheet("PIXIV_CACHE_DATA").writeInfos(createCellStyle().apply {
                    dataFormat = workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss")
                })
                createSheet("PIXIV_TAG_DATA").writeTags()
                createSheet("PIXIV_STATISTICAL_DATA").writeStatistical()
            }
            xlsxFile().apply {
                outputStream().use {
                    workbook.write(it)
                }
                logger.verbose("数据将保存至${absolutePath}")
            }
        }
    }
}