package com.example.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * DEVELOPMENT ONLY - Dialog for updating the authenticated user's real Firestore document role.
 * Clean Minimalism style: soft blues (#D1E4FF, #0061A4), crisp whites, charcoal texts (#1A1C1E).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevelopmentRoleSwitcherDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firestore = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid

    var currentDbRole by remember { mutableStateOf("Loading...") }
    var selectedRole by remember { mutableStateOf<String?>(null) }
    var isUpdating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Synchronize current role from Firestore in real-time within the dialog
    LaunchedEffect(uid) {
        if (uid != null) {
            firestore.collection("users").document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && snapshot.exists()) {
                        val role = snapshot.getString("role") ?: "Member"
                        currentDbRole = role
                        if (selectedRole == null) {
                            selectedRole = role
                        }
                    }
                }
        } else {
            currentDbRole = "No Session"
        }
    }

    val roles = listOf("Member", "Treasurer", "Admin")

    AlertDialog(
        onDismissRequest = { if (!isUpdating) onDismiss() },
        confirmButton = {
            Button(
                onClick = {
                    val targetRole = selectedRole
                    if (uid != null && targetRole != null && !isUpdating) {
                        isUpdating = true
                        errorMessage = null
                        firestore.collection("users").document(uid)
                            .update("role", targetRole)
                            .addOnSuccessListener {
                                isUpdating = false
                                onDismiss()
                            }
                            .addOnFailureListener { e ->
                                isUpdating = false
                                errorMessage = e.localizedMessage ?: "Failed to update Firestore document"
                            }
                    }
                },
                enabled = !isUpdating && uid != null && selectedRole != null && selectedRole != currentDbRole,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0061A4),
                    disabledContainerColor = Color(0xFF0061A4).copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("apply_role_button")
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 8.dp)
                    )
                }
                Text("Apply Role Update", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isUpdating,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1A1C1E)),
                modifier = Modifier.testTag("cancel_role_button")
            ) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                text = "Set My Development Role",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color(0xFF1A1C1E)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "This tool directly updates your authorized role in the Firestore users collection. All database access rules and UI permissions will immediately adapt to the updated role.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1A1C1E).copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F4F9), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Current Firestore Role:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1A1C1E).copy(alpha = 0.6f)
                    )
                    Text(
                        text = currentDbRole,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0061A4)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                roles.forEach { roleName ->
                    val isSelected = selectedRole == roleName
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSelected) Color(0xFFD1E4FF) else Color(0xFFF1F4F9),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable(enabled = !isUpdating) {
                                selectedRole = roleName
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("role_option_$roleName"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = roleName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = Color(0xFF1A1C1E)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color(0xFF0061A4)
                            )
                        }
                    }
                }

                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("role_switcher_error")
                    )
                }
            }
        },
        containerColor = Color(0xFFFDFBFF), // Crisp white
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.testTag("dev_role_switcher_dialog")
    )
}
