package com.acxdev.sqlitez.read


sealed class Query(val conditions: List<Condition>) {
    data class SelectOf(
        val variables: List<String> = emptyList(),
        val cons: List<Condition> = emptyList()
    ) : Query(cons)
    data class SelectAll(
        val cons: List<Condition> = emptyList()
    ) : Query(cons)
    data class SelectCount(
        val cons: List<Condition> = emptyList()
    ) : Query(cons)
}