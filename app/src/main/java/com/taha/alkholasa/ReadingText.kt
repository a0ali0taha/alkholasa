package com.taha.alkholasa

data class ReadingText(
    val titleSp: Float,
    val sourceSp: Float,
    val metaSp: Float,
) {
    companion object {
        val small = ReadingText(18f, 14f, 13f)
        val comfortable = ReadingText(21f, 15f, 14f)
        val large = ReadingText(25f, 17f, 16f)

        fun fromId(id: String): ReadingText = when (id) {
            SMALL -> small
            LARGE -> large
            else -> comfortable
        }

        const val SMALL = "small"
        const val COMFORTABLE = "comfortable"
        const val LARGE = "large"
    }
}
