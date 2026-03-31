package com.me.zapret.android.update

import android.content.Context
import com.me.zapret.android.core.CoreBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.ZipInputStream

@Serializable
data class CoreManifest(
    @SerialName("core_version") val coreVersion: String,
    @SerialName("min_app_version") val minAppVersion: String,
    @SerialName("config_schema_version") val configSchemaVersion: Int,
    val abis: List<String>,
    val files: List<ManifestFile>,
    val signature: String,
)

@Serializable
data class ManifestFile(
    val path: String,
    val sha256: String,
    val size: Long,
)

@Serializable
data class CoreManifestPayload(
    @SerialName("core_version") val coreVersion: String,
    @SerialName("min_app_version") val minAppVersion: String,
    @SerialName("config_schema_version") val configSchemaVersion: Int,
    val abis: List<String>,
    val files: List<ManifestFile>,
)

data class TokenTestResult(
    val ok: Boolean,
    val message: String,
    val rateLimitRemaining: String? = null,
)

data class CoreHealthcheckResult(
    val ok: Boolean,
    val message: String,
)

data class UpdateStageResult(
    val ok: Boolean,
    val message: String,
    val stagedVersion: String? = null,
    val selectedAbi: String? = null,
)

fun interface CoreCandidateHealthchecker {
    fun verify(candidateLibrary: File, configJson: String): CoreHealthcheckResult
}

class BridgeBackedHealthchecker(
    private val coreBridge: CoreBridge,
) : CoreCandidateHealthchecker {
    override fun verify(candidateLibrary: File, configJson: String): CoreHealthcheckResult {
        // The injected NativeBindings implementation is responsible for wiring staged `.so` loading.
        val response = coreBridge.healthcheckConfig(configJson)
        return if (response.ok) {
            CoreHealthcheckResult(true, "Healthcheck passed for ${candidateLibrary.name}")
        } else {
            CoreHealthcheckResult(false, response.error ?: "Healthcheck failed")
        }
    }
}

