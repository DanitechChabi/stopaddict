package com.stopaddict.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.stopaddict.core.designsystem.GreenSoft
import com.stopaddict.core.designsystem.StopAddictTheme

/**
 * Écran de bienvenue (A). Présente StopAddict et le principe de l'administration
 * externe, puis mène à la configuration. Rien n'est affiché comme « protégé »
 * ici : la protection n'est pas encore configurée.
 */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(
                Modifier.size(72.dp).clip(CircleShape).background(GreenSoft),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "StopAddict",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Une protection volontaire contre les contenus pornographiques, " +
                    "supervisée par une personne de confiance.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Transparent : aucun de vos contenus privés n'est enregistré " +
                    "ni transmis. L'administrateur ne voit que l'état de la protection.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onStart) {
                Text("Configurer la protection")
            }
        }
    }
}

@Preview
@Composable
private fun WelcomePreview() {
    StopAddictTheme { WelcomeScreen(onStart = {}) }
}
