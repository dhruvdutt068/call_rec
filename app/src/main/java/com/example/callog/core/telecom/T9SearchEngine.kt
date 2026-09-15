package com.example.callog.core.telecom

import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.domain.model.Lead
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.domain.model.Person
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result representing a contact matched via T9 keypad or number searching.
 */
data class T9MatchResult(
    val personId: String?,
    val displayName: String,
    val phoneNumber: String,
    val normalizedPhoneNumber: String,
    val companyName: String? = null,
    val crmStatus: LeadStatus = LeadStatus.UNKNOWN,
    val priority: LeadPriority = LeadPriority.MEDIUM,
    val matchedNameRange: IntRange? = null,
    val matchedNumberRange: IntRange? = null,
    val matchScore: Int = 0
)

/**
 * High-performance T9 Predictive Search Engine.
 *
 * Translates letters to T9 digits (2=ABC ... 9=WXYZ) and performs instant multi-attribute matching
 * over names, companies, and phone numbers with intelligent ranking.
 */
@Singleton
open class T9SearchEngine @Inject constructor() {

    companion object {
        private val CHAR_TO_T9 = charArrayOf(
            '2', '2', '2', // a, b, c
            '3', '3', '3', // d, e, f
            '4', '4', '4', // g, h, i
            '5', '5', '5', // j, k, l
            '6', '6', '6', // m, n, o
            '7', '7', '7', '7', // p, q, r, s
            '8', '8', '8', // t, u, v
            '9', '9', '9', '9'  // w, x, y, z
        )

        /**
         * Converts a character to its T9 keypad digit.
         */
        fun charToT9Digit(c: Char): Char {
            val lower = c.lowercaseChar()
            return when {
                lower in 'a'..'z' -> CHAR_TO_T9[lower - 'a']
                lower in '0'..'9' -> lower
                lower == '+' -> '0'
                else -> ' '
            }
        }

        /**
         * Converts an entire text string to its T9 digit sequence.
         */
        fun textToT9(text: String): String {
            val sb = StringBuilder(text.length)
            for (c in text) {
                val digit = charToT9Digit(c)
                sb.append(digit)
            }
            return sb.toString()
        }
    }

    /**
     * Performs T9 and substring search against a list of Persons and CRM Leads.
     *
     * @param query The entered numeric/digit string (e.g. "724" for "RAH" or phone digits)
     * @param people The list of available canonical contacts
     * @param leadsMap Map of personId to CRM Lead record
     * @param limit Maximum results to return
     */
    open fun search(
        query: String,
        people: List<Person>,
        leadsMap: Map<String, Lead> = emptyMap(),
        limit: Int = 20
    ): List<T9MatchResult> {
        val cleanQuery = query.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleanQuery.isBlank()) return emptyList()

        val results = mutableListOf<T9MatchResult>()

        for (person in people) {
            val lead = leadsMap[person.id]
            val status = lead?.status ?: LeadStatus.UNKNOWN
            val priority = lead?.priority ?: LeadPriority.MEDIUM

            // Match against Person display name via T9
            val nameT9 = textToT9(person.displayName)
            val nameMatchIndex = findMatchIndex(nameT9, person.displayName, cleanQuery)

            // Match against phone numbers
            for (phone in person.phoneNumbers) {
                val rawNumber = phone.phoneNumber
                val normNumber = phone.normalizedNumber.ifBlank { PhoneNumberNormalizer.normalize(rawNumber) }

                val numberMatchIndex = rawNumber.indexOf(cleanQuery, ignoreCase = true)
                val normMatchIndex = if (numberMatchIndex == -1) normNumber.indexOf(cleanQuery) else -1

                if (nameMatchIndex != -1 || numberMatchIndex != -1 || normMatchIndex != -1) {
                    var score = 0

                    val nameRange = if (nameMatchIndex != -1) {
                        score += 100 // Name match base score
                        if (nameMatchIndex == 0) score += 50 // Prefix match bonus
                        nameMatchIndex until (nameMatchIndex + cleanQuery.length)
                    } else null

                    val numberRange = when {
                        numberMatchIndex != -1 -> {
                            score += 50
                            if (numberMatchIndex == 0) score += 30
                            numberMatchIndex until (numberMatchIndex + cleanQuery.length)
                        }
                        normMatchIndex != -1 -> {
                            score += 40
                            normMatchIndex until (normMatchIndex + cleanQuery.length)
                        }
                        else -> null
                    }

                    // CRM priority bonus
                    if (status == LeadStatus.HOT) score += 40
                    if (status == LeadStatus.WARM) score += 20
                    if (priority == LeadPriority.URGENT) score += 30

                    results.add(
                        T9MatchResult(
                            personId = person.id,
                            displayName = person.displayName,
                            phoneNumber = rawNumber,
                            normalizedPhoneNumber = normNumber,
                            companyName = person.companyName,
                            crmStatus = status,
                            priority = priority,
                            matchedNameRange = nameRange,
                            matchedNumberRange = numberRange,
                            matchScore = score
                        )
                    )
                }
            }
        }

        return results
            .sortedByDescending { it.matchScore }
            .distinctBy { it.phoneNumber }
            .take(limit)
    }

    /**
     * Finds match index in T9 converted string, favoring word boundaries.
     */
    private fun findMatchIndex(t9String: String, originalText: String, query: String): Int {
        val directIndex = t9String.indexOf(query)
        if (directIndex != -1) return directIndex

        // Check each word start (e.g. "Rahul Sharma" -> query matches "Sharma")
        val words = originalText.split(Regex("[\\s,.-]+"))
        var accumulatedLength = 0
        for (word in words) {
            val wordT9 = textToT9(word)
            val indexInWord = wordT9.indexOf(query)
            if (indexInWord != -1) {
                val realIndex = originalText.indexOf(word, accumulatedLength, ignoreCase = true)
                if (realIndex != -1) {
                    return realIndex + indexInWord
                }
            }
            accumulatedLength += word.length + 1
        }

        return -1
    }
}
