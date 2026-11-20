package com.thehhr.form

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thehhr.form.ui.FormTheme

/** Health Connect requires this screen: it explains what Form reads/writes when the user asks why. */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FormTheme(FormStore.get(this).data.prefs.accent) {
                Scaffold { pad ->
                    Column(Modifier.fillMaxSize().padding(pad).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Form and Health Connect", style = MaterialTheme.typography.headlineMediumEmphasized)
                        Text("If you turn it on in Settings, Form writes each finished workout (as a strength-training session) " +
                            "and each meal you log (calories, protein, carbs, fat) to Health Connect, and reads your latest body weight " +
                            "when you tap \"Import weight\".")
                        Text("Nothing is sent anywhere else. Your training and nutrition data stay in your own Form folder and in Health Connect on this phone.")
                        Button(onClick = ::finish) { Text("Close") }
                    }
                }
            }
        }
    }
}
