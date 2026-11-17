package com.example.localization

enum class AppLanguage(
    val code: String,
    val nativeLabel: String,
    val englishLabel: String,
    val regionTag: String,
    val isRtl: Boolean
) {
    ENGLISH("en", "English", "English", "EN", false),
    HAUSA("ha", "Hausa", "Hausa", "HA", false),
    FRENCH("fr", "Français", "French", "FR", false),
    ARABIC("ar", "العربية", "Arabic", "AR", true);

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
    }
}
