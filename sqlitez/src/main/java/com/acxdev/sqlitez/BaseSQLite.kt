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
import com.acxdev.sqlitez.Utils.primaryKey
import com.google.gson.Gson
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
    : SQLiteOpenHelper(context, DatabaseNameHolder.dbName, null, 1) {

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

        val args = jsonMap.map { json ->
            if (json.value is Number) {
                val currentField = fields.find { it.name == json.key }
                currentField?.let {
                    when (it.returnType.classifier as? KClass<*>) {
                        Int::class -> (json.value as Double).toInt()
                        Long::class -> (json.value as Double).toLong()
                        Float::class -> (json.value as Double).toFloat()
                        else -> json.value
                    }
                } ?: run {
                    json.value
                }
            } else {
                json.value
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
                "$name TEXT"
            }
        val sql = "CREATE TABLE IF NOT EXISTS $tableName (${primaryKey} INTEGER PRIMARY KEY AUTOINCREMENT, $properties)"

        writableDatabase.execSQL(sql)
        created.invoke(tableName, fields)
    }

    inline fun <T> KCallable<*>.putContentValues(
        model: T,
        contentValues: ContentValues,
        crossinline idFetched: (
            id: String
        ) -> Unit = { _ -> }
    ) {
        isAccessible = true

        val value = call(model)

        val fieldClass = returnType.classifier as KClass<*>
        val arguments = returnType.arguments

        if (value == null) {
            contentValues.putNull(name)
        } else if (name == primaryKey) {
            idFetched.invoke(value.toString())
        } else if (arguments.isNotEmpty()) {
            //put data list
            contentValues.put(name, gson.toJson(value))
        } else if (fieldClass.getFields().isNotEmpty()) {
            //put data class
            contentValues.put(name, gson.toJson(value))
        } else {
            when (returnType.javaType) {
                Boolean::class.java -> {
                    contentValues.put(name, value.toString().toBoolean())
                }
                ByteArray::class.java -> {
                    val byteArray = value as? ByteArray
                    contentValues.put(name, byteArray)
                }
                else -> {
                    contentValues.put(name, value.toString())
                }
            }
        }

        isAccessible = false
    }

    inline fun whenCursorMoved(
        crossinline result: () -> Unit
    ) {
        try {
            result.invoke()
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
                    val dataList = gson.fromJson<List<Any>>(getString(columnIndex), listType)
                    dataList
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
                    val dataList = gson.fromJson<List<Any>>(getString(columnIndex), listType)
                    dataList
                }
            } else if(field.returnType.javaType == ByteArray::class.java) {
                getBlobOrNull(columnIndex)
            } else if (fieldClass.getFields().isNotEmpty()) {
                getString(columnIndex).asClass(fieldClass.primaryConstructor, fieldClass.getFields())
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
                    else -> getString(columnIndex)
                }
            }
        }.toTypedArray()

        return constructor?.call(*args)
    }

    fun Long.logDuration(log: String) {
        Log.i(DURATION, "$log took ${readableDuration()}")
    }

    fun Pair<KProperty1<*, Any>, Any>?.getCursor(tableName: String?): Cursor {
        val sql: String
        val selectionArgs: Array<String>?

        if (this != null) {
            sql = "SELECT * FROM $tableName WHERE ${first.name} = ?"
            selectionArgs = arrayOf(second.toString())
        } else {
            sql = "SELECT * FROM $tableName"
            selectionArgs = null
        }

        return readableDatabase.rawQuery(sql, selectionArgs)
    }
}