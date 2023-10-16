package com.acxdev.sqlitez

import java.sql.Blob
import java.util.Locale
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType
import androidx.core.database.getBlobOrNull
import androidx.core.database.getDoubleOrNull
import androidx.core.database.getFloatOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.reflect.KFunction

class SqliteZ(context: Context?)
    : SQLiteOpenHelper(context, DatabaseNameHolder.dbName, null, 1) {

    companion object {
        const val TAG = "SqliteZ"
    }

    override fun onCreate(p0: SQLiteDatabase?) {}

    override fun onUpgrade(p0: SQLiteDatabase?, p1: Int, p2: Int) {}

    fun <T : Any> KClass<T>.getFields(): List<KCallable<*>> {
        val constructor = constructors.first()

        val parameterNames = constructor.parameters.map { it.name }

        return members
            .filter { it.name in parameterNames }
            .sortedBy { parameterNames.indexOf(it.name) }
    }

    fun <T : Any> KClass<T>.whenTableCreated(created: (String?, List<KCallable<*>>) -> Unit) {
        val fields = getFields()
        val properties = fields.filter { field -> field.name != "id" }
            .joinToString(", ") { field ->
                val name = field.name
                "$name TEXT"
            }
        val tableName = simpleName?.replace(Regex("([a-z])([A-Z])"), "$1_$2")?.lowercase(Locale.ROOT)
        val sql = "CREATE TABLE IF NOT EXISTS $tableName (id INTEGER PRIMARY KEY AUTOINCREMENT, $properties)"

        writableDatabase.execSQL(sql)
        close()
        created.invoke(tableName, fields)
    }

    fun <T : Any> getAll(entity: KClass<T>): List<T> {
        val items = mutableListOf<T>()
        entity.whenTableCreated { table, fields ->
            val sql = "SELECT * FROM $table"
            val cursor = readableDatabase.rawQuery(sql, null)

            try {
                while (cursor.moveToNext()) {
                    cursor?.getArgs(entity.primaryConstructor, fields)?.let {
                        items.add(it)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Incompatible Data Type")
                e.printStackTrace()
            }
            cursor.close()
            close()
        }
        return items
    }

    inline fun <reified T : Any> get(model: T): T? {
        val entity = T::class
        var item: T? = null

        entity.whenTableCreated { table, fields ->
            var id = "1"

            fields.forEach { property ->
                property.isAccessible = true

                val propertyName = property.name
                val propertyValue = property.call(model)

                if (propertyName == "id") {
                    id = propertyValue.toString()
                }
                property.isAccessible = false
            }

            val sql = "SELECT * FROM $table WHERE id = ?"
            val cursor = readableDatabase.rawQuery(sql, arrayOf(id))

            try {
                if (cursor.moveToFirst()) {
                    item = cursor.getArgs(entity.primaryConstructor, fields)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Incompatible Data Type")
                e.printStackTrace()
            }

            cursor.close()
            close()
        }
        return item
    }

    inline fun <reified T : Any> getById(id: Int): T? {
        val entity = T::class
        var item: T? = null

        entity.whenTableCreated { table, fields ->
            val sql = "SELECT * FROM $table WHERE id = ?"
            val cursor = readableDatabase.rawQuery(sql, arrayOf(id.toString()))

            try {
                if (cursor.moveToFirst()) {
                    item = cursor.getArgs(entity.primaryConstructor, fields)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Incompatible Data Type")
                e.printStackTrace()
            }

            cursor.close()
            close()
        }
        return item
    }

    inline fun <reified T: Any> insert(model: T) {
        val entity = T::class
        val gson = Gson()

        entity.whenTableCreated { table, fields ->
            val values = ContentValues()

            fields.filter { field -> field.name != "id" }
                .forEach { property ->
                    property.isAccessible = true

                    val propertyName = property.name
                    val propertyValue = property.call(model)

                    val fieldClass = property.returnType.classifier as KClass<*>
                    val arguments = property.returnType.arguments

                    if (propertyValue == null) {
                        values.putNull(propertyName)
                    } else if (arguments.isNotEmpty()) {
                        //put data list
                        values.put(propertyName, gson.toJson(propertyValue))
                    } else if (fieldClass.getFields().isNotEmpty()) {
                        //put data class
                        values.put(propertyName, gson.toJson(propertyValue))
                    } else {
                        if (property.returnType.javaType == Boolean::class.java) {
                            values.put(propertyName, propertyValue.toString().toBoolean())
                        } else {
                            values.put(propertyName, propertyValue.toString())
                        }
                    }

                    property.isAccessible = false
                }

            writableDatabase.insert(table, null, values)
            close()
        }
    }

    inline fun <reified T: Any> update(model: T) {
        val entity = T::class
        val gson = Gson()

        entity.whenTableCreated { table, fields ->
            val values = ContentValues()
            var id = "1"

            fields.forEach { property ->
                property.isAccessible = true

                val propertyName = property.name
                val propertyValue = property.call(model)

                val fieldClass = property.returnType.classifier as KClass<*>
                val arguments = property.returnType.arguments

                if (propertyValue == null) {
                    values.putNull(propertyName)
                } else if (propertyName == "id") {
                    id = propertyValue.toString()
                }  else if (arguments.isNotEmpty()) {
                    //put data list
                    values.put(propertyName, gson.toJson(propertyValue))
                } else if (fieldClass.getFields().isNotEmpty()) {
                    //put data class
                    values.put(propertyName, gson.toJson(propertyValue))
                } else {
                    if (property.returnType.javaType == Boolean::class.java) {
                        values.put(propertyName, propertyValue.toString().toBoolean())
                    } else {
                        values.put(propertyName, propertyValue.toString())
                    }
                }
                property.isAccessible = false
            }

            writableDatabase.update(table, values, "id = ?", arrayOf(id))
            close()
        }
    }

    inline fun <reified T: Any> delete(model: T) {
        val entity = T::class

        entity.whenTableCreated { table, fields ->
            var id = "1"

            fields.forEach { property ->
                property.isAccessible = true

                val propertyName = property.name
                val propertyValue = property.call(model)

                if (propertyName == "id") {
                    id = propertyValue.toString()
                }
                property.isAccessible = false
            }

            writableDatabase.delete(table, "id = ?", arrayOf(id))
            close()
        }
    }

    fun <T: Any> deleteAll(entity: KClass<T>) {
        entity.whenTableCreated { table, _ ->
            val sql = "DELETE FROM $table"
            writableDatabase.execSQL(sql)
            close()
        }
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
                    Blob::class.java -> {
                        getBlobOrNull(columnIndex)
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
}