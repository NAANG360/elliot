package com.naang360.missminutes

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.File
import java.time.Instant

class MainActivity : Activity() {
    companion object {
        init {
            try { System.loadLibrary("missminutes") } catch (_: UnsatisfiedLinkError) {}
        }
    }

    private lateinit var web: WebView
    private lateinit var framework: FrameworkBridge
    private val modelRequest = 7412

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        framework = FrameworkBridge(filesDir.resolve("framework"), this)
        web = WebView(this).apply {
            setBackgroundColor(Color.rgb(12, 8, 4))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.databaseEnabled = true
            webViewClient = AppAssetClient(assetLoader)
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    android.util.Log.e("MissMinutes", "JS: ${message.message()} @${message.sourceId()}:${message.lineNumber()}")
                    return true
                }
            }
            addJavascriptInterface(framework, "MissMinutesNative")
            loadUrl("https://appassets.androidplatform.net/assets/web/index.html")
        }
        setContentView(web)
    }

    private val assetLoader: WebViewAssetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != modelRequest || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val file = framework.importModel(uri) ?: return
        web.post {
            val name = file.name.replace("\\", "\\\\").replace("'", "\\'")
            val path = file.absolutePath.replace("\\", "\\\\").replace("'", "\\'")
            web.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('miss-model-ready',{detail:{name:'$name',path:'$path',ready:true}}))",
                null
            )
        }
    }

    fun openModelPicker() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
            },
            modelRequest
        )
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}

private class AppAssetClient(private val loader: WebViewAssetLoader) : WebViewClientCompat() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
}

class FrameworkBridge(private val root: File, private val activity: MainActivity) {
    init {
        root.mkdirs()
        File(root, "models").mkdirs()
    }

    @JavascriptInterface
    fun frameworkPath(): String = root.absolutePath

    @JavascriptInterface
    fun nativeRuntimeStatus(): String =
        if (NativeBrain.available()) "LOCAL_LLM_READY" else "MODEL_STORAGE_ONLY"

    @JavascriptInterface
    fun generateLocal(modelPath: String, prompt: String): String =
        NativeBrain.generate(modelPath, prompt)

    @JavascriptInterface
    fun checkpoint(json: String, reason: String): String {
        val file = File(root, "checkpoint-${System.currentTimeMillis()}.json")
        file.writeText(json)
        File(root, "last-good.txt").writeText(file.name)
        return file.name
    }

    @JavascriptInterface
    fun windUp(): String {
        val name = File(root, "last-good.txt").takeIf { it.exists() }?.readText()?.trim()
        return if (name.isNullOrBlank()) "" else File(root, name).takeIf { it.exists() }?.readText().orEmpty()
    }

    @JavascriptInterface
    fun recordEvolution(summary: String): Boolean {
        File(root, "evolution.log").appendText("${Instant.now()} | $summary\n")
        return true
    }

    fun importModel(uri: Uri): File? {
        val name = queryName(uri) ?: "model.gguf"
        if (!name.lowercase().endsWith(".gguf")) return null
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val out = File(root, "models/$safe")
        return try {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    private fun queryName(uri: Uri): String? {
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
