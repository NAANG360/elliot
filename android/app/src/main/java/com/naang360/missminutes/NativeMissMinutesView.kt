package com.naang360.missminutes

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** Native mascot renderer. Death is a visual state machine; it never crashes Android. */
class NativeMissMinutesView(context:Context):View(context){
 var emotion:String="warm";set(v){field=v;invalidate()}
 var speaking:Boolean=false;set(v){field=v;invalidate()}
 var motion:Float=0f;set(v){field=v.coerceIn(0f,1f);invalidate()}
 private val p=Paint(Paint.ANTI_ALIAS_FLAG)
 private val mono=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)
 private val start=System.nanoTime()
 private var death=0f
 private var dead=false
 private var deathStarted=0L

 fun beginDeath(){if(dead)return;dead=true;death=0f;deathStarted=System.currentTimeMillis();invalidate()}

 private fun glitch(c:Canvas,cx:Float,cy:Float,r:Float,t:Float,alpha:Int){
  p.typeface=mono;p.textAlign=Paint.Align.CENTER;p.textSize=max(12f,r*.115f)
  p.color=Color.argb(alpha,255,218,150)
  val lines=arrayOf("01001101 01001001","CLOCK//NULL","CHRONAL_BUFFER",":: :: 0101 :: ::","MISS_MINUTES")
  for(i in lines.indices){
   val jitter=sin(t*(11f+i*2f))*(r*.75f)
   c.drawText(lines[i],cx+jitter,cy-r*(.9f-i*.18f),p)
  }
 }

 private fun asciiClock(c:Canvas,cx:Float,cy:Float,r:Float,alpha:Int,t:Float){
  p.color=Color.argb(alpha,255,255,255);p.typeface=mono;p.textAlign=Paint.Align.CENTER;p.textSize=max(18f,r*.19f)
  c.drawText(" .--------. ",cx,cy-r*.20f,p)
  c.drawText("/  12  |  \\ ",cx,cy-r*.02f,p)
  c.drawText("|  9 --O-- 3 |",cx,cy+r*.18f,p)
  c.drawText("\  6  |  /",cx,cy+r*.38f,p)
  c.drawText(" '--------' ",cx,cy+r*.56f,p)
  p.strokeWidth=max(2f,r*.025f);p.style=Paint.Style.STROKE
  val hand=sin(t*5.0)*.9
  c.drawLine(cx,cy+r*.08f,cx+cos(hand).toFloat()*r*.23f,cy+r*.08f+sin(hand).toFloat()*r*.23f,p)
  p.style=Paint.Style.FILL
  p.textSize=max(12f,r*.09f);c.drawText("<|>  <|>",cx,cy+r*.82f,p)
 }

 override fun onDraw(c:Canvas){
  super.onDraw(c)
  val t=(System.nanoTime()-start)/1_000_000_000f
  val w=width.toFloat();val h=height.toFloat();val cx=w/2f
  c.drawColor(Color.rgb(12,8,4))
  val phase=if(dead)(System.currentTimeMillis()-deathStarted)/1000f else 0f
  death=if(dead)min(1f,phase/8f)else 0f
  if(dead && death>=.72f){
   val a=((death-.72f)/.28f).coerceIn(0f,1f)
   asciiClock(c,cx,h*.43f,min(w,h)*.27f,((1f-a)*255).roundToInt(),t)
   glitch(c,cx,h*.43f,min(w,h)*.27f,t,(a*220).roundToInt())
  }
  val characterAlpha=((1f-(death/.72f)).coerceIn(0f,1f)*255f).roundToInt()
  if(characterAlpha>0){
   val r=min(w,h)*.27f;val cy=h*.43f+sin(t*1.7f)*5f
   p.style=Paint.Style.FILL;p.color=Color.argb(45,255,173,70);c.drawCircle(cx,cy,r*1.35f,p)
   p.color=Color.argb(24,255,220,160);c.drawCircle(cx,cy,r*1.65f,p)
   p.alpha=characterAlpha;p.color=Color.rgb(230,125,38);c.drawCircle(cx,cy,r,p)
   p.alpha=characterAlpha;p.style=Paint.Style.STROKE;p.strokeWidth=r*.035f;p.color=Color.rgb(48,29,20)
   for(i in 0 until 12){val a=i*Math.PI/6;c.drawLine(cx+cos(a).toFloat()*r*.82f,cy+sin(a).toFloat()*r*.82f,cx+cos(a).toFloat()*r*.91f,cy+sin(a).toFloat()*r*.91f,p)}
   p.style=Paint.Style.FILL;p.color=Color.WHITE;c.drawOval(cx-r*.55f,cy-r*.42f,cx-r*.04f,cy+r*.12f,p);c.drawOval(cx+r*.04f,cy-r*.42f,cx+r*.55f,cy+r*.12f,p)
   val eyeJitter=if(death>.18f)sin(t*23f)*r*.045f else 0f
   p.color=Color.rgb(35,23,20);val shift=if(emotion=="curious")r*.09f else 0f
   c.drawCircle(cx-r*.28f+shift+eyeJitter,cy-r*.14f,r*.13f,p);c.drawCircle(cx+r*.28f+shift-eyeJitter,cy-r*.14f,r*.13f,p)
   p.color=Color.rgb(46,26,18);c.drawCircle(cx,cy+r*.02f,r*.045f,p)
   p.style=Paint.Style.STROKE;p.strokeWidth=r*.028f;p.strokeCap=Paint.Cap.ROUND
   val ha=if(speaking)t*3.8 else t*.7
   c.drawLine(cx,cy,cx+cos(ha).toFloat()*r*.62f,cy+sin(ha).toFloat()*r*.62f,p)
   c.drawLine(cx,cy,cx+cos(ha+Math.PI/2).toFloat()*r*.38f,cy+sin(ha+Math.PI/2).toFloat()*r*.38f,p)
   p.strokeCap=Paint.Cap.BUTT;p.style=Paint.Style.FILL;p.color=Color.WHITE
   c.drawCircle(cx-r*.95f,cy+r*.15f,r*.12f,p);c.drawCircle(cx+r*.95f,cy+r*.15f,r*.12f,p)
   p.color=Color.rgb(48,29,20);p.style=Paint.Style.STROKE;p.strokeWidth=r*.025f
   c.drawLine(cx-r*.82f,cy+r*.18f,cx-r*1.08f,cy+r*.45f,p);c.drawLine(cx+r*.82f,cy+r*.18f,cx+r*1.08f,cy+r*.45f,p)
   p.style=Paint.Style.FILL;p.color=Color.rgb(35,23,20)
   c.drawRect(cx-r*.38f,cy+r*.98f,cx-r*.08f,cy+r*1.35f,p);c.drawRect(cx+r*.08f,cy+r*.98f,cx+r*.38f,cy+r*1.35f,p)
   p.color=Color.rgb(210,95,32);c.drawOval(cx-r*.48f,cy+r*1.25f,cx-r*.01f,cy+r*1.45f,p);c.drawOval(cx+r*.01f,cy+r*1.25f,cx+r*.48f,cy+r*1.45f,p)
   if(death>.12f)glitch(c,cx,cy,r,t,((death/.72f)*210).roundToInt().coerceAtMost(220))
  }
  if(dead&&death>=.72f&&death<.94f){
   val a=((death-.72f)/.22f).coerceIn(0f,1f);asciiClock(c,cx,h*.43f,min(w,h)*.27f,(255*(1f-a)).roundToInt(),t)
  }
  if(dead&&death>=.94f){
   val a=((death-.94f)/.06f).coerceIn(0f,1f)
   p.color=Color.argb((255*a).roundToInt(),0,0,0);c.drawRect(0f,0f,w,h,p)
   p.color=Color.argb((255*a).roundToInt(),255,255,255);p.typeface=mono;p.textAlign=Paint.Align.CENTER
   p.textSize=max(20f,min(w,h)*.075f);c.drawText("CHRONAL FATAL ERROR",cx,h*.39f,p)
   p.textSize=max(12f,min(w,h)*.042f);c.drawText("The program encountered a timeline anomaly.",cx,h*.49f,p)
   c.drawText("MISS MINUTES HAS CEASED EXECUTION.",cx,h*.55f,p)
   p.textSize=max(10f,min(w,h)*.032f);c.drawText("STATUS: VISUAL TERMINATION / SYSTEM INTACT",cx,h*.63f,p)
  }
  p.alpha=255
  postInvalidateDelayed(16)
 }
}