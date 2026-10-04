package com.valpr.bikecompanion.bike.api

import com.valpr.bikecompanion.data.BikeTelemetry
import java.util.UUID

data class OutboundPacket(
    val characteristicUuid: UUID,
    val data: ByteArray,
    val description: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OutboundPacket
        return characteristicUuid == other.characteristicUuid &&
            data.contentEquals(other.data) &&
            description == other.description
    }

    override fun hashCode(): Int {
        var result = characteristicUuid.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + description.hashCode()
        return result
    }
}

sealed interface ParseResult {
    data class TelemetryUpdate(
        val update: (BikeTelemetry) -> BikeTelemetry,
        val logOpcode: String,
        val logDescription: String,
        val rawCadenceRpm: Int? = null,
        val rawResistance: Int? = null
    ) : ParseResult

    data class LockedWarning(val logDescription: String) : ParseResult
    data class Ignored(val reason: String) : ParseResult
}

interface BikeProtocol {
    val serviceUuid: UUID
    val notifyCharacteristics: List<UUID>
    val indicateCharacteristics: List<UUID> get() = emptyList()
    val writeCharacteristic: UUID
    val capabilities: BikeCapabilities
    val resistanceModel: ResistanceModel?

    fun createHandshake(): List<OutboundPacket>
    fun createKeepAlive(counter: Int): OutboundPacket?
    fun parseNotification(characteristicUuid: UUID, data: ByteArray, currentTelemetry: BikeTelemetry): ParseResult
    fun createResistanceCommand(level: Int): OutboundPacket
    fun createTargetPowerCommand(watts: Int): OutboundPacket? = null
}
