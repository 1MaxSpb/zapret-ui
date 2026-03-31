package com.me.zapret.android.diagnostics

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DiagnosticBundleExporter(
    private val context: Context,
) {
    fun export(
        destination: File,
        logFiles: List<File>,
        configJson: String,
        metadata: Map<String, String>,
    ): File {
        destination.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(destination)).use { zip ->
            logFiles.filter { it.exists() }.forEach { file ->
                addFile(zip, "logs/${file.name}", file.readBytes())
            }
            addFile(zip, "config/redacted_config.json", redactSecrets(configJson).toByteArray())
            addFile(
                zip,
                "meta/device.txt",
                metadata.entries.joinToString("\n") { (key, value) -> "$key=$value" }.toByteArray(),
            )
        }
        return destination
    }

    private fun addFile(zip: ZipOutputStream, entryName: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun redactSecrets(input: String): String =
        input.replace(Regex("(?i)\"(token|pat)\"\\s*:\\s*\"[^\"]*\""), "\"token\":\"REDACTED\"")
}
