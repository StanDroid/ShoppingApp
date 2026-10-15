package com.standroid.shoppingapp.domain.model

import java.math.BigDecimal

data class Product(
    val productId: Long,
    val productName: String,
    val salesPriceIncVat: BigDecimal,
    val productImage: String,
    val reviewSummary: ReviewSummary,
    val listPriceIncVat: BigDecimal? = null
)

data class ReviewSummary(val reviewAverage: Double, val reviewCount: Int)
