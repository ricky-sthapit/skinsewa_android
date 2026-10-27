package org.skinsewa

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.skinsewa.data.auth.AdminUser
import org.skinsewa.data.auth.AdminUserUpdateRequest
import org.skinsewa.data.auth.AuthApi
import org.skinsewa.data.auth.User
import java.io.IOException

private data class UserRoleFilter(val value: String, val label: String)

private val userRoleFilters = listOf(
    UserRoleFilter("", "All users"),
    UserRoleFilter("ADMIN", "Administrators"),
    UserRoleFilter("MEDICAL_OFFICER", "Medical officers"),
    UserRoleFilter("DERMATOLOGIST", "Dermatologists"),
)

@Composable
fun UsersScreen(api: AuthApi, currentUser: User, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val users = remember { mutableStateListOf<AdminUser>() }
    var search by rememberSaveable { mutableStateOf("") }
    var appliedSearch by rememberSaveable { mutableStateOf("") }
    var roleFilter by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(1) }
    var lastPage by remember { mutableStateOf(1) }
    var total by remember { mutableStateOf(0) }
    var editingUser by remember { mutableStateOf<AdminUser?>(null) }
    var deactivatingUser by remember { mutableStateOf<AdminUser?>(null) }

    fun loadUsers(requestedPage: Int = 1, append: Boolean = false) {
        if (loading && append) return
        loading = true
        scope.launch {
            error = null
            try {
                val response = api.adminUsers(
                    query = appliedSearch.takeIf(String::isNotBlank),
                    userType = roleFilter.takeIf(String::isNotBlank),
                    page = requestedPage,
                    perPage = 15,
                )
                if (!response.isSuccessful) throw IOException(response.errorBody()?.string()?.let(::usersServerMessage) ?: "Couldn't load users.")
                val body = response.body() ?: throw IOException("The users response was empty.")
                if (!append) users.clear()
                users.addAll(body.data)
                page = body.meta?.currentPage ?: requestedPage
                lastPage = body.meta?.lastPage ?: requestedPage
                total = body.meta?.total ?: users.size
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Couldn't load users. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(appliedSearch, roleFilter) { loadUsers() }

    if (editingUser != null) {
        UserEditorScreen(
            api = api,
            user = editingUser!!,
            onBack = { editingUser = null },
            onSaved = { updated ->
                val index = users.indexOfFirst { it.id == updated.id }
                if (index >= 0) users[index] = updated
                notice = "User updated."
                editingUser = null
                loadUsers()
            },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Users", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { loadUsers() }, enabled = !loading) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh users") }
        }
        Text("Manage active accounts and their access.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search name or email") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (search.isNotBlank()) IconButton(onClick = { search = ""; appliedSearch = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                    IconButton(onClick = { appliedSearch = search.trim() }, enabled = !loading) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search users", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { appliedSearch = search.trim() }),
        )
        UserRoleDropdown(roleFilter, onSelect = { roleFilter = it })
        Text("$total ${if (total == 1) "user" else "users"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        when {
            loading && users.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            users.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                Text(if (error == null) "No users match your search." else "", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(users, key = { it.id }) { account ->
                    AdminUserCard(
                        user = account,
                        currentUserId = currentUser.id,
                        enabled = !working,
                        onEdit = { editingUser = account; error = null; notice = null },
                        onDeactivate = { deactivatingUser = account },
                    )
                }
                if (page < lastPage) item {
                    OutlinedButton(onClick = { loadUsers(page + 1, append = true) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Load more users")
                    }
                }
            }
        }
    }

    deactivatingUser?.let { account ->
        AlertDialog(
            onDismissRequest = { if (!working) deactivatingUser = null },
            title = { Text("Deactivate ${account.name}?") },
            text = { Text("They will lose access to SkinSewa and will be signed out on their devices.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            working = true
                            error = null
                            notice = null
                            try {
                                val response = api.deactivateAdminUser(account.id)
                                if (!response.isSuccessful) throw IOException(response.errorBody()?.string()?.let(::usersServerMessage) ?: "Couldn't deactivate this account.")
                                users.removeAll { it.id == account.id }
                                total = (total - 1).coerceAtLeast(0)
                                notice = response.body()?.message ?: "User deactivated."
                                deactivatingUser = null
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.message ?: "Couldn't deactivate this account."
                                deactivatingUser = null
                            } finally {
                                working = false
                            }
                        }
                    },
                    enabled = !working,
                ) { Text(if (working) "Deactivating…" else "Deactivate", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deactivatingUser = null }, enabled = !working) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AdminUserCard(user: AdminUser, currentUserId: Long, enabled: Boolean, onEdit: () -> Unit, onDeactivate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(user.roleLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.Outlined.People, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(user.email, style = MaterialTheme.typography.bodyMedium)
            user.phone?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            user.facilityName?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit, enabled = enabled) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(5.dp))
                    Text("Edit")
                }
                TextButton(onClick = onDeactivate, enabled = enabled && user.id != currentUserId) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.size(5.dp))
                    Text("Deactivate", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun UserRoleDropdown(value: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = userRoleFilters.firstOrNull { it.value == value }?.label ?: "All users"
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            label = { Text("Account type") },
            readOnly = true,
            singleLine = true,
            trailingIcon = { IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Filter users") } },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            userRoleFilters.forEach { filter ->
                DropdownMenuItem(text = { Text(filter.label) }, onClick = { onSelect(filter.value); expanded = false })
            }
        }
    }
}

@Composable
private fun UserEditorScreen(api: AuthApi, user: AdminUser, onBack: () -> Unit, onSaved: (AdminUser) -> Unit) {
    val scope = rememberCoroutineScope()
    var firstName by rememberSaveable(user.id) { mutableStateOf(user.firstName) }
    var lastName by rememberSaveable(user.id) { mutableStateOf(user.lastName) }
    var email by rememberSaveable(user.id) { mutableStateOf(user.email) }
    var userType by rememberSaveable(user.id) { mutableStateOf(user.userType) }
    var adminAccess by rememberSaveable(user.id) { mutableStateOf(user.isAdmin || user.role == "ADMIN") }
    var phone by rememberSaveable(user.id) { mutableStateOf(user.phone.orEmpty()) }
    var facilityName by rememberSaveable(user.id) { mutableStateOf(user.facilityName.orEmpty()) }
    var specialty by rememberSaveable(user.id) { mutableStateOf(user.specialty.orEmpty()) }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !saving) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to users") }
            Text("Edit user", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Text("Update account details and access for ${user.name}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(firstName, { firstName = it; error = null }, Modifier.fillMaxWidth(), label = { Text("First name") }, singleLine = true)
        OutlinedTextField(lastName, { lastName = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Last name") }, singleLine = true)
        OutlinedTextField(email, { email = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Box(Modifier.fillMaxWidth()) {
            val roleLabel = when (userType) {
                "MEDICAL_OFFICER" -> "Medical officer"
                "DERMATOLOGIST" -> "Dermatologist"
                else -> "Administrator only"
            }
            OutlinedTextField(
                value = roleLabel,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth().clickable { roleMenuExpanded = true },
                label = { Text("Clinical role") },
                readOnly = true,
                singleLine = true,
                trailingIcon = { IconButton(onClick = { roleMenuExpanded = true }) { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose clinical role") } },
            )
            DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                DropdownMenuItem(text = { Text("Medical officer") }, onClick = { userType = "MEDICAL_OFFICER"; roleMenuExpanded = false })
                DropdownMenuItem(text = { Text("Dermatologist") }, onClick = { userType = "DERMATOLOGIST"; roleMenuExpanded = false })
                DropdownMenuItem(text = { Text("Administrator only") }, onClick = { userType = "ADMIN"; adminAccess = true; roleMenuExpanded = false })
            }
        }
        if (userType == "MEDICAL_OFFICER") {
            OutlinedTextField(facilityName, { facilityName = it }, Modifier.fillMaxWidth(), label = { Text("Facility name (optional)") }, singleLine = true)
        }
        if (userType == "DERMATOLOGIST") {
            OutlinedTextField(specialty, { specialty = it }, Modifier.fillMaxWidth(), label = { Text("Specialty (optional)") }, singleLine = true)
        }
        if (userType != "ADMIN") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Administrator access", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("This user can manage accounts and app content.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = adminAccess, onCheckedChange = { adminAccess = it }, enabled = !saving)
            }
        } else {
            Text("Administrator access is required for an administrator-only account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(
            onClick = {
                val cleanEmail = email.trim()
                if (firstName.isBlank() || lastName.isBlank()) error = "Enter the user's first and last name."
                else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) error = "Enter a valid email address."
                else {
                    scope.launch {
                        saving = true
                        error = null
                        try {
                            val response = api.updateAdminUser(
                                user.id,
                                AdminUserUpdateRequest(
                                    firstName = firstName.trim(),
                                    lastName = lastName.trim(),
                                    email = cleanEmail,
                                    userType = userType,
                                    isAdmin = userType == "ADMIN" || adminAccess,
                                    phone = phone.trim().takeIf(String::isNotBlank),
                                    facilityName = facilityName.trim().takeIf { userType == "MEDICAL_OFFICER" && it.isNotBlank() },
                                    specialty = specialty.trim().takeIf { userType == "DERMATOLOGIST" && it.isNotBlank() },
                                ),
                            )
                            if (!response.isSuccessful) throw IOException(response.errorBody()?.string()?.let(::usersServerMessage) ?: "Couldn't save this user.")
                            val updated = response.body()?.data ?: throw IOException("The updated user response was empty.")
                            onSaved(updated)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message ?: "Couldn't save this user. Check your connection and try again."
                        } finally {
                            saving = false
                        }
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Edit, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(if (saving) "Saving…" else "Save changes")
        }
    }
}

private fun usersServerMessage(body: String): String = runCatching {
    val payload = JSONObject(body)
    val errors = payload.optJSONObject("errors")
    if (errors != null) {
        val fields = errors.keys()
        if (fields.hasNext()) errors.optJSONArray(fields.next())?.optString(0)?.takeIf(String::isNotBlank)?.let { return@runCatching it }
    }
    payload.optString("message").takeIf(String::isNotBlank)
}.getOrNull() ?: "Please check the details and try again."
