package org.swasthaconnect

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.swasthaconnect.data.auth.CaseDetail
import org.swasthaconnect.data.auth.CaseMessage
import org.swasthaconnect.data.auth.CasePhoto
import org.swasthaconnect.data.auth.CaseSummary
import org.swasthaconnect.data.auth.CaseTreatment
import org.swasthaconnect.data.auth.CaseUser
import org.swasthaconnect.data.auth.PlaceOption
import org.swasthaconnect.data.auth.TreatmentRequest
import org.swasthaconnect.data.auth.User
import org.swasthaconnect.data.auth.AuthApi
import java.io.IOException

private enum class CaseView { LIST, CREATE, DETAIL }

@Composable
fun CasesScreen(api: AuthApi, user: User, onBack: () -> Unit, initialCaseId: Long? = null) {
    var view by rememberSaveable(initialCaseId) { mutableStateOf(if (initialCaseId == null) CaseView.LIST else CaseView.DETAIL) }
    var selectedCaseId by rememberSaveable(initialCaseId) { mutableStateOf(initialCaseId) }
    var refreshKey by rememberSaveable { mutableStateOf(0) }

    when (view) {
        CaseView.LIST -> CaseListScreen(
            api = api,
            user = user,
            onBack = onBack,
            onCreate = { view = CaseView.CREATE },
            onSelect = { id -> selectedCaseId = id; view = CaseView.DETAIL },
        )
        CaseView.CREATE -> CaseCreateScreen(
            api = api,
            user = user,
            onBack = { view = CaseView.LIST },
            onCreated = { id -> selectedCaseId = id; refreshKey++; view = CaseView.DETAIL },
        )
        CaseView.DETAIL -> selectedCaseId?.let { id ->
            CaseDetailScreen(
                api = api,
                caseId = id,
                refreshKey = refreshKey,
                onBack = { if (initialCaseId != null) onBack() else view = CaseView.LIST },
                onRefresh = { refreshKey++ },
            )
        } ?: run { view = CaseView.LIST }
    }
}

