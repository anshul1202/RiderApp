package com.example.riderapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.riderapp.presentation.navigation.RiderNavGraph
import com.example.riderapp.ui.theme.RiderAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RiderAppTheme {
                val navController = rememberNavController()
                RiderNavGraph(navController = navController)
            }
        }
    }
}
