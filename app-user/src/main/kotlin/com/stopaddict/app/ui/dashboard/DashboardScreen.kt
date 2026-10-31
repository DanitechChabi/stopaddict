package com.stopaddict.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stopaddict.core.designsystem.StatusDanger
import com.stopaddict.core.designsystem.StatusOk
import com.stopaddict.core.designsystem.StatusWarn
import com.stopaddict.core.model.CommitmentProgress

@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val s = vm.state
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("StopAddict", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        when {
            s.loading -> CircularProgressIndicator()
            s.commitment == null -> {
                Text(s.error ?: "Aucun engagement actif.", color = MaterialTheme.colorScheme.onBackground)
            }
            else -> {
                val c = s.commitment

                // Décompte honnête : jamais de faux chiffre si le temps est inconnu.
                val (title, subtitle) = when (val p = s.progress) {
                    is CommitmentProgress.Running -> "${p.remainingDays} jours restants" to "sur ${c.duration_days} jours"
                    CommitmentProgress.Completed -> "Engagement terminé" to ""
                    CommitmentProgress.NotActivated -> "En attente d'activation" to ""
                    CommitmentProgress.Unknown -> "Synchronisation en attente" to ""
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                        c.end_at?.let { Text("Fin : $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }

                Spacer(Modifier.height(12.dp))
                SignatureBadge(s.signature)

                Spacer(Modifier.height(12.dp))
                // Statut de protection HONNÊTE : le moteur VPN/IA n'est pas encore
                // installé dans cette version — on ne prétend pas « protégé ».
                StatusCard(
                    color = StatusWarn,
                    title = "Filtrage non encore actif",
                    detail = "Le moteur de filtrage (VPN/DNS) et la détection IA seront activés " +
                        "dans une prochaine version. L'engagement et sa supervision fonctionnent déjà.",
                )

                Spacer(Modifier.height(16.dp))
                if (c.status == "pending_consent") {
                    Text("Un engagement vous est proposé.", style = MaterialTheme.typography.titleSmall)
                    Text("Durée : ${c.duration_days} jours", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::consent, enabled = !s.working, modifier = Modifier.fillMaxWidth()) {
                        Text("Je consens et j'active la protection")
                    }
                } else if (c.status == "active") {
                    Text("Libération anticipée", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.requestRelease("standard") }, enabled = !s.working) {
                            Text("Demander à l'admin")
                        }
                        OutlinedButton(onClick = { vm.requestRelease("emergency") }, enabled = !s.working) {
                            Text("Urgence (72 h)")
                        }
                    }
                }

                s.message?.let { Spacer(Modifier.height(16.dp)); Text(it, color = MaterialTheme.colorScheme.secondary) }
                s.error?.let { Spacer(Modifier.height(16.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun SignatureBadge(status: SignatureStatus) {
    val (color, text) = when (status) {
        SignatureStatus.VALID -> StatusOk to "Engagement vérifié (signature authentique)"
        SignatureStatus.INVALID -> StatusDanger to "Signature invalide — ne pas faire confiance"
        SignatureStatus.UNVERIFIABLE -> StatusWarn to "Signature non vérifiable (clé publique non configurée)"
    }
    StatusCard(color = color, title = text, detail = null)
}

@Composable
private fun StatusCard(color: Color, title: String, detail: String?) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