@Composable
private fun CaseListScreen(
    api: AuthApi,
    user: User,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val cases = remember { mutableStateListOf<CaseSummary>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var appliedSearch by rememberSaveable { mutableStateOf("") }
    var selectedStatus by rememberSaveable { mutableStateOf("") }
    var statuses by remember { mutableStateOf(listOf("awaiting_review", "completed")) }
    var currentPage by remember { mutableStateOf(1) }
    var lastPage by remember { mutableStateOf(1) }
    var total by remember { mutableStateOf(0) }

    fun load(page: Int = 1, append: Boolean = false) {
        scope.launch {
            loading = true
            error = null
            try {
                val response = api.cases(query = appliedSearch.takeIf(String::isNotBlank), status = selectedStatus.takeIf(String::isNotBlank), page = page, perPage = 15)
                if (!response.isSuccessful) throw IOException(response.errorBody()?.string()?.let(::caseServerMessage) ?: "Couldn't load cases.")
                val payload = response.body() ?: throw IOException("The case list was empty.")
                if (!append) cases.clear()
                cases.addAll(payload.data)
                currentPage = payload.meta?.currentPage ?: page
                lastPage = payload.meta?.lastPage ?: page
                total = payload.meta?.total ?: cases.size
                statuses = (listOf("awaiting_review", "completed") + payload.meta?.availableStatuses.orEmpty()).distinct()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                error = exception.message ?: "Couldn't load cases. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(selectedStatus, appliedSearch) { load() }

    CasePageFrame(title = "Cases", onBack = onBack, actions = {
        IconButton(onClick = { load() }, enabled = !loading) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh cases") }
        if (user.clinicalRole == "MEDICAL_OFFICER") {
            IconButton(onClick = onCreate) { Icon(Icons.Outlined.Add, contentDescription = "Add patient case") }
        }
    }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$total ${if (total == 1) "case" else "cases"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(caseRoleDescription(user), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(search, { search = it }, Modifier.weight(1f), label = { Text("Search patient or case") }, singleLine = true)
            TextButton(onClick = { appliedSearch = search.trim() }) { Text("Search") }
        }
        CaseStringDropdownField(
            label = "Status",
            value = if (selectedStatus.isBlank()) "All statuses" else selectedStatus.caseStatusLabel(),
            options = listOf("" to "All statuses") + statuses.map { it to it.caseStatusLabel() },
            onSelect = { selectedStatus = it },
        )
        error?.let { CaseNotice(it, error = true) }
        if (loading && cases.isEmpty()) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        if (!loading && error == null && cases.isEmpty()) CaseNotice("No cases found for this account.")
        cases.forEach { item -> CaseListCard(item, onClick = { onSelect(item.id) }) }
        if (currentPage < lastPage) OutlinedButton(onClick = { load(currentPage + 1, append = true) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Load more cases")
        }
    }
}

@Composable
private fun CaseListCard(case: CaseSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Case #${case.caseNumber ?: case.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(case.patient?.name ?: "Patient details unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(case.status)
            }
            Text("Patient ID: ${case.patient?.patientCode ?: "—"}  ·  ${case.patient?.age?.let { "$it years" } ?: "Age unknown"}", style = MaterialTheme.typography.bodySmall)
            case.symptoms?.takeIf(String::isNotBlank)?.let { Text(it, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            HorizontalDivider()
            Text("Dermatologist: ${case.assignedDoctor?.name ?: "Unassigned"}  ·  Photos: ${case.photoCount ?: 0}", style = MaterialTheme.typography.labelMedium)
            Text(case.createdAt?.caseDateLabel() ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CaseCreateScreen(api: AuthApi, user: User, onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var patientCode by rememberSaveable { mutableStateOf("") }
    var selectedPatient by remember { mutableStateOf<org.swasthaconnect.data.auth.CasePatient?>(null) }
    var previousCases by remember { mutableStateOf(emptyList<CaseSummary>()) }
    var followUpId by rememberSaveable { mutableStateOf<Long?>(null) }
    var provinces by remember { mutableStateOf(emptyList<PlaceOption>()) }
    var districts by remember { mutableStateOf(emptyList<PlaceOption>()) }
    var municipalities by remember { mutableStateOf(emptyList<PlaceOption>()) }
    var dermatologists by remember { mutableStateOf(emptyList<CaseUser>()) }
    var province by remember { mutableStateOf<PlaceOption?>(null) }
    var district by remember { mutableStateOf<PlaceOption?>(null) }
    var municipality by remember { mutableStateOf<PlaceOption?>(null) }
    var assignedDoctor by remember { mutableStateOf<CaseUser?>(null) }
    var patientName by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var facility by rememberSaveable { mutableStateOf(user.facilityName.orEmpty()) }
    var weight by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var symptoms by rememberSaveable { mutableStateOf("") }
    var finding by rememberSaveable { mutableStateOf("") }
    var history by rememberSaveable { mutableStateOf("") }
    var consent by rememberSaveable { mutableStateOf(false) }
    val photos = remember { mutableStateListOf<Uri>() }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selected ->
        if (selected.isNotEmpty()) {
            if (photos.size + selected.size > 10) message = "You can attach up to 10 photos. Remove a photo before adding more."
            else { photos.addAll(selected); message = null }
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val placeResponse = api.provinces()
            if (!placeResponse.isSuccessful) throw IOException("Couldn't load province choices.")
            provinces = placeResponse.body()?.data.orEmpty()
            val doctorResponse = api.caseDermatologists()
            if (!doctorResponse.isSuccessful) throw IOException(doctorResponse.errorBody()?.string()?.let(::caseServerMessage) ?: "Couldn't load dermatologist choices.")
            dermatologists = doctorResponse.body()?.data.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            error = exception.message ?: "Couldn't load case form choices."
        } finally { loading = false }
    }

    CasePageFrame(title = "New case", onBack = onBack, actions = {}) {
        Text("Patient details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(patientCode, { patientCode = it }, Modifier.fillMaxWidth(), label = { Text("Returning patient ID (optional)") }, singleLine = true)
        OutlinedButton(onClick = {
            scope.launch {
                loading = true; error = null; message = null
                try {
                    val response = api.lookupPatient(patientCode.trim())
                    if (!response.isSuccessful) throw IOException(response.errorBody()?.string()?.let(::caseServerMessage) ?: "Patient not found in your case history.")
                    val found = response.body()?.data ?: throw IOException("Patient record was empty.")
                    selectedPatient = found.patient
                    previousCases = found.previousCases
                    followUpId = null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (exception: Exception) { error = exception.message ?: "Couldn't find this patient." }
                finally { loading = false }
            }
        }, enabled = !loading && patientCode.isNotBlank()) { Text("Find patient") }
        selectedPatient?.let { patient ->
            CaseNotice("Using ${patient.name ?: "patient"} · ${patient.patientCode ?: "No ID"}. New patient fields are skipped.")
            TextButton(onClick = { selectedPatient = null; previousCases = emptyList(); followUpId = null }) { Text("Clear patient selection") }
            if (previousCases.isNotEmpty()) CaseLongDropdownField(
                label = "Link as follow-up (optional)",
                value = previousCases.firstOrNull { it.id == followUpId }?.let { "Case #${it.id} · ${it.createdAt?.caseDateLabel().orEmpty()}" } ?: "New or unrelated concern",
                options = listOf(0L to "New or unrelated concern") + previousCases.map { it.id to "Case #${it.id} · ${it.createdAt?.caseDateLabel().orEmpty()}" },
                onSelect = { followUpId = it.takeIf { id -> id != 0L } },
            )
            if (previousCases.isNotEmpty()) CaseSection("Previous visit details") {
                previousCases.forEach { prior ->
                    Text("Case #${prior.id} · ${prior.createdAt?.caseDateLabel().orEmpty()} · ${prior.status?.caseStatusLabel().orEmpty()}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    prior.symptoms?.takeIf(String::isNotBlank)?.let { DetailLine("Symptoms", it) }
                    prior.finding?.takeIf(String::isNotBlank)?.let { DetailLine("Findings", it) }
                    prior.history?.takeIf(String::isNotBlank)?.let { DetailLine("History", it) }
                    prior.treatments.forEach { plan ->
                        DetailLine("Diagnosis", plan.diagnosis ?: "—")
                        DetailLine("Treatment", plan.treatment ?: "—")
                    }
                    if (prior.id != previousCases.last().id) HorizontalDivider()
                }
            }
        }
        if (selectedPatient == null) {
            CaseTextField("Patient full name", patientName, { patientName = it })
            CaseTextField("Age", age, { age = it }, numeric = true)
            CaseStringDropdownField("Gender", gender.ifBlank { "Choose gender" }, listOf("Male" to "Male", "Female" to "Female")) { gender = it }
            CaseTextField("Health post / facility", facility, { facility = it })
            CaseTextField("Weight in kg (optional)", weight, { weight = it }, numeric = true)
            CaseTextField("Phone (optional)", phone, { phone = it }, numeric = false)
            CaseTextField("Email (optional)", email, { email = it })
            CaseLongDropdownField("Province", province?.placeLabel() ?: "Choose province", provinces.map { it.id to it.placeLabel() }) { id ->
                province = provinces.firstOrNull { it.id == id }; district = null; municipality = null; districts = emptyList(); municipalities = emptyList()
                scope.launch { runCatching { api.placeChildren(id).body()?.data.orEmpty() }.onSuccess { districts = it }.onFailure { error = "Couldn't load districts." } }
            }
            CaseLongDropdownField("District", district?.placeLabel() ?: "Choose district", districts.map { it.id to it.placeLabel() }, enabled = province != null) { id ->
                district = districts.firstOrNull { it.id == id }; municipality = null; municipalities = emptyList()
                scope.launch { runCatching { api.placeChildren(id).body()?.data.orEmpty() }.onSuccess { municipalities = it }.onFailure { error = "Couldn't load local levels." } }
            }
            CaseLongDropdownField("Municipality / local level", municipality?.placeLabel() ?: "Choose local level", municipalities.map { it.id to it.placeLabel() }, enabled = district != null) { id -> municipality = municipalities.firstOrNull { it.id == id } }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Skin concern", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        CaseTextField("Current symptoms", symptoms, { symptoms = it }, multiline = true)
        CaseTextField("Clinical findings", finding, { finding = it }, multiline = true)
        CaseTextField("Past medical history", history, { history = it }, multiline = true)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Case photos (optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("JPG, PNG, or WebP · up to 10 photos · 10 MB each", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = photos.size < 10) {
            Icon(Icons.Outlined.AttachFile, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Add photos (${photos.size}/10)")
        }
        photos.forEachIndexed { index, uri ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(uri.lastPathSegment?.substringAfterLast('/') ?: "Photo ${index + 1}", Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = { photos.removeAt(index) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove photo ${index + 1}") }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Review and consent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        CaseLongDropdownField("Assign dermatologist", assignedDoctor?.name ?: "Choose dermatologist", dermatologists.map { it.id to (it.name ?: "Dermatologist") }) { id -> assignedDoctor = dermatologists.firstOrNull { it.id == id } }
        if (dermatologists.isEmpty() && !loading) CaseNotice("No active dermatologists are available for assignment.", error = true)
        if (provinces.isEmpty() && !loading) CaseNotice("Location choices are unavailable. Ask an administrator to load the places dataset.", error = true)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(checked = consent, onCheckedChange = { consent = it })
            Text("I confirm consent to share the case with the assigned care team.", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { CaseNotice(it, error = true) }
        message?.let { CaseNotice(it, error = true) }
        Button(onClick = {
            scope.launch {
                loading = true; error = null
                try {
                    val fields = linkedMapOf(
                        "symptoms" to symptoms.trim(), "finding" to finding.trim(), "history" to history.trim(),
                        "assigned_to" to (assignedDoctor?.id?.toString() ?: ""), "consent" to if (consent) "1" else "0",
                    )
                    selectedPatient?.let { fields["patient_id"] = it.id.toString() }
                    followUpId?.let { fields["follow_up_of"] = it.toString() }
                    if (selectedPatient == null) {
                        fields.putAll(mapOf(
                            "patient_name" to patientName.trim(), "age" to age.trim(), "gender" to gender,
                            "phc" to facility.trim(), "province_id" to (province?.id?.toString() ?: ""),
                            "district_id" to (district?.id?.toString() ?: ""), "municipality_id" to (municipality?.id?.toString() ?: ""),
                        ))
                        if (phone.isNotBlank()) fields["phone"] = phone.trim()
                        if (email.isNotBlank()) fields["email"] = email.trim()
                        if (weight.isNotBlank()) fields["weight"] = weight.trim()
                    }
                    val formParts = fields.mapValues { (_, value) -> value.toRequestBody("text/plain".toMediaType()) }
                    val photoParts = makePhotoParts(context, photos.toList())
                    val response = api.createCase(formParts, photoParts)
                    if (!response.isSuccessful) throw IOException(response.errorBody()?.string()?.let(::caseServerMessage) ?: "Couldn't submit the case.")
                    val id = response.body()?.data?.id ?: throw IOException("Case was created, but its number was missing.")
                    onCreated(id)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (exception: Exception) { error = exception.message ?: "Couldn't submit the case. Check your details and connection." }
                finally { loading = false }
            }
        }, enabled = !loading && consent && assignedDoctor != null && symptoms.isNotBlank() && finding.isNotBlank() && history.isNotBlank() && (selectedPatient != null || (patientName.isNotBlank() && age.isNotBlank() && gender.isNotBlank() && facility.isNotBlank() && province != null && district != null && municipality != null)), modifier = Modifier.fillMaxWidth()) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Create and send case")
        }
    }
}

@Composable
private fun CaseDetailScreen(api: AuthApi, caseId: Long, refreshKey: Int, onBack: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember(caseId) { mutableStateOf<CaseDetail?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var chatText by rememberSaveable(caseId) { mutableStateOf("") }
    var chatPhotos by remember(caseId) { mutableStateOf(emptyList<Uri>()) }
    var diagnosis by rememberSaveable(caseId) { mutableStateOf("") }
    var treatment by rememberSaveable(caseId) { mutableStateOf("") }
    var prevention by rememberSaveable(caseId) { mutableStateOf("") }
    var referral by rememberSaveable(caseId) { mutableStateOf("") }
    val chatImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selected ->
        chatPhotos = (chatPhotos + selected).distinct().take(10)
    }

    LaunchedEffect(caseId, refreshKey) {
        loading = true; error = null
        try {
            val response = api.caseDetails(caseId)
            if (!response.isSuccessful) throw IOException(response.errorBody()?.string()?.let(::caseServerMessage) ?: "Couldn't load this case.")
            val casePayload = response.body()?.data ?: throw IOException("Case details were empty.")
            val messagesResponse = api.caseMessages(caseId)
            detail = if (messagesResponse.isSuccessful) {
                casePayload.copy(messages = messagesResponse.body()?.data.orEmpty())
            } else casePayload
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (exception: Exception) { error = exception.message ?: "Couldn't load this case." }
        finally { loading = false }
    }

    LaunchedEffect(caseId) {
        while (true) {
            val afterId = detail?.messages?.maxOfOrNull { it.id } ?: 0L
            try {
                val response = api.caseMessages(caseId, afterId = afterId)
                if (response.isSuccessful) {
                    val incoming = response.body()?.data.orEmpty()
                    if (incoming.isNotEmpty()) {
                        detail = detail?.let { current ->
                            current.copy(messages = (current.messages + incoming).distinctBy { it.id }.sortedBy { it.id })
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the open chat responsive during brief connection interruptions.
            }
            delay(2_000)
        }
    }

    CasePageFrame(title = "Case #$caseId", onBack = onBack, actions = {
        IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh case") }
    }) {
        error?.let { CaseNotice(it, error = true) }
        if (loading && detail == null) Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        detail?.let { payload ->
            val case = payload.caseSummary
            val allCasePhotos = (payload.photos + payload.messages.flatMap { it.photos }).distinctBy { it.id }
            Text(payload.patient?.name ?: case.patient?.name ?: "Patient", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Patient ID ${payload.patient?.patientCode ?: "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusPill(case.status)
            }
            CaseSection("Patient details") {
                DetailLine("Age / gender", listOfNotNull(payload.patient?.age?.let { "$it years" }, payload.patient?.gender).joinToString(" · ").ifBlank { "—" })
                DetailLine("Facility", payload.patient?.phc ?: case.phc ?: "—")
                DetailLine("Location", listOfNotNull(payload.patient?.municipality, payload.patient?.district, payload.patient?.province).joinToString(", ").ifBlank { "—" })
                DetailLine("Phone", payload.patient?.phone ?: "—")
                DetailLine("Email", payload.patient?.email ?: "—")
                DetailLine("Weight", payload.patient?.weight?.let { "$it kg" } ?: "—")
            }
            CaseSection("Case information") {
                DetailLine("Status", case.status?.caseStatusLabel() ?: "—")
                DetailLine("Priority", case.priority ?: "Normal")
                DetailLine("Created", case.createdAt?.caseDateLabel() ?: "—")
                DetailLine("Health worker", payload.healthWorker?.name ?: case.creator?.name ?: "—")
                DetailLine("Dermatologist", payload.dermatologist?.name ?: case.assignedDoctor?.name ?: "Unassigned")
                if (case.followUpOf != null) DetailLine("Follow-up of", "Case #${case.followUpOf}")
                DetailLine("Symptoms", case.symptoms ?: "—")
                DetailLine("Clinical findings", case.finding ?: "—")
                DetailLine("Medical history", case.history ?: "—")
            }
            if (payload.photos.isNotEmpty()) CaseSection("Case photos (${payload.photos.size})") {
                payload.photos.forEach { photo -> CasePhotoRow(api, caseId, photo, allCasePhotos) }
            }
            if (payload.treatments.isNotEmpty()) CaseSection("Clinical plans") {
                payload.treatments.forEach { plan -> TreatmentCard(plan) }
            }
            if (payload.previousCases.isNotEmpty()) CaseSection("Previous cases for this patient") {
                payload.previousCases.forEach { prior ->
                    Text("Case #${prior.id} · ${prior.createdAt?.caseDateLabel().orEmpty()} · ${prior.status?.caseStatusLabel().orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                    prior.treatments.forEach { DetailLine("Diagnosis", it.diagnosis ?: "—") }
                }
            }
            val permissions = payload.permissions
            if (payload.messages.isNotEmpty() || permissions?.canChat == true) CaseSection("Care team conversation") {
                if (payload.messages.isEmpty()) Text("No messages yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                payload.messages.forEach { message -> MessageCard(api, caseId, message, allCasePhotos) }
                if (permissions?.canChat == true) {
                    OutlinedTextField(chatText, { chatText = it }, Modifier.fillMaxWidth(), label = { Text("Message") }, minLines = 2, maxLines = 5)
                    OutlinedButton(onClick = { chatImagePicker.launch("image/*") }, enabled = chatPhotos.size < 10) { Text("Attach photos (${chatPhotos.size}/10)") }
                    chatPhotos.forEachIndexed { index, uri ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(uri.lastPathSegment?.substringAfterLast('/') ?: "Attachment ${index + 1}", Modifier.weight(1f), maxLines = 1)
                            IconButton(onClick = { chatPhotos = chatPhotos.filterIndexed { i, _ -> i != index } }) { Icon(Icons.Outlined.DeleteOutline, "Remove attachment") }
                        }
                    }
                    Button(onClick = {
                        scope.launch {
                            loading = true; error = null
                            try {
                                val fields = if (chatText.isBlank()) emptyMap() else mapOf("detail" to chatText.trim().toRequestBody("text/plain".toMediaType()))
                                val response = api.sendCaseMessage(caseId, fields, makePhotoParts(context, chatPhotos))
                                if (!response.isSuccessful) throw IOException(response.errorBody()?.string()?.let(::caseServerMessage) ?: "Couldn't send the message.")
                                val sentMessage = response.body()?.data
                                if (sentMessage != null) {
                                    detail = detail?.let { current -> current.copy(messages = (current.messages + sentMessage).distinctBy { it.id }.sortedBy { it.id }) }
                                } else onRefresh()
                                chatText = ""; chatPhotos = emptyList()
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (exception: Exception) { error = exception.message ?: "Couldn't send the message." }
                            finally { loading = false }
                        }
                    }, enabled = !loading && (chatText.isNotBlank() || chatPhotos.isNotEmpty()), modifier = Modifier.fillMaxWidth()) { Text("Send message") }
                } else CaseNotice("Read-only conversation access.")
            }
            if (permissions?.canPrescribe == true) CaseSection("Add clinical plan") {
                CaseTextField("Diagnosis", diagnosis, { diagnosis = it })
                CaseTextField("Treatment", treatment, { treatment = it }, multiline = true)
                CaseTextField("Prevention advice (optional)", prevention, { prevention = it }, multiline = true)
                CaseTextField("Referral (optional)", referral, { referral = it }, multiline = true)
                Button(onClick = {
                    scope.launch {
                        loading = true; error = null
                        try {
                            val response = api.prescribeCase(caseId, TreatmentRequest(diagnosis.trim(), treatment.trim(), prevention.trim().ifBlank { null }, referral.trim().ifBlank { null }))
                            if (!response.isSuccessful) throw IOException(response.errorBody()?.string()?.let(::caseServerMessage) ?: "Couldn't save the clinical plan.")
                            diagnosis = ""; treatment = ""; prevention = ""; referral = ""; onRefresh()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (exception: Exception) { error = exception.message ?: "Couldn't save the clinical plan." }
                        finally { loading = false }
                    }
                }, enabled = !loading && diagnosis.isNotBlank() && treatment.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save plan and complete case") }
            }
        }
    }
}

@Composable
private fun CasePageFrame(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(content = actions)
        }
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun CaseSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CaseTextField(label: String, value: String, onChange: (String) -> Unit, numeric: Boolean = false, multiline: Boolean = false) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = !multiline, minLines = if (multiline) 3 else 1, maxLines = if (multiline) 6 else 1, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text))
}

@Composable
private fun CaseLongDropdownField(label: String, value: String, options: List<Pair<Long, String>>, enabled: Boolean = true, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(value, Modifier.weight(1f), maxLines = 1) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; onSelect(id) }) }
            }
        }
    }
}

@Composable
private fun CaseStringDropdownField(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(value, Modifier.weight(1f), maxLines = 1) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (valueKey, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; onSelect(valueKey) }) }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String?) {
    val label = status?.caseStatusLabel() ?: "Unknown"
    Text(label, modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
}

@Composable
private fun CaseNotice(text: String, error: Boolean = false) {
    Text(text, modifier = Modifier.fillMaxWidth().background(if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)).padding(12.dp), color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun CasePhotoRow(api: AuthApi, caseId: Long, photo: CasePhoto, viewerPhotos: List<CasePhoto> = listOf(photo)) {
    var bitmap by remember(caseId, photo.id) { mutableStateOf<android.graphics.Bitmap?>(CasePhotoBitmapCache.get(caseId, photo.id)) }
    var failed by remember(photo.id) { mutableStateOf(false) }
    var showViewer by remember(photo.id) { mutableStateOf(false) }
    LaunchedEffect(photo.id, caseId) {
        try {
            bitmap = loadCasePhotoBitmap(api, caseId, photo.id)
            failed = bitmap == null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        bitmap?.let {
            Image(
                it.asImageBitmap(), contentDescription = "View ${photo.filename ?: "case photo"} full screen",
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(8.dp)).clickable { showViewer = true },
                contentScale = ContentScale.Crop,
            )
        }
            ?: Box(Modifier.size(76.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text(if (failed) "Unavailable" else "…", style = MaterialTheme.typography.labelSmall) }
        Column(Modifier.weight(1f)) {
            Text(photo.filename ?: "Case photo", maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            Text(photo.source?.replaceFirstChar(Char::uppercase) ?: "Case image", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showViewer) bitmap?.let {
        val photosForViewer = viewerPhotos.ifEmpty { listOf(photo) }
        val initialIndex = photosForViewer.indexOfFirst { viewerPhoto -> viewerPhoto.id == photo.id }.coerceAtLeast(0)
        PhotoViewer(
            api = api,
            caseId = caseId,
            photos = photosForViewer,
            initialIndex = initialIndex,
            initialBitmap = it.asImageBitmap(),
            onDismiss = { showViewer = false },
        )
    }
}

@Composable
private fun PhotoViewer(
    api: AuthApi,
    caseId: Long,
    photos: List<CasePhoto>,
    initialIndex: Int,
    initialBitmap: androidx.compose.ui.graphics.ImageBitmap,
    onDismiss: () -> Unit,
) {
    var selectedIndex by remember(photos, initialIndex) { mutableStateOf(initialIndex.coerceIn(0, photos.lastIndex)) }
    var bitmap by remember(photos, initialIndex) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(initialBitmap) }
    var imageLoading by remember(photos, initialIndex) { mutableStateOf(false) }
    var imageError by remember(photos, initialIndex) { mutableStateOf(false) }
    val preloadScope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val minScale = 1f
    val maxScale = 5f
    val currentPhoto = photos[selectedIndex]

    LaunchedEffect(currentPhoto.id) {
        scale = minScale
        offset = Offset.Zero
        val cached = CasePhotoBitmapCache.get(caseId, currentPhoto.id)
        if (cached != null) {
            bitmap = cached.asImageBitmap()
            imageLoading = false
            imageError = false
        } else {
            imageLoading = true
            imageError = false
            bitmap = null
            try {
                val loaded = loadCasePhotoBitmap(api, caseId, currentPhoto.id)
                bitmap = loaded?.asImageBitmap()
                imageError = loaded == null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                imageError = true
            } finally {
                imageLoading = false
            }
        }

        listOfNotNull(photos.getOrNull(selectedIndex - 1), photos.getOrNull(selectedIndex + 1)).forEach { adjacent ->
            if (CasePhotoBitmapCache.get(caseId, adjacent.id) == null) {
                preloadScope.launch { runCatching { loadCasePhotoBitmap(api, caseId, adjacent.id) } }
            }
        }
    }

    fun moveTo(index: Int) {
        if (index in photos.indices) {
            selectedIndex = index
            val cached = CasePhotoBitmapCache.get(caseId, photos[index].id)
            bitmap = cached?.asImageBitmap()
            imageLoading = cached == null
            imageError = false
            scale = minScale
            offset = Offset.Zero
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xE6121A20)).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Close photo", tint = Color.White) }
                Text(currentPhoto.filename ?: "Case photo", Modifier.weight(1f), maxLines = 1, color = Color.White, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { scale = (scale / 1.25f).coerceAtLeast(minScale); if (scale == minScale) offset = Offset.Zero }) {
                    Icon(Icons.Outlined.ZoomOut, contentDescription = "Zoom out", tint = Color.White)
                }
                Text("${(scale * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { scale = (scale * 1.25f).coerceAtMost(maxScale) }) {
                    Icon(Icons.Outlined.ZoomIn, contentDescription = "Zoom in", tint = Color.White)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    imageLoading -> CircularProgressIndicator(color = Color.White)
                    imageError -> Text("Couldn't load this photo.", color = Color.White)
                    else -> bitmap?.let { image ->
                        Image(
                            bitmap = image,
                            contentDescription = currentPhoto.filename ?: "Case photo ${selectedIndex + 1}",
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                                .pointerInput(image) {
                                    detectTransformGestures { _, pan, zoomChange, _ ->
                                        scale = (scale * zoomChange).coerceIn(minScale, maxScale)
                                        offset = if (scale <= minScale) Offset.Zero else offset + pan
                                    }
                                },
                            contentScale = ContentScale.Fit,
                        )
                    } ?: CircularProgressIndicator(color = Color.White)
                }
            }
            Column(Modifier.fillMaxWidth().background(Color(0xE6121A20)).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (photos.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    TextButton(
                        onClick = { moveTo(selectedIndex - 1) },
                        enabled = selectedIndex > 0,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.38f)),
                    ) { Text("‹ Previous") }
                    Text("${selectedIndex + 1} / ${photos.size}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    TextButton(
                        onClick = { moveTo(selectedIndex + 1) },
                        enabled = selectedIndex < photos.lastIndex,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.38f)),
                    ) { Text("Next ›") }
                }
                Text(
                    "Pinch to zoom · drag to move · use + / − to adjust",
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

private object CasePhotoBitmapCache {
    private val cacheSizeKb = (Runtime.getRuntime().maxMemory() / 1024 / 12).toInt().coerceIn(4 * 1024, 24 * 1024)
    private val cache = object : LruCache<String, android.graphics.Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun get(caseId: Long, photoId: Long): android.graphics.Bitmap? = cache.get("$caseId:$photoId")

    fun put(caseId: Long, photoId: Long, bitmap: android.graphics.Bitmap) {
        cache.put("$caseId:$photoId", bitmap)
    }
}

private suspend fun loadCasePhotoBitmap(api: AuthApi, caseId: Long, photoId: Long): android.graphics.Bitmap? {
    CasePhotoBitmapCache.get(caseId, photoId)?.let { return it }
    val response = api.casePhoto(caseId, photoId)
    if (!response.isSuccessful) throw IOException("Couldn't load this photo.")
    val bytes = withContext(Dispatchers.IO) { response.body()?.bytes() } ?: throw IOException("This photo was empty.")
    val bitmap = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 2400 || bounds.outHeight / sampleSize > 2400) sampleSize *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    } ?: throw IOException("This photo couldn't be decoded.")
    CasePhotoBitmapCache.put(caseId, photoId, bitmap)
    return bitmap
}

@Composable
private fun MessageCard(api: AuthApi, caseId: Long, message: CaseMessage, viewerPhotos: List<CasePhoto>) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(message.author?.name ?: "Care team", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        message.detail?.takeIf(String::isNotBlank)?.let { Text(it) }
        Text(message.createdAt?.caseDateLabel().orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        message.photos.forEach { CasePhotoRow(api, caseId, it, viewerPhotos) }
    }
}

@Composable
private fun TreatmentCard(treatment: CaseTreatment) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(treatment.diagnosis ?: "Clinical plan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        DetailLine("Treatment", treatment.treatment ?: "—")
        treatment.prevention?.takeIf(String::isNotBlank)?.let { DetailLine("Prevention", it) }
        treatment.referral?.takeIf(String::isNotBlank)?.let { DetailLine("Referral", it) }
        Text("${treatment.doctor?.name ?: "Dermatologist"} · ${treatment.createdAt?.caseDateLabel().orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private suspend fun makePhotoParts(context: Context, uris: List<Uri>): List<MultipartBody.Part> = withContext(Dispatchers.IO) {
    uris.mapIndexed { index, uri ->
        val metadata = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                (if (column >= 0) cursor.getString(column) else null) to (if (sizeColumn >= 0) cursor.getLong(sizeColumn) else -1L)
            } else null
        }
        val fileName = metadata?.first ?: "case-photo-${index + 1}.jpg"
        val size = metadata?.second ?: -1L
        if (size > 10 * 1024 * 1024) throw IOException("Each case photo must be 10 MB or smaller.")
        val mime = context.contentResolver.getType(uri)?.takeIf { it in listOf("image/jpeg", "image/png", "image/webp") } ?: "image/jpeg"
        MultipartBody.Part.createFormData("photos[]", fileName, UriPhotoRequestBody(context, uri, mime.toMediaType(), size))
    }
}

private class UriPhotoRequestBody(
    private val context: Context,
    private val uri: Uri,
    private val mediaType: okhttp3.MediaType,
    private val expectedSize: Long,
) : RequestBody() {
    override fun contentType(): okhttp3.MediaType = mediaType

    override fun contentLength(): Long = expectedSize

    override fun writeTo(sink: BufferedSink) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Couldn't read the selected photo.")
        var total = 0L
        input.source().use { source ->
            val buffer = okio.Buffer()
            while (true) {
                val read = source.read(buffer, 8_192)
                if (read == -1L) break
                total += read
                if (total > 10 * 1024 * 1024) throw IOException("Each case photo must be 10 MB or smaller.")
                sink.write(buffer, read)
            }
        }
        if (expectedSize >= 0 && total != expectedSize) throw IOException("The selected photo changed while uploading. Please select it again.")
    }
}

private fun caseRoleDescription(user: User): String = if (user.hasAdminAccess) {
    when (user.clinicalRole) {
        "MEDICAL_OFFICER" -> "All cases · clinical actions on your cases"
        "DERMATOLOGIST" -> "All cases · clinical actions on assigned cases"
        else -> "All cases · review access"
    }
} else when (user.clinicalRole) {
    "MEDICAL_OFFICER" -> "Your cases"
    "DERMATOLOGIST" -> "Assigned cases"
    else -> "All cases · review access"
}

private fun PlaceOption.placeLabel(): String = listOfNotNull(name, nameNepali).joinToString(" · ")

private fun String.caseStatusLabel(): String = when (lowercase()) {
    "awaiting_review" -> "Awaiting review"
    "completed" -> "Completed"
    else -> replace('_', ' ').replaceFirstChar(Char::uppercase)
}

private fun String.caseDateLabel(): String = replace('T', ' ').substringBefore('.').take(16)

private fun caseServerMessage(body: String): String {
    return try {
        val json = org.json.JSONObject(body)
        val errors = json.optJSONObject("errors")
        if (errors != null) {
            val keys = errors.keys()
            if (keys.hasNext()) errors.optJSONArray(keys.next())?.optString(0)?.takeIf(String::isNotBlank)?.let { return it }
        }
        json.optString("message").takeIf(String::isNotBlank) ?: "Please review the case details and try again."
    } catch (_: Exception) { "Please review the case details and try again." }
}
