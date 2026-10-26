package org.swasthaconnect

import android.os.Bundle
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import org.json.JSONObject
import org.swasthaconnect.core.designsystem.SkinSewaTheme
import org.swasthaconnect.core.designsystem.ThemeChoice
import org.swasthaconnect.data.auth.AuthResponse
import org.swasthaconnect.data.auth.ForgotPasswordRequest
import org.swasthaconnect.data.auth.LoginRequest
import org.swasthaconnect.data.auth.RegistrationRequest
import org.swasthaconnect.data.auth.User
import org.swasthaconnect.data.auth.EducationalResource
import java.io.IOException
import java.io.File
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SkinSewaApplication
        setContent {
            val themeChoice by app.themePreferences.choice.collectAsState(initial = ThemeChoice.LIGHT)
            SkinSewaTheme(darkTheme = themeChoice == ThemeChoice.DARK) {
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = themeChoice == ThemeChoice.LIGHT
                        isAppearanceLightNavigationBars = themeChoice == ThemeChoice.LIGHT
                    }
                }
                SkinSewaApp(app = app)
            }
        }
    }
}

private enum class Page { SPLASH, LOGIN, REGISTER, FORGOT_PASSWORD, HOME, RESOURCES, RESOURCE_ADMIN, CASES, USERS, INVITATIONS, SETTINGS }

