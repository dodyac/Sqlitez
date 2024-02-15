package com.acxdev.sqlitez.read

import kotlin.reflect.KProperty1

sealed class Condition {
    data class Value<T>(
        val variable: KProperty1<T, Any>,
        val value: Any,
        val isContains: Boolean = false
    ) : Condition() {
        val query : String
            get() {
                val query = if (isContains) {
                    "LOWER(${variable.name}) LIKE LOWER(?)"
                } else {
                    "${variable.name} = ?"
                }

                return query
            }
    }
    data class Order(
        val by: OrderBy
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
                }

                return query
            }
    }
    data class Limit(val value: Int = 0) : Condition() {
        val query : String
            get() {
                val query = if (value > 0) {
                    "LIMIT $value"
                } else {
                    ""
                }

                return query
            }
    }
}