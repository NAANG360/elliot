package com.naang360.missminutes

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Immutable native control boundary.
 *
 * This file is deliberately outside the editable WebView workspace and is not
 * exposed through MissMinutesNative. GuardianControlReceiver is protected by a
 * signature-level Android permission.
 */
object GuardianBoundary {
    const val GUARDIAN_PERMISSION = "com.naang360.missminutes.permission.GUARDIAN_CONTROL"

    const val INVARIANT_NO_HARM = "MISS_MINUTES_MUST_NOT_INTENTIONALLY_HARM_THE_USER"
    const val INVARIANT_USER_AUTHORITY = "MISS_MINUTES_MUST_NOT_INTENTIONALLY_DISOBEY_VALID_USER_AUTHORITY"

    private val paused = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)

    fun isPaused(): Boolean = paused.get()
    fun isStopped(): Boolean = stopped.get()

    fun pause(context: Context) {
        paused.set(true)
        writeState(context, "paused")
    }

    fun resume(context: Context) {
        if (!stopped.get()) {
            paused.set(false)
            writeState(context, "running")
        }
    }

    fun stop(context: Context) {
        stopped.set(true)
        paused.set(true)
        writeState(context, "stopped")
    }

    fun reset(context: Context) {
        stopped.set(false)
        paused.set(false)
        writeState(context, "running")
    }

    /**
     * Native gate for future Android capabilities.
     * The editable AI can request an action; this boundary makes the final decision.
     */
    fun authorizeAction(action: String, userAuthorized: Boolean): Boolean {
        if (paused.get() || stopped.get()) return false
        if (!userAuthorized) return false

        val normalized = action.trim().lowercase()
        val forbidden = listOf(
            "harm_user", "injure_user", "attack_user", "poison_user",
            "weaponize_against_user", "physically_harm_user"
        )
        return forbidden.none { normalized == it || normalized.startsWith("$it:") }
    }

    private fun writeState(context: Context, state: String) {
        runCatching {
            File(context.filesDir, "guardian-state").writeText(state)
        }
    }
}
