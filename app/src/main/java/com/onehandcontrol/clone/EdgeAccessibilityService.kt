package com.onehandcontrol.clone

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import android.media.AudioManager
import android.media.MediaPlayer
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.text.InputType
import android.text.TextUtils
import android.graphics.Point
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class EdgeAccessibilityService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("control", MODE_PRIVATE) }
    private val edgeViews = mutableListOf<View>()
    private var panel: View? = null
    private var panelKind = ""
    private var lastTap = 0L
    private var cursor: View? = null
    private var cursorX = 0f
    private var cursorY = 0f
    private var torchOn = false
    private var lastVolume = -1
    private val audio by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val clipboard by lazy { getSystemService(CLIPBOARD_SERVICE) as ClipboardManager }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        serviceInfo = serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 50
        }
        installEdges()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString().orEmpty()
            if (pkg == packageName || pkg == "com.android.systemui") return
            val paused = prefs.getStringSet("paused_packages", emptySet()) ?: emptySet()
            if (paused.contains(pkg)) removeEdges() else if (edgeViews.isEmpty()) installEdges()
        }
    }
    override fun onInterrupt() { removeEdges(); closePanel() }
    override fun onDestroy() { removeEdges(); closePanel(); super.onDestroy() }

    private fun overlayType() = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
    private fun params(w: Int, h: Int, gravity: Int, flags: Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE): WindowManager.LayoutParams =
        WindowManager.LayoutParams(w, h, overlayType(), flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { this.gravity = gravity }

    private fun installEdges() {
        removeEdges()
        val dm = resources.displayMetrics
        val thickness = prefs.getInt("edge_thickness", 16).coerceIn(8, 48)
        val lengthPct = prefs.getInt("edge_length", 55).coerceIn(10, 100)
        val height = (dm.heightPixels * lengthPct / 100f).toInt()
        if (prefs.getBoolean("left_enabled", true)) addEdge("left", thickness, height, Gravity.LEFT or Gravity.CENTER_VERTICAL)
        if (prefs.getBoolean("right_enabled", true)) addEdge("right", thickness, height, Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        if (prefs.getBoolean("bottom_enabled", false)) addEdge("bottom", dm.widthPixels, thickness, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        if (prefs.getBoolean("top_enabled", false)) addEdge("top", dm.widthPixels, thickness, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
    }
    private fun addEdge(side: String, w: Int, h: Int, gravity: Int) {
        val v = View(this).apply {
            val color = prefs.getInt("edge_color", 0x664C6FFF)
            background = GradientDrawable().apply { setColor(color); cornerRadius = 18f }
            setOnTouchListener(EdgeTouch(side))
        }
        try { wm.addView(v, params(w, h, gravity)); edgeViews.add(v) } catch (_: Exception) {}
    }
    private fun removeEdges() { edgeViews.forEach { try { wm.removeView(it) } catch (_: Exception) {} }; edgeViews.clear() }

    private inner class EdgeTouch(private val side: String) : View.OnTouchListener {
        var downX = 0f; var downY = 0f; var downAt = 0L; var longFired = false
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; downAt = System.currentTimeMillis(); longFired = false
                    handler.postDelayed({ if (v.isPressed && !longFired) { longFired = true; runMapped(side, "long") } }, prefs.getInt("hold_ms", 550).toLong())
                    v.isPressed = true; return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) + abs(dy) > 25) v.isPressed = false
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (longFired) return true
                    if (abs(dx) + abs(dy) > prefs.getInt("swipe_dp", 55) * resources.displayMetrics.density) {
                        val key = when {
                            side == "bottom" && abs(dx) > abs(dy) -> if (dx < 0) "swipe_left" else "swipe_right"
                            side == "bottom" -> if (dy < 0) "pull" else "swipe_down"
                            abs(dx) > abs(dy) -> if ((side == "right" && dx < 0) || (side == "left" && dx > 0)) "pull" else if (dx < 0) "swipe_left" else "swipe_right"
                            dy < 0 -> "swipe_up"
                            else -> "swipe_down"
                        }
                        runMapped(side, key)
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - lastTap < prefs.getInt("tap_ms", 300)) { lastTap = 0; runMapped(side, "double") }
                        else { lastTap = now; handler.postDelayed({ if (lastTap == now) { lastTap = 0; runMapped(side, "tap") } }, prefs.getInt("tap_ms", 300).toLong()) }
                    }
                    return true
                }
            }
            return true
        }
    }

    private fun runMapped(side: String, gesture: String) {
        val action = prefs.getString("${side}_${gesture}", defaultAction(gesture)) ?: "panel"
        performAction(action)
    }
    private fun defaultAction(g: String) = when(g) { "tap" -> "pie"; "double" -> "quick"; "long" -> "side"; "pull" -> "back"; "swipe_up" -> "home"; "swipe_down" -> "notifications"; else -> "recents" }

    private fun performAction(a: String) {
        when (a) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "power" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            "screenshot" -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            "lock" -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            "volume_up" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "volume_down" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> { val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC); if (now > 0) { lastVolume = now; audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI) } else audio.setStreamVolume(AudioManager.STREAM_MUSIC, max(1,lastVolume), AudioManager.FLAG_SHOW_UI) }
            "brightness" -> openBrightnessSettings()
            "flashlight" -> toggleTorch()
            "rotate" -> startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "quick" -> showPanel("quick")
            "pie" -> showPanel("pie")
            "side" -> showPanel("side")
            "apps" -> showPanel("apps")
            "media" -> showPanel("media")
            "cursor" -> showCursor()
            "timer" -> showPanel("timer")
            "notes" -> showPanel("notes")
            "none" -> Unit
            else -> if (a.startsWith("app:")) launchPackage(a.removePrefix("app:")) else showPanel("apps")
        }
    }
    private fun openBrightnessSettings() { try { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }
    private fun toggleTorch() {
        try {
            val cm = getSystemService(CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull() ?: return
            torchOn = !torchOn; cm.setTorchMode(id, torchOn)
        } catch (_: Exception) { Toast.makeText(this, "Flashlight unavailable", Toast.LENGTH_SHORT).show() }
    }
    private fun launchPackage(pkg: String) { try { startActivity(packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return) } catch (_: Exception) {} }

    private fun shape(color: Int, radius: Float = 24f) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun closePanel() { panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }; panel = null; panelKind = "" }
    private fun showPanel(kind: String) {
        if (panelKind == kind && panel != null) { closePanel(); return }
        closePanel()
        panelKind = kind
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(12),dp(14),dp(12)); background = shape(0xF21A2030.toInt(), dp(24).toFloat()) }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply { text = when(kind) { "quick" -> "QUICK TOOLS"; "pie" -> "PIE CONTROL"; "side" -> "SIDE PANEL"; "media" -> "MEDIA"; "timer" -> "TIMER"; else -> "APP LAUNCHER" }; textSize=16f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0,-2,1f))
        head.addView(Button(this).apply { text = "×"; setOnClickListener { closePanel() } })
        root.addView(head)
        when(kind) {
            "quick" -> buildQuick(root)
            "pie" -> buildPie(root)
            "side" -> buildSide(root)
            "apps" -> buildApps(root)
            "media" -> buildMedia(root)
            "timer" -> buildTimer(root)
            "notes" -> buildSide(root, "Notes")
        }
        val dm = resources.displayMetrics
        val w = min(dp(340), dm.widthPixels - dp(28))
        val h = min(dp(590), dm.heightPixels - dp(80))
        val p = params(w,h,Gravity.RIGHT or Gravity.CENTER_VERTICAL, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        p.x = dp(8)
        try { panel = root; wm.addView(root,p) } catch (_: Exception) { panel = null }
    }
    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply { text=label; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1,dp(48)).apply { bottomMargin=dp(4) })
    }
    private fun buildQuick(root: LinearLayout) {
        val scroll = ScrollView(this); val col = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val brightness = SeekBar(this).apply { max=255; progress=try { Settings.System.getInt(contentResolver,Settings.System.SCREEN_BRIGHTNESS) } catch (_:Exception) { 120 }; setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s:SeekBar?,p:Int,u:Boolean) { if(u) try { if(Settings.System.canWrite(this@EdgeAccessibilityService)) Settings.System.putInt(contentResolver,Settings.System.SCREEN_BRIGHTNESS,p) else openWriteSettings() } catch (_:Exception) {} }; override fun onStartTrackingTouch(s:SeekBar?){}; override fun onStopTrackingTouch(s:SeekBar?){} }) }
        col.addView(TextView(this).apply { text="Brightness"; setTextColor(Color.WHITE) }); col.addView(brightness)
        val volume = SeekBar(this).apply { max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC); progress=audio.getStreamVolume(AudioManager.STREAM_MUSIC); setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s:SeekBar?,p:Int,u:Boolean){if(u)audio.setStreamVolume(AudioManager.STREAM_MUSIC,p,0)}; override fun onStartTrackingTouch(s:SeekBar?){}; override fun onStopTrackingTouch(s:SeekBar?){} }) }
        col.addView(TextView(this).apply { text="Media volume"; setTextColor(Color.WHITE) }); col.addView(volume)
        addButton(col,"Wi-Fi settings") { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        addButton(col,"Bluetooth settings") { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        addButton(col,"Flashlight") { toggleTorch() }
        addButton(col,"Notifications") { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) }
        addButton(col,"Quick Settings") { performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) }
        addButton(col,"Settings") { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        scroll.addView(col); root.addView(scroll)
    }
    private fun openWriteSettings() { startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    private fun buildPie(root: LinearLayout) {
        val names = prefs.getString("pie_items","apps,quick,back,home,recents,volume_up,volume_down,notifications,flashlight,cursor").orEmpty().split(",")
        val grid = GridLayout(this).apply { columnCount=3 }
        names.forEach { name -> val label=pretty(name); grid.addView(Button(this).apply { text=label; setOnClickListener { if(name=="apps") showPanel("apps") else performAction(name) } }, GridLayout.LayoutParams().apply { width=0; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(dp(2),dp(2),dp(2),dp(2)) }) }
        root.addView(grid)
        addButton(root,"Edit Pie items") { openMainSettings("pie") }
    }
    private fun buildSide(root: LinearLayout, initial: String = "Actions") {
        val tabs=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        listOf("Actions","Clipboard","Notes").forEach { tab -> tabs.addView(Button(this).apply { text=tab; setOnClickListener { body.removeAllViews(); fillSidePage(body,tab) } },LinearLayout.LayoutParams(0,-2,1f)) }
        root.addView(tabs); root.addView(body); fillSidePage(body,initial)
    }
    private fun fillSidePage(body: LinearLayout, tab: String) {
        body.removeAllViews()
        when(tab) {
            "Actions" -> { addButton(body,"Open Apps") { showPanel("apps") }; addButton(body,"Quick Tools") { showPanel("quick") }; addButton(body,"Open Pie Control") { showPanel("pie") }; addButton(body,"Remote Cursor") { showCursor() }; addButton(body,"Settings") { startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            "Clipboard" -> {
                val clip=clipboard.primaryClip
                if(clip!=null) for(i in 0 until clip.itemCount) { val txt=clip.getItemAt(i).coerceToText(this)?.toString().orEmpty(); addButton(body,txt.take(90)) { clipboard.setPrimaryClip(ClipData.newPlainText("copy",txt)); Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show() } }
                else addButton(body,"Clipboard is empty") {}
                addButton(body,"Paste clipboard text into note") { val t=clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty(); saveNote(t); fillSidePage(body,"Notes") }
            }
            else -> {
                val notes=prefs.getString("notes","").orEmpty().split("\n---NOTE---\n").filter { it.isNotBlank() }
                notes.forEach { n -> addButton(body,n.take(80)) { showNoteEditor(n) } }
                addButton(body,"+ Add note") { showNoteEditor("") }
            }
        }
    }
    private fun saveNote(note:String) { val old=prefs.getString("notes","").orEmpty().split("\n---NOTE---\n").filter{it.isNotBlank()}.toMutableList(); if(note.isNotBlank()) old.add(0,note); prefs.edit().putString("notes",old.distinct().take(100).joinToString("\n---NOTE---\n")).apply() }
    private fun showNoteEditor(old:String) {
        val edit=EditText(this).apply { setText(old); minLines=4; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        val wrap=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(16),dp(16),dp(16)); background=shape(0xFF20283A.toInt()) }; wrap.addView(edit); addButton(wrap,"Save note") { saveNote(edit.text.toString()); closePanel(); showPanel("side") }
        val p=params(min(dp(340),resources.displayMetrics.widthPixels-dp(24)),dp(280),Gravity.CENTER,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        closePanel(); panel=wrap; panelKind="notes"; try{wm.addView(wrap,p)}catch(_:Exception){}
    }
    private fun buildApps(root: LinearLayout) {
        val search=EditText(this).apply { hint="Search installed apps"; setSingleLine(true) }; root.addView(search)
        val scroll=ScrollView(this); val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; scroll.addView(list); root.addView(scroll)
        val apps=packageManager.getInstalledApplications(0).filter { packageManager.getLaunchIntentForPackage(it.packageName)!=null }.sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
        fun populate(q:String) { list.removeAllViews(); apps.filter { packageManager.getApplicationLabel(it).toString().contains(q,true) }.take(100).forEach { ai -> addButton(list,packageManager.getApplicationLabel(ai).toString()) { launchPackage(ai.packageName) } } }
        populate(""); search.addTextChangedListener(SimpleWatcher { populate(it) })
    }
    private fun buildMedia(root: LinearLayout) {
        addButton(root,"Previous track") { audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)); audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)) }
        addButton(root,"Play / Pause") { audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)); audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) }
        addButton(root,"Next track") { audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_MEDIA_NEXT)); audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_MEDIA_NEXT)) }
        addButton(root,"Volume down") { performAction("volume_down") }; addButton(root,"Volume up") { performAction("volume_up") }
        addButton(root,"Open sound settings") { startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    private fun buildTimer(root: LinearLayout) {
        val label=TextView(this).apply { text="Choose a timer"; textSize=20f; setTextColor(Color.WHITE); gravity=Gravity.CENTER }
        root.addView(label)
        listOf(30,60,300).forEach { secs -> addButton(root,"${secs}s") { label.text="Timer running: ${secs}s"; handler.postDelayed({ label.text="Time is up!"; try { val v=getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator; if(Build.VERSION.SDK_INT>=26) v.vibrate(android.os.VibrationEffect.createOneShot(500,180)) else @Suppress("DEPRECATION") v.vibrate(500) } catch(_:Exception){} },secs*1000L) } }
    }
    private fun showCursor() {
        cursor?.let { try{wm.removeView(it)}catch(_:Exception){} }
        cursorX=resources.displayMetrics.widthPixels*0.55f; cursorY=resources.displayMetrics.heightPixels*0.45f
        val pad=TextView(this).apply { text="◎\nTouch pad"; gravity=Gravity.CENTER; setTextColor(Color.WHITE); background=shape(0xEE39445E.toInt(),dp(42).toFloat()); setOnTouchListener(object:View.OnTouchListener { var x=0f; var y=0f; override fun onTouch(v:View,e:MotionEvent):Boolean { when(e.action){ MotionEvent.ACTION_DOWN->{x=e.rawX;y=e.rawY;return true}; MotionEvent.ACTION_MOVE->{val dx=e.rawX-x;val dy=e.rawY-y;x=e.rawX;y=e.rawY;cursorX=(cursorX+dx*1.6f).coerceIn(0f,resources.displayMetrics.widthPixels.toFloat());cursorY=(cursorY+dy*1.6f).coerceIn(0f,resources.displayMetrics.heightPixels.toFloat());moveCursorTarget();return true}; MotionEvent.ACTION_UP->{ if(abs(e.rawX-x)+abs(e.rawY-y)<dp(8)) tapCursor(); return true } }; return true } }) }
        val p=params(dp(110),dp(110),Gravity.BOTTOM or Gravity.LEFT); p.x=dp(24);p.y=dp(90)
        cursor=pad; try{wm.addView(pad,p)}catch(_:Exception){}
        val target=TextView(this).apply { text="●"; textSize=26f; gravity=Gravity.CENTER; setTextColor(0xFFFFC857.toInt()); background=shape(0x55333333,dp(30).toFloat()); setOnClickListener{tapCursor()} }
        val tp=params(dp(48),dp(48),Gravity.TOP or Gravity.LEFT); tp.x=cursorX.toInt();tp.y=cursorY.toInt()
        cursorTarget=target; try{wm.addView(target,tp)}catch(_:Exception){}
        pad.setOnLongClickListener { closeCursor(); true }
    }
    private var cursorTarget: View?=null
    private fun moveCursorTarget() { cursorTarget?.let { val p=it.layoutParams as WindowManager.LayoutParams; p.x=cursorX.toInt();p.y=cursorY.toInt();try{wm.updateViewLayout(it,p)}catch(_:Exception){} } }
    private fun tapCursor() { val path=Path().apply{moveTo(cursorX,cursorY)}; val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,80)).build(); dispatchGesture(gesture,null,null) }
    private fun closeCursor() { cursor?.let{try{wm.removeView(it)}catch(_:Exception){}};cursorTarget?.let{try{wm.removeView(it)}catch(_:Exception){}};cursor=null;cursorTarget=null }
    private fun pretty(s:String)=s.replace('_',' ').replaceFirstChar{it.uppercase()}
    private fun openMainSettings(tab:String) { startActivity(Intent(this,MainActivity::class.java).putExtra("tab",tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
class SimpleWatcher(val changed:(String)->Unit): android.text.TextWatcher {
    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){changed(s?.toString().orEmpty())}
    override fun afterTextChanged(s:android.text.Editable?){}
}
