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
import androidx.compose.material3.HorizontalDivider
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
    val result by viewModel.result.collectAsStateWithLifecycle()
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
            result?.let { r ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    ReconResultView(r, modifier = Modifier.padding(12.dp).fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun ReconResultView(result: ReconResult, modifier: Modifier = Modifier) {
    when (result) {
        is ReconResult.Cve -> CveResultView(result.data, modifier)
        is ReconResult.Dns -> DnsResultView(result.data, modifier)
        is ReconResult.IpIntel -> IpIntelResultView(result.data, modifier)
        is ReconResult.Sanctions -> SanctionsResultView(result.data, modifier)
        is ReconResult.Raw -> RawResultView(result.json, modifier)
    }
}

@Composable
private fun RawResultView(json: String, modifier: Modifier = Modifier) {
    Text(
        json,
        modifier = modifier.verticalScroll(rememberScrollState()),
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun CveResultView(data: CveResult, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.error != null) {
            Text(data.error, color = MaterialTheme.colorScheme.error)
        } else {
            Text(data.id ?: "CVE", style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    data.severity,
                    data.cvss?.let { "CVSS $it" },
                    data.cwe,
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = severityColor(data.severity),
            )
            data.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (data.affected.isNotEmpty()) {
                SectionTitle("Produits affectés")
                data.affected.forEach { affected ->
                    val versions = affected.versions.joinToString().ifBlank { "toutes versions" }
                    Text("${affected.vendor ?: "?"} — ${affected.product ?: "?"} ($versions)", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (data.references.isNotEmpty()) {
                SectionTitle("Références")
                data.references.forEach { ref ->
                    Text(ref, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun DnsResultView(data: DnsResult, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.error != null) {
            Text(data.error, color = MaterialTheme.colorScheme.error)
        } else {
            Text(data.domain ?: "", style = MaterialTheme.typography.titleMedium)
            data.summary?.let { summary ->
                if (summary.ipAddresses.isNotEmpty()) LabeledLine("IP", summary.ipAddresses.joinToString())
                if (summary.mailServers.isNotEmpty()) LabeledLine("MX", summary.mailServers.joinToString())
                if (summary.nameservers.isNotEmpty()) LabeledLine("NS", summary.nameservers.joinToString())
            }
            HorizontalDivider()
            data.records.filterValues { it.isNotEmpty() }.forEach { (type, records) ->
                SectionTitle(type)
                records.forEach { record ->
                    Text(record.data ?: "?", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun IpIntelResultView(data: IpIntelResult, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.error != null) {
            Text(data.error, color = MaterialTheme.colorScheme.error)
        } else {
            Text(data.ip ?: "", style = MaterialTheme.typography.titleMedium)
            data.geo?.let { geo ->
                LabeledLine("Localisation", listOfNotNull(geo.city, geo.region, geo.country).joinToString(", "))
                LabeledLine("FAI / Org", listOfNotNull(geo.isp, geo.org).joinToString(" — "))
                LabeledLine("ASN", listOfNotNull(geo.asNumber, geo.asName).joinToString(" "))
            }
            data.reputation?.let { rep ->
                Text(
                    "Risque : ${rep.riskLevel ?: "?"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = severityColor(rep.riskLevel),
                )
                val flags = listOfNotNull(
                    "proxy".takeIf { rep.isProxy },
                    "hébergement".takeIf { rep.isHosting },
                    "mobile".takeIf { rep.isMobile },
                )
                if (flags.isNotEmpty()) Text(flags.joinToString(), style = MaterialTheme.typography.bodySmall)
            }
            data.sanctionsMatch?.hits?.forEach { hit ->
                SectionTitle("⚠ Sanctions — ${hit.matchedValue}")
                hit.entries.forEach { entry -> SanctionEntryRow(entry) }
            }
        }
    }
}

@Composable
private fun SanctionsResultView(data: SanctionsResult, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.error != null) {
            Text(data.error, color = MaterialTheme.colorScheme.error)
        } else {
            Text("${data.total} résultat(s)", style = MaterialTheme.typography.titleMedium)
            data.matches.forEach { entry -> SanctionEntryRow(entry) }
        }
    }
}

@Composable
private fun SanctionEntryRow(entry: SanctionEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(entry.name, style = MaterialTheme.typography.bodyMedium)
        Text(
            listOfNotNull(
                entry.schema,
                entry.countries.joinToString().ifBlank { null },
                entry.programs.joinToString().ifBlank { null },
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun LabeledLine(label: String, value: String) {
    if (value.isBlank()) return
    Text("$label : $value", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun severityColor(level: String?) = when (level?.uppercase()) {
    "CRITICAL", "HIGH" -> MaterialTheme.colorScheme.error
    "MEDIUM", "ELEVATED" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
