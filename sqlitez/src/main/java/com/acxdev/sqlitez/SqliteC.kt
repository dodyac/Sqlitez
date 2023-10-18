package com.acxdev.sqlitez

import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

class SqliteC(context: Context?) : BaseSQLite(context) {

    suspend fun <T : Any> getAll(entity: KClass<T>, condition: Pair<String, Any>? = null): List<T> {
        val items = mutableListOf<T>()
        var tableName: String? = ""
        val log = if (condition != null) {
            "where ${condition.first} = ${condition.second}"
        } else {
            ""
        }

        measureTimeMillis {
            withContext(Dispatchers.IO) {
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
                    close()
                }
            }
        }.logDuration("getAll $tableName $log")

        return items
    }

    suspend inline fun <reified T : Any> get(condition: Pair<String, Any>): T? {
        var item: T? = null
        var tableName: String? = ""

        measureTimeMillis {
            withContext(Dispatchers.IO) {
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
                    close()
                }
            }
        }.logDuration("get $tableName where ${condition.first} = ${condition.second}")

        return item
    }

    suspend inline fun <reified T: Any> insert(model: T): Int {
        var ids = 0L
        var tableName: String? = ""

        measureTimeMillis {
            withContext(Dispatchers.IO) {
                val entity = T::class
                entity.whenTableCreated { table, fields ->
                    tableName = table
                    val values = ContentValues()

                    fields.filter { field -> field.name != "id" }
                        .forEach { field ->
                            field.putContentValues(model, values)
                        }

                    ids = writableDatabase.insert(table, null, values)
                    close()
                }
            }
        }.logDuration("insert $tableName with id $ids")

        return ids.toInt()
    }

    suspend inline fun <reified T: Any> update(model: T): Int {
        var ids = "0"
        var tableName: String? = ""

        measureTimeMillis {
            withContext(Dispatchers.IO) {
                val entity = T::class
                entity.whenTableCreated { table, fields ->
                    tableName = table
                    val values = ContentValues()

                    fields.forEach { field ->
                        field.putContentValues(model, values) {
                            ids = it
                        }
                    }

                    writableDatabase.update(table, values, "id = ?", arrayOf(ids))
                    close()
                }
            }
        }.logDuration("update $tableName with id $ids")

        return ids.toInt()
    }

    suspend inline fun <reified T: Any> delete(model: T): Int {
        var ids = "0"
        var tableName: String? = ""

        measureTimeMillis {
            withContext(Dispatchers.IO) {
                val entity = T::class
                entity.whenTableCreated { table, fields ->
                    tableName = table

                    fields.find { it.name == "id" }
                        ?.let { field ->
                            field.isAccessible = true

                            val value = field.call(model)
                            ids = value.toString()

                            field.isAccessible = false
                        }

                    writableDatabase.delete(table, "id = ?", arrayOf(ids))
                    close()
                }
            }
        }.logDuration("delete $tableName with id $ids")

        return ids.toInt()
    }

    suspend fun <T: Any> deleteAll(entity: KClass<T>) {
        var tableName: String? = ""

        measureTimeMillis {
            withContext(Dispatchers.IO) {
                entity.whenTableCreated { table, _ ->
                    tableName = table
                    val sql = "DELETE FROM $table"
                    writableDatabase.execSQL(sql)
                    close()
                }
            }
        }.logDuration("deleteAll $tableName")
    }
}