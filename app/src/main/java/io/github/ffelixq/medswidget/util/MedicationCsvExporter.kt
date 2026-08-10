package io.github.ffelixq.medswidget.util

import io.github.ffelixq.medswidget.domain.DoseEvent
import io.github.ffelixq.medswidget.domain.Medicine

object MedicationCsvExporter {
    private val spreadsheetFormulaPrefixes = setOf('=', '+', '-', '@')

    fun export(
        medicines: List<Medicine>,
        events: List<DoseEvent>,
    ): String =
        buildString {
            appendLine(
                "recordType,medicineId,name,slot,action,logicalDay,occurredAt,source,label,notes",
            )
            medicines.forEach { medicine ->
                appendCsvRow(
                    "medicine",
                    medicine.id,
                    medicine.name,
                    "",
                    if (medicine.archived) "archived" else "active",
                    "",
                    medicine.updatedAt.toString(),
                    "",
                    medicine.enabledSlots().joinToString("|") { it.wireValue },
                    medicine.notes,
                )
            }
            events.sortedBy(DoseEvent::occurredAt).forEach { event ->
                appendCsvRow(
                    "dose_event",
                    event.medicineId,
                    event.medicineNameSnapshot,
                    event.slot.wireValue,
                    event.action.wireValue,
                    event.logicalDay.toString(),
                    event.occurredAt.toString(),
                    event.source.wireValue,
                    event.labelSnapshot,
                    event.skipReason.orEmpty(),
                )
            }
        }

    private fun StringBuilder.appendCsvRow(vararg values: String) {
        appendLine(values.joinToString(",", transform = ::escape))
    }

    private fun escape(value: String): String {
        val normalized = value.replace("\r\n", "\n").replace('\r', '\n')
        val safeValue = neutralizeSpreadsheetFormula(normalized)
        if (safeValue.none { it == ',' || it == '"' || it == '\n' }) return safeValue
        return "\"${safeValue.replace("\"", "\"\"")}\""
    }

    private fun neutralizeSpreadsheetFormula(value: String): String {
        val firstMeaningfulCharacter = value.firstOrNull { !it.isWhitespace() }
        val isFormula =
            firstMeaningfulCharacter != null &&
                firstMeaningfulCharacter in spreadsheetFormulaPrefixes
        return if (isFormula) "'$value" else value
    }
}
