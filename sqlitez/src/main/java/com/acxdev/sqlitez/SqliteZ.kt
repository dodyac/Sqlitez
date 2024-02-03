package com.acxdev.sqlitez

import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import android.content.ContentValues
import android.content.Context
import com.acxdev.sqlitez.common.Utils.primaryKey
import com.acxdev.sqlitez.read.Condition
import com.acxdev.sqlitez.read.Query
import com.acxdev.sqlitez.read.Readable
import kotlin.system.measureTimeMillis

class SqliteZ(context: Context?) : BaseSQLite(context) {

    fun <T : Any> getAll(
        entity: KClass<T>,
        vararg conditions: Condition = arrayOf()
    ): List<T> {
        val items = mutableListOf<T>()
        var tableName: String? = ""
        var log = ""

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

    fun <T : Any> getCount(
        entity: KClass<T>,
        vararg conditions: Condition = arrayOf()
    ): Int {
        var tableName: String? = ""
        var count = 0
        var log = ""

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

    inline fun <reified T : Any> get(
        vararg conditions: Condition = arrayOf()
    ): T? {
        var item: T? = null
        var tableName: String? = ""
        var log = ""

        measureTimeMillis {
            val entity = T::class
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

    inline fun <reified T: Any> insertAll(models: List<T>) {
        var tableName: String? = ""

        measureTimeMillis {
            val entity = T::class
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

    inline fun <reified T: Any> insert(model: T): Int {
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

    inline fun <reified T: Any> update(model: T): Int {
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

    inline fun <reified T: Any> delete(model: T): Int {
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

    fun <T: Any> deleteAll(entity: KClass<T>) {
        var tableName: String? = ""

        measureTimeMillis {
            entity.whenTableCreated { table, _ ->
                tableName = table
                val sql = "DELETE FROM $table"
                writableDatabase.execSQL(sql)
            }
        }.logDuration("deleteAll $tableName")
    }
}