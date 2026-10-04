package com.valpr.bikecompanion.bike.echelon

import com.valpr.bikecompanion.bike.api.BikeCapabilities
import com.valpr.bikecompanion.bike.api.BikeProtocol
import com.valpr.bikecompanion.bike.api.OutboundPacket
import com.valpr.bikecompanion.bike.api.ParseResult
import com.valpr.bikecompanion.bike.api.ResistanceModel
import com.valpr.bikecompanion.ble.EchelonGattAttributes
import com.valpr.bikecompanion.ble.EchelonPacketParser
import com.valpr.bikecompanion.ble.EchelonProtocol
import com.valpr.bikecompanion.ble.ParsedPacket
import com.valpr.bikecompanion.data.BikeTelemetry
import com.valpr.bikecompanion.data.EchelonWattTable
import java.util.UUID

class EchelonBikeProtocol(
    override val capabilities: BikeCapabilities = BikeCapabilities.DEFAULT_ECHELON.copy(resistanceModel = EchelonResistanceModel),
    override val resistanceModel: ResistanceModel = EchelonResistanceModel
) : BikeProtocol {

    override val serviceUuid: UUID = EchelonGattAttributes.SERVICE_ECHELON
    override val notifyCharacteristics: List<UUID> = listOf(
        EchelonGattAttributes.CHAR_NOTIFY_1,
        EchelonGattAttributes.CHAR_NOTIFY_2
    )
    override val writeCharacteristic: UUID = EchelonGattAttributes.CHAR_WRITE

    override fun createHandshake(): List<OutboundPacket> = EchelonProtocol.getFullHandshakeSequence().map { (bytes, desc) ->
        OutboundPacket(
            characteristicUuid = writeCharacteristic,
            data = bytes,
            description = desc
        )
    }

    override fun createKeepAlive(counter: Int): OutboundPacket {
        val bytes = EchelonProtocol.createPollCommand(counter)
        return OutboundPacket(
            characteristicUuid = writeCharacteristic,
            data = bytes,
            description = "Poll (counter=$counter)"
        )
    }

    override fun parseNotification(
        characteristicUuid: UUID,
        data: ByteArray,
        currentTelemetry: BikeTelemetry
    ): ParseResult = when (val parsed = EchelonPacketParser.parse(data)) {
        is ParsedPacket.CadenceFrame -> {
            ParseResult.TelemetryUpdate(
                update = { current ->
                    val watts = EchelonWattTable.calculateWattsInt(
                        resistance = current.resistanceLevel,
                        cadenceRpm = parsed.cadenceRpm.toDouble()
                    )
                    current.copy(
                        cadenceRpm = parsed.cadenceRpm,
                        elapsedSeconds = parsed.elapsedSeconds,
                        distanceKm = parsed.distanceKm,
                        speedKmh = parsed.speedKmh,
                        estimatedWatts = watts,
                        lastUpdateTimestampMs = System.currentTimeMillis()
                    )
                },
                logOpcode = "0xD1 Cadence",
                logDescription = "Cadence: ${parsed.cadenceRpm} RPM, Dist: %.2f km".format(parsed.distanceKm),
                rawCadenceRpm = parsed.cadenceRpm
            )
        }
        is ParsedPacket.ResistanceFrame -> {
            ParseResult.TelemetryUpdate(
                update = { current ->
                    val watts = EchelonWattTable.calculateWattsInt(
                        resistance = parsed.resistanceLevel,
                        cadenceRpm = current.cadenceRpm.toDouble()
                    )
                    current.copy(
                        resistanceLevel = parsed.resistanceLevel,
                        estimatedWatts = watts,
                        lastUpdateTimestampMs = System.currentTimeMillis()
                    )
                },
                logOpcode = "0xD2 Resistance",
                logDescription = "Resistance Level: ${parsed.resistanceLevel}",
                rawResistance = parsed.resistanceLevel
            )
        }
        is ParsedPacket.LockedBikeFrame -> {
            ParseResult.LockedWarning("WARNING: Bike firmware lock detected!")
        }
        is ParsedPacket.UnknownFrame -> {
            ParseResult.Ignored(parsed.reason)
        }
    }

    override fun createResistanceCommand(level: Int): OutboundPacket {
        val safeLevel = level.coerceIn(capabilities.resistanceRange)
        val commandBytes = EchelonProtocol.createResistanceCommand(safeLevel)
        return OutboundPacket(
            characteristicUuid = writeCharacteristic,
            data = commandBytes,
            description = "Set Resistance $safeLevel"
        )
    }
}
