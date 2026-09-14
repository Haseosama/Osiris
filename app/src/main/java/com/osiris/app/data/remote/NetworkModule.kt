@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.osiris.app.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds (and caches) an [OsirisApi] for whatever backend URL the user has configured in
 * Settings. The URL is only known at runtime — there is no fixed base URL to bake in at
 * build time since every user points this app at their own self-hosted Osiris instance.
 */
object NetworkModule {

    /** Shared by the `data/source/*` and `recon/source/*` direct-fetch clients too — no reason
     * for each to build its own [Json]/[OkHttpClient]. */
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var cachedBaseUrl: String? = null
    @Volatile private var cachedApi: OsirisApi? = null

    fun apiFor(baseUrl: String): OsirisApi {
        val normalized = normalize(baseUrl)
        cachedApi?.let { if (cachedBaseUrl == normalized) return it }

        val retrofit = Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(OsirisApi::class.java).also {
            cachedBaseUrl = normalized
            cachedApi = it
        }
    }

    private fun normalize(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return "$withScheme/"
    }
}
