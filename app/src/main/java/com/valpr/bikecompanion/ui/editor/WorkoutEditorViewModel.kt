package com.valpr.bikecompanion.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.valpr.bikecompanion.BikeApplication

/**
 * Hosts the editor draft across configuration changes (rotation on a
 * handlebar-mounted device must not wipe edits). The [WorkoutEditorState]
 * itself stays a plain testable class; this ViewModel is only its
 * rotation-proof owner, following the Dashboard/AthleteStats pattern.
 */
class WorkoutEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BikeApplication

    val editorState = WorkoutEditorState(app.workoutRepository)

    var openFilename: String? = null
        private set
    private var opened = false

    fun hasUnsavedChanges(): Boolean = editorState.isDirty

    /**
     * Opens the draft for [filename] (null = new workout). No-op when the
     * same file is already open so rotation and re-entry keep unsaved edits.
     *
     * Returns false (leaving the current draft untouched) when there are
     * unsaved edits for a *different* file: the caller must confirm discard
     * (or save) first and then retry with [forceOpen] / [discardAndOpen].
     * Switching files must never silently drop edits.
     */
    fun open(filename: String?): Boolean {
        if (opened && filename == openFilename) return true
        if (opened && editorState.isDirty) return false
        forceOpen(filename)
        return true
    }

    /** Unconditional open after the caller resolved unsaved edits. */
    fun forceOpen(filename: String?) {
        openFilename = filename
        opened = true
        if (filename != null && !editorState.load(filename)) {
            editorState.loadNew()
        } else if (filename == null) {
            editorState.loadNew()
        }
    }

    /** Discards the current draft, restoring the last saved/opened state. */
    fun discardChanges() {
        editorState.discardChanges()
    }

    /**
     * Discards unsaved edits and opens [filename]. Used by the
     * switch-file confirmation dialog.
     */
    fun discardAndOpen(filename: String?) {
        editorState.discardChanges()
        forceOpen(filename)
    }
}
