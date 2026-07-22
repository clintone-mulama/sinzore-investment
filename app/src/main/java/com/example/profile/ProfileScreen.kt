package com.example.profile

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.dashboard.DashboardViewModel
import com.example.dashboard.DashboardUiState
import com.example.models.User
import com.example.settings.PreferencesManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: DashboardViewModel,
    preferencesManager: PreferencesManager,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isBiometricsEnabled by preferencesManager.biometricsEnabledFlow.collectAsState(initial = false)
    val userActivities by viewModel.userActivities.collectAsState()
    
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isEditing by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Navigation sub-tabs within the profile (like Instagram / TikTok grids and settings tabs)
    var selectedTab by remember { mutableStateOf(0) } // 0: Identity Info, 1: App Security

    // Synchronize initial input values when success state loads
    LaunchedEffect(uiState) {
        if (uiState is DashboardUiState.Success) {
            val user = (uiState as DashboardUiState.Success).user
            emailInput = user.email
            phoneInput = user.phoneNumber
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeContent
    ) { innerPadding ->
        when (val state = uiState) {
            is DashboardUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is DashboardUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            is DashboardUiState.Success -> {
                val user = state.user
                
                // Calculate dynamic statistics from real transactions/activities list
                val totalContributed = remember(userActivities) {
                    userActivities.filter { 
                        it.activityType.contains("Contribution", ignoreCase = true) 
                    }.sumOf { it.amount }
                }
                val totalLoans = remember(userActivities) {
                    userActivities.filter { 
                        it.activityType.contains("Loan", ignoreCase = true) && 
                        !it.activityType.contains("Denial", ignoreCase = true) 
                    }.sumOf { it.amount }
                }
                val transactionsCount = remember(userActivities) { userActivities.size }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(scrollState)
                        .testTag("profile_screen_root"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Instagram/TikTok Profile Title bar (top row, centered)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = user.fullName.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Premium TikTok Circle Avatar with Primary Color border ring
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.fullName.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // User Name & Verification Badge Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = user.fullName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified Investor",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // TikTok/Instagram unique handle style (using lowercase initials or email prefix)
                    val handleName = remember(user.fullName) {
                        user.fullName.lowercase().replace("\\s".toRegex(), "")
                    }
                    Text(
                        text = "@$handleName",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Medium
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // System Role Badge
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(100.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = user.role.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Instagram / TikTok Stats Counters Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileStatItem(
                            number = "KES ${formatStatValue(totalContributed)}",
                            label = "Contributed"
                        )
                        ProfileStatItem(
                            number = "KES ${formatStatValue(totalLoans)}",
                            label = "Loans"
                        )
                        ProfileStatItem(
                            number = "$transactionsCount",
                            label = "Activities"
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Primary Action Button Row (Instagram/TikTok edit buttons)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isEditing) {
                            OutlinedButton(
                                onClick = {
                                    isEditing = false
                                    emailInput = user.email
                                    phoneInput = user.phoneNumber
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onBackground
                                )
                            ) {
                                Text("Cancel", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (emailInput.trim().isEmpty() || phoneInput.trim().isEmpty()) {
                                        Toast.makeText(context, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isSaving = true
                                    viewModel.updateProfile(
                                        newEmail = emailInput.trim(),
                                        newPhone = phoneInput.trim(),
                                        onSuccess = {
                                            isSaving = false
                                            isEditing = false
                                            Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                                        },
                                        onFailure = { error ->
                                            isSaving = false
                                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_save_button"),
                                enabled = !isSaving,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Save Changes", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Button(
                                onClick = { isEditing = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_edit_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Edit Profile Info", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Instagram / TikTok Grid vs Likes selection tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.GridOn, contentDescription = "Identity") },
                            text = { Text("Identity", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.Security, contentDescription = "Security") },
                            text = { Text("Security", fontWeight = FontWeight.Bold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Tab Contents full screen width
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        if (selectedTab == 0) {
                            // Tab 0: Identity and Contact details
                            if (isEditing) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = borderStroke()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "EDIT CONTACT DETAILS",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))

                                        OutlinedTextField(
                                            value = emailInput,
                                            onValueChange = { emailInput = it },
                                            label = { Text("Email Address") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("profile_email_input"),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        OutlinedTextField(
                                            value = phoneInput,
                                            onValueChange = { phoneInput = it },
                                            label = { Text("Phone Number") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("profile_phone_input"),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                            )
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // 1. RECTIFIED USER ID (Displaying the actual user's National ID!)
                                    ProfileInfoRow(
                                        icon = Icons.Outlined.Badge,
                                        label = "User ID (National ID)",
                                        value = user.idNumber
                                    )

                                    ProfileInfoRow(
                                        icon = Icons.Outlined.Email,
                                        label = "Email Address",
                                        value = user.email
                                    )

                                    ProfileInfoRow(
                                        icon = Icons.Outlined.Phone,
                                        label = "Phone Number",
                                        value = user.phoneNumber
                                    )

                                    val sdf = remember { SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()) }
                                    val registeredDate = remember(user.createdAt) { sdf.format(Date(user.createdAt)) }
                                    val lastLoginDate = remember(user.lastLogin) { sdf.format(Date(user.lastLogin)) }

                                    ProfileInfoRow(
                                        icon = Icons.Outlined.CalendarMonth,
                                        label = "Registered Since",
                                        value = registeredDate
                                    )

                                    ProfileInfoRow(
                                        icon = Icons.Outlined.Timer,
                                        label = "Last Account Activity",
                                        value = lastLoginDate
                                    )
                                }
                            }
                        } else {
                            // Tab 1: App Security Settings
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    ),
                                    border = borderStroke()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Fingerprint,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Secure Lock App",
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Lock Sinzore app with your device PIN, fingerprint, or biometric credentials.",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            )
                                        }
                                        
                                        Switch(
                                            checked = isBiometricsEnabled,
                                            onCheckedChange = { checked ->
                                                if (checked && activity != null) {
                                                    val executor = ContextCompat.getMainExecutor(activity)
                                                    val biometricPrompt = BiometricPrompt(activity, executor,
                                                        object : BiometricPrompt.AuthenticationCallback() {
                                                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                                Toast.makeText(activity, "Verification failed: $errString", Toast.LENGTH_SHORT).show()
                                                            }

                                                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                                coroutineScope.launch {
                                                                    preferencesManager.setBiometricsEnabled(true)
                                                                    Toast.makeText(activity, "App lock enabled", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }

                                                            override fun onAuthenticationFailed() {
                                                                Toast.makeText(activity, "Authentication failed", Toast.LENGTH_SHORT).show()
                                                            }
                                                        })

                                                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                                        .setTitle("Enable App Lock")
                                                        .setSubtitle("Confirm security credentials to protect app vault")
                                                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                                        .build()

                                                    try {
                                                        biometricPrompt.authenticate(promptInfo)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(activity, "Security lock not configured or supported", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    coroutineScope.launch {
                                                        preferencesManager.setBiometricsEnabled(false)
                                                        Toast.makeText(context, "App lock disabled", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.testTag("profile_biometrics_switch")
                                        )
                                    }
                                }


                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Minimalist clean logout text button
                    TextButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier
                            .padding(bottom = 32.dp)
                            .testTag("profile_logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log Out of Session",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "Sign Out Confirmation",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to log out of Sinzore Investments? Your active session will be terminated completely."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("profile_logout_confirm_button")
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProfileStatItem(
    number: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        )
    }
}

@Composable
fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
fun borderStroke() = androidx.compose.foundation.BorderStroke(
    width = 1.dp,
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
)

// Clean format helper for displaying K, M suffixes
private fun formatStatValue(amount: Double): String {
    return when {
        amount >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", amount / 1_000_000)
        amount >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", amount / 1_000)
        else -> amount.toInt().toString()
    }
}
