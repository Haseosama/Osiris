package com.osiris.app.data.remote

import com.osiris.app.data.model.ConflictsResponse
import com.osiris.app.data.model.EarthquakesResponse
import com.osiris.app.data.model.FiresResponse
import com.osiris.app.data.model.FlightsResponse
import com.osiris.app.data.model.WeatherResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET

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
}
