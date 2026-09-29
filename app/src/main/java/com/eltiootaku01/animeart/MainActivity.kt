package com.eltiootaku01.animeart

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = getString(R.string.initial_message)
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        setContentView(title)
    }
}
