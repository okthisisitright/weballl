package com.halo.island

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout

/** A touch target in the separate test APK, never included in HALO's installable app. */
class TestProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val frame = FrameLayout(this)
        frame.setBackgroundColor(android.graphics.Color.rgb(240, 240, 240))
        val button =
            Button(this).apply {
                text = "Tap target"
                setOnClickListener { text = "Touch passed through" }
            }
        frame.addView(button, FrameLayout.LayoutParams(400, 180, Gravity.CENTER))
        setContentView(frame)
    }
}
