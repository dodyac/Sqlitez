package com.acxdev.sqlitez.read

import kotlin.reflect.KProperty1

sealed class Condition {
    data class Value<T>(
        val variable: KProperty1<T, Any>,
        val value: Any,
        val command: Command = Command.Equal,
        val isLowerCase: Boolean = false
    ) : Condition() {
        sealed class Command {
            object Equal: Command()
            data class Like(
                val operator: Operator
            ): Command()

            enum class Operator {
                StartWith, Contains, EndWith
            }
        }
        val query : String
            get() {
                val query = if (isLowerCase) {
                    when(command) {
                        Command.Equal -> "LOWER(${variable.name}) = (?)"
                        is Command.Like -> "LOWER(${variable.name}) LIKE (?)"
                    }
                } else {
                    when(command) {
                        Command.Equal -> "${variable.name} = ?"
                        is Command.Like -> "${variable.name} LIKE ?"
                    }
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
    data class Each(
        val by: Condition
    ) : Condition() {
        enum class Condition {
            And, Or
        }
    }
}