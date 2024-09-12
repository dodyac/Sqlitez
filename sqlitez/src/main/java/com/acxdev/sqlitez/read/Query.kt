package com.acxdev.sqlitez.read


sealed class Query(val condition: Condition?) {
    data class SelectOf(
        val variables: List<String> = emptyList(),
        val cons: Condition?
    ) : Query(cons)

    data class SelectAll(
        val cons: Condition?
    ) : Query(cons)

    data class SelectCount(
        val cons: Condition?
    ) : Query(cons)
}