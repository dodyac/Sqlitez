package com.acxdev.sqlitez.read

import kotlin.reflect.KProperty1

class Condition {
    var values: List<Value<*>> = listOf()
    var each: Each = Each.And
    var orderBy: OrderBy? = null
    var limit: Int = 0
    var offset: Int = 0

    val query : String
        get() {
            val eachConditionClause = each.name.uppercase()
            val queryValues = if (values.isNotEmpty()) {
                val condition = values.joinToString(" $eachConditionClause ") {
                    it.query
                }
                "WHERE $condition "
            } else {
                ""
            }
            var queryOrderBy = ""
            orderBy?.let {
                queryOrderBy = "ORDER BY " + when (it) {
                    is OrderBy.Ascending -> "${it.variable.name} ASC "
                    is OrderBy.Descending -> "${it.variable.name} DESC "
                    OrderBy.Random -> "RANDOM() "
                }
            }
            val queryLimit = if (limit > 0) {
                "LIMIT $limit"
            } else {
                ""
            }
            val queryOffset = if (offset > 0) {
                "OFFSET $offset"
            } else {
                ""
            }
            val query = queryValues + queryOrderBy + queryLimit + queryOffset

            return query
        }

    val args: Array<String>?
        get() {
            val selectionArgs = if (values.isNotEmpty()) {
                values.map {
                    when(it.command) {
                        Value.Command.Equal -> it.value.toString()
                        is Value.Command.Like -> {
                            when (it.command.operator) {
                                Value.Command.Operator.StartWith -> "%${it.value}"
                                Value.Command.Operator.Contains -> "%${it.value}%"
                                Value.Command.Operator.EndWith -> "${it.value}%"
                            }
                        }
//                        is Value.Command.In -> it.command.values.map { v -> v.toString() }
//                        is Value.Command.Between -> listOf(it.command.start.toString(), it.command.end.toString())
//                        Value.Command.IsNotNull -> TODO()
//                        Value.Command.IsNull -> TODO()
//                        Value.Command.NotEqual -> TODO()
                    }
                }.toTypedArray()
            } else {
                null
            }

            return selectionArgs
        }

    sealed class OrderBy {
        data object Random : OrderBy()
        data class Ascending(
            val variable: KProperty1<*, Any>
        ) : OrderBy()
        data class Descending(
            val variable: KProperty1<*, Any>
        ) : OrderBy()
    }

    data class Value<T>(
        val variable: KProperty1<T, Any>,
        val value: Any,
        val command: Command = Command.Equal,
        val isLowerCase: Boolean = false
    ) {
        sealed class Command {
            data object Equal: Command()
            data class Like(
                val operator: Operator
            ): Command()
//            data class In(
//                val values: List<Any>
//            ) : Command()
//            data class Between(
//                val start: Any,
//                val end: Any
//            ) : Command()
//            data object IsNull : Command()
//            data object IsNotNull : Command()
//            data object NotEqual : Command()

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
//                        is Command.In -> "LOWER(${variable.name}) IN (${command.values.joinToString(",") { "?" }})"
//                        is Command.Between -> "LOWER(${variable.name}) BETWEEN ? AND ?"
//                        Command.IsNull -> "LOWER(${variable.name}) IS NULL"
//                        Command.IsNotNull -> "LOWER(${variable.name}) IS NOT NULL"
//                        Command.NotEqual -> "LOWER(${variable.name}) != ?"
                    }
                } else {
                    when(command) {
                        Command.Equal -> "${variable.name} = ?"
                        is Command.Like -> "${variable.name} LIKE ?"
//                        is Command.In -> "${variable.name} IN (${command.values.joinToString(",") { "?" }})"
//                        is Command.Between -> "${variable.name} BETWEEN ? AND ?"
//                        Command.IsNull -> "${variable.name} IS NULL"
//                        Command.IsNotNull -> "${variable.name} IS NOT NULL"
//                        Command.NotEqual -> "${variable.name} != ?"
                    }
                }

                return query
            }
    }
    enum class Each {
        And, Or
    }
}

data class Person(val name: String, val age: Int, val gender: String)
fun main() {
    val conditionSx = Condition()
    conditionSx.values = listOf(
        Condition.Value(variable = Person::name, value = "Acx", command = Condition.Value.Command.Equal, isLowerCase = true),
        Condition.Value(variable = Person::name, value = "Acx", command = Condition.Value.Command.Like(Condition.Value.Command.Operator.StartWith), isLowerCase = false),
        Condition.Value(variable = Person::age, value = 22, command = Condition.Value.Command.Equal, isLowerCase = false),
    )
    conditionSx.limit = 1
    conditionSx.orderBy = Condition.OrderBy.Random
    print(conditionSx.query)
    print(conditionSx.args?.joinToString())

}