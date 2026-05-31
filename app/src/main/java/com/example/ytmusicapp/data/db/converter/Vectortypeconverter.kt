package com.example.ytmusicapp.data.db.converter

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VectorTypeConverter {

    @TypeConverter
    fun fromFloatArray(value: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(value.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(value)
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(value: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(value.size / Float.SIZE_BYTES)
        buffer.asFloatBuffer().get(floats)
        return floats
    }
}