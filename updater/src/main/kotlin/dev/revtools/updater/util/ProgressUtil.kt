package dev.revtools.updater.util

import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle

object ProgressUtil {

    private lateinit var progress: ProgressBar

    fun start(name: String, max: Int) {
        progress = ProgressBarBuilder()
            .setTaskName(name)
            .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BAR)
            .setInitialMax(max.toLong())
            .setUpdateIntervalMillis(10)
            .continuousUpdate()
            .setMaxRenderedLength(120)
            .build()
    }

    fun step() {
        progress.step()
    }

    fun stepTo(step: Int) {
        progress.stepTo(step.toLong())
    }

    fun stop() {
        progress.close()
    }
}