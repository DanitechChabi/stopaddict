package com.stopaddict.admin.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stopaddict.core.network.dto.DeviceDto

@Composable
fun DashboardScreen(
    onNewInvitation: () -> Unit,
    onOpenDevice: (String) -> Unit,
    onOpenReleases: () -> Unit,
    vm: DashboardViewModel = viewModel(),
) {
    val s = vm.state
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Tableau de bord", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Appareils", s.devices.size.toString(), Modifier.weight(1f))
                StatCard("Engagements actifs", s.activeCommitments.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth().clickable { onOpenReleases() }) {
                Column(Modifier.padding(16.dp)) {
                    Text("Demandes de libération en attente", style = MaterialTheme.typography.bodyMedium)
                    Text(s.pendingReleases.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
            ElevatedButton(onClick = onNewInvitation, modifier = Modifier.fillMaxWidth()) {
                Text("Associer un nouvel appareil")
            }

            Spacer(Modifier.height(16.dp))
            Text("Appareils supervisés", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            when {
                s.loading -> CircularProgressIndicator()
                s.error != null -> Text(s.error, color = MaterialTheme.colorScheme.error)
                s.devices.isEmpty() -> Text("Aucun appareil associé pour l'instant.", color = MaterialTheme.colorScheme.onBackground)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.devices) { d -> DeviceRow(d) { onOpenDevice(d.id) } }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DeviceRow(d: DeviceDto, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(d.device_name, style = MaterialTheme.typography.titleSmall)
                Text("Mode : ${d.management_mode}", style = MaterialTheme.typography.bodySmall)
            }
            Text(d.device_status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
