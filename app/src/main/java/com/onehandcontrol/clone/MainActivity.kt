package com.onehandcontrol.clone

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            setBackgroundColor(0xFFF4F5F8.toInt())
        }
        root.addView(TextView(this).apply {
            text = "ONE HAND CONTROL\nEdge Panel • Gestures • Quick Tools"
            textSize = 22f
            setTextColor(0xFF18202B.toInt())
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "\nAllow “Display over other apps” to show the floating edge handle.\n\nThis starter clone is offline-first; no Internet permission is requested."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFF596273.toInt())
        })
        root.addView(Button(this).apply {
            text = "Grant overlay permission"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else startPanel()
            }
        })
        root.addView(Button(this).apply {
            text = "Start / Stop Edge Panel"
            setOnClickListener { if (Settings.canDrawOverlays(this@MainActivity)) startPanel() else startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        })
        setContentView(root)
    }
    private fun startPanel() {
        val i = Intent(this, EdgePanelService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }
}
