package com.stopaddict.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stopaddict.app.Graph
import kotlinx.coroutines.launch

/**
 * Aiguillage après authentification : si l'utilisateur a déjà un appareil associé,
 * on va au tableau de bord ; sinon, à l'association.
 */
class GateViewModel : ViewModel() {
    fun resolve(onHasDevice: () -> Unit, onNoDevice: () -> Unit) {
        viewModelScope.launch {
            val token = Graph.container.session.accessToken
            if (token == null) { onNoDevice(); return@launch }
            val hasDevice = runCatching { Graph.container.client.listDevices(token).isNotEmpty() }.getOrDefault(false)
            if (hasDevice) onHasDevice() else onNoDevice()
        }
    }
}

@Composable
fun GateScreen(onHasDevice: () -> Unit, onNoDevice: () -> Unit, vm: GateViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.resolve(onHasDevice, onNoDevice) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
