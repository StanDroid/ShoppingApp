package com.standroid.shoppingapp.data.remote

import com.standroid.shoppingapp.data.remote.dto.ProductDto
import com.standroid.shoppingapp.data.remote.dto.SearchResponseDto
import com.standroid.shoppingapp.domain.model.Product
import com.standroid.shoppingapp.domain.model.ReviewSummary

internal data class SearchPage(
    val products: List<Product>,
    val currentPage: Int,
    val pageCount: Int
)

internal fun SearchResponseDto.toSearchPage(requestedPage: Int): SearchPage {
    val items = products.required()
    val current = currentPage.required()
    val count = pageCount.required()
    require(current == requestedPage && current >= 1 && count >= 0)
    require(items.isEmpty() || current <= count)
    return SearchPage(
        products = items.map { it.required().toDomain() },
        currentPage = current,
        pageCount = count
    )
}

internal fun ProductDto.toDomain(): Product {
    val id = productId.required()
    val summary = reviewInformation.required()
        .reviewSummary.required()
    val average = summary.reviewAverage.required()
    val count = summary.reviewCount.required()
    return Product(
        productId = id,
        productName = productName.requiredText(),
        salesPriceIncVat = salesPriceIncVat.required(),
        productImage = productImage.requiredText(),
        reviewSummary = ReviewSummary(average, count),
        listPriceIncVat = listPriceIncVat
    )
}

private fun <T : Any> T?.required(): T = requireNotNull(this)

private fun String?.requiredText(): String = required().also { require(it.isNotBlank()) }
