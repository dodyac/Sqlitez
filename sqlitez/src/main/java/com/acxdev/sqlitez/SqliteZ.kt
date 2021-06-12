package com.acxdev.sqlitez

import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.google.gson.Gson
import nl.qbusict.cupboard.CupboardFactory
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

open class SqliteZ(val context: Context, entity: Class<*>) : SQLiteOpenHelper(context, "data.db", null, 1) {

    init { CupboardFactory.cupboard().register(entity) }

    override fun onCreate(db: SQLiteDatabase?) {}

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "Upgrading database from version $oldVersion to $newVersion...")
        val paths = java.util.ArrayList<String>()
//        getUpgradeFilePaths(oldVersion, newVersion - 1, newVersion, paths)
        if (paths.isEmpty()) {
            Log.e(TAG, "no upgrade script path from $oldVersion to $newVersion")
            throw AssetHelper.SQLiteAssetException("no upgrade script path from $oldVersion to $newVersion")
        }
        Collections.sort(paths, VersionComparator())
        for (path in paths) {
            try {
                Log.w(TAG, "processing upgrade: $path")
                val `is` = context.assets.open(path)
                val sql = Utils.convertStreamToString(`is`)
                val cmds = Utils.splitSqlScript(sql, ';')
                for (cmd in cmds) { if (cmd.trim { it <= ' ' }.isNotEmpty()) db?.execSQL(cmd) }
            } catch (e: IOException) { e.printStackTrace() }
        }
        Log.w(TAG, "Successfully upgraded database from version $oldVersion to $newVersion")
    }

//    private fun getUpgradeSQLStream(oldVersion: Int, newVersion: Int): InputStream? {
//        val path = String.format(mUpgradePathFormat, oldVersion, newVersion)
//        return try { context.assets.open(path) }
//        catch (e: IOException) {
//            Log.w(TAG, "missing database upgrade script: $path")
//            null
//        }
//    }
//
//    private fun getUpgradeFilePaths(baseVersion: Int, start: Int, end: Int, paths: java.util.ArrayList<String>) {
//        val a: Int
//        val b: Int
//        var `is` = getUpgradeSQLStream(start, end)
//        if (`is` != null) {
//            val path = String.format(mUpgradePathFormat, start, end)
//            paths.add(path)
//            a = start - 1
//            b = start
//            `is` = null
//        } else {
//            a = start - 1
//            b = end
//        }
//        if (a < baseVersion) return
//        else getUpgradeFilePaths(baseVersion, a, b, paths)
//    }

    companion object {

        private const val TAG = "SqliteZ"

        fun <T>Context.createDBTable(entity: Class<T>){
            try {
                val db = SqliteZ(this, entity)
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
            } catch (ex: IOException){ Log.e(TAG, "Table ${entity.simpleName} Failed to Create \n${ex.message}") }
        }

        fun <T>Context.createDBTableDefaultPrimary(entity: Class<T>){
            try {
                val db = SqliteZ(this, entity)
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
            } catch (ex: IOException){ Log.e(TAG, "Table ${entity.simpleName} Failed to Create \n${ex.message}") }
        }

        private fun <T>changeToDataDB(entity: Class<T>): String{
            return when(entity.simpleName){
                "Int" -> "INTEGER"
                else -> "TEXT"
            }
        }

        fun <T> Context.readDBTable(entity: Class<T>): List<T> {
            val db = SqliteZ(this, entity).writableDatabase
            val a : MutableList<T> = ArrayList()
            val count = DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM ${entity.simpleName}", null)
            for(i in 1..count){
                val bunny = CupboardFactory.cupboard().withDatabase(db).query(entity).byId(i)
                a.add(bunny.get())
            }
            db.close()
            Log.v(TAG, Gson().toJson(a))
            return a
        }

        fun <T>Context.readDBDefaultPrimary(entity: Class<T>): T {
            val db = SqliteZ(this, entity).readableDatabase
            val user = CupboardFactory.cupboard().withDatabase(db)[entity, 1L]
            db.close()
            Log.v(TAG, Gson().toJson(user))
            return user as T
        }

        fun <T>Context.readDB(entity: Class<T>, id: Long): T {
            val db = SqliteZ(this, entity).readableDatabase
            val user = CupboardFactory.cupboard().withDatabase(db)[entity, id]
            db.close()
            Log.v(TAG, Gson().toJson(user))
            return user as T
        }

        fun <T>Context.insertDB(entity: Class<T>, model: T) {
            try {
                val db = SqliteZ(this, entity).writableDatabase
                CupboardFactory.cupboard().withDatabase(db).put(model)
                db.close()
                Log.v(TAG, "Successfully Insert Data ${Gson().toJson(model)}")
            } catch (ex: IOException){ Log.e(TAG, "Failed to Insert Data \n${ex.message.toString()}") }
        }

        fun <T>Context.deleteDB(entity: Class<T>, id: Long) {
            try {
                val db = SqliteZ(this, entity).writableDatabase
                CupboardFactory.cupboard().withDatabase(db).delete(entity, id)
                db.close()
                Log.v(TAG, "Successfully Delete Data From ${entity.simpleName} Where _id=$id")
            } catch (ex: IOException){ Log.e(TAG, "Failed to Delete \n${ex.message.toString()}") }
        }

        fun <T>Context.rowDBExist(entity: Class<T>, where: String, equal: String): Boolean {
            try {
                val db = SqliteZ(this, entity).readableDatabase
                val query = "SELECT * FROM ${entity.simpleName} WHERE $where = $equal"
                val cursor: Cursor = db.rawQuery(query, null)
                if (cursor.count <= 0) {
                    cursor.close()
                    db.close()
                    Log.v(TAG, "Database isn't Exist ${entity.simpleName} Where $where = $equal")
                    return false
                }
                cursor.close()
                db.close()
                Log.v(TAG, "Database Exist ${entity.simpleName} Where $where = $equal")
                return true
            } catch (ex: IOException){
                Log.e(TAG, "Cannot check Database ${ex.message.toString()}")
                return false
            }
        }

        fun <T>Context.updateDBDefaultPrimary(entity: Class<T>, model: T){
            try {
                val json = Gson().toJson(model).replace(":", "=").removePrefix("{").removeSuffix("}")
                val db = SqliteZ(this, entity).writableDatabase
                val sql = "UPDATE ${entity.simpleName} SET $json WHERE _id=1"
                println(sql)
                db.execSQL(sql)
                db.close()
                Log.v(TAG, "Successfully update data Table ${entity.simpleName} with ${Gson().toJson(model)}")
            } catch (ex: IOException){
                Log.e(TAG, "Failed update data Table ${entity.simpleName} with ${Gson().toJson(model)} \n with Error ${ex.message.toString()}")
            }
        }

        fun <T>Context.updateDB(entity: Class<T>, model: T, id: Long){
            try {
                val json = Gson().toJson(model).replace(":", "=").removePrefix("{").removeSuffix("}")
                val db = SqliteZ(this, entity).writableDatabase
                val sql = "UPDATE ${entity.simpleName} SET $json WHERE _id=$id"
                println(sql)
                db.execSQL(sql)
                db.close()
                Log.v(TAG, "Successfully update data Table ${entity.simpleName} with ${Gson().toJson(model)}")
            } catch (ex: IOException){
                Log.e(TAG, "Failed update data Table ${entity.simpleName} with ${Gson().toJson(model)} \n with Error ${ex.message.toString()}")
            }
        }
    }
}