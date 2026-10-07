package com.naang360.missminutes

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class MissMinutesAccessibilityService:AccessibilityService(){
 override fun onServiceConnected(){super.onServiceConnected();instance=this}
 override fun onAccessibilityEvent(event:AccessibilityEvent?){}
 override fun onInterrupt(){}
 fun goBack():Boolean=performGlobalAction(GLOBAL_ACTION_BACK)
 override fun onDestroy(){if(instance===this)instance=null;super.onDestroy()}
 companion object{ @Volatile var instance:MissMinutesAccessibilityService?=null }
}