package com.acxdev.sqlitez

import com.google.gson.FieldNamingStrategy
import java.lang.reflect.Field

class IgnoreSerializedNameStrategy : FieldNamingStrategy {
    override fun translateName(f: Field): String {
        return f.name // Use the field name directly
    }
}