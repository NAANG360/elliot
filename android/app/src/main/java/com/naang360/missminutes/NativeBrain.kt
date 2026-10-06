package com.naang360.missminutes

object NativeBrain {
    private var loadError: Throwable? = null

    init {
        try {
            System.loadLibrary("missminutes")
        } catch (t: Throwable) {
            loadError = t
        }
    }

    @JvmStatic external fun nativeAvailable(): Boolean
    @JvmStatic external fun generateNative(modelPath: String, prompt: String): String
    @JvmStatic external fun startGenerationNative(modelPath: String, prompt: String): String
    @JvmStatic external fun pollGenerationNative(jobId: String): String
    @JvmStatic external fun cancelGenerationNative(jobId: String)
    @JvmStatic external fun releaseModelNative()

    fun available(): Boolean = try {
        loadError == null && nativeAvailable()
    } catch (_: Throwable) {
        false
    }

    fun generate(modelPath: String, prompt: String): String = try {
        if (loadError != null) {
            "[LOCAL_BRAIN_ERROR] Native inference library could not be loaded: ${loadError?.message ?: "unknown linker error"}"
        } else {
            generateNative(modelPath, prompt)
        }
    } catch (e: Throwable) {
        "[LOCAL_BRAIN_ERROR] ${e.message ?: "native inference failed"}"
    }

    fun start(modelPath: String, prompt: String): String = startGenerationNative(modelPath, prompt)
    fun poll(jobId: String): String = pollGenerationNative(jobId)
    fun cancel(jobId: String) { try { cancelGenerationNative(jobId) } catch (_: Throwable) {} }
    fun releaseModel() { try { if (loadError == null) releaseModelNative() } catch (_: Throwable) {} }
}
