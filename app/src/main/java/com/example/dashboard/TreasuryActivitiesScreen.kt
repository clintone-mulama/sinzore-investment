package com.example.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.UserActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreasuryActivitiesScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activities by viewModel.treasuryActivities.collectAsState()

    // Filter to only display treasury-specific activities
    val treasuryTypes = listOf(
        "Manual Loan Record Added",
        "Manual Contribution Record Added",
        "Loan Approval",
        "Loan Denial",
        "Manual Balance Update"
    )
    val filteredActivities = activities.filter { it.activityType in treasuryTypes }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Treasury Activities",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("treasury_activities_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back",
                            tint = Color(0xFF1A1C1E)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFDFBFF) // Crisp White background for Clean Minimalism
                )
            )
        },
        modifier = modifier.testTag("treasury_activities_screen_root")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFDFBFF)) // Crisp White background
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            if (filteredActivities.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0061A4).copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFF0061A4),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Treasury Activities",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1A1C1E)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "When you approve or deny loans, record manual contributions or loans, or update balances, logs will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1A1C1E).copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(filteredActivities) { activity ->
                        TreasuryActivityItemCard(activity = activity)
                    }
                }
            }
        }
    }
}

@Composable
fun TreasuryActivityItemCard(activity: UserActivity) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFDFBFF) // Crisp White card content
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFD1E4FF).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .testTag("treasury_activity_card_${activity.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // High-Contrast Type Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFD1E4FF))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = activity.activityType.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color(0xFF0061A4)
                    )
                }

                // Date done
                Text(
                    text = activity.dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1A1C1E).copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details description
            Text(
                text = activity.details,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = Color(0xFF1A1C1E)
            )

            if (activity.amount > 0.0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Amount: Ksh ${String.format("%,.2f", activity.amount)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0061A4)
                )
            }
        }
    }
}
