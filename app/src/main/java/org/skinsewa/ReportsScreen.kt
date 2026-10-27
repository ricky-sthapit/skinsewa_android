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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.skinsewa.data.auth.AdminCaseReportData
import org.skinsewa.data.auth.AuthApi
import org.skinsewa.data.auth.PlaceOption
import org.skinsewa.data.auth.ReportCase
import org.skinsewa.data.auth.ReportDermatologist
import org.skinsewa.data.auth.ReportLocation
import java.io.IOException
import java.util.Calendar

private enum class ReportTab { CASES, LOCATIONS, DERMATOLOGISTS }

@Composable
fun ReportsScreen(api: AuthApi, onBack: () -> Unit, onOpenCase: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val reportCases = remember { mutableStateListOf<ReportCase>() }
    var report by remember { mutableStateOf<AdminCaseReportData?>(null) }
    var districtBreakdown by remember { mutableStateOf(emptyList<ReportLocation>()) }
    var loadingDistrictBreakdown by remember { mutableStateOf(false) }
    var month by rememberSaveable { mutableStateOf(currentReportMonth()) }
    var tab by rememberSaveable { mutableStateOf(ReportTab.CASES) }
    var showLocationFilterPage by rememberSaveable { mutableStateOf(false) }
    var provinces by remember { mutableStateOf(emptyList<PlaceOption>()) }
    var districts by remember { mutableStateOf(emptyList<PlaceOption>()) }
    var municipalities by remember { mutableStateOf(emptyList<PlaceOption>()) }
    var province by rememberSaveable { mutableStateOf<PlaceOption?>(null) }
    var district by rememberSaveable { mutableStateOf<PlaceOption?>(null) }
    var municipality by rememberSaveable { mutableStateOf<PlaceOption?>(null) }
    var selectedDoctor by rememberSaveable { mutableStateOf<ReportDermatologist?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var appliedSearch by rememberSaveable { mutableStateOf("") }
    var page by remember { mutableStateOf(1) }
    var lastPage by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var loadingPlaces by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadReports(requestedPage: Int = 1, append: Boolean = false) {
        scope.launch {
            loading = true
            error = null
            try {
                val response = api.adminCaseReports(
                    month = month,
                    provinceId = province?.id,
                    districtId = district?.id,
                    municipalityId = municipality?.id,
                    query = appliedSearch.takeIf(String::isNotBlank),
                    dermatologistId = selectedDoctor?.id,
                    page = requestedPage,
                )
                if (!response.isSuccessful) throw IOException(response.errorBody()?.string()?.let(::reportServerMessage) ?: "Couldn't load reports.")
                val data = response.body()?.data ?: throw IOException("The report response was empty.")
                report = data
                if (!append) reportCases.clear()
                reportCases.addAll(data.cases.data)
                page = data.cases.meta?.currentPage ?: requestedPage
                lastPage = data.cases.meta?.lastPage ?: requestedPage
                if (province == null && district == null) {
                    districts = data.locations.mapNotNull { item ->
                        item.id?.let { id -> PlaceOption(id = id, name = item.name, type = "District", parent = item.provinceId) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Couldn't load reports. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    fun loadDistrictBreakdown() {
        scope.launch {
            loadingDistrictBreakdown = true
            try {
                val response = api.adminCaseReports(
                    month = month,
                    provinceId = province?.id,
                    districtId = null,
                    municipalityId = null,
                    query = null,
                    dermatologistId = null,
                    page = 1,
                    perPage = 5,
                )
                if (!response.isSuccessful) throw IOException(response.errorBody()?.string()?.let(::reportServerMessage) ?: "Couldn't load districts.")
                districtBreakdown = response.body()?.data?.locations.orEmpty().sortedBy { it.name.lowercase() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Couldn't load districts. Check your connection and try again."
            } finally {
                loadingDistrictBreakdown = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadingPlaces = true
        try {
            val response = api.provinces()
            if (response.isSuccessful) provinces = response.body()?.data.orEmpty()
            else error = response.errorBody()?.string()?.let(::reportServerMessage) ?: "Couldn't load provinces."
        } catch (_: Exception) {
            error = "Couldn't load location filters. Check your connection and try again."
        } finally {
            loadingPlaces = false
        }
    }

    LaunchedEffect(province?.id) {
        if (province == null) {
            if (district == null) {
                districts = report?.locations.orEmpty().mapNotNull { item ->
                    item.id?.let { id -> PlaceOption(id = id, name = item.name, type = "District", parent = item.provinceId) }
                }
            }
            district = null
            municipalities = emptyList()
            municipality = null
        } else {
            loadingPlaces = true
            try {
                val response = api.placeChildren(province!!.id)
                if (response.isSuccessful) districts = response.body()?.data.orEmpty()
                else error = response.errorBody()?.string()?.let(::reportServerMessage) ?: "Couldn't load districts."
            } catch (_: Exception) {
                error = "Couldn't load districts. Check your connection and try again."
            } finally {
                loadingPlaces = false
            }
        }
    }

    LaunchedEffect(district?.id) {
        if (district == null) {
            municipalities = emptyList()
            municipality = null
        } else {
            loadingPlaces = true
            try {
                val response = api.placeChildren(district!!.id)
                if (response.isSuccessful) municipalities = response.body()?.data.orEmpty()
                else error = response.errorBody()?.string()?.let(::reportServerMessage) ?: "Couldn't load municipalities."
            } catch (_: Exception) {
                error = "Couldn't load municipalities. Check your connection and try again."
            } finally {
                loadingPlaces = false
            }
        }
    }

    LaunchedEffect(month, province?.id, district?.id, municipality?.id, appliedSearch, selectedDoctor?.id) {
        loadReports()
    }

    LaunchedEffect(month, province?.id, tab, showLocationFilterPage) {
        if (tab == ReportTab.LOCATIONS && !showLocationFilterPage) loadDistrictBreakdown()
    }

    val currentMonth = currentReportMonth()
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (showLocationFilterPage) showLocationFilterPage = false else onBack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = if (showLocationFilterPage) "Back to reports" else "Back")
            }
            Text(if (showLocationFilterPage) "Filter by location" else "Reports", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!showLocationFilterPage) {
                IconButton(onClick = { showLocationFilterPage = true }) { Icon(Icons.Outlined.FilterList, contentDescription = "Filter by location") }
                IconButton(onClick = { loadReports() }, enabled = !loading) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh reports") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = shiftReportMonth(month, -1) }) { Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Previous month") }
            Text(reportMonthLabel(month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { if (month < currentMonth) month = shiftReportMonth(month, 1) }, enabled = month < currentMonth) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = "Next month")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (loading && report == null) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(18.dp))
        } else {
            report?.let { data ->
                if (showLocationFilterPage) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Choose a province, district, or local level to narrow this month's report.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ReportPlaceDropdown("Province", province?.name, provinces, !loadingPlaces) { chosen ->
                                province = chosen
                                district = null
                                municipality = null
                                selectedDoctor = null
                            }
                            ReportPlaceDropdown("District", district?.name, districts, !loadingPlaces) { chosen ->
                                district = chosen
                                municipality = null
                                selectedDoctor = null
                            }
                            ReportPlaceDropdown("Municipality / local level", municipality?.name, municipalities, !loadingPlaces && district != null) { chosen ->
                                municipality = chosen
                                selectedDoctor = null
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(
                                onClick = {
                                    province = null
                                    district = null
                                    municipality = null
                                    selectedDoctor = null
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Clear filters") }
                            Button(onClick = { showLocationFilterPage = false }, modifier = Modifier.weight(1f)) { Text("Done") }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ReportMetric("Cases created", data.summary.totalCases, Modifier.weight(1f))
                        ReportMetric("Completed", data.summary.completedCases, Modifier.weight(1f))
                        ReportMetric("Districts", data.summary.districtsReported, Modifier.weight(1f)) { tab = ReportTab.LOCATIONS }
                    }
                    PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                        Tab(selected = tab == ReportTab.CASES, onClick = { tab = ReportTab.CASES }, text = { Text("Cases") })
                        Tab(selected = tab == ReportTab.LOCATIONS, onClick = { tab = ReportTab.LOCATIONS }, text = { Text("Locations") })
                        Tab(selected = tab == ReportTab.DERMATOLOGISTS, onClick = { tab = ReportTab.DERMATOLOGISTS }, text = { Text("Doctors", maxLines = 1, softWrap = false) })
                    }
                    when (tab) {
                    ReportTab.CASES -> {
                        if (selectedDoctor != null) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Assigned to ${selectedDoctor?.name}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { selectedDoctor = null }) { Text("Clear") }
                            }
                        }
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Patient name or case #") },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { appliedSearch = search.trim() }, enabled = !loading) {
                                    Icon(Icons.Outlined.Search, contentDescription = "Search cases")
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { appliedSearch = search.trim() }),
                        )
                        if (loading && reportCases.isEmpty()) {
                            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
                        } else if (reportCases.isEmpty()) {
                            Text("No cases match this month and location.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(reportCases, key = { it.id }) { item -> ReportCaseCard(item, onClick = { onOpenCase(item.id) }) }
                                if (page < lastPage) item {
                                    Button(onClick = { loadReports(page + 1, append = true) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Load more cases")
                                    }
                                }
                            }
                        }
                    }
                    ReportTab.DERMATOLOGISTS -> {
                        val items = data.dermatologists
                        if (items.isEmpty()) Text("No assigned dermatologist cases for this month and location.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(items, key = { it.id }) { doctor ->
                                ReportBreakdownCard(
                                    title = doctor.name,
                                    subtitle = "Cases currently assigned in this month",
                                    count = doctor.totalCases,
                                    onClick = { selectedDoctor = doctor; tab = ReportTab.CASES },
                                )
                            }
                        }
                    }
                    ReportTab.LOCATIONS -> {
                        if (loadingDistrictBreakdown && districtBreakdown.isEmpty()) {
                            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                        } else if (districtBreakdown.isEmpty()) {
                            Text("No districts reported cases this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(districtBreakdown, key = { "${it.id}-${it.name}" }) { item ->
                                ReportBreakdownCard(
                                    title = item.name,
                                    subtitle = item.province ?: "Cases reported this month",
                                    count = item.totalCases,
                                    onClick = {
                                        province = item.provinceId?.let { id -> provinces.firstOrNull { it.id == id } }
                                        district = item.id?.let { id -> PlaceOption(id, item.name, type = "District", parent = item.provinceId) }
                                        municipality = null
                                        selectedDoctor = null
                                        tab = ReportTab.CASES
                                    },
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun ReportPlaceDropdown(
    label: String,
    value: String?,
    options: List<PlaceOption>,
    enabled: Boolean,
    onSelect: (PlaceOption?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val allLabel = when (label) {
        "Province" -> "All provinces"
        "District" -> "All districts"
        else -> "All municipalities / local levels"
    }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value ?: allLabel,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { expanded = true },
            label = { Text(label) },
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }, enabled = enabled) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose $label")
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { onSelect(null); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.name) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
private fun ReportMetric(label: String, value: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(
        modifier = modifier.then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReportCaseCard(case: ReportCase, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Case #${case.caseNumber ?: case.id}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text((case.status ?: "unknown").replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(case.patient?.name ?: "Patient details unavailable", style = MaterialTheme.typography.bodyMedium)
            Text("${case.patient?.district ?: "District unavailable"} · ${case.patient?.municipality ?: "Local level unavailable"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Dermatologist: ${case.dermatologist?.name ?: "Unassigned"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReportBreakdownCard(title: String, subtitle: String, count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun currentReportMonth(): String = Calendar.getInstance().let { calendar ->
    "%04d-%02d".format(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
}

private fun shiftReportMonth(month: String, amount: Int): String {
    val parts = month.split('-')
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, parts[0].toInt())
        set(Calendar.MONTH, parts[1].toInt() - 1)
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, amount)
    }
    return "%04d-%02d".format(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
}

private fun reportMonthLabel(month: String): String {
    val parts = month.split('-')
    val calendar = Calendar.getInstance().apply { set(Calendar.YEAR, parts[0].toInt()); set(Calendar.MONTH, parts[1].toInt() - 1) }
    return java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(calendar.time)
}

private fun reportServerMessage(body: String): String = runCatching {
    val payload = JSONObject(body)
    val errors = payload.optJSONObject("errors")
    if (errors != null) {
        val fields = errors.keys()
        if (fields.hasNext()) errors.optJSONArray(fields.next())?.optString(0)?.takeIf(String::isNotBlank)?.let { return@runCatching it }
    }
    payload.optString("message").takeIf(String::isNotBlank)
}.getOrNull() ?: "Couldn't load reports. Check the selected filters and try again."
