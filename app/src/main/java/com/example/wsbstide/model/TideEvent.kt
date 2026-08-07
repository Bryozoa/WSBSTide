package com.example.wsbstide.model

enum class TideEventType {
    HIGH,
    LOW
}

data class TideEvent(
    val timestampMillis: Long,
    val height: Double,
    val type: TideEventType,
)