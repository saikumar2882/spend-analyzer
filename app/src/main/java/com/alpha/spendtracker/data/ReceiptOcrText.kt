package com.alpha.spendtracker.data

/**
 * Turns the lines OCR read off a payment-receipt screenshot into text for [SharedPaymentText] and
 * the parser. Pure, so it is unit-testable without ML Kit.
 *
 * Plain reading order is a poor input for [AiParser.extractAmount]: its last resort is the first
 * standalone number, which on a receipt is usually the date or the clock ("11 Oct", "10:32"), and
 * OCR often reads the "₹" glyph as "T", "Z" or "?". Payment apps all print the amount in the
 * largest type on screen, so the tallest purely-numeric line is taken as the amount and put
 * first, with an explicit "₹".
 */
object ReceiptOcrText {

    /** One recognised line of text and the pixel height of its bounding box. */
    data class Line(val text: String, val height: Int)

    // Optional OCR'd currency glyph (up to 2 non-digit chars), then a number: 1,250.00 / 250 / 1250.5
    private val AMOUNT_LINE = Regex("""^[^\d\s]{0,2}\s?(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)$""")

    /** The amount line must be this much taller than a typical line, or it is just another number. */
    private const val MIN_HEIGHT_RATIO = 1.3

    fun fromLines(lines: List<Line>): String {
        val usable = lines.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return ""

        val typicalHeight = usable.map { it.height }.sorted()[usable.size / 2]
        val amountLine = usable
            .filter { AMOUNT_LINE.matches(it.text.trim()) }
            .maxByOrNull { it.height }
            ?.takeIf { it.height >= typicalHeight * MIN_HEIGHT_RATIO }

        if (amountLine == null) return usable.joinToString(" ") { it.text.trim() }

        val amount = AMOUNT_LINE.matchEntire(amountLine.text.trim())!!.groupValues[1]
        val rest = usable.filter { it !== amountLine }.joinToString(" ") { it.text.trim() }
        return "₹$amount $rest".trim()
    }
}
