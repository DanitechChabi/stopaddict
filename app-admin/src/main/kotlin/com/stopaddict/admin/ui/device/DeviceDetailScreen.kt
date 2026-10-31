package com.stopaddict.admin.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceDetailScreen(deviceId: String) {
    val vm: DeviceViewModel = viewModel(
        key = deviceId,
        factory = viewModelFactory { initializer { DeviceViewModel(deviceId) } },
    )
    val s = vm.state

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Appareil", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        when {
            s.loading -> CircularProgressIndicator()
            s.device == null -> Text(s.error ?: "Appareil introuvable", color = MaterialTheme.colorScheme.error)
            else -> {
                val d = s.device
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(d.device_name, style = MaterialTheme.typography.titleMedium)
                        Text("Statut : ${d.device_status}", style = MaterialTheme.typography.bodyMedium)
                        Text("Mode : ${d.management_mode}", style = MaterialTheme.typography.bodyMedium)
                        d.last_sync_at?.let { Text("Dernière synchro : $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }

                Spacer(Modifier.height(20.dp))
                val c = s.commitment
                if (c != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Engagement en cours", style = MaterialTheme.typography.titleSmall)
                            Text("Statut : ${c.status}", style = MaterialTheme.typography.bodyMedium)
                            Text("Durée : ${c.duration_days} jours", style = MaterialTheme.typography.bodyMedium)
                            c.end_at?.let { Text("Fin : $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                } else {
                    Text("Proposer un engagement", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.durations.forEach { days ->
                            FilterChip(
                                selected = false,
                                enabled = !s.proposing,
                                onClick = { vm.propose(days) },
                                label = { Text("$days j") },
                            )
                        }
                    }
                    Text(
                        "L'engagement ne devient actif qu'après le consentement explicite de l'utilisateur.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                s.message?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.secondary)
                }
                s.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
