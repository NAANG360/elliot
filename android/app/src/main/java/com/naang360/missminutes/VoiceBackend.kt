package com.naang360.missminutes

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Speech boundary.
 *
 * The character brain is independent of the speaker. A future voice backend can
 * be installed here when its training data/model and use are properly authorized.
 */
interface SpeechBackend{
 fun speak(text:String)
 fun stop()
 fun shutdown()
}

class AndroidTtsBackend(context:Context):SpeechBackend,TextToSpeech.OnInitListener{
 private var tts:TextToSpeech?=TextToSpeech(context.applicationContext,this)
 private var ready=false
 override fun onInit(status:Int){
  ready=status==TextToSpeech.SUCCESS
  if(ready)tts?.apply{language=Locale.US;setSpeechRate(1.02f);setPitch(1.08f)}
 }
 override fun speak(text:String){
  if(ready)tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"miss-minutes-"+System.currentTimeMillis())
 }
 override fun stop(){tts?.stop()}
 override fun shutdown(){tts?.shutdown();tts=null}
}

/**
 * One stable application-facing voice facade.
 *
 * This deliberately does not contain an actor-specific clone. If an explicitly
 * authorized voice model is ever supplied, implement SpeechBackend separately
 * and inject it here; the brain, memory and renderer do not need to change.
 */
class VoiceBackend(context:Context){
 private var backend:SpeechBackend=AndroidTtsBackend(context)
 fun replaceBackend(next:SpeechBackend){backend.shutdown();backend=next}
 fun speak(text:String)=backend.speak(text)
 fun stop()=backend.stop()
 fun shutdown()=backend.shutdown()
}