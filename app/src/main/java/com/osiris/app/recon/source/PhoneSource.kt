package com.osiris.app.recon.source

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import com.osiris.app.recon.PhoneResult
import java.util.Locale

/** Phone number validation/formatting, done entirely on-device via Google's libphonenumber —
 * mirrors `osiris-backend/src/app/api/osint/phone/route.ts`. No network call at all, unlike
 * every other RECON tool. */
object PhoneSource {

    private val phoneUtil = PhoneNumberUtil.getInstance()

    fun lookup(numberInput: String): PhoneResult {
        var query = numberInput.trim()
        val digitsOnly = query.filter { it.isDigit() }

        query = when {
            digitsOnly.length == 10 && !query.startsWith("+") && !query.startsWith("00") -> "+1$digitsOnly"
            !query.startsWith("+") && !query.startsWith("00") -> "+$query"
            else -> query
        }

        return try {
            val parsed: Phonenumber.PhoneNumber = phoneUtil.parse(query, null)
            val isValid = phoneUtil.isValidNumber(parsed)
            val regionCode = phoneUtil.getRegionCodeForNumber(parsed) ?: "Unknown"
            val countryCode = parsed.countryCode

            val formatE164 = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            val formatIntl = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
            val formatNat = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)

            val regionName = if (regionCode != "Unknown") {
                runCatching { Locale("", regionCode).displayCountry.takeIf { it.isNotBlank() } }.getOrNull() ?: regionCode
            } else {
                "Unknown Region"
            }

            val nationalDigits = parsed.nationalNumber.toString()
            val firstDigit = nationalDigits.firstOrNull()
            val lineType = when {
                countryCode == 1 && nationalDigits.length == 10 -> "MOBILE_OR_LANDLINE"
                firstDigit == '7' || firstDigit == '8' || firstDigit == '9' -> "MOBILE"
                else -> "LANDLINE"
            }

            PhoneResult(
                query = numberInput,
                valid = isValid,
                number = formatE164,
                international = formatIntl,
                national = formatNat,
                countryCode = "+$countryCode",
                region = regionName,
                lineType = lineType,
            )
        } catch (e: Exception) {
            PhoneResult(
                query = numberInput,
                valid = false,
                number = query,
                international = query,
                national = query,
                countryCode = "Unknown",
                region = "Invalid format",
                lineType = "UNKNOWN",
                error = e.message,
            )
        }
    }
}
