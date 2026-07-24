package com.alpha.spendtracker.data

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room type converters for [NoteEntry.customFields]. The list is stored in a single TEXT
 * column as a compact JSON array; Firestore serializes the same property natively as an
 * array of maps, so the two storage layers stay independent. org.json is used (already a
 * dependency) to avoid pulling DI into a Room-instantiated converter.
 */
class NoteConverters {

    @TypeConverter
    fun fromNoteFields(fields: List<NoteField>): String {
        val arr = JSONArray()
        fields.forEach { field ->
            arr.put(JSONObject().put("name", field.name).put("value", field.value))
        }
        return arr.toString()
    }

    @TypeConverter
    fun toNoteFields(json: String): List<NoteField> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                NoteField(obj.optString("name"), obj.optString("value"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
