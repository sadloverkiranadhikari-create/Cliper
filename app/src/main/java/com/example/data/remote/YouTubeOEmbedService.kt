package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class YouTubeOEmbedResponse(
    val title: String,
    @Json(name = "author_name") val authorName: String,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null
)

interface YouTubeOEmbedApi {
    @GET("oembed")
    suspend fun getVideoInfo(
        @Query("url") videoUrl: String,
        @Query("format") format: String = "json"
    ): YouTubeOEmbedResponse
}

class YouTubeMetadataClient {
    private val api: YouTubeOEmbedApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl("https://www.youtube.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YouTubeOEmbedApi::class.java)
    }

    suspend fun fetchMetadata(canonicalUrl: String): Result<YouTubeOEmbedResponse> {
        return try {
            Result.success(api.getVideoInfo(videoUrl = canonicalUrl))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
