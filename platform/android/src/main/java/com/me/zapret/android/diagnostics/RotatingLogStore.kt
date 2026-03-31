package com.me.zapret.android.diagnostics

import android.content.Context
import java.io.File
import java.time.Instant

class RotatingLogStore(
    context: Context,
    private val maxFiles: Int = 5,
    private val maxFileBytes: Long = 2L * 1024L * 1024L,
) {
    private val logDir = File(context.filesDir, "logs").apply { mkdirs() }

    fun append(level: String, tag: String, message: String) {
        val entry = "${Instant.now()} [$level] $tag $message\n"
        val target = currentFile()
        target.appendText(entry)
        if (target.length() > maxFileBytes) {
            rotate()
        }
    }

    fun listFiles(): List<File> =
        logDir.listFiles()?.sortedByDescending(File::name).orEmpty()

    private fun currentFile(): File = File(logDir, "current.log")

    private fun rotate() {
        for (index in maxFiles - 1 downTo 1) {
            val from = File(logDir, "log-$index.log")
            val to = File(logDir, "log-${index + 1}.log")
            if (from.exists()) {
                from.renameTo(to)
            }
        }
        currentFile().renameTo(File(logDir, "log-1.log"))
        listFiles()
            .drop(maxFiles)
            .forEach(File::delete)
    }
}
