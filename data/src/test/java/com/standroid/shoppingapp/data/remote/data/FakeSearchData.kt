package com.standroid.shoppingapp.data.remote.data

internal fun productJson(id: Long = 793652, price: String = "24.99", extra: String = ""): String =
    """
    {
      "productId": $id,
      "productName": "Apple USB adapter",
      "reviewInformation": {
        "reviews": [],
        "reviewSummary": {"reviewAverage": 9.2, "reviewCount": 209}
      },
      "salesPriceIncVat": $price,
      "productImage": "https://image.coolblue.nl/products/1"
      $extra
    }
    """.trimIndent()

internal fun searchJson(
    page: Int = 1,
    pageCount: Int = 3,
    products: String = productJson(),
    total: Int = 70
): String = """
    {
      "products": [$products],
      "currentPage": $page,
      "pageSize": 24,
      "totalResults": $total,
      "pageCount": $pageCount
    }
""".trimIndent()
