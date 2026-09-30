package com.naang360.missminutes

import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import java.time.Instant

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var framework: FrameworkBridge
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        framework = FrameworkBridge(filesDir.resolve("framework"))
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(framework, "MissMinutesNative")
            loadUrl("file:///android_asset/web/index.html")
        }
        setContentView(web)
    }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
class FrameworkBridge(private val root: File) {
    init { root.mkdirs() }
    @JavascriptInterface fun frameworkPath(): String = root.absolutePath
    @JavascriptInterface fun checkpoint(json: String, reason: String): String {
        val file = File(root, "checkpoint-${System.currentTimeMillis()}.json")
        file.writeText(json)
        File(root, "last-good.txt").writeText(file.name)
        return file.name
    }
    @JavascriptInterface fun windUp(): String {
        val name = File(root, "last-good.txt").takeIf { it.exists() }?.readText()?.trim()
        return if (name.isNullOrBlank()) "" else File(root, name).takeIf { it.exists() }?.readText().orEmpty()
    }
    @JavascriptInterface fun recordEvolution(summary: String): Boolean {
        File(root, "evolution.log").appendText("${Instant.now()} | $summary\n")
        return true
    }
}
