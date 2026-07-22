package com.example.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.common.components.CurrencyFormatter
import com.example.models.GlobalConfig
import com.example.models.User
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bottom navigation tab enumeration.
 */
enum class DashboardTab(val label: String, val icon: ImageVector, val tag: String) {
    DASHBOARD("Dashboard", Icons.Default.Home, "tab_item_dashboard"),
    NOTIFICATIONS("Notifications", Icons.Default.Notifications, "tab_item_notifications"),
    REPORTS("Reports", Icons.Default.Assessment, "tab_item_reports"),
    PROFILE("Profile", Icons.Default.Person, "tab_item_profile")
}

/**
 * Main wrapper screen integrating the bottom navigation shell and state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var activeTab by rememberSaveable { mutableStateOf(DashboardTab.DASHBOARD) }

    val notifications by viewModel.notifications.collectAsState()
    val lastReadNotificationsTimestamp by viewModel.lastReadNotificationsTimestamp.collectAsState()

    val hasUnreadNotifications by remember(notifications, lastReadNotificationsTimestamp) {
        derivedStateOf {
            notifications.any { it.timestamp > lastReadNotificationsTimestamp }
        }
    }

    LaunchedEffect(activeTab, notifications) {
        if (activeTab == DashboardTab.NOTIFICATIONS) {
            viewModel.markAllNotificationsAsRead()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = "App Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Sinzore Investment",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    .testTag("dashboard_bottom_bar")
            ) {
                DashboardTab.values().forEach { tab ->
                    val isSelected = activeTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { activeTab = tab },
                        icon = {
                            if (tab == DashboardTab.NOTIFICATIONS && hasUnreadNotifications) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(6.dp)
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label
                                )
                            }
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        },
        modifier = modifier.testTag("dashboard_root")
    ) { innerPadding ->
        Crossfade(
            targetState = activeTab,
            label = "TabTransition",
            modifier = Modifier.padding(innerPadding)
        ) { currentTab ->
            when (currentTab) {
                DashboardTab.DASHBOARD -> {
                    DashboardTabContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onNavigate = onNavigate,
                        onRetry = { viewModel.retry() },
                        onInitializeConfig = { viewModel.initializeDefaultGlobalConfig() }
                    )
                }
                DashboardTab.NOTIFICATIONS -> {
                    com.example.notifications.NotificationsScreen(
                        viewModel = viewModel,
                        onBack = null
                    )
                }
                DashboardTab.REPORTS -> {
                    com.example.reports.ReportsScreen(
                        viewModel = viewModel,
                        onBack = null
                    )
                }
                DashboardTab.PROFILE -> {
                    com.example.profile.ProfileScreen(
                        viewModel = viewModel,
                        preferencesManager = viewModel.preferencesManager,
                        onLogout = {
                            coroutineScope.launch {
                                viewModel.preferencesManager.clearSession()
                                com.example.common.ServiceLocator.provideAuthRepository().logout()
                                onNavigate(com.example.navigation.Screen.Login.route)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Main dashboard tab content.
 */
@Composable
fun DashboardTabContent(
    uiState: DashboardUiState,
    viewModel: DashboardViewModel,
    onNavigate: (String) -> Unit,
    onRetry: () -> Unit,
    onInitializeConfig: () -> Unit
) {
    when (uiState) {
        is DashboardUiState.Loading -> {
            DashboardShimmerState()
        }
        is DashboardUiState.Error -> {
            DashboardErrorState(
                message = uiState.message,
                onRetry = onRetry
            )
        }
        is DashboardUiState.Success -> {
            DashboardSuccessState(
                user = uiState.user,
                config = uiState.config,
                viewModel = viewModel,
                onNavigate = onNavigate,
                onInitializeConfig = onInitializeConfig
            )
        }
    }
}

/**
 * Renders the success dashboard.
 */
