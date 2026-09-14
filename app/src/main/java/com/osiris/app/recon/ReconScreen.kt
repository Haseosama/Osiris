package com.osiris.app.recon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val isCurrentQueryWatched by viewModel.isCurrentQueryWatched.collectAsStateWithLifecycle()
    var showWatchlist by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RECON") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    BadgedBox(badge = { if (watchlist.isNotEmpty()) Badge { Text(watchlist.size.toString()) } }) {
                        IconButton(onClick = { showWatchlist = true }) {
                            Icon(Icons.Filled.Bookmark, contentDescription = "Watchlist")
                        }
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

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = viewModel::runQuery, enabled = !isLoading) {
                    Text("Rechercher")
                }
                // Needs a result to hash as the watchlist baseline — see ReconViewModel.toggleWatch.
                if (result != null) {
                    IconButton(onClick = viewModel::toggleWatch) {
                        Icon(
                            if (isCurrentQueryWatched) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (isCurrentQueryWatched) "Retirer de la watchlist" else "Ajouter à la watchlist",
                            tint = if (isCurrentQueryWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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

    if (showWatchlist) {
        WatchlistDialog(
            entries = watchlist,
            onRemove = viewModel::removeFromWatchlist,
            onDismiss = { showWatchlist = false },
        )
    }
}

/** Lists every saved [WatchlistEntry] with a way to remove it — see [ReconScreen]'s star toggle
 * for how one gets added. No "edit" here: changing a watched query's target is just removing it
 * and re-adding the new one from the search screen, not a distinct flow worth its own UI. */
@Composable
private fun WatchlistDialog(entries: List<WatchlistEntry>, onRemove: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watchlist RECON") },
        text = {
            if (entries.isEmpty()) {
                Text("Aucune requête surveillée pour l'instant. Lance une recherche puis appuie sur l'étoile pour la surveiller en arrière-plan.")
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                ) {
                    entries.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(entry.tool.label, style = MaterialTheme.typography.labelSmall)
                                Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { onRemove(entry.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Retirer de la watchlist")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

@Composable
private fun ReconResultView(result: ReconResult, modifier: Modifier = Modifier) {
    when (result) {
        is ReconResult.Cve -> CveResultView(result.data, modifier)
        is ReconResult.Dns -> DnsResultView(result.data, modifier)
        is ReconResult.IpIntel -> IpIntelResultView(result.data, modifier)
        is ReconResult.Sanctions -> SanctionsResultView(result.data, modifier)
        is ReconResult.Whois -> WhoisResultView(result.data, modifier)
        is ReconResult.CryptoWallet -> CryptoWalletResultView(result.data, modifier)
        is ReconResult.Username -> UsernameResultView(result.data, modifier)
        is ReconResult.Leaks -> LeaksResultView(result.data, modifier)
        is ReconResult.Github -> GithubResultView(result.data, modifier)
        is ReconResult.Phone -> PhoneResultView(result.data, modifier)
        is ReconResult.Mac -> MacResultView(result.data, modifier)
        is ReconResult.Raw -> JsonTreeView(result.json, modifier)
    }
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
private fun WhoisResultView(data: WhoisResult, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.error != null) {
            Text(data.error, color = MaterialTheme.colorScheme.error)
        } else {
            Text(data.domain ?: "", style = MaterialTheme.typography.titleMedium)
            LabeledLine("Enregistré le", data.registration.orEmpty())
            LabeledLine("Expire le", data.expiration.orEmpty())
            LabeledLine("Dernière modif.", data.lastChanged.orEmpty())

            data.rdap?.let { rdap ->
                if (rdap.nameservers.isNotEmpty()) LabeledLine("Nameservers", rdap.nameservers.joinToString())
                val orgsAndNames = rdap.entities.mapNotNull { it.org ?: it.name }
                if (orgsAndNames.isNotEmpty()) LabeledLine("Registrant / Org", orgsAndNames.joinToString())
            }

            data.securityScore?.let { score ->
                Text(
                    "Sécurité HTTP : ${score.grade ?: "?"} (${score.score}/${score.max})",
                    style = MaterialTheme.typography.labelMedium,
                    color = severityColor(
                        when (score.grade?.uppercase()) {
                            "A" -> "LOW"
                            "B" -> "MEDIUM"
                            else -> "HIGH"
                        }
                    ),
                )
            }
            data.http?.let { http ->
                LabeledLine("HTTP", listOfNotNull(http.status?.toString(), http.finalUrl).joinToString(" → "))
            }

            data.sanctionsMatch?.hits?.forEach { hit ->
                SectionTitle("⚠ Sanctions — ${hit.matchedValue}")
                hit.entries.forEach { entry -> SanctionEntryRow(entry) }
            }
        }
    }
}

@Composable
private fun CryptoWalletResultView(data: CryptoWalletResult, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.error != null) {
            Text(data.error, color = MaterialTheme.colorScheme.error)
        } else {
            Text(data.address ?: "", style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            Text(data.chainLabel ?: data.chain ?: "", style = MaterialTheme.typography.labelMedium)

            data.balance?.let { balance ->
                LabeledLine(
                    "Solde",
                    "${balance.native} ${data.symbol ?: ""}".trim() + (balance.usd?.let { " (~$it USD)" } ?: ""),
                )
            }
            data.activity?.let { activity ->
                LabeledLine("Transactions", activity.txCount.toString())
                LabeledLine("Première / dernière", listOfNotNull(activity.firstSeen, activity.lastSeen).joinToString(" → "))
            }
            data.flow?.let { flow ->
                LabeledLine("Flux", "reçu ${flow.totalIn} / envoyé ${flow.totalOut} (net ${flow.net})")
            }

            data.risk?.let { risk ->
                Text(
                    "Risque : ${risk.level ?: "?"} (${risk.score}/100)",
                    style = MaterialTheme.typography.labelMedium,
                    color = severityColor(risk.level),
                )
                risk.factors.forEach { factor ->
                    Text(
                        "• ${factor.label ?: factor.code ?: "?"} — ${factor.detail ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = severityColor(factor.severity),
                    )
                }
            }

            if (data.sanctions?.hit == true) {
                SectionTitle("⚠ Sanctions OFAC")
                data.sanctions.entries.forEach { entry -> SanctionEntryRow(entry) }
            }

            if (data.counterparties.isNotEmpty()) {
                SectionTitle("Contreparties")
                data.counterparties.take(10).forEach { cp ->
                    Text(
                        "${cp.address} (${cp.direction}, ${cp.txs} tx, ${cp.value})",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            if (data.transactions.isNotEmpty()) {
                SectionTitle("Transactions récentes")
                data.transactions.take(10).forEach { tx ->
                    Text(
                        "${tx.hash.take(16)}…  ${tx.direction}  ${tx.value}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsernameResultView(data: UsernameScanResult, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.error != null) {
            Text(data.error, color = MaterialTheme.colorScheme.error)
        } else {
            Text(data.username ?: "", style = MaterialTheme.typography.titleMedium)
            Text(
                "${data.found.size} trouvé(s) sur ${data.checked} site(s) vérifiés",
                style = MaterialTheme.typography.labelMedium,
                color = if (data.found.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (data.found.isNotEmpty()) {
                SectionTitle("Comptes trouvés")
                data.found.forEach { hit -> LabeledLine(hit.site ?: "?", hit.url.orEmpty()) }
            }
            if (data.inconclusive.isNotEmpty()) {
                SectionTitle("Non concluant (${data.inconclusive.size})")
                Text(data.inconclusive.joinToString { it.site ?: "?" }, style = MaterialTheme.typography.bodySmall)
            }
            if (data.blocked.isNotEmpty()) {
                SectionTitle("Site bloqué la vérification (${data.blocked.size})")
                Text(data.blocked.joinToString { it.site ?: "?" }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LeaksResultView(data: LeaksResult, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.error != null) {
            Text(data.error, color = MaterialTheme.colorScheme.error)
        } else {
            Text(data.email ?: "", style = MaterialTheme.typography.titleMedium)
            Text(
                if (data.breached) "⚠ Présent dans ${data.breaches.size} fuite(s)" else "Aucune fuite connue",
                style = MaterialTheme.typography.labelMedium,
                color = if (data.breached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (data.breaches.isNotEmpty()) {
                SectionTitle("Sites concernés")
                Text(data.breaches.joinToString(), style = MaterialTheme.typography.bodySmall)
            }
            if (data.dataExposed.isNotEmpty()) {
                SectionTitle("Données exposées")
                Text(data.dataExposed.joinToString(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun GithubResultView(data: GithubResult, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.error != null) {
            Text(data.error, color = MaterialTheme.colorScheme.error)
        } else {
            Text(data.name ?: data.username ?: "", style = MaterialTheme.typography.titleMedium)
            data.bio?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            LabeledLine("Entreprise", data.company.orEmpty())
            LabeledLine("Localisation", data.location.orEmpty())
            LabeledLine("Blog", data.blog.orEmpty())
            LabeledLine("Email public", data.email.orEmpty())
            LabeledLine("Twitter", data.twitter.orEmpty())
            Text(
                "${data.publicRepos ?: 0} dépôts publics · ${data.followers ?: 0} followers",
                style = MaterialTheme.typography.labelMedium,
            )
            LabeledLine("Compte créé le", data.createdAt.orEmpty())
            if (data.recentRepos.isNotEmpty()) {
                SectionTitle("Dépôts récents")
                data.recentRepos.forEach { repo ->
                    Text(
                        listOfNotNull(repo.name, repo.language).joinToString(" — "),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneResultView(data: PhoneResult, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.error != null) {
            Text(data.error, color = MaterialTheme.colorScheme.error)
        } else {
            Text(data.international ?: data.number ?: "", style = MaterialTheme.typography.titleMedium)
            Text(
                if (data.valid) "Numéro valide" else "Numéro invalide",
                style = MaterialTheme.typography.labelMedium,
                color = if (data.valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            LabeledLine("National", data.national.orEmpty())
            LabeledLine("Indicatif", data.countryCode.orEmpty())
            LabeledLine("Région", data.region.orEmpty())
            LabeledLine("Type de ligne", data.lineType.orEmpty())
        }
    }
}

@Composable
private fun MacResultView(data: MacResult, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.error != null) {
            Text(data.error, color = MaterialTheme.colorScheme.error)
        } else {
            Text(data.mac ?: "", style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            LabeledLine("Fabricant", data.vendor.orEmpty())
            LabeledLine("Adresse", data.address.orEmpty())
            LabeledLine("Préfixe", data.prefix.orEmpty())
        }
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
