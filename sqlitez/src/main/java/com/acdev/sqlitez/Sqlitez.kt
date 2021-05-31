package com.acdev.sqlitez

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.os.Parcel
import android.os.Parcel.obtain
import com.google.gson.Gson
import nl.qbusict.cupboard.CupboardFactory.cupboard

open class Sqlitez(context: Context, entity: Class<*>) : AssetHelper(context, "data.db", null, 1) {

    init { cupboard().register(entity) }

    companion object {

        fun <T>Context.createDBTable(entity: Class<T>){
            val db = Sqlitez(this, entity)
            val item = entity.declaredFields
            val result = StringBuilder()
            result.append("CREATE TABLE IF NOT EXISTS ${entity.simpleName} (_id INTEGER PRIMARY KEY AUTOINCREMENT")
            for (field in item) {
                try { result.append(", ${field.name} TEXT") }
                catch (ex: IllegalAccessException) { println(ex) }
            }
            result.append(")")
            db.writableDatabase.execSQL(result.toString())
            db.close()
        }

        fun <T>Context.createDBTableDefaultPrimary(entity: Class<T>){
            val db = Sqlitez(this, entity)
            val item = entity.declaredFields
            val result = StringBuilder()
            result.append("CREATE TABLE IF NOT EXISTS ${entity.simpleName} (_id INTEGER PRIMARY KEY DEFAULT 1")
            for (field in item) {
                try { result.append(", ${field.name} TEXT") }
                catch (ex: IllegalAccessException) { println(ex) }
            }
            result.append(")")
            db.writableDatabase.execSQL(result.toString())
            db.close()
        }

        private fun <T>changeToDataDB(entity: Class<T>): String{
            return when(entity.simpleName){
                "Int" -> "INTEGER"
                else -> "TEXT"
            }
        }

        fun <T> Context.readDBTable(entity: Class<T>): List<T> {
            val db = Sqlitez(this, entity).writableDatabase
            val a : MutableList<T> = ArrayList()
            val count = DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM ${entity.simpleName}", null)
            for(i in 1..count){
                val bunny = cupboard().withDatabase(db).query(entity).byId(i)
                a.add(bunny.get())
            }
            db.close()
            return a
        }

        fun <T> Context.readDB(entity: Class<T>, id: Long): T {
            val db = Sqlitez(this, entity).readableDatabase
            val user = cupboard().withDatabase(db)[entity, id]
            db.close()
            return user as T
        }


        fun <T> Context.insertDB(entity: Class<T>, model: T?) {
            val db = Sqlitez(this, entity).writableDatabase
            cupboard().withDatabase(db).put(model)
            db.close()
        }

        fun Context.deleteDB(entity: Class<*>, id: Long) {
            val db = Sqlitez(this, entity).writableDatabase
            cupboard().withDatabase(db).delete(entity, id)
            db.close()
        }

        fun Context.rowDBExist(entity: Class<*>, where: String, equal: String): Boolean {
            val db = Sqlitez(this, entity).readableDatabase
            val query = "SELECT * FROM ${entity.simpleName} WHERE $where = $equal"
            val cursor: Cursor = db.rawQuery(query, null)
            if (cursor.count <= 0) {
                cursor.close()
                db.close()
                return false
            }
            cursor.close()
            db.close()
            return true
        }

        fun <T>Context.updateDB(entity: Class<T>, model: T?){
            val json = Gson().toJson(model).replace(":", "=").removePrefix("{").removeSuffix("}")
            val db = AssetHelper(this, "data.db", null, 1).writableDatabase
            val sql = "UPDATE ${entity.simpleName} SET $json WHERE _id=1"
            println(sql)
            db.execSQL(sql)
            db.close()
        }
        
        fun <T>Context.updateDBById(entity: Class<T>, model: T?, id: Long){
            val json = Gson().toJson(model).replace(":", "=").removePrefix("{").removeSuffix("}")
            val db = AssetHelper(this, "data.db", null, 1).writableDatabase
            val sql = "UPDATE ${entity.simpleName} SET $json WHERE _id=$id"
            println(sql)
            db.execSQL(sql)
            db.close()
        }
    }
}