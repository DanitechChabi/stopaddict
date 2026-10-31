package com.stopaddict.app.ui.pairing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PairingScreen(onPaired: () -> Unit, vm: PairingViewModel = viewModel()) {
    val s = vm.state
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Associer un administrateur", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Saisissez le code fourni par la personne de confiance qui supervisera " +
                "votre protection.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(20.dp))

        if (s.admin == null) {
            OutlinedTextField(
                value = s.code, onValueChange = vm::onCode,
                label = { Text("Code d'association") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            s.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = vm::redeem, enabled = !s.loading && s.code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                if (s.loading) CircularProgressIndicator(Modifier.height(18.dp)) else Text("Valider le code")
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Administrateur", style = MaterialTheme.typography.titleSmall)
                    Text(s.admin.display_name ?: s.admin.email ?: "—", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Confirmez que vous reconnaissez cette personne. Elle supervisera votre " +
                            "engagement, sans jamais voir vos contenus privés.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onPaired, modifier = Modifier.fillMaxWidth()) {
                Text("Je confirme, continuer")
            }
        }
    }
}
