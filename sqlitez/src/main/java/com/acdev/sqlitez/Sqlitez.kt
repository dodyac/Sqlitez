package com.acdev.sqlitez

import android.content.ContentValues
import android.content.Context
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
                try { result.append(", ${field.name} ${changeToDataDB(field.name::class.java)}") }
                catch (ex: IllegalAccessException) { println(ex) }
            }
            result.append(")")
            db.writableDatabase.execSQL(result.toString())
            db.close()
        }

        private fun <T>changeToDataDB(entity: Class<T>): String{
            return when(entity.simpleName){
                "String" -> "TEXT"
                "Long" -> "TEXT"
                "BigDecimal" -> "TEXT"
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
            return a
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> Context.readDB(entity: Class<*>, id: Long): T {
            val db = Sqlitez(this, entity).readableDatabase
            val user = cupboard().withDatabase(db)[entity, id]
            db.close()
            return user as T
        }


        fun <T> Context.insertDB(entity: Class<*>, model: T?) {
            val db = Sqlitez(this, entity).writableDatabase
            cupboard().withDatabase(db).put(model)
            db.close()
        }

        fun Context.deleteDB(entity: Class<*>, id: Long) {
            val db = Sqlitez(this, entity).writableDatabase
            cupboard().withDatabase(db).delete(entity, id)
            db.close()
        }

        fun <T> Context.updateDB(entity: Class<*>, model: T?, id: Long) {
            val db = Sqlitez(this, entity).writableDatabase
            val parcel: Parcel = obtain()
            parcel.writeMap(Gson().fromJson(Gson().toJson(model), HashMap::class.java))
            parcel.setDataPosition(0)
            cupboard().withDatabase(db).update(entity, ContentValues.CREATOR.createFromParcel(parcel), "_id = $id", null)
            db.close()
        }
    }
}