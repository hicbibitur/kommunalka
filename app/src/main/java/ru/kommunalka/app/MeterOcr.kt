package ru.kommunalka.app

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/**
 * Превращает распознанный текст в варианты показаний.
 * Счётчики часто читаются без запятой («00214517»), поэтому для каждой
 * найденной группы цифр перебираются возможные положения запятой и выбираются
 * те, что правдоподобнее всего с учётом прошлого показания и обычного расхода.
 */
object MeterOcr {
    data class Candidate(val value: Double, val text: String, val score: Double)

    // Частые ошибки распознавания цифр на барабанах счётчика
    private val confusions = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0', 'О' to '0', 'о' to '0',
        'I' to '1', 'l' to '1', '|' to '1', 'i' to '1',
        'Z' to '2', 'z' to '2', 'S' to '5', 's' to '5', 'B' to '8', 'В' to '8',
        'b' to '6', 'G' to '6', 'g' to '9', 'q' to '9', 'T' to '7'
    )
    private val numRe = Regex("""\d(?:[\d ,.]*\d)?""")

    private fun typicalMonthly(k: Kind): Double = when (k) {
        Kind.COLD -> 5.0
        Kind.HOT -> 3.0
        Kind.ELEC -> 200.0
        Kind.GAS -> 15.0
        Kind.HEAT -> 1.0
    }

    fun candidates(lines: List<String>, kind: Kind, prev: Double?, avg: Double?): List<Candidate> {
        val best = mutableMapOf<Double, Candidate>()
        for (line in lines) {
            val cleaned = line.map { c -> confusions[c] ?: c }.joinToString("")
            if (cleaned.count { it.isDigit() } < 3) continue
            for (m in numRe.findAll(cleaned)) {
                val raw = m.value.trim()
                val digits = raw.filter { it.isDigit() }
                if (digits.length < 3 || digits.length > 11) continue

                val interps = mutableListOf<Pair<Double, Double>>() // значение, бонус
                val sep = raw.indexOfLast { it == ',' || it == '.' }
                if (sep > 0) {
                    val ip = raw.substring(0, sep).filter { it.isDigit() }
                    val fp = raw.substring(sep + 1).filter { it.isDigit() }
                    if (ip.isNotEmpty() && fp.isNotEmpty() && fp.length <= 3) {
                        "$ip.$fp".toDoubleOrNull()?.let { interps += it to 1.0 }
                    }
                }
                val n = digits.toLongOrNull() ?: continue
                for (f in 0..kind.frac) {
                    val v = n / 10.0.pow(f)
                    val bonus = when {
                        f == kind.frac && digits.length == kind.intDigits + kind.frac -> 0.8
                        f == kind.frac -> 0.3
                        else -> 0.0
                    }
                    interps += v to bonus
                }

                for ((v, bonus) in interps) {
                    var score = bonus + minOf(digits.length, 8) * 0.05
                    if (prev != null) {
                        if (v < prev - 1e-9) {
                            score -= 10.0
                        } else {
                            val expected = (avg ?: typicalMonthly(kind)).coerceAtLeast(0.001)
                            val ratio = (v - prev + expected * 0.05) / expected
                            score += 3.0 - abs(ln(ratio))
                        }
                    }
                    val key = round3(v)
                    val old = best[key]
                    if (old == null || old.score < score) best[key] = Candidate(key, raw, score)
                }
            }
        }
        val all = best.values.sortedByDescending { it.score }
        val plausible = all.filter { it.score > -5.0 }
        return (plausible.ifEmpty { all }).take(5)
    }
}
