package com.stopaddict.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stopaddict.admin.ui.AdminNavHost
import com.stopaddict.core.designsystem.StopAddictTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        enableEdgeToEdge()
        setContent {
            StopAddictTheme {
                AdminNavHost()
            }
        }
    }
}
