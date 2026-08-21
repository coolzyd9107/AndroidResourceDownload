package link.mczihan.androidResourceDownload.core.theme

import com.materialkolor.hct.Hct

data class ThemeSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val seedColorArgb: Int = DEFAULT_THEME_SEED_ARGB,
    val schemeVariant: ThemeSchemeVariant = ThemeSchemeVariant.TONAL_SPOT,
)

enum class ThemeSchemeVariant(val displayName: String) {
    TONAL_SPOT("Tonal Spot"),
    FIDELITY("Fidelity"),
    MONOCHROME("Monochrome"),
    NEUTRAL("Neutral"),
    VIBRANT("Vibrant"),
    EXPRESSIVE("Expressive"),
    CONTENT("Content"),
    RAINBOW("Rainbow"),
    ;

    val usesSourceChromaAndTone: Boolean
        get() = this == FIDELITY || this == CONTENT
}

enum class ThemeSeedPreset(val seedColorArgb: Int) {
    FOREST(DEFAULT_THEME_SEED_ARGB),
    INDIGO(0xFF5066A8.toInt()),
    CORAL(0xFFB45F49.toInt()),
    SKY(0xFF2879A8.toInt()),
    OLIVE(0xFF77771F.toInt()),
    CYAN(0xFF087C8C.toInt()),
    MINT(0xFF4E8149.toInt()),
    ROSE(0xFFA84263.toInt()),
    VIOLET(0xFF7355A8.toInt()),
}

data class ThemeTone(
    val hue: Float,
    val chroma: Float,
    val tone: Float,
)

fun themeToneFromArgb(argb: Int): ThemeTone = Hct.fromInt(normalizeThemeSeedArgb(argb)).let { hct ->
    ThemeTone(
        hue = hct.hue.toFloat(),
        chroma = hct.chroma.toFloat(),
        tone = hct.tone.toFloat(),
    )
}

fun themeSeedFromTone(tone: ThemeTone): Int = normalizeThemeSeedArgb(
    Hct.from(
        hue = tone.hue.toDouble().coerceIn(0.0, 360.0),
        chroma = tone.chroma.toDouble().coerceIn(0.0, 100.0),
        tone = tone.tone.toDouble().coerceIn(0.0, 100.0),
    ).toInt(),
)

fun normalizeThemeSeedArgb(argb: Int): Int = argb or 0xFF000000.toInt()

val DEFAULT_THEME_SEED_ARGB: Int = 0xFF45664B.toInt()
