package com.standroid.shoppingapp.data.remote.dto

import java.math.BigDecimal

internal data class SearchResponseDto(
    val products: List<ProductDto?>? = null,
    val currentPage: Int? = null,
    val pageCount: Int? = null
)

internal data class ProductDto(
    val productId: Long? = null,
    val productName: String? = null,
    val productImage: String? = null,
    val salesPriceIncVat: BigDecimal? = null,
    val listPriceIncVat: BigDecimal? = null,
    val reviewInformation: ReviewInformationDto? = null
)

internal data class ReviewInformationDto(val reviewSummary: ReviewSummaryDto? = null)

internal data class ReviewSummaryDto(
    val reviewAverage: Double? = null,
    val reviewCount: Int? = null
)
