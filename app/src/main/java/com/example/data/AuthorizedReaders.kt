package com.example.data

data class AuthorizedReader(
    val readerId: String,
    val mobileNumber: String,
    val displayName: String,
    val shortName: String,
    val initials: String
)

object AuthorizedReaders {
    const val SHARED_CIRCLE_ID = "narsinh_vishwaraj_duo"
    const val SHARED_CIRCLE_NAME = "Narsinh & Vishwaraj"
    const val DAILY_GOAL_MINUTES = 30

    val NARSINH = AuthorizedReader(
        readerId = "reader_narsinh",
        mobileNumber = "7203070830",
        displayName = "Narsinh Chad",
        shortName = "Narsinh",
        initials = "NC"
    )

    val VISHWARAJ = AuthorizedReader(
        readerId = "reader_vishwaraj",
        mobileNumber = "7096295484",
        displayName = "Vishwaraj Jadeja",
        shortName = "Vishwaraj",
        initials = "VJ"
    )

    val ALL: List<AuthorizedReader> = listOf(NARSINH, VISHWARAJ)

    fun normalizePhoneInput(raw: String): String {
        val digitsOnly = raw.filter { it.isDigit() }
        return when {
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> digitsOnly.substring(2)
            digitsOnly.length == 11 && digitsOnly.startsWith("0") -> digitsOnly.substring(1)
            else -> digitsOnly
        }
    }

    fun findByMobileNumber(rawInput: String): AuthorizedReader? {
        val normalized = normalizePhoneInput(rawInput)
        return ALL.firstOrNull { it.mobileNumber == normalized }
    }

    fun findByReaderId(readerId: String?): AuthorizedReader? {
        if (readerId.isNullOrBlank()) return null
        return ALL.firstOrNull { it.readerId == readerId }
    }

    fun partnerOf(readerId: String): AuthorizedReader {
        return if (readerId == NARSINH.readerId) VISHWARAJ else NARSINH
    }
}
