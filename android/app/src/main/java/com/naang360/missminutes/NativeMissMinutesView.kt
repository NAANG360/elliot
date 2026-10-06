package com.naang360.missminutes

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class NativeMissMinutesView(context: Context) : View(context) {
    var emotion:String="warm"; set(v){field=v;invalidate()}
    var speaking:Boolean=false; set(v){field=v;invalidate()}
    var motion:Float=0f; set(v){field=v.coerceIn(0f,1f);invalidate()}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var start=System.nanoTime(); private var death=0f; private var dead=false
    init { setLayerType(View.LAYER_TYPE_SOFTWARE,null) }
    fun beginDeath(){dead=true;death=0f;invalidate()}
    override fun onDraw(c:Canvas){
        super.onDraw(c); val t=(System.nanoTime()-start)/1_000_000_000f; val w=width.toFloat(); val h=height.toFloat()
        c.drawColor(Color.rgb(12,8,4)); val cx=w/2f; val cy=h*.43f+sin(t*1.7f)*5f; val r=minOf(w,h)*.27f
        paint.style=Paint.Style.FILL; paint.color=Color.argb(42,255,173,70);c.drawCircle(cx,cy,r*1.35f,paint);paint.color=Color.argb(24,255,220,160);c.drawCircle(cx,cy,r*1.65f,paint)
        val fade=if(dead)(1f-death).coerceIn(0f,1f)else 1f;paint.alpha=(255*fade).toInt();paint.color=Color.rgb(230,125,38);c.drawCircle(cx,cy,r,paint)
        paint.color=Color.rgb(48,29,20);paint.style=Paint.Style.STROKE;paint.strokeWidth=r*.035f
        for(i in 0 until 12){val a=i*Math.PI/6.0;c.drawLine(cx+cos(a).toFloat()*r*.82f,cy+sin(a).toFloat()*r*.82f,cx+cos(a).toFloat()*r*.91f,cy+sin(a).toFloat()*r*.91f,paint)}
        paint.style=Paint.Style.FILL;paint.color=Color.WHITE;c.drawOval(cx-r*.55f,cy-r*.42f,cx-r*.04f,cy+r*.12f,paint);c.drawOval(cx+r*.04f,cy-r*.42f,cx+r*.55f,cy+r*.12f,paint)
        paint.color=Color.rgb(35,23,20);val shift=if(emotion=="curious")r*.09f else 0f;c.drawCircle(cx-r*.28f+shift,cy-r*.14f,r*.13f,paint);c.drawCircle(cx+r*.28f+shift,cy-r*.14f,r*.13f,paint);paint.color=Color.rgb(46,26,18);c.drawCircle(cx,cy+r*.02f,r*.045f,paint)
        paint.strokeWidth=r*.028f;paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;val ha=if(speaking)t*3.8 else t*.7;c.drawLine(cx,cy,cx+cos(ha).toFloat()*r*.62f,cy+sin(ha).toFloat()*r*.62f,paint);c.drawLine(cx,cy,cx+cos(ha+Math.PI/2).toFloat()*r*.38f,cy+sin(ha+Math.PI/2).toFloat()*r*.38f,paint);paint.strokeCap=Paint.Cap.BUTT;paint.style=Paint.Style.FILL
        paint.color=Color.WHITE;c.drawCircle(cx-r*.95f,cy+r*.15f,r*.12f,paint);c.drawCircle(cx+r*.95f,cy+r*.15f,r*.12f,paint);paint.color=Color.rgb(48,29,20);paint.strokeWidth=r*.025f;paint.style=Paint.Style.STROKE;c.drawLine(cx-r*.82f,cy+r*.18f,cx-r*1.08f,cy+r*.45f,paint);c.drawLine(cx+r*.82f,cy+r*.18f,cx+r*1.08f,cy+r*.45f,paint)
        paint.style=Paint.Style.FILL;paint.color=Color.rgb(35,23,20);c.drawRect(cx-r*.38f,cy+r*.98f,cx-r*.08f,cy+r*1.35f,paint);c.drawRect(cx+r*.08f,cy+r*.98f,cx+r*.38f,cy+r*1.35f,paint);paint.color=Color.rgb(210,95,32);c.drawOval(cx-r*.48f,cy+r*1.25f,cx-r*.01f,cy+r*1.45f,paint);c.drawOval(cx+r*.01f,cy+r*1.25f,cx+r*.48f,cy+r*1.45f,paint)
        if(dead){paint.color=Color.argb((220*death).toInt(),255,255,255);paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.MONOSPACE;paint.textSize=r*.16f;listOf("[ CHRONAL PROCESS ]","   CLOCK//FATAL   ","   0110 0101 0110").forEachIndexed{i,s->c.drawText(s,cx,cy+r*(1.9f+i*.22f),paint)}}
        if(dead&&death>=1f){paint.color=Color.BLACK;c.drawRect(0f,0f,w,h,paint);paint.color=Color.WHITE;paint.textAlign=Paint.Align.CENTER;paint.textSize=r*.18f;c.drawText("CHRONAL FATAL ERROR",cx,h*.42f,paint);paint.textSize=r*.10f;c.drawText("The program encountered a timeline anomaly.",cx,h*.51f,paint);c.drawText("Miss Minutes has ceased execution.",cx,h*.58f,paint)}
        postInvalidateDelayed(16);if(dead&&death<1f)death=(death+.008f).coerceAtMost(1f)
    }
}