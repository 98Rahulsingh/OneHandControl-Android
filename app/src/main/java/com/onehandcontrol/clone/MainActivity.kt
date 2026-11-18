package com.onehandcontrol.clone

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.content.Context
import android.text.InputType

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("control", MODE_PRIVATE) }
    private lateinit var body: LinearLayout
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); render(intent.getStringExtra("tab") ?: "Overview") }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); render(intent.getStringExtra("tab") ?: "Overview") }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun render(tab:String) {
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(12));setBackgroundColor(0xFFF3F5FA.toInt())}
        root.addView(TextView(this).apply{text="ONE HAND CONTROL";textSize=22f;setTextColor(0xFF202A40.toInt());gravity=Gravity.CENTER;setPadding(0,dp(8),0,dp(12))})
        val tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("Overview","Edges","Actions","Panels","Settings").forEach { t-> tabs.addView(Button(this).apply{text=t;setOnClickListener{render(t)}},LinearLayout.LayoutParams(0,-2,1f)) }
        root.addView(tabs)
        body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val scroll=ScrollView(this);scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        when(tab){"Edges"->edges(); "Actions"->actions(); "Panels"->panels(); "Settings"->settings();else->overview()}
    }
    private fun text(t:String) { body.addView(TextView(this).apply{text=t;textSize=15f;setTextColor(0xFF283247.toInt());setPadding(4,dp(8),4,dp(8))}) }
    private fun button(t:String,fn:()->Unit) { body.addView(Button(this).apply{text=t;setOnClickListener{fn()}},LinearLayout.LayoutParams(-1,dp(48))) }
    private fun switch(t:String,key:String,def:Boolean) { body.addView(Switch(this).apply{text=t;isChecked=prefs.getBoolean(key,def);setOnCheckedChangeListener{_,v->prefs.edit().putBoolean(key,v).apply()}},LinearLayout.LayoutParams(-1,dp(48))) }
    private fun seek(t:String,key:String,def:Int,min:Int,max:Int) {
        text("$t: ${prefs.getInt(key,def)}")
        val s=SeekBar(this).apply{this.max=max-min;progress=(prefs.getInt(key,def)-min).coerceIn(0,max-min);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(b:SeekBar?,p:Int,u:Boolean){if(u)prefs.edit().putInt(key,p+min).apply()};override fun onStartTrackingTouch(b:SeekBar?){};override fun onStopTrackingTouch(b:SeekBar?){}})}
        body.addView(s)
    }
    private fun overview() {
        text("Offline-first Android edge gesture and one-hand control suite. Enable the Accessibility Service to activate edge zones and global actions.")
        val enabled=isServiceEnabled()
        button(if(enabled)"Accessibility service: ENABLED" else "Enable Accessibility Service"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
        button("Open overlay / gesture test"){ if(enabled) Toast.makeText(this,"Touch the left or right edge",Toast.LENGTH_LONG).show() else startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button("Grant modify system settings (brightness)"){startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:$packageName")))}
        text("Quick setup: enable Accessibility → return here → set edge zones → choose gesture actions. No INTERNET permission is requested.")
        text("Features: left/right/top/bottom zones, tap/double/long/pull/swipes, Quick Tools, Pie Control, app drawer, Side Panel notes and clipboard, media buttons, remote cursor, timer, and custom mappings.")
    }
    private fun edges() {
        text("Edge zone layout")
        switch("Left edge enabled","left_enabled",true);switch("Right edge enabled","right_enabled",true);switch("Bottom edge enabled (3-zone base)","bottom_enabled",false);switch("Top / notch edge enabled","top_enabled",false)
        seek("Edge thickness (dp)","edge_thickness",16,8,48);seek("Edge length (%)","edge_length",55,10,100);seek("Swipe threshold (dp)","swipe_dp",55,20,160);seek("Long press timeout (ms)","hold_ms",550,300,1200);seek("Double tap interval (ms)","tap_ms",300,180,600)
        button("Refresh edge overlays"){sendBroadcast(Intent("com.onehandcontrol.REFRESH"))}
    }
    private fun actions() {
        text("Map each gesture on Left / Right / Bottom / Top edges. Android protects some actions and may show its system settings page instead.")
        val edges=listOf("left","right","bottom","top"); val gestures=listOf("tap","double","long","pull","swipe_up","swipe_down","swipe_left","swipe_right")
        val actions=listOf("back","home","recents","notifications","quick_settings","screenshot","power","lock","volume_up","volume_down","mute","brightness","flashlight","rotate","quick","pie","side","apps","media","cursor","timer","none")
        edges.forEach { edge-> text(edge.uppercase()+" EDGE"); gestures.forEach { g->
            val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
            row.addView(TextView(this).apply{text=g.replace('_',' ').uppercase();setTextColor(Color.DKGRAY)})
            val spinner=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,actions.map{it.replace('_',' ')});val current=prefs.getString("${edge}_${g}",defaultFor(g)) ?: defaultFor(g);setSelection(actions.indexOf(current).coerceAtLeast(0));onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:android.widget.AdapterView<*>?){};override fun onItemSelected(p:android.widget.AdapterView<*>?,v:android.view.View?,pos:Int,id:Long){prefs.edit().putString("${edge}_${g}",actions[pos]).apply()}}}
            row.addView(spinner);body.addView(row)
        } }
    }
    private fun defaultFor(g:String)=when(g){"tap"->"pie";"double"->"quick";"long"->"side";"pull"->"back";"swipe_up"->"home";"swipe_down"->"notifications";else->"recents"}
    private fun panels() {
        text("Panel contents are local and can be changed here. The live panel closes the previous panel before opening another.")
        button("Open App Launcher"){startActivity(Intent(this,PanelPreviewActivity::class.java).putExtra("panel","apps"))}
        button("Open Quick Tools"){startActivity(Intent(this,PanelPreviewActivity::class.java).putExtra("panel","quick"))}
        button("Open Pie Control"){startActivity(Intent(this,PanelPreviewActivity::class.java).putExtra("panel","pie"))}
        button("Open Side Panel"){startActivity(Intent(this,PanelPreviewActivity::class.java).putExtra("panel","side"))}
        button("Open Media Controls"){startActivity(Intent(this,PanelPreviewActivity::class.java).putExtra("panel","media"))}
        text("Pie item IDs (comma separated)"); val pie=EditText(this).apply{setText(prefs.getString("pie_items","apps,quick,back,home,recents,volume_up,volume_down,notifications,flashlight,cursor"));setSingleLine(false);minLines=2};body.addView(pie)
        button("Save Pie items"){prefs.edit().putString("pie_items",pie.text.toString().replace(" ","")).apply();Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show()}
    }
    private fun settings() {
        switch("Haptic feedback","haptics",true);switch("Pause edges on home screen (best effort)","pause_home",false);switch("Keep panels on right side","panel_right",true)
        seek("Panel opacity (%)","panel_opacity",95,25,100)
        button("Accessibility settings"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
        button("Overlay / special access"){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}
        button("Notification listener settings"){startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))}
        button("Export local settings"){val data=prefs.all.entries.joinToString("\n"){"${it.key}=${it.value}"};val send=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,data);startActivity(Intent.createChooser(send,"Export settings"))}
    }
    private fun isServiceEnabled():Boolean {
        val am=getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { it.resolveInfo.serviceInfo.packageName==packageName && it.resolveInfo.serviceInfo.name.contains("EdgeAccessibilityService") }
    }
}
