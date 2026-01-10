package com.example.distancereporter.data

enum class DistanceUnit(
    val displayName: String,
    val spokenName: String,
    val abbreviation: String,
    val metersPerUnit: Double
) {
    KILOMETERS("Kilometers", "kilometers", "km", 1000.0),
    MILES("Miles", "miles", "mi", 1609.344),
    NAUTICAL_MILES("Nautical Miles", "nautical miles", "nm", 1852.0);

    fun convertFromMeters(meters: Double): Double = meters / metersPerUnit
}
