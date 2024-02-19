package com.acxdev.sqlitez

import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.core.database.getDoubleOrNull
import androidx.core.database.getFloatOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import com.acxdev.sqlitez.common.Utils.primaryKey
import com.acxdev.sqlitez.read.Condition
import com.acxdev.sqlitez.read.Query
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaType
import kotlin.system.measureTimeMillis

class SqliteZ(context: Context?) : BaseSQLite(context) {

    suspend inline fun <reified T: Any> insert(
        model: T
    ): Int {
        var ids = 0L
        var tableName: String? = ""

        val entity = T::class
        measureTimeMillis {
            withContext(Dispatchers.IO) {
                entity.whenTableCreated { table, fields ->
                    tableName = table
                    val values = ContentValues()
                    fields.filter { field -> field.name != entity.primaryKey }
                        .forEach { field ->
                            field.putContentValues(
                                entity = entity,
                                model = model,
                                contentValues = values
                            )
                        }
                    ids = writableDatabase.insert(table, null, values)
                }
            }
        }.logDuration("insert $tableName with ${entity.primaryKey} $ids")

        return ids.toInt()
    }

    suspend inline fun <reified T: Any> insertAll(
        models: List<T>
    ) {
        var tableName: String? = ""

        val entity = T::class
        measureTimeMillis {
            withContext(Dispatchers.IO) {
                entity.whenTableCreated { table, fields ->
                    tableName = table
                    val db = writableDatabase
                    db.beginTransaction()
                    try {
                        models.forEach { model ->
                            val values = ContentValues()

                            fields.filter { field -> field.name != entity.primaryKey }
                                .forEach { field ->
                                    field.putContentValues(
                                        entity = entity,
                                        model = model,
                                        contentValues = values
                                    )
                                }
                            db.insert(table, null, values)
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            }
        }.logDuration("insertAll ${models.size} row into $tableName")
    }

    suspend inline fun <reified T : Any> getCount(
        vararg conditions: Condition = arrayOf()
    ): Int {
        var tableName: String? = ""
        var count = 0
        var log = ""

        val entity = T::class
        measureTimeMillis {
            withContext(Dispatchers.IO) {
                entity.whenTableCreated { table, _ ->
                    tableName = table
                    val cursor = Query.SelectCount(
                        conditions = conditions.toList()
                    ).getCursor(tableName) {
                        log = it
                    }
                    cursor.whenMoved {
                        count = cursor.getInt(0)
                    }
                    cursor.close()
                }
            }
        }.logDuration("getCount $tableName is $count $log")

        return count
    }

    suspend inline fun <reified T : Any> get(
        vararg conditions: Condition = arrayOf()
    ): T? {
        var item: T? = null
        var tableName: String? = ""
        var log = ""

        val entity = T::class
        measureTimeMillis {
            withContext(Dispatchers.IO) {
                entity.whenTableCreated { table, fields ->
                    tableName = table

                    val conditionsGet = mutableListOf<Condition>()
                    conditionsGet.add(Condition.Limit(1))
                    conditionsGet.addAll(conditions.toList())

                    val cursor = Query.SelectAll(
                        conditions = conditionsGet
                    ).getCursor(tableName) {
                        log = it
                    }
                    cursor.whenMoved {
                        item = cursor.getArgs(entity.primaryConstructor, fields)
                    }
                    cursor.close()
                }
            }
        }.logDuration("get $tableName $log")

        return item
    }

    suspend inline fun <reified T : Any, reified O : Any> getOf(
        variables: List<KProperty1<T, Any>>,
        vararg conditions: Condition = arrayOf()
    ): O? {
        var item: O? = null
        var tableName: String? = ""
        var log = ""

        val entity = T::class
        val entityOutput = O::class
        measureTimeMillis {
            withContext(Dispatchers.IO) {
                entity.whenTableCreated { table, fields ->
                    tableName = table

                    val conditionsGet = mutableListOf<Condition>()
                    conditionsGet.add(Condition.Limit(1))
                    conditionsGet.addAll(conditions.toList())

                    val cursor = Query.SelectOf(
                        variables = variables,
                        conditions = conditionsGet
                    ).getCursor(tableName) {
                        log = it
                    }
                    cursor.whenMoved {
                        val outputFields = entityOutput.getFields()
                        val filteredFields = outputFields.intersect(fields.toSet()).toList()
                        item = cursor.getArgs(entityOutput.primaryConstructor, filteredFields)
                    }
                    cursor.close()
                }
            }
        }.logDuration("getOf $tableName $log")

        return item
    }

    suspend inline fun <reified T : Any> getAll(
        vararg conditions: Condition = arrayOf()
    ): List<T> {
        val items = mutableListOf<T>()
        var tableName: String? = ""
        var log = ""

        val entity = T::class
        measureTimeMillis {
            withContext(Dispatchers.IO) {
                entity.whenTableCreated { table, fields ->
                    tableName = table

                    val cursor = Query.SelectAll(
                        conditions = conditions.toList()
                    ).getCursor(tableName) {
                        log = it
                    }
                    cursor.whenMoved {
                        cursor.getArgs(entity.primaryConstructor, fields)?.let {
                            items.add(it)
                        }
                    }
                    cursor.close()
                }
            }
        }.logDuration("getAll $tableName $log with ${items.size} row")

        return items
    }

    suspend inline fun <reified T: Any, reified O : Any> getAllOf(
        variables: List<KProperty1<T, Any>>,
        vararg conditions: Condition = arrayOf()
    ): List<O> {
        val items = mutableListOf<O>()
        var tableName: String? = ""
        var log = ""

        val entity = T::class
        val entityOutput = O::class
        measureTimeMillis {
            withContext(Dispatchers.IO) {
                entity.whenTableCreated { table, fields ->
                    tableName = table

                    val cursor = Query.SelectOf(
                        variables = variables,
                        conditions = conditions.toList()
                    ).getCursor(tableName) {
                        log = it
                    }
                    cursor.whenMoved {
                        val outputFields = entityOutput.getFields()
                        val filteredFields = outputFields.intersect(fields.toSet()).toList()
                        cursor.getArgs(entityOutput.primaryConstructor, filteredFields)?.let {
                            items.add(it)
                        }
                    }
                    cursor.close()
                }
            }
        }.logDuration("getAllOf $tableName $log with ${items.size} row")

        return items
    }

    suspend inline fun <reified T: Any> update(
        model: T
    ): Int {
        var ids = "0"
        var tableName: String? = ""

        val entity = T::class
        measureTimeMillis {
            withContext(Dispatchers.IO) {
                entity.whenTableCreated { table, fields ->
                    tableName = table
                    val values = ContentValues()
                    fields.forEach { field ->
                        field.putContentValues(
                            entity = entity,
                            model = model,
                            contentValues = values
                        ) {
                            ids = it
                        }
                    }
                    writableDatabase.update(table, values, "${entity.primaryKey} = ?", arrayOf(ids))
                }
            }
        }.logDuration("update $tableName with ${entity.primaryKey} $ids")

        return ids.toInt()
    }

    suspend inline fun <reified T: Any> delete(
        model: T
    ): Int {
        var ids = "0"
        var tableName: String? = ""

        val entity = T::class
        measureTimeMillis {
            withContext(Dispatchers.IO) {
                entity.whenTableCreated { table, fields ->
                    tableName = table
                    fields.find { it.name == entity.primaryKey }
                        ?.let { field ->
                            field.isAccessible = true

                            val value = field.call(model)
                            ids = value.toString()

                            field.isAccessible = false
                        }
                    writableDatabase.delete(table, "${entity.primaryKey} = ?", arrayOf(ids))
                }
            }
        }.logDuration("delete $tableName with ${entity.primaryKey} $ids")

        return ids.toInt()
    }

    suspend inline fun <reified T: Any> deleteAll() {
        var tableName: String? = ""

        val entity = T::class
        measureTimeMillis {
            withContext(Dispatchers.IO) {
                entity.whenTableCreated { table, _ ->
                    tableName = table
                    val sql = "DELETE FROM $table"
                    writableDatabase.execSQL(sql)
                }
            }
        }.logDuration("deleteAll $tableName")
    }

    suspend inline fun <reified T: Any> dropTable() {
        var tableName: String? = ""

        val entity = T::class
        measureTimeMillis {
            withContext(Dispatchers.IO) {
                entity.whenTableCreated { table, _ ->
                    tableName = table
                    val sql = "DROP TABLE IF EXISTS $table"
                    writableDatabase.execSQL(sql)
                }
            }
        }.logDuration("dropTable $tableName")
    }

    suspend inline fun <reified T : Any> exportTo(
        file: File,
        vararg conditions: Condition = arrayOf()
    ) {
        measureTimeMillis {
            val list = getAll<T>(*conditions)
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use {
                    val json = Gson().toJson(list)
                    it.write(json.toByteArray())
                }
            }
        }.logDuration("exporting ${T::class.simpleName}")
    }

    //some error
    suspend inline fun <reified T : Any> restore(
        inputStream: InputStream,
    ) {
        measureTimeMillis {
            withContext(Dispatchers.IO) {
                val jsonFileString = inputStream.bufferedReader()
                    .use(BufferedReader::readText)
                try {
                    val list = Gson().fromJson(jsonFileString, Array<T>::class.java)
                        ?.toList() ?: emptyList()

                    insertAll(list)
                } catch (e: Exception) {
                    Log.e(TAG, "Restore Error")
                    e.printStackTrace()
                }
            }
        }.logDuration("restoring ${T::class.simpleName}")
    }
}