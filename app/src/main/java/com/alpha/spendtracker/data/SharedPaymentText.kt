package com.alpha.spendtracker.data

/**
 * Prepares text shared into Spendly from a payment app ("Share" on a GPay/PhonePe receipt)
 * for the AI parser. Receipts carry identifiers that are useless for categorising and should
 * not be sent to the LLM — transaction/reference ids, UPI ids, account and phone numbers,
 * links — so they are stripped first. Pure, so it is unit-testable.
 */
object SharedPaymentText {
    private const val MAX_LENGTH = 300

    private val URL = Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE)
    private val UPI_ID = Regex("""[\w.\-]+@[\w.\-]+""")
    // Whole "UPI transaction ID: 1234…" / "Ref No. ABC123" fragments, label included.
    private val ID_LABEL = Regex(
        """(?:upi\s*)?(?:transaction|txn|ref(?:erence)?|utr|order)\s*(?:id|no\.?|number)?\s*[:#-]?\s*[A-Z0-9]{6,}""",
        RegexOption.IGNORE_CASE
    )
    // Long digit runs: ids, account and phone numbers. Amounts never get this long.
    private val LONG_NUMBER = Regex("""(?<![\d.,])[Xx*]*\d[\d\s-]{7,}\d""")
    private val MASKED_ACCOUNT = Regex("""[Xx*]{2,}\d{2,}""")
    private val WHITESPACE = Regex("""\s+""")

    /** Known payment apps by package name, for when the receipt text doesn't name the app. */
    private val PACKAGE_APPS = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "com.dreamplug.android.cred" to "CRED",
        "com.dreamplug.androidapp" to "CRED",
        "in.amazon.mShop.android.shopping" to "Amazon Pay"
    )

    fun clean(raw: String): String = raw
        .replace(URL, " ")
        .replace(ID_LABEL, " ")
        .replace(UPI_ID, " ")
        .replace(LONG_NUMBER, " ")
        .replace(MASKED_ACCOUNT, " ")
        .replace(WHITESPACE, " ")
        .trim()
        .take(MAX_LENGTH)

    fun appForPackage(packageName: String?): String? = packageName?.let { PACKAGE_APPS[it] }

    /**
     * The text handed to the parser: cleaned, plus "via <app>" when the sharing app is a known
     * payment app the text itself doesn't name, so the right app is pre-selected.
     */
    fun toParserInput(raw: String, sourcePackage: String?): String {
        val text = clean(raw)
        val app = appForPackage(sourcePackage)
        return if (app != null && AiParser.findAppPreset(text) == null) "$text via $app" else text
    }
}
