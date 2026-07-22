package com.example.repository

import com.example.models.User

interface AuthRepository {
    suspend fun registerUser(
        fullName: String,
        idNumber: String,
        phoneNumber: String,
        email: String,
        password: CharSequence
    ): Result<User>

    suspend fun loginUser(
        email: String,
        password: CharSequence
    ): Result<User>

    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    suspend fun logout(): Result<Unit>

    suspend fun getCurrentUser(): User?
}
