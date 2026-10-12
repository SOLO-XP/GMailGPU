package com.g57.issuehub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.g57.issuehub.data.Attachment
import com.g57.issuehub.data.Issue
import com.g57.issuehub.data.Notification
import com.g57.issuehub.data.Profile
import com.g57.issuehub.ui.*
import java.util.Locale


@Composable
internal fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current
) {
    val language = LocalAppLanguage.current
    val translated = remember(text, language) { mutableStateOf(UiTextTranslationCache.get(language, text) ?: text) }
    LaunchedEffect(text, language) {
        translated.value = UiTextTranslationCache.get(language, text) ?: text
        if (language == "en" || text.isBlank() || translated.value != text) return@LaunchedEffect
        val target = when (language) {
            "ar" -> com.google.mlkit.nl.translate.TranslateLanguage.ARABIC
            "pt-BR" -> com.google.mlkit.nl.translate.TranslateLanguage.PORTUGUESE
            else -> null
        } ?: return@LaunchedEffect
        try {
            // All app-owned interface labels are authored in English. Do not run language
            // identification on short labels ("Home", "OK", "GPU"): ML Kit often returns "und".
            // That was the reason many controls stayed in English after a language change.
            val source = com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH
            if (text.any { it in '\u0600'..'\u06FF' } && language == "ar") return@LaunchedEffect
            if (text.any { it in '\u00C0'..'\u024F' } && language == "pt-BR" &&
                text.any { it in "ãõçáéíóúâêô" }) return@LaunchedEffect
            val translator = ChatTranslationCache.translator(source, target)
            ChatTranslationCache.ensureModel(source, target)
            val result = translator.translate(text).await()
            UiTextTranslationCache.put(language, text, result)
            translated.value = result
        } catch (_: Exception) {
            // Keep the source text readable if a model is unavailable or offline.
        }
    }
    MaterialText(
        text = translated.value,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        style = style
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { G57App() }
    }
}

private data class G57Palette(
    val background: Color, val surface: Color, val primary: Color, val secondary: Color,
    val text: Color, val muted: Color, val border: Color, val light: Boolean = false
)
private val LocalG57Palette = compositionLocalOf {
    G57Palette(Color(0xFF080A0F), Color(0xFF121722), Color(0xFF9B7BFF), Color(0xFF62D9FF),
        Color(0xFFF5F7FB), Color(0xFF9AA4B5), Color(0xFF303746))
}
private val G57Bg: Color @Composable get() = LocalG57Palette.current.background
private val G57Card: Color @Composable get() = LocalG57Palette.current.surface
private val G57Primary: Color @Composable get() = LocalG57Palette.current.primary
private val G57Cyan: Color @Composable get() = LocalG57Palette.current.secondary
private val G57Text: Color @Composable get() = LocalG57Palette.current.text
private val G57Muted: Color @Composable get() = LocalG57Palette.current.muted
private val G57Border: Color @Composable get() = LocalG57Palette.current.border

private fun g57Palette(choice: String): G57Palette = when (choice) {
    "Ocean" -> G57Palette(Color(0xFF061923), Color(0xFF0D2938), Color(0xFF48B8E8), Color(0xFF8BE5FF), Color(0xFFEAF9FF), Color(0xFF91B6C7), Color(0xFF245064))
    "Emerald" -> G57Palette(Color(0xFF071A14), Color(0xFF102B20), Color(0xFF4ED6A0), Color(0xFF9AF5CB), Color(0xFFE9FFF5), Color(0xFF96BDAA), Color(0xFF285641))
    "Amber" -> G57Palette(Color(0xFF1B1408), Color(0xFF302312), Color(0xFFFFBD59), Color(0xFFFFDE9A), Color(0xFFFFF7E8), Color(0xFFC6B28F), Color(0xFF5B4528))
    "Rose" -> G57Palette(Color(0xFF1D0B14), Color(0xFF321321), Color(0xFFFF78AC), Color(0xFFFFB6D0), Color(0xFFFFF0F6), Color(0xFFC8A0B1), Color(0xFF633047))
    "Light" -> G57Palette(Color(0xFFF3F5FA), Color(0xFFFFFFFF), Color(0xFF6750A4), Color(0xFF356A9A), Color(0xFF171923), Color(0xFF5E6573), Color(0xFFD8DDE7), light = true)
    else -> G57Palette(Color(0xFF080A0F), Color(0xFF121722), Color(0xFF9B7BFF), Color(0xFF62D9FF), Color(0xFFF5F7FB), Color(0xFF9AA4B5), Color(0xFF303746))
}

