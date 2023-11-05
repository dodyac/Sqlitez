package com.acxdev.sqlitez

import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import android.content.ContentValues
import android.content.Context
import kotlin.reflect.KProperty1
import kotlin.system.measureTimeMillis

class SqliteZ(context: Context?) : BaseSQLite(context) {

    fun <T : Any> getAll(entity: KClass<T>, condition: Pair<KProperty1<T, String>, Any>? = null): List<T> {
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

                try {
                    val cursor = condition.getCursor(tableName)

                    whenCursorMoved {
                        while (cursor.moveToNext()) {
                            cursor.getArgs(entity.primaryConstructor, fields)?.let {
                                items.add(it)
                            }
                        }
                    }
                    cursor.close()
                } finally {
                    close()
                }
            }
        }.logDuration("getAll $tableName $log")

        return items
    }

    inline fun <reified T : Any> get(condition: Pair<KProperty1<T, String>, Any>): T? {
        var item: T? = null
        var tableName: String? = ""

        measureTimeMillis {
            val entity = T::class
            entity.whenTableCreated { table, fields ->
                tableName = table

                try {
                    val cursor = condition.getCursor(tableName)

                    whenCursorMoved {
                        if (cursor.moveToFirst()) {
                            item = cursor.getArgs(entity.primaryConstructor, fields)
                        }
                    }
                    cursor.close()
                } finally {
                    close()
                }
            }
        }.logDuration("get $tableName where ${condition.first.name} = ${condition.second}")

        return item
    }

    inline fun <reified T: Any> insert(model: T): Int {
        var ids = 0L
        var tableName: String? = ""

        measureTimeMillis {
            val entity = T::class
            entity.whenTableCreated { table, fields ->
                tableName = table

                try {
                    val values = ContentValues()

                    fields.filter { field -> field.name != "_id" }
                        .forEach { field ->
                            field.putContentValues(model, values)
                        }
                    ids = writableDatabase.insert(table, null, values)
                } finally {
                    close()
                }
            }
        }.logDuration("insert $tableName with _id $ids")

        return ids.toInt()
    }

    inline fun <reified T: Any> update(model: T): Int {
        var ids = "0"
        var tableName: String? = ""

        measureTimeMillis {
            val entity = T::class
            entity.whenTableCreated { table, fields ->
                tableName = table

                try {
                    val values = ContentValues()

                    fields.forEach { field ->
                        field.putContentValues(model, values) {
                            ids = it
                        }
                    }
                    writableDatabase.update(table, values, "_id = ?", arrayOf(ids))
                } finally {
                    close()
                }
            }
        }.logDuration("update $tableName with _id $ids")

        return ids.toInt()
    }

    inline fun <reified T: Any> delete(model: T): Int {
        var ids = "0"
        var tableName: String? = ""

        measureTimeMillis {
            val entity = T::class
            entity.whenTableCreated { table, fields ->
                tableName = table

                try {
                    fields.find { it.name == "_id" }
                        ?.let { field ->
                            field.isAccessible = true

                            val value = field.call(model)
                            ids = value.toString()

                            field.isAccessible = false
                        }
                    writableDatabase.delete(table, "_id = ?", arrayOf(ids))
                } finally {
                    close()
                }
            }
        }.logDuration("delete $tableName with _id $ids")

        return ids.toInt()
    }

    fun <T: Any> deleteAll(entity: KClass<T>) {
        var tableName: String? = ""

        measureTimeMillis {
            entity.whenTableCreated { table, _ ->
                tableName = table

                try {
                    val sql = "DELETE FROM $table"
                    writableDatabase.execSQL(sql)
                } finally {
                    close()
                }
            }
        }.logDuration("deleteAll $tableName")
    }
}