@Composable
fun DashboardSuccessState(
    user: User,
    config: GlobalConfig,
    viewModel: DashboardViewModel,
    onNavigate: (String) -> Unit,
    onInitializeConfig: () -> Unit
) {
    var showMakeContributionDialog by remember { mutableStateOf(false) }
    var showApplyLoanDialog by remember { mutableStateOf(false) }
    var isSubmittingLoan by remember { mutableStateOf(false) }
    var isSubmittingContribution by remember { mutableStateOf(false) }
    var showStkSimulation by remember { mutableStateOf(false) }
    var stkAmount by remember { mutableStateOf(0.0) }
    var stkPhone by remember { mutableStateOf("") }
    
    var showRepayLoanDialog by remember { mutableStateOf(false) }
    var activeLoanToRepay by remember { mutableStateOf<com.example.models.Loan?>(null) }
    var isSubmittingRepayment by remember { mutableStateOf(false) }
    var isCheckingActiveLoan by remember { mutableStateOf(false) }

    var showManualAddContributionDialog by remember { mutableStateOf(false) }
    var showManualAddLoanDialog by remember { mutableStateOf(false) }
    var showManualUpdateBalanceDialog by remember { mutableStateOf(false) }
    var showApproveDenyLoansDialog by remember { mutableStateOf(false) }
    var showSendAnnouncementDialog by remember { mutableStateOf(false) }
    var isSubmittingManualContribution by remember { mutableStateOf(false) }
    var isSubmittingManualLoan by remember { mutableStateOf(false) }
    var isSubmittingManualBalance by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // --- Clean Minimalist Profile Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Welcome back,",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Text(
                    text = user.fullName,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Modern High-Contrast Role Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = user.role,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Format Last Login
                    val lastLoginStr = remember(user.lastLogin) {
                        try {
                            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                            sdf.format(Date(user.lastLogin))
                        } catch (e: Exception) {
                            "Just now"
                        }
                    }
                    Text(
                        text = "Last sync: $lastLoginStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Initials Avatar Circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                val initials = remember(user.fullName) {
                    user.fullName.split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .map { it.first().uppercase() }
                        .joinToString("")
                }
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // --- Friendly helper notice if database config document has 0.0 value or empty ---
        if (config.totalBalance == 0.0 && config.totalContributions == 0.0 && (user.role == "Admin" || user.role == "Treasurer")) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Initial Setup",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Initialize the global financial summary collection in Firestore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Button(
                        onClick = onInitializeConfig,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("init_default_config_button")
                    ) {
                        Text(
                            text = "Setup",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // --- Financial Summary Headers & Cards ---
        Text(
            text = "FINANCIAL SUMMARY",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color(0xFF1A1C1E).copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Prominent Current Treasury Balance Card (Flat, clean minimalist look, primary soft blue color scheme)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFD1E4FF)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("dashboard_balance_card")
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "CURRENT TREASURY BALANCE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color(0xFF0061A4)
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedMoneyText(
                    amount = config.totalBalance,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color(0xFF1A1C1E)
                )
            }
        }

        // Secondary cards: Expected Balance & Active Loans side-by-side
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Total Expected Balance
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFDFBFF)
                ),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color(0xFF1A1C1E).copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Total Expected Balance",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1A1C1E).copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AnimatedMoneyText(
                        amount = config.totalExpectedBalance,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1A1C1E)
                    )
                }
            }

            // Active Loans Balance
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFDFBFF)
                ),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color(0xFF1A1C1E).copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Active Loans",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1A1C1E).copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AnimatedMoneyText(
                        amount = config.activeLoans,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1A1C1E)
                    )
                }
            }
        }

        // Count metrics: Active count & Pending count side-by-side
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Number of Active Loans
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFDFBFF)
                ),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color(0xFF1A1C1E).copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Number of Active Loans",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1A1C1E).copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${config.numActiveLoans} active",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1A1C1E)
                    )
                }
            }

            // Number of Pending Loans
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFDFBFF)
                ),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color(0xFF1A1C1E).copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Number of Pending Loans",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1A1C1E).copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${config.numPendingLoans} pending",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1A1C1E)
                    )
                }
            }
        }

        // --- Action Buttons Section (Clean minimalist, role-specific segmentation) ---
        val context = androidx.compose.ui.platform.LocalContext.current

        // Core common buttons across all roles
        val commonActions = remember {
            listOf(
                ActionItem("Make Contribution", Icons.Default.AddCard, "make_contribution", isCoded = true),
                ActionItem("Apply Loan", Icons.Default.RequestQuote, "apply_loan", isCoded = true),
                ActionItem("Repay Loan", Icons.Default.Payments, "repay_loan", isCoded = true),
                ActionItem("Contributions", Icons.Default.TrendingUp, "contributions", isCoded = true),
                ActionItem("Loans", Icons.Default.AccountBalance, "loans", isCoded = true),
                ActionItem("My Activities", Icons.Default.History, "activities", isCoded = true),
                ActionItem("View Members", Icons.Default.Group, "members", isCoded = true)
            )
        }

        // Treasurer quick action buttons
        val treasurerActions = remember {
            listOf(
                ActionItem("Approve/Deny Loan", Icons.Default.FactCheck, "approve_deny_loan", isCoded = true),
                ActionItem("Manual Add Loan", Icons.Default.PostAdd, "manual_add_loan", isCoded = true),
                ActionItem("Manual Add Contributions", Icons.Default.Queue, "manual_add_contributions", isCoded = true),
                ActionItem("Update Current Balance", Icons.Default.AccountBalanceWallet, "update_current_balance", isCoded = true),
                ActionItem("Treasury Activities", Icons.Default.History, "treasury_activities", isCoded = true)
            )
        }

        // Admin quick action buttons
        val adminActions = remember {
            listOf(
                ActionItem("Manage Members", Icons.Default.Group, "members", isCoded = true),
                ActionItem("Send Announcement", Icons.Default.Campaign, "send_announcement", isCoded = true)
            )
        }

        // Chunk lists into pairs of 2 to display in 2-column layout without nested LazyVerticalGrid scroll issues
        val commonChunked = remember(commonActions) { commonActions.chunked(2) }
        val treasurerChunked = remember(treasurerActions) { treasurerActions.chunked(2) }
        val adminChunked = remember(adminActions) { adminActions.chunked(2) }

        // Render sections based on roles and requirements
        val customNavigate: (String) -> Unit = { destination ->
            if (destination == "make_contribution") {
                showMakeContributionDialog = true
            } else if (destination == "apply_loan") {
                showApplyLoanDialog = true
            } else if (destination == "manual_add_contributions") {
                showManualAddContributionDialog = true
            } else if (destination == "manual_add_loan") {
                showManualAddLoanDialog = true
            } else if (destination == "update_current_balance") {
                showManualUpdateBalanceDialog = true
            } else if (destination == "approve_deny_loan") {
                showApproveDenyLoansDialog = true
            } else if (destination == "send_announcement") {
                showSendAnnouncementDialog = true
            } else if (destination == "repay_loan") {
                if (!isCheckingActiveLoan) {
                    isCheckingActiveLoan = true
                    viewModel.checkActiveLoan(
                        onSuccess = { loan ->
                            isCheckingActiveLoan = false
                            if (loan != null) {
                                activeLoanToRepay = loan
                                showRepayLoanDialog = true
                            } else {
                                android.widget.Toast.makeText(context, "You have no active loan to repay.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        onFailure = { errorMsg ->
                            isCheckingActiveLoan = false
                            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } else {
                onNavigate(destination)
            }
        }
        
        // 1. Common buttons for ALL users
        ActionsSection(
            title = "QUICK ACTIONS",
            chunkedItems = commonChunked,
            context = context,
            onNavigate = customNavigate
        )

        // 2. Treasurer quick action buttons (ONLY for Treasurer role)
        if (user.role == "Treasurer") {
            ActionsSection(
                title = "TREASURER ACTIONS",
                chunkedItems = treasurerChunked,
                context = context,
                onNavigate = customNavigate
            )
        }

        // 3. Admin quick action buttons (ONLY for Admin role, on top of default buttons)
        if (user.role == "Admin") {
            ActionsSection(
                title = "ADMIN ACTIONS",
                chunkedItems = adminChunked,
                context = context,
                onNavigate = customNavigate
            )
        }

        // --- Active dialog renderers ---
        val members by viewModel.members.collectAsState()

        if (showManualAddContributionDialog) {
            ManualAddContributionDialog(
                isSubmitting = isSubmittingManualContribution,
                members = members,
                onDismiss = { showManualAddContributionDialog = false },
                onSubmit = { member, amount ->
                    isSubmittingManualContribution = true
                    viewModel.recordManualContribution(
                        member = member,
                        amount = amount,
                        onSuccess = {
                            isSubmittingManualContribution = false
                            showManualAddContributionDialog = false
                            android.widget.Toast.makeText(context, "Manual contribution recorded successfully!", android.widget.Toast.LENGTH_LONG).show()
                        },
                        onFailure = { errorMsg ->
                            isSubmittingManualContribution = false
                            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        if (showManualAddLoanDialog) {
            ManualAddLoanDialog(
                isSubmitting = isSubmittingManualLoan,
                members = members,
                onDismiss = { showManualAddLoanDialog = false },
                onSubmit = { member, amount ->
                    isSubmittingManualLoan = true
                    viewModel.recordManualLoan(
                        member = member,
                        amount = amount,
                        onSuccess = {
                            isSubmittingManualLoan = false
                            showManualAddLoanDialog = false
                            android.widget.Toast.makeText(context, "Manual loan disbursed successfully!", android.widget.Toast.LENGTH_LONG).show()
                        },
                        onFailure = { errorMsg ->
                            isSubmittingManualLoan = false
                            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        if (showManualUpdateBalanceDialog) {
            ManualUpdateBalanceDialog(
                isSubmitting = isSubmittingManualBalance,
                onDismiss = { showManualUpdateBalanceDialog = false },
                onSubmit = { amount ->
                    isSubmittingManualBalance = true
                    viewModel.updateGlobalBalance(
                        newBalance = amount,
                        onSuccess = {
                            isSubmittingManualBalance = false
                            showManualUpdateBalanceDialog = false
                            android.widget.Toast.makeText(context, "Treasury balance updated successfully!", android.widget.Toast.LENGTH_LONG).show()
                        },
                        onFailure = { errorMsg ->
                            isSubmittingManualBalance = false
                            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        if (showApproveDenyLoansDialog) {
            ApproveDenyLoansDialog(
                viewModel = viewModel,
                onDismiss = { showApproveDenyLoansDialog = false }
            )
        }

        if (showSendAnnouncementDialog) {
            com.example.notifications.MakeAnnouncementDialog(
                onDismiss = { showSendAnnouncementDialog = false },
                onSubmit = { title, msg ->
                    viewModel.sendAnnouncement(
                        title = title,
                        message = msg,
                        onSuccess = {
                            showSendAnnouncementDialog = false
                            android.widget.Toast.makeText(context, "Announcement broadcasted successfully!", android.widget.Toast.LENGTH_LONG).show()
                        },
                        onFailure = { error ->
                            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        if (showApplyLoanDialog) {
            ApplyLoanDialog(
                user = user,
                isSubmitting = isSubmittingLoan,
                onDismiss = { showApplyLoanDialog = false },
                onSubmit = { amount ->
                    isSubmittingLoan = true
                    viewModel.applyLoan(
                        amount = amount,
                        onSuccess = {
                            isSubmittingLoan = false
                            showApplyLoanDialog = false
                            android.widget.Toast.makeText(context, "Loan application submitted successfully!", android.widget.Toast.LENGTH_LONG).show()
                        },
                        onFailure = { errorMsg ->
                            isSubmittingLoan = false
                            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        if (showMakeContributionDialog) {
            MakeContributionDialog(
                user = user,
                isSubmitting = isSubmittingContribution,
                onDismiss = { showMakeContributionDialog = false },
                onSubmit = { amount, phone ->
                    stkAmount = amount
                    // Format telephone string to standard 254XXXXXXXXX
                    val formattedPhone = when {
                        phone.startsWith("+254") -> phone.substring(1)
                        phone.startsWith("07") -> "254" + phone.substring(1)
                        phone.startsWith("01") -> "254" + phone.substring(1)
                        phone.startsWith("7") -> "254$phone"
                        phone.startsWith("1") -> "254$phone"
                        else -> phone
                    }
                    stkPhone = formattedPhone
                    isSubmittingContribution = true
                    viewModel.makeContribution(
                        amount = stkAmount,
                        phoneNumber = stkPhone,
                        onSuccess = {
                            isSubmittingContribution = false
                            showMakeContributionDialog = false
                            android.widget.Toast.makeText(context, "STK Push initiated successfully! Please authorize on your phone.", android.widget.Toast.LENGTH_LONG).show()
                        },
                        onFailure = { errorMsg ->
                            isSubmittingContribution = false
                            android.widget.Toast.makeText(context, "Transaction failed: $errorMsg", android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        if (showRepayLoanDialog && activeLoanToRepay != null) {
            RepayLoanDialog(
                user = user,
                loan = activeLoanToRepay!!,
                isSubmitting = isSubmittingRepayment,
                onDismiss = { showRepayLoanDialog = false },
                onSubmit = { amount, phone ->
                    // Format telephone string to standard 254XXXXXXXXX
                    val formattedPhone = when {
                        phone.startsWith("+254") -> phone.substring(1)
                        phone.startsWith("07") -> "254" + phone.substring(1)
                        phone.startsWith("01") -> "254" + phone.substring(1)
                        phone.startsWith("7") -> "254$phone"
                        phone.startsWith("1") -> "254$phone"
                        else -> phone
                    }
                    isSubmittingRepayment = true
                    viewModel.repayLoan(
                        loan = activeLoanToRepay!!,
                        amount = amount,
                        phoneNumber = formattedPhone,
                        onSuccess = {
                            isSubmittingRepayment = false
                            showRepayLoanDialog = false
                            android.widget.Toast.makeText(context, "STK Push initiated successfully! Please authorize loan repayment of Ksh ${String.format("%,.2f", amount)} on your phone.", android.widget.Toast.LENGTH_LONG).show()
                        },
                        onFailure = { errorMsg ->
                            isSubmittingRepayment = false
                            android.widget.Toast.makeText(context, "Repayment failed: $errorMsg", android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        // --- Development Tools Section ---
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "DEVELOPMENT TOOLS",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(12.dp))

        var showRoleSwitcherDialog by remember { mutableStateOf(false) }

        Card(
            onClick = { showRoleSwitcherDialog = true },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFDFBFF) // Crisp white
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A1C1E).copy(alpha = 0.12f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
                .testTag("switch_role_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFD1E4FF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color(0xFF0061A4)
                        )
                    }
                    Column {
                        Text(
                            text = "Set My Development Role",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1A1C1E),
                            modifier = Modifier.testTag("set_my_development_role_title")
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Directly update your role in the Firestore database to test security rules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1A1C1E).copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        if (showRoleSwitcherDialog) {
            com.example.common.DevelopmentRoleSwitcherDialog(
                onDismiss = { showRoleSwitcherDialog = false }
            )
        }
    }
}

/**
 * Animated Money counter fade effect.
 */
@Composable
fun AnimatedMoneyText(
    amount: Double,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val formatted = remember(amount) { CurrencyFormatter.formatToCurrency(amount) }
    Crossfade(targetState = formatted, label = "MoneyAnimation") { targetText ->
        Text(
            text = targetText,
            style = style,
            color = color,
            modifier = modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

data class ActionItem(
    val title: String,
    val icon: ImageVector,
    val destination: String,
    val isCoded: Boolean = false
)

@Composable
fun ActionsSection(
    title: String,
    chunkedItems: List<List<ActionItem>>,
    context: android.content.Context,
    onNavigate: (String) -> Unit
) {
    if (chunkedItems.isNotEmpty()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color(0xFF1A1C1E).copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            chunkedItems.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            ActionTile(
                                item = item,
                                onClick = {
                                    if (item.isCoded) {
                                        onNavigate(item.destination)
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "${item.title} feature not coded yet",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                    if (rowItems.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ActionTile(
    item: ActionItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .testTag("action_tile_${item.destination}")
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("tile_title_${item.destination}")
            )
        }
    }
}

/**
 * Placeholder UI screen for bottom tabs.
 */
@Composable
fun PlaceholderTabScreen(
    title: String,
    subtitle: String
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = "Coming Soon Logo",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "COMING SOON",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Friendly Error & Retry layout.
 */
@Composable
fun DashboardErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connection Offline",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("error_retry_button")
            ) {
                Text(
                    text = "Retry Syncing",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

/**
 * Polished, shimmery loading indicators.
 */
@Composable
fun DashboardShimmerState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
            .testTag("shimmer_loading")
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Profile Header Shimmer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.LightGray.copy(alpha = 0.3f))
                        .shimmer()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.LightGray.copy(alpha = 0.3f))
                        .shimmer()
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray.copy(alpha = 0.3f))
                    .shimmer()
            )
        }

        // Summary Card Shimmer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.LightGray.copy(alpha = 0.3f))
                .shimmer()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.LightGray.copy(alpha = 0.3f))
                    .shimmer()
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.LightGray.copy(alpha = 0.3f))
                    .shimmer()
            )
        }
    }
}

/**
 * Fading animatable alpha shimmer helper.
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "ShimmerAnim")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ShimmerAlpha"
    )
    return this.alpha(alpha)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplyLoanDialog(
    user: User,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("apply_loan_dialog"),
        content = {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Apply for Loan",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            inputError = null
                        },
                        label = { Text("Loan Amount (Ksh)") },
                        placeholder = { Text("Enter amount, e.g. 5000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = inputError != null,
                        modifier = Modifier.fillMaxWidth().testTag("loan_amount_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (inputError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = inputError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Live Breakdown Card
                    val amountVal = amountText.toDoubleOrNull() ?: 0.0
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD1E4FF).copy(alpha = 0.3f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD1E4FF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Interest Charged (10%):", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                Text(CurrencyFormatter.formatToCurrency(amountVal * 0.1), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Repayable Balance:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                Text(CurrencyFormatter.formatToCurrency(amountVal * 1.1), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF0061A4))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All loans attract a flat interest rate of 10% on the principal amount.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("dialog_cancel_button")
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }

                        Button(
                            onClick = {
                                val amount = amountText.toDoubleOrNull()
                                if (amount == null || amount <= 0) {
                                    inputError = "Please enter a valid loan amount greater than zero."
                                } else {
                                    onSubmit(amount)
                                }
                            },
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("dialog_submit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Apply", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAddContributionDialog(
    isSubmitting: Boolean,
    members: List<User>,
    onDismiss: () -> Unit,
    onSubmit: (User, Double) -> Unit
) {
    var selectedMember by remember { mutableStateOf<User?>(null) }
    var amountText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }

    var showConfirmDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting && !showConfirmDialog) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("manual_contribution_dialog"),
        content = {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Record Contribution",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = onDismiss, enabled = !isSubmitting, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close dialog")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD1E4FF)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedMember?.fullName ?: "Select Target Member",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF0061A4))
                            }
                        }

                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            members.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text(member.fullName, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        selectedMember = member
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            inputError = null
                        },
                        label = { Text("Contribution Amount (KES)") },
                        placeholder = { Text("Enter KES amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = inputError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_contribution_amount_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (inputError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = inputError ?: "",
                            color = Color(0xFFB71C1C),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }

                        Button(
                            onClick = {
                                val member = selectedMember
                                val amount = amountText.toDoubleOrNull()
                                if (member == null) {
                                    inputError = "Please select a member."
                                } else if (amount == null || amount <= 0) {
                                    inputError = "Please enter a valid amount greater than 0."
                                } else {
                                    showConfirmDialog = true
                                }
                            },
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("dialog_submit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Record", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    )

    if (showConfirmDialog) {
        val member = selectedMember
        val amount = amountText.toDoubleOrNull() ?: 0.0
        if (member != null) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.padding(24.dp).testTag("manual_contribution_confirm_dialog"),
                content = {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Confirm Details",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Are you sure you want to record a manual contribution of KES ${String.format("%,.2f", amount)} for ${member.fullName}?\n\nPlease double check that the member name and contribution amount are correct before proceeding.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showConfirmDialog = false },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                                ) {
                                    Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                }
                                Button(
                                    onClick = {
                                        showConfirmDialog = false
                                        onSubmit(member, amount)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("confirm_submit_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                                ) {
                                    Text("Confirm", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAddLoanDialog(
    isSubmitting: Boolean,
    members: List<User>,
    onDismiss: () -> Unit,
    onSubmit: (User, Double) -> Unit
) {
    var selectedMember by remember { mutableStateOf<User?>(null) }
    var amountText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }

    var showConfirmDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting && !showConfirmDialog) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("manual_loan_dialog"),
        content = {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Disburse Manual Loan",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = onDismiss, enabled = !isSubmitting, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close dialog")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD1E4FF)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedMember?.fullName ?: "Select Target Member",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF0061A4))
                            }
                        }

                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            members.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text(member.fullName, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        selectedMember = member
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            inputError = null
                        },
                        label = { Text("Loan Principal Amount") },
                        placeholder = { Text("Enter KES amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = inputError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_loan_amount_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (inputError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = inputError ?: "",
                            color = Color(0xFFB71C1C),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    val amountVal = amountText.toDoubleOrNull() ?: 0.0
                    if (amountVal > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFD1E4FF).copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFD1E4FF).copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Interest Rate", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                Text("10%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Interest Amount", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                Text("KES ${String.format("%,.2f", amountVal * 0.10)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                            }
                            Divider(color = Color(0xFFD1E4FF))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Repayment", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                Text("KES ${String.format("%,.2f", amountVal * 1.10)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF0061A4))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }

                        Button(
                            onClick = {
                                val member = selectedMember
                                val amount = amountText.toDoubleOrNull()
                                if (member == null) {
                                    inputError = "Please select a member."
                                } else if (amount == null || amount <= 0) {
                                    inputError = "Please enter a valid amount greater than 0."
                                } else {
                                    showConfirmDialog = true
                                }
                            },
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("dialog_submit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Disburse", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    )

    if (showConfirmDialog) {
        val member = selectedMember
        val amount = amountText.toDoubleOrNull() ?: 0.0
        if (member != null) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.padding(24.dp).testTag("manual_loan_confirm_dialog"),
                content = {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Confirm Loan Disbursal",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Are you sure you want to disburse a manual loan of KES ${String.format("%,.2f", amount)} (with 10% interest, total KES ${String.format("%,.2f", amount * 1.1)}) to ${member.fullName}?\n\nThis will immediately subtract KES ${String.format("%,.2f", amount)} from the available treasury cash and record an active loan.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showConfirmDialog = false },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                                ) {
                                    Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                }
                                Button(
                                    onClick = {
                                        showConfirmDialog = false
                                        onSubmit(member, amount)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("confirm_submit_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                                ) {
                                    Text("Confirm Disburse", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualUpdateBalanceDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    var showConfirmDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting && !showConfirmDialog) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("manual_update_balance_dialog"),
        content = {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Update Treasury Balance",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = onDismiss, enabled = !isSubmitting, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close dialog")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            inputError = null
                        },
                        label = { Text("New Cash Balance (KES)") },
                        placeholder = { Text("Enter new KES amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = inputError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_balance_amount_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (inputError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = inputError ?: "",
                            color = Color(0xFFB71C1C),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }

                        Button(
                            onClick = {
                                val amount = amountText.toDoubleOrNull()
                                if (amount == null || amount < 0) {
                                    inputError = "Please enter a valid amount greater than or equal to 0."
                                } else {
                                    showConfirmDialog = true
                                }
                            },
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("dialog_submit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Update", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    )

    if (showConfirmDialog) {
        val amount = amountText.toDoubleOrNull() ?: 0.0
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(24.dp).testTag("manual_balance_confirm_dialog"),
            content = {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Confirm Balance Update",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Are you sure you want to manually update the treasury cash balance to KES ${String.format("%,.2f", amount)}?\n\nThis will record a manual adjustment in the financial ledger and update the available cash immediately.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showConfirmDialog = false },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                            ) {
                                Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Button(
                                onClick = {
                                    showConfirmDialog = false
                                    onSubmit(amount)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("confirm_submit_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                            ) {
                                Text("Confirm Update", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MakeContributionDialog(
    user: User,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Double, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var phoneText by remember { mutableStateOf(user.phoneNumber) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("make_contribution_dialog"),
        content = {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Contribute Seeds",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            inputError = null
                        },
                        label = { Text("Contribution Amount (Ksh)") },
                        placeholder = { Text("Enter amount, min Ksh 10") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = inputError != null,
                        modifier = Modifier.fillMaxWidth().testTag("contribution_amount_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (inputError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = inputError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phoneText,
                        onValueChange = {
                            phoneText = it
                            phoneError = null
                        },
                        label = { Text("M-Pesa Phone Number") },
                        placeholder = { Text("e.g. 0712345678") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        isError = phoneError != null,
                        modifier = Modifier.fillMaxWidth().testTag("contribution_phone_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (phoneError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = phoneError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0061A4).copy(alpha = 0.05f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF0061A4), modifier = Modifier.size(16.dp))
                            Text(
                                text = "Submitting will initiate an M-Pesa STK Push request to authorize contribution securely.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("dialog_cancel_button")
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }

                        Button(
                            onClick = {
                                val amount = amountText.toDoubleOrNull()
                                val phone = phoneText.trim()
                                
                                val isAmountValid = amount != null && amount >= 10.0
                                val isPhoneValid = phone.length >= 9 && (phone.startsWith("07") || phone.startsWith("01") || phone.startsWith("+254") || phone.startsWith("254"))

                                if (!isAmountValid) {
                                    inputError = "Contribution amount must be at least Ksh 10."
                                }
                                if (!isPhoneValid) {
                                    phoneError = "Please enter a valid Kenyan phone number (e.g., 0712345678)."
                                }

                                if (isAmountValid && isPhoneValid && amount != null) {
                                    onSubmit(amount, phone)
                                }
                            },
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("dialog_submit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Contribute", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StkPushSimulationDialog(
    amount: Double,
    phone: String,
    onPinSubmitted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var isSendingPush by remember { mutableStateOf(true) }
    var pinText by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        isSendingPush = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("stk_push_simulation_dialog"),
        content = {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSendingPush) {
                    Column(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF0061A4),
                            modifier = Modifier.size(48.dp).testTag("stk_sending_indicator")
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Sending STK Push...",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Initiating payment sequence on +$phone",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "M-Pesa STK Push Simulation",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0061A4),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Enter your 4-digit M-Pesa PIN to authorize contribution of Ksh ${String.format("%,.2f", amount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(4) { index ->
                                val hasDigit = index < pinText.length
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (hasDigit) Color(0xFF0061A4) else Color(0xFFD1E4FF))
                                        .border(1.dp, Color(0xFF0061A4), CircleShape)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            val rows = listOf(
                                listOf("1", "2", "3"),
                                listOf("4", "5", "6"),
                                listOf("7", "8", "9"),
                                listOf("⌫", "0", "OK")
                            )
                            
                            rows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { key ->
                                        Card(
                                            onClick = {
                                                if (key == "⌫") {
                                                    if (pinText.isNotEmpty()) pinText = pinText.dropLast(1)
                                                } else if (key == "OK") {
                                                    if (pinText.length == 4) {
                                                        onPinSubmitted(pinText)
                                                    }
                                                } else {
                                                    if (pinText.length < 4) pinText += key
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (key == "OK") Color(0xFF0061A4) else MaterialTheme.colorScheme.surface
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(54.dp)
                                                .testTag("keypad_key_$key")
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Text(
                                                    text = key,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = if (key == "OK") Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        TextButton(onClick = onDismiss) {
                            Text("Cancel Transaction", color = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepayLoanDialog(
    user: User,
    loan: com.example.models.Loan,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Double, String) -> Unit
) {
    var isClearEntireLoan by remember { mutableStateOf(true) }
    var customAmountText by remember { mutableStateOf("") }
    var phoneText by remember { mutableStateOf(user.phoneNumber) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    val remaining = loan.remainingBalance

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("repay_loan_dialog"),
        content = {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Repay Outstanding Loan",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Loan details container card (Minimalist, soft-blue high contrast text style)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD1E4FF).copy(alpha = 0.3f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0061A4).copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "LOAN REPAYMENT STATUS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = Color(0xFF0061A4)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total Loan Repayable:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "Ksh ${String.format("%,.2f", if (loan.totalRepayment > 0.0) loan.totalRepayment else (loan.principalAmount * 1.10))}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Amount Paid So Far:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "Ksh ${String.format("%,.2f", loan.totalPaid)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF0061A4)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Outstanding Balance:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "Ksh ${String.format("%,.2f", remaining)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.Red.copy(alpha = 0.8f)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Progress Bar
                            LinearProgressIndicator(
                                progress = { loan.progressPercentage },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF0061A4),
                                trackColor = Color(0xFFD1E4FF)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "${(loan.progressPercentage * 100).toInt()}% Repaid",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF0061A4),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "CHOOSE REPAYMENT METHOD",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth().align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Card(
                            onClick = { isClearEntireLoan = true },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isClearEntireLoan) 2.dp else 1.dp,
                                color = if (isClearEntireLoan) Color(0xFF0061A4) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isClearEntireLoan) Color(0xFFD1E4FF).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .testTag("repay_clear_entire_card")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize().padding(8.dp)
                            ) {
                                Text(
                                    text = "Clear Full Loan\n(Ksh ${String.format("%,.0f", remaining)})",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isClearEntireLoan) Color(0xFF0061A4) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Card(
                            onClick = { isClearEntireLoan = false },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (!isClearEntireLoan) 2.dp else 1.dp,
                                color = if (!isClearEntireLoan) Color(0xFF0061A4) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (!isClearEntireLoan) Color(0xFFD1E4FF).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .testTag("repay_custom_installment_card")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize().padding(8.dp)
                            ) {
                                Text(
                                    text = "Pay Custom\nInstallment",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (!isClearEntireLoan) Color(0xFF0061A4) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isClearEntireLoan) {
                        OutlinedTextField(
                            value = customAmountText,
                            onValueChange = {
                                customAmountText = it
                                inputError = null
                            },
                            label = { Text("Installment Amount (Ksh)") },
                            placeholder = { Text("Enter amount to pay") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = inputError != null,
                            modifier = Modifier.fillMaxWidth().testTag("repay_amount_input"),
                            shape = RoundedCornerShape(8.dp)
                        )

                        if (inputError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = inputError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = phoneText,
                        onValueChange = {
                            phoneText = it
                            phoneError = null
                        },
                        label = { Text("M-Pesa Phone Number") },
                        placeholder = { Text("e.g. 0712345678") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        isError = phoneError != null,
                        modifier = Modifier.fillMaxWidth().testTag("repay_phone_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (phoneError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = phoneError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0061A4).copy(alpha = 0.05f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF0061A4), modifier = Modifier.size(16.dp))
                            Text(
                                text = "This payment will trigger an M-Pesa STK Push request to authorize your transaction.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("repay_cancel_button")
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }

                        Button(
                            onClick = {
                                val amount = if (isClearEntireLoan) remaining else customAmountText.toDoubleOrNull()
                                val phone = phoneText.trim()

                                val isAmountValid = amount != null && amount >= 1.0 && amount <= remaining + 0.01
                                val isPhoneValid = phone.length >= 9 && (phone.startsWith("07") || phone.startsWith("01") || phone.startsWith("+254") || phone.startsWith("254"))

                                if (!isAmountValid) {
                                    inputError = if (amount != null && amount > remaining) {
                                        "Amount cannot exceed outstanding balance of Ksh ${String.format("%,.2f", remaining)}"
                                    } else {
                                        "Please enter a valid amount of at least Ksh 1."
                                    }
                                }
                                if (!isPhoneValid) {
                                    phoneError = "Please enter a valid Kenyan phone number (e.g., 0712345678)."
                                }

                                if (isAmountValid && isPhoneValid && amount != null) {
                                    onSubmit(amount, phone)
                                }
                            },
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("repay_submit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Repay", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    )
}

