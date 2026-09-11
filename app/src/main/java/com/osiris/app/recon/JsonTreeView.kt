package com.osiris.app.recon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Renders arbitrary JSON as an indented key/value tree instead of one flat monospace blob —
 * used for the RECON tools with no dedicated DTO (scanner, SSL certs, space weather) since
 * their schemas are either externally-owned (scanner proxies a separate microservice) or not
 * worth locking into a typed model. Falls back to plain monospace text if the string isn't
 * valid JSON at all.
 */
@Composable
fun JsonTreeView(json: String, modifier: Modifier = Modifier) {
    val element = remember(json) { runCatching { Json.parseToJsonElement(json) }.getOrNull() }
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        if (element != null) {
            JsonNode(name = null, element = element, depth = 0)
        } else {
            Text(json, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun JsonNode(name: String?, element: JsonElement, depth: Int) {
    val indent = (depth * 12).dp
    when (element) {
        is JsonObject -> {
            name?.let { JsonKey(it, indent) }
            element.entries.forEach { (key, value) -> JsonNode(key, value, depth + 1) }
        }
        is JsonArray -> JsonArrayNode(name, element, depth, indent)
        // JsonNull is itself a JsonPrimitive (object JsonNull : JsonPrimitive()), so this
        // branch must come first — an `is JsonPrimitive` check above it would shadow it and
        // "null" would print as the literal string "null" instead of the placeholder.
        JsonNull -> if (name != null) JsonLeaf(name, "—", indent)
        is JsonPrimitive -> if (name != null) JsonLeaf(name, element.content, indent)
    }
}

@Composable
private fun JsonArrayNode(name: String?, array: JsonArray, depth: Int, indent: Dp) {
    val isFlatPrimitives = array.all { it is JsonPrimitive } // JsonNull is a JsonPrimitive too
    when {
        array.isEmpty() -> if (name != null) JsonLeaf(name, "[]", indent)
        isFlatPrimitives -> {
            val text = array.joinToString { (it as? JsonPrimitive)?.content ?: "—" }
            if (name != null) JsonLeaf(name, text, indent) else Text(text, modifier = Modifier.padding(start = indent))
        }
        else -> {
            name?.let { JsonKey("$it [${array.size}]", indent) }
            array.forEachIndexed { index, item -> JsonNode("#$index", item, depth + 1) }
        }
    }
}

@Composable
private fun JsonKey(text: String, indent: Dp) {
    Text(
        text,
        modifier = Modifier.padding(start = indent, top = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun JsonLeaf(label: String, value: String, indent: Dp) {
    Row(modifier = Modifier.padding(start = indent), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
