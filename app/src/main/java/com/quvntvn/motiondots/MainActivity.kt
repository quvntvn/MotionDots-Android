package com.example.motiondots

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.motiondots.overlay.OverlayService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val canDraw = remember { mutableStateOf(Settings.canDrawOverlays(this)) }

                LaunchedEffect(Unit) {
                    canDraw.value = Settings.canDrawOverlays(this@MainActivity)
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("MotionDots — MVP", style = MaterialTheme.typography.headlineSmall)

                    Text("Permission overlay: ${if (canDraw.value) "OK" else "NON"}")

                    Button(onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }) {
                        Text("Autoriser l’overlay")
                    }

                    Button(
                        enabled = canDraw.value,
                        onClick = {
                            startService(Intent(this@MainActivity, OverlayService::class.java))
                        }
                    ) {
                        Text("Démarrer l’overlay")
                    }

                    Button(
                        onClick = {
                            stopService(Intent(this@MainActivity, OverlayService::class.java))
                        }
                    ) {
                        Text("Arrêter l’overlay")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // juste pour rafraîchir le state si tu reviens des settings
    }
}