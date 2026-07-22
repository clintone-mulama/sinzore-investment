package com.example.reports

import android.app.DatePickerDialog
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dashboard.DashboardUiState
import com.example.dashboard.DashboardViewModel
import com.example.models.Contribution
import com.example.models.GlobalConfig
import com.example.models.LedgerEntry
import com.example.models.Loan
import com.example.models.User
import com.example.models.UserActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReportType(val label: String, val icon: ImageVector, val description: String) {
    FINANCIAL_SUMMARY("Financial Summary", Icons.Default.Assessment, "Live treasury cash, loans, and total assets."),
    MEMBER("Member Directory", Icons.Default.Group, "Registered member list, roles, and status."),
    CONTRIBUTIONS("Contributions Ledger", Icons.Default.Receipt, "Chronological record of verified member contributions."),
    LOANS("Loans & Interest", Icons.Default.AccountBalance, "Disbursed principal amounts, interest, and status."),
    REPAYMENTS("Repayment Statement", Icons.Default.MonetizationOn, "Summary of verified loan repayments."),
    LEDGER("Financial Ledger", Icons.Default.ListAlt, "Official audit record of balance modifications."),
    TREASURY("Treasury Cashflow", Icons.Default.History, "Liquidity flows, inflows, and outflows."),
    AUDIT("System Audit Log", Icons.Default.Shield, "User activities and administrative audit logs.")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: DashboardViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    
    // UI states
    var selectedReport by remember { mutableStateOf<ReportType?>(null) }
    var isGeneralReport by remember { mutableStateOf(false) }
    
    BackHandler(enabled = selectedReport != null || isGeneralReport) {
        selectedReport = null
        isGeneralReport = false
    }
    
    // On-demand fetched detailed data
    var isLoadingDetails by remember { mutableStateOf(false) }
    var contributionsList by remember { mutableStateOf<List<Contribution>>(emptyList()) }
    var loansList by remember { mutableStateOf<List<Loan>>(emptyList()) }
    var ledgerList by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }
    var auditList by remember { mutableStateOf<List<UserActivity>>(emptyList()) }
    var membersList by remember { mutableStateOf<List<User>>(emptyList()) }
    
    // Filters and search
    var searchQuery by remember { mutableStateOf("") }
    var filterRange by remember { mutableStateOf("This Month") }
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }
    
    val currentUserName = (uiState as? DashboardUiState.Success)?.user?.fullName ?: "Administrator"
    val config = (uiState as? DashboardUiState.Success)?.config ?: GlobalConfig()

    // Load data on demand
    fun loadDetailedData() {
        scope.launch {
            try {
                isLoadingDetails = true
                val db = FirebaseFirestore.getInstance()
                
                // Fetch in parallel using coroutines
                val contrTask = db.collection("contributions").get()
                val loansTask = db.collection("loans").get()
                val ledgerTask = db.collection("ledger").get()
                val auditTask = db.collection("activities").get()
                val usersTask = db.collection("users").get()
                
                val contrSnapshot = contrTask.await()
                val loansSnapshot = loansTask.await()
                val ledgerSnapshot = ledgerTask.await()
                val auditSnapshot = auditTask.await()
                val usersSnapshot = usersTask.await()
                
                contributionsList = contrSnapshot.toObjects(Contribution::class.java)
                loansList = loansSnapshot.toObjects(Loan::class.java)
                ledgerList = ledgerSnapshot.toObjects(LedgerEntry::class.java)
                auditList = auditSnapshot.toObjects(UserActivity::class.java)
                membersList = usersSnapshot.toObjects(User::class.java)
            } catch (e: Exception) {
                Toast.makeText(context, "Error fetching report details: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoadingDetails = false
            }
        }
    }

    // Load on selection
    LaunchedEffect(selectedReport, isGeneralReport) {
        if (selectedReport != null || isGeneralReport) {
            loadDetailedData()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isGeneralReport) "General Report" else selectedReport?.label ?: "Financial Reports",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    if (selectedReport != null || isGeneralReport) {
                        IconButton(onClick = {
                            selectedReport = null
                            isGeneralReport = false
                        }, modifier = Modifier.testTag("reports_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Menu")
                        }
                    } else if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("reports_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.testTag("reports_screen_root")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            if (selectedReport == null && !isGeneralReport) {
                // Reports main directory menu
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(
                                    text = "SINZORE INVESTMENTS",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "AUDIT PORTFOLIO & STATEMENTS",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "AVAILABLE STATEMENT TYPES",
                            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 1.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    items(ReportType.values()) { type ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReport = type }
                                .testTag("report_button_${type.name.lowercase()}"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = type.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = type.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = type.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Open Report",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = { isGeneralReport = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("general_report_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Assignment, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GENERATE GENERAL REPORT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            } else {
                // Inside report view
                if (isLoadingDetails) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Querying live database records...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Let's filter first
                    val periods = getPeriodTimestamps(filterRange, customStartDate, customEndDate)
                    
                    // Filter datasets based on selection and range
                    val filteredContributions = contributionsList.filter {
                        val ts = if (it.dateContributed != null) it.dateContributed.time else it.date
                        ts in periods.first..periods.second
                    }.filter {
                        searchQuery.isEmpty() || 
                        it.userName.contains(searchQuery, ignoreCase = true) || 
                        it.contributorFullName.contains(searchQuery, ignoreCase = true) ||
                        it.id.contains(searchQuery, ignoreCase = true)
                    }

                    val filteredLoans = loansList.filter {
                        val ts = it.createdAtLong
                        ts == 0L || ts in periods.first..periods.second
                    }.filter {
                        searchQuery.isEmpty() ||
                        it.effectiveApplicantName.contains(searchQuery, ignoreCase = true) ||
                        it.applicantIdNumber.contains(searchQuery, ignoreCase = true) ||
                        it.applicantPhoneNumber.contains(searchQuery, ignoreCase = true) ||
                        it.id.contains(searchQuery, ignoreCase = true)
                    }

                    val filteredLedger = ledgerList.filter {
                        val ts = it.firestoreServerTimestamp?.time ?: 0L
                        ts == 0L || ts in periods.first..periods.second
                    }.filter {
                        searchQuery.isEmpty() ||
                        it.memberName.contains(searchQuery, ignoreCase = true) ||
                        it.performedBy.contains(searchQuery, ignoreCase = true) ||
                        it.receiptNumber.contains(searchQuery, ignoreCase = true) ||
                        it.relatedDocumentId.contains(searchQuery, ignoreCase = true)
                    }

                    val filteredAudit = auditList.filter {
                        it.timestamp in periods.first..periods.second
                    }.filter {
                        searchQuery.isEmpty() ||
                        it.userName.contains(searchQuery, ignoreCase = true) ||
                        it.activityType.contains(searchQuery, ignoreCase = true) ||
                        it.details.contains(searchQuery, ignoreCase = true)
                    }

                    val filteredMembers = membersList.filter {
                        searchQuery.isEmpty() ||
                        it.fullName.contains(searchQuery, ignoreCase = true) ||
                        it.idNumber.contains(searchQuery, ignoreCase = true) ||
                        it.phoneNumber.contains(searchQuery, ignoreCase = true)
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Query Controls Section
                        QueryControls(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            filterRange = filterRange,
                            onFilterRangeChange = { filterRange = it },
                            onSelectCustomRange = { start, end ->
                                customStartDate = start
                                customEndDate = end
                            }
                        )

                        // Print Action Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Matching records: ${
                                    if (isGeneralReport) "Full suite"
                                    else when (selectedReport) {
                                        ReportType.FINANCIAL_SUMMARY -> "1 Summary"
                                        ReportType.MEMBER -> "${filteredMembers.size} members"
                                        ReportType.CONTRIBUTIONS -> "${filteredContributions.size} entries"
                                        ReportType.LOANS -> "${filteredLoans.size} applications"
                                        ReportType.REPAYMENTS -> "${filteredLoans.sumOf { it.repayments.size }} entries"
                                        ReportType.LEDGER -> "${filteredLedger.size} ledger rows"
                                        ReportType.TREASURY -> "1 Statement"
                                        ReportType.AUDIT -> "${filteredAudit.size} log lines"
                                        null -> ""
                                    }
                                }",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = {
                                    scope.launch {
                                        val htmlContent = if (isGeneralReport) {
                                            generateHtmlWrapper(
                                                title = "Combined General Report Statement",
                                                userName = currentUserName,
                                                content = generateGeneralReportHtml(
                                                    config = config,
                                                    members = filteredMembers,
                                                    contributions = filteredContributions,
                                                    loans = filteredLoans,
                                                    ledger = filteredLedger,
                                                    activities = filteredAudit
                                                )
                                            )
                                        } else {
                                            generateHtmlWrapper(
                                                title = selectedReport?.label ?: "Financial Statement",
                                                userName = currentUserName,
                                                content = when (selectedReport) {
                                                    ReportType.FINANCIAL_SUMMARY -> generateFinancialSummaryHtml(config, filteredLoans, filteredLedger)
                                                    ReportType.MEMBER -> generateMemberReportHtml(filteredMembers, filteredContributions, filteredLoans)
                                                    ReportType.CONTRIBUTIONS -> generateContributionsReportHtml(filteredContributions)
                                                    ReportType.LOANS -> generateLoansReportHtml(filteredLoans)
                                                    ReportType.REPAYMENTS -> generateRepaymentsReportHtml(filteredLoans)
                                                    ReportType.LEDGER -> generateLedgerReportHtml(filteredLedger)
                                                    ReportType.TREASURY -> generateTreasuryReportHtml(config, filteredContributions, filteredLoans)
                                                    ReportType.AUDIT -> generateAuditReportHtml(filteredAudit)
                                                    null -> ""
                                                }
                                            )
                                        }
                                        printReportHtml(context, htmlContent, "Sinzore_Report_${selectedReport?.name ?: "General"}")
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Print / PDF", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        // Lazy Loaded Rows representing the detailed ledger
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isGeneralReport) {
                                item {
                                    SectionHeader("1. Financial Summary")
                                    FinancialSummaryView(config, filteredLoans, filteredLedger)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SectionHeader("2. Member Report")
                                }
                                items(filteredMembers) { MemberReportRow(it, filteredContributions, filteredLoans) }
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SectionHeader("3. Contributions Report")
                                }
                                items(filteredContributions) { ContributionReportRow(it) }
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SectionHeader("4. Loans Report")
                                }
                                items(filteredLoans) { LoanReportRow(it) }
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SectionHeader("5. Repayments Report")
                                }
                                items(filteredLoans.flatMap { loan -> loan.repayments.map { loan to it } }) { (loan, repayment) ->
                                    RepaymentReportRow(loan, repayment)
                                }
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SectionHeader("6. Financial Ledger")
                                }
                                items(filteredLedger) { LedgerReportRow(it) }
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SectionHeader("7. Treasury Report")
                                    TreasuryReportView(config, filteredContributions, filteredLoans)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SectionHeader("8. Audit Report")
                                }
                                items(filteredAudit) { AuditReportRow(it) }
                            } else {
                                when (selectedReport) {
                                    ReportType.FINANCIAL_SUMMARY -> {
                                        item { FinancialSummaryView(config, filteredLoans, filteredLedger) }
                                    }
                                    ReportType.MEMBER -> {
                                        items(filteredMembers) { MemberReportRow(it, filteredContributions, filteredLoans) }
                                    }
                                    ReportType.CONTRIBUTIONS -> {
                                        items(filteredContributions) { ContributionReportRow(it) }
                                    }
                                    ReportType.LOANS -> {
                                        items(filteredLoans) { LoanReportRow(it) }
                                    }
                                    ReportType.REPAYMENTS -> {
                                        items(filteredLoans.flatMap { loan -> loan.repayments.map { loan to it } }) { (loan, repayment) ->
                                            RepaymentReportRow(loan, repayment)
                                        }
                                    }
                                    ReportType.LEDGER -> {
                                        items(filteredLedger) { LedgerReportRow(it) }
                                    }
                                    ReportType.TREASURY -> {
                                        item { TreasuryReportView(config, filteredContributions, filteredLoans) }
                                    }
                                    ReportType.AUDIT -> {
                                        items(filteredAudit) { AuditReportRow(it) }
                                    }
                                    null -> {}
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                CorporateReportFooter()
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

// ----------------------------------------------------
// FILTER & SEARCH PANEL (QUERY CONTROLS)
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueryControls(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterRange: String,
    onFilterRangeChange: (String) -> Unit,
    onSelectCustomRange: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    val filterOptions = listOf("Today", "Yesterday", "This Week", "Last Week", "This Month", "Last Month", "This Year", "Custom Date Range")
    var showFilterMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(16.dp)
    ) {
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search members, receipts, IDs, reference notes...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("report_search_input"),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Range Selector Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Period: $filterRange",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            Box {
                Button(
                    onClick = { showFilterMenu = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Select Period")
                }

                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false }
                ) {
                    filterOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                showFilterMenu = false
                                if (option == "Custom Date Range") {
                                    // Trigger consecutive date pickers
                                    showDoubleDatePicker(context) { start, end ->
                                        onFilterRangeChange(option)
                                        onSelectCustomRange(start, end)
                                    }
                                } else {
                                    onFilterRangeChange(option)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun showDoubleDatePicker(context: Context, onRangeSelected: (Long, Long) -> Unit) {
    val cal = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, sy, sm, sd ->
            val startCal = Calendar.getInstance().apply {
                set(sy, sm, sd, 0, 0, 0)
            }
            DatePickerDialog(
                context,
                { _, ey, em, ed ->
                    val endCal = Calendar.getInstance().apply {
                        set(ey, em, ed, 23, 59, 59)
                    }
                    onRangeSelected(startCal.timeInMillis, endCal.timeInMillis)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                setTitle("Select End Date")
                show()
            }
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).apply {
        setTitle("Select Start Date")
        show()
    }
}

// ----------------------------------------------------
// CORPORATE STATEMENTS COMPOSE RENDERING
// ----------------------------------------------------

@Composable
fun FinancialSummaryView(config: GlobalConfig, loans: List<Loan>, ledger: List<LedgerEntry>) {
    val totalLoansDisbursed = loans.filter { it.effectiveStatus == "APPROVED" || it.effectiveStatus == "CLEARED" }.sumOf { it.effectiveAmount }
    val totalLoanRepayments = loans.sumOf { it.totalPaid }
    val totalInterestEarned = loans.filter { it.effectiveStatus == "APPROVED" || it.effectiveStatus == "CLEARED" }.sumOf { it.effectiveTotalRepayment - it.effectiveAmount }
    val totalManualAdjustments = ledger.filter { it.transactionType == "Balance Adjustment" }.sumOf { it.amount }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("FINANCIAL STATEMENT SUMMARY", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            StatRow("Available Treasury Cash", config.totalBalance)
            StatRow("Outstanding Loan Balance", config.activeLoans)
            StatRow("Total Assets (Liquidity + Loans)", config.totalBalance + config.activeLoans, highlight = true)
            StatRow("Total Contributions", config.totalContributions)
            StatRow("Total Loans Disbursed", totalLoansDisbursed)
            StatRow("Total Loan Repayments", totalLoanRepayments)
            StatRow("Total Interest Earned", totalInterestEarned)
            StatRow("Total Manual Adjustments", totalManualAdjustments)
        }
    }
}

@Composable
fun TreasuryReportView(config: GlobalConfig, contributions: List<Contribution>, loans: List<Loan>) {
    val totalReceived = contributions.sumOf { it.amount } + loans.sumOf { it.totalPaid }
    val totalPaidOut = loans.filter { it.effectiveStatus == "APPROVED" || it.effectiveStatus == "CLEARED" }.sumOf { it.effectiveAmount }
    val openingBalance = config.totalBalance - totalReceived + totalPaidOut

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("TREASURY CASH FLOW STATEMENT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            StatRow("Opening Balance", openingBalance)
            StatRow("Money Received (Inflows)", totalReceived)
            StatRow("Money Paid Out (Outflows)", totalPaidOut)
            StatRow("Current Available Cash", config.totalBalance, highlight = true)
            StatRow("Outstanding Loan Book", config.activeLoans)
            StatRow("Total Consolidated Assets", config.totalBalance + config.activeLoans, highlight = true)
        }
    }
}

@Composable
fun StatRow(label: String, value: Double, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (highlight) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Ksh ${String.format("%,.2f", value)}",
            style = if (highlight) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ----------------------------------------------------
// RECORD ROWS FOR LISTS
// ----------------------------------------------------

@Composable
fun MemberReportRow(user: User, contributions: List<Contribution>, loans: List<Loan>) {
    val contrSum = contributions.filter { it.userId == user.uid }.sumOf { it.amount }
    val uLoans = loans.filter { it.effectiveApplicantId == user.uid && (it.effectiveStatus == "APPROVED" || it.effectiveStatus == "CLEARED") }
    val totalLoans = uLoans.sumOf { it.effectiveAmount }
    val remaining = uLoans.sumOf { it.remainingBalance }
    val repaid = uLoans.sumOf { it.totalPaid }
    val regDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(user.createdAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(user.fullName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(user.status, style = MaterialTheme.typography.labelMedium, color = if (user.status == "Active") MaterialTheme.colorScheme.primary else Color.Gray)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("National ID: ${user.idNumber} | Phone: ${user.phoneNumber}", style = MaterialTheme.typography.bodySmall)
            Text("Registered: $regDate | Role: ${user.role}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Contributions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Ksh ${String.format("%,.2f", contrSum)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Outstanding Loan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Ksh ${String.format("%,.2f", remaining)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Loans Taken", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Ksh ${String.format("%,.2f", totalLoans)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun ContributionReportRow(contr: Contribution) {
    val dateStr = if (contr.dateContributed != null) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(contr.dateContributed)
    } else {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(contr.date))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(contr.userName.ifBlank { contr.contributorFullName }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text("Receipt: ${contr.id}", style = MaterialTheme.typography.bodySmall)
                Text("Channel: ${contr.paymentMethod} | Source: ${contr.source}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Ksh ${String.format("%,.2f", contr.amount)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(dateStr, style = MaterialTheme.typography.bodySmall)
                Text("Verified", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LoanReportRow(loan: Loan) {
    val interest = loan.effectiveTotalRepayment - loan.effectiveAmount
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(loan.effectiveApplicantName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(loan.effectiveStatus, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = when(loan.effectiveStatus) {
                    "APPROVED" -> MaterialTheme.colorScheme.primary
                    "CLEARED" -> Color.Green
                    "DENIED" -> Color.Red
                    else -> Color.Gray
                })
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Principal: Ksh ${String.format("%,.2f", loan.effectiveAmount)} | Interest: Ksh ${String.format("%,.2f", interest)}", style = MaterialTheme.typography.bodySmall)
            Text("Approved By: ${loan.approvedBy.ifBlank { "N/A" }} | Date: ${loan.approvedExactDate.ifBlank { "N/A" }}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Total Repayable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Ksh ${String.format("%,.2f", loan.effectiveTotalRepayment)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Repaid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Ksh ${String.format("%,.2f", loan.totalPaid)}", style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Remaining Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Ksh ${String.format("%,.2f", loan.remainingBalance)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun RepaymentReportRow(loan: Loan, repayment: com.example.models.Repayment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(loan.effectiveApplicantName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text("Loan ID: ${repayment.loanId}", style = MaterialTheme.typography.bodySmall)
                Text("Receipt: ${repayment.effectiveTransactionId} | Method: ${repayment.paymentMethod}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Ksh ${String.format("%,.2f", repayment.amount)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("${repayment.exactDate} ${repayment.exactTime}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun LedgerReportRow(entry: LedgerEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.transactionType, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Ksh ${String.format("%,.2f", entry.amount)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Member: ${entry.memberName.ifBlank { "N/A" }} | Recorded By: ${entry.performedBy}", style = MaterialTheme.typography.bodySmall)
            Text("Receipt: ${entry.receiptNumber.ifBlank { "N/A" }} | Date: ${entry.exactDate} ${entry.exactTime}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Treasury Before: Ksh ${String.format("%,.2f", entry.treasuryBalanceBefore)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Treasury After: Ksh ${String.format("%,.2f", entry.treasuryBalanceAfter)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun AuditReportRow(act: UserActivity) {
    val dateStr = if (act.serverTimestamp != null) {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(act.serverTimestamp)
    } else {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(act.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(act.activityType, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(if (act.isTreasuryAction) "Treasury" else "Member", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(act.details, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("By: ${act.userName}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text(dateStr, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun CorporateReportFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "This report was generated electronically by the SINZORE'S SONS AND DAUGHTERS INVESTMENT Management System.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// ----------------------------------------------------
// FILTER RANGE UTILITIES
// ----------------------------------------------------
fun getPeriodTimestamps(filter: String, customStart: Long? = null, customEnd: Long? = null): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    val now = System.currentTimeMillis()
    
    var start = 0L
    var end = now
    
    when (filter) {
        "Today" -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            start = cal.timeInMillis
        }
        "Yesterday" -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            end = cal.timeInMillis - 1
            cal.add(Calendar.DAY_OF_YEAR, -1)
            start = cal.timeInMillis
        }
        "This Week" -> {
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            start = cal.timeInMillis
        }
        "Last Week" -> {
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            end = cal.timeInMillis - 1
            cal.add(Calendar.WEEK_OF_YEAR, -1)
            start = cal.timeInMillis
        }
        "This Month" -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            start = cal.timeInMillis
        }
        "Last Month" -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            end = cal.timeInMillis - 1
            cal.add(Calendar.MONTH, -1)
            start = cal.timeInMillis
        }
        "This Year" -> {
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            start = cal.timeInMillis
        }
        "Custom Date Range" -> {
            start = customStart ?: 0L
            end = customEnd ?: now
        }
    }
    return Pair(start, end)
}

// ----------------------------------------------------
// PDF PRINTING ADAPTER TRIGGER
// ----------------------------------------------------
fun printReportHtml(context: Context, htmlContent: String, jobName: String) {
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
}

// ----------------------------------------------------
// HTML CORPORATE TEMPLATE GENERATION HELPERS
// ----------------------------------------------------
fun generateHtmlWrapper(title: String, userName: String, content: String): String {
    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                @page {
                    size: A4;
                    margin: 15mm;
                    @bottom-center {
                        content: "This report was generated electronically by the SINZORE'S SONS AND DAUGHTERS INVESTMENT Management System.";
                        font-family: Arial, sans-serif;
                        font-size: 8px;
                        color: #777777;
                    }
                }
                body {
                    font-family: Arial, sans-serif;
                    color: #1A1C1E;
                    margin: 0;
                    padding: 0;
                    background-color: #FFFFFF;
                }
                .header {
                    text-align: center;
                    border-bottom: 2px solid #0061A4;
                    padding-bottom: 10px;
                    margin-bottom: 20px;
                }
                .header h1 {
                    font-size: 18px;
                    color: #0061A4;
                    margin: 0;
                    text-transform: uppercase;
                    letter-spacing: 1px;
                }
                .header h2 {
                    font-size: 13px;
                    color: #1A1C1E;
                    margin: 4px 0 0 0;
                }
                .meta-table {
                    width: 100%;
                    margin-bottom: 15px;
                    font-size: 10px;
                    border-collapse: collapse;
                }
                .meta-table td {
                    padding: 3px 0;
                }
                .meta-table td.label {
                    font-weight: bold;
                    color: #555555;
                    width: 15%;
                }
                .meta-table td.value {
                    color: #1A1C1E;
                }
                table.report-table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-top: 10px;
                    margin-bottom: 20px;
                    font-size: 9px;
                }
                table.report-table th {
                    background-color: #0061A4;
                    color: #FFFFFF;
                    text-align: left;
                    padding: 5px;
                    font-weight: bold;
                    border: 1px solid #0061A4;
                }
                table.report-table td {
                    padding: 5px;
                    border: 1px solid #E1E2E5;
                }
                table.report-table tr:nth-child(even) {
                    background-color: #F4F6FA;
                }
                .stat-box {
                    border: 1px solid #D1E4FF;
                    background-color: #FDFBFF;
                    padding: 10px;
                    border-radius: 4px;
                    margin-bottom: 12px;
                }
                .stat-grid {
                    display: grid;
                    grid-template-columns: repeat(2, 1fr);
                    gap: 10px;
                }
                .stat-item {
                    padding: 5px;
                    border-bottom: 1px solid #E1E2E5;
                }
                .stat-label {
                    font-size: 9px;
                    color: #555555;
                }
                .stat-value {
                    font-size: 12px;
                    font-weight: bold;
                    color: #0061A4;
                }
                .section-title {
                    font-size: 12px;
                    font-weight: bold;
                    color: #0061A4;
                    margin-top: 20px;
                    margin-bottom: 8px;
                    text-transform: uppercase;
                    border-bottom: 1px solid #D1E4FF;
                    padding-bottom: 3px;
                }
                .footer {
                    text-align: center;
                    font-size: 8px;
                    color: #777777;
                    border-top: 1px solid #E1E2E5;
                    padding-top: 6px;
                    margin-top: 25px;
                }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>SINZORE'S SONS AND DAUGHTERS INVESTMENT</h1>
                <h2>$title</h2>
            </div>
            <table class="meta-table">
                <tr>
                    <td class="label">Generated Date:</td>
                    <td class="value">$dateStr</td>
                    <td class="label">Generated By:</td>
                    <td class="value">$userName</td>
                </tr>
            </table>
            
            $content
            
            <div class="footer">
                This report was generated electronically by the SINZORE'S SONS AND DAUGHTERS INVESTMENT Management System.
            </div>
        </body>
        </html>
    """
}

fun generateFinancialSummaryHtml(config: GlobalConfig, loans: List<Loan>, ledger: List<LedgerEntry>): String {
    val totalLoansDisbursed = loans.filter { it.effectiveStatus == "APPROVED" || it.effectiveStatus == "CLEARED" }.sumOf { it.effectiveAmount }
    val totalLoanRepayments = loans.sumOf { it.totalPaid }
    val totalInterestEarned = loans.filter { it.effectiveStatus == "APPROVED" || it.effectiveStatus == "CLEARED" }.sumOf { it.effectiveTotalRepayment - it.effectiveAmount }
    val totalManualAdjustments = ledger.filter { it.transactionType == "Balance Adjustment" }.sumOf { it.amount }
    
    return """
        <div class="stat-box">
            <div class="stat-grid">
                <div class="stat-item">
                    <div class="stat-label">Available Treasury Cash</div>
                    <div class="stat-value">Ksh ${String.format("%,.2f", config.totalBalance)}</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">Outstanding Loan Balance</div>
                    <div class="stat-value">Ksh ${String.format("%,.2f", config.activeLoans)}</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">Total Assets</div>
                    <div class="stat-value">Ksh ${String.format("%,.2f", config.totalBalance + config.activeLoans)}</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">Total Contributions</div>
                    <div class="stat-value">Ksh ${String.format("%,.2f", config.totalContributions)}</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">Total Loans Disbursed</div>
                    <div class="stat-value">Ksh ${String.format("%,.2f", totalLoansDisbursed)}</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">Total Loan Repayments</div>
                    <div class="stat-value">Ksh ${String.format("%,.2f", totalLoanRepayments)}</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">Total Interest Earned</div>
                    <div class="stat-value">Ksh ${String.format("%,.2f", totalInterestEarned)}</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">Total Manual Adjustments</div>
                    <div class="stat-value">Ksh ${String.format("%,.2f", totalManualAdjustments)}</div>
                </div>
            </div>
        </div>
    """
}

fun generateMemberReportHtml(members: List<User>, contributions: List<Contribution>, loans: List<Loan>): String {
    val sb = StringBuilder()
    sb.append("""
        <table class="report-table">
            <thead>
                <tr>
                    <th>Full Name</th>
                    <th>National ID</th>
                    <th>Phone Number</th>
                    <th>Status</th>
                    <th>Total Contributions</th>
                    <th>Total Loans Taken</th>
                    <th>Outstanding Loan Balance</th>
                    <th>Total Repaid</th>
                </tr>
            </thead>
            <tbody>
    """)
    for (m in members) {
        val mContr = contributions.filter { it.userId == m.uid }.sumOf { it.amount }
        val mLoans = loans.filter { it.effectiveApplicantId == m.uid && (it.effectiveStatus == "APPROVED" || it.effectiveStatus == "CLEARED") }
        val totalLoans = mLoans.sumOf { it.effectiveAmount }
        val outstanding = mLoans.sumOf { it.remainingBalance }
        val repaid = mLoans.sumOf { it.totalPaid }
        
        sb.append("""
            <tr>
                <td>${m.fullName}</td>
                <td>${m.idNumber}</td>
                <td>${m.phoneNumber}</td>
                <td>${m.status}</td>
                <td>Ksh ${String.format("%,.2f", mContr)}</td>
                <td>Ksh ${String.format("%,.2f", totalLoans)}</td>
                <td>Ksh ${String.format("%,.2f", outstanding)}</td>
                <td>Ksh ${String.format("%,.2f", repaid)}</td>
            </tr>
        """)
    }
    sb.append("</tbody></table>")
    return sb.toString()
}

fun generateContributionsReportHtml(contributions: List<Contribution>): String {
    val sb = StringBuilder()
    sb.append("""
        <table class="report-table">
            <thead>
                <tr>
                    <th>Member</th>
                    <th>Amount</th>
                    <th>Payment Method</th>
                    <th>Receipt Number</th>
                    <th>Source</th>
                    <th>Date</th>
                    <th>Verified Status</th>
                </tr>
            </thead>
            <tbody>
    """)
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    for (c in contributions) {
        val dateStr = if (c.dateContributed != null) sdf.format(c.dateContributed) else sdf.format(Date(c.date))
        sb.append("""
            <tr>
                <td>${c.userName.ifBlank { c.contributorFullName }}</td>
                <td>Ksh ${String.format("%,.2f", c.amount)}</td>
                <td>${c.paymentMethod}</td>
                <td>${c.id}</td>
                <td>${c.source}</td>
                <td>$dateStr</td>
                <td>Verified</td>
            </tr>
        """)
    }
    sb.append("</tbody></table>")
    return sb.toString()
}

fun generateLoansReportHtml(loans: List<Loan>): String {
    val sb = StringBuilder()
    sb.append("""
        <table class="report-table">
            <thead>
                <tr>
                    <th>Applicant</th>
                    <th>Principal</th>
                    <th>Interest</th>
                    <th>Total Repayment</th>
                    <th>Paid</th>
                    <th>Remaining</th>
                    <th>Status</th>
                    <th>Approval Date</th>
                    <th>Treasurer</th>
                </tr>
            </thead>
            <tbody>
    """)
    for (l in loans) {
        val interest = l.effectiveTotalRepayment - l.effectiveAmount
        sb.append("""
            <tr>
                <td>${l.effectiveApplicantName}</td>
                <td>Ksh ${String.format("%,.2f", l.effectiveAmount)}</td>
                <td>Ksh ${String.format("%,.2f", interest)}</td>
                <td>Ksh ${String.format("%,.2f", l.effectiveTotalRepayment)}</td>
                <td>Ksh ${String.format("%,.2f", l.totalPaid)}</td>
                <td>Ksh ${String.format("%,.2f", l.remainingBalance)}</td>
                <td>${l.effectiveStatus}</td>
                <td>${l.approvedExactDate.ifBlank { "N/A" }}</td>
                <td>${l.approvedBy.ifBlank { "N/A" }}</td>
            </tr>
        """)
    }
    sb.append("</tbody></table>")
    return sb.toString()
}

fun generateRepaymentsReportHtml(loans: List<Loan>): String {
    val sb = StringBuilder()
    sb.append("""
        <table class="report-table">
            <thead>
                <tr>
                    <th>Member</th>
                    <th>Loan ID</th>
                    <th>Amount</th>
                    <th>Receipt Number</th>
                    <th>Method</th>
                    <th>Date</th>
                    <th>Time</th>
                </tr>
            </thead>
            <tbody>
    """)
    for (l in loans) {
        for (r in l.repayments) {
            sb.append("""
                <tr>
                    <td>${l.effectiveApplicantName}</td>
                    <td>${l.id}</td>
                    <td>Ksh ${String.format("%,.2f", r.amount)}</td>
                    <td>${r.effectiveTransactionId}</td>
                    <td>${r.paymentMethod}</td>
                    <td>${r.exactDate}</td>
                    <td>${r.exactTime}</td>
                </tr>
            """)
        }
    }
    sb.append("</tbody></table>")
    return sb.toString()
}

fun generateLedgerReportHtml(ledger: List<LedgerEntry>): String {
    val sb = StringBuilder()
    sb.append("""
        <table class="report-table">
            <thead>
                <tr>
                    <th>Transaction Type</th>
                    <th>Member</th>
                    <th>Performed By</th>
                    <th>Amount</th>
                    <th>Treasury Before</th>
                    <th>Treasury After</th>
                    <th>Receipt</th>
                    <th>Date</th>
                    <th>Time</th>
                </tr>
            </thead>
            <tbody>
    """)
    for (entry in ledger) {
        sb.append("""
            <tr>
                <td>${entry.transactionType}</td>
                <td>${entry.memberName}</td>
                <td>${entry.performedBy}</td>
                <td>Ksh ${String.format("%,.2f", entry.amount)}</td>
                <td>Ksh ${String.format("%,.2f", entry.treasuryBalanceBefore)}</td>
                <td>Ksh ${String.format("%,.2f", entry.treasuryBalanceAfter)}</td>
                <td>${entry.receiptNumber}</td>
                <td>${entry.exactDate}</td>
                <td>${entry.exactTime}</td>
            </tr>
        """)
    }
    sb.append("</tbody></table>")
    return sb.toString()
}

fun generateTreasuryReportHtml(config: GlobalConfig, contributions: List<Contribution>, loans: List<Loan>): String {
    val totalReceived = contributions.sumOf { it.amount } + loans.sumOf { it.totalPaid }
    val totalPaidOut = loans.filter { it.effectiveStatus == "APPROVED" || it.effectiveStatus == "CLEARED" }.sumOf { it.effectiveAmount }
    val openingBalance = config.totalBalance - totalReceived + totalPaidOut
    
    return """
        <div class="stat-box">
            <div class="stat-grid">
                <div class="stat-item">
                    <div class="stat-label">Opening Balance</div>
                    <div class="stat-value">Ksh ${String.format("%,.2f", openingBalance)}</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">Money Received</div>
                    <div class="stat-value">Ksh ${String.format("%,.2f", totalReceived)}</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">Money Paid Out</div>
                    <div class="stat-value">Ksh ${String.format("%,.2f", totalPaidOut)}</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">Current Cash</div>
                    <div class="stat-value">Ksh ${String.format("%,.2f", config.totalBalance)}</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">Outstanding Loans</div>
                    <div class="stat-value">Ksh ${String.format("%,.2f", config.activeLoans)}</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">Total Assets</div>
                    <div class="stat-value">Ksh ${String.format("%,.2f", config.totalBalance + config.activeLoans)}</div>
                </div>
            </div>
        </div>
    """
}

fun generateAuditReportHtml(activities: List<UserActivity>): String {
    val sb = StringBuilder()
    sb.append("""
        <table class="report-table">
            <thead>
                <tr>
                    <th>Action</th>
                    <th>Performed By</th>
                    <th>Timestamp</th>
                    <th>Source</th>
                    <th>Device</th>
                </tr>
            </thead>
            <tbody>
    """)
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    for (act in activities) {
        val dateStr = if (act.serverTimestamp != null) sdf.format(act.serverTimestamp) else sdf.format(Date(act.timestamp))
        val source = if (act.isTreasuryAction) "Treasury" else "Member"
        sb.append("""
            <tr>
                <td>${act.activityType} - ${act.details}</td>
                <td>${act.userName}</td>
                <td>$dateStr</td>
                <td>$source</td>
                <td>Android Mobile App</td>
            </tr>
        """)
    }
    sb.append("</tbody></table>")
    return sb.toString()
}

fun generateGeneralReportHtml(
    config: GlobalConfig,
    members: List<User>,
    contributions: List<Contribution>,
    loans: List<Loan>,
    ledger: List<LedgerEntry>,
    activities: List<UserActivity>
): String {
    val sb = StringBuilder()
    
    sb.append("<div class=\"section-title\">1. Financial Summary Report</div>")
    sb.append(generateFinancialSummaryHtml(config, loans, ledger))
    
    sb.append("<div class=\"section-title\">2. Member Report</div>")
    sb.append(generateMemberReportHtml(members, contributions, loans))
    
    sb.append("<div class=\"section-title\">3. Contributions Report</div>")
    sb.append(generateContributionsReportHtml(contributions))
    
    sb.append("<div class=\"section-title\">4. Loans Report</div>")
    sb.append(generateLoansReportHtml(loans))
    
    sb.append("<div class=\"section-title\">5. Repayments Report</div>")
    sb.append(generateRepaymentsReportHtml(loans))
    
    sb.append("<div class=\"section-title\">6. Financial Ledger Report</div>")
    sb.append(generateLedgerReportHtml(ledger))
    
    sb.append("<div class=\"section-title\">7. Treasury Report</div>")
    sb.append(generateTreasuryReportHtml(config, contributions, loans))
    
    sb.append("<div class=\"section-title\">8. Audit Report</div>")
    sb.append(generateAuditReportHtml(activities))
    
    return sb.toString()
}
