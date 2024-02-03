package com.acxdev.sqlitez.read

import kotlin.reflect.KProperty1

sealed class Condition {
    data class Value<T>(
        val variable: KProperty1<T, Any>,
        val value: Any
    ) : Condition()
    data class Order(
        val by: OrderBy,
        val limit: Int = 0
    ) : Condition() {
        sealed class OrderBy {
            object Random : OrderBy()
            data class Ascending(
                val variable: KProperty1<*, Any>
            ) : OrderBy()
            data class Descending(
                val variable: KProperty1<*, Any>
            ) : OrderBy()
        }

        val query : String
            get() {
                val query = "ORDER BY " + when (by) {
                    is OrderBy.Ascending -> "${by.variable.name} ASC "
                    is OrderBy.Descending -> "${by.variable.name} DESC "
                    OrderBy.Random -> "RANDOM() "
                } + if (limit > 0) {
                    "LIMIT $limit"
                } else {
                    ""
                }

                return query
            }
    }
}