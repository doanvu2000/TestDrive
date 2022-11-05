package com.example.test

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity
data class Note(
    @field:PrimaryKey(autoGenerate = true) var note_id: Int, // column name will be "note_content" instead of "content" in table
    @field:ColumnInfo(name = "note_content") var content: String,
    @field:ColumnInfo(name = "note_title") var title: String,
) {

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is Note) return false
        if (note_id != o.note_id) return false
        return title == o.title
    }

    override fun hashCode(): Int {
        var result = note_id
        result = 31 * result + title.hashCode()
        return result
    }

}