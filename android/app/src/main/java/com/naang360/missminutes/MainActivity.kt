package com.naang360.missminutes

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.content.ComponentName
import android.view.Gravity
import android.view.View
import android.widget.*
import android.app.AlertDialog
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : Activity() {
 private lateinit var character:NativeMissMinutesView
 private lateinit var chat:LinearLayout
 private lateinit var input:EditText
 private lateinit var scroll:ScrollView
 private lateinit var brain:FrameworkBridge
 private lateinit var memory:NativeChronalMemory
 private lateinit var voice:VoiceBackend
 private val handler=Handler(Looper.getMainLooper())
 private var generationToken=0L
 private var voiceEnabled=true

 override fun onCreate(state:Bundle?){super.onCreate(state);brain=FrameworkBridge(filesDir.resolve("framework"),this);memory=NativeChronalMemory(this);voice=VoiceBackend(this);buildUi();requestPermissionsIfNeeded()}
 private fun buildUi(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(12,8,4));setPadding(18,18,18,12)}
  root.addView(TextView(this).apply{text="TIME VARIANCE AUTHORITY
MISS MINUTES";setTextColor(Color.rgb(255,190,105));textSize=20f;gravity=Gravity.CENTER;setPadding(0,4,0,10)},LinearLayout.LayoutParams(-1,-2))
  character=NativeMissMinutesView(this);root.addView(character,LinearLayout.LayoutParams(-1,0,1f))
  scroll=ScrollView(this).apply{setBackgroundColor(Color.rgb(22,14,9));setPadding(12,8,12,8)};chat=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(chat);root.addView(scroll,LinearLayout.LayoutParams(-1,0,.72f))
  val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};input=EditText(this).apply{hint="Speak, sugar...";setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);setSingleLine(false);setMaxLines(3)};row.addView(input,LinearLayout.LayoutParams(0,-2,1f));row.addView(Button(this).apply{text="↗";setOnClickListener{send()}},LinearLayout.LayoutParams(64,-2));root.addView(row)
  val tools=LinearLayout(this).apply{gravity=Gravity.CENTER}
  listOf("Memory","Brain","Wind Up").forEach{label->tools.addView(Button(this).apply{text=label;setOnClickListener{when(label){"Memory"->showMemory();"Brain"->installBrain();"Wind Up"->windUp()}}})}
  tools.addView(Button(this).apply{text="VOICE";setOnClickListener{showVoicePicker()}})
  tools.addView(Button(this).apply{text="VOICE LAB";setOnClickListener{showVoiceLab()}})
  root.addView(tools);setContentView(root)
  addMessage("MISS MINUTES","Well hello there, sugar! The native chronal core is awake. No WebView required.")
 }
 private fun addMessage(who:String,message:String){chat.addView(TextView(this).apply{text=who+"
"+message;setTextColor(if(who=="MISS MINUTES")Color.rgb(255,220,175)else Color.WHITE);textSize=15f;setPadding(4,8,4,12)});scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}}
 private fun send(){
  val text=input.text.toString().trim();if(text.isBlank())return
  input.setText("")
  generationToken++
  voice.stop()
  character.speaking=true
  addMessage("YOU",text)
  memory.remember(text,"conversation",.35)
  character.emotion="curious"
  val token=generationToken
  val q=text.lowercase()
  when{q.startsWith("remember ")->{memory.remember(text.substringAfter("remember ").trim(),"explicit",.98);reply("I've tucked that away in chronal memory, sugar.")};q.contains("what do you remember")->reply(memory.recall("",12).joinToString("
• ",prefix="Here's what survived the timeline:
• "));q.startsWith("search ")->search(text.substringAfter("search ").trim());q.startsWith("open http://")||q.startsWith("open https://")->{brain.openUrl(text.substringAfter(" ").trim());reply("Opened it. The external browser stays outside my process.")};q.contains("accessibility")->openAccessibility();q.contains("time")->reply("The local time is "+java.text.SimpleDateFormat("h:mm:ss a").format(java.util.Date())+".");else->generate(text,token)}
 }
 private fun generate(text:String, token:Long){val path=brain.modelPath();if(path.isBlank()){reply("My local brain slot is empty, sugar. Tap Brain to install the starter brain first.");return};thread{val prompt="You are Miss Minutes, a cheerful theatrical retro TVA clock companion. Be concise, warm, mischievous and precise. Never claim an action happened unless it did. USER: "+text;val result=NativeBrain.generate(path,prompt);handler.post{if(token==generationToken)reply(result)}}}
 private fun cleanModelReply(raw:String):String{
  var text=raw.trim()
  try{
   val start=raw.indexOf('{');val end=raw.lastIndexOf('}')
   if(start>=0&&end>start){val candidate=raw.substring(start,end+1);text=JSONObject(candidate).optString("text",text)}
  }catch(_:Throwable){}
  if(text.contains("Assistant:"))text=text.substringAfterLast("Assistant:").trim()
  text=text.replace(Regex("(?im)^User:\\s*.*$"),"").replace(Regex("(?im)^Assistant:\\s*"),"")
  val seen=HashSet<String>()
  text=text.lines().map{it.trimEnd()}.filter{it.isNotBlank()&&seen.add(it.trim().lowercase())}.joinToString("
").trim()
  if(text.isBlank())text="I'm sorry, sugar. My chronal response came back empty."
  return text.take(1200)
 }
 private fun reply(rawText:String){handler.post{val text=cleanModelReply(rawText);character.speaking=voiceEnabled;addMessage("MISS MINUTES",text);if(voiceEnabled)voice.speak(text.take(700));handler.postDelayed({character.speaking=false;character.emotion="warm"},maxOf(900L,text.length*35L))}}
 private fun showVoicePicker(){
  val engines=voice.engineInfos()
  val custom=voice.customVoices()
  val items=ArrayList<String>()
  items.add("ANDROID TTS")
  engines.forEach{items.add("  "+it.label.toString()+if(it.name==voice.enginePackage()&&voice.currentEngineLabel().startsWith("ANDROID"))"  ✓" else "")}
  items.add("CUSTOM NEURAL")
  custom.forEach{id->items.add("  "+id+if(voice.currentEngineLabel()=="CUSTOM NEURAL: "+id)"  ✓" else "")}
  items.add("IMPORT CUSTOM VOICE")
  val builder=AlertDialog.Builder(this).setTitle("Miss Minutes Speech")
  builder.setItems(items.toTypedArray()){_,which->
   when{
    which==0->Unit
    which in 1..engines.size->{
     voice.selectAndroidEngine(engines[which-1].name)
     reply("Speech engine set to "+engines[which-1].label+".")
    }
    which==engines.size+1->Unit
    which in (engines.size+2)..(engines.size+1+custom.size)->{
     val id=custom[which-(engines.size+2)]
     if(voice.selectCustomVoice(id))reply("Custom neural voice loaded: "+id+".")
    }
    else->showVoiceLab()
   }
  }.setNegativeButton("Android TTS settings"){_,_->startActivity(Intent(Settings.ACTION_TTS_SETTINGS))}
   .setPositiveButton(if(voiceEnabled)"VOICE ON" else "VOICE OFF"){_,_->voiceEnabled=!voiceEnabled;if(!voiceEnabled){voice.stop();character.speaking=false}}
   .show()
 }
 private fun showVoiceLab(){
  val custom=voice.customVoices()
  val labels=arrayOf(
   "IMPORT PIPER VOICE FOLDER",
   "VOICE ENGINE: "+voice.currentEngineLabel(),
   "INSTALLED CUSTOM VOICES: "+custom.size,
   "TRAINING PIPELINE / YOUTUBE SOURCES"
  )
  AlertDialog.Builder(this).setTitle("MISS MINUTES // VOICE LAB")
   .setItems(labels){_,which->
    when(which){
     0->startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),9102)
     1->showVoicePicker()
     2->showVoicePicker()
     3->{
      reply("Voice Lab expects an authorized recording dataset. You can feed it your own recordings or source videos you are licensed to use; the training pipeline prepares WAV plus metadata, trains a neural voice, then exports the Android-ready voice bundle.")
      startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/NAANG360/miss-minutes/tree/main/voice_lab")))
     }
    }
   }.setNegativeButton("Close",null).show()
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode!=9102||resultCode!=RESULT_OK||data?.data==null)return
  val uri=data.data!!
  try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Throwable){}
  thread{
   val result=voice.importCustomVoice(uri)
   handler.post{
    if(result.startsWith("Import failed")||result.startsWith("Could not")||result.startsWith("Voice folder"))reply(result)
    else reply("Custom voice imported and ready: "+result+". Open VOICE to select it.")
   }
  }
 }
 private fun openAccessibility(){
  val component=ComponentName(this,MissMinutesAccessibilityService::class.java).flattenToString()
  val enabled=Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.split(":")?.any{it.equals(component,true)}==true
  if(enabled) reply("Android Accessibility is connected, sugar. My native accessibility service is enabled.")
  else {reply("Let's connect it properly. I'm opening Android Accessibility settings; enable Miss Minutes there, then come back.");startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
 }
 private fun search(query:String){thread{val r=brain.search(query);handler.post{reply(if(r.isBlank())"The timeline returned no useful results." else "I found:
"+r)}}}
 private fun showMemory(){reply("Chronal memory currently contains "+memory.count()+" durable traces. It is stored natively on-device rather than in browser storage.")}
 private fun installBrain(){if(brain.modelPath().isNotBlank()){reply("My local brain is already installed. I won't silently replace it with another model.");return};reply("I'm fetching the starter brain once, then all future adaptation stays attached to that installed brain.");thread{brain.installBaseModel{m->handler.post{addMessage("BRAIN",m)}}}}
 private fun windUp(){reply("Wind Up is now a native checkpoint boundary; the next native milestone will make framework rollback independent of browser state.")}
 private fun requestPermissionsIfNeeded(){val missing=mutableListOf<String>();if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)missing+=Manifest.permission.CAMERA;if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)missing+=Manifest.permission.RECORD_AUDIO;if(missing.isNotEmpty())requestPermissions(missing.toTypedArray(),9001)}
 override fun onDestroy(){voice.shutdown();brain.shutdown();super.onDestroy()}
}

class FrameworkBridge(private val root:File,private val activity:MainActivity){
 companion object{private const val URL_BASE="https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/df5bf01389a39c743ab467d734bf501681e041c5/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true";private const val NAME="qwen2.5-0.5b-instruct-q4_k_m.gguf";private const val HASH="74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"}
 init{root.mkdirs();File(root,"models").mkdirs()}
 fun modelPath():String{val name=File(root,"active-model.txt").takeIf{it.exists()}?.readText()?.trim()?:NAME;val f=File(root,"models/"+name);return if(f.exists()&&f.length()>1024*1024)f.absolutePath else ""}
 fun installBaseModel(report:(String)->Unit){val target=File(root,"models/"+NAME);if(target.exists()&&target.length()>450L*1024*1024){File(root,"active-model.txt").writeText(NAME);report("Starter brain already installed.");return};val part=File(root,"models/"+NAME+".part");try{var offset=if(part.exists())part.length()else 0L;val c=(URL(URL_BASE).openConnection()as HttpURLConnection).apply{connectTimeout=30000;readTimeout=120000;instanceFollowRedirects=true;if(offset>0)setRequestProperty("Range","bytes="+offset+"-")};c.connect();if(c.responseCode !in 200..299)error("HTTP "+c.responseCode);if(offset>0&&c.responseCode==200){part.delete();offset=0};c.inputStream.use{input->FileOutputStream(part,offset>0).use{out->val buf=ByteArray(1024*1024);var total=offset;while(true){val n=input.read(buf);if(n<=0)break;out.write(buf,0,n);total+=n;if(total%(20L*1024*1024)<1024*1024)report("Downloaded "+total/1024/1024+" MB")}}};c.disconnect();if(part.length()<450L*1024*1024)error("Download ended early");val md=MessageDigest.getInstance("SHA-256");part.inputStream().use{input->val buf=ByteArray(1024*1024);while(true){val n=input.read(buf);if(n<=0)break;md.update(buf,0,n)}};val actual=md.digest().joinToString(""){String.format("%02x",it)};if(actual!=HASH)error("Checksum mismatch");if(target.exists())target.delete();if(!part.renameTo(target)){part.copyTo(target,true);part.delete()};File(root,"active-model.txt").writeText(NAME);report("Starter brain ready.")}catch(t:Throwable){report("Brain install failed: "+t.message)}}
 fun openUrl(url:String){try{val u=Uri.parse(url);if(u.scheme!="http"&&u.scheme!="https")return;activity.startActivity(Intent(Intent.ACTION_VIEW,u))}catch(_:Throwable){}}
 fun search(query:String):String{try{val enc=URLEncoder.encode(query.take(200),"UTF-8");val c=(URL("https://html.duckduckgo.com/html/?q="+enc).openConnection()as HttpURLConnection).apply{connectTimeout=8000;readTimeout=10000;setRequestProperty("User-Agent","MissMinutes/0.8.2")};val html=c.inputStream.bufferedReader().use{it.readText()};c.disconnect();val p=Regex("""<a[^>]*class=["'][^"']*result__a[^"']*["'][^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""",RegexOption.IGNORE_CASE);return p.findAll(html).take(5).joinToString("

"){m->android.text.Html.fromHtml(m.groupValues[2],android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()+"
"+m.groupValues[1]}}catch(t:Throwable){return "Search failed: "+t.message}}
 fun shutdown(){NativeBrain.releaseModel()}
}