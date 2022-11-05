package com.example.test

import android.content.Context
import androidx.room.Room

object DatabaseBuilder {
    private var INSTANCE: NoteDatabase? = null
    fun getInstance(context: Context): NoteDatabase {
        if (INSTANCE == null) {
            synchronized(NoteDatabase::class) {
                INSTANCE = buildRoomDB(context)
            }
        }
        return INSTANCE!!
    }

    private fun buildRoomDB(context: Context) =
        Room.databaseBuilder(
            context.applicationContext,
            NoteDatabase::class.java,
            DB_NAME
        ).build()

    fun reloadDatabase(context: Context): NoteDatabase {
        INSTANCE = null
        return getInstance(context)
    }
}