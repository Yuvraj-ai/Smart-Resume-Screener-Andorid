package com.yuvraj.resumescreener

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yuvraj.resumescreener.ui.ResumeScreenerApp
import com.yuvraj.resumescreener.ui.theme.ResumeScreenerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ResumeScreenerTheme {
                ResumeScreenerApp()
            }
        }
    }
}
