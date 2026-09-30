package com.naang360.missminutes

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.File
import java.time.Instant

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var framework: FrameworkBridge

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        framework = FrameworkBridge(filesDir.resolve("framework"))

        web = WebView(this).apply {
            setBackgroundColor(Color.rgb(12, 10, 7))
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

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}

private class AppAssetClient(private val loader: WebViewAssetLoader) : WebViewClientCompat() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        return loader.shouldInterceptRequest(request.url)
    }
}

class FrameworkBridge(private val root: File) {
    init { root.mkdirs() }

    @JavascriptInterface
    fun frameworkPath(): String = root.absolutePath

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
}
