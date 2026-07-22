package com.example.models

/**
 * Account model representing users in Firebase and Firestore database.
 */
data class User(
    val uid: String = "",
    val fullName: String = "",
    val idNumber: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val role: String = "Member", // "Member", "Treasurer", "Admin"
    val status: String = "Active",
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis(),
    val lastProfileUpdate: Long = 0L
)

