package com.naang360.missminutes

import android.app.Activity
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import kotlin.math.min
import kotlin.math.sin

/**
 * Separate theatrical chess hand-off. It never modifies Chess.com or its data.
 * The corruption sequence is entirely rendered by this activity before the
 * user's installed Chess.com app is launched.
 */
class ChessModeActivity:Activity(){
 private val handler=Handler(Looper.getMainLooper())
 private lateinit var scene:ChessCorruptionView
 private var launched=false

 override fun onCreate(state:Bundle?){
  super.onCreate(state)
  window.setFlags(1024,1024)
  scene=ChessCorruptionView()
  setContentView(scene)
  handler.postDelayed({launchChessCom()},5200)
 }

 private fun launchChessCom(){
  if(launched)return
  launched=true
  val intent=packageManager.getLaunchIntentForPackage("com.chess")
  if(intent!=null){
   startActivity(intent)
  }else{
   Toast.makeText(this,"Chess.com isn't installed, sugar.",Toast.LENGTH_LONG).show()
  }
  handler.postDelayed({finish()},350)
 }

 override fun onBackPressed(){
  handler.removeCallbacksAndMessages(null)
  finish()
 }

 inner class ChessCorruptionView:View(this){
  private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  private val start=System.nanoTime()
  override fun onDraw(c:Canvas){
   val t=(System.nanoTime()-start)/1_000_000_000f
   val progress=(t/5.2f).coerceIn(0f,1f)
   c.drawColor(Color.rgb(9,6,3))
   val w=width.toFloat();val h=height.toFloat();val cx=w/2f
   paint.textAlign=Paint.Align.CENTER
   paint.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)

   paint.textSize=min(w,h)*.065f
   paint.color=Color.rgb(255,205,130)
   c.drawText("MISS MINUTES // CHESS",cx,h*.14f,paint)

   val s=min(w,h)*.29f
   val left=cx-s;val top=h*.31f
   paint.style=Paint.Style.FILL
   paint.color=Color.rgb(35,28,21);c.drawRoundRect(left,top,left+s*2,top+s*2,.08f*s,.08f*s,paint)
   paint.color=Color.rgb((60+195*progress).toInt(),(60+100*progress).toInt(),(60+10*progress).toInt())
   c.drawRoundRect(left+4,top+4,left+s*2-4,top+s*2-4,.07f*s,.07f*s,paint)

   val board=s*1.55f;val bx=cx-board/2;val by=top+board*.20f;val cell=board/8f
   for(y in 0 until 8)for(x in 0 until 8){
    val dark=((x+y)%2==0)
    val base=if(dark)Color.rgb(45,34,25)else Color.rgb(220,180,120)
    val orange=Color.rgb(240,120,25)
    val r=Color.red(base)+(Color.red(orange)-Color.red(base))*progress
    val g=Color.green(base)+(Color.green(orange)-Color.green(base))*progress
    val b=Color.blue(base)+(Color.blue(orange)-Color.blue(base))*progress
    paint.color=Color.rgb(r.toInt(),g.toInt(),b.toInt())
    c.drawRect(bx+x*cell,by+y*cell,bx+(x+1)*cell,by+(y+1)*cell,paint)
   }
   paint.textSize=cell*.62f
   paint.color=Color.rgb(30,20,15)
   val pieces=arrayOf("♜","♞","♝","♛","♚","♝","♞","♜")
   for(x in 0 until 8)c.drawText(pieces[x],bx+(x+.5f)*cell,by+cell*.72f,paint)
   for(x in 0 until 8)c.drawText("♟",bx+(x+.5f)*cell,by+cell*1.72f,paint)
   paint.color=Color.WHITE
   for(x in 0 until 8)c.drawText("♙",bx+(x+.5f)*cell,by+cell*6.72f,paint)
   val white=arrayOf("♖","♘","♗","♕","♔","♗","♘","♖")
   for(x in 0 until 8)c.drawText(white[x],bx+(x+.5f)*cell,by+cell*7.72f,paint)

   paint.textSize=min(w,h)*.045f
   paint.color=Color.rgb(255,190,95)
   c.drawText(if(progress<.25f)"CONNECTING TO THE CHRONAL BOARD"
     else if(progress<.55f)"TIME VARIANCE DETECTED"
     else if(progress<.82f)"MISS MINUTES IS TAKING THE BOARD"
     else "OPENING CHESS.COM // CHRONAL HANDSHAKE",cx,h*.86f,paint)

   val glitchCount=(progress*28).toInt()
   paint.color=Color.argb(170,255,125,25)
   paint.strokeWidth=2f
   for(i in 0 until glitchCount){
    val yy=h*(.20f+((i*37)%60)/100f)
    val xx=((sin(t*7+i)*.5f+.5f)*w)
    c.drawRect(xx,yy,xx+((i*19)%120+20),yy+2f,paint)
   }
   postInvalidateDelayed(16)
  }
 }
}