package com.valpr.bikecompanion

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural invariant test (AGENTS.md §12):
 * Screens must respect edge-to-edge window insets (status bars, navigation bars,
 * and cutouts) either via Scaffold or explicit safeDrawingPadding / systemBarsPadding.
 */
class UiWindowInsetsBoundaryTest {

    @Test
    fun allScreens_handleWindowInsetsOrUseScaffold() {
        val uiDir = File("src/main/java/com/valpr/bikecompanion/ui")
        assertTrue("ui directory must exist", uiDir.exists() && uiDir.isDirectory)

        val violations = mutableListOf<String>()
        val screenFiles = uiDir.walkTopDown().filter { it.isFile && it.name.endsWith("Screen.kt") }.toList()
        assertTrue("Expected screen files to exist", screenFiles.isNotEmpty())

        for (file in screenFiles) {
            val content = file.readText()
            val usesScaffold = content.contains("Scaffold(")
            val usesSafeDrawing = content.contains("safeDrawingPadding()") ||
                content.contains("WindowInsets.safeDrawing")
            val usesSystemBars = content.contains("systemBarsPadding()") ||
                content.contains("statusBarsPadding()") ||
                content.contains("WindowInsets.systemBars")

            if (!usesScaffold && !usesSafeDrawing && !usesSystemBars) {
                violations.add("${file.name} does not use Scaffold or safeDrawingPadding/systemBarsPadding")
            }
        }

        assertTrue(
            "Found screen(s) violating edge-to-edge insets invariant:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun activeWorkoutScreen_specificallyEnforcesSafeDrawingPadding() {
        val file = File("src/main/java/com/valpr/bikecompanion/ui/workout/ActiveWorkoutScreen.kt")
        assertTrue("ActiveWorkoutScreen.kt must exist", file.exists())

        val content = file.readText()
        assertTrue(
            "ActiveWorkoutScreen must use safeDrawingPadding() to avoid clashing with status bar items",
            content.contains("safeDrawingPadding()")
        )
    }
}
