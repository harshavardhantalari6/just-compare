package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.AppViewModel
import com.example.ui.PriceCompareApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Instantiate ViewModel cleanly passing Application context
        val viewModel = AppViewModel(application)
        
        setContent {
            MyApplicationTheme {
                PriceCompareApp(viewModel = viewModel)
            }
        }
    }
}
