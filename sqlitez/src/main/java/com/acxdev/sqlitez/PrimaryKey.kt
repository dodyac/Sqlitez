package com.acxdev.sqlitez

import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.javaType

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class PrimaryKey(
//    val strategy: Strategy = Strategy.Replace
) {
    enum class Strategy {
        /**
         * OnConflict strategy constant to replace the old data and continue the transaction.
         *
         *
         * An [Insert] method that returns the inserted rows ids will never return -1 since
         * this strategy will always insert a row even if there is a conflict.
         */
        Replace,
        /**
         * OnConflict strategy constant to abort the transaction. *The transaction is rolled
         * back.*
         */
        Abort,
        /**
         * OnConflict strategy constant to ignore the conflict.
         *
         *
         * An [Insert] method that returns the inserted rows ids will return -1 for rows
         * that are not inserted since this strategy will ignore the row if there is a conflict.
         */
        Ignore
    }
}