@Composable
fun G57App(vm: G57ViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(ui.appLanguage) {
        val target = when (ui.appLanguage) {
            "ar" -> com.google.mlkit.nl.translate.TranslateLanguage.ARABIC
            "pt-BR" -> com.google.mlkit.nl.translate.TranslateLanguage.PORTUGUESE
            else -> null
        }
        if (target != null) {
            runCatching {
                ChatTranslationCache.ensureModel(
                    com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH,
                    target
                )
            }
        }
    }

    LaunchedEffect(ui.openUrl) {
        ui.openUrl?.let { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW).apply { data = android.net.Uri.parse(url) })
            }.onFailure {
                android.widget.Toast.makeText(context, "No app can open this file type.", android.widget.Toast.LENGTH_LONG).show()
            }
            vm.clearOpenUrl()
        }
    }

    LaunchedEffect(ui.downloadUrl, ui.downloadFilename) {
        val url = ui.downloadUrl
        val filename = ui.downloadFilename
        if (url != null && filename != null) {
            runCatching {
                val manager = context.getSystemService(android.app.DownloadManager::class.java)
                    ?: error("Android Download Manager is unavailable")
                val safeName = filename.substringAfterLast('/').substringAfterLast('\\')
                    .replace(Regex("[^A-Za-z0-9._() -]"), "_")
                    .take(160).ifBlank { "gmailgpu-attachment" }
                val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                    .setTitle(safeName)
                    .setDescription("GMailGPU issue attachment")
                    .setMimeType(ui.downloadMimeType ?: "application/octet-stream")
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, safeName)
                manager.enqueue(request)
                android.widget.Toast.makeText(context, "Downloading to Downloads: $safeName", android.widget.Toast.LENGTH_LONG).show()
            }.onFailure {
                android.widget.Toast.makeText(context, "Download failed: ${it.message ?: "unknown error"}", android.widget.Toast.LENGTH_LONG).show()
            }
            vm.clearDownloadRequest()
        }
    }

    BackHandler(enabled = ui.screen != ScreenState.Login) { vm.back() }

    val palette = g57Palette(ui.themeChoice)
    LaunchedEffect(ui.screen) { drawerState.close() }

    CompositionLocalProvider(
        LocalG57Palette provides palette,
        LocalAppLanguage provides ui.appLanguage,
        LocalLayoutDirection provides if (ui.appLanguage == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
    MaterialTheme(
        colorScheme = if (palette.light) lightColorScheme(
            background = palette.background, surface = palette.surface, primary = palette.primary,
            secondary = palette.secondary, onBackground = palette.text, onSurface = palette.text,
            onPrimary = Color.White, outline = palette.border
        ) else darkColorScheme(
            background = palette.background, surface = palette.surface, primary = palette.primary,
            secondary = palette.secondary, onBackground = palette.text, onSurface = palette.text,
            onPrimary = Color(0xFF080A0F), outline = palette.border
        )
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,
            drawerContent = {
                if (ui.screen != ScreenState.Login) {
                    ModalDrawerSheet(drawerContainerColor = G57Card, drawerContentColor = G57Text) {
                        AppText("GMailGPU", modifier = Modifier.padding(start = 22.dp, top = 24.dp, bottom = 4.dp), color = G57Primary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        AppText("Navigation", modifier = Modifier.padding(start = 22.dp, bottom = 18.dp), color = G57Muted)
                        NavigationDrawerItem(
                            label = { AppText("Home") },
                            selected = ui.screen == ScreenState.UserHome || ui.screen == ScreenState.AdminHome,
                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                            onClick = { scope.launch { drawerState.close() }; vm.goHome() },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                        NavigationDrawerItem(
                            label = { AppText("Need Help? • How To Use App") },
                            selected = ui.screen == ScreenState.Help,
                            icon = { Icon(Icons.Default.HelpOutline, contentDescription = null) },
                            onClick = { scope.launch { drawerState.close() }; vm.openHelp() },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                        NavigationDrawerItem(
                            label = { AppText("Settings") },
                            selected = ui.screen == ScreenState.Settings,
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = { scope.launch { drawerState.close() }; vm.openSettings() },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                        NavigationDrawerItem(
                            label = { AppText("Mali Driver Developers") },
                            selected = ui.screen == ScreenState.DriverDevelopers,
                            icon = { Icon(Icons.Default.Code, contentDescription = null) },
                            onClick = { scope.launch { drawerState.close() }; vm.openDriverDevelopers() },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                        Spacer(Modifier.weight(1f))
                        NavigationDrawerItem(
                            label = { AppText("Sign out") },
                            selected = false,
                            icon = { Icon(Icons.Default.Logout, contentDescription = null) },
                            onClick = { scope.launch { drawerState.close() }; vm.logout() },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        ) {
            Box(Modifier.fillMaxSize().background(G57Bg)) {
                when (val screen = ui.screen) {
                    ScreenState.Login -> LoginScreen(ui, vm)
                    ScreenState.UserHome -> UserHome(ui, vm) { scope.launch { drawerState.open() } }
                    ScreenState.CreateIssue -> CreateIssueScreen(ui, vm)
                    is ScreenState.UserIssue -> IssueDetails(screen.issue, ui, vm, admin = false)
                    ScreenState.AdminHome -> AdminHome(ui, vm) { scope.launch { drawerState.open() } }
                    ScreenState.AdminSolvedUsers -> AdminSolvedUsers(ui, vm)
                    ScreenState.AdminUserManagement -> AdminUserManagementScreen(ui, vm)
                    ScreenState.DriverDevelopers -> DriverDevelopersScreen(vm)
                    ScreenState.Help -> HelpScreen(vm)
                    ScreenState.Settings -> SettingsScreen(ui, vm)
                    is ScreenState.AdminIssue -> IssueDetails(screen.issue, ui, vm, admin = true)
                }
            }
        }
    }
    }
}

@Composable
private fun BrandHeader(subtitle: String? = null, onMenu: (() -> Unit)? = null, onBack: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = G57Card,
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, G57Border)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onMenu != null) {
                IconButton(onClick = onMenu, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Icons.Default.Menu, contentDescription = "Open menu", tint = G57Primary, modifier = Modifier.size(27.dp))
                }
            }
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.padding(end = 6.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = G57Primary)
                }
            }
            Column(Modifier.weight(1f)) {
                AppText("GMailGPU", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = G57Primary)
                if (subtitle != null) AppText(subtitle, color = G57Muted, style = MaterialTheme.typography.bodySmall)
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = G57Primary.copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(1.dp, G57Primary.copy(alpha = 0.35f))
            ) {
                Icon(Icons.Default.Memory, contentDescription = "Mali GPU", tint = G57Primary, modifier = Modifier.padding(10.dp).size(21.dp))
            }
        }
    }
}

@Composable
private fun LoginScreen(ui: UiState, vm: G57ViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize().background(G57Bg)) {
        val compact = maxHeight < 760.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = G57Card,
                border = androidx.compose.foundation.BorderStroke(1.dp, G57Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(if (compact) 18.dp else 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(5.dp).background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(G57Primary, G57Cyan, G57Primary)),
                            RoundedCornerShape(50)
                        )
                    )
                    Image(painterResource(com.g57.issuehub.R.drawable.g57_uploaded_logo), "GMailGPU logo", Modifier.size(if (compact) 76.dp else 96.dp))
                    AppText("GMailGPU", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = G57Primary)
                    AppText("MALI GPU • WINLATOR ISSUE HUB", color = G57Cyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    AppText("G57 · G52 · G615 · G720", color = G57Muted, style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("en" to "English", "ar" to "العربية", "pt-BR" to "PT-BR").forEach { (code, label) ->
                            FilterChip(
                                selected = ui.appLanguage == code,
                                onClick = { vm.setAppLanguage(code) },
                                label = { AppText(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Divider(color = G57Border)
                    AppText("Welcome back", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    AppText("Choose your access type to continue.", color = G57Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoleCard("USER", "Report & track issues", ui.loginRole == "user", { vm.setLoginRole("user") }, Modifier.weight(1f))
            RoleCard("ADMIN", "Developer panel", ui.loginRole == "admin", { vm.setLoginRole("admin") }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            ui.username,
            vm::setUsername,
            label = { AppText("Username") },
            supportingText = { AppText(if (ui.loginRole == "user") "Choose a unique username" else "Admin username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (ui.loginRole == "user") {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !ui.userCreateAccount,
                    onClick = { vm.setUserCreateAccount(false) },
                    label = { AppText("SIGN IN") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = ui.userCreateAccount,
                    onClick = { vm.setUserCreateAccount(true) },
                    label = { AppText("CREATE ACCOUNT") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            ui.password,
            vm::setPassword,
            label = { AppText("Password") },
            supportingText = { AppText(if (ui.loginRole == "user" && ui.userCreateAccount) "Use at least 8 characters. Keep it safe for reinstalling." else "Your account password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = vm::login, enabled = !ui.loading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            AppText(if (ui.loading) "PLEASE WAIT…" else if (ui.loginRole == "user") { if (ui.userCreateAccount) "CREATE ACCOUNT" else "SIGN IN" } else "SIGN IN")
        }
                    ui.error?.let { AppText(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 14.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RoleCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = if (selected) G57Primary.copy(alpha = 0.22f) else G57Card), border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, G57Primary) else null, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (title == "ADMIN") Icons.Default.AdminPanelSettings else Icons.Default.Person, null, tint = if (selected) G57Primary else G57Cyan)
            Spacer(Modifier.height(6.dp))
            AppText(title, fontWeight = FontWeight.Bold)
            AppText(subtitle, color = G57Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun UserHome(ui: UiState, vm: G57ViewModel, onMenu: () -> Unit) {
    var shownNotification by remember(ui.profile?.id, ui.notifications) {
        mutableStateOf(ui.notifications.firstOrNull { it.readAt == null })
    }
    LaunchedEffect(ui.profile?.id) {
        ui.profile?.id?.let(vm::loadUserData)
    }
    Column(Modifier.fillMaxSize()) {
        BrandHeader("Your support workspace", onMenu = onMenu)
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            shape = RoundedCornerShape(24.dp),
            color = G57Primary.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, G57Primary.copy(alpha = 0.35f))
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    AppText("Welcome, ${ui.profile?.username ?: ui.username}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    AppText("Track driver issues and share logs with developers.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                    val unread = ui.notifications.count { it.readAt == null }
                    if (unread > 0) AppText("🔔 $unread unread notification${if (unread == 1) "" else "s"}", color = G57Cyan, fontWeight = FontWeight.SemiBold)
                }
                Surface(shape = RoundedCornerShape(18.dp), color = G57Card) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = G57Primary, modifier = Modifier.padding(14.dp).size(32.dp))
                }
            }
        }
        Button(
            onClick = vm::openCreate,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            AppText("CREATE NEW ISSUE", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        IssueList(ui.issues, vm::openUserIssue, "You have no issues yet.")
    }

    shownNotification?.let { notification ->
        AlertDialog(
            onDismissRequest = {
                vm.markNotificationRead(notification)
                shownNotification = null
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.markNotificationRead(notification)
                    shownNotification = null
                }) { AppText("OK") }
            },
            title = { AppText("GMailGPU • Problem Resolved ✅") },
            text = { AppText("${notification.title}\n\n${notification.message}") }
        )
    }
}

@Composable
private fun AdminHome(ui: UiState, vm: G57ViewModel, onMenu: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadIssues(true) }
    Column(Modifier.fillMaxSize()) {
        BrandHeader("Developer control center", onMenu = onMenu)
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            shape = RoundedCornerShape(24.dp),
            color = G57Card,
            border = androidx.compose.foundation.BorderStroke(1.dp, G57Border)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AppText("Issue operations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        AppText("Review evidence, download user files, and reply to reports.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = G57Primary, modifier = Modifier.size(32.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::openUserManagement, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ManageAccounts, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        AppText("USERS")
                    }
                    OutlinedButton(onClick = vm::openSolvedUsers, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Verified, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        AppText("RESOLVED")
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("OPEN", ui.issues.count { it.status == "open" })
                    StatChip("ACTIVE", ui.issues.count { it.status == "investigating" || it.status == "fix_in_progress" })
                    StatChip("TESTING", ui.issues.count { it.status == "testing" })
                    StatChip("FIXED", ui.issues.count { it.status == "fixed" || it.status == "closed" })
                }
            }
        }
        IssueList(ui.issues, vm::openAdminIssue, "No issues in Cloud.")
    }
}

@Composable
private fun HelpScreen(vm: G57ViewModel) {
    Column(Modifier.fillMaxSize()) {
        BrandHeader("Need Help? Follow These Steps • How To Use App") { vm.back() }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HelpStep("1. Create your account", "Choose USER, enter a unique username and a password with at least 8 characters, then tap CREATE ACCOUNT. Use SIGN IN for an existing account.")
            HelpStep("2. Open a new issue", "Tap CREATE ISSUE and enter the game, issue title, and a clear description. Write the title and description in English.")
            HelpStep("3. Find Winlator logs", "Open your phone's Files app, choose Internal storage, then open winlator and logs. The common full path is /storage/emulated/0/winlator/logs. If your Winlator setup stores logs elsewhere, choose its actual logs folder.")
            LogFolderIllustration()
            HelpStep("4. Attach logs or a video", "In CREATE ISSUE, tap Logs, browse to the logs folder shown above, select the newest relevant .log or .txt file, then tap Open/Use this file. You can attach a video instead. Screenshots alone are not enough.")
            LogUploadIllustration()
            HelpStep("5. Submit and track", "Tap SUBMIT ISSUE and wait for confirmation. When device diagnostics are enabled, GMailGPU also attaches a detailed device report automatically.")
            HelpStep("6. Talk to the developer", "Open your issue, scroll to the chat section, write a message, and tap SEND MESSAGE. Tap REFRESH CHAT to check for replies.")
            HelpStep("7. Add missing files later", "Inside your issue, use ADD MORE FILES and UPLOAD FILES TO THIS ISSUE to send extra logs, videos, or screenshots.")
            HelpStep("8. Protect your account", "Keep your password safe. Use Sign out from the side menu when you want to end your session.")
            HelpStep("5. Talk to the developer", "Open your issue, scroll to the chat section, write a message, and tap SEND MESSAGE. Tap REFRESH CHAT to check for replies.")
            HelpStep("6. Add missing files later", "Inside your issue, use ADD MORE FILES and UPLOAD FILES TO THIS ISSUE to send extra logs, videos, or screenshots.")
            HelpStep("7. Protect your account", "Keep your password safe. Use Sign out from the side menu when you want to end your session.")
            Button(onClick = { vm.openCreate() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                AppText("CREATE ISSUE")
            }
        }
    }
}

@Composable
private fun LogFolderIllustration() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = G57Card),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = G57Cyan, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    AppText("FILE MANAGER • FOLDER GUIDE", color = G57Cyan, fontWeight = FontWeight.Bold)
                    AppText("Example location — your phone may differ", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Surface(color = G57Bg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Smartphone, contentDescription = null, tint = G57Muted)
                        Spacer(Modifier.width(8.dp))
                        AppText("Internal storage", fontWeight = FontWeight.SemiBold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = G57Muted)
                        Icon(Icons.Default.Folder, contentDescription = null, tint = G57Cyan)
                        Spacer(Modifier.width(8.dp))
                        AppText("winlator")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(24.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = G57Muted)
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = G57Primary)
                        Spacer(Modifier.width(8.dp))
                        AppText("logs", fontWeight = FontWeight.Bold, color = G57Primary)
                    }
                    Divider(color = G57Border)
                    AppText("/storage/emulated/0/winlator/logs", color = G57Cyan, style = MaterialTheme.typography.bodySmall)
                }
            }
            AppText("Open this folder in the Android file picker, select a .log or .txt file, and confirm. If the folder is missing, run the game once in Winlator or use Winlator's own log-export option.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LogUploadIllustration() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = G57Card),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, contentDescription = null, tint = G57Cyan, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                AppText("WHAT TO TAP", color = G57Cyan, fontWeight = FontWeight.Bold)
            }
            listOf(
                "1" to ("Logs" to "Choose a .log or .txt file"),
                "2" to ("Open / Use this file" to "Confirm the selection"),
                "3" to ("SUBMIT ISSUE" to "Wait until upload completes")
            ).forEach { (number, pair) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = G57Primary, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(34.dp)) {
                        Box(contentAlignment = Alignment.Center) { AppText(number, color = Color(0xFF080A0F), fontWeight = FontWeight.ExtraBold) }
                    }
                    Column(Modifier.weight(1f)) {
                        AppText(pair.first, fontWeight = FontWeight.SemiBold)
                        AppText(pair.second, color = G57Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = G57Cyan)
                }
            }
        }
    }
}

