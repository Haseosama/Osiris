package com.osiris.app.recon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconScreen(onBack: () -> Unit, viewModel: ReconViewModel = viewModel()) {
    val selectedTool by viewModel.selectedTool.collectAsStateWithLifecycle()
    val inputValue by viewModel.inputValue.collectAsStateWithLifecycle()
    val secondaryValue by viewModel.secondaryValue.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val resultText by viewModel.resultText.collectAsStateWithLifecycle()
    val errorText by viewModel.errorText.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RECON") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReconTool.entries) { tool ->
                    FilterChip(
                        selected = tool == selectedTool,
                        onClick = { viewModel.selectTool(tool) },
                        label = { Text(tool.label) },
                    )
                }
            }

            if (selectedTool.paramName.isNotEmpty()) {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = viewModel::updateInput,
                    label = { Text(selectedTool.hint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            selectedTool.secondaryParam?.let { secondary ->
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(secondary.options) { option ->
                        FilterChip(
                            selected = option == secondaryValue,
                            onClick = { viewModel.updateSecondary(option) },
                            label = { Text(option) },
                        )
                    }
                }
            }

            Button(onClick = viewModel::runQuery, enabled = !isLoading) {
                Text("Rechercher")
            }

            if (isLoading) {
                CircularProgressIndicator()
            }
            errorText?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            resultText?.let { json ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Text(
                        json,
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
