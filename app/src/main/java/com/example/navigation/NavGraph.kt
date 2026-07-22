package com.example.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.admin.AdminScreen
import com.example.authentication.LoginScreen
import com.example.authentication.RegisterScreen
import com.example.authentication.LoginViewModel
import com.example.authentication.RegisterViewModel
import com.example.common.components.LoadingScreen
import com.example.contributions.ContributionsScreen
import com.example.dashboard.DashboardScreen
import com.example.dashboard.DashboardViewModel
import com.example.dashboard.ActivitiesScreen
import com.example.dashboard.TreasuryActivitiesScreen
import com.example.loans.LoansScreen
import com.example.loans.LoansViewModel
import com.example.members.MembersScreen
import com.example.notifications.NotificationsScreen
import com.example.payments.PaymentsScreen
import com.example.reports.ReportsScreen
import com.example.settings.PreferencesManager
import com.example.settings.SettingsScreen
import com.example.treasury.TreasuryScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Main Navigation Graph assembling all platform operational packages.
 */
@Composable
fun AppNavGraph(
    preferencesManager: PreferencesManager,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        // --- 1. Beautiful Splash/Loading Screen ---
        composable(Screen.Splash.route) {
            val isLoggedIn by preferencesManager.isLoggedInFlow.collectAsState(initial = false)
            val isBiometricsEnabled by preferencesManager.biometricsEnabledFlow.collectAsState(initial = false)
            
            val context = androidx.compose.ui.platform.LocalContext.current
            val activity = context as? androidx.fragment.app.FragmentActivity
            
            var isUnlocked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var authErrorMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            
            // Function to run authentication
            val performUnlock = {
                if (activity != null) {
                    val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                    val biometricPrompt = androidx.biometric.BiometricPrompt(activity, executor,
                        object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                authErrorMessage = errString.toString()
                            }

                            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                isUnlocked = true
                                navController.navigate(Screen.Dashboard.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }

                            override fun onAuthenticationFailed() {
                                authErrorMessage = "Authentication failed. Try again."
                            }
                        })

                    val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Sinzore Lock")
                        .setSubtitle("Unlock Sinzore Investments using your device security")
                        .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        .build()

                    try {
                        biometricPrompt.authenticate(promptInfo)
                    } catch (e: Exception) {
                        authErrorMessage = "Device security lock not configured or supported."
                        // Fallback to bypass if not configured to prevent blocking the user
                        isUnlocked = true
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                } else {
                    // Fallback
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            }

            LaunchedEffect(isLoggedIn, isBiometricsEnabled) {
                delay(1200) // Aesthetic delay for branding
                if (isLoggedIn) {
                    if (isBiometricsEnabled && !isUnlocked) {
                        performUnlock()
                    } else {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                } else {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            }

            if (isLoggedIn && isBiometricsEnabled && !isUnlocked) {
                // Render a modern and beautifully locked screen with an Unlock Button
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "App Locked",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "SINZORE INVESTMENTS",
                            style = MaterialTheme.typography.labelLarge.copy(
                                letterSpacing = 3.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "App is Locked",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = authErrorMessage ?: "Please authenticate using your PIN, pattern, or biometrics to access your account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(40.dp))
                        
                        Button(
                            onClick = { performUnlock() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Unlock"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock App", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LoadingScreen(message = "Securing portfolio vault...")
            }
        }

        // --- 2. Secure Login Screen ---
        composable(Screen.Login.route) {
            val loginViewModel: LoginViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        LoginViewModel(preferencesManager = preferencesManager)
                    }
                }
            )
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        // --- 2.1. Secure Register Screen ---
        composable(Screen.Register.route) {
            val registerViewModel: RegisterViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        RegisterViewModel()
                    }
                }
            )
            RegisterScreen(
                viewModel = registerViewModel,
                onRegisterSuccess = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        // --- 3. Platform Core Dashboard ---
        composable(Screen.Dashboard.route) {
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        DashboardViewModel(preferencesManager = preferencesManager)
                    }
                }
            )
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigate = { destinationRoute ->
                    if (destinationRoute == Screen.Login.route) {
                        navController.navigate(destinationRoute) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        navController.navigate(destinationRoute)
                    }
                }
            )
        }

        // --- 4. Operational Modules ---
        composable(Screen.Members.route) {
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        DashboardViewModel(preferencesManager = preferencesManager)
                    }
                }
            )
            MembersScreen(
                viewModel = dashboardViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Loans.route) {
            val loansViewModel: LoansViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        LoansViewModel(preferencesManager = preferencesManager)
                    }
                }
            )
            LoansScreen(
                viewModel = loansViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Contributions.route) {
            ContributionsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Payments.route) {
            PaymentsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Reports.route) {
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        DashboardViewModel(preferencesManager = preferencesManager)
                    }
                }
            )
            ReportsScreen(
                viewModel = dashboardViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Treasury.route) {
            TreasuryScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Admin.route) {
            AdminScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Notifications.route) {
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        DashboardViewModel(preferencesManager = preferencesManager)
                    }
                }
            )
            NotificationsScreen(
                viewModel = dashboardViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                preferencesManager = preferencesManager,
                onBack = { navController.popBackStack() },
                onLogoutSuccess = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Activities.route) {
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        DashboardViewModel(preferencesManager = preferencesManager)
                    }
                }
            )
            ActivitiesScreen(
                viewModel = dashboardViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.TreasuryActivities.route) {
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        DashboardViewModel(preferencesManager = preferencesManager)
                    }
                }
            )
            TreasuryActivitiesScreen(
                viewModel = dashboardViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
