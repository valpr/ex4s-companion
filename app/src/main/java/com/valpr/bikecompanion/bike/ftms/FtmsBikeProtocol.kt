package com.valpr.bikecompanion.bike.ftms

import com.valpr.bikecompanion.bike.api.BikeCapabilities
import com.valpr.bikecompanion.bike.api.BikeProtocol
import com.valpr.bikecompanion.bike.api.OutboundPacket
import com.valpr.bikecompanion.bike.api.ParseResult
import com.valpr.bikecompanion.bike.api.ResistanceModel
import com.valpr.bikecompanion.data.BikeTelemetry
import java.util.UUID

/**
 * Standard Bluetooth SIG Fitness Machine Service (FTMS) Indoor Bike Protocol.
 *
 * References:
 * - Bluetooth SIG Fitness Machine Service Specification v1.0
 * - Service UUID: 0x1826
 * - Indoor Bike Data: 0x2AD2 (Notify)
 * - Fitness Machine Control Point: 0x2AD9 (Write, Indicate)
 * - Fitness Machine Status: 0x2ADA (Notify)
 */
class FtmsBikeProtocol(
    override val capabilities: BikeCapabilities = BikeCapabilities(
        modelName = "FTMS Bike",
        resistanceRange = 1..100,
        reportsMeasuredPower = true,
        supportsNativeErg = true,
        reportsDistance = true,
        resistanceModel = null
    )
) : BikeProtocol {

    companion object {
        val FTMS_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
        val INDOOR_BIKE_DATA_UUID: UUID = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")
        val CONTROL_POINT_UUID: UUID = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")
        val FITNESS_MACHINE_STATUS_UUID: UUID = UUID.fromString("00002ada-0000-1000-8000-00805f9b34fb")

        const val OPCODE_REQUEST_CONTROL: Byte = 0x00
        const val OPCODE_RESET: Byte = 0x01
        const val OPCODE_SET_TARGET_RESISTANCE: Byte = 0x04
        const val OPCODE_SET_TARGET_POWER: Byte = 0x05
        const val OPCODE_START_OR_RESUME: Byte = 0x07
        const val OPCODE_STOP_OR_PAUSE: Byte = 0x08
    }

    override val serviceUuid: UUID = FTMS_SERVICE_UUID
    override val notifyCharacteristics: List<UUID> = listOf(
        INDOOR_BIKE_DATA_UUID,
        FITNESS_MACHINE_STATUS_UUID
    )
    override val indicateCharacteristics: List<UUID> = listOf(
        CONTROL_POINT_UUID
    )
    override val writeCharacteristic: UUID = CONTROL_POINT_UUID
    override val resistanceModel: ResistanceModel? = null

    override fun createHandshake(): List<OutboundPacket> = listOf(
        OutboundPacket(
            characteristicUuid = CONTROL_POINT_UUID,
            data = byteArrayOf(OPCODE_REQUEST_CONTROL),
            description = "FTMS Request Control"
        ),
        OutboundPacket(
            characteristicUuid = CONTROL_POINT_UUID,
            data = byteArrayOf(OPCODE_RESET),
            description = "FTMS Reset"
        ),
        OutboundPacket(
            characteristicUuid = CONTROL_POINT_UUID,
            data = byteArrayOf(OPCODE_START_OR_RESUME),
            description = "FTMS Start/Resume"
        )
    )

    override fun createKeepAlive(counter: Int): OutboundPacket? = null

    override fun parseNotification(
        characteristicUuid: UUID,
        data: ByteArray,
        currentTelemetry: BikeTelemetry
    ): ParseResult {
        if (characteristicUuid != INDOOR_BIKE_DATA_UUID) {
            return ParseResult.Ignored("Non-telemetry characteristic: $characteristicUuid")
        }

        val parsed = FtmsIndoorBikeDataParser.parse(data)
            ?: return ParseResult.Ignored("Invalid FTMS payload (size=${data.size})")

        return ParseResult.TelemetryUpdate(
            update = { prev ->
                prev.copy(
                    cadenceRpm = parsed.cadenceRpm ?: prev.cadenceRpm,
                    estimatedWatts = parsed.powerWatts ?: prev.estimatedWatts,
                    resistanceLevel = parsed.resistanceLevel ?: prev.resistanceLevel,
                    speedKmh = parsed.speedKmh ?: prev.speedKmh,
                    distanceKm = parsed.totalDistanceKm ?: prev.distanceKm
                )
            },
            logOpcode = "0x2AD2",
            logDescription = "FTMS Telemetry (cadence=${parsed.cadenceRpm}, watts=${parsed.powerWatts}, res=${parsed.resistanceLevel})",
            rawCadenceRpm = parsed.cadenceRpm,
            rawResistance = parsed.resistanceLevel
        )
    }

    override fun createResistanceCommand(level: Int): OutboundPacket {
        val clamped = level.coerceIn(capabilities.resistanceRange)
        return OutboundPacket(
            characteristicUuid = CONTROL_POINT_UUID,
            data = byteArrayOf(OPCODE_SET_TARGET_RESISTANCE, clamped.toByte()),
            description = "FTMS Set Resistance ($clamped)"
        )
    }

    override fun createTargetPowerCommand(watts: Int): OutboundPacket {
        val clamped = watts.coerceAtLeast(0)
        return OutboundPacket(
            characteristicUuid = CONTROL_POINT_UUID,
            data = byteArrayOf(
                OPCODE_SET_TARGET_POWER,
                (clamped and 0xFF).toByte(),
                ((clamped shr 8) and 0xFF).toByte()
            ),
            description = "FTMS Set Target Power (${clamped}W)"
        )
    }
}
