package com.acxdev.sqlitez

import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import android.content.ContentValues
import android.content.Context
import com.acxdev.sqlitez.common.Utils.primaryKey
import com.acxdev.sqlitez.read.Condition
import com.acxdev.sqlitez.read.Query
import com.acxdev.sqlitez.read.Readable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

class SqliteZ(context: Context?) : BaseSQLite(context) {

    inline fun <reified T : Any> getAll(
        vararg conditions: Condition = arrayOf()
    ): List<T> {
        val items = mutableListOf<T>()
        var tableName: String? = ""
        var log = ""

        val entity = T::class
        measureTimeMillis {
            entity.whenTableCreated { table, fields ->
                tableName = table

                val cursor = Readable(
                    query = Query.SelectAll,
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
        }.logDuration("getAll $tableName $log with ${items.size} row")

        return items
    }

    suspend inline fun <reified T : Any> getAllSuspend(
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

                    val cursor = Readable(
                        query = Query.SelectAll,
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

    inline fun <reified T : Any> getCount(
        vararg conditions: Condition = arrayOf()
    ): Int {
        var tableName: String? = ""
        var count = 0
        var log = ""

        val entity = T::class
        measureTimeMillis {
            entity.whenTableCreated { table, _ ->
                tableName = table

                val cursor = Readable(
                    query = Query.SelectCount,
                    conditions = conditions.toList()
                ).getCursor(tableName) {
                    log = it
                }
                cursor.whenMoved {
                    count = cursor.getInt(0)
                }
                cursor.close()
            }
        }.logDuration("getCount $tableName is $count $log")

        return count
    }

    suspend inline fun <reified T : Any> getCountSuspend(
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

                    val cursor = Readable(
                        query = Query.SelectCount,
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

    inline fun <reified T : Any> get(
        vararg conditions: Condition = arrayOf()
    ): T? {
        var item: T? = null
        var tableName: String? = ""
        var log = ""

        val entity = T::class
        measureTimeMillis {
            entity.whenTableCreated { table, fields ->
                tableName = table

                val cursor = Readable(
                    query = Query.Select,
                    conditions = conditions.toList()
                ).getCursor(tableName) {
                    log = it
                }
                cursor.whenMoved {
                    item = cursor.getArgs(entity.primaryConstructor, fields)
                }
                cursor.close()
            }
        }.logDuration("get $tableName $log")

        return item
    }

    suspend inline fun <reified T : Any> getSuspend(
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

                    val cursor = Readable(
                        query = Query.Select,
                        conditions = conditions.toList()
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

    inline fun <reified T: Any> insertAll(
        models: List<T>
    ) {
        var tableName: String? = ""

        val entity = T::class
        measureTimeMillis {
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
        }.logDuration("insertAll ${models.size} row into $tableName")
    }

    suspend inline fun <reified T: Any> insertAllSuspend(
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

    inline fun <reified T: Any> insert(
        model: T
    ): Int {
        var ids = 0L
        var tableName: String? = ""

        val entity = T::class
        measureTimeMillis {
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
        }.logDuration("insert $tableName with ${entity.primaryKey} $ids")

        return ids.toInt()
    }

    suspend inline fun <reified T: Any> insertSuspend(
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

    inline fun <reified T: Any> update(
        model: T
    ): Int {
        var ids = "0"
        var tableName: String? = ""

        val entity = T::class
        measureTimeMillis {
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
        }.logDuration("update $tableName with ${entity.primaryKey} $ids")

        return ids.toInt()
    }

    suspend inline fun <reified T: Any> updateSuspend(
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

    inline fun <reified T: Any> delete(
        model: T
    ): Int {
        var ids = "0"
        var tableName: String? = ""

        val entity = T::class
        measureTimeMillis {
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
        }.logDuration("delete $tableName with ${entity.primaryKey} $ids")

        return ids.toInt()
    }

    suspend inline fun <reified T: Any> deleteSuspend(
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

    inline fun <reified T: Any> deleteAll() {
        var tableName: String? = ""

        val entity = T::class
        measureTimeMillis {
            entity.whenTableCreated { table, _ ->
                tableName = table
                val sql = "DELETE FROM $table"
                writableDatabase.execSQL(sql)
            }
        }.logDuration("deleteAll $tableName")
    }

    suspend inline fun <reified T: Any> deleteAllSuspend() {
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
}