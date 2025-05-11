package com.acxdev.sqlitez

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.database.getBlobOrNull
import androidx.core.database.getDoubleOrNull
import androidx.core.database.getFloatOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import com.acxdev.sqlitez.common.DatabaseNameHolder
import com.acxdev.sqlitez.common.Utils.primaryKey
import com.acxdev.sqlitez.read.Condition
import com.acxdev.sqlitez.read.Query
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.util.Locale
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

open class BaseSQLite(context: Context?)
    : SQLiteOpenHelper(context, DatabaseNameHolder.dbName, null, DatabaseNameHolder.dbVersion) {

    val TAG: String? = javaClass.simpleName
    private val DURATION: String = "${TAG}_Duration"
    val gson by lazy {
        Gson()
    }

    override fun onCreate(p0: SQLiteDatabase?) {}

    override fun onUpgrade(p0: SQLiteDatabase?, p1: Int, p2: Int) {}

    private fun <T> String.asClass(constructor: KFunction<T>?, fields: List<KCallable<*>>): Any? {
        val gson = Gson()

        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        val jsonMap: Map<String, Any> = gson.fromJson(this, mapType)

        val args = fields.map { field ->
            val fieldName = field.name
            val fieldType = field.returnType.classifier as? KClass<*>
            val fieldValue = jsonMap[fieldName]
            if (fieldValue == null) {
                null
            } else {
                when (fieldType) {
                    Int::class -> (fieldValue as? Number)?.toInt()
                    Long::class -> (fieldValue as? Number)?.toLong()
                    Float::class -> (fieldValue as? Number)?.toFloat()
                    Double::class -> (fieldValue as? Number)?.toDouble()
                    Boolean::class -> (fieldValue as? Boolean)
                    String::class -> fieldValue as? String
                    else -> fieldValue
                }
            }
        }.toTypedArray()

        return constructor?.call(*args)
    }

    private fun Long.readableDuration(): String {
        return when {
            this < 1000 -> "$this ms"
            this < 60_000 -> String.format("%.1f s", this / 1000.0)
            this < 3_600_000 -> String.format("%.1f min", this / 60_000.0)
            else -> String.format("%.1f hours", this / 3_600_000.0)
        }
    }

    inline fun <T : Any> KClass<T>.whenTableCreated(
        crossinline created: (
            tableName: String?,
            fields: List<KCallable<*>>
        ) -> Unit) {

        val tableName = simpleName?.replace(Regex("([a-z])([A-Z])"), "$1_$2")?.lowercase(Locale.ROOT)
        val fields = getFields()

        val properties = fields.filter { field -> field.name != primaryKey }
            .joinToString(", ") { field ->
                val name = field.name
                val escapedName = "`$name`"
                "$escapedName TEXT"
            }
        val sql = "CREATE TABLE IF NOT EXISTS $tableName (${primaryKey} INTEGER PRIMARY KEY AUTOINCREMENT, $properties)"

        writableDatabase.execSQL(sql)
        created.invoke(tableName, fields)
    }

    inline fun <T> KCallable<*>.putContentValues(
        entity: KClass<*>,
        model: T,
        contentValues: ContentValues,
        crossinline idFetched: (
            id: String
        ) -> Unit = { _ -> }
    ) {
        isAccessible = true

        val value = call(model)

        val escapedName = "`$name`"
        // Get the SerializedName annotation if present
        val serializedName = annotations
            .filterIsInstance<SerializedName>()
            .firstOrNull()
            ?.value


        val fieldClass = returnType.classifier as KClass<*>
        val arguments = returnType.arguments

        if (value == null) {
            contentValues.putNull(escapedName)
        } else if (name == entity.primaryKey) {
            idFetched.invoke(value.toString())
        } else if (arguments.isNotEmpty()) {
            //put data list
            val json = gson.toJson(value, fieldClass.java)
            val json2 = gson.toJson(value)
            val json3 = gson.toJson(value, value::class.java)
            contentValues.put(escapedName, json)
        } else if (fieldClass.getFields().isNotEmpty()) {
            //put data class
            val json = gson.toJson(value, fieldClass.java)
            val json2 = gson.toJson(value)
            val json3 = gson.toJson(value, value::class.java)
            contentValues.put(escapedName, json)
        } else {
            when (returnType.javaType) {
                Boolean::class.java -> {
                    contentValues.put(escapedName, value.toString().toBoolean())
                }
                ByteArray::class.java -> {
                    val byteArray = value as? ByteArray
                    contentValues.put(escapedName, byteArray)
                }
                else -> {
                    contentValues.put(escapedName, value.toString())
                }
            }
        }

        isAccessible = false
    }

    inline fun Cursor.whenMoved(
        crossinline result: () -> Unit
    ) {
        try {
            while (moveToNext()) {
                result.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Incompatible Data Type")
            e.printStackTrace()
        }
    }

    fun <T : Any> KClass<T>.getFields(): List<KCallable<*>> {
        val constructor = constructors.first()

        val parameterNames = constructor.parameters.map { it.name }

        return members
            .filter { it.name in parameterNames }
            .sortedBy { parameterNames.indexOf(it.name) }
    }

    fun <T : Any> Cursor.getArgs(constructor: KFunction<T>?, fields: List<KCallable<*>>): T? {
        val gson = Gson()
        val args = fields.map { field ->
            val columnIndex = getColumnIndex(field.name)
            val fieldClass = (field.returnType.classifier as KClass<*>)
            val arguments = field.returnType.arguments

            if (arguments.isNotEmpty()) {
                val firstArgument = arguments.first().type?.classifier as KClass<*>
                val argumentsField = firstArgument.getFields()
                if (argumentsField.isNotEmpty()) {
                    //list of data class
                    val listType = TypeToken.getParameterized(List::class.java, firstArgument.java).type

                    val stringIndex = getString(columnIndex)
                    if (stringIndex != null) {
                        val dataList = gson.fromJson<List<Any>>(stringIndex, listType)
                        dataList
                    } else {
                        null
                    }
                } else {
                    //list of value
                    val listType = when (firstArgument) {
                        Int::class -> object : TypeToken<List<Int>>() {}.type
                        Long::class -> object : TypeToken<List<Long>>() {}.type
                        Float::class -> object : TypeToken<List<Float>>() {}.type
                        Double::class -> object : TypeToken<List<Double>>() {}.type
                        Boolean::class -> object : TypeToken<List<String>>() {}.type
                        String::class -> object : TypeToken<List<String>>() {}.type
                        else -> object : TypeToken<List<String>>() {}.type
                    }
                    val stringIndex = getString(columnIndex)
                    if (stringIndex != null) {
                        if (fieldClass == Map::class) {
                            val mapType = object : TypeToken<Map<String, Any>>() {}.type
                            val dataList = gson.fromJson<Map<String, Any>>(stringIndex, mapType)
                            dataList
                        } else {
                            val dataList = gson.fromJson<List<Any>>(stringIndex, listType)
                            dataList
                        }
                    } else {
                        null
                    }
                }
            } else if(field.returnType.javaType == ByteArray::class.java) {
                getBlobOrNull(columnIndex)
            } else if (fieldClass.getFields().isNotEmpty()) {
                val stringIndex = getString(columnIndex)
                if (stringIndex != null) {
                    stringIndex.asClass(fieldClass.primaryConstructor, fieldClass.getFields())
                } else {
                    null
                }
            } else {
                when(field.returnType.javaType) {
                    Int::class.java, java.lang.Integer::class.java -> {
                        getIntOrNull(columnIndex)
                    }
                    String::class.java -> {
                        getString(columnIndex)
                    }
                    Double::class.java -> {
                        getDoubleOrNull(columnIndex)
                    }
                    Long::class.java -> {
                        getLongOrNull(columnIndex)
                    }
                    Float::class.java -> {
                        getFloatOrNull(columnIndex)
                    }
                    Boolean::class.java -> {
                        getIntOrNull(columnIndex) == 1
                    }
                    else -> {
                        if (fieldClass.java.isEnum) {
                            fieldClass.java.enumConstants
                                .filterIsInstance<Enum<*>>().find {
                                    it.name == getString(columnIndex)
                                }
                        } else {
                            getString(columnIndex)
                        }
                    }
                }
            }
        }.toTypedArray()

        return constructor?.call(*args)
    }

    fun Long.logDuration(log: String) {
        Log.i(DURATION, "$log took ${readableDuration()}")
    }

    fun Query.getCursor(tableName: String?, log: (String) -> Unit): Cursor {
        val variablesClause = mutableListOf<String>()
        if (this is Query.SelectOf) {
            variablesClause.addAll(variables)
        }
        val variablesSql = variablesClause.joinToString(", ")
        val query = condition?.query ?: ""
        var conditionLog = "$variablesSql $query".trim()
        condition?.values?.forEach {
            conditionLog = conditionLog.replaceFirst("?", it.value.toString())
        }
        log.invoke(conditionLog)

        val sql = when (this) {
            is Query.SelectAll -> {
                "SELECT * FROM $tableName $query"
            }
            is Query.SelectOf -> {
                "SELECT $variablesSql FROM $tableName $query"
            }
            is Query.SelectCount -> {
                "SELECT COUNT(*) FROM $tableName $query"
            }
        }

        return readableDatabase.rawQuery(sql, condition?.args)
    }
}
