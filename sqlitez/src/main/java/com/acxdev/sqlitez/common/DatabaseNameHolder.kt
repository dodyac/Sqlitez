package com.acxdev.sqlitez.common

object DatabaseNameHolder {
    var dbName: String = "data.db"
    var dbVersion: Int = 1

    fun setDatabaseName(name: String) {
        dbName = name
    }

    fun setDatabaseVersion(version: Int) {
        dbVersion = version
    }
}