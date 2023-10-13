package com.acxdev.sqlitez

import android.content.Context

class SqliteZAssets(
    private val context: Context
) : AssetHelper(context, DatabaseNameHolder.dbName, null, 1) {

    fun sqliteZ(): SqliteZ {
        return SqliteZ(context)
    }
}