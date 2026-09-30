package com.naang360.missminutes

import android.app.Activity\nimport android.Manifest\nimport android.content.pm.PackageManager\nimport android.webkit.PermissionRequest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import kotlin.math.abs
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import org.json.JSONObject

class MainActivity : Activity() {
    companion object {
        init {
            try { System.loadLibrary("missminutes") } catch (_: UnsatisfiedLinkError) {}
        }
    }

    private lateinit var web: WebView
    private lateinit var framework: FrameworkBridge
    private val modelRequest = 7412
    private lateinit var sensorManager: SensorManager
    private var sensorBridge: SensorBridge? = null

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
            webChromeClient = object : WebChromeClient() {\n                override fun onPermissionRequest(request: PermissionRequest) {\n                    runOnUiThread {\n                        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {\n                            request.grant(request.resources)\n                        } else request.deny()\n                    }\n                }
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    android.util.Log.e("MissMinutes", "JS: ${message.message()} @${message.sourceId()}:${message.lineNumber()}")
                    return true
                }
            }
            addJavascriptInterface(framework, "MissMinutesNative")
            loadUrl("https://appassets.androidplatform.net/assets/web/index.html")
        }
        setContentView(web)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorBridge = SensorBridge(sensorManager, this).also { it.start() }
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
                "window.dispatchEvent(new CustomEvent('miss-model-event',{detail:{name:'$name',path:'$path',ready:true,status:'ready',progress:100,source:'Imported GGUF'}}))",
                null
            )
        }
    }

    fun dispatchModelEvent(script: String) {
        web.post { web.evaluateJavascript(script, null) }
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


private class SensorBridge(private val manager: SensorManager, private val activity: MainActivity) : SensorEventListener {
    private val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val light = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val proximity = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private var lastAx=0f; private var lastAy=0f; private var lastAz=0f
    fun start(){
        listOf(accel,gyro,light,proximity).filterNotNull().forEach { manager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME) }
    }
    fun stop(){ manager.unregisterListener(this) }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(e: SensorEvent){
        when(e.sensor.type){
            Sensor.TYPE_ACCELEROMETER -> {
                val dx=abs(e.values[0]-lastAx); val dy=abs(e.values[1]-lastAy); val dz=abs(e.values[2]-lastAz)
                lastAx=e.values[0]; lastAy=e.values[1]; lastAz=e.values[2]
                val shake=(dx+dy+dz).coerceIn(0f,12f)/12f
                val tilt=(sqrt(e.values[0]*e.values[0]+e.values[1]*e.values[1])/9.81f).coerceIn(0f,1f)
                emit("shake",shake,"tilt",tilt)
            }
            Sensor.TYPE_GYROSCOPE -> {
                val g=sqrt(e.values[0]*e.values[0]+e.values[1]*e.values[1]+e.values[2]*e.values[2]).coerceIn(0f,8f)/8f
                emit("gyro",g)
            }
            Sensor.TYPE_LIGHT -> emit("light",e.values[0].coerceIn(0f,1000f)/1000f)
            Sensor.TYPE_PROXIMITY -> emit("proximity",if(e.values[0] < (proximity?.maximumRange ?: 5f)*0.5f) 1f else 0f)
        }
    }
    private fun emit(vararg pairs: Any){
        val json=JSONObject()
        var i=0
        while(i+1<pairs.size){json.put(pairs[i].toString(),pairs[i+1]);i+=2}
        activity.dispatchModelEvent("window.dispatchEvent(new CustomEvent('miss-sensor-event',{detail:$json}))")
    }
}

class FrameworkBridge(private val root: File, private val activity: MainActivity) {
    companion object {
        private const val BASE_MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/df5bf01389a39c743ab467d734bf501681e041c5/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true"
        private const val BASE_MODEL_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        private const val BASE_MODEL_SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
        private const val BASE_MODEL_MIN_BYTES = 450L * 1024L * 1024L
    }
    @Volatile private var downloading = false
    init {
        root.mkdirs()
        File(root, "models").mkdirs()
    }

    @JavascriptInterface
    fun startSensors(): Boolean = true

    @JavascriptInterface
    fun frameworkPath(): String = root.absolutePath

    @JavascriptInterface
    fun pickModel() {
        activity.openModelPicker()
    }

