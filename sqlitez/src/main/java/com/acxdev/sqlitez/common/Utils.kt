package com.acxdev.sqlitez.common

import kotlin.reflect.KClass
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.javaType

object Utils {

     val <T : Any> KClass<T>.primaryKey: String
        get() {
            val clazz = this
            val parameterNames = (clazz.constructors as List).first().parameters.map { it.name }

            val fields = clazz.members.filter { it.name in parameterNames }
                .sortedBy { parameterNames.indexOf(it.name) }
            val primaryKeyField = fields.find {
                it.hasAnnotation<PrimaryKey>()
            }

            primaryKeyField?.let {
                if (primaryKeyField.returnType.javaType != Int::class.java) {
                    throw IllegalArgumentException("Right now, Primary Key only support Int type")
                }

                return primaryKeyField.name
            } ?: run {
                //default primary key
                return "_id"
            }
        }
}