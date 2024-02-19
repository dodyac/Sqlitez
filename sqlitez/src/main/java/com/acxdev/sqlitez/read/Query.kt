package com.acxdev.sqlitez.read

import kotlin.reflect.KProperty1

sealed class Query<T>() {
    data class SelectOf<T>(
        val variables: List<KProperty1<T, Any>> = emptyList(),
        val conditions: List<Condition> = emptyList()
    ) : Query<T>()
    data class SelectAll(
        val conditions: List<Condition> = emptyList()
    ) : Query<Nothing>()
    data class SelectCount(
        val conditions: List<Condition> = emptyList()
    ) : Query<Nothing>()
}