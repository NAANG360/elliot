package com.naang360.missminutes

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

interface SpeechBackend{
 fun speak(text:String)
 fun stop()
 fun shutdown()
}

class AndroidTtsBackend(private val context:Context,private val enginePackage:String?):SpeechBackend,TextToSpeech.OnInitListener{
 private val prefs=context.getSharedPreferences("miss_minutes_voice",Context.MODE_PRIVATE)
 private var tts:TextToSpeech?=TextToSpeech(context.applicationContext,this,enginePackage)
 private var ready=false
 override fun onInit(status:Int){
  ready=status==TextToSpeech.SUCCESS
  if(ready)tts?.apply{
   language=Locale.US
   setSpeechRate(1.02f)
   setPitch(1.08f)
   prefs.getString("voice_name_${enginePackage ?: "default"}",null)?.let{name->voices.firstOrNull{it.name==name}?.let{setVoice(it)}}
  }
 }
 fun availableVoices():List<Voice> = tts?.voices?.filter{it.locale.language=="en" && !it.isNetworkConnectionRequired}?.sortedBy{it.name} ?: emptyList()
 fun currentVoiceName():String = tts?.voice?.name ?: "Android default"
 fun setVoice(name:String):Boolean{
  val v=tts?.voices?.firstOrNull{it.name==name} ?: return false
  val ok=tts?.setVoice(v)==TextToSpeech.SUCCESS
  if(ok)prefs.edit().putString("voice_name_${enginePackage ?: "default"}",name).apply()
  return ok
 }
 override fun speak(text:String){if(ready)tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"miss-minutes-"+System.currentTimeMillis())}
 override fun stop(){tts?.stop()}
 override fun shutdown(){tts?.shutdown();tts=null}
}

class VoiceBackend(private val context:Context){
 private val prefs=context.getSharedPreferences("miss_minutes_voice",Context.MODE_PRIVATE)
 private var enginePackage:String?=prefs.getString("engine_package",TextToSpeech.getDefaultEngine(context))
 private var android=AndroidTtsBackend(context,enginePackage)
 private var backend:SpeechBackend=android

 fun replaceBackend(next:SpeechBackend){backend.shutdown();backend=next}
 fun speak(text:String)=backend.speak(text)
 fun stop()=backend.stop()
 fun shutdown(){backend.shutdown()}
 fun availableVoices():List<Voice> = android.availableVoices()
 fun currentVoiceName():String = android.currentVoiceName()
 fun selectVoice(name:String):Boolean = android.setVoice(name)
 fun enginePackage():String?=enginePackage
 fun engineInfos():List<TextToSpeech.EngineInfo>{
  return androidEngineList()
 }
 private fun androidEngineList():List<TextToSpeech.EngineInfo>{
  return android.availableEngineInfos()
 }
 fun selectEngine(packageName:String):Boolean{
  if(packageName==enginePackage)return true
  val next=AndroidTtsBackend(context,packageName)
  enginePackage=packageName
  prefs.edit().putString("engine_package",packageName).apply()
  backend.shutdown()
  android=next
  backend=android
  return true
 }
 private fun AndroidTtsBackend.availableEngineInfos():List<TextToSpeech.EngineInfo>{
  return try{
   val probe=TextToSpeech(context.applicationContext,null,enginePackage)
   val list=probe.engines
   probe.shutdown()
   list
  }catch(_:Throwable){emptyList()}
 }
}