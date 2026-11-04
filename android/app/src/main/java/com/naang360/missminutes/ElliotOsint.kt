package com.naang360.missminutes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Public-feed OSINT collector. RSS entries are leads; never presented as independently verified facts. */
class ElliotOsint(context: Context) {
    data class Feed(val id:String,val category:String,val publisher:String,val url:String)
    data class Item(val id:String,val category:String,val publisher:String,val title:String,val url:String,val published:String,val retrieved:String,val summary:String,val sha256:String)

    private val root=File(context.applicationContext.filesDir,"elliot-osint").apply{mkdirs()}
    private val archive=File(root,"items.jsonl")
    private val feeds= listOf(
        Feed("github-blog","software","GitHub Blog","https://github.blog/feed/"),
        Feed("github-changelog","software","GitHub Changelog","https://github.blog/changelog/feed/"),
        Feed("arxiv-ai","ai-research","arXiv cs.AI","https://rss.arxiv.org/rss/cs.AI"),
        Feed("arxiv-ml","ai-research","arXiv cs.LG","https://rss.arxiv.org/rss/cs.LG"),
        Feed("google-security","cybersecurity","Google Security Blog","https://security.googleblog.com/feeds/posts/default")
    )

    fun refresh(): List<Item> {
        val known=if(archive.exists()) archive.readLines().mapNotNull{runCatching{JSONObject(it).optString("id")}.getOrNull()}.toMutableSet() else mutableSetOf()
        val added=mutableListOf<Item>()
        feeds.forEach { feed ->
            runCatching { fetch(feed) }.getOrDefault(emptyList()).forEach { item ->
                if(known.add(item.id)) { archive.appendText(toJson(item).toString()+"\n"); added+=item }
            }
        }
        return added
    }

    fun recent(limit:Int=40,category:String?=null):List<Item> {
        if(!archive.exists()) return emptyList()
        return archive.readLines().asReversed().mapNotNull{line->runCatching{fromJson(JSONObject(line))}.getOrNull()}
            .filter{category==null||it.category==category}.take(limit)
    }

    fun briefing():String {
        val items=recent(35)
        if(items.isEmpty()) return "No OSINT items are archived yet. Refresh public intelligence feeds first."
        return items.groupBy{it.category}.entries.joinToString("\n\n"){(category,rows)->
            category.uppercase(Locale.ROOT)+"\n"+rows.take(8).joinToString("\n"){ "• ${it.title}\n  ${it.publisher} · ${it.published.ifBlank{"publication date unavailable"}}\n  ${it.url}\n  Lead only: ${it.summary.take(220)}" }
        }+"\n\nThese are public-feed leads, not independently verified conclusions."
    }

    private fun fetch(feed:Feed):List<Item> {
        val conn=(URL(feed.url).openConnection() as HttpURLConnection).apply{
            connectTimeout=10000;readTimeout=12000;instanceFollowRedirects=true
            setRequestProperty("User-Agent","ElliotOSINT/0.1 (public-feed-reader)")
            setRequestProperty("Accept","application/rss+xml, application/atom+xml, application/xml, text/xml")
        }
        try {
            require(conn.responseCode in 200..299){"HTTP ${conn.responseCode}"}
            require(conn.contentLengthLong<=2_000_000L || conn.contentLengthLong<0){"Feed too large"}
            val bytes=conn.inputStream.use{it.readNBytes(2_000_001)}
            require(bytes.size<=2_000_000){"Feed too large"}
            return parse(feed,bytes.toString(Charsets.UTF_8)).take(30)
        } finally { conn.disconnect() }
    }

    private fun parse(feed:Feed,xml:String):List<Item> {
        val parser=XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(xml.reader())
        val out=mutableListOf<Item>(); var inItem=false;var tag="";var title="";var link="";var date="";var desc=""
        var event=parser.eventType
        while(event!=XmlPullParser.END_DOCUMENT && out.size<30){
            when(event){
                XmlPullParser.START_TAG->{tag=parser.name.lowercase(Locale.ROOT);if(tag=="item"||tag=="entry"){inItem=true;title="";link="";date="";desc=""}
                    if(inItem&&tag=="link"){val href=parser.getAttributeValue(null,"href");if(!href.isNullOrBlank())link=href}}
                XmlPullParser.TEXT->{if(inItem) when(tag){"title"->title+=parser.text;"link"->link+=parser.text;"pubdate","published","updated"->date+=parser.text;"description","summary","content"->desc+=parser.text}}
                XmlPullParser.END_TAG->{val end=parser.name.lowercase(Locale.ROOT);if(inItem&&(end=="item"||end=="entry")){
                    val cleanTitle=strip(title).take(300);val cleanUrl=link.trim()
                    if(cleanTitle.isNotBlank()&&cleanUrl.startsWith("https://")){
                        val retrieved=Instant.now().toString();val sum=strip(desc).take(1200);val digest=sha((cleanTitle+"\n"+cleanUrl+"\n"+date).toByteArray())
                        out+=Item(digest,feed.category,feed.publisher,cleanTitle,cleanUrl,date.trim(),retrieved,sum,sha(xml.toByteArray()))
                    };inItem=false
                };tag=""}
            };event=parser.next()
        }
        return out
    }
    private fun strip(s:String)=s.replace(Regex("<[^>]+>")," ").replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replace(Regex("\\s+")," ").trim()
    private fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
    private fun toJson(i:Item)=JSONObject().apply{put("id",i.id);put("category",i.category);put("publisher",i.publisher);put("title",i.title);put("url",i.url);put("published",i.published);put("retrieved",i.retrieved);put("summary",i.summary);put("sha256",i.sha256)}
    private fun fromJson(o:JSONObject)=Item(o.optString("id"),o.optString("category"),o.optString("publisher"),o.optString("title"),o.optString("url"),o.optString("published"),o.optString("retrieved"),o.optString("summary"),o.optString("sha256"))
}
