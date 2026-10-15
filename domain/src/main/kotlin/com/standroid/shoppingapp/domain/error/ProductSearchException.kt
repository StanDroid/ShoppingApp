package com.standroid.shoppingapp.domain.error

sealed class ProductSearchException(cause: Throwable) : Exception(null, cause) {

    class Connectivity(cause: Throwable) : ProductSearchException(cause)

    class Unknown(cause: Throwable) : ProductSearchException(cause)
}