class CoreUpdateManager(
    private val context: Context,
    private val coreBridge: CoreBridge,
    private val tokenStore: GitHubTokenStore = GitHubTokenStore(context),
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val embeddedEd25519PublicKeyBase64: String = "",
    private val healthchecker: CoreCandidateHealthchecker = BridgeBackedHealthchecker(coreBridge),
) {
    suspend fun testToken(apiBaseUrl: String, repoReference: String, token: String?): TokenTestResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${apiBaseUrl.trimEnd('/')}/repos/$repoReference")
                .header("Accept", "application/vnd.github+json")
                .apply {
                    if (!token.isNullOrBlank()) {
                        header("Authorization", "Bearer $token")
                    }
                }
                .build()

            runCatching { httpClient.newCall(request).execute() }.fold(
                onSuccess = { response ->
                    response.use {
                        val remaining = response.header("X-RateLimit-Remaining")
                        when {
                            response.isSuccessful -> TokenTestResult(true, "Token accepted", remaining)
                            response.code == 401 || response.code == 403 ->
                                TokenTestResult(false, "Authorization failed", remaining)
                            response.code == 404 ->
                                TokenTestResult(false, "Repository not found or inaccessible", remaining)
                            else -> TokenTestResult(false, "GitHub API error: ${response.code}", remaining)
                        }
                    }
                },
                onFailure = { error -> TokenTestResult(false, error.message ?: "Request failed") },
            )
        }

    fun saveToken(token: String?) {
        tokenStore.save(token)
    }

    fun loadToken(): String? = tokenStore.load()

    suspend fun stageUpdate(
        zipFile: File,
        appVersion: String,
        supportedAbis: List<String>,
        schemaVersion: Int = coreBridge.getSchemaVersion(),
    ): UpdateStageResult = withContext(Dispatchers.IO) {
        val extractionDir = File(context.filesDir, "core_updates/extracted").apply { mkdirs() }
        extractionDir.deleteRecursively()
        extractionDir.mkdirs()

        unzip(zipFile, extractionDir)
        val manifestFile = File(extractionDir, "core_manifest.json")
        if (!manifestFile.exists()) {
            return@withContext UpdateStageResult(false, "core_manifest.json is missing")
        }

        val manifest = json.decodeFromString<CoreManifest>(manifestFile.readText())
        if (!verifyManifestSignature(manifest)) {
            return@withContext UpdateStageResult(false, "manifest signature verification failed")
        }
        if (manifest.configSchemaVersion != schemaVersion) {
            return@withContext UpdateStageResult(false, "schema mismatch: ${manifest.configSchemaVersion} != $schemaVersion")
        }
        if (compareVersions(manifest.minAppVersion, appVersion) > 0) {
            return@withContext UpdateStageResult(false, "app version $appVersion is below required ${manifest.minAppVersion}")
        }
        if (!verifyFiles(extractionDir, manifest)) {
            return@withContext UpdateStageResult(false, "file size or sha256 mismatch")
        }
        val selectedAbi = supportedAbis.firstOrNull { manifest.abis.contains(it) }
            ?: return@withContext UpdateStageResult(false, "no compatible ABI found")

        val candidateLibrary = File(extractionDir, "$selectedAbi/libzapret_core.so")
        if (!candidateLibrary.exists()) {
            return@withContext UpdateStageResult(false, "missing staged library for $selectedAbi")
        }
        val healthcheck = healthchecker.verify(candidateLibrary, sampleHealthcheckConfig(appVersion))
        if (!healthcheck.ok) {
            return@withContext UpdateStageResult(false, healthcheck.message)
        }

        val stagingDir = File(context.filesDir, "core_updates/staged/${manifest.coreVersion}").apply {
            deleteRecursively()
            mkdirs()
        }
        extractionDir.copyRecursively(stagingDir, overwrite = true)
        UpdateStageResult(
            ok = true,
            message = "update staged for next VPN start",
            stagedVersion = manifest.coreVersion,
            selectedAbi = selectedAbi,
        )
    }

    private fun sampleHealthcheckConfig(appVersion: String): String = """
        {
          "app_version":"$appVersion",
          "core_version":"0.1.0",
          "config_schema_version":${coreBridge.getSchemaVersion()},
          "feature_flags":{
            "enable_tls_sni":true,
            "enable_http_host":true,
            "enable_aggressive_split":true,
            "enable_self_test_ab":true
          },
          "active_profile_id":"healthcheck",
          "profiles":[
            {
              "id":"healthcheck",
              "name":"Healthcheck",
              "preset":"Balanced",
              "custom_techniques":null,
              "rules":[],
              "quic_policy":"Allow",
              "fallback_policy":{
                "aggressive_threshold":3,
                "balanced_threshold":2,
                "time_window_seconds":300
              }
            }
          ],
          "settings":{
            "language":"EN",
            "theme_mode":"SYSTEM",
            "debug_logs":false,
            "safe_mode_enabled":true,
            "updates":{
              "repo_reference":"owner/repo",
              "api_base_url":"https://api.github.com",
              "auto_update":true,
              "wifi_only":true,
              "apply_on_next_vpn_start":true
            }
          },
          "last_valid_config_snapshot":null
        }
    """.trimIndent()

    private fun verifyManifestSignature(manifest: CoreManifest): Boolean {
        if (embeddedEd25519PublicKeyBase64.isBlank()) {
            return false
        }
        val payload = json.encodeToString(
            CoreManifestPayload(
                coreVersion = manifest.coreVersion,
                minAppVersion = manifest.minAppVersion,
                configSchemaVersion = manifest.configSchemaVersion,
                abis = manifest.abis,
                files = manifest.files,
            ),
        ).toByteArray()
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(embeddedEd25519PublicKeyBase64)),
        )
        return Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(payload)
            verify(Base64.getDecoder().decode(manifest.signature))
        }
    }

    private fun verifyFiles(root: File, manifest: CoreManifest): Boolean =
        manifest.files.all { file ->
            val target = File(root, file.path)
            target.exists()
                && target.length() == file.size
                && sha256(target).equals(file.sha256, ignoreCase = true)
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun unzip(zipFile: File, outputDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val destination = File(outputDir, entry.name)
                if (entry.isDirectory) {
                    destination.mkdirs()
                } else {
                    destination.parentFile?.mkdirs()
                    FileOutputStream(destination).use { output -> zip.copyTo(output) }
                }
            }
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split('.').map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split('.').map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until maxSize) {
            val l = leftParts.getOrElse(index) { 0 }
            val r = rightParts.getOrElse(index) { 0 }
            if (l != r) {
                return l.compareTo(r)
            }
        }
        return 0
    }
}
