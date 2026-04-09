package com.athena.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.athena.client.ui.navigation.AthenaNavHost
import com.athena.client.ui.theme.AthenaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AthenaTheme {
                AthenaNavHost()
            }
        }
    }
}
