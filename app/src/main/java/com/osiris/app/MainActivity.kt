package com.osiris.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.osiris.app.navigation.OsirisNavGraph
import com.osiris.app.ui.theme.OsirisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OsirisTheme {
                OsirisNavGraph()
            }
        }
    }
}
