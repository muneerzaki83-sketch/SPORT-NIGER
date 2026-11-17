package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class FootballApiResponse(
    @Json(name = "matches") val matches: List<ApiMatchItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ApiMatchItem(
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "status") val status: String = "",
    @Json(name = "utcDate") val utcDate: String = "",
    @Json(name = "competition") val competition: ApiNamedEntity? = null,
    @Json(name = "homeTeam") val homeTeam: ApiNamedEntity? = null,
    @Json(name = "awayTeam") val awayTeam: ApiNamedEntity? = null,
    @Json(name = "score") val score: ApiScore? = null
)

@JsonClass(generateAdapter = true)
data class ApiNamedEntity(
    @Json(name = "name") val name: String = "",
    @Json(name = "shortName") val shortName: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiScore(
    @Json(name = "fullTime") val fullTime: ApiScoreLine? = null
)

@JsonClass(generateAdapter = true)
data class ApiScoreLine(
    @Json(name = "home") val home: Int? = null,
    @Json(name = "away") val away: Int? = null
)

interface FootballDataRetrofitApi {
    @GET("v4/matches")
    suspend fun getMatches(
        @Header("X-Auth-Token") apiKey: String,
        @Query("status") status: String? = null
    ): FootballApiResponse
}

sealed interface FootballApiFeedResult {
    data object NotConfigured : FootballApiFeedResult
    data class Available(val matches: List<ApiMatchItem>) : FootballApiFeedResult
    data class Unavailable(val reason: String) : FootballApiFeedResult
}

class FootballApiRepository {
    private val service: FootballDataRetrofitApi by lazy {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.football-data.org/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FootballDataRetrofitApi::class.java)
    }

    suspend fun fetchLiveMatches(): FootballApiFeedResult {
        val key = BuildConfig.FOOTBALL_DATA_API_KEY.trim()
        if (key.isBlank() || key == "MY_FOOTBALL_DATA_API_KEY" || key == "YOUR_FOOTBALL_DATA_API_KEY") {
            return FootballApiFeedResult.NotConfigured
        }
        return try {
            val response = service.getMatches(apiKey = key)
            FootballApiFeedResult.Available(response.matches)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FootballApiFeedResult.Unavailable(e.localizedMessage ?: "API request failed")
        }
    }
}
