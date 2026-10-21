package com.unishuttle.app.data.mapper

import com.unishuttle.app.domain.model.CampusLatLng

object PolylineDecoder {
    fun decode(encoded: String): List<CampusLatLng> {
        val points = mutableListOf<CampusLatLng>()
        var index = 0
        var latitude = 0
        var longitude = 0

        while (index < encoded.length) {
            val latitudeResult = decodeNextValue(encoded, index)
            index = latitudeResult.nextIndex
            latitude += latitudeResult.delta

            val longitudeResult = decodeNextValue(encoded, index)
            index = longitudeResult.nextIndex
            longitude += longitudeResult.delta

            points += CampusLatLng(
                latitude = latitude / 100000.0,
                longitude = longitude / 100000.0,
            )
        }

        return points
    }

    private fun decodeNextValue(encoded: String, startIndex: Int): DecodeResult {
        var result = 0
        var shift = 0
        var index = startIndex
        var byte: Int

        do {
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20)

        val delta = if (result and 1 != 0) {
            (result shr 1).inv()
        } else {
            result shr 1
        }

        return DecodeResult(delta = delta, nextIndex = index)
    }

    private data class DecodeResult(
        val delta: Int,
        val nextIndex: Int,
    )
}
