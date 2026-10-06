package com.naang360.missminutes

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Swappable speech boundary. No actor-specific voice is embedded here. */
class VoiceBackend(context:Context):TextToSpeech.OnInitListener{
 private var tts:TextToSpeech?=TextToSpeech(context.applicationContext,this);private var ready=false
 override fun onInit(status:Int){ready=status==TextToSpeech.SUCCESS;if(ready)tts?.language=Locale.US}
 fun speak(text:String){if(ready)tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"miss-minutes-"+System.currentTimeMillis())}
 fun stop(){tts?.stop()}
 fun shutdown(){tts?.shutdown();tts=null}
}