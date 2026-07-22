package com.example.loans

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.common.components.CurrencyFormatter
import com.example.models.Loan
import com.example.models.Repayment
import com.example.models.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    viewModel: LoansViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    var showApplyDialog by remember { mutableStateOf(false) }
    var showManualLoanDialog by remember { mutableStateOf(false) }
    var showTreasurerActionSelector by remember { mutableStateOf(false) }
    var loanToDeny by remember { mutableStateOf<Loan?>(null) }
    var loanForRepayment by remember { mutableStateOf<Loan?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Synchronize snackbars
    LaunchedEffect(errorMessage, successMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long
            )
            viewModel.clearMessages()
        }
        successMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearMessages()
        }
    }

    val currentUser = (uiState as? LoansUiState.Success)?.currentUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Loans Ledger",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("loans_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFDFBFF) // Crisp White for Clean Minimalism
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.testTag("loans_screen_root")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFDFBFF)) // Crisp White for Clean Minimalism
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is LoansUiState.Loading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator(color = Color(0xFF0061A4))
                    }
                }
                is LoansUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Color(0xFFB71C1C),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Failed to sync loans ledger",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1A1C1E)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF5E6064),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.retry() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                            modifier = Modifier.testTag("error_retry_button")
                        ) {
                            Text("Retry Ledger Sync")
                        }
                    }
                }
                is LoansUiState.Success -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Treasury Balance Bar (Styled clean minimalist, flat, soft blue accents)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFD1E4FF).copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                .border(
                                    1.dp,
                                    Color(0xFFD1E4FF),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "AVAILABLE TREASURY CASH",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = Color(0xFF0061A4)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = CurrencyFormatter.formatToCurrency(state.config.totalBalance),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                        color = Color(0xFF1A1C1E)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "OUTSTANDING LOANS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = Color(0xFF5E6064)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = CurrencyFormatter.formatToCurrency(state.config.activeLoans),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF1A1C1E)
                                    )
                                }
                            }
                        }

                        LoansListContent(
                            currentUser = state.currentUser,
                            loans = state.loans,
                            onApprove = { viewModel.approveLoan(it.id) },
                            onDenyPrompt = { loanToDeny = it },
                            onRepayClick = { loanForRepayment = it }
                        )
                    }
                }
            }

            // Treasurer Choice Dialog
            if (showTreasurerActionSelector) {
                AlertDialog(
                    onDismissRequest = { showTreasurerActionSelector = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                    modifier = Modifier.padding(24.dp),
                    title = { Text("Choose Loan Operation", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    showTreasurerActionSelector = false
                                    showApplyDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Apply for Personal Loan")
                            }

                            Button(
                                onClick = {
                                    showTreasurerActionSelector = false
                                    showManualLoanDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF5E6064)
                                )
                            ) {
                                Icon(Icons.Default.Groups, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Disburse Manual Loan for Member")
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showTreasurerActionSelector = false }) {
                            Text("Cancel", color = Color(0xFF0061A4))
                        }
                    }
                )
            }

            // Loan Application Dialog
            if (showApplyDialog) {
                ApplyLoanDialog(
                    isSubmitting = isSubmitting,
                    onDismiss = { showApplyDialog = false },
                    onSubmit = { amount ->
                        viewModel.applyLoan(amount)
                        showApplyDialog = false
                    }
                )
            }

            // Manual Loan Dialog (Treasurer Disburser)
            if (showManualLoanDialog && uiState is LoansUiState.Success) {
                val successState = uiState as LoansUiState.Success
                ManualLoanDialog(
                    isSubmitting = isSubmitting,
                    members = successState.members,
                    onDismiss = { showManualLoanDialog = false },
                    onSubmit = { member, amount ->
                        viewModel.recordManualLoan(member, amount)
                        showManualLoanDialog = false
                    }
                )
            }

            // Loan Denial Dialog
            loanToDeny?.let { loan ->
                DenyLoanDialog(
                    isSubmitting = isSubmitting,
                    onDismiss = { loanToDeny = null },
                    onSubmit = { reason ->
                        viewModel.denyLoan(loan.id, reason)
                        loanToDeny = null
                    }
                )
            }

            // Repayment Submission Dialog
            loanForRepayment?.let { loan ->
                RepaymentDialog(
                    loan = loan,
                    currentUser = currentUser ?: User(),
                    isSubmitting = isSubmitting,
                    onDismiss = { loanForRepayment = null },
                    onSubmit = { amount, method, receipt ->
                        viewModel.submitRepayment(loan.id, amount, method, receipt)
                        loanForRepayment = null
                    }
                )
            }
        }
    }
}

