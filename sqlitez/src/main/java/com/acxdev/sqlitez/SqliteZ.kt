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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.reflect.KFunction
import kotlin.system.measureTimeMillis

class SqliteZ(context: Context?)
    : SQLiteOpenHelper(context, DatabaseNameHolder.dbName, null, 1) {

    companion object {
        const val TAG = "SqliteZ"
        const val DURATION = "SqliteZ_Duration"
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

    suspend fun <T : Any> suspendGetAll(entity: KClass<T>): List<T> {
        val items = mutableListOf<T>()
        var tableName: String? = ""

        val duration = measureTimeMillis {
            withContext(Dispatchers.IO) {
                entity.whenTableCreated { table, fields ->
                    tableName = table
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
            }
        }
        Log.i(DURATION, "getAll $tableName took ${duration.readableDuration()}")
        return items
    }

    inline fun <reified T : Any> get(model: T): T? {
        var item: T? = null
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

    suspend inline fun <reified T : Any> suspendGet(model: T): T? {
        var item: T? = null
        var tableName: String? = ""
        var ids = "0"

        val duration = measureTimeMillis {
            withContext(Dispatchers.IO) {
                val entity = T::class
                entity.whenTableCreated { table, fields ->
                    tableName = table

                    fields.forEach { property ->
                        property.isAccessible = true

                        val propertyName = property.name
                        val propertyValue = property.call(model)

                        if (propertyName == "id") {
                            ids = propertyValue.toString()
                        }
                        property.isAccessible = false
                    }

                    val sql = "SELECT * FROM $table WHERE id = ?"
                    val cursor = readableDatabase.rawQuery(sql, arrayOf(ids))

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
            }
        }
        Log.i(DURATION, "get $tableName with id $ids took ${duration.readableDuration()}")
        return item
    }

    inline fun <reified T : Any> getById(id: Int): T? {
        var item: T? = null
        val entity = T::class

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

    suspend inline fun <reified T : Any> suspendGetById(ids: Int): T? {
        var item: T? = null
        var tableName: String? = ""

        val duration = measureTimeMillis {
            withContext(Dispatchers.IO) {
                val entity = T::class
                entity.whenTableCreated { table, fields ->
                    tableName = table
                    val sql = "SELECT * FROM $table WHERE id = ?"
                    val cursor = readableDatabase.rawQuery(sql, arrayOf(ids.toString()))

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
            }
        }
        Log.i(DURATION, "get $tableName with id $ids took ${duration.readableDuration()}")
        return item
    }

    inline fun <reified T: Any> insert(model: T): Int {
        var ids = 0L
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
                        when (property.returnType.javaType) {
                            Boolean::class.java -> {
                                values.put(propertyName, propertyValue.toString().toBoolean())
                            }
                            ByteArray::class.java -> {
                                val byteArray = propertyValue as? ByteArray
                                values.put(propertyName, byteArray)
                            }
                            else -> {
                                values.put(propertyName, propertyValue.toString())
                            }
                        }
                    }

                    property.isAccessible = false
                }

            ids = writableDatabase.insert(table, null, values)
            close()
        }
        return ids.toInt()
    }

    suspend inline fun <reified T: Any> suspendInsert(model: T): Int {
        var ids = 0L
        var tableName: String? = ""

        val duration = measureTimeMillis {
            withContext(Dispatchers.IO) {
                val entity = T::class
                val gson = Gson()

                entity.whenTableCreated { table, fields ->
                    tableName = table

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
                                when (property.returnType.javaType) {
                                    Boolean::class.java -> {
                                        values.put(propertyName, propertyValue.toString().toBoolean())
                                    }
                                    ByteArray::class.java -> {
                                        val byteArray = propertyValue as? ByteArray
                                        values.put(propertyName, byteArray)
                                    }
                                    else -> {
                                        values.put(propertyName, propertyValue.toString())
                                    }
                                }
                            }

                            property.isAccessible = false
                        }

                    ids = writableDatabase.insert(table, null, values)
                    close()
                }
            }
        }
        Log.i(DURATION, "insert $tableName with id $ids took ${duration.readableDuration()}")
        return ids.toInt()
    }

    inline fun <reified T: Any> update(model: T): Int {
        var ids = 0
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
                    when (property.returnType.javaType) {
                        Boolean::class.java -> {
                            values.put(propertyName, propertyValue.toString().toBoolean())
                        }
                        ByteArray::class.java -> {
                            val byteArray = propertyValue as? ByteArray
                            values.put(propertyName, byteArray)
                        }
                        else -> {
                            values.put(propertyName, propertyValue.toString())
                        }
                    }
                }
                property.isAccessible = false
            }

            ids = writableDatabase.update(table, values, "id = ?", arrayOf(id))
            close()
        }
        return ids
    }

    suspend inline fun <reified T: Any> suspendUpdate(model: T): Int {
        var ids = 0
        var tableName: String? = ""

        val duration = measureTimeMillis {
            withContext(Dispatchers.IO) {
                val entity = T::class
                val gson = Gson()

                entity.whenTableCreated { table, fields ->
                    tableName = table
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
                            when (property.returnType.javaType) {
                                Boolean::class.java -> {
                                    values.put(propertyName, propertyValue.toString().toBoolean())
                                }
                                ByteArray::class.java -> {
                                    val byteArray = propertyValue as? ByteArray
                                    values.put(propertyName, byteArray)
                                }
                                else -> {
                                    values.put(propertyName, propertyValue.toString())
                                }
                            }
                        }
                        property.isAccessible = false
                    }

                    ids = writableDatabase.update(table, values, "id = ?", arrayOf(id))
                    close()
                }
            }
        }
        Log.i(DURATION, "update $tableName with id $ids took ${duration.readableDuration()}")
        return ids
    }

    inline fun <reified T: Any> delete(model: T): Int {
        var ids = 0
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

            ids = writableDatabase.delete(table, "id = ?", arrayOf(id))
            close()
        }
        return ids
    }

    suspend inline fun <reified T: Any> suspendDelete(model: T): Int {
        var ids = 0
        var tableName: String? = ""

        val duration = measureTimeMillis {
            withContext(Dispatchers.IO) {
                val entity = T::class
                entity.whenTableCreated { table, fields ->
                    tableName = table
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

                    ids = writableDatabase.delete(table, "id = ?", arrayOf(id))
                    close()
                }
            }
        }
        Log.i(DURATION, "delete $tableName with id $ids took ${duration.readableDuration()}")
        return ids
    }

    fun <T: Any> deleteAll(entity: KClass<T>) {
        entity.whenTableCreated { table, _ ->
            val sql = "DELETE FROM $table"
            writableDatabase.execSQL(sql)
            close()
        }
    }

    suspend fun <T: Any> suspendDeleteAll(entity: KClass<T>) {
        var tableName: String? = ""

        val duration = measureTimeMillis {
            withContext(Dispatchers.IO) {
                entity.whenTableCreated { table, _ ->
                    tableName = table
                    val sql = "DELETE FROM $table"
                    writableDatabase.execSQL(sql)
                    close()
                }
            }
        }
        Log.i(DURATION, "deleteAll $tableName took ${duration.readableDuration()}")
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

    fun Long.readableDuration(): String {
        return when {
            this < 1000 -> "$this ms"
            this < 60_000 -> String.format("%.1f s", this / 1000.0)
            this < 3_600_000 -> String.format("%.1f min", this / 60_000.0)
            else -> String.format("%.1f hours", this / 3_600_000.0)
        }
    }
}