@Composable
private fun SkinSewaApp(app: SkinSewaApplication) {
    var page by rememberSaveable { mutableStateOf(Page.SPLASH.name) }
    var user by remember { mutableStateOf<User?>(null) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var registrationError by remember { mutableStateOf<String?>(null) }
    var forgotMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val resources = remember { mutableStateListOf<EducationalResource>() }
    var resourcesLoading by remember { mutableStateOf(false) }
    var resourcesError by remember { mutableStateOf<String?>(null) }
    var resourcesMessage by remember { mutableStateOf<String?>(null) }
    var resourcePage by remember { mutableStateOf(1) }
    var resourceLastPage by remember { mutableStateOf(1) }
    var dashboardMetrics by remember { mutableStateOf<DashboardMetrics?>(null) }
    var dashboardLoading by remember { mutableStateOf(false) }
    var dashboardError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun loadResources(pageToLoad: Int = 1) {
        scope.launch {
            resourcesLoading = true
            resourcesError = null
            try {
                val response = app.authApi.resources(page = pageToLoad)
                if (response.isSuccessful) {
                    val payload = response.body()
                    if (pageToLoad == 1) resources.clear()
                    resources.addAll(payload?.data.orEmpty())
                    resourcePage = payload?.meta?.currentPage ?: pageToLoad
                    resourceLastPage = payload?.meta?.lastPage ?: pageToLoad
                } else {
                    resourcesError = response.errorBody()?.string()?.let(::serverMessage) ?: "Couldn't load resources."
                }
            } catch (_: IOException) {
                resourcesError = "Couldn't reach SkinSewa. Check your connection and try again."
            } catch (_: Exception) {
                resourcesError = "Something went wrong while loading resources."
            } finally {
                resourcesLoading = false
            }
        }
    }

    fun loadDashboardMetrics(account: User) {
        scope.launch {
            dashboardLoading = true
            dashboardError = null
            try {
                if (account.hasAdminAccess) {
                    val response = app.authApi.adminDashboard()
                    if (!response.isSuccessful) {
                        throw IOException(response.errorBody()?.string()?.let(::serverMessage) ?: "Couldn't load dashboard totals.")
                    }
                    val data = response.body()?.data ?: throw IOException("The dashboard response was empty.")
                    dashboardMetrics = DashboardMetrics(
                        totalUsers = data.users,
                        totalCases = data.cases,
                        totalResources = data.resources,
                        completedCases = data.casesByStatus.firstOrNull { it.status.equals("completed", ignoreCase = true) }?.total ?: 0,
                    )
                } else {
                    val caseResponse = app.authApi.cases()
                    if (!caseResponse.isSuccessful) {
                        throw IOException(caseResponse.errorBody()?.string()?.let(::serverMessage) ?: "Couldn't load your case totals.")
                    }
                    val completedResponse = app.authApi.cases(status = "completed")
                    if (!completedResponse.isSuccessful) {
                        throw IOException(completedResponse.errorBody()?.string()?.let(::serverMessage) ?: "Couldn't load completed cases.")
                    }
                    val resourceResponse = app.authApi.resources(perPage = 5)
                    if (!resourceResponse.isSuccessful) {
                        throw IOException(resourceResponse.errorBody()?.string()?.let(::serverMessage) ?: "Couldn't load resource totals.")
                    }
                    dashboardMetrics = DashboardMetrics(
                        totalUsers = null,
                        totalCases = caseResponse.body()?.meta?.total ?: 0,
                        totalResources = resourceResponse.body()?.meta?.total ?: 0,
                        completedCases = completedResponse.body()?.meta?.total ?: 0,
                    )
                }
            } catch (error: IOException) {
                dashboardError = error.message ?: "Couldn't load dashboard totals."
            } catch (_: Exception) {
                dashboardError = "Couldn't load dashboard totals. Check your connection and try again."
            } finally {
                dashboardLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val token = app.tokenStore.read()
        if (token != null) {
            runCatching { app.authApi.me() }
                .onSuccess { response ->
                    if (response.isSuccessful) {
                        user = response.body()?.data?.user
                        if (user != null) page = Page.HOME.name
                    } else {
                        app.tokenStore.clear()
                    }
                }
        }
        if (page == Page.SPLASH.name) {
            delay(950)
            page = Page.LOGIN.name
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (Page.valueOf(page)) {
            Page.SPLASH -> SplashPage()
            Page.LOGIN -> LoginPage(
                busy = busy,
                error = loginError,
                onForgotPassword = { loginError = null; page = Page.FORGOT_PASSWORD.name },
                onRegister = { registrationError = null; page = Page.REGISTER.name },
                onLogin = { email, password ->
                    scope.launch {
                        busy = true
                        loginError = null
                        try {
                            val response = app.authApi.login(LoginRequest(email.trim(), password))
                            if (response.isSuccessful) {
                                val payload: AuthResponse? = response.body()
                                val token = payload?.data?.token
                                if (token != null && payload?.data?.user != null) {
                                    user = activateSession(app, token)
                                    if (user != null) {
                                        page = Page.HOME.name
                                    } else {
                                        loginError = "Sign-in succeeded, but authenticated API access failed. Please sign in again."
                                    }
                                } else {
                                    loginError = "We couldn't read the sign-in response. Please try again."
                                }
                            } else {
                                loginError = response.errorBody()?.string()?.let(::serverMessage)
                                    ?: "Sign in failed. Check your email and password."
                            }
                        } catch (_: IOException) {
                            loginError = "Couldn't reach SkinSewa. Check your connection and try again."
                        } catch (_: Exception) {
                            loginError = "Something went wrong. Please try again."
                        } finally {
                            busy = false
                        }
                    }
                },
            )
            Page.REGISTER -> RegisterPage(
                busy = busy,
                error = registrationError,
                onBack = { registrationError = null; page = Page.LOGIN.name },
                onRegister = { request ->
                    scope.launch {
                        busy = true
                        registrationError = null
                        try {
                            val response = app.authApi.register(request)
                            if (response.isSuccessful) {
                                val payload = response.body()
                                val token = payload?.data?.token
                                if (token != null && payload?.data?.user != null) {
                                    user = activateSession(app, token)
                                    if (user != null) {
                                        page = Page.HOME.name
                                    } else {
                                        registrationError = "Your account was created, but authenticated API access failed. Please sign in again."
                                        page = Page.LOGIN.name
                                    }
                                } else {
                                    registrationError = "Your account was created, but we couldn't read the sign-in response. Please sign in."
                                    page = Page.LOGIN.name
                                }
                            } else {
                                registrationError = response.errorBody()?.string()?.let(::serverMessage)
                                    ?: "Registration failed. Check your invitation email and code."
                            }
                        } catch (_: IOException) {
                            registrationError = "Couldn't reach SkinSewa. Check your connection and try again."
                        } catch (_: Exception) {
                            registrationError = "Something went wrong. Please try again."
                        } finally {
                            busy = false
                        }
                    }
                },
            )
            Page.FORGOT_PASSWORD -> ForgotPasswordPage(
                busy = busy,
                message = forgotMessage,
                onBack = { forgotMessage = null; page = Page.LOGIN.name },
                onSubmit = { email ->
                    scope.launch {
                        busy = true
                        forgotMessage = null
                        try {
                            val response = app.authApi.forgotPassword(ForgotPasswordRequest(email.trim()))
                            if (response.isSuccessful) {
                                forgotMessage = response.body()?.message
                                    ?: "If an account exists for that email, password reset instructions have been sent."
                            } else {
                                forgotMessage = response.errorBody()?.string()?.let(::serverMessage)
                                    ?: "Enter a valid email address and try again."
                            }
                        } catch (_: IOException) {
                            forgotMessage = "Couldn't reach SkinSewa. Check your connection and try again."
                        } catch (_: Exception) {
                            forgotMessage = "Something went wrong. Please try again."
                        } finally {
                            busy = false
                        }
                    }
                },
            )
            Page.HOME -> HomePage(
                user = user,
                metrics = dashboardMetrics,
                metricsLoading = dashboardLoading,
                metricsError = dashboardError,
                onSettings = { page = Page.SETTINGS.name },
                onNavigate = { destination -> page = destination.name },
                onRefreshMetrics = { user?.let(::loadDashboardMetrics) },
                onSignOut = {
                    scope.launch {
                        runCatching { app.authApi.logout() }
                        app.tokenStore.clear()
                        user = null
                        page = Page.LOGIN.name
                    }
                },
            )
            Page.RESOURCES, Page.RESOURCE_ADMIN -> ResourcesScreen(
                admin = Page.valueOf(page) == Page.RESOURCE_ADMIN && user?.hasAdminAccess == true,
                resources = resources,
                loading = resourcesLoading,
                error = resourcesError,
                message = resourcesMessage,
                canLoadMore = resourcePage < resourceLastPage,
                onBack = { resourcesMessage = null; page = Page.HOME.name },
                onRefresh = { loadResources() },
                onLoadMore = { loadResources(resourcePage + 1) },
                onOpen = { resource ->
                    scope.launch {
                        resourcesError = null
                        resourcesMessage = null
                        resourcesLoading = true
                        try {
                            val response = app.authApi.viewResource(resource.id)
                            if (!response.isSuccessful) throw IOException("The PDF couldn't be opened.")
                            val file = withContext(Dispatchers.IO) {
                                val folder = File(context.cacheDir, "resources").apply { mkdirs() }
                                File(folder, "resource-${resource.id}.pdf").also { output ->
                                    val body = response.body() ?: throw IOException("The PDF was empty.")
                                    body.byteStream().use { input -> output.outputStream().use { input.copyTo(it) } }
                                }
                            }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        } catch (_: android.content.ActivityNotFoundException) {
                            resourcesError = "No PDF viewer is installed on this device."
                        } catch (_: IOException) {
                            resourcesError = "Couldn't open this PDF. Check your connection and try again."
                        } catch (_: Exception) {
                            resourcesError = "Couldn't open this PDF."
                        } finally {
                            resourcesLoading = false
                        }
                    }
                },
                onSave = { id, displayName, uri ->
                    scope.launch {
                        resourcesLoading = true
                        resourcesError = null
                        resourcesMessage = null
                        try {
                            val namePart = displayName.toRequestBody("text/plain".toMediaType())
                            val filePart = uri?.let { selected ->
                                val fileName = context.contentResolver.query(selected, null, null, null, null)?.use { cursor ->
                                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                                } ?: "resource.pdf"
                                val bytes = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(selected)?.use { it.readBytes() }
                                        ?: throw IOException("Couldn't read the selected PDF.")
                                }
                                if (bytes.size > 20 * 1024 * 1024) throw IOException("Choose a PDF smaller than 20 MB.")
                                MultipartBody.Part.createFormData("resource_file", fileName, bytes.toRequestBody("application/pdf".toMediaType()))
                            }
                            val response = if (id == null) {
                                if (filePart == null) throw IOException("Choose a PDF to upload.")
                                app.authApi.createResource(namePart, filePart)
                            } else app.authApi.updateResource(id, namePart, filePart)
                            if (response.isSuccessful) {
                                resourcesMessage = response.body()?.message ?: "Resource saved."
                                loadResources()
                            } else resourcesError = response.errorBody()?.string()?.let(::serverMessage) ?: "Couldn't save this resource."
                        } catch (e: IOException) {
                            resourcesError = e.message ?: "Couldn't save this resource."
                        } catch (_: Exception) {
                            resourcesError = "Something went wrong while saving the resource."
                        } finally {
                            resourcesLoading = false
                        }
                    }
                },
                onDelete = { resource ->
                    scope.launch {
                        resourcesLoading = true
                        resourcesError = null
                        try {
                            val response = app.authApi.deleteResource(resource.id)
                            if (response.isSuccessful) {
                                resources.removeAll { it.id == resource.id }
                                resourcesMessage = response.body()?.message ?: "Resource deleted."
                            } else resourcesError = response.errorBody()?.string()?.let(::serverMessage) ?: "Couldn't delete this resource."
                        } catch (_: Exception) {
                            resourcesError = "Couldn't delete this resource. Check your connection and try again."
                        } finally {
                            resourcesLoading = false
                        }
                    }
                },
            )
            Page.CASES -> user?.let { account ->
                CasesScreen(api = app.authApi, user = account, onBack = { page = Page.HOME.name })
            }
            Page.USERS, Page.INVITATIONS -> ComingSoonPage(
                title = when (Page.valueOf(page)) {
                    Page.CASES -> "Cases"
                    Page.USERS -> "Users"
                    else -> "Invitations"
                },
                onBack = { page = Page.HOME.name },
            )
            Page.SETTINGS -> SettingsPage(
                onBack = { page = Page.HOME.name },
                selected = app.themePreferences.choice.collectAsState(initial = ThemeChoice.LIGHT).value,
                onThemeSelected = { choice -> scope.launch { app.themePreferences.set(choice) } },
            )
        }
    }

    LaunchedEffect(page) {
        if (page == Page.RESOURCES.name || page == Page.RESOURCE_ADMIN.name) loadResources()
    }

    LaunchedEffect(page, user?.id) {
        if (page == Page.HOME.name) user?.let(::loadDashboardMetrics)
    }
}

private data class DashboardMetrics(
    val totalUsers: Int?,
    val totalCases: Int,
    val totalResources: Int,
    val completedCases: Int,
)

private suspend fun activateSession(app: SkinSewaApplication, accessToken: String): User? {
    return try {
        val tokenSaved = withContext(Dispatchers.IO) {
            app.tokenStore.save(accessToken) && app.tokenStore.read() == accessToken
        }
        if (!tokenSaved) {
            withContext(Dispatchers.IO) { app.tokenStore.clear() }
            return null
        }
        val response = app.authApi.me()
        if (!response.isSuccessful) {
            withContext(Dispatchers.IO) { app.tokenStore.clear() }
            null
        } else {
            response.body()?.data?.user ?: run {
                withContext(Dispatchers.IO) { app.tokenStore.clear() }
                null
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        withContext(Dispatchers.IO) { app.tokenStore.clear() }
        throw error
    }
}

private fun serverMessage(body: String): String = runCatching {
    val payload = JSONObject(body)
    val errors = payload.optJSONObject("errors")
    if (errors != null) {
        val fields = errors.keys()
        if (fields.hasNext()) {
            errors.optJSONArray(fields.next())?.optString(0)?.takeIf { it.isNotBlank() }?.let { return@runCatching it }
        }
    }
    payload.optString("message").takeIf { it.isNotBlank() }
}.getOrNull() ?: "Please check your details and try again."

@Composable
private fun SplashPage() {
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.skinsewa_logo_screen),
            contentDescription = "SkinSewa logo",
            modifier = Modifier.size(232.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(16.dp))
        Text("Your Skin. Our Care.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LoginPage(
    busy: Boolean,
    error: String?,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit,
    onLogin: (String, String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    AuthPageFrame {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CenteredBrandHeader()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Welcome back", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Sign in to continue caring for healthier skin.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; localError = null },
                label = { Text("Email address") },
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; localError = null },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.End)) {
                Text("Forgot password?")
            }
            (localError ?: error)?.let { InlineMessage(it, isError = true) }
            Button(
                onClick = {
                    localError = when {
                        email.isBlank() -> "Enter your email address."
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address."
                        password.isBlank() -> "Enter your password."
                        else -> null
                    }
                    if (localError == null) onLogin(email, password)
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Sign in", fontWeight = FontWeight.SemiBold)
            }
            Text("Secure access for your care team", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Invited to SkinSewa?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRegister) { Text("Create account") }
            }
        }
    }
}

@Composable
private fun RegisterPage(
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRegister: (RegistrationRequest) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var facility by rememberSaveable { mutableStateOf("") }
    var specialty by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    AuthPageFrame {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to sign in")
            }
            CenteredBrandHeader()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Create your account", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Use the email and six-digit code from your SkinSewa invitation. Your role is assigned by the invitation.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it; localError = null },
                label = { Text("Invited email address") }, leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = otp,
                onValueChange = { value -> otp = value.filter(Char::isDigit).take(6); localError = null },
                label = { Text("Email verification code") }, leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = firstName, onValueChange = { firstName = it; localError = null },
                label = { Text("First name") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = lastName, onValueChange = { lastName = it; localError = null },
                label = { Text("Last name") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it; localError = null },
                label = { Text("Phone (optional)") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = facility, onValueChange = { facility = it; localError = null },
                label = { Text("Facility (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = specialty, onValueChange = { specialty = it; localError = null },
                label = { Text("Specialty (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it; localError = null },
                label = { Text("Create password") }, leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = confirmPassword, onValueChange = { confirmPassword = it; localError = null },
                label = { Text("Confirm password") }, leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            )
            Text("Use at least 10 characters, including letters and numbers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (localError ?: error)?.let { InlineMessage(it, isError = true) }
            Button(
                onClick = {
                    localError = when {
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter the email address from your invitation."
                        !Regex("^[0-9]{6}$").matches(otp) -> "Enter the six-digit code from your invitation email."
                        firstName.isBlank() -> "Enter your first name."
                        lastName.isBlank() -> "Enter your last name."
                        password.length < 10 || !password.any(Char::isLetter) || !password.any(Char::isDigit) -> "Use at least 10 characters with letters and numbers."
                        password != confirmPassword -> "Your passwords do not match."
                        else -> null
                    }
                    if (localError == null) onRegister(
                        RegistrationRequest(
                            email = email.trim(), otp = otp, firstName = firstName.trim(), lastName = lastName.trim(),
                            password = password, passwordConfirmation = confirmPassword,
                            phone = phone.trim().ifBlank { null }, facilityName = facility.trim().ifBlank { null },
                            specialty = specialty.trim().ifBlank { null },
                        ),
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Verify and create account", fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Return to sign in") }
        }
    }
}

@Composable
private fun ForgotPasswordPage(busy: Boolean, message: String?, onBack: () -> Unit, onSubmit: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    AuthPageFrame {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to sign in")
            }
            BrandHeader(compact = true)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reset your password", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Enter the email for your SkinSewa account. If it exists, we’ll send password reset instructions.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; localError = null },
                label = { Text("Email address") },
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            (localError ?: message)?.let { InlineMessage(it, isError = localError != null) }
            Button(
                onClick = {
                    localError = if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) "Enter a valid email address." else null
                    if (localError == null) onSubmit(email)
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Send reset instructions", fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Return to sign in") }
        }
    }
}

@Composable
private fun HomePage(
    user: User?,
    metrics: DashboardMetrics?,
    metricsLoading: Boolean,
    metricsError: String?,
    onSettings: () -> Unit,
    onNavigate: (Page) -> Unit,
    onRefreshMetrics: () -> Unit,
    onSignOut: () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val isAdmin = user?.hasAdminAccess == true
    var adminExpanded by rememberSaveable { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)) {
                    BrandHeader(compact = true)
                    Spacer(Modifier.height(16.dp))
                    Text(user?.name ?: "SkinSewa user", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(user?.displayRole ?: "Care team", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                NavigationDrawerItem(
                    label = { Text("Dashboard") }, icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    selected = true, onClick = { scope.launch { drawerState.close() } }, modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Cases") }, icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                    selected = false, onClick = { scope.launch { drawerState.close(); onNavigate(Page.CASES) } }, modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Resources") }, icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                    selected = false, onClick = { scope.launch { drawerState.close(); onNavigate(Page.RESOURCES) } }, modifier = Modifier.padding(horizontal = 12.dp),
                )
                if (isAdmin) {
                    NavigationDrawerItem(
                        label = { Text("Administration") }, icon = { Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null) },
                        badge = { Text(if (adminExpanded) "−" else "+", color = MaterialTheme.colorScheme.primary) },
                        selected = false, onClick = { adminExpanded = !adminExpanded }, modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    if (adminExpanded) {
                        NavigationDrawerItem(
                            label = { Text("Users") }, icon = { Icon(Icons.Outlined.People, contentDescription = null) },
                            selected = false, onClick = { scope.launch { drawerState.close(); onNavigate(Page.USERS) } }, modifier = Modifier.padding(start = 28.dp, end = 12.dp),
                        )
                        NavigationDrawerItem(
                            label = { Text("Invitations") }, icon = { Icon(Icons.Outlined.GroupAdd, contentDescription = null) },
                            selected = false, onClick = { scope.launch { drawerState.close(); onNavigate(Page.INVITATIONS) } }, modifier = Modifier.padding(start = 28.dp, end = 12.dp),
                        )
                        NavigationDrawerItem(
                            label = { Text("Manage resources") }, icon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null) },
                            selected = false, onClick = { scope.launch { drawerState.close(); onNavigate(Page.RESOURCE_ADMIN) } }, modifier = Modifier.padding(start = 28.dp, end = 12.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                NavigationDrawerItem(
                    label = { Text("Settings") }, icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    selected = false, onClick = { scope.launch { drawerState.close(); onSettings() } }, modifier = Modifier.padding(horizontal = 12.dp),
                )
                TextButton(onClick = { scope.launch { drawerState.close(); onSignOut() } }, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.widthIn(max = 900.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Outlined.Menu, contentDescription = "Open navigation menu") }
                BrandHeader(compact = true, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
            }
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Welcome back, ${user?.firstName ?: "care team"}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    DashboardMetricGrid(
                        metrics = metrics,
                        isAdmin = user?.hasAdminAccess == true,
                        loading = metricsLoading,
                    )
                    metricsError?.let {
                        InlineMessage(it, isError = true)
                        TextButton(onClick = onRefreshMetrics, modifier = Modifier.align(Alignment.End)) { Text("Retry") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Text(user?.name ?: "SkinSewa user", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    InfoLine("Account type", user?.displayRole ?: "Care team")
                    InfoLine("Email", user?.email.orEmpty())
                    user?.facilityName?.takeIf(String::isNotBlank)?.let { InfoLine("Facility", it) }
                    user?.specialty?.takeIf(String::isNotBlank)?.let { InfoLine("Specialty", it) }
                }
            }
        }
    }
}

@Composable
private fun DashboardMetricGrid(
    metrics: DashboardMetrics?,
    isAdmin: Boolean,
    loading: Boolean,
) {
    val values = buildList {
        if (isAdmin) add("Total users" to metrics?.totalUsers?.toString())
        add((if (isAdmin) "Total cases" else "Your cases") to metrics?.totalCases?.toString())
        add("Resources" to metrics?.totalResources?.toString())
        add("Completed cases" to metrics?.completedCases?.toString())
    }
    val tileColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(2).forEachIndexed { rowIndex, rowValues ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowValues.forEachIndexed { columnIndex, (label, value) ->
                    val colorIndex = (rowIndex * 2 + columnIndex) % tileColors.size
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = tileColors[colorIndex]),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = value ?: if (loading) "…" else "—",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (rowValues.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ComingSoonPage(title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(36.dp))
        Text("$title are coming soon.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsPage(onBack: () -> Unit, selected: ThemeChoice, onThemeSelected: (ThemeChoice) -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.widthIn(max = 760.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Choose how SkinSewa looks on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ThemeOption(ThemeChoice.LIGHT, selected, "Light", "A bright, clear interface", Icons.Outlined.LightMode, onThemeSelected)
                ThemeOption(ThemeChoice.DARK, selected, "Dark", "A low-glare palette for dim settings", Icons.Outlined.DarkMode, onThemeSelected)
            }
        }
    }
}

@Composable
private fun ThemeOption(choice: ThemeChoice, selected: ThemeChoice, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onSelect: (ThemeChoice) -> Unit) {
    Surface(
        onClick = { onSelect(choice) },
        shape = RoundedCornerShape(16.dp),
        color = if (selected == choice) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected == choice, onClick = { onSelect(choice) })
        }
    }
}

@Composable
private fun AuthPageFrame(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val wide = maxWidth >= 820.dp
        if (wide) {
            Row(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WelcomePanel(Modifier.weight(1f))
                Surface(
                    modifier = Modifier.weight(1f).widthIn(max = 520.dp),
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                ) { Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), content = content) }
            }
        } else {
            Column(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), content = content)
            }
        }
    }
}

