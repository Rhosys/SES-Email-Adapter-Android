package ch.rhosys.email.data.local

import androidx.room.TypeConverter
import org.json.JSONObject

/** Simple delimiter-based list converters — addresses/labels never contain "|". */
class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString("|")

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("|")

    @TypeConverter
    fun fromStringMap(value: Map<String, String>): String = JSONObject(value as Map<*, *>).toString()

    @TypeConverter
    fun toStringMap(value: String): Map<String, String> {
        if (value.isEmpty()) return emptyMap()
        val json = JSONObject(value)
        return json.keys().asSequence().associateWith { json.getString(it) }
    }
}
