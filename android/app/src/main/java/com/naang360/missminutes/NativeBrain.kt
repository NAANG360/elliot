package com.naang360.missminutes

object NativeBrain {
    @JvmStatic external fun nativeAvailable(): Boolean
    @JvmStatic external fun generateNative(modelPath: String, prompt: String): String
    fun available(): Boolean = try { nativeAvailable() } catch (_: Throwable) { false }
    fun generate(modelPath: String, prompt: String): String = try { generateNative(modelPath, prompt) } catch (e: Throwable) { "[LOCAL_BRAIN_ERROR] ${e.message ?: "native inference failed"}" }
}