@Composable
fun LoansListContent(
    currentUser: User,
    loans: List<Loan>,
    onApprove: (Loan) -> Unit,
    onDenyPrompt: (Loan) -> Unit,
    onRepayClick: (Loan) -> Unit
) {
    if (loans.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info",
                tint = Color(0xFFD1E4FF),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Loan Records Found",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF1A1C1E)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Submitted family loan applications will appear in this feed in real-time.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF5E6064),
                textAlign = TextAlign.Center
            )
        }
    } else {
        var expandedLoanId by remember { mutableStateOf<String?>(null) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(loans, key = { it.id }) { loan ->
                LoanItemCard(
                    currentUser = currentUser,
                    loan = loan,
                    isExpanded = expandedLoanId == loan.id,
                    onToggleExpand = {
                        expandedLoanId = if (expandedLoanId == loan.id) null else loan.id
                    },
                    onApprove = { onApprove(loan) },
                    onDeny = { onDenyPrompt(loan) },
                    onRepayClick = { onRepayClick(loan) }
                )
            }
        }
    }
}

@Composable
fun LoanItemCard(
    currentUser: User,
    loan: Loan,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onRepayClick: () -> Unit
) {
    val status = loan.effectiveStatus
    val isPending = status == "PENDING"
    val isApproved = status == "APPROVED"
    val isCleared = status == "CLEARED"
    val isDenied = status == "DENIED"

    val statusBg: Color
    val statusText: Color
    val statusBorder: Color

    when (status) {
        "APPROVED" -> {
            statusBg = Color(0xFFE8F5E9)
            statusText = Color(0xFF1B5E20)
            statusBorder = Color(0xFF4CAF50)
        }
        "CLEARED" -> {
            statusBg = Color(0xFFE3F2FD)
            statusText = Color(0xFF0D47A1)
            statusBorder = Color(0xFF1976D2)
        }
        "DENIED" -> {
            statusBg = Color(0xFFFFEBEE)
            statusText = Color(0xFFB71C1C)
            statusBorder = Color(0xFFF44336)
        }
        else -> { // PENDING
            statusBg = Color(0xFFFFF8E1)
            statusText = Color(0xFFFF8F00)
            statusBorder = Color(0xFFFFB300)
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isExpanded) Color(0xFF0061A4).copy(alpha = 0.3f) else Color(0xFFD1E4FF).copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onToggleExpand() }
            .animateContentSize()
            .testTag("loan_card_${loan.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Summary Header Row (Always visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar Badge: displaying the first 2 letters of the applicant's name
                val displayName = loan.effectiveApplicantName
                val initial = if (displayName.length >= 2) displayName.take(2).uppercase() else (displayName.take(1) + " ").uppercase()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .background(statusBg, shape = RoundedCornerShape(8.dp))
                        .border(1.dp, statusBorder, shape = RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = statusText
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Applicant Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1A1C1E)
                        )
                        if (loan.effectiveSource == "treasurer") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF0061A4).copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFF0061A4).copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Ledger",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFF0061A4)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Capsule status pill
                    Box(
                        modifier = Modifier
                            .background(statusBg.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
                            .border(1.dp, statusBorder.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusText
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Amounts Info
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Ksh ${String.format("%,.0f", loan.effectiveAmount)}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color(0xFF1A1C1E)
                    )
                    Text(
                        text = "Repay: Ksh ${String.format("%,.0f", loan.effectiveTotalRepayment)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5E6064)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Repayment Progress Visual
            if (isPending) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⏳ Awaiting review",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFFFF8F00)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        color = Color(0xFFFF8F00),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else if (isDenied) {
                Text(
                    text = "❌ Rejected - See reason",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFFB71C1C)
                )
            } else { // APPROVED or CLEARED
                Column(modifier = Modifier.fillMaxWidth()) {
                    val barColor = if (isCleared) Color(0xFF1976D2) else Color(0xFF4CAF50)
                    LinearProgressIndicator(
                        progress = { loan.progressPercentage },
                        color = barColor,
                        trackColor = Color(0xFFECEFF1),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Ksh ${String.format("%,.0f", loan.totalPaid)} / Ksh ${String.format("%,.0f", loan.effectiveTotalRepayment)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFF5E6064)
                        )
                        Text(
                            text = "${(loan.progressPercentage * 100).toInt()}% paid",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = barColor
                        )
                    }
                }
            }

            // Expanded details block
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFD1E4FF).copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Loanee Metadata Block
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFDFBFF), shape = RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFD1E4FF).copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    MetadataRow(label = "Loanee Full Name:", value = loan.effectiveApplicantName)
                    val formattedDate = formatLongDate(loan.createdAtLong)
                    MetadataRow(label = "Application Date:", value = formattedDate)
                    val displaySource = if (loan.effectiveSource == "treasurer") "Treasurer (Manual Ledger)" else "Member (Self)"
                    MetadataRow(label = "Loan Source:", value = displaySource)
                }

                // If Status is DENIED: Red Italic Callout
                if (isDenied) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFF44336).copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "Reason for Rejection",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFB71C1C)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "\"${loan.effectiveDenialReason.ifBlank { "Insufficient family treasury balances to satisfy demand at this time." }}\"",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = Color(0xFFB71C1C)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF5E6064)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF1A1C1E)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplyLoanDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(
                    1.dp,
                    Color(0xFFD1E4FF),
                    RoundedCornerShape(16.dp)
                )
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
                        text = "New Loan Request",
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
                    label = { Text("Loan Principal Amount") },
                    placeholder = { Text("Enter KES amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = inputError != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loan_amount_input"),
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

                // Dynamic interest rate visualizer
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
                            Text("Dynamic Interest (10%)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text("Ksh ${String.format("%,.0f", amountVal * 0.10)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Calculated Repayment Total", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                            Text("Ksh ${String.format("%,.0f", amountVal * 1.10)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color(0xFF0061A4))
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
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_cancel_button")
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }

                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull()
                            if (amount == null || amount <= 0) {
                                inputError = "Please enter a valid amount greater than zero."
                            } else {
                                onSubmit(amount)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_apply_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Apply", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualLoanDialog(
    isSubmitting: Boolean,
    members: List<User>,
    onDismiss: () -> Unit,
    onSubmit: (User, Double) -> Unit
) {
    var selectedMember by remember { mutableStateOf<User?>(null) }
    var amountText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(
                    1.dp,
                    Color(0xFFD1E4FF),
                    RoundedCornerShape(16.dp)
                )
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD1E4FF))
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
                            Text("Dynamic Interest (10%)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text("Ksh ${String.format("%,.0f", amountVal * 0.10)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Calculated Repayment Total", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                            Text("Ksh ${String.format("%,.0f", amountVal * 1.10)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color(0xFF0061A4))
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
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }

                    Button(
                        onClick = {
                            val member = selectedMember
                            val amount = amountText.toDoubleOrNull()
                            if (member == null) {
                                inputError = "Please select a target member."
                            } else if (amount == null || amount <= 0) {
                                inputError = "Please enter a valid amount."
                            } else {
                                onSubmit(member, amount)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("manual_loan_submit_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Disburse", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DenyLoanDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var reasonText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBFF)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(
                    1.dp,
                    Color(0xFFD1E4FF),
                    RoundedCornerShape(16.dp)
                )
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
                        text = "Deny Loan Request",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1A1C1E)
                    )
                    IconButton(onClick = onDismiss, enabled = !isSubmitting, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close dialog")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = reasonText,
                    onValueChange = {
                        reasonText = it
                        inputError = null
                    },
                    label = { Text("Reason for Denial") },
                    placeholder = { Text("Explain why this loan is denied") },
                    singleLine = false,
                    maxLines = 4,
                    isError = inputError != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("denial_reason_input"),
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
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cancel_denial_button")
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }

                    Button(
                        onClick = {
                            if (reasonText.trim().isEmpty()) {
                                inputError = "Denial reason cannot be empty."
                            } else {
                                onSubmit(reasonText)
                            }
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB71C1C),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("submit_denial_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Deny Loan", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepaymentDialog(
    loan: Loan,
    currentUser: User,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Double, String, String) -> Unit
) {
    var amountText by remember { mutableStateOf(String.format(Locale.US, "%.0f", loan.remainingBalance)) }
    var paymentMethod by remember { mutableStateOf("M-PESA") }
    var receiptNumber by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var isMpesaProcessing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!isSubmitting && !isMpesaProcessing) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBFF)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(
                    1.dp,
                    Color(0xFFD1E4FF),
                    RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .animateContentSize()
            ) {
                if (isMpesaProcessing) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp), color = Color(0xFF0061A4))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "STK PUSH TRIGGERED",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0061A4)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Awaiting payment confirmation... Please check your device to enter M-Pesa PIN.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF5E6064),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pay Installment",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1A1C1E)
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close dialog")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "OUTSTANDING BALANCE: ${CurrencyFormatter.formatToCurrency(loan.remainingBalance)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0061A4)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            inputError = null
                        },
                        label = { Text("Repayment Amount") },
                        placeholder = { Text("Enter KES amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = inputError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("repayment_amount_input"),
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Method Selector
                    Text(
                        text = "Payment Method",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF5E6064)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val isTreasurer = currentUser.role == "Treasurer" || currentUser.role == "Admin"

                        OutlinedButton(
                            onClick = { paymentMethod = "M-PESA" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (paymentMethod == "M-PESA") Color(0xFFD1E4FF) else Color.Transparent,
                                contentColor = if (paymentMethod == "M-PESA") Color(0xFF0061A4) else Color(0xFF1A1C1E)
                            ),
                            border = if (paymentMethod == "M-PESA") null else ButtonDefaults.outlinedButtonBorder
                        ) {
                            Text("M-PESA")
                        }

                        if (isTreasurer) {
                            OutlinedButton(
                                onClick = { paymentMethod = "CASH" },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (paymentMethod == "CASH") Color(0xFFD1E4FF) else Color.Transparent,
                                    contentColor = if (paymentMethod == "CASH") Color(0xFF0061A4) else Color(0xFF1A1C1E)
                                ),
                                border = if (paymentMethod == "CASH") null else ButtonDefaults.outlinedButtonBorder
                            ) {
                                Text("CASH")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = receiptNumber,
                        onValueChange = { receiptNumber = it },
                        label = { Text(if (paymentMethod == "M-PESA") "M-Pesa Receipt / Transaction ID" else "Reference / Receipt No.") },
                        placeholder = { Text("e.g. RK45XT981Z") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("repayment_receipt_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }

                        Button(
                            onClick = {
                                val amount = amountText.toDoubleOrNull()
                                if (amount == null || amount <= 0) {
                                    inputError = "Please enter a valid amount."
                                } else if (amount > loan.remainingBalance) {
                                    inputError = "Amount exceeds outstanding balance."
                                } else {
                                    if (paymentMethod == "M-PESA" && currentUser.role != "Treasurer" && currentUser.role != "Admin") {
                                        // Trigger STK Push simulator
                                        isMpesaProcessing = true
                                        coroutineScope.launch {
                                            delay(2000) // Simulated STK Push delay
                                            isMpesaProcessing = false
                                            onSubmit(amount, paymentMethod, receiptNumber.ifBlank { "STK_" + System.currentTimeMillis().toString().takeLast(6) })
                                        }
                                    } else {
                                        onSubmit(amount, paymentMethod, receiptNumber)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("submit_repayment_button")
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Pay Now", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatLongDate(timestamp: Long): String {
    if (timestamp <= 0L) return "N/A"
    return try {
        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "N/A"
    }
}