@Composable
private fun HelpStep(title: String, body: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = G57Card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppText(title, color = G57Cyan, fontWeight = FontWeight.Bold)
            AppText(body, color = G57Text)
        }
    }
}

@Composable
private fun SettingsScreen(ui: UiState, vm: G57ViewModel) {
    Column(Modifier.fillMaxSize()) {
        BrandHeader("Settings") { vm.back() }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppText("APP LANGUAGE", color = G57Cyan, fontWeight = FontWeight.Bold)
            AppText("Choose English, Arabic, or Brazilian Portuguese. This preference is saved on this device and controls the automatic chat-translation target.", color = G57Muted)
            listOf("en" to "English", "ar" to "العربية", "pt-BR" to "Português (Brasil)").forEach { (code, label) ->
                Card(
                    onClick = { vm.setAppLanguage(code) },
                    colors = CardDefaults.cardColors(containerColor = G57Card),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (ui.appLanguage == code) 2.dp else 1.dp,
                        if (ui.appLanguage == code) G57Primary else G57Border
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppText(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        if (ui.appLanguage == code) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = G57Primary)
                    }
                }
            }
            Divider(color = G57Border)
            AppText("APP APPEARANCE", color = G57Cyan, fontWeight = FontWeight.Bold)
            AppText("Choose a complete app theme. Backgrounds, cards, text, borders, and accent colors change together. Your choice is saved on this device.", color = G57Muted)
            listOf(
                "Violet" to Color(0xFF9B7BFF),
                "Ocean" to Color(0xFF48B8E8),
                "Emerald" to Color(0xFF4ED6A0),
                "Amber" to Color(0xFFFFBD59),
                "Rose" to Color(0xFFFF78AC),
                "Light" to Color(0xFF6750A4)
            ).forEach { (name, color) ->
                Card(
                    onClick = { vm.setThemeChoice(name) },
                    colors = CardDefaults.cardColors(containerColor = G57Card),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (ui.themeChoice == name) 2.dp else 1.dp,
                        if (ui.themeChoice == name) color else G57Border
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = color, shape = RoundedCornerShape(50), modifier = Modifier.size(24.dp)) {}
                        Spacer(Modifier.width(12.dp))
                        AppText(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        if (ui.themeChoice == name) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = color)
                    }
                }
            }
            AppText("Theme changes apply across the whole app immediately and remain saved after restarting.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
            Divider(color = G57Border)
            AppText("PRIVACY & FILE CONTROLS", color = G57Cyan, fontWeight = FontWeight.Bold)
            AppText("These preferences stay on this device. Nothing is uploaded until you attach a file to an issue and submit it.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AppText("Include device details in reports", fontWeight = FontWeight.SemiBold)
                    AppText("Model, Android version, SoC and available memory when Android exposes them.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                }
                Checkbox(checked = ui.includeDeviceDiagnostics, onCheckedChange = vm::setIncludeDeviceDiagnostics)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AppText("Allow app-process logcat collection", fontWeight = FontWeight.SemiBold)
                    AppText("Off by default. Only this app process is requested; Android may restrict access.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                }
                Checkbox(checked = ui.collectAppLogsEnabled, onCheckedChange = vm::setCollectAppLogsEnabled)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AppText("Redact likely sensitive data", fontWeight = FontWeight.SemiBold)
                    AppText("Masks common token/password fields and email addresses in collected logs.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                }
                Checkbox(checked = ui.redactSensitiveLogs, onCheckedChange = vm::setRedactSensitiveLogs)
            }
            AppText("Review every report or log file before uploading. Redaction is best-effort and may not catch every secret.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                AppText("SIGN OUT")
            }
        }
    }
}

@Composable
private fun AdminSolvedUsers(ui: UiState, vm: G57ViewModel) {
    LaunchedEffect(Unit) { vm.loadSolvedUsers() }
    Column(Modifier.fillMaxSize()) {
        BrandHeader("USERS WITH RESOLVED ISSUES") { vm.back() }
        if (ui.resolvedUsers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppText("No resolved users yet.", color = G57Muted) }
        } else {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ui.resolvedUsers) { user ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = G57Card)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF63E6BE))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                AppText(user.username, fontWeight = FontWeight.Bold)
                                AppText("${user.issueCount} resolved issue(s) • latest #${user.latestIssueNumber}", color = G57Muted)
                                AppText(user.latestTitle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverDevelopersScreen(vm: G57ViewModel) {
    val repositories = listOf(
        Triple("G57", "Noysz / panvk-g99-jm", "https://github.com/Noysz/panvk-g99-jm"),
        Triple("G57", "mexicanbr0auth / mesa-panvk-g57", "https://github.com/mexicanbr0auth/mesa-panvk-g57"),
        Triple("G57", "FristOneRR / FristOneRR-Panvk-Driver", "https://github.com/FristOneRR/FristOneRR-Panvk-Driver"),
        Triple("G52", "LukeValen / panvk-mali-g52", "https://github.com/LukeValen/panvk-mali-g52"),
        Triple("G615", "GunaCharanTeja / panvk-kbase-android", "https://github.com/GunaCharanTeja/panvk-kbase-android"),
        Triple("G720", "wonderkast02 / panvk-g720-kbase-csf", "https://github.com/wonderkast02/panvk-g720-kbase-csf")
    )
    Column(Modifier.fillMaxSize()) {
        BrandHeader("GMailGPU • Mali Driver Developers") { vm.back() }
        AppText(
            "🤝 These open-source driver projects may help you investigate Mali GPU, Vulkan, and Winlator issues.",
            color = G57Muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(repositories) { (gpu, name, url) ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = G57Card),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = G57Cyan, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { AppText("GPU • $gpu", color = G57Cyan, style = MaterialTheme.typography.labelMedium); AppText(name, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.height(8.dp))
                        AppText(url, color = G57Muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.openExternalUrl(url) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            AppText("OPEN GITHUB")
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { vm.openExternalUrl("https://github.com/GunaCharanTeja/WinlatorMali") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                AppText("Winlator Download Charan")
            }
            Spacer(Modifier.height(12.dp))
            AppText(
                "Contact Us on Telegram",
                color = G57Cyan,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            AppText(
                "Jin woo { PanVK tester }",
                color = G57Primary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun AdminUserManagementScreen(ui: UiState, vm: G57ViewModel) {
    var selectedUser by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(Unit) { vm.loadUsers(); vm.loadIssues(true) }
    Column(Modifier.fillMaxSize()) {
        BrandHeader("USER MANAGEMENT") { vm.back() }
        AppText(
            "Registered profiles • deleting a user removes their linked issues and notifications.",
            color = G57Muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        ui.success?.let { AppText(it, color = G57Cyan, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
        ui.error?.let { AppText(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
        if (ui.loading && ui.userProfiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (ui.userProfiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppText("No profiles found.", color = G57Muted) }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ui.userProfiles, key = { it.id }) { profile ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = G57Card),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (profile.role == "admin") Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (profile.role == "admin") G57Primary else G57Cyan
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    AppText(profile.username, fontWeight = FontWeight.Bold)
                                    AppText("Role: ${profile.role}", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                                    AppText("Issues: ${ui.issues.count { it.userId == profile.id }}", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                                    profile.createdAt?.let { AppText("Created: ${it.take(10)}", color = G57Muted, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                            if (profile.role != "admin" && profile.id != ui.profile?.id) {
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { selectedUser = profile },
                                    enabled = !ui.loading,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    AppText("DELETE USER")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selectedUser?.let { profile ->
        AlertDialog(
            onDismissRequest = { selectedUser = null },
            title = { AppText("Delete ${profile.username}?") },
            text = { AppText("This permanently deletes the Auth account, profile, issues, attachments metadata and notifications. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { selectedUser = null; vm.deleteUser(profile) }, enabled = !ui.loading) {
                    AppText("DELETE", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { selectedUser = null }) { AppText("CANCEL") } }
        )
    }
}

@Composable
private fun StatChip(name: String, count: Int) {
    Surface(shape = RoundedCornerShape(14.dp), color = G57Card) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
            AppText(count.toString(), fontWeight = FontWeight.Bold)
            AppText(name, style = MaterialTheme.typography.labelSmall, color = G57Muted)
        }
    }
}

@Composable
private fun IssueList(issues: List<Issue>, onClick: (Issue) -> Unit, emptyText: String) {
    var selectedGroup by remember { mutableStateOf("All") }
    val groups = listOf(
        "All" to issues.size,
        "Opened" to issues.count { it.status == "open" },
        "Active" to issues.count { it.status in listOf("investigating", "fix_in_progress", "testing") },
        "Fixed" to issues.count { it.status == "fixed" },
        "Closed" to issues.count { it.status == "closed" }
    )
    val filtered = remember(issues, selectedGroup) {
        val matching = when (selectedGroup) {
            "Opened" -> issues.filter { it.status == "open" }
            "Active" -> issues.filter { it.status in listOf("investigating", "fix_in_progress", "testing") }
            "Fixed" -> issues.filter { it.status == "fixed" }
            "Closed" -> issues.filter { it.status == "closed" }
            else -> issues
        }
        matching.sortedWith(compareByDescending<Issue> { it.issueNumber })
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groups.forEach { (name, count) ->
                FilterChip(
                    selected = selectedGroup == name,
                    onClick = { selectedGroup = name },
                    label = { AppText("$name ($count)") },
                    leadingIcon = if (selectedGroup == name) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppText(
                    if (issues.isEmpty()) emptyText else "No issues in ${selectedGroup.lowercase()} yet.",
                    color = G57Muted
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.id }) { issue -> IssueCard(issue, onClick) }
            }
        }
    }
}

@Composable
private fun IssueCard(issue: Issue, onClick: (Issue) -> Unit) {
    ElevatedCard(
        onClick = { onClick(issue) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = G57Card),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(G57Primary))
            Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = G57Primary.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                        AppText("#${issue.issueNumber}", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = G57Primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(issue.status)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = G57Muted)
                }
                AppText(issue.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                AppText("${issue.game} • ${issue.problemType}", color = G57Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = G57Cyan, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    AppText("${issue.driver} • ${issue.emulator}", color = G57Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                if (issue.userUsername != null) AppText("Reported by ${issue.userUsername}", color = G57Cyan, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val label = status.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }
    AssistChip(onClick = {}, label = { AppText(label) }, leadingIcon = { Icon(Icons.Default.Circle, null, Modifier.size(8.dp)) })
}

@Composable
private fun CreateIssueScreen(ui: UiState, vm: G57ViewModel) {
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(readPickedFiles(uris, "image", context))
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(readPickedFiles(uris, "video", context))
    }
    val logPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(readPickedFiles(uris, "log", context))
    }

    Column(Modifier.fillMaxSize()) {
        BrandHeader("Create an issue") { vm.back() }
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppText("PROBLEM", color = G57Cyan, fontWeight = FontWeight.Bold)
            OutlinedTextField(ui.title, { vm.setField("title", it) }, label = { AppText("Issue title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ui.game, { vm.setField("game", it) }, label = { AppText("Game") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ui.gameVersion, { vm.setField("gameVersion", it) }, label = { AppText("Game version (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ui.description, { vm.setField("description", it) }, label = { AppText("Describe the problem (English only)") }, supportingText = { AppText("Write the steps, exact error message, and expected result in English.") }, minLines = 6, modifier = Modifier.fillMaxWidth())
            AppText("Problem type", color = G57Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp)) {
                listOf("Crash", "Black Screen", "Graphics", "Performance", "Vulkan Error", "Other").forEach { type ->
                    FilterChip(selected = ui.problemType == type, onClick = { vm.setProblemType(type) }, label = { AppText(type) })
                }
            }
            AppText("ENVIRONMENT", color = G57Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(ui.gpu, { vm.setField("gpu", it) }, label = { AppText("GPU model (verify auto-detection)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ui.soc, { vm.setField("soc", it) }, label = { AppText("SoC / chipset") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ui.driver, { vm.setField("driver", it) }, label = { AppText("Driver") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ui.emulator, { vm.setField("emulator", it) }, label = { AppText("Emulator") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ui.emulatorVersion, { vm.setField("emulatorVersion", it) }, label = { AppText("Emulator version") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ui.dxvk, { vm.setField("dxvk", it) }, label = { AppText("DXVK") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ui.wine, { vm.setField("wine", it) }, label = { AppText("Wine") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ui.proton, { vm.setField("proton", it) }, label = { AppText("Proton") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ui.vkd3d, { vm.setField("vkd3d", it) }, label = { AppText("VKD3D") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ui.box64, { vm.setField("box64", it) }, label = { AppText("Box64") }, modifier = Modifier.fillMaxWidth())

            AppText("ATTACHMENTS • REQUIRED", color = G57Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            AppText(
                "You must attach at least one diagnostic log file OR one video showing the problem. Images alone are not enough. Please write all issue details in English. Maximum 150 MB per file.",
                color = G57Muted,
                style = MaterialTheme.typography.bodySmall
            )
            val hasRequiredEvidence = ui.selectedFiles.any { it.type == "log" || it.type == "video" }
            AppText(
                if (hasRequiredEvidence) "Required evidence selected." else "Missing required evidence: choose a log file or a video.",
                color = if (hasRequiredEvidence) G57Cyan else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { AppText("📷 Images") }
                OutlinedButton(onClick = { videoPicker.launch(arrayOf("video/*")) }, modifier = Modifier.weight(1f)) { AppText("🎥 Video") }
                OutlinedButton(onClick = { logPicker.launch(arrayOf("text/*", "application/zip", "application/octet-stream", "application/json")) }, modifier = Modifier.weight(1f)) { AppText("📄 Logs") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = vm::generateDiagnosticReport, enabled = !ui.loading, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Assignment, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    AppText("SMART REPORT")
                }
                OutlinedButton(onClick = vm::collectAppLogs, enabled = !ui.loading, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    AppText("COLLECT LOGS")
                }
            }
            AppText("Device diagnostics are attached automatically at submission when enabled in Settings. To add Winlator logs, open Files → Internal storage → winlator → logs, then select a .log or .txt file. Common path: /storage/emulated/0/winlator/logs. Generated files can also be added here for review.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
            LogFolderIllustration()
            if (ui.selectedFiles.isNotEmpty()) {
                OutlinedButton(onClick = vm::clearSelectedFiles, enabled = !ui.loading, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    AppText("CLEAR ALL SELECTED FILES")
                }
            }
            ui.selectedFiles.forEachIndexed { index, file ->
                Surface(color = G57Card, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppText(file.name, Modifier.weight(1f), maxLines = 1)
                        AppText(formatBytes(file.size), color = G57Muted, style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { vm.removeFile(index) }) { Icon(Icons.Default.Close, contentDescription = "Remove") }
                    }
                }
            }
            ui.uploadProgress?.let { AppText(it, color = G57Cyan, style = MaterialTheme.typography.bodySmall) }
            ui.success?.let { AppText(it, color = G57Cyan) }
            ui.error?.let { AppText(it, color = MaterialTheme.colorScheme.error) }
            Button(vm::submitIssue, enabled = !ui.loading, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) { AppText(if (ui.loading) "UPLOADING…" else "SUBMIT ISSUE") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun IssueDetails(issue: Issue, ui: UiState, vm: G57ViewModel, admin: Boolean) {
    var showDelete by remember { mutableStateOf(false) }
    var note by remember(issue.id) { mutableStateOf(issue.developerNote.orEmpty()) }
    var statusExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(readPickedFiles(uris, "image", context))
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(readPickedFiles(uris, "video", context))
    }
    val logPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(readPickedFiles(uris, "log", context))
    }
    Column(Modifier.fillMaxSize()) {
        BrandHeader(if (admin) "ADMIN • Issue #${issue.issueNumber}" else "Issue #${issue.issueNumber}") { vm.back() }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(issue.status)
                Spacer(Modifier.weight(1f))
            }
            AppText(issue.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(
                onClick = { showDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7A2430),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                AppText(if (admin) "DELETE ISSUE (ADMIN)" else "DELETE MY ISSUE")
            }
            AppText(issue.description)
            if (admin) Detail("User", issue.userUsername ?: issue.userId ?: "Unknown")
            Detail("Game", "${issue.game} ${issue.gameVersion.orEmpty()}")
            Detail("Problem", issue.problemType)
            Detail("Driver", issue.driver)
            Detail("Emulator", "${issue.emulator} ${issue.emulatorVersion.orEmpty()}")
            Detail("DXVK", issue.dxvk ?: "None")
            Detail("Wine", issue.wine ?: "None")
            Detail("Proton", issue.proton ?: "None")
            Detail("VKD3D", issue.vkd3d ?: "None")
            Detail("Box64", issue.box64 ?: "None")
            Detail("GPU", issue.gpu ?: "Unknown")
            Detail("SoC", issue.soc ?: "Unknown")
            Detail("Device", issue.deviceModel ?: "Unknown")
            Detail("Android", issue.androidVersion ?: "Unknown")

            AppText("Attachments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            if (ui.attachments.isEmpty()) {
                AppText("No attachments.", color = G57Muted)
            } else {
                ui.attachments.forEach { attachment ->
                    AttachmentRow(
                        attachment = attachment,
                        onOpen = { vm.openAttachment(attachment) },
                        onDownload = if (admin) ({ vm.downloadAttachment(attachment) }) else null
                    )
                }
            }

            if (!admin) {
                AppText("ADD MORE FILES", color = G57Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                AppText("Forgot a screenshot, video, or log? Add it here without creating another issue. Maximum 150 MB per file.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { AppText("📷 Images") }
                    OutlinedButton(onClick = { videoPicker.launch(arrayOf("video/*")) }, modifier = Modifier.weight(1f)) { AppText("🎥 Video") }
                    OutlinedButton(onClick = { logPicker.launch(arrayOf("text/*", "application/zip", "application/octet-stream", "application/json")) }, modifier = Modifier.weight(1f)) { AppText("📄 Logs") }
                }
                ui.selectedFiles.forEachIndexed { index, file ->
                    Surface(color = G57Card, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppText(file.name, Modifier.weight(1f), maxLines = 1)
                            AppText(formatBytes(file.size), color = G57Muted, style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { vm.removeFile(index) }, enabled = !ui.loading) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }
                Button(
                    onClick = { vm.uploadAdditionalFiles(issue.id) },
                    enabled = !ui.loading && ui.selectedFiles.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    AppText(if (ui.loading) "UPLOADING…" else "UPLOAD FILES TO THIS ISSUE")
                }
                ui.uploadProgress?.let { AppText(it, color = G57Cyan, style = MaterialTheme.typography.bodySmall) }
                ui.success?.let { AppText(it, color = G57Cyan) }
                ui.error?.let { AppText(it, color = MaterialTheme.colorScheme.error) }
            }

            AppText(
                if (admin) "ISSUE CHAT • USER" else "CHAT WITH DEVELOPER",
                color = G57Cyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp)
            )
            AppText(
                "Messages are saved to this issue. Only the issue owner and admins can read or reply.",
                color = G57Muted,
                style = MaterialTheme.typography.bodySmall
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { vm.refreshIssueMessages(issue.id) }, enabled = !ui.loading) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    AppText("REFRESH CHAT")
                }
            }
            if (ui.chatMessages.isEmpty()) {
                AppText("No messages yet. Start the conversation about this issue.", color = G57Muted)
            } else {
                ui.chatMessages.forEach { message ->
                    val isMine = message.senderId == ui.profile?.id
                    Surface(
                        color = if (isMine) G57Primary.copy(alpha = 0.18f) else G57Card,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            AppText(
                                when {
                                    isMine -> "You"
                                    admin -> issue.userUsername ?: "User"
                                    else -> "Developer"
                                },
                                color = if (isMine) G57Cyan else G57Primary,
                                fontWeight = FontWeight.Bold
                            )
                            AutoTranslatedMessage(message.body, ui.appLanguage)
                            message.createdAt?.let {
                                AppText(it.replace("T", " ").take(16), color = G57Muted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = ui.chatDraft,
                onValueChange = vm::setChatDraft,
                label = { AppText("Write a message") },
                placeholder = { AppText("Describe the next step or share a test result…") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                enabled = !ui.loading
            )
            Button(
                onClick = { vm.sendIssueMessage(issue.id) },
                enabled = !ui.loading && ui.chatDraft.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                AppText(if (ui.loading) "SENDING…" else "SEND MESSAGE")
            }
            ui.error?.let { AppText(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            ui.success?.let { AppText(it, color = G57Cyan, style = MaterialTheme.typography.bodySmall) }

            if (admin) {
                AppText("DEVELOPER TOOLS", color = G57Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                Box {
                    OutlinedButton(onClick = { statusExpanded = true }, modifier = Modifier.fillMaxWidth()) { AppText("Status: ${issue.status.replace('_', ' ')} ▼") }
                    DropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                        listOf("open", "investigating", "fix_in_progress", "testing", "fixed", "closed").forEach { status ->
                            DropdownMenuItem(text = { AppText(status.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }) }, onClick = {
                                statusExpanded = false
                                vm.updateStatus(issue.id, status)
                            })
                        }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { AppText("Developer note") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.saveDeveloperNote(issue.id, note) }, modifier = Modifier.fillMaxWidth()) { AppText("SAVE DEVELOPER NOTE") }
                AppText("Setting status to Fixed/Closed creates an in-app notification for the user and adds them to Solved Users.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
            }

            if (!admin && issue.status in listOf("fixed", "closed")) {
                Surface(color = Color(0xFF153322), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF63E6BE))
                        Spacer(Modifier.width(10.dp))
                        AppText("The developer marked this issue as resolved.")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (ui.attachmentPreviewText != null) {
        AlertDialog(
            onDismissRequest = vm::closeAttachmentPreview,
            title = { AppText(ui.attachmentPreviewName ?: "Log preview", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    AppText(
                        ui.attachmentPreviewText.orEmpty(),
                        color = G57Text,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = vm::closeAttachmentPreview) { AppText("CLOSE") }
            },
            dismissButton = {
                TextButton(onClick = vm::openAttachmentExternally) { AppText("OPEN FILE") }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            confirmButton = {
                TextButton(onClick = { showDelete = false; vm.deleteIssue(issue, admin) }) { AppText("DELETE", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { AppText("CANCEL") } },
            title = { AppText("Delete Issue #${issue.issueNumber}?") },
            text = { AppText("This will delete the Issue record and its Cloud attachments. This action cannot be undone.") }
        )
    }
}

@Composable
private fun AttachmentRow(
    attachment: Attachment,
    onOpen: () -> Unit,
    onDownload: (() -> Unit)? = null
) {
    ElevatedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = G57Card),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = when (attachment.type) {
                "image" -> Icons.Default.Image
                "video" -> Icons.Default.VideoFile
                else -> Icons.Default.Description
            }
            Surface(shape = RoundedCornerShape(12.dp), color = G57Primary.copy(alpha = 0.13f)) {
                Icon(icon, contentDescription = null, tint = G57Primary, modifier = Modifier.padding(10.dp).size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AppText(attachment.filename, maxLines = 2, fontWeight = FontWeight.SemiBold)
                AppText("${attachment.type.uppercase(Locale.getDefault())} • ${formatBytes(attachment.size ?: 0)}", color = G57Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (onDownload != null) {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Download attachment", tint = G57Cyan)
                }
            }
            Icon(Icons.Default.OpenInNew, contentDescription = "Open attachment", tint = G57Muted)
        }
    }
}

@Composable
private fun Detail(name: String, value: String) {
    Surface(color = G57Card, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp)) { AppText(name, Modifier.weight(0.35f), color = G57Cyan, fontWeight = FontWeight.Bold); AppText(value, Modifier.weight(0.65f)) }
    }
}

private fun readPickedFiles(uris: List<android.net.Uri>, type: String, context: android.content.Context): List<PickedFile> {
    val result = mutableListOf<PickedFile>()
    uris.forEach { uri ->
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        var name = "file"
        var size = 0L
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        if (size <= 150L * 1024L * 1024L) {
            result += PickedFile(uri, name, mime, type, size)
        } else {
            android.widget.Toast.makeText(context, "$name exceeds the 150 MB file limit and was not added.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    return result
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(Locale.US, bytes / 1024f / 1024f)
    bytes >= 1024L -> "%.0f KB".format(Locale.US, bytes / 1024f)
    else -> "$bytes B"
}
