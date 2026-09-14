package com.osiris.app.data.source

import com.osiris.app.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Thin GET helper shared by every direct-source client under `data/source/` and
 * `recon/source/` — these bypass the self-hosted Osiris backend entirely and hit each
 * upstream API straight from the phone (see the "no backend" migration plan). Not built on
 * Retrofit like [NetworkModule.apiFor] since there's no single base URL here, just one-off
 * GETs to a different host per source.
 */
object DirectHttp {

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.string() ?: error("Empty response body")
            }
        }

    suspend inline fun <reified T> getJson(url: String, headers: Map<String, String> = emptyMap()): T =
        NetworkModule.json.decodeFromString(getText(url, headers))
}
