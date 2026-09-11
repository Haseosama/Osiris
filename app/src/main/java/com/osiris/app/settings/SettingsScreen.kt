package com.osiris.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.osiris.app.map.MapLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val savedUrl by viewModel.backendUrl.collectAsStateWithLifecycle()
    val testState by viewModel.connectionTestState.collectAsStateWithLifecycle()
    val testError by viewModel.connectionError.collectAsStateWithLifecycle()
    val pollIntervals by viewModel.pollIntervals.collectAsStateWithLifecycle()

    var urlField by remember { mutableStateOf(savedUrl) }
    LaunchedEffect(savedUrl) { urlField = savedUrl }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Backend Osiris", style = MaterialTheme.typography.titleMedium)
            Text(
                "Osiris n'a pas de backend intégré — l'appli appelle l'API d'une instance " +
                    "que tu auto-héberges (docker compose up, voir DOCKER.md du dépôt Osiris). " +
                    "Renseigne son URL ci-dessous, ex: http://192.168.1.10:3000",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = urlField,
                onValueChange = { urlField = it },
                label = { Text("URL du backend") },
                placeholder = { Text("http://192.168.1.10:3000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { viewModel.saveBackendUrl(urlField) },
                    enabled = urlField.isNotBlank(),
                ) {
                    Text("Enregistrer")
                }
                OutlinedButton(
                    onClick = { viewModel.testConnection(urlField) },
                    enabled = urlField.isNotBlank() && testState != ConnectionTestState.TESTING,
                ) {
                    Text("Tester la connexion")
                }
            }

            when (testState) {
                ConnectionTestState.TESTING -> CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
                ConnectionTestState.SUCCESS -> Text(
                    "✓ Backend joignable",
                    color = MaterialTheme.colorScheme.primary,
                )
                ConnectionTestState.FAILED -> Text(
                    "✗ Échec : ${testError ?: "erreur inconnue"}",
                    color = MaterialTheme.colorScheme.error,
                )
                ConnectionTestState.IDLE -> Unit
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("Cadence de polling", style = MaterialTheme.typography.titleMedium)
            Text(
                "À quelle fréquence chaque couche interroge le backend. Un poll plus fréquent " +
                    "consomme plus de batterie/données ; un poll plus rare rend les données " +
                    "moins fraîches. Prend effet au prochain cycle, pas besoin de relancer l'appli.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(MapLayer.entries) { layer ->
                    PollIntervalRow(
                        layer = layer,
                        currentMs = pollIntervals[layer] ?: layer.pollIntervalMs,
                        onSelect = { viewModel.setPollInterval(layer, it) },
                    )
                }
            }
        }
    }
}

private val POLL_INTERVAL_PRESETS = listOf(
    15_000L, 30_000L, 60_000L, 2 * 60_000L, 5 * 60_000L, 10 * 60_000L, 15 * 60_000L, 20 * 60_000L, 30 * 60_000L,
)

private fun formatInterval(ms: Long): String =
    if (ms < 60_000L) "${ms / 1000}s" else "${ms / 60_000L} min"

@Composable
private fun PollIntervalRow(layer: MapLayer, currentMs: Long, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(layer.label, style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(formatInterval(currentMs))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                POLL_INTERVAL_PRESETS.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(formatInterval(preset)) },
                        onClick = {
                            onSelect(preset)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
