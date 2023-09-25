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

class SqliteX(context: Context)
    : SQLiteOpenHelper(context, DatabaseNameHolder.dbName, null, 1) {

    companion object {
        const val TAG = "SqliteX"
    }

    override fun onCreate(p0: SQLiteDatabase?) {}

    override fun onUpgrade(p0: SQLiteDatabase?, p1: Int, p2: Int) {}

    fun <T : Any> KClass<T>.getTableName(): String? {
        return simpleName?.replace(Regex("([a-z])([A-Z])"), "$1_$2")?.lowercase(Locale.ROOT)
    }

    fun <T : Any> KClass<T>.getFields(isIdIncluded: Boolean = false): List<KCallable<*>> {
        val constructor = constructors.first()

        val parameterNames = constructor.parameters.map { it.name }

        val fields = members
            .filter { it.name in parameterNames }
            .sortedBy { parameterNames.indexOf(it.name) }

        return if (isIdIncluded) {
            fields
        } else {
            fields.filter { field -> field.name != "id" }
        }
    }

    fun <T : Any> KClass<T>.whenTableCreated(created: () -> Unit) {
        val properties = getFields()
            .joinToString(", ") { field ->
                val name = field.name
                "$name TEXT"
            }
        val sql = "CREATE TABLE IF NOT EXISTS ${getTableName()} (id INTEGER PRIMARY KEY AUTOINCREMENT, $properties)"

        writableDatabase.execSQL(sql)
        close()
        created.invoke()
    }

    fun <T : Any> getAll(entity: KClass<T>): List<T> {
        val items = mutableListOf<T>()
        entity.whenTableCreated {
            val sql = "SELECT * FROM ${entity.getTableName()}"
            val cursor = readableDatabase.rawQuery(sql, null)

            try {
                while (cursor.moveToNext()) {
                    cursor?.getArgs(entity)?.let {
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

        entity.whenTableCreated {
            var id = "1"

            entity.getFields(true).forEach { property ->
                property.isAccessible = true

                val propertyName = property.name
                val propertyValue = property.call(model)

                if (propertyName == "id") {
                    id = propertyValue.toString()
                }
                property.isAccessible = false
            }

            val sql = "SELECT * FROM ${entity.getTableName()} WHERE id = ?"
            val cursor = readableDatabase.rawQuery(sql, arrayOf(id))

            try {
                if (cursor.moveToFirst()) {
                    item = cursor.getArgs(entity)
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

        entity.whenTableCreated {
            val sql = "SELECT * FROM ${entity.getTableName()} WHERE id = ?"
            val cursor = readableDatabase.rawQuery(sql, arrayOf(id.toString()))

            try {
                if (cursor.moveToFirst()) {
                    item = cursor.getArgs(entity)
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

        entity.whenTableCreated {
            val values = ContentValues()

            entity.getFields().forEach { property ->
                property.isAccessible = true

                val propertyName = property.name
                val propertyValue = property.call(model)

                if (propertyValue == null) {
                    values.putNull(propertyName)
                } else if ((property.returnType.classifier as KClass<*>).getFields(true).isNotEmpty()) {
                    //put data class
                    values.put(propertyName, gson.toJson(propertyValue))
                } else {
                    values.put(propertyName, propertyValue.toString())
                }

                property.isAccessible = false
            }

            writableDatabase.insert(entity.getTableName(), null, values)
            close()
        }
    }

    inline fun <reified T: Any> update(model: T) {
        val entity = T::class
        val gson = Gson()

        entity.whenTableCreated {
            val values = ContentValues()
            var id = "1"

            entity.getFields(true).forEach { property ->
                property.isAccessible = true

                val propertyName = property.name
                val propertyValue = property.call(model)

                if (propertyValue == null) {
                    values.putNull(propertyName)
                } else if (propertyName == "id") {
                    id = propertyValue.toString()
                } else if ((property.returnType.classifier as KClass<*>).getFields(true).isNotEmpty()) {
                    //put data class
                    values.put(propertyName, gson.toJson(propertyValue))
                } else {
                    values.put(propertyName, propertyValue.toString())
                }
                property.isAccessible = false
            }

            writableDatabase.update(entity.getTableName(), values, "id = ?", arrayOf(id))
            close()
        }
    }

    inline fun <reified T: Any> delete(model: T) {
        val entity = T::class

        entity.whenTableCreated {
            var id = "1"

            entity.getFields(true).forEach { property ->
                property.isAccessible = true

                val propertyName = property.name
                val propertyValue = property.call(model)

                if (propertyName == "id") {
                    id = propertyValue.toString()
                }
                property.isAccessible = false
            }

            writableDatabase.delete(entity.getTableName(), "id = ?", arrayOf(id))
            close()
        }
    }

    fun <T: Any> deleteAll(entity: KClass<T>) {
        entity.whenTableCreated {
            val sql = "DELETE FROM ${entity.getTableName()}"
            writableDatabase.execSQL(sql)
            close()
        }
    }

    fun <T : Any> Cursor.getArgs(entity: KClass<T>): T? {
        val constructor = entity.primaryConstructor
        val args = entity.getFields(true).map { field ->
            val columnIndex = getColumnIndex(field.name)
            val fieldClass = (field.returnType.classifier as KClass<*>)
            val arguments = field.returnType.arguments

            when {
                arguments.isNotEmpty() -> {
                    val argumentsField =
                        (arguments.first().type?.classifier as KClass<*>).getFields(true)
                    if (argumentsField.isNotEmpty()) {
                        //list of data class
                        getString(columnIndex)
                    } else {
                        //list of value
                        getString(columnIndex)
                    }
                }
                field.returnType.javaType == Int::class.java || field.returnType.javaType == java.lang.Integer::class.java -> {
                    getIntOrNull(columnIndex)
                }
                field.returnType.javaType == String::class.java -> {
                    getString(columnIndex)
                }
                field.returnType.javaType == Double::class.java -> {
                    getDoubleOrNull(columnIndex)
                }
                field.returnType.javaType == Long::class.java -> {
                    getLongOrNull(columnIndex)
                }
                field.returnType.javaType == Float::class.java -> {
                    getFloatOrNull(columnIndex)
                }
                field.returnType.javaType == Blob::class.java -> {
                    getBlobOrNull(columnIndex)
                }
                fieldClass.getFields(true).isNotEmpty() -> {
                    getString(columnIndex).asClass(fieldClass)
                }
                else -> getString(columnIndex)
            }
        }.toTypedArray()

        return constructor?.call(*args)
    }

    private fun String.asClass(fieldClass: KClass<*>): Any? {
        val constructor = fieldClass.primaryConstructor
        val gson = Gson()

        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        val jsonMap: Map<String, Any> = gson.fromJson(this, mapType)

        val args = jsonMap.map { json ->
            if (json.value is Number) {
                val currentField = fieldClass.getFields(true).find { it.name == json.key }
                currentField?.let {
                    when (it.returnType?.classifier as? KClass<*>) {
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