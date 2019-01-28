package com.boostcamp.travery.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.boostcamp.travery.data.model.Activity
import com.boostcamp.travery.data.model.Route

/**
 * room database route 테이블과 activity 테이블을 가짐.
 */
@Database(entities = [Route::class, Activity::class], version = 1)
@TypeConverters(Converters::class)
abstract class DataBase : RoomDatabase() {
    companion object {
        private var INSTANCE: DataBase? = null
        fun getDataBase(context: Context): DataBase {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(context, DataBase::class.java, "travery")
                    .build()
            }
            return INSTANCE as DataBase
        }
    }

    abstract fun daoActivity(): ActivityDao
    abstract fun daoRoute(): RouteDao
}