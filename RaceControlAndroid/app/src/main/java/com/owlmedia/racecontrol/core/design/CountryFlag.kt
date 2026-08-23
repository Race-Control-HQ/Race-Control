package com.owlmedia.racecontrol.core.design

import androidx.compose.ui.semantics.contentDescription

/**
 * Maps FastF1 / Ergast country and nationality strings to emoji flags, so the
 * UI shows a recognisable marker without bundling image assets.
 *
 * Ported from iOS `CountryFlag`, including the same alpha-3 and name tables.
 */
object CountryFlag {

    private const val REGIONAL_INDICATOR_BASE = 0x1F1E6

    /** Builds a flag emoji from a two-letter ISO country code. */
    private fun regional(alpha2: String): String {
        if (alpha2.length != 2) return FALLBACK
        val sb = StringBuilder()
        for (ch in alpha2.uppercase()) {
            if (ch !in 'A'..'Z') return FALLBACK
            sb.appendCodePoint(REGIONAL_INDICATOR_BASE + (ch - 'A'))
        }
        return sb.toString()
    }

    const val FALLBACK = "🏁" // chequered flag

    private val alpha3ToAlpha2 = mapOf(
        "GBR" to "GB", "NED" to "NL", "MON" to "MC", "ESP" to "ES", "MEX" to "MX",
        "AUS" to "AU", "FIN" to "FI", "GER" to "DE", "FRA" to "FR", "CAN" to "CA",
        "JPN" to "JP", "THA" to "TH", "CHN" to "CN", "DEN" to "DK", "USA" to "US",
        "ITA" to "IT", "AUT" to "AT", "BRA" to "BR", "NZL" to "NZ", "BEL" to "BE",
        "SUI" to "CH", "POL" to "PL", "RUS" to "RU", "IND" to "IN", "ARG" to "AR",
        "BRN" to "BH", "SAU" to "SA", "ARE" to "AE", "AZE" to "AZ", "SGP" to "SG",
        "QAT" to "QA", "HUN" to "HU", "POR" to "PT", "TUR" to "TR", "KSA" to "SA",
    )

    private val alpha3ToFlag: Map<String, String> by lazy {
        alpha3ToAlpha2.mapValues { (_, alpha2) -> regional(alpha2) }
    }

    private val nameToAlpha2 = mapOf(
        "united kingdom" to "GB", "uk" to "GB", "great britain" to "GB", "british" to "GB",
        "netherlands" to "NL", "dutch" to "NL",
        "monaco" to "MC", "monegasque" to "MC",
        "spain" to "ES", "spanish" to "ES",
        "mexico" to "MX", "mexican" to "MX",
        "australia" to "AU", "australian" to "AU",
        "finland" to "FI", "finnish" to "FI",
        "germany" to "DE", "german" to "DE",
        "france" to "FR", "french" to "FR",
        "canada" to "CA", "canadian" to "CA",
        "japan" to "JP", "japanese" to "JP",
        "thailand" to "TH", "thai" to "TH",
        "china" to "CN", "chinese" to "CN",
        "denmark" to "DK", "danish" to "DK",
        "united states" to "US", "usa" to "US", "american" to "US",
        "italy" to "IT", "italian" to "IT",
        "austria" to "AT", "austrian" to "AT",
        "brazil" to "BR", "brazilian" to "BR",
        "new zealand" to "NZ",
        "belgium" to "BE", "belgian" to "BE",
        "switzerland" to "CH", "swiss" to "CH",
        "bahrain" to "BH", "saudi arabia" to "SA", "united arab emirates" to "AE",
        "azerbaijan" to "AZ", "singapore" to "SG", "qatar" to "QA",
        "hungary" to "HU", "portugal" to "PT", "turkey" to "TR",
    )

    private val nameToFlag: Map<String, String> by lazy {
        nameToAlpha2.mapValues { (_, alpha2) -> regional(alpha2) }
    }

    fun flag(country: String?, code: String? = null): String {
        code?.uppercase()?.let { alpha3ToFlag[it] }?.let { return it }
        country?.lowercase()?.let { nameToFlag[it] }?.let { return it }
        return FALLBACK
    }

    /**
     * TalkBack reads an unfamiliar flag emoji as a pair of letters, so screens
     * pair the glyph with the country name and mark the glyph itself decorative.
     */
    fun contentDescription(country: String?): String? = country
}
