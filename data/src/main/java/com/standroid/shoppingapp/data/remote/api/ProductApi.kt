package com.standroid.shoppingapp.data.remote.api

import com.standroid.shoppingapp.data.remote.dto.SearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

internal interface ProductApi {
    @GET("search")
    suspend fun search(@Query("que") query: String, @Query("page") page: Int): SearchResponseDto
}