    @JavascriptInterface
    fun modelStatus(): String {
        val activeName = File(root, "active-model.txt").takeIf { it.exists() }?.readText()?.trim().orEmpty()
        val active = if (activeName.isNotBlank()) File(root, "models/" + activeName) else null
        val base = File(root, "models/" + BASE_MODEL_NAME)
        val file = active?.takeIf { it.exists() } ?: base.takeIf { it.exists() }
        val ready = file != null && file.length() > 1024L * 1024L
        return JSONObject().apply {
            put("name", if (ready) file!!.name else "No base brain installed")
            put("path", if (ready) file!!.absolutePath else "")
            put("ready", ready)
            put("status", if (downloading) "downloading" else if (ready) "ready" else "none")
            put("progress", if (ready) 100 else 0)
            put("source", if (ready && file!!.name == BASE_MODEL_NAME) "Qwen2.5-0.5B-Instruct Q4_K_M" else "Imported GGUF")
        }.toString()
    }

    @JavascriptInterface
    fun installBaseModel(): Boolean {
        if (downloading) return false
        val target = File(root, "models/" + BASE_MODEL_NAME)
        if (target.exists() && target.length() > BASE_MODEL_MIN_BYTES) {
            File(root, "active-model.txt").writeText(BASE_MODEL_NAME)
            emitModel("ready", 100, target)
            return true
        }
        downloading = true
        emitModel("downloading", 0, null)
        Thread {
            val part = File(root, "models/" + BASE_MODEL_NAME + ".part")
            try {
                if (part.exists()) part.delete()
                val connection = (URL(BASE_MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }
                connection.connect()
                if (connection.responseCode !in 200..299) error("Model download HTTP " + connection.responseCode)
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(1024 * 1024)
                        var readTotal = 0L
                        var lastPercent = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            readTotal += n
                            val pct = if (total > 0) ((readTotal * 100L) / total).toInt().coerceIn(0, 100) else -1
                            if (pct >= 0 && pct != lastPercent) {
                                lastPercent = pct
                                emitModel("downloading", pct, null)
                            }
                        }
                    }
                }
                connection.disconnect()
                if (part.length() < BASE_MODEL_MIN_BYTES) error("Downloaded model is unexpectedly small.")
                val digest=MessageDigest.getInstance("SHA-256")
                part.inputStream().use { input ->
                    val buf=ByteArray(1024*1024)
                    while(true){
                        val n=input.read(buf)
                        if(n<=0) break
                        digest.update(buf,0,n)
                    }
                }
                val actualHash=digest.digest().joinToString("") { "%02x".format(it) }
                if(actualHash!=BASE_MODEL_SHA256) error("Base brain checksum mismatch.")
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                downloading = false
                File(root, "active-model.txt").writeText(BASE_MODEL_NAME)
                emitModel("ready", 100, target)
            } catch (t: Throwable) {
                downloading = false
                if (part.exists()) part.delete()
                emitModel("error", 0, null, t.message ?: "Base brain download failed.")
            }
        }.start()
        return true
    }

    private fun emitModel(status: String, progress: Int, file: File?, error: String = "") {
        val detail = JSONObject().apply {
            put("name", file?.name ?: "No base brain installed")
            put("path", file?.absolutePath ?: "")
            put("ready", status == "ready")
            put("status", status)
            put("progress", progress)
            put("error", error)
            put("source", "Qwen2.5-0.5B-Instruct Q4_K_M")
        }.toString()
        val script = "window.dispatchEvent(new CustomEvent(\"miss-model-event\",{detail:" + detail + "}))"
        activity.dispatchModelEvent(script)
    }

    @JavascriptInterface
    fun nativeRuntimeStatus(): String =
        if (NativeBrain.available()) "LOCAL_LLM_READY" else "MODEL_STORAGE_ONLY"

    @JavascriptInterface
    fun generateLocal(modelPath: String, prompt: String): String =
        NativeBrain.generate(modelPath, prompt)

    @JavascriptInterface
    fun startGeneration(modelPath: String, prompt: String): String =
        NativeBrain.start(modelPath, prompt)

    @JavascriptInterface
    fun pollGeneration(jobId: String): String =
        NativeBrain.poll(jobId)

    @JavascriptInterface
    fun cancelGeneration(jobId: String) {
        NativeBrain.cancel(jobId)
    }

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
            File(root, "active-model.txt").writeText(out.name)
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
