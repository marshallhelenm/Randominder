package io.github.marshallhelenm.randominder.data.local

import androidx.room.TypeConverter
import io.github.marshallhelenm.randominder.model.SoundSource
import java.time.DayOfWeek
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun localTimeToString(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun daysToString(value: Set<DayOfWeek>?): String? =
        value?.sorted()?.joinToString(",") { it.name }

    @TypeConverter
    fun stringToDays(value: String?): Set<DayOfWeek>? =
        value?.split(",")?.filter { it.isNotEmpty() }?.map(DayOfWeek::valueOf)?.toSet()

    @TypeConverter
    fun soundSourceToString(value: SoundSource?): String? = value?.name

    @TypeConverter
    fun stringToSoundSource(value: String?): SoundSource? = value?.let(SoundSource::valueOf)
}
