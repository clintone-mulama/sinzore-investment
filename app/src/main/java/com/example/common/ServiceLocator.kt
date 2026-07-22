package com.example.common

import com.example.network.ApiClient
import com.example.network.ApiService
import com.example.repository.AuthRepository
import com.example.repository.AuthRepositoryImpl
import com.example.repository.InvestmentRepository
import com.example.repository.InvestmentRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Thread-safe Service Locator facilitating clean Constructor Injection
 * for ViewModels and other components without compilation/KSP overhead.
 */
object ServiceLocator {
    
    @Volatile
    private var authRepository: AuthRepository? = null

    @Volatile
    private var investmentRepository: InvestmentRepository? = null

    fun provideAuthRepository(): AuthRepository {
        return authRepository ?: synchronized(this) {
            authRepository ?: AuthRepositoryImpl(
                firebaseAuth = FirebaseAuth.getInstance(),
                firestore = FirebaseFirestore.getInstance()
            ).also { authRepository = it }
        }
    }

    fun provideInvestmentRepository(): InvestmentRepository {
        return investmentRepository ?: synchronized(this) {
            investmentRepository ?: InvestmentRepositoryImpl(
                firestore = FirebaseFirestore.getInstance(),
                firebaseAuth = FirebaseAuth.getInstance(),
                apiService = ApiClient.apiService
            ).also { investmentRepository = it }
        }
    }

    fun provideApiService(): ApiService {
        return ApiClient.apiService
    }
}

