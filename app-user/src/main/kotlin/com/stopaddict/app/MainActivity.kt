package com.stopaddict.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stopaddict.app.ui.UserNavHost
import com.stopaddict.core.designsystem.StopAddictTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(
            url = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY,
            signingPublicKey = BuildConfig.SIGNING_PUBLIC_KEY_SPKI_B64,
        )
        enableEdgeToEdge()
        setContent {
            StopAddictTheme {
                UserNavHost()
            }
        }
    }
}
