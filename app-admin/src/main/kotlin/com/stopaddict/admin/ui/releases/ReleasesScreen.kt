package com.stopaddict.admin.ui.releases

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ReleasesScreen(vm: ReleasesViewModel = viewModel()) {
    val s = vm.state
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Demandes de libération", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        when {
            s.loading -> CircularProgressIndicator()
            s.error != null -> Text(s.error, color = MaterialTheme.colorScheme.error)
            s.requests.isEmpty() -> Text("Aucune demande en attente.", color = MaterialTheme.colorScheme.onBackground)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.requests) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                if (r.kind == "emergency") "Demande d'urgence" else "Demande standard",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (r.kind == "emergency") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            r.reason_category?.let { Text("Motif : $it", style = MaterialTheme.typography.bodyMedium) }
                            r.auto_release_at?.let { Text("Libération auto le : $it", style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { vm.review(r.id, "approved") },
                                    enabled = s.working == null,
                                ) { Text("Accepter") }
                                OutlinedButton(
                                    onClick = { vm.review(r.id, "refused") },
                                    enabled = s.working == null,
                                ) { Text("Refuser") }
                                OutlinedButton(
                                    onClick = { vm.review(r.id, "needs_confirmation") },
                                    enabled = s.working == null,
                                ) { Text("Confirmation") }
                            }
                        }
                    }
                }
            }
        }
    }
}
