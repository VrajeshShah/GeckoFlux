package com.geckoflux.extensions

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Manages the lifecycle, first-run download, and installation of the uBlock Origin WebExtension.
 */
object UblockManager {

    private const val TAG = "UblockManager"
    const val EXTENSION_ID = "uBlock0@raymondhill.net"
    private const val AMO_DOWNLOAD_URL =
        "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
    private const val MAX_XPI_SIZE = 50 * 1024 * 1024L // 50 MB DoS prevention limit

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    interface InstallListener {
        /**
         * Invoked when uBlock Origin is missing and needs to be downloaded.
         */
        fun onDownloadStarted()

        /**
         * Invoked periodically as the .xpi is downloaded.
         */
        fun onDownloadProgress(percent: Int, bytesRead: Long, totalBytes: Long)

        /**
         * Invoked when the .xpi is downloaded and is being installed into GeckoView.
         */
        fun onInstalling()

        /**
         * Invoked when uBlock Origin is ready (either already present or freshly installed).
         */
        fun onReady()

        /**
         * Invoked if download or installation fails.
         */
        fun onError(error: Throwable)
    }

    /**
     * Checks if uBlock Origin is installed. If so, immediately invokes onReady() and triggers
     * a background update check. If not, begins the download and installation sequence.
     */
    fun ensureInstalled(
        context: Context,
        runtime: GeckoRuntime,
        listener: InstallListener
    ) {
        setupPromptDelegate(runtime)

        runtime.webExtensionController.list().accept({ extensions ->
            val existing = extensions?.find { it.id == EXTENSION_ID }
            if (existing != null) {
                Log.d(TAG, "uBlock Origin is already installed (${existing.id}).")
                // Check for updates asynchronously without blocking startup
                checkBackgroundUpdate(runtime, existing)
                mainHandler.post { listener.onReady() }
            } else {
                Log.d(TAG, "uBlock Origin not found in profile. Starting first-run install.")
                mainHandler.post { listener.onDownloadStarted() }
                startDownloadAndInstall(context.applicationContext, runtime, listener)
            }
        }, { error ->
            Log.w(TAG, "Could not list extensions, attempting install flow", error)
            mainHandler.post { listener.onDownloadStarted() }
            startDownloadAndInstall(context.applicationContext, runtime, listener)
        })
    }

    /**
     * Installs or re-installs uBlock Origin. Useful when retrying after a failed attempt.
     */
    fun retry(
        context: Context,
        runtime: GeckoRuntime,
        listener: InstallListener
    ) {
        setupPromptDelegate(runtime)
        mainHandler.post { listener.onDownloadStarted() }
        startDownloadAndInstall(context.applicationContext, runtime, listener)
    }

