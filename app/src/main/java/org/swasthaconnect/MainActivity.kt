package org.swasthaconnect

import android.os.Bundle
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.json.JSONObject
import org.swasthaconnect.core.designsystem.SkinSewaTheme
import org.swasthaconnect.core.designsystem.ThemeChoice
import org.swasthaconnect.data.auth.AuthResponse
import org.swasthaconnect.data.auth.ForgotPasswordRequest
import org.swasthaconnect.data.auth.LoginRequest
import org.swasthaconnect.data.auth.User
import java.io.IOException
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

private enum class Page { SPLASH, LOGIN, FORGOT_PASSWORD, HOME, SETTINGS }

@Composable
private fun SkinSewaApp(app: SkinSewaApplication) {
    var page by rememberSaveable { mutableStateOf(Page.SPLASH.name) }
    var user by remember { mutableStateOf<User?>(null) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var forgotMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                onLogin = { email, password ->
                    scope.launch {
                        busy = true
                        loginError = null
                        try {
                            val response = app.authApi.login(LoginRequest(email.trim(), password))
                            if (response.isSuccessful) {
                                val payload: AuthResponse? = response.body()
                                val token = payload?.data?.token
                                val account = payload?.data?.user
                                if (token != null && account != null) {
                                    app.tokenStore.save(token)
                                    user = account
                                    page = Page.HOME.name
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
                onSettings = { page = Page.SETTINGS.name },
                onSignOut = {
                    scope.launch {
                        runCatching { app.authApi.logout() }
                        app.tokenStore.clear()
                        user = null
                        page = Page.LOGIN.name
                    }
                },
            )
            Page.SETTINGS -> SettingsPage(
                onBack = { page = Page.HOME.name },
                selected = app.themePreferences.choice.collectAsState(initial = ThemeChoice.LIGHT).value,
                onThemeSelected = { choice -> scope.launch { app.themePreferences.set(choice) } },
            )
        }
    }
}

private fun serverMessage(body: String): String = runCatching {
    JSONObject(body).optString("message").takeIf { it.isNotBlank() }
}.getOrNull() ?: "Please check your details and try again."

@Composable
private fun SplashPage() {
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)
            .background(MaterialTheme.colorScheme.background).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.skinsewa_logo_screen),
            contentDescription = "SkinSewa logo",
            modifier = Modifier.size(232.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(24.dp))
        Text("Your Skin. Our Care.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(36.dp))
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LoginPage(busy: Boolean, error: String?, onForgotPassword: () -> Unit, onLogin: (String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    AuthPageFrame {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Sign in", fontWeight = FontWeight.SemiBold)
            }
            Text("Secure access for your care team", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ForgotPasswordPage(busy: Boolean, message: String?, onBack: () -> Unit, onSubmit: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    AuthPageFrame {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
                modifier = Modifier.fillMaxWidth().height(54.dp),
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
private fun HomePage(user: User?, onSettings: () -> Unit, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.widthIn(max = 900.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BrandHeader(compact = true, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
        }
        Spacer(Modifier.height(36.dp))
        Card(
            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("You’re signed in", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Welcome to the SkinSewa care workspace.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Text(user?.name ?: "SkinSewa user", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                InfoLine("Account type", user?.role?.replace('_', ' ')?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "Care team")
                InfoLine("Email", user?.email.orEmpty())
                user?.facilityName?.takeIf(String::isNotBlank)?.let { InfoLine("Facility", it) }
                user?.specialty?.takeIf(String::isNotBlank)?.let { InfoLine("Specialty", it) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Settings") }
                    TextButton(onClick = onSignOut) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(onBack: () -> Unit, selected: ThemeChoice, onThemeSelected: (ThemeChoice) -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Card(Modifier.widthIn(max = 760.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WelcomePanel(Modifier.weight(1f))
                Surface(
                    modifier = Modifier.weight(1f).widthIn(max = 520.dp),
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                ) { Column(Modifier.verticalScroll(rememberScrollState()).padding(32.dp), content = content) }
            }
        } else {
            Column(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), content = content)
            }
        }
    }
}

@Composable
private fun WelcomePanel(modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.skinsewa_logo_screen), contentDescription = "SkinSewa", modifier = Modifier.size(280.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(12.dp))
        Text("Skin care, closer to home.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Connect local health workers with dermatologists for thoughtful, timely care.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
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
        Text(message, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
