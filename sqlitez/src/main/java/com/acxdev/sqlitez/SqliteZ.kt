package com.acxdev.sqlitez

import android.content.Context
import android.database.Cursor
import android.util.Log
import com.google.gson.Gson
import nl.qbusict.cupboard.CupboardFactory.cupboard

open class SqliteZ(
    context: Context,
    entity: Class<*>
) : AssetHelper(context, DatabaseNameHolder.dbName, null, 1) {

    init {
        cupboard().register(entity)
    }

    companion object {

        private const val TAG = "SqliteZ"

        fun <T>Context.sqLiteZCreateTable(entity: Class<T>) {
            try {
                val db = SqliteZ(this, entity)
                val item = entity.declaredFields
                val result = StringBuilder()
                result.append("CREATE TABLE IF NOT EXISTS ${entity.simpleName} (_id INTEGER PRIMARY KEY AUTOINCREMENT")
                for (field in item) {
                    try {
                        if(field.name != "_id") {
                            result.append(", ${field.name} TEXT")
                        }
                    }
                    catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                result.append(")")
                db.writableDatabase.execSQL(result.toString())
                db.close()
                Log.d(TAG, "Table ${entity.simpleName} Successfully Created")
            } catch (e: Exception) {
                Log.e(TAG, "Table ${entity.simpleName} Failed to Create")
                e.printStackTrace()
            }
        }

        fun Context.sqLiteZCreateTables(vararg entity: Class<*>) {
            try {
                entity.forEach {
                    val db = SqliteZ(this, it)
                    val item = it.declaredFields
                    val result = StringBuilder()
                    result.append("CREATE TABLE IF NOT EXISTS ${it.simpleName} (_id INTEGER PRIMARY KEY AUTOINCREMENT")
                    for (field in item) {
                        try {
                            if(field.name != "_id") {
                                result.append(", ${field.name} TEXT")
                            }
                        }
                        catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    result.append(")")
                    db.writableDatabase.execSQL(result.toString())
                    db.close()
                    Log.d(TAG, "Table ${it.simpleName} Successfully Created")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tables Failed to Create")
                e.printStackTrace()
            }
        }

        fun <T> Context.sqLiteZSelectTable(entity: Class<T>, shouldPrintLog: Boolean = false): List<T> {
            val db = SqliteZ(this, entity).writableDatabase
            val databaseList = cupboard().withDatabase(db).query(entity).list()
            db.close()
            if(shouldPrintLog) {
                Log.v(TAG, Gson().toJson(databaseList))
            }
            return databaseList
        }

        fun <T>Context.sqLiteZGetById(entity: Class<T>, id: Long, shouldPrintLog: Boolean = false): T {
            val db = SqliteZ(this, entity).readableDatabase
            val user = cupboard().withDatabase(db)[entity, id]
            db.close()
            if(shouldPrintLog) {
                Log.v(TAG, Gson().toJson(user))
            }
            return user as T
        }

        fun <T>Context.sqLiteZInsert(entity: Class<T>, model: T) {
            try {
                val db = SqliteZ(this, entity).writableDatabase
                cupboard().withDatabase(db).put(model)
                db.close()
                Log.v(TAG, "Successfully Insert Data ${Gson().toJson(model)}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to Insert Data")
                e.printStackTrace()
            }
        }

        fun <T>Context.sqliteZDeleteById(entity: Class<T>, id: Long) {
            try {
                val db = SqliteZ(this, entity).writableDatabase
                cupboard().withDatabase(db).delete(entity, id)
                db.close()
                Log.v(TAG, "Successfully Delete Data From ${entity.simpleName} Where _id=$id")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to Delete")
                e.printStackTrace()
            }
        }

        fun <T>Context.sqliteZDelete(entity: Class<T>, model: T) {
            try {
                val db = SqliteZ(this, entity).writableDatabase
                cupboard().withDatabase(db).delete(model)
                db.close()
                Log.v(TAG, "Successfully Delete Data From ${entity.simpleName} Where ${Gson().toJson(model)}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to Delete")
                e.printStackTrace()
            }
        }

        fun <T>Context.sqliteZDrop(entity: Class<T>) {
            try {
                val db = SqliteZ(this, entity).writableDatabase
                cupboard().withDatabase(db).delete(entity, null)
                db.close()
                Log.v(TAG, "Successfully Drop Table ${entity.simpleName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to Drop Table")
                e.printStackTrace()
            }
        }

        fun <T>Context.sqLiteZIsExist(entity: Class<T>, where: String, equal: String): Boolean {
            return try {
                val db = SqliteZ(this, entity).readableDatabase
                val query = "SELECT * FROM ${entity.simpleName} WHERE $where = '$equal'"
                val cursor: Cursor = db.rawQuery(query, null)
                return if (cursor.count <= 0) {
                    cursor.close()
                    db.close()
                    Log.v(TAG, "Database isn't Exist ${entity.simpleName} Where $where = $equal")
                    false
                } else {
                    cursor.close()
                    db.close()
                    Log.v(TAG, "Database Exist ${entity.simpleName} Where $where = $equal")
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot check Database")
                e.printStackTrace()
                false
            }
        }
    }
}