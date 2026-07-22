package com.example.contributions

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.models.Contribution
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContributionsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(successMessage) {
        successMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearMessages()
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearMessages()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Savings & Capital",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    ) 
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack, 
                        modifier = Modifier.testTag("contributions_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFDFBFF) // Crisp White background for Clean Minimalism
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.testTag("contributions_screen_root")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFDFBFF)) // Crisp White background for Clean Minimalism
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is ContributionsUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF0061A4),
                            modifier = Modifier.testTag("loading_indicator")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading financial logs...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF1A1C1E).copy(alpha = 0.6f)
                        )
                    }
                }
                is ContributionsUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Error",
                            tint = Color.Red,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF1A1C1E),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.retry() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                            modifier = Modifier.testTag("retry_button")
                        ) {
                            Text("Retry Connection")
                        }
                    }
                }
                is ContributionsUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ContributionsLogList(
                            contributions = state.contributions,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Public contributions log feed viewable by all members.
 */
@Composable
fun ContributionsLogList(
    contributions: List<Contribution>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Component A: Header
        Column {
            Text(
                text = "FAMILY CONTRIBUTIONS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                color = Color(0xFF80DEEA) // Light Cyan/Teal
            )
        }

        // Component B: Main Scrollable List Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E2633) // Deep slate/dark gray rounded card
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = Color(0xFF80DEEA).copy(alpha = 0.3f) // Subtle cyan/teal border outline with transparency
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (contributions.isEmpty()) {
                // EMPTY STATE LOGIC
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.9f,
                        targetValue = 1.1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )
                    
                    Icon(
                        imageVector = Icons.Default.Favorite, // Heart icon
                        contentDescription = null,
                        tint = Color(0xFF80DEEA).copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "NO CONTRIBUTIONS YET",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = Color(0xFF90A4AE) // muted gray text
                    )
                }
            } else {
                // POPULATED STATE LIST ITEMS
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("contributions_lazy_column")
                ) {
                    items(contributions) { item ->
                        ContributionRowItem(item)
                    }
                }
            }
        }

        // Component C: Aggregate Total Footer Card
        val totalAmount = contributions.sumOf { it.amount }
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF161B22) // Darker solid background
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = Color(0xFF80DEEA).copy(alpha = 0.2f) // clean border matching list container
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("aggregate_total_footer")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "TOTAL CONTRIBUTION WEIGHTS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = Color(0xFFB0BEC5) // muted light gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ksh ${String.format("%,.0f", totalAmount)}",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color(0xFF10B981) // emerald green
                )
            }
        }

        // Component D: Muted Inspirational Banner Card
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color(0xFF161B22), shape = RoundedCornerShape(12.dp))
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    drawRoundRect(
                        color = Color(0xFF80DEEA).copy(alpha = 0.3f), // Light Cyan/Teal border with transparency
                        style = Stroke(width = strokeWidth, pathEffect = dashPathEffect),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                    )
                }
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "\"Unity is strength.\nGrowth is shared.\"",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                ),
                color = Color(0xFF80DEEA) // Light Cyan/Teal
            )
        }
    }
}

@Composable
fun ContributionRowItem(item: Contribution) {
    val displayName = if (item.userName.isNotBlank()) item.userName else item.contributorFullName
    val initial = if (displayName.isNotBlank()) displayName.first().uppercase() else "S"
    
    val timeMs = if (item.date > 0) item.date else (item.dateContributed?.time ?: 0L)
    val formattedDate = if (timeMs > 0) {
        SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date(timeMs))
    } else {
        "Pending"
    }

    val isTreasurerSource = item.source.equals("treasurer", ignoreCase = true) || item.isManual
    val statusText = if (isTreasurerSource) "Contribution Verified" else "Contribution"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("contribution_item_${item.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Member Avatar & Name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF10B981).copy(alpha = 0.2f), shape = CircleShape)
                    .border(1.dp, Color(0xFF10B981), shape = CircleShape)
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF10B981)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // Content
            Column {
                Text(
                    text = displayName.uppercase(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFF5F5F5) // off-white
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0BEC5) // muted light gray
                )
            }
        }

        // Right side: Amount & Status Badge
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "+Ksh ${String.format("%,.0f", item.amount)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF10B981) // bright emerald green
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFB0BEC5) // muted label
            )
        }
    }
}
