package com.acxdev.sqlitez

import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import android.content.ContentValues
import android.content.Context
import com.acxdev.sqlitez.Utils.primaryKey
import kotlin.reflect.KProperty1
import kotlin.system.measureTimeMillis

class SqliteZ(context: Context?) : BaseSQLite(context) {

    fun <T : Any> getAll(entity: KClass<T>, condition: Pair<KProperty1<T, Any>, Any>? = null): List<T> {
        val items = mutableListOf<T>()
        var tableName: String? = ""
        val log = if (condition != null) {
            "where ${condition.first.name} = ${condition.second}"
        } else {
            ""
        }

        measureTimeMillis {
            entity.whenTableCreated { table, fields ->
                tableName = table

                val cursor = condition.getCursor(tableName)
                whenCursorMoved {
                    while (cursor.moveToNext()) {
                        cursor.getArgs(entity.primaryConstructor, fields)?.let {
                            items.add(it)
                        }
                    }
                }
                cursor.close()
            }
        }.logDuration("getAll ${items.size} row $tableName $log")

        return items
    }

    inline fun <reified T : Any> get(condition: Pair<KProperty1<T, Any>, Any>): T? {
        var item: T? = null
        var tableName: String? = ""

        measureTimeMillis {
            val entity = T::class
            entity.whenTableCreated { table, fields ->
                tableName = table

                val cursor = condition.getCursor(tableName)
                whenCursorMoved {
                    if (cursor.moveToFirst()) {
                        item = cursor.getArgs(entity.primaryConstructor, fields)
                    }
                }
                cursor.close()
            }
        }.logDuration("get $tableName where ${condition.first.name} = ${condition.second}")

        return item
    }

    inline fun <reified T: Any> insertAll(models: List<T>) {
        var tableName: String? = ""

        measureTimeMillis {
            val entity = T::class
            entity.whenTableCreated { table, fields ->
                tableName = table
                models.forEach { model ->
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