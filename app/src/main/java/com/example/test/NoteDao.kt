package com.example.test

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.IGNORE
import androidx.room.Query
import androidx.room.Update


@Dao
interface NoteDao {
    @Query("SELECT * FROM Note")
    fun getAll(): List<Note?>?

    @Insert(onConflict = IGNORE)
    fun insert(note: Note?)

    @Update(onConflict = IGNORE)
    fun update(repos: Note?)

    @Delete
    fun delete(note: Note?)
}