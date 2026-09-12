package com.catch7ng.ostatus

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PositionSizeActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val d by lazy { resources.displayMetrics.density }
    private val dark get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val pageColor get() = if (dark) Color.BLACK else Color.rgb(242,242,247)
    private val cardColor get() = if (dark) Color.rgb(28,28,30) else Color.WHITE
    private val primary get() = if (dark) Color.WHITE else Color.BLACK
    private val secondary get() = if (dark) Color.rgb(142,142,147) else Color.rgb(99,99,102)
    private val separator get() = if (dark) Color.rgb(56,56,58) else Color.rgb(229,229,234)
    private var sizeValue: TextView? = null; private var hValue: TextView? = null; private var vValue: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); window.statusBarColor=pageColor; window.navigationBarColor=pageColor; if(!dark) window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; buildUi() }

    private fun buildUi() {
        val scroll=ScrollView(this).apply{setBackgroundColor(pageColor)}
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(38),dp(20),dp(28))}
        content.addView(TextView(this).apply{text="‹";textSize=24f;setTextColor(Color.rgb(0,122,255));setPadding(dp(2),0,0,dp(10));setOnClickListener{finish()}})
        content.addView(TextView(this).apply{text="Position & Size";textSize=34f;setTextColor(primary);typeface=Typeface.create("sans-serif",Typeface.BOLD);setPadding(dp(2),0,0,dp(14))})
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=GradientDrawable().apply{setColor(cardColor);cornerRadius=dp(14).toFloat()};clipToOutline=true}
        val r1=controlRow("Size", { adjustSize(-5) }, { adjustSize(5) }); sizeValue=r1.second; card.addView(r1.first); card.addView(sep())
        val r2=controlRow("Horizontal", { adjust("duo_h_offset",-1) }, { adjust("duo_h_offset",1) }); hValue=r2.second; card.addView(r2.first); card.addView(sep())
        val r3=controlRow("Vertical", { adjust("duo_v_offset_v2",-1) }, { adjust("duo_v_offset_v2",1) }); vValue=r3.second; card.addView(r3.first)
        content.addView(card)
        content.addView(Button(this).apply{text="Reset to Default";textSize=16f;isAllCaps=false;stateListAnimator=null;elevation=0f;translationZ=0f;setTextColor(Color.rgb(0,122,255));background=GradientDrawable().apply{setColor(cardColor);cornerRadius=dp(14).toFloat()};setOnClickListener{prefs.edit().putInt("duo_scale_v2_pct",100).putInt("duo_h_offset",0).putInt("duo_v_offset_v2",0).apply(); refresh(); applyNow()};layoutParams=LinearLayout.LayoutParams(-1,dp(54)).apply{topMargin=dp(20)}})
        scroll.addView(content);setContentView(scroll);refresh()
    }
    private fun controlRow(title:String, minus:()->Unit, plus:()->Unit):Pair<LinearLayout,TextView>{
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),dp(8),dp(12),dp(8));minimumHeight=dp(58)}
        row.addView(TextView(this).apply{text=title;textSize=16f;setTextColor(primary)},LinearLayout.LayoutParams(0,-2,1f))
        val m=Button(this).apply{text="−";textSize=20f;isAllCaps=false;setOnClickListener{minus()}}; row.addView(m,LinearLayout.LayoutParams(dp(48),dp(42)))
        val value=TextView(this).apply{textSize=15f;setTextColor(secondary);gravity=Gravity.CENTER;text="0"}; row.addView(value,LinearLayout.LayoutParams(dp(70),dp(42)))
        val p=Button(this).apply{text="+";textSize=20f;isAllCaps=false;setOnClickListener{plus()}}; row.addView(p,LinearLayout.LayoutParams(dp(48),dp(42)))
        return Pair(row,value)
    }
    private fun adjustSize(delta:Int){val n=(prefs.getInt("duo_scale_v2_pct",100)+delta).coerceIn(50,250);prefs.edit().putInt("duo_scale_v2_pct",n).apply();refresh();applyNow()}
    private fun adjust(key:String,delta:Int){val n=(prefs.getInt(key,0)+delta).coerceIn(-30,30);prefs.edit().putInt(key,n).apply();refresh();applyNow()}
    private fun refresh(){sizeValue?.text="${prefs.getInt("duo_scale_v2_pct",100)}%";hValue?.text=signed(prefs.getInt("duo_h_offset",0));vValue?.text=signed(prefs.getInt("duo_v_offset_v2",0))}
    private fun signed(v:Int)=if(v>0) "+$v" else "$v"
    private fun applyNow(){if(prefs.getBoolean("duo_enabled",true)&&Settings.canDrawOverlays(this)){stopService(Intent(this,StatusBarService::class.java));ContextCompat.startForegroundService(this,Intent(this,StatusBarService::class.java))}}
    private fun sep()=View(this).apply{setBackgroundColor(separator);layoutParams=LinearLayout.LayoutParams(-1,1).apply{marginStart=dp(16)}}
    private fun dp(v:Int)=(v*d+0.5f).toInt()
}
