package com.unishuttle.app.domain.model

enum class UserRole {
    STUDENT,
    DRIVER,
    ADMIN,
    ;

    companion object {
        fun fromString(value: String): UserRole =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: STUDENT
    }
}
