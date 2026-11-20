package com.thehhr.form

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.thehhr.form.ui.FormApp
import com.thehhr.form.ui.Media

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Media.hasVideos = assets.list("videos")?.isNotEmpty() == true
        // The app is dark-only: light status/nav bar icons regardless of the system theme.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        setContent { FormApp(vm) }
    }

    override fun onResume() { super.onResume(); vm.onResume() }
    override fun onPause() { vm.onPause(); super.onPause() }
}
