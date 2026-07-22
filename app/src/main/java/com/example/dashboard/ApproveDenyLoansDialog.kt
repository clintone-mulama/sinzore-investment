package com.example.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.models.Loan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApproveDenyLoansDialog(
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit
) {
    val pendingLoans by viewModel.pendingLoans.collectAsState()
    
    // Sub-dialogs state
    var loanToDeny by remember { mutableStateOf<Loan?>(null) }
    var denialReason by remember { mutableStateOf("") }
    var denialError by remember { mutableStateOf<String?>(null) }
    var isDenyingSubmitting by remember { mutableStateOf(false) }

    var loanToApprove by remember { mutableStateOf<Loan?>(null) }
    var isApprovingSubmitting by remember { mutableStateOf(false) }
    var approveError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .testTag("approve_deny_loans_dialog_container")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0061A4).copy(alpha = 0.08f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.FactCheck,
                                contentDescription = null,
                                tint = Color(0xFF0061A4),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Pending Loan Approvals",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (pendingLoans.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "All loan requests reviewed!",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "There are no pending loan applications to approve or deny at the moment.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Done")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pendingLoans) { loan ->
                            PendingLoanItemRow(
                                loan = loan,
                                onApprove = { 
                                    loanToApprove = loan 
                                    approveError = null
                                },
                                onDeny = { 
                                    loanToDeny = loan
                                    denialReason = ""
                                    denialError = null
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Close", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }

    // Denial Reason Dialog
    if (loanToDeny != null) {
        val currentLoanToDeny = loanToDeny!!
        AlertDialog(
            onDismissRequest = { if (!isDenyingSubmitting) loanToDeny = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(24.dp).testTag("deny_reason_dialog"),
            content = {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBFF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A1C1E).copy(alpha = 0.12f)),
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
                                text = "Reject Loan Request",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { loanToDeny = null }, enabled = !isDenyingSubmitting, modifier = Modifier.size(24.dp)) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close dialog", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Are you sure you want to deny the loan request of KES ${String.format("%,.2f", currentLoanToDeny.effectiveAmount)} for ${currentLoanToDeny.effectiveApplicantName}?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = denialReason,
                            onValueChange = {
                                denialReason = it
                                denialError = null
                            },
                            label = { Text("Reason for Rejection") },
                            placeholder = { Text("Provide a clear reason...") },
                            singleLine = false,
                            minLines = 2,
                            isError = denialError != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("denial_reason_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        if (denialError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = denialError ?: "",
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
                                onClick = { loanToDeny = null },
                                enabled = !isDenyingSubmitting,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                            ) {
                                Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }

                            Button(
                                onClick = {
                                    if (denialReason.trim().isEmpty()) {
                                        denialError = "Please enter a reason for rejecting this loan."
                                    } else {
                                        isDenyingSubmitting = true
                                        viewModel.denyPendingLoan(
                                            loanId = currentLoanToDeny.id,
                                            reason = denialReason,
                                            onSuccess = {
                                                isDenyingSubmitting = false
                                                loanToDeny = null
                                            },
                                            onFailure = { error ->
                                                isDenyingSubmitting = false
                                                denialError = error
                                            }
                                        )
                                    }
                                },
                                enabled = !isDenyingSubmitting,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("dialog_submit_deny_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                            ) {
                                if (isDenyingSubmitting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("Confirm Reject", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    // Approve Confirmation Dialog
    if (loanToApprove != null) {
        val currentLoanToApprove = loanToApprove!!
        AlertDialog(
            onDismissRequest = { if (!isApprovingSubmitting) loanToApprove = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(24.dp).testTag("approve_confirmation_dialog"),
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
                                text = "Approve & Disburse Loan",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { loanToApprove = null }, enabled = !isApprovingSubmitting, modifier = Modifier.size(24.dp)) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close dialog", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Are you sure you want to approve the loan of KES ${String.format("%,.2f", currentLoanToApprove.effectiveAmount)} for ${currentLoanToApprove.effectiveApplicantName}?\n\nUpon confirmation, this amount will be immediately disbursed from the Available Treasury Cash and logged to the Financial Ledger.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (approveError != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = approveError ?: "",
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
                                onClick = { loanToApprove = null },
                                enabled = !isApprovingSubmitting,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                            ) {
                                Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }

                            Button(
                                onClick = {
                                    isApprovingSubmitting = true
                                    approveError = null
                                    viewModel.approvePendingLoan(
                                        loanId = currentLoanToApprove.id,
                                        onSuccess = {
                                            isApprovingSubmitting = false
                                            loanToApprove = null
                                        },
                                        onFailure = { error ->
                                            isApprovingSubmitting = false
                                            approveError = error
                                        }
                                    )
                                },
                                enabled = !isApprovingSubmitting,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("dialog_submit_approve_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                            ) {
                                if (isApprovingSubmitting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("Confirm Approve", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun PendingLoanItemRow(
    loan: Loan,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBFF)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFD1E4FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .testTag("pending_loan_item_${loan.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = loan.effectiveApplicantName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1A1C1E)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Requested Amount",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1A1C1E).copy(alpha = 0.5f)
                    )
                }
                
                Text(
                    text = "KES ${String.format("%,.2f", loan.effectiveAmount)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0061A4)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp).testTag("deny_loan_action_${loan.id}"),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB71C1C).copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB71C1C))
                ) {
                    Text("Deny", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }

                Button(
                    onClick = onApprove,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp).testTag("approve_loan_action_${loan.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
                ) {
                    Text("Approve", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
