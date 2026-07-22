package com.example.common.components

import java.text.NumberFormat
import java.util.Locale

/**
 * Enterprise-grade utility for formatting and parsing currencies and phone numbers.
 */
object CurrencyFormatter {

    private val kesLocale = Locale("en", "KE")
    private val currencyFormat = NumberFormat.getCurrencyInstance(kesLocale)

    /**
     * Formats a double value to KES currency representation (e.g., KES 1,250.00)
     */
    fun formatToCurrency(amount: Double): String {
        return try {
            currencyFormat.format(amount)
        } catch (e: Exception) {
            "KES %,.2f".format(amount)
        }
    }

    /**
     * Formats a double value to a pure decimal representation without currency symbol (e.g., 1,250.00)
     */
    fun formatToDecimal(amount: Double): String {
        return try {
            val formatter = NumberFormat.getNumberInstance(Locale.US)
            formatter.minimumFractionDigits = 2
            formatter.maximumFractionDigits = 2
            formatter.format(amount)
        } catch (e: Exception) {
            "%,.2f".format(amount)
        }
    }

    /**
     * Parses a formatted string to clean numeric double value.
     */
    fun parseToDouble(value: String): Double {
        if (value.isBlank()) return 0.0
        val cleanString = value.replace("[^\\d.]".toRegex(), "")
        return cleanString.toDoubleOrNull() ?: 0.0
    }
}

object PhoneNumberFormatter {

    /**
     * Formats raw digits to East African Standard phone pattern: +254 (XXX) XXX XXX
     * or standard fallback for other lengths.
     */
    fun formatPhoneNumber(rawDigits: String): String {
        val digits = rawDigits.replace("[^\\d]".toRegex(), "")
        
        return when {
            digits.startsWith("254") -> {
                formatKenyaNumber(digits)
            }
            digits.startsWith("0") && digits.length == 10 -> {
                val base = digits.substring(1)
                formatKenyaNumber("254$base")
            }
            digits.length == 9 -> {
                formatKenyaNumber("254$digits")
            }
            else -> {
                // Fallback standard partitioning
                if (digits.length <= 3) {
                    digits
                } else if (digits.length <= 6) {
                    "${digits.substring(0, 3)}-${digits.substring(3)}"
                } else {
                    "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6, minOf(digits.length, 10))}"
                }
            }
        }
    }

    private fun formatKenyaNumber(digits: String): String {
        // digits is expected to start with "254" and be 12 digits long
        if (digits.length != 12) {
            return "+$digits"
        }
        val countryCode = digits.substring(0, 3)
        val area = digits.substring(3, 6)
        val part1 = digits.substring(6, 9)
        val part2 = digits.substring(9, 12)
        return "+$countryCode ($area) $part1-$part2"
    }

    /**
     * Cleans formatted phone number into digits only.
     */
    fun cleanPhoneNumber(formatted: String): String {
        return formatted.replace("[^\\d]".toRegex(), "")
    }
}
