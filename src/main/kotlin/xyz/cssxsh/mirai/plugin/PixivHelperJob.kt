package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

class PixivHelperJob(
    private val contactInfo: String,
    parent: Job? = null
) : CompletableJob by SupervisorJob(parent) {
    override fun toString(): String = "PixivHelperJob for $contactInfo"
}