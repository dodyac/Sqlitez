package com.acxdev.sqlitez

object DatabaseNameHolder {
    var dbName: String = "data.db"

    fun setDatabaseName(name: String) {
        dbName = name
    }
}