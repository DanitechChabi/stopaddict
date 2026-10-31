package com.stopaddict.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stopaddict.app.ui.WelcomeScreen
import com.stopaddict.core.designsystem.StopAddictTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StopAddictTheme {
                // Point d'entrée ; la navigation (association, permissions,
                // tableau de bord) s'ajoute aux écrans suivants.
                WelcomeScreen(onStart = { /* TODO: navigation vers l'association */ })
            }
        }
    }
}
