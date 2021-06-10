package com.acxdev.sqlitez

import android.util.Log
import java.util.*
import java.util.regex.Pattern

@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class VersionComparator : Comparator<String?> {
    private val pattern = Pattern.compile(".*_upgrade_([0-9]+)-([0-9]+).*")

    override fun compare(file0: String?, file1: String?): Int {
        val m0 = pattern.matcher(file0)
        val m1 = pattern.matcher(file1)
        if (!m0.matches()) {
            Log.w(TAG, "could not parse upgrade script file: $file0")
            throw AssetHelper.SQLiteAssetException("Invalid upgrade script file")
        }
        if (!m1.matches()) {
            Log.w(TAG, "could not parse upgrade script file: $file1")
            throw AssetHelper.SQLiteAssetException("Invalid upgrade script file")
        }
        val v0From = m0.group(1).toInt()
        val v1From = m1.group(1).toInt()
        val v0To = m0.group(2).toInt()
        val v1To = m1.group(2).toInt()
        if (v0From == v1From) {
            if (v0To == v1To) return 0
            return if (v0To < v1To) -1 else 1
        }
        return if (v0From < v1From) -1 else 1
    }

    companion object { private val TAG = AssetHelper::class.java.simpleName }
}
