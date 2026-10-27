package org.skinsewa

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.skinsewa.data.auth.AuthApi
import org.skinsewa.data.auth.Invitation
import org.skinsewa.data.auth.InvitationRequest
import java.io.IOException

@Composable
fun InvitationsScreen(api: AuthApi, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val invitations = remember { mutableStateListOf<Invitation>() }
    var email by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("MEDICAL_OFFICER") }
    var facilityName by rememberSaveable { mutableStateOf("") }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun loadInvitations() {
        scope.launch {
            loading = true
            error = null
            try {
                val response = api.invitations(perPage = 25)
                if (!response.isSuccessful) throw IOException(response.errorBody()?.string()?.let(::invitationServerMessage) ?: "Couldn't load invitations.")
                invitations.clear()
                invitations.addAll(response.body()?.data.orEmpty())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Couldn't load invitations. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadInvitations() }

    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Invitations", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { loadInvitations() }, enabled = !loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh invitations")
            }
        }
        Text("Invite a medical officer or dermatologist. The registration code and link will be emailed to them.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Send an invitation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (role == "MEDICAL_OFFICER") "Medical officer" else "Dermatologist",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().clickable { roleMenuExpanded = true },
                        label = { Text("Role") },
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { roleMenuExpanded = true }) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose role")
                            }
                        },
                    )
                    DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Medical officer") },
                            onClick = { role = "MEDICAL_OFFICER"; roleMenuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Dermatologist") },
                            onClick = { role = "DERMATOLOGIST"; facilityName = ""; roleMenuExpanded = false },
                        )
                    }
                }
                if (role == "MEDICAL_OFFICER") {
                    OutlinedTextField(
                        value = facilityName,
                        onValueChange = { facilityName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Facility name (optional)") },
                        singleLine = true,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        val cleanEmail = email.trim()
                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                            error = "Enter a valid email address."
                            notice = null
                        } else {
                            scope.launch {
                                sending = true
                                error = null
                                notice = null
                                try {
                                    val response = api.sendInvitation(
                                        InvitationRequest(
                                            email = cleanEmail,
                                            userType = role,
                                            facilityName = facilityName.trim().takeIf { role == "MEDICAL_OFFICER" && it.isNotEmpty() },
                                        ),
                                    )
                                    if (!response.isSuccessful) throw IOException(response.errorBody()?.string()?.let(::invitationServerMessage) ?: "Couldn't send the invitation.")
                                    val body = response.body()
                                    body?.data?.let { sent ->
                                        invitations.removeAll { it.email.equals(sent.email, ignoreCase = true) }
                                        invitations.add(0, sent)
                                    } ?: loadInvitations()
                                    email = ""
                                    facilityName = ""
                                    notice = body?.message ?: "Invitation sent. The recipient will receive an email with a registration code."
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error = failure.message ?: "Couldn't send the invitation. Check your connection and try again."
                                } finally {
                                    sending = false
                                }
                            }
                        }
                    },
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.GroupAdd, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (sending) "Sending…" else "Send invitation")
                }
            }
        }

        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent invitations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${invitations.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading && invitations.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
        } else if (!loading && invitations.isEmpty()) {
            Text("No invitations yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(invitations, key = { it.id }) { invitation ->
                    InvitationCard(
                        invitation = invitation,
                        onResend = {
                            scope.launch {
                                sending = true
                                error = null
                                notice = null
                                try {
                                    val response = api.resendInvitation(invitation.id)
                                    if (!response.isSuccessful) throw IOException(response.errorBody()?.string()?.let(::invitationServerMessage) ?: "Couldn't resend the invitation.")
                                    notice = response.body()?.message ?: "A new invitation email has been sent."
                                    loadInvitations()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error = failure.message ?: "Couldn't resend the invitation."
                                } finally {
                                    sending = false
                                }
                            }
                        },
                        resendEnabled = !sending && invitation.status != "accepted",
                    )
                }
            }
        }
    }
}

@Composable
private fun InvitationCard(invitation: Invitation, onResend: () -> Unit, resendEnabled: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(invitation.email, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(invitation.status.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(invitation.userType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(invitation.expiresAt?.let { "Expires $it" } ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (invitation.status != "accepted") TextButton(onClick = onResend, enabled = resendEnabled) { Text("Resend") }
            }
        }
    }
}

private fun invitationServerMessage(body: String): String = runCatching {
    val payload = JSONObject(body)
    val errors = payload.optJSONObject("errors")
    if (errors != null) {
        val fields = errors.keys()
        if (fields.hasNext()) errors.optJSONArray(fields.next())?.optString(0)?.takeIf(String::isNotBlank)?.let { return@runCatching it }
    }
    payload.optString("message").takeIf(String::isNotBlank)
}.getOrNull() ?: "Please check the details and try again."
