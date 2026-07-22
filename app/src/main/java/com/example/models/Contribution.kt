package com.example.models

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Contribution(
    val id: String = "",
    val userId: String = "",        // Reference to the member who contributed
    val userName: String = "",      // Contributor's display/full name
    val amount: Double = 0.0,       // Amount contributed in Ksh
    val date: Long = 0L,            // Timestamp of the transaction
    val source: String = "mpesa",   // Either "mpesa" or "treasurer" (for manual seeds)

    val contributorUid: String = "",
    val contributorFullName: String = "",
    val contributorPhoneNumber: String = "",
    @ServerTimestamp
    val dateContributed: Date? = null,
    val paymentMethod: String = "", // e.g. "M-PESA" or "Manual Record"
    @get:PropertyName("isManual")
    @PropertyName("isManual")
    val isManual: Boolean = false,
    
    // Audit information for Manual Records
    val treasurerUid: String = "",
    val treasurerName: String = ""
)
