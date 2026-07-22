package com.example.repository

import com.example.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override suspend fun registerUser(
        fullName: String,
        idNumber: String,
        phoneNumber: String,
        email: String,
        password: CharSequence
    ): Result<User> {
        return try {
            // 1. Verify ID Number is unique in Firestore (with timeout to prevent infinite loading)
            val idQuery = try {
                withTimeout(8000L) {
                    firestore.collection("users")
                        .whereEqualTo("idNumber", idNumber)
                        .get()
                        .await()
                }
            } catch (ex: Exception) {
                return Result.failure(Exception("National ID validation failed or timed out: ${ex.localizedMessage ?: "Timeout"}. Please make sure your Firestore Database is initialized and active in your Firebase Console, and that your phone has an active internet connection."))
            }

            if (!idQuery.isEmpty) {
                return Result.failure(Exception("National ID Number is already in use by another account."))
            }

            // 2. Verify Phone Number is unique in Firestore
            val phoneQuery = try {
                withTimeout(8000L) {
                    firestore.collection("users")
                        .whereEqualTo("phoneNumber", phoneNumber)
                        .get()
                        .await()
                }
            } catch (ex: Exception) {
                return Result.failure(Exception("Phone number validation failed or timed out: ${ex.localizedMessage ?: "Timeout"}. Please make sure your Firestore Database is initialized and active in your Firebase Console, and that your phone has an active internet connection."))
            }

            if (!phoneQuery.isEmpty) {
                return Result.failure(Exception("Phone Number is already in use by another account."))
            }

            // 3. Create Firebase Authentication Account
            val authResult = try {
                withTimeout(10000L) {
                    firebaseAuth.createUserWithEmailAndPassword(email, password.toString()).await()
                }
            } catch (ex: Exception) {
                return Result.failure(Exception("Authentication failed or timed out: ${ex.localizedMessage ?: "Timeout"}. Please check your connection and verify that 'Email/Password' is enabled in the 'Sign-in method' tab under Authentication in your Firebase Console."))
            }
            val firebaseUser = authResult.user ?: return Result.failure(Exception("Registration failed: Account creation failed."))

            // 4. Create Firestore Document
            val user = User(
                uid = firebaseUser.uid,
                fullName = fullName,
                idNumber = idNumber,
                phoneNumber = phoneNumber,
                email = email,
                role = "Member", // Default role
                status = "Active",
                createdAt = System.currentTimeMillis(),
                lastLogin = System.currentTimeMillis()
            )

            try {
                withTimeout(8000L) {
                    firestore.collection("users")
                        .document(firebaseUser.uid)
                        .set(user)
                        .await()
                }
                
                // Create a shared notification for the new member
                val notifId = firestore.collection("notifications").document().id
                val notif = com.example.models.AppNotification(
                    id = notifId,
                    title = "New Member Joined",
                    message = "$fullName has joined Sinzore Investment Group.",
                    timestamp = System.currentTimeMillis(),
                    senderName = fullName,
                    type = "member_joined"
                )
                firestore.collection("notifications").document(notifId).set(notif).await()
            } catch (ex: Exception) {
                // If notification write fails, we still return the success user
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loginUser(
        email: String,
        password: CharSequence
    ): Result<User> {
        return try {
            // 1. Authenticate with Firebase Auth
            val authResult = try {
                withTimeout(10000L) {
                    firebaseAuth.signInWithEmailAndPassword(email, password.toString()).await()
                }
            } catch (ex: Exception) {
                return Result.failure(Exception("Login authentication failed or timed out: ${ex.localizedMessage ?: "Timeout"}. Please check your connection and credentials."))
            }
            val firebaseUser = authResult.user ?: return Result.failure(Exception("Login failed: Authentication failed."))

            // 2. Fetch user profile from Firestore
            val userDoc = try {
                withTimeout(8000L) {
                    firestore.collection("users")
                        .document(firebaseUser.uid)
                        .get()
                        .await()
                }
            } catch (ex: Exception) {
                return Result.failure(Exception("Logged in successfully, but fetching your database profile failed/timed out: ${ex.localizedMessage ?: "Timeout"}. Please make sure your Firestore Database is initialized and that your security rules are correctly set up."))
            }

            if (!userDoc.exists()) {
                return Result.failure(Exception("User record not found in the database."))
            }

            val user = userDoc.toObject(User::class.java) ?: return Result.failure(Exception("Failed to decode user profile."))

            // 3. Update lastLogin timestamp in Firestore
            val updatedUser = user.copy(lastLogin = System.currentTimeMillis())
            try {
                withTimeout(8000L) {
                    firestore.collection("users")
                        .document(firebaseUser.uid)
                        .set(updatedUser)
                        .await()
                }
            } catch (ex: Exception) {
                // Non-blocking warning since profile login has succeeded
            }

            Result.success(updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            withTimeout(8000L) {
                firebaseAuth.sendPasswordResetEmail(email).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            firebaseAuth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentUser(): User? {
        val firebaseUser = firebaseAuth.currentUser ?: return null
        return try {
            val userDoc = withTimeout(5000L) {
                firestore.collection("users")
                    .document(firebaseUser.uid)
                    .get()
                    .await()
            }
            userDoc.toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
