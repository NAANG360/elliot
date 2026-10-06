package com.naang360.missminutes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class NativeChronalMemory(context:Context){
 private val prefs=context.getSharedPreferences("chronal_memory_v2",Context.MODE_PRIVATE)
 private val lock=Any()
 private fun load()=try{JSONArray(prefs.getString("events","[]"))}catch(_:Throwable){JSONArray()}
 private fun save(a:JSONArray){prefs.edit().putString("events",a.toString()).apply()}
 fun remember(text:String,source:String="conversation",importance:Double=.7)=synchronized(lock){val a=load();a.put(JSONObject().apply{put("id",UUID.randomUUID().toString());put("time",System.currentTimeMillis());put("text",text.trim().take(4000));put("source",source);put("importance",importance.coerceIn(0.0,1.0))});while(a.length()>5000)a.remove(0);save(a)}
 fun recall(query:String,limit:Int=12):List<String>=synchronized(lock){val terms=query.lowercase().split(Regex("[^a-z0-9_-]+")).filter{it.length>=3}.toSet();val a=load();val scored=mutableListOf<Pair<Double,String>>();for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;val text=o.optString("text");val low=text.lowercase();val overlap=terms.count{low.contains(it)};if(overlap>0||terms.isEmpty()){val age=(System.currentTimeMillis()-o.optLong("time"))/86_400_000.0;scored+=overlap*3.0+o.optDouble("importance",.5)*2.0+1.0/(1.0+age/30.0) to text}};scored.sortedByDescending{it.first}.take(limit).map{it.second}}
 fun count()=synchronized(lock){load().length()}
}