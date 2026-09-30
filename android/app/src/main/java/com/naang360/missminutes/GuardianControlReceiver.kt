package com.naang360.missminutes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Guardian-only control surface.
 * It is intentionally not exposed through the WebView JavaScript bridge.
 */
class GuardianControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.naang360.missminutes.guardian.PAUSE" -> GuardianBoundary.pause(context)
            "com.naang360.missminutes.guardian.RESUME" -> GuardianBoundary.resume(context)
            "com.naang360.missminutes.guardian.STOP" -> GuardianBoundary.stop(context)
            "com.naang360.missminutes.guardian.RESET" -> GuardianBoundary.reset(context)
        }
    }
}
