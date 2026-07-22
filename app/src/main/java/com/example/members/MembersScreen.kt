package com.example.members

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dashboard.DashboardUiState
import com.example.dashboard.DashboardViewModel
import com.example.models.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val members by viewModel.members.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Determine current user and Admin status
    val currentUser = (uiState as? DashboardUiState.Success)?.user
    val isAdmin = currentUser?.role == "Admin"

    // Dialog state
    var memberToDelete by remember { mutableStateOf<User?>(null) }
    var deleteStage by remember { mutableIntStateOf(1) } // 1 for first prompt, 2 for checkbox double-confirmation
    var acceptTermsToDelete by remember { mutableStateOf(false) }

    var memberToEditRole by remember { mutableStateOf<User?>(null) }
    var selectedRoleToAssign by remember { mutableStateOf("Member") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Members Directory", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("members_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.testTag("members_screen_root")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Real-time Active Members Banner (Clean Minimalism Theme)
            val activeCount = members.size
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("members_count_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Group size",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Active Group Members",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "$activeCount Registered",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ALL REGISTERED USERS",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (members.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(members) { member ->
                        val dateFormatted = remember(member.createdAt) {
                            try {
                                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                sdf.format(Date(member.createdAt))
                            } catch (e: Exception) {
                                "N/A"
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("member_card_${member.uid}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // User Avatar Indicator based on Role
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (member.role) {
                                                    "Admin" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                                    "Treasurer" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                    else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (member.role) {
                                                "Admin" -> Icons.Default.Shield
                                                "Treasurer" -> Icons.Default.ManageAccounts
                                                else -> Icons.Default.Person
                                            },
                                            contentDescription = member.role,
                                            tint = when (member.role) {
                                                "Admin" -> MaterialTheme.colorScheme.error
                                                "Treasurer" -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.secondary
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = member.fullName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Registered on: $dateFormatted",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Display Role Tag
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when (member.role) {
                                                    "Admin" -> MaterialTheme.colorScheme.errorContainer
                                                    "Treasurer" -> MaterialTheme.colorScheme.primaryContainer
                                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = member.role.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when (member.role) {
                                                "Admin" -> MaterialTheme.colorScheme.onErrorContainer
                                                "Treasurer" -> MaterialTheme.colorScheme.onPrimaryContainer
                                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                                            }
                                        )
                                    }
                                }

                                if (isAdmin && member.uid != currentUser?.uid) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Change Role Button
                                        OutlinedButton(
                                            onClick = {
                                                memberToEditRole = member
                                                selectedRoleToAssign = member.role
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .height(36.dp)
                                                .testTag("btn_change_role_${member.uid}")
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Change Role", style = MaterialTheme.typography.labelMedium)
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Remove Member Button
                                        Button(
                                            onClick = {
                                                memberToDelete = member
                                                deleteStage = 1
                                                acceptTermsToDelete = false
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .height(36.dp)
                                                .testTag("btn_remove_member_${member.uid}")
                                        ) {
                                            Icon(
                                                Icons.Default.PersonRemove,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Remove", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS FOR TWO-STEP DELETE CONFIRMATION ---
    memberToDelete?.let { member ->
        if (deleteStage == 1) {
            AlertDialog(
                onDismissRequest = { memberToDelete = null },
                title = { Text("Confirm User Removal") },
                text = {
                    Text(
                        "Are you sure you want to remove ${member.fullName} from the group? This will delete all of their profile details and security records from the database."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { deleteStage = 2 },
                        modifier = Modifier.testTag("btn_confirm_delete_stage1")
                    ) {
                        Text("Proceed", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { memberToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        } else if (deleteStage == 2) {
            AlertDialog(
                onDismissRequest = { memberToDelete = null },
                title = { Text("Final Confirmation Required") },
                text = {
                    Column {
                        Text(
                            text = "This action is completely permanent and cannot be undone. Once deleted, the user's phone, ID number, and email credentials will be completely wiped from the users records so they can sign up again like a brand new user.\n\nPlease check the confirmation box below to perform the deletion.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = acceptTermsToDelete,
                                onCheckedChange = { acceptTermsToDelete = it },
                                modifier = Modifier.testTag("chk_delete_terms")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "I understand and wish to permanently wipe all database credentials for ${member.fullName}.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val targetMember = member
                            memberToDelete = null
                            viewModel.removeMember(
                                member = targetMember,
                                onSuccess = {
                                    Toast.makeText(context, "${targetMember.fullName} was wiped out completely from the database.", Toast.LENGTH_LONG).show()
                                },
                                onFailure = { errorMsg ->
                                    Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        enabled = acceptTermsToDelete,
                        modifier = Modifier.testTag("btn_confirm_delete_stage2")
                    ) {
                        Text("Confirm Permanent Deletion", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { memberToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // --- DIALOG FOR CHANGING ROLE ---
    memberToEditRole?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToEditRole = null },
            title = { Text("Update Member Role") },
            text = {
                Column {
                    Text(
                        text = "Modify role configuration for ${member.fullName}. Note that there can be at most one active Treasurer. To set this user as Treasurer, please ensure that the current Treasurer has been set to Member first.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedRoleToAssign == "Member",
                            onClick = { selectedRoleToAssign = "Member" },
                            modifier = Modifier.testTag("rb_role_member")
                        )
                        Text("Member", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(
                            selected = selectedRoleToAssign == "Treasurer",
                            onClick = { selectedRoleToAssign = "Treasurer" },
                            modifier = Modifier.testTag("rb_role_treasurer")
                        )
                        Text("Treasurer", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetMember = member
                        val targetRole = selectedRoleToAssign
                        memberToEditRole = null
                        viewModel.changeMemberRole(
                            member = targetMember,
                            newRole = targetRole,
                            onSuccess = {
                                Toast.makeText(context, "${targetMember.fullName}'s role has been successfully changed to $targetRole.", Toast.LENGTH_LONG).show()
                            },
                            onFailure = { errorMsg ->
                                Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    modifier = Modifier.testTag("btn_save_role")
                ) {
                    Text("Apply Role Change")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToEditRole = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
