package com.acdev.sqlitez

import android.content.ContentValues
import android.content.Context
import android.os.Parcel
import android.os.Parcel.obtain
import com.google.gson.Gson
import nl.qbusict.cupboard.CupboardFactory.cupboard

open class Sqlitez(context: Context, entity: Class<*>) : AssetHelper(context, "data.db", null, 1) {

    init { cupboard().register(entity) }

    companion object {

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> Context.getUser(entity: Class<*>, id: Long): T {
            val db = Sqlitez(this, entity).readableDatabase
            val user = cupboard().withDatabase(db)[entity, id]
            db.close()
            return user as T
        }


        fun <T : Any> Context.insertUser(entity: Class<*>, model: T?) {
            val db = Sqlitez(this, entity).writableDatabase
            cupboard().withDatabase(db).put(model)
            db.close()
        }

        fun Context.deleteUser(entity: Class<*>, id: Long) {
            val db = Sqlitez(this, entity).writableDatabase
            cupboard().withDatabase(db).delete(entity, id)
            db.close()
        }

        fun <T : Any> Context.updateUser(entity: Class<*>, model: T?, id: Int) {
            val db = Sqlitez(this, entity).writableDatabase
            val parcel: Parcel = obtain()
            parcel.writeMap(Gson().fromJson(Gson().toJson(model), HashMap::class.java))
            parcel.setDataPosition(0)
            cupboard().withDatabase(db).update(entity, ContentValues.CREATOR.createFromParcel(parcel), "_id = ?", id.toString())
            db.close()
        }
    }
}