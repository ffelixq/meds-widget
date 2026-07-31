package io.github.ffelixq.medswidget.widget

import androidx.compose.ui.unit.DpSize

enum class WidgetLayoutCategory {
    COMPACT,
    STANDARD,
    SPACIOUS,
}

enum class WidgetKind {
    SINGLE,
    ALL,
}

data class WidgetLayoutSpec(
    val category: WidgetLayoutCategory,
    val titleSp: Int,
    val bodySp: Int,
    val checkSp: Int,
    val supportingSp: Int,
    val outerPaddingDp: Int,
    val rowHeightDp: Int,
) {
    companion object {
        @Suppress("CyclomaticComplexMethod")
        fun forSize(
            size: DpSize,
            kind: WidgetKind,
        ): WidgetLayoutSpec {
            val category =
                when (kind) {
                    WidgetKind.SINGLE -> {
                        when {
                            size.width.value < 160f || size.height.value < 115f -> {
                                WidgetLayoutCategory.COMPACT
                            }

                            size.width.value < 235f || size.height.value < 175f -> {
                                WidgetLayoutCategory.STANDARD
                            }

                            else -> {
                                WidgetLayoutCategory.SPACIOUS
                            }
                        }
                    }

                    WidgetKind.ALL -> {
                        when {
                            size.width.value < 280f || size.height.value < 115f -> {
                                WidgetLayoutCategory.COMPACT
                            }

                            size.width.value < 380f || size.height.value < 175f -> {
                                WidgetLayoutCategory.STANDARD
                            }

                            else -> {
                                WidgetLayoutCategory.SPACIOUS
                            }
                        }
                    }
                }
            return when (category) {
                WidgetLayoutCategory.COMPACT -> {
                    WidgetLayoutSpec(category, 16, 14, 20, 12, 6, 44)
                }

                WidgetLayoutCategory.STANDARD -> {
                    WidgetLayoutSpec(category, 19, 16, 24, 14, 9, 52)
                }

                WidgetLayoutCategory.SPACIOUS -> {
                    WidgetLayoutSpec(category, 22, 18, 28, 15, 12, 58)
                }
            }
        }
    }
}
