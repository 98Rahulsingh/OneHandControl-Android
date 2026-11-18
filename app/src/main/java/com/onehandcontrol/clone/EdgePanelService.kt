package com.onehandcontrol.clone

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

class EdgePanelService : Service() {
    private lateinit var wm: WindowManager
    private var handle: View? = null
    private var panel: View? = null
    private val channel = "edge_panel"
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "Edge panel", NotificationManager.IMPORTANCE_LOW))
            val n = Notification.Builder(this, channel).setContentTitle("One Hand Control active").setSmallIcon(android.R.drawable.ic_menu_view).build()
            startForeground(7, n)
        }
        showHandle()
    }
    private fun lp(type: Int, w: Int, h: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(w, h, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            x = 0
        }
    private fun showHandle() {
        val v = TextView(this).apply {
            text = "‹"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = bg(0xFF4656D9.toInt(), 48f)
            setOnClickListener { togglePanel() }
            setOnTouchListener(object : View.OnTouchListener {
                var down = 0L
                var sx = 0f
                override fun onTouch(view: View, e: MotionEvent): Boolean {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> { down = System.currentTimeMillis(); sx = e.rawX; return true }
                        MotionEvent.ACTION_UP -> {
                            val dx = e.rawX - sx
                            if (kotlin.math.abs(dx) > 45) togglePanel() else if (System.currentTimeMillis() - down > 550) togglePanel() else togglePanel()
                            return true
                        }
                    }
                    return true
                }
            })
        }
        handle = v
        wm.addView(v, lp(if (Build.VERSION.SDK_INT >= 26) 2038 else 2002, 42, 64))
    }
    private fun bg(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun togglePanel() { if (panel == null) showPanel() else hidePanel() }
    private fun hidePanel() { panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }; panel = null }
    private fun showPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 16, 14, 16)
            background = bg(0xFF171C27.toInt(), 28f)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "CONTROL"; textSize = 14f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply { text = "×"; setOnClickListener { hidePanel() } })
        root.addView(header)
        val content = FrameLayout(this)
        fun page(name: String): View {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(4, 12, 4, 4) }
            val title = TextView(this).apply { text = name; textSize = 18f; setTextColor(Color.WHITE) }
            box.addView(title)
            val items = when(name) {
                "Quick" -> listOf("Brightness (open system control)", "Volume", "Wi-Fi settings", "Bluetooth settings", "Settings")
                "Media" -> listOf("Media controls are provided by Android notification controls", "Open sound settings")
                "Groups" -> listOf("Favorites", "Work", "Tools", "+ Add group")
                else -> listOf("Phone", "Camera", "Browser", "Settings", "+ Add app")
            }
            items.forEach { label ->
                box.addView(Button(this).apply {
                    text = label
                    setOnClickListener {
                        when(label) {
                            "Settings" -> startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            "Wi-Fi settings" -> startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            "Bluetooth settings" -> startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            "Open sound settings" -> startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            else -> Toast.makeText(this@EdgePanelService, "$label — customize in a later build", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
            return box
        }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Apps", "Quick", "Groups", "Media").forEach { name ->
            tabs.addView(Button(this).apply {
                text = name
                setOnClickListener { content.removeAllViews(); content.addView(page(name)) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(tabs)
        content.addView(page("Apps"))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val p = lp(if (Build.VERSION.SDK_INT >= 26) 2038 else 2002, 300, 560).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            x = 8
        }
        panel = root
        wm.addView(root, p)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() { hidePanel(); handle?.let { try { wm.removeView(it) } catch (_: Exception) {} }; handle = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
