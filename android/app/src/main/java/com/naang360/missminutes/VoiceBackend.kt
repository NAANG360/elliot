package com.naang360.missminutes

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.documentfile.provider.DocumentFile
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.GenerationConfig
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

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

class PiperVoiceBackend(private val root:File):SpeechBackend{
 private var worker:Thread?=null
 @Volatile private var stopped=false
 @Volatile private var track:AudioTrack?=null

 override fun speak(text:String){
  stop()
  stopped=false
  worker=thread(name="miss-minutes-piper"){
   var tts:OfflineTts?=null
   try{
    val model=firstFile(root,"*.onnx")
    val tokens=File(root,"tokens.txt")
    val espeak=File(root,"espeak-ng-data")
    if(model==null||!tokens.isFile||!espeak.isDirectory) return@thread
    val config=OfflineTtsConfig(
     model=OfflineTtsModelConfig(
      vits=OfflineTtsVitsModelConfig(
       model=model.absolutePath,
       tokens=tokens.absolutePath,
       dataDir=espeak.absolutePath,
       noiseScale=.667f,
       noiseScaleW=.8f,
       lengthScale=1.0f
      ),
      numThreads=2,
      debug=false,
      provider="cpu"
     ),
     maxNumSentences=1,
     silenceScale=.2f
    )
    // Recreate the native TTS object for each request. This avoids state
    // reuse problems seen by some sherpa-onnx/Piper VITS builds.
    tts=OfflineTts(config=config)
    val audio=tts.generateWithConfig(text,GenerationConfig(silenceScale=.2f))
    if(stopped||audio.samples.isEmpty()||audio.sampleRate<=0)return@thread
    val shorts=ShortArray(audio.samples.size){i->(audio.samples[i].coerceIn(-1f,1f)*32767f).toInt().toShort()}
    val minBuffer=AudioTrack.getMinBufferSize(audio.sampleRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
    val a=AudioTrack.Builder()
     .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
     .setAudioFormat(AudioFormat.Builder().setSampleRate(audio.sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
     .setBufferSizeInBytes(maxOf(minBuffer,8192))
     .setTransferMode(AudioTrack.MODE_STREAM)
     .build()
    track=a
    a.play()
    var off=0
    while(off<shorts.size&&!stopped){
     val n=a.write(shorts,off,minOf(2048,shorts.size-off))
     if(n<=0)break
     off+=n
    }
    try{a.stop()}catch(_:Throwable){}
    a.release()
    track=null
   }catch(_:Throwable){
    try{track?.release()}catch(_:Throwable){}
    track=null
   }finally{try{tts?.release()}catch(_:Throwable){}}
  }
 }
 override fun stop(){
  stopped=true
  try{track?.pause();track?.flush();track?.release()}catch(_:Throwable){}
  track=null
  worker?.interrupt()
  worker=null
 }
 override fun shutdown(){stop()}
 private fun firstFile(dir:File,pattern:String):File?=dir.listFiles()?.firstOrNull{it.isFile&&it.name.endsWith(".onnx")&&!it.name.endsWith(".onnx.json")}
}

class VoiceBackend(private val context:Context){
 private val prefs=context.getSharedPreferences("miss_minutes_voice",Context.MODE_PRIVATE)
 private val voiceRoot=File(context.filesDir,"voices").apply{mkdirs()}
 private var enginePackage:String?=prefs.getString("engine_package",TextToSpeech.getDefaultEngine(context))
 private var android=AndroidTtsBackend(context,enginePackage)
 private var backend:SpeechBackend=android
 private var selectedCustom:String?=prefs.getString("custom_voice_id",null)

 init{
  selectedCustom?.let{id->customVoiceDir(id)?.takeIf{it.isDirectory}?.let{selectCustomVoice(it.name)}}
 }

 fun replaceBackend(next:SpeechBackend){backend.shutdown();backend=next}
 fun speak(text:String)=backend.speak(text)
 fun stop()=backend.stop()
 fun shutdown(){backend.shutdown()}
 fun availableVoices():List<Voice> = android.availableVoices()
 fun currentVoiceName():String = android.currentVoiceName()
 fun selectVoice(name:String):Boolean = android.setVoice(name)
 fun enginePackage():String?=enginePackage
 fun engineInfos():List<TextToSpeech.EngineInfo>{
  return try{
   val probe=TextToSpeech(context.applicationContext,null,enginePackage)
   val list=probe.engines
   probe.shutdown()
   list
  }catch(_:Throwable){emptyList()}
 }
 fun customVoices():List<String> = voiceRoot.listFiles()?.filter{it.isDirectory&&File(it,"tokens.txt").isFile&&File(it,"espeak-ng-data").isDirectory&&it.listFiles()?.any{f->f.name.endsWith(".onnx")&&!f.name.endsWith(".onnx.json")}==true}?.map{it.name}?.sorted() ?: emptyList()
 fun currentEngineLabel():String = selectedCustom?.let{"CUSTOM NEURAL: $it"} ?: "ANDROID TTS: ${enginePackage ?: "default"}"
 fun selectCustomVoice(id:String):Boolean{
  val dir=customVoiceDir(id) ?: return false
  if(!dir.isDirectory)return false
  backend.shutdown()
  backend=PiperVoiceBackend(dir)
  selectedCustom=id
  prefs.edit().putString("custom_voice_id",id).apply()
  return true
 }
 fun selectAndroidEngine(packageName:String):Boolean{
  val next=AndroidTtsBackend(context,packageName)
  backend.shutdown()
  enginePackage=packageName
  android=next
  backend=android
  selectedCustom=null
  prefs.edit().putString("engine_package",packageName).remove("custom_voice_id").apply()
  return true
 }
 fun importCustomVoice(treeUri:Uri):String{
  val tree=DocumentFile.fromTreeUri(context,treeUri) ?: return "Could not open that voice folder."
  val onnx=tree.listFiles().firstOrNull{it.isFile&&it.name?.endsWith(".onnx")==true&&!it.name!!.endsWith(".onnx.json")}
  val tokens=tree.findFile("tokens.txt")
  val espeak=tree.findFile("espeak-ng-data")
  if(onnx==null||tokens==null||espeak==null||!espeak.isDirectory)return "Voice folder needs *.onnx, tokens.txt and espeak-ng-data/."
  val rawName=tree.name?.ifBlank{onnx.name?.substringBeforeLast(".") ?: "voice"} ?: "voice"
  val id=rawName.replace(Regex("[^A-Za-z0-9._-]"),"_").take(64)+"-"+UUID.randomUUID().toString().take(8)
  val target=File(voiceRoot,id)
  val staging=File(voiceRoot,".$id.part")
  try{
   staging.mkdirs()
   copyDocument(onnx,File(staging,onnx.name ?: "model.onnx"))
   val config=tree.listFiles().firstOrNull{it.isFile&&it.name?.endsWith(".onnx.json")==true}
   if(config!=null)copyDocument(config,File(staging,config.name ?: "model.onnx.json"))
   copyDocument(tokens,File(staging,"tokens.txt"))
   copyTree(espeak,File(staging,"espeak-ng-data"))
   if(!staging.renameTo(target)){staging.copyRecursively(target,true);staging.deleteRecursively()}
   return id
  }catch(t:Throwable){
   staging.deleteRecursively()
   return "Import failed: ${t.message}"
  }
 }
 fun deleteCustomVoice(id:String){customVoiceDir(id)?.deleteRecursively();if(selectedCustom==id){selectedCustom=null;selectAndroidEngine(enginePackage ?: TextToSpeech.getDefaultEngine(context))}}
 private fun customVoiceDir(id:String):File?=File(voiceRoot,id).takeIf{it.exists()}
 private fun copyDocument(src:DocumentFile,dst:File){
  dst.parentFile?.mkdirs()
  context.contentResolver.openInputStream(src.uri).use{input->requireNotNull(input){"Cannot read "+src.name};FileOutputStream(dst).use{out->input.copyTo(out,1024*1024)}}
 }
 private fun copyTree(src:DocumentFile,dst:File){
  dst.mkdirs()
  src.listFiles().forEach{child->
   val name=child.name ?: return@forEach
   if(child.isDirectory)copyTree(child,File(dst,name)) else copyDocument(child,File(dst,name))
  }
 }
}
