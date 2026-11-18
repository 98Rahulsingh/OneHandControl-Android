package com.onehandcontrol.clone

import android.app.Activity
import android.os.Bundle
import android.widget.*
import android.view.Gravity
import android.content.Intent
import android.provider.Settings

class PanelPreviewActivity:Activity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
 val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24)}
 root.addView(TextView(this).apply{text="This panel is opened from the always-on accessibility edge service. Enable the service in app settings to use the floating overlay.";textSize=18f})
 root.addView(Button(this).apply{text="Accessibility settings";setOnClickListener{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}})
 setContentView(root)
 }
}