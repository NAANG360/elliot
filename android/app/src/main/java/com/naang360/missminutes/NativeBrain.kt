package com.naang360.missminutes

object NativeBrain {
    @JvmStatic external fun nativeAvailable(): Boolean
    @JvmStatic external fun generateNative(modelPath: String, prompt: String): String
    @JvmStatic external fun startGenerationNative(modelPath: String, prompt: String): String
    @JvmStatic external fun pollGenerationNative(jobId: String): String
    @JvmStatic external fun cancelGenerationNative(jobId: String)

    fun available(): Boolean = try { nativeAvailable() } catch (_: Throwable) { false }
    fun generate(modelPath: String, prompt: String): String = try {
        generateNative(modelPath, prompt)
    } catch (e: Throwable) {
        "[LOCAL_BRAIN_ERROR] ${e.message ?: "native inference failed"}"
    }
    fun start(modelPath: String, prompt: String): String = startGenerationNative(modelPath, prompt)
    fun poll(jobId: String): String = pollGenerationNative(jobId)
    fun cancel(jobId: String) { try { cancelGenerationNative(jobId) } catch (_: Throwable) {} }
}
