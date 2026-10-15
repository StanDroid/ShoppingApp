package com.standroid.shoppingapp.feature.search.model

import com.standroid.shoppingapp.domain.model.Product
import java.math.BigDecimal

internal const val PRICE_CURRENCY_CODE = "EUR"

data class SearchProductUiModel(
    val productId: Long,
    val productName: String,
    val salesPriceIncVat: BigDecimal,
    val productImage: String,
    val reviewAverage: Double,
    val reviewCount: Int,
    val listPriceIncVat: BigDecimal?,
    val favoriteStatus: FavoriteProductStatus
)

internal fun Product.toSearchUiModel(favoriteStatus: FavoriteProductStatus): SearchProductUiModel =
    SearchProductUiModel(
        productId = productId,
        productName = productName,
        salesPriceIncVat = salesPriceIncVat,
        productImage = productImage,
        reviewAverage = reviewSummary.reviewAverage,
        reviewCount = reviewSummary.reviewCount,
        listPriceIncVat = listPriceIncVat,
        favoriteStatus = favoriteStatus
    )
