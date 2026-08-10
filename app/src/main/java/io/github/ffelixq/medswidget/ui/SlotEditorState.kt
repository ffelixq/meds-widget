package io.github.ffelixq.medswidget.ui

import java.io.Serializable

internal data class SlotEditorState(
    val enabled: Boolean,
    val label: String,
    val countdownMinutes: Int?,
    val reminderMinutes: Int?,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
