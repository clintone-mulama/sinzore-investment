package com.example.models

/**
 * Model representing the live global financial configuration of the Sinzore Investment app.
 */
data class GlobalConfig(
    val totalBalance: Double = 0.0,
    val totalExpectedBalance: Double = 0.0,
    val activeLoans: Double = 0.0,
    val numActiveLoans: Int = 0,
    val numPendingLoans: Int = 0,
    val totalContributions: Double = 0.0,
    val pendingLoans: Double = 0.0,
    val registeredMembers: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
