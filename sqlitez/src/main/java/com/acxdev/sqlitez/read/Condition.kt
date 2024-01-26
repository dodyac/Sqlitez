package com.acxdev.sqlitez.read

import kotlin.reflect.KProperty1

data class Condition<T>(
    val variable: KProperty1<T, Any>,
    val value: Any
)