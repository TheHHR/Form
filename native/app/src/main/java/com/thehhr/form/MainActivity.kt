package com.thehhr.form

import android.os.Bundle
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
        enableEdgeToEdge()
        setContent { FormApp(vm) }
    }

    override fun onResume() { super.onResume(); vm.onResume() }
    override fun onPause() { vm.onPause(); super.onPause() }
}