@Composable
private fun WelcomePanel(modifier: Modifier = Modifier) {
    Column(modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.skinsewa_logo_screen), contentDescription = "SkinSewa", modifier = Modifier.size(280.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(12.dp))
        Text("Skin care, closer to home.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Connect local health workers with dermatologists for thoughtful, timely care.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { index -> Box(Modifier.size(if (index == 0) 9.dp else 7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = if (index == 0) 1f else .35f))) }
            Text("Care teams · Case review · Follow-up", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BrandHeader(compact: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(
            painter = painterResource(R.drawable.skinsewa_logo_screen),
            contentDescription = "SkinSewa logo",
            modifier = Modifier.size(if (compact) 58.dp else 82.dp),
            contentScale = ContentScale.Fit,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("SkinSewa", style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("YOUR SKIN. OUR CARE.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.1.sp)
        }
    }
}

@Composable
private fun CenteredBrandHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.skinsewa_logo_screen),
            contentDescription = "SkinSewa logo",
            modifier = Modifier.size(118.dp),
            contentScale = ContentScale.Fit,
        )
        Text("SkinSewa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("YOUR SKIN. OUR CARE.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.1.sp)
    }
}

@Composable
private fun InlineMessage(message: String, isError: Boolean) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
    Text(message, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(10.dp))
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
