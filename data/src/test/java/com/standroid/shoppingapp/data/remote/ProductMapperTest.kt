package com.standroid.shoppingapp.data.remote

import com.google.gson.Gson
import com.standroid.shoppingapp.data.remote.data.productJson
import com.standroid.shoppingapp.data.remote.data.searchJson
import com.standroid.shoppingapp.data.remote.dto.SearchResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ProductMapperTest {
    private fun decode(json: String): SearchResponseDto =
        Gson().fromJson(json, SearchResponseDto::class.java)

    @Test fun mapsListingFieldsAndExactDecimalPrices() {
        val product = decode(searchJson()).toSearchPage(1).products.single()
        assertEquals(793652L, product.productId)
        assertEquals("Apple USB adapter", product.productName)
        assertEquals("https://image.coolblue.nl/products/1", product.productImage)
        assertEquals(BigDecimal("24.99"), product.salesPriceIncVat)
        assertEquals(9.2, product.reviewSummary.reviewAverage, 0.0)
        assertEquals(209, product.reviewSummary.reviewCount)
        assertNull(product.listPriceIncVat)
    }

    @Test fun mapsIntegerAndDecimalPricesWithoutLosingPrecision() {
        listOf(
            "575" to "626",
            "24.99" to "29.1234567890123456789"
        ).forEach { (salePrice, listPrice) ->
            val product = decode(
                searchJson(
                    products = productJson(
                        price = salePrice,
                        extra = ", \"listPriceIncVat\": $listPrice"
                    )
                )
            ).toSearchPage(1).products.single()
            assertEquals(BigDecimal(salePrice), product.salesPriceIncVat)
            assertEquals(BigDecimal(listPrice), product.listPriceIncVat)
        }
    }

    @Test fun acceptsNullListPrice() {
        val product = decode(
            searchJson(products = productJson(extra = ", \"listPriceIncVat\": null"))
        ).toSearchPage(1).products.single()
        assertNull(product.listPriceIncVat)
    }

    @Test fun ignoresUnusedProductFieldsInsteadOfValidatingThem() {
        val extra = """,
          "USPs": [null],
          "availabilityState": "unknown",
          "nextDayDelivery": {},
          "listPriceExVat": "unused",
          "coolbluesChoiceInformationTitle": false,
          "promoIcon": {"text": null, "type": null}
        """
        val expected = decode(searchJson()).toSearchPage(1).products.single()
        val actual = decode(
            searchJson(products = productJson(extra = extra))
        ).toSearchPage(1).products.single()
        assertEquals(expected, actual)
    }

    @Test fun rejectsMissingOrNullRequiredFieldsAndNullProducts() {
        val invalid = listOf(
            searchJson().replace("\"productId\": 793652,", ""),
            searchJson().replace("\"salesPriceIncVat\": 24.99", "\"salesPriceIncVat\": null"),
            searchJson(products = "null"),
            searchJson().replace("\"productName\": \"Apple USB adapter\"", "\"productName\": \"\"")
        )
        invalid.forEach { json ->
            assertThrows(IllegalArgumentException::class.java) {
                decode(json).toSearchPage(1)
            }
        }
    }

    @Test fun validatesPaginationAndAcceptsEmptyResults() {
        listOf(
            searchJson(page = 2),
            searchJson(pageCount = -1),
            searchJson(pageCount = 0),
            searchJson(page = 0),
            searchJson().replace("\"pageCount\": 3", "\"pageCount\": null")
        ).forEach { json ->
            assertThrows(IllegalArgumentException::class.java) {
                decode(json).toSearchPage(1)
            }
        }
        assertTrue(
            decode(searchJson(pageCount = 0, products = "", total = 0))
                .toSearchPage(1).products.isEmpty()
        )
    }
}
