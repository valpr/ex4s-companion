package com.valpr.bikecompanion

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImportBoundaryTest {

    @Test
    fun bikeApi_doesNotImportImplementationOrAndroidBluetooth() {
        val rootDir = File("src/main/java/com/valpr/bikecompanion/bike/api")
        assertTrue("bike/api directory must exist", rootDir.exists() && rootDir.isDirectory)

        val bannedPrefixes = listOf(
            "com.valpr.bikecompanion.bike.echelon",
            "com.valpr.bikecompanion.bike.ble",
            "com.valpr.bikecompanion.bike.ftms",
            "android.bluetooth"
        )

        val violations = mutableListOf<String>()
        rootDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import ")) {
                        val imported = trimmed.removePrefix("import ").trim()
                        for (banned in bannedPrefixes) {
                            if (imported.startsWith(banned)) {
                                violations.add("${file.name}:${index + 1} imports $imported")
                            }
                        }
                    }
                }
            }
        }

        assertTrue(
            "Found architectural boundary violations in bike/api:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }
}
