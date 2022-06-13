package xyz.cssxsh.mirai.pixiv.task

import com.cronutils.descriptor.*
import com.cronutils.model.*
import com.cronutils.model.definition.*
import com.cronutils.model.time.*
import com.cronutils.parser.*
import java.util.*

internal const val CRON_TYPE_KEY = "xyz.cssxsh.mirai.pixiv.cron.type"

public val DefaultCronParser: CronParser by lazy {
    val type = CronType.valueOf(System.getProperty(CRON_TYPE_KEY, CronType.QUARTZ.name))
    CronParser(CronDefinitionBuilder.instanceDefinitionFor(type))
}

internal const val CRON_LOCALE_KEY = "xyz.cssxsh.mirai.pixiv.cron.locale"

public val DefaultCronDescriptor: CronDescriptor by lazy {
    val locale = System.getProperty(CRON_LOCALE_KEY)?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
    CronDescriptor.instance(locale)
}

public fun Cron.asData(): DataCron = this as? DataCron ?: DataCron(delegate = this)

public fun Cron.toExecutionTime(): ExecutionTime = ExecutionTime.forCron((this as? DataCron)?.delegate ?: this)

public fun Cron.description(): String = DefaultCronDescriptor.describe(this)