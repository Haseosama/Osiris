package com.osiris.app.data.remote

import com.osiris.app.data.model.CctvResponse
import com.osiris.app.data.model.ConflictsResponse
import com.osiris.app.data.model.CyberAttacksResponse
import com.osiris.app.data.model.EarthquakesResponse
import com.osiris.app.data.model.FiresResponse
import com.osiris.app.data.model.FlightsResponse
import com.osiris.app.data.model.LiveNewsResponse
import com.osiris.app.data.model.MaritimeResponse
import com.osiris.app.data.model.OsintResponse
import com.osiris.app.data.model.SatellitesResponse
import com.osiris.app.data.model.WeatherResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

/** REST surface of a self-hosted Osiris backend that this client actually consumes. */
interface OsirisApi {

    @GET("api/health")
    suspend fun health(): Response<ResponseBody>

    @GET("api/flights")
    suspend fun flights(): FlightsResponse

    @GET("api/earthquakes")
    suspend fun earthquakes(): EarthquakesResponse

    @GET("api/fires")
    suspend fun fires(): FiresResponse

    @GET("api/weather")
    suspend fun weather(): WeatherResponse

    @GET("api/conflicts")
    suspend fun conflicts(): ConflictsResponse

    @GET("api/maritime")
    suspend fun maritime(): MaritimeResponse

    @GET("api/satellites")
    suspend fun satellites(): SatellitesResponse

    @GET("api/live-news")
    suspend fun liveNews(): LiveNewsResponse

    @GET("api/cyber-attacks")
    suspend fun cyberAttacks(): CyberAttacksResponse

    @GET("api/cctv")
    suspend fun cctv(): CctvResponse

    @GET("api/news")
    suspend fun osintNews(): OsintResponse

    /** Dynamic endpoint for the RECON toolkit (scanner, DNS, WHOIS, CVE, sanctions...) —
     * each tool's query string differs too much to justify a typed method per tool. */
    @GET
    suspend fun raw(@Url relativeUrl: String): Response<ResponseBody>
}
