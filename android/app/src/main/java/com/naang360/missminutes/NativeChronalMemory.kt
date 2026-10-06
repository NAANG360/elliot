package com.naang360.missminutes

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Native long-horizon memory.
 *
 * The model never has to carry the whole archive. Events live on disk and are
 * retrieved by relevance, importance, recency and memory type.
 */
class NativeChronalMemory(context: Context) {
 private val appContext=context.applicationContext
 private val dir=File(appContext.filesDir,"chronal-memory").apply{mkdirs()}
 private val events=File(dir,"events.jsonl")
 private val ledger=File(dir,"mutation-ledger.jsonl")
 private val lock=Any()

 private fun append(file:File,obj:JSONObject){file.appendText(obj.toString()+"\n")}
 private fun read(file:File):List<JSONObject>=if(!file.exists())emptyList()else synchronized(lock){
  file.useLines{lines->lines.mapNotNull{runCatching{JSONObject(it)}.getOrNull()}.toList()}
 }

 fun remember(text:String,source:String="conversation",importance:Double=.7,type:String="fact",
              entities:List<String> = emptyList(),relations:List<String> = emptyList(),
              confidence:Double=1.0){
  val clean=text.trim();if(clean.isBlank())return
  synchronized(lock){
   val now=System.currentTimeMillis()
   val o=JSONObject().apply{
    put("id","mem_"+UUID.randomUUID().toString())
    put("created",now);put("lastReferenced",now)
    put("content",clean.take(8000));put("source",source)
    put("type",type);put("importance",importance.coerceIn(0.0,1.0))
    put("confidence",confidence.coerceIn(0.0,1.0))
    put("entities",entities.take(32).joinToString("|"))
    put("relations",relations.take(32).joinToString("|"))
   }
   append(events,o)
   append(ledger,JSONObject().apply{
    put("id","evt_"+UUID.randomUUID().toString());put("at",now)
    put("operation","remember");put("memoryId",o.getString("id"));put("source",source)
   })
  }
 }

 fun recall(query:String,limit:Int=16):List<String>=synchronized(lock){
  val terms=query.lowercase().split(Regex("[^a-z0-9_-]+")).filter{it.length>=3}.toSet()
  val now=System.currentTimeMillis()
  read(events).mapNotNull{o->
   val text=o.optString("content");if(text.isBlank())return@mapNotNull null
   val hay=(text+" "+o.optString("entities")+" "+o.optString("relations")).lowercase()
   val overlap=terms.count{hay.contains(it)}
   if(overlap==0 && terms.isNotEmpty())return@mapNotNull null
   val ageDays=max(0.0,(now-o.optLong("created",now))/86400000.0)
   val recency=1.0/(1.0+ageDays/45.0)
   val score=overlap*4.0+o.optDouble("importance",.5)*3.0+o.optDouble("confidence",.5)*2.0+recency
   score to text
  }.sortedByDescending{it.first}.take(limit).map{it.second}
 }

 fun count():Int=synchronized(lock){read(events).size}
 fun mutationCount():Int=synchronized(lock){read(ledger).size}
 fun stats():Map<String,Int>{
  val all=read(events);val types=all.groupingBy{it.optString("type","fact")}.eachCount()
  return mapOf("memories" to all.size,"mutations" to mutationCount(),"facts" to (types["fact"]?:0),
   "episodes" to (types["episode"]?:0),"decisions" to (types["decision"]?:0),
   "preferences" to (types["preference"]?:0),"project" to (types["project"]?:0))
 }
}
