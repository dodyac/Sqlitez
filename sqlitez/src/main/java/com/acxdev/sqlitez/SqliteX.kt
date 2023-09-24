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
import android.util.Log

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
                val constructor = entity.primaryConstructor
                while (cursor.moveToNext()) {
                    val args = entity.getFields(true).map { field ->
                        val columnIndex = cursor.getColumnIndex(field.name)

                        when (field.returnType.javaType) {
                            Int::class.java, java.lang.Integer::class.java -> cursor.getIntOrNull(columnIndex)
                            String::class.java -> cursor.getString(columnIndex)
                            Double::class.java -> cursor.getDoubleOrNull(columnIndex)
                            Long::class.java -> cursor.getLongOrNull(columnIndex)
                            Float::class.java -> cursor.getFloatOrNull(columnIndex)
                            Blob::class.java -> cursor.getBlobOrNull(columnIndex)
                            else -> cursor.getString(columnIndex)
                        }
                    }.toTypedArray()

                    val item = constructor?.call(*args)
                    items.add(item as T)
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
                    val args = entity.getFields(true).map { field ->
                        val columnIndex = cursor.getColumnIndex(field.name)

                        when (field.returnType.javaType) {
                            Int::class.java, java.lang.Integer::class.java -> cursor.getIntOrNull(columnIndex)
                            String::class.java -> cursor.getString(columnIndex)
                            Double::class.java -> cursor.getDoubleOrNull(columnIndex)
                            Long::class.java -> cursor.getLongOrNull(columnIndex)
                            Float::class.java -> cursor.getFloatOrNull(columnIndex)
                            Blob::class.java -> cursor.getBlobOrNull(columnIndex)
                            else -> cursor.getString(columnIndex)
                        }
                    }.toTypedArray()

                    val constructor = entity.primaryConstructor
                    item = constructor?.call(*args)
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
                    val args = entity.getFields(true).map { field ->
                        val columnIndex = cursor.getColumnIndex(field.name)

                        when (field.returnType.javaType) {
                            Int::class.java, java.lang.Integer::class.java -> cursor.getIntOrNull(columnIndex)
                            String::class.java -> cursor.getString(columnIndex)
                            Double::class.java -> cursor.getDoubleOrNull(columnIndex)
                            Long::class.java -> cursor.getLongOrNull(columnIndex)
                            Float::class.java -> cursor.getFloatOrNull(columnIndex)
                            Blob::class.java -> cursor.getBlobOrNull(columnIndex)
                            else -> cursor.getString(columnIndex)
                        }
                    }.toTypedArray()

                    val constructor = entity.primaryConstructor
                    item = constructor?.call(*args)
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

        entity.whenTableCreated {
            val values = ContentValues()

            entity.getFields().forEach { property ->
                property.isAccessible = true

                val propertyName = property.name
                val propertyValue = property.call(model)

                if (propertyValue == null) {
                    values.putNull(propertyName)
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
}