    private fun setupPromptDelegate(runtime: GeckoRuntime) {
        runtime.webExtensionController.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                technicalAndInteractionData: Array<out String>
            ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                if (extension.id == EXTENSION_ID) {
                    Log.d(TAG, "Auto-granting permissions for extension: ${extension.id}")
                    return GeckoResult.fromValue(
                        WebExtension.PermissionPromptResponse(
                            true, // isPermissionsGranted
                            true, // isPrivateModeGranted
                            false // isTechnicalAndInteractionDataGranted
                        )
                    )
                } else {
                    Log.w(TAG, "Denying permissions for untrusted extension: ${extension.id}")
                    return GeckoResult.fromValue(
                        WebExtension.PermissionPromptResponse(
                            false,
                            false,
                            false
                        )
                    )
                }
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                technicalAndInteractionData: Array<out String>
            ): GeckoResult<AllowOrDeny>? {
                return if (extension.id == EXTENSION_ID) {
                    GeckoResult.fromValue(AllowOrDeny.ALLOW)
                } else {
                    Log.w(TAG, "Denying update prompt for untrusted extension: ${extension.id}")
                    GeckoResult.fromValue(AllowOrDeny.DENY)
                }
            }

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                technicalAndInteractionData: Array<out String>
            ): GeckoResult<AllowOrDeny>? {
                return if (extension.id == EXTENSION_ID) {
                    GeckoResult.fromValue(AllowOrDeny.ALLOW)
                } else {
                    Log.w(TAG, "Denying optional prompt for untrusted extension: ${extension.id}")
                    GeckoResult.fromValue(AllowOrDeny.DENY)
                }
            }
        }
    }

    private fun checkBackgroundUpdate(runtime: GeckoRuntime, extension: WebExtension) {
        executor.execute {
            try {
                mainHandler.post {
                    runtime.webExtensionController.update(extension).accept({ updated ->
                        Log.d(TAG, "uBlock Origin background update check complete: ${updated?.id}")
                    }, { error ->
                        Log.d(TAG, "uBlock Origin background update skipped: ${error?.message}")
                    })
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error initiating background update", e)
            }
        }
    }

    private fun startDownloadAndInstall(
        appContext: Context,
        runtime: GeckoRuntime,
        listener: InstallListener
    ) {
        executor.execute {
            var tempFile: File? = null
            try {
                val extensionsDir = File(appContext.filesDir, "extensions").apply { mkdirs() }
                val targetFile = File(extensionsDir, "ublock_origin.xpi")
                val tmp = File(extensionsDir, "ublock_origin.xpi.tmp")
                tempFile = tmp

                if (tmp.exists()) {
                    tmp.delete()
                }

                downloadXpiWithProgress(tmp, listener)

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!tmp.renameTo(targetFile)) {
                    throw IllegalStateException("Failed to move temporary XPI to destination.")
                }
                tempFile = null

                mainHandler.post {
                    listener.onInstalling()
                    installIntoGeckoView(runtime, targetFile, listener)
                }
            } catch (t: Throwable) {
                tempFile?.let {
                    if (it.exists()) it.delete()
                }
                Log.e(TAG, "Download/installation failed", t)
                mainHandler.post { listener.onError(t) }
            }
        }
    }

    private fun downloadXpiWithProgress(
        destinationFile: File,
        listener: InstallListener
    ) {
        var currentUrl = AMO_DOWNLOAD_URL
        var connection: HttpURLConnection? = null
        var redirects = 0
        val maxRedirects = 5

        while (redirects <= maxRedirects) {
            if (!currentUrl.startsWith("https://")) {
                throw SecurityException("Refusing to follow insecure redirect: $currentUrl")
            }
            val url = URL(currentUrl)
            val conn = url.openConnection() as HttpURLConnection
            connection = conn
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Android; Mobile; rv:154.0) Gecko/154.0 Firefox/154.0"
            )

            val status = conn.responseCode
            if (status in 300..399) {
                redirects++
                if (redirects > maxRedirects) {
                    conn.disconnect()
                    throw java.io.IOException("Too many redirects ($redirects) while downloading extension")
                }
                val redirectLocation = conn.getHeaderField("Location")
                conn.disconnect()
                if (redirectLocation != null) {
                    val resolvedUrl = if (redirectLocation.startsWith("http://") || redirectLocation.startsWith("https://")) {
                        redirectLocation
                    } else {
                        URL(URL(currentUrl), redirectLocation).toString()
                    }
                    if (!resolvedUrl.startsWith("https://")) {
                        throw SecurityException("Insecure redirect location: $resolvedUrl")
                    }
                    currentUrl = resolvedUrl
                    continue
                } else {
                    throw java.io.IOException("Redirect response $status missing Location header")
                }
            }
            break
        }

        val conn = connection ?: throw IllegalStateException("Could not establish connection.")
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw IllegalStateException("Server returned HTTP response ${conn.responseCode}")
        }

        val totalBytes = conn.contentLengthLong
        var bytesReadTotal: Long = 0
        var lastReportedPercent = -1

        var input: InputStream? = null
        var output: FileOutputStream? = null

        try {
            input = conn.inputStream
            output = FileOutputStream(destinationFile)
            val buffer = ByteArray(8192)
            var read: Int

            while (input.read(buffer).also { read = it } != -1) {
                bytesReadTotal += read
                if (bytesReadTotal > MAX_XPI_SIZE) {
                    throw java.io.IOException("Download exceeded maximum allowed size ($MAX_XPI_SIZE bytes)")
                }
                output.write(buffer, 0, read)

                val percent = if (totalBytes > 0) {
                    ((bytesReadTotal * 100) / totalBytes).toInt()
                } else {
                    -1
                }

                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent
                    mainHandler.post {
                        listener.onDownloadProgress(percent, bytesReadTotal, totalBytes)
                    }
                }
            }
            output.flush()
        } finally {
            try { input?.close() } catch (_: Exception) {}
            try { output?.close() } catch (_: Exception) {}
            conn.disconnect()
        }

        // Validate that the downloaded file is a valid ZIP/XPI archive (magic bytes PK\x03\x04)
        if (!isValidXpiArchive(destinationFile)) {
            destinationFile.delete()
            throw SecurityException("Downloaded extension file is corrupted or not a valid XPI/ZIP archive")
        }
    }

    /**
     * Verifies the magic bytes of a ZIP/XPI archive (0x50, 0x4B, 0x03, 0x04).
     */
    fun isValidXpiArchive(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            java.io.FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                val read = fis.read(header)
                read == 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun installIntoGeckoView(
        runtime: GeckoRuntime,
        xpiFile: File,
        listener: InstallListener
    ) {
        val fileUri = "file://${xpiFile.absolutePath}"
        Log.d(TAG, "Installing XPI from URI: $fileUri")

        runtime.webExtensionController.install(fileUri).accept({ extension ->
            Log.d(TAG, "uBlock Origin successfully installed: ${extension?.id}")
            listener.onReady()
        }, { error ->
            Log.e(TAG, "GeckoView failed to install uBlock Origin", error)
            listener.onError(error ?: RuntimeException("Unknown installation error"))
        })
    }
}
