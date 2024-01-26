package com.acxdev.sqlitez.read

data class Readable<T>(
    val query: Query,
    val conditions: List<Condition<T>> = emptyList()
)