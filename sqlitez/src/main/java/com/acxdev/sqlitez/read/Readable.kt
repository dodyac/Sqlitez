package com.acxdev.sqlitez.read

data class Readable(
    val query: Query,
    val conditions: List<Condition> = emptyList()
)