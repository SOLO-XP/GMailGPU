package com.g57.issuehub

import android.content.Intent
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.WindowManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.g57.issuehub.data.Attachment
import com.g57.issuehub.data.G57Repository
import com.g57.issuehub.data.SupabaseProvider
import com.g57.issuehub.data.Issue
import com.g57.issuehub.data.Notification
import com.g57.issuehub.data.UserPresence
import com.g57.issuehub.ui.*
import java.util.Locale


@Composable
internal fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    // Preserve the original parameter order so existing positional calls remain source-compatible.
    fontWeight: FontWeight? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    softWrap: Boolean = true,
    minLines: Int = 1,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {}
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
        color = if (color == Color.Unspecified) G57Text else color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

class MainActivity : ComponentActivity() {
    private var pendingNotificationIssueId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GMailGpuPush.createChannel(this)
        pendingNotificationIssueId = readIssueId(intent)
        setContent {
            G57App(
                pendingNotificationIssueId = pendingNotificationIssueId,
                onNotificationConsumed = { pendingNotificationIssueId = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationIssueId = readIssueId(intent)
    }

    private fun readIssueId(intent: Intent?): String? =
        intent?.getStringExtra(GMailGpuPush.EXTRA_ISSUE_ID)
            ?: intent?.getStringExtra("issue_id")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
    // Default: a low-glare graphite / midnight dashboard with restrained neon accents.
    "Violet" -> G57Palette(Color(0xFF0B0D17), Color(0xFF151827), Color(0xFF9B7BFF), Color(0xFF52D7F5), Color(0xFFF0F1FA), Color(0xFFA4A8BE), Color(0xFF30344A))
    "Ocean" -> G57Palette(Color(0xFF07141D), Color(0xFF102431), Color(0xFF48B8E8), Color(0xFF8BE5FF), Color(0xFFEAF9FF), Color(0xFF91B6C7), Color(0xFF245064))
    "Emerald" -> G57Palette(Color(0xFF091711), Color(0xFF14271F), Color(0xFF4ED6A0), Color(0xFF9AF5CB), Color(0xFFE9FFF5), Color(0xFF96BDAA), Color(0xFF285641))
    "Amber" -> G57Palette(Color(0xFF19130B), Color(0xFF292116), Color(0xFFFFBD59), Color(0xFFFFDE9A), Color(0xFFFFF7E8), Color(0xFFC6B28F), Color(0xFF5B4528))
    "Rose" -> G57Palette(Color(0xFF1A0D15), Color(0xFF2A1722), Color(0xFFFF78AC), Color(0xFFFFB6D0), Color(0xFFFFF0F6), Color(0xFFC8A0B1), Color(0xFF633047))
    "Light" -> G57Palette(Color(0xFFF0F2F8), Color(0xFFFFFFFF), Color(0xFF6548D7), Color(0xFF007F9F), Color(0xFF202235), Color(0xFF626B80), Color(0xFFD6DBE8), light = true)
    else -> G57Palette(Color(0xFF0B0D17), Color(0xFF151827), Color(0xFF9B7BFF), Color(0xFF52D7F5), Color(0xFFF0F1FA), Color(0xFFA4A8BE), Color(0xFF30344A))
}

@Composable
fun G57App(
    vm: G57ViewModel = viewModel(),
    pendingNotificationIssueId: String? = null,
    onNotificationConsumed: () -> Unit = {}
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val deviceLanguage = configuration.locales[0]?.language?.lowercase(Locale.ROOT)
        ?: Locale.getDefault().language.lowercase(Locale.ROOT)
    val resolvedLanguage = remember(ui.appLanguage, deviceLanguage) {
        resolveAppLanguage(ui.appLanguage, deviceLanguage)
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val lifecycleOwner = context.findActivity() as? LifecycleOwner
    var activityStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> activityStarted = true
                Lifecycle.Event.ON_STOP -> activityStarted = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(ui.profile?.id) {
        if (ui.profile == null) return@LaunchedEffect
        GMailGpuPush.createChannel(context)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        GMailGpuPush.tokenOrNull(context)?.let { vm.registerPushToken(it) }
    }

    LaunchedEffect(ui.profile?.id, activityStarted) {
        if (ui.profile == null || !SupabaseProvider.enabled) return@LaunchedEffect
        if (!activityStarted) {
            vm.setPresenceOnline(false)
            return@LaunchedEffect
        }
        vm.setPresenceOnline(true)
        vm.refreshChatSignals()
        while (true) {
            delay(20_000)
            vm.setPresenceOnline(true)
            vm.refreshChatSignals()
        }
    }

    LaunchedEffect(pendingNotificationIssueId, ui.profile?.id) {
        val issueId = pendingNotificationIssueId ?: return@LaunchedEffect
        if (ui.profile == null) return@LaunchedEffect
        vm.openIssueFromNotification(issueId)
        onNotificationConsumed()
    }

    val activeChatIssueId = when (val currentScreen = ui.screen) {
        is ScreenState.UserIssue -> currentScreen.issue.id
        is ScreenState.AdminIssue -> currentScreen.issue.id
        else -> null
    }
    LaunchedEffect(activeChatIssueId, activityStarted) {
        val issueId = activeChatIssueId ?: return@LaunchedEffect
        if (!activityStarted) return@LaunchedEffect
        vm.refreshIssueMessages(issueId)
        while (true) {
            delay(5_000)
            vm.refreshIssueMessages(issueId)
        }
    }

    // Hide only screens that reveal registered/resolved usernames. Other app screens
    // continue to allow screenshots, including screenshots of images and issue evidence.
    DisposableEffect(context, ui.screen) {
        val window = context.findActivity()?.window
        val sensitiveScreen = ui.screen == ScreenState.AdminUserManagement ||
            ui.screen == ScreenState.AdminSolvedUsers
        if (sensitiveScreen) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(ui.appLanguage, deviceLanguage, resolvedLanguage) {
        // Re-run whenever the user changes the language or Android's device locale changes.
        val target = when (resolvedLanguage) {
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
        LocalAppLanguage provides resolvedLanguage,
        LocalLayoutDirection provides if (resolvedLanguage == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
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
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp)
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
                            label = { AppText("Game Compatibility Library") },
                            selected = ui.screen == ScreenState.GameCompatibility,
                            icon = { Icon(Icons.Default.SportsEsports, contentDescription = null) },
                            onClick = { scope.launch { drawerState.close() }; vm.openGameCompatibility() },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                        NavigationDrawerItem(
                            label = { AppText("Community Game Lab") },
                            selected = ui.screen == ScreenState.GameLab,
                            icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                            onClick = { scope.launch { drawerState.close() }; vm.openGameLab() },
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
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(G57Primary.copy(alpha = 0.07f), G57Bg, G57Bg)))) {
                when (val screen = ui.screen) {
                    ScreenState.Login -> LoginScreen(ui, vm)
                    ScreenState.UserHome -> UserHome(ui, vm) { scope.launch { drawerState.open() } }
                    ScreenState.CreateIssue -> CreateIssueScreen(ui, vm)
                    is ScreenState.UserIssue -> IssueDetails(screen.issue, ui, vm, admin = false)
                    ScreenState.AdminHome -> AdminHome(ui, vm) { scope.launch { drawerState.open() } }
                    ScreenState.AdminSolvedUsers -> AdminSolvedUsers(ui, vm)
                    ScreenState.AdminUserManagement -> AdminUserManagementScreen(ui, vm)
                    ScreenState.DriverDevelopers -> DriverDevelopersScreen(vm)
                    ScreenState.GameCompatibility -> GameCompatibilityScreen { vm.back() }
                    ScreenState.GameLab -> GameLabScreen(
                        onBack = { vm.back() },
                        onOpenGameLibrary = { vm.openGameCompatibility() },
                        onCreateIssue = { title, game, type, description ->
                            vm.openCreate()
                            vm.setField("title", title)
                            vm.setField("game", game)
                            vm.setProblemType(type)
                            vm.setField("description", description)
                        }
                    )
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
    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(G57Primary.copy(alpha = 0.24f), G57Bg, G57Cyan.copy(alpha = 0.10f))
            )
        )
    ) {
        val compact = maxHeight < 760.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = G57Primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, G57Primary.copy(alpha = 0.30f))
            ) {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = if (compact) 14.dp else 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painterResource(com.g57.issuehub.R.drawable.g57_uploaded_logo),
                        "GMailGPU logo",
                        Modifier.size(if (compact) 76.dp else 104.dp)
                    )
                    AppText("GMailGPU", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = G57Primary)
                    AppText("YOUR MALI GPU SUPPORT HUB", color = G57Cyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("G57", "G52", "G615", "G720").forEach { gpu ->
                            Surface(
                                color = G57Card,
                                shape = RoundedCornerShape(50),
                                border = androidx.compose.foundation.BorderStroke(1.dp, G57Border)
                            ) {
                                AppText(gpu, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = G57Text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = G57Card,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, G57Border)
            ) {
                Column(
                    Modifier.padding(if (compact) 18.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppText("Welcome back", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = G57Text)
                    AppText("Sign in to track GPU issues, share logs, and chat with developers.", color = G57Muted, style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RoleCard("USER", "Report & track issues", ui.loginRole == "user", { vm.setLoginRole("user") }, Modifier.weight(1f))
                        RoleCard("ADMIN", "Developer panel", ui.loginRole == "admin", { vm.setLoginRole("admin") }, Modifier.weight(1f))
                    }
                    OutlinedTextField(
                        value = ui.username,
                        onValueChange = vm::setUsername,
                        label = { AppText("Username") },
                        supportingText = { AppText(if (ui.loginRole == "user") "Choose a unique username" else "Admin username") },
                        leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = null, tint = G57Primary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    if (ui.loginRole == "user") {
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
                    OutlinedTextField(
                        value = ui.password,
                        onValueChange = vm::setPassword,
                        label = { AppText("Password") },
                        supportingText = { AppText(if (ui.loginRole == "user" && ui.userCreateAccount) "At least 8 characters" else "Your account password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = G57Primary) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Button(
                        onClick = vm::login,
                        enabled = !ui.loading,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(if (ui.loading) Icons.Default.HourglassTop else Icons.Default.Login, contentDescription = null)
                        Spacer(Modifier.width(9.dp))
                        AppText(if (ui.loading) "PLEASE WAIT…" else if (ui.loginRole == "user" && ui.userCreateAccount) "CREATE ACCOUNT" else "SIGN IN", fontWeight = FontWeight.Bold)
                    }
                    ui.error?.let { error ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppText(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            AppText("Private issue tracking • Device-aware chat translation", color = G57Muted, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
        IssueList(
            issues = ui.issues,
            onClick = vm::openUserIssue,
            emptyText = "You have no issues yet.",
            modifier = Modifier.weight(1f),
            headerContent = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = G57Primary.copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, G57Primary.copy(alpha = 0.30f))
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppText("Welcome, ${ui.profile?.username ?: ui.username}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            AppText("Track driver issues and share logs with developers.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                            val unread = ui.notifications.count { it.readAt == null }
                            if (unread > 0) AppText("🔔 $unread unread notification${if (unread == 1) "" else "s"}", color = G57Cyan, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = vm::openCreate,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 11.dp)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(6.dp))
                            AppText("CREATE ISSUE", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { ui.profile?.id?.let(vm::loadUserData) },
                            enabled = ui.profile != null,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 11.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(6.dp))
                            AppText("REFRESH")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatChip("TOTAL", ui.issues.size)
                        StatChip("OPEN", ui.issues.count { it.status == "open" })
                        StatChip("ACTIVE", ui.issues.count { it.status in listOf("investigating", "fix_in_progress", "testing") })
                        StatChip("FIXED", ui.issues.count { it.status == "fixed" || it.status == "closed" })
                    }
                    AppText("YOUR ISSUES", color = G57Cyan, fontWeight = FontWeight.Bold)
                }
            }
        )
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
                    notification.issueId?.let(vm::openIssueFromNotification)
                }) { AppText(if (notification.issueId != null) "OPEN ISSUE" else "OK") }
            },
            title = { AppText(if (notification.title.contains("reply", true)) "GMailGPU • New reply" else "GMailGPU • Notification") },
            text = { AppText("${notification.title}\n\n${notification.message}") }
        )
    }
}

@Composable
private fun AdminHome(ui: UiState, vm: G57ViewModel, onMenu: () -> Unit) {
    var shownReplyNotification by remember(ui.profile?.id, ui.notifications) {
        mutableStateOf(ui.notifications.firstOrNull { it.readAt == null && it.title.contains("reply", ignoreCase = true) })
    }
    LaunchedEffect(Unit) { vm.loadIssues(true) }
    Column(Modifier.fillMaxSize()) {
        BrandHeader("Developer control center", onMenu = onMenu)
        IssueList(
            issues = ui.issues,
            onClick = vm::openAdminIssue,
            emptyText = "No issues in Cloud.",
            modifier = Modifier.weight(1f),
            headerContent = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppText("ISSUE OPERATIONS", color = G57Cyan, fontWeight = FontWeight.Bold)
                    AppText("Review evidence, download user files, and reply to reports.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = vm::openUserManagement,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.ManageAccounts, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            AppText("USERS", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = vm::openSolvedUsers,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Verified, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            AppText("RESOLVED", maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = { vm.loadIssues(true) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        AppText("REFRESH", maxLines = 1)
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatChip("OPEN", ui.issues.count { it.status == "open" })
                        StatChip("ACTIVE", ui.issues.count { it.status == "investigating" || it.status == "fix_in_progress" })
                        StatChip("TESTING", ui.issues.count { it.status == "testing" })
                        StatChip("FIXED", ui.issues.count { it.status == "fixed" || it.status == "closed" })
                    }
                    AppText("ALL ISSUES", color = G57Cyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
    shownReplyNotification?.let { notification ->
        AlertDialog(
            onDismissRequest = {
                vm.markNotificationRead(notification)
                shownReplyNotification = null
            },
            title = { AppText("New reply in GMailGPU") },
            text = { AppText("${notification.title}\n\n${notification.message}") },
            confirmButton = {
                TextButton(onClick = {
                    vm.markNotificationRead(notification)
                    shownReplyNotification = null
                    notification.issueId?.let(vm::openIssueFromNotification)
                }) { AppText(if (notification.issueId != null) "OPEN ISSUE CHAT" else "OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.markNotificationRead(notification)
                    shownReplyNotification = null
                }) { AppText("DISMISS") }
            }
        )
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
            AppText("APP LANGUAGE & TRANSLATION", color = G57Cyan, fontWeight = FontWeight.Bold)
            AppText("Choose the language for the app and chat translations. Use your device language to follow Android's default language automatically.", color = G57Muted)
            AppText("First-time translation can take a little while because the language model may need to download. Please wait; the original message stays visible.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
            listOf(
                "system" to "Use device language (automatic)",
                "en" to "English",
                "ar" to "العربية",
                "pt-BR" to "Português (Brasil)"
            ).forEach { (code, label) ->
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
            AppUpdateSection(
                accent = G57Cyan,
                muted = G57Muted,
                surface = G57Card,
                border = G57Border
            )
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
            Divider(color = G57Border)
            AppText("FEEDBACK & BUG REPORTS", color = G57Cyan, fontWeight = FontWeight.Bold)
            AppText(
                "Have a feature suggestion or found an app bug? Open a new GitHub issue and describe the steps to reproduce it. Remove passwords, tokens, and private information from logs first.",
                color = G57Muted,
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { vm.openExternalUrl("https://github.com/SOLO-XP/GMailGPU/issues/new/choose") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                AppText("SUGGEST A FEATURE / REPORT A BUG")
            }
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

private data class DriverTestRecord(
    val gpu: String,
    val game: String,
    val driver: String,
    val averageFps: Int,
    val minimumFps: Int,
    val graphicsResult: String,
    val notes: String,
    val testedAt: String
)

private fun loadDriverTestRecords(context: Context): List<DriverTestRecord> = runCatching {
    val raw = context.getSharedPreferences("gmailgpu_driver_test_lab", Context.MODE_PRIVATE)
        .getString("records", "[]") ?: "[]"
    val array = org.json.JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        val row = array.optJSONObject(index) ?: return@mapNotNull null
        DriverTestRecord(
            gpu = row.optString("gpu", "G57"),
            game = row.optString("game", ""),
            driver = row.optString("driver", "System Driver"),
            averageFps = row.optInt("averageFps", 0),
            minimumFps = row.optInt("minimumFps", 0),
            graphicsResult = row.optString("graphicsResult", "Not recorded"),
            notes = row.optString("notes", ""),
            testedAt = row.optString("testedAt", "")
        )
    }
}.getOrDefault(emptyList())

private fun saveDriverTestRecords(context: Context, records: List<DriverTestRecord>) {
    val array = org.json.JSONArray()
    records.forEach { record ->
        array.put(org.json.JSONObject().apply {
            put("gpu", record.gpu)
            put("game", record.game)
            put("driver", record.driver)
            put("averageFps", record.averageFps)
            put("minimumFps", record.minimumFps)
            put("graphicsResult", record.graphicsResult)
            put("notes", record.notes)
            put("testedAt", record.testedAt)
        })
    }
    context.getSharedPreferences("gmailgpu_driver_test_lab", Context.MODE_PRIVATE)
        .edit().putString("records", array.toString()).apply()
}

private fun exportDriverTestRecords(records: List<DriverTestRecord>): String = buildString {
    appendLine("GMailGPU Driver Test Lab — user-recorded results")
    appendLine("Results are device/game-specific and are not universal driver rankings.")
    records.forEach { record ->
        appendLine(record.gpu + " | " + record.game + " | " + record.driver +
            " | Average FPS: " + record.averageFps +
            " | Minimum FPS: " + (if (record.minimumFps > 0) record.minimumFps.toString() else "not recorded") +
            " | Graphics: " + record.graphicsResult + " | " + record.testedAt)
        if (record.notes.isNotBlank()) appendLine("Notes: " + record.notes)
    }
}

@Composable
private fun DriverDevelopersScreen(vm: G57ViewModel) {
    val context = LocalContext.current
    val repositories = listOf(
        Triple("G57", "Noysz / panvk-g99-jm", "https://github.com/Noysz/panvk-g99-jm"),
        Triple("G57", "mexicanbr0auth / mesa-panvk-g57", "https://github.com/mexicanbr0auth/mesa-panvk-g57"),
        Triple("G57", "FristOneRR / FristOneRR-Panvk-Driver", "https://github.com/FristOneRR/FristOneRR-Panvk-Driver"),
        Triple("G52", "LukeValen / panvk-mali-g52", "https://github.com/LukeValen/panvk-mali-g52"),
        Triple("G615", "GunaCharanTeja / panvk-kbase-android", "https://github.com/GunaCharanTeja/panvk-kbase-android"),
        Triple("G720", "wonderkast02 / panvk-g720-kbase-csf", "https://github.com/wonderkast02/panvk-g720-kbase-csf")
    )
    val visualReferences = listOf(
        Triple("G52", "Open Mali-G52 architecture image", "https://www.arm.com/-/media/arm%20developer%20community/images/block%20diagrams/mali%20processor/mali-g52%20diagram%20v2-01.png?h=1647&hash=18201226E04DC4D55B13858082647F28&la=zh-CN&rev=58ae4edd858b421faade97bcc6cb3d22&revision=58ae4edd-858b-421f-aade-97bcc6b3d22&w=1578"),
        Triple("G57", "Open Mali-G57 architecture diagram", "https://cdn.mobilecity.vn/mobilecity-vn/images/2024/03/danh-gia-hieu-nang-unisoc-tiger-t612-7.jpg"),
        Triple("G615", "Open Mali-G615 architecture diagram", "https://www.notebookcheck.net/fileadmin/Notebooks/ARM/Arm_Mali-G615.jpg"),
        Triple("G720", "Open Mali-G720 architecture illustration", "https://www.androidauthority.com/wp-content/uploads/2023/05/Arm-5th-Gen-GPU-Immortalis-G720.jpg")
    )
    var selectedGpu by remember { mutableStateOf("G57") }
    var selectedDriver by remember { mutableStateOf("System Driver") }
    var gameName by remember { mutableStateOf("") }
    var averageFps by remember { mutableStateOf("") }
    var minimumFps by remember { mutableStateOf("") }
    var graphicsResult by remember { mutableStateOf("Clean") }
    var notes by remember { mutableStateOf("") }
    var records by remember { mutableStateOf(loadDriverTestRecords(context)) }
    var showClearHistory by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var referenceImageUrl by remember { mutableStateOf<String?>(null) }
    var referenceImageTitle by remember { mutableStateOf<String?>(null) }
    var selectedSymptom by remember { mutableStateOf("Black textures / missing text") }

    Column(Modifier.fillMaxSize()) {
        BrandHeader("Mali Driver Hub • evidence, testing & community projects") { vm.back() }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        color = G57Card,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, G57Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppText("⚖️ NO UNIVERSAL WINNER", color = G57Cyan, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                            AppText(
                                "We do not have verified, same-device benchmarks proving that System Driver or PanVK is always faster. System Driver is a baseline; PanVK is experimental. Compare the same game, scene, resolution and settings before choosing.",
                                color = G57Text
                            )
                            AppText(
                                "These GPU icons are illustrations, not die photographs or performance charts. Reference links open technical images or project test notes.",
                                color = G57Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    AppText("🧩 GPU IMAGE & TECHNICAL REFERENCES", color = G57Cyan, fontWeight = FontWeight.Bold)
                    visualReferences.forEach { (gpu, label, url) ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = G57Card),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val tint = when (gpu) {
                                    "G52" -> Color(0xFF49B7D5)
                                    "G57" -> Color(0xFF9B7BFF)
                                    "G615" -> Color(0xFF4ED6A0)
                                    else -> Color(0xFFFFB454)
                                }
                                Icon(Icons.Default.Memory, contentDescription = "Illustration for Mali-" + gpu, tint = tint, modifier = Modifier.size(42.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    AppText("Mali-" + gpu, color = tint, fontWeight = FontWeight.ExtraBold)
                                    AppText(label, color = G57Muted, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = {
                                    referenceImageUrl = url
                                    referenceImageTitle = gpu + " • " + label
                                }) {
                                    Icon(Icons.Default.Image, contentDescription = "Preview GPU reference", tint = G57Cyan)
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = G57Card),
                        border = androidx.compose.foundation.BorderStroke(1.dp, G57Border),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppText("🧭 QUICK TROUBLESHOOTER", color = G57Cyan, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                            AppText("Choose the symptom to get a safe first-check list. These steps diagnose the issue; they do not promise a fix.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                            listOf(
                                "Black textures / missing text",
                                "Vulkan error / won't launch",
                                "Low FPS / stutter",
                                "Crash / freeze"
                            ).forEach { symptom ->
                                FilterChip(
                                    selected = selectedSymptom == symptom,
                                    onClick = { selectedSymptom = symptom },
                                    label = { AppText(symptom) }
                                )
                            }
                            val steps = when (selectedSymptom) {
                                "Black textures / missing text" -> listOf(
                                    "Capture a screenshot in the exact scene and note which textures are missing.",
                                    "Repeat the same scene with the other driver if available; do not change resolution or other settings.",
                                    "Attach the screenshot and a log from immediately after the test."
                                )
                                "Vulkan error / won't launch" -> listOf(
                                    "Copy the full Vulkan error text instead of only writing 'crashed'.",
                                    "Record the selected driver, Winlator/GameHub version, Wine and DXVK versions.",
                                    "Attach the startup log and note whether the game worked with another driver."
                                )
                                "Low FPS / stutter" -> listOf(
                                    "Warm up the game and test the same scene three times with matching settings.",
                                    "Record average FPS, minimum FPS, resolution and whether the phone is overheating.",
                                    "Compare recorded results in Driver Test Lab; FPS alone does not prove better rendering."
                                )
                                else -> listOf(
                                    "Write the exact steps that trigger the crash and whether it happens every time.",
                                    "Collect the log immediately after the crash, before launching the game again.",
                                    "Include device/GPU, driver build, emulator, Wine and DXVK details."
                                )
                            }
                            steps.forEachIndexed { index, step ->
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(color = G57Primary.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                                        AppText((index + 1).toString(), color = G57Primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
                                    }
                                    AppText(step, color = G57Text, modifier = Modifier.weight(1f))
                                }
                            }
                            OutlinedButton(
                                onClick = { vm.openExternalUrl("https://github.com/SOLO-XP/GMailGPU/issues/new/choose") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                AppText("REPORT THIS PROBLEM ON GITHUB")
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = G57Card),
                        border = androidx.compose.foundation.BorderStroke(1.dp, G57Border),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppText("🧪 DRIVER TEST LAB", color = G57Cyan, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                            AppText(
                                "Record tests from your own device. Results stay locally on this phone unless you share them.",
                                color = G57Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                            AppText("GPU", color = G57Text, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("G52", "G57", "G615", "G720").forEach { gpu ->
                                    FilterChip(
                                        selected = selectedGpu == gpu,
                                        onClick = { selectedGpu = gpu },
                                        label = { AppText(gpu) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            AppText("Driver tested", color = G57Text, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("System Driver", "PanVK").forEach { driver ->
                                    FilterChip(
                                        selected = selectedDriver == driver,
                                        onClick = { selectedDriver = driver },
                                        label = { AppText(driver) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = gameName,
                                onValueChange = { gameName = it.take(100) },
                                label = { AppText("Game / test scene") },
                                placeholder = { AppText("e.g. GTA V — same benchmark scene") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = averageFps,
                                    onValueChange = { averageFps = it.filter(Char::isDigit).take(4) },
                                    label = { AppText("Average FPS *") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = minimumFps,
                                    onValueChange = { minimumFps = it.filter(Char::isDigit).take(4) },
                                    label = { AppText("Minimum FPS") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            AppText("Visual / stability result", color = G57Text, fontWeight = FontWeight.Bold)
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                listOf("Clean", "Visual bugs", "Crash / freeze", "Too slow").forEach { result ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = graphicsResult == result, onClick = { graphicsResult = result })
                                        AppText(result, color = G57Text)
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it.take(800) },
                                label = { AppText("Notes (optional)") },
                                placeholder = { AppText("Resolution, DXVK, Wine, stutter, temperature…") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    val avg = averageFps.toIntOrNull()
                                    val min = minimumFps.toIntOrNull() ?: 0
                                    if (gameName.isBlank() || avg == null || avg <= 0 || min < 0 || (min > 0 && min > avg)) {
                                        feedbackMessage = "Enter a game name and valid FPS values. Minimum FPS cannot exceed average FPS."
                                    } else {
                                        val entry = DriverTestRecord(
                                            selectedGpu,
                                            gameName.trim(),
                                            selectedDriver,
                                            avg,
                                            min,
                                            graphicsResult,
                                            notes.trim(),
                                            java.time.Instant.now().toString()
                                        )
                                        records = (listOf(entry) + records).take(100)
                                        saveDriverTestRecords(context, records)
                                        averageFps = ""
                                        minimumFps = ""
                                        notes = ""
                                        feedbackMessage = "Test saved on this device."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                AppText("SAVE TEST RESULT")
                            }
                            AppText(
                                "For fairer results, warm up the game, use the same scene/settings, close background apps and repeat each test 3 times. Different devices are not directly comparable.",
                                color = G57Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                            feedbackMessage?.let { AppText(it, color = if (it.startsWith("Test saved")) G57Cyan else MaterialTheme.colorScheme.error) }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = G57Card),
                        border = androidx.compose.foundation.BorderStroke(1.dp, G57Border),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    AppText("📊 MY TEST HISTORY", color = G57Cyan, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                                    AppText(records.size.toString() + " saved result(s) • stored only on this device", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(
                                    onClick = {
                                        runCatching {
                                            val share = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "GMailGPU Driver Test Results")
                                                putExtra(Intent.EXTRA_TEXT, exportDriverTestRecords(records))
                                            }
                                            context.startActivity(Intent.createChooser(share, "Share test results"))
                                        }.onFailure { feedbackMessage = "Could not open the share menu." }
                                    },
                                    enabled = records.isNotEmpty()
                                ) { Icon(Icons.Default.Share, contentDescription = "Share results", tint = G57Cyan) }
                                IconButton(onClick = { showClearHistory = true }, enabled = records.isNotEmpty()) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear history", tint = G57Muted)
                                }
                            }
                            val groups = records.groupBy { it.gpu + "|" + it.game.trim().lowercase(Locale.ROOT) }
                            val pairs = groups.values.mapNotNull { group ->
                                val system = group.firstOrNull { it.driver == "System Driver" }
                                val panvk = group.firstOrNull { it.driver == "PanVK" }
                                if (system != null && panvk != null) system to panvk else null
                            }
                            if (pairs.isEmpty() && records.isNotEmpty()) {
                                AppText("Save both drivers for the same GPU and game to see a side-by-side comparison here.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            pairs.take(5).forEach { pair ->
                                val system = pair.first
                                val panvk = pair.second
                                val delta = ((panvk.averageFps - system.averageFps).toDouble() / system.averageFps.toDouble()) * 100.0
                                val deltaText = String.format(Locale.US, "%.1f", delta)
                                Surface(color = G57Bg, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, G57Border), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        AppText(system.gpu + " • " + system.game, color = G57Text, fontWeight = FontWeight.Bold)
                                        AppText("System: " + system.averageFps + " FPS | PanVK: " + panvk.averageFps + " FPS", color = G57Text)
                                        AppText("PanVK average FPS difference in this pair: " + (if (delta > 0) "+" else "") + deltaText + "%", color = G57Cyan, style = MaterialTheme.typography.bodySmall)
                                        AppText("Graphics: System = " + system.graphicsResult + "; PanVK = " + panvk.graphicsResult + ". This is not a universal ranking.", color = G57Muted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            records.take(12).forEach { record ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            AppText(record.gpu + " • " + record.game, color = G57Text, fontWeight = FontWeight.SemiBold)
                                            AppText(record.driver + " • Avg " + record.averageFps + " FPS • " + record.graphicsResult, color = G57Muted, style = MaterialTheme.typography.bodySmall)
                                            if (record.notes.isNotBlank()) AppText(record.notes, color = G57Muted, style = MaterialTheme.typography.bodySmall)
                                        }
                                        IconButton(onClick = {
                                            records = records.filterNot { it.testedAt == record.testedAt && it.gpu == record.gpu && it.game == record.game && it.driver == record.driver }
                                            saveDriverTestRecords(context, records)
                                        }) { Icon(Icons.Default.Close, contentDescription = "Remove test", tint = G57Muted) }
                                    }
                                    Divider(color = G57Border)
                                }
                            }
                        }
                    }

                    AppText("⚠️ DRIVER SUPPORT IS EXPERIMENTAL", color = G57Cyan, fontWeight = FontWeight.Bold)
                    AppText(
                        "Community Android drivers are independent forks and can differ from upstream Mesa. Validation on one GPU variant, core count, kernel and game does not prove compatibility elsewhere. Read the source project's limitations before installing.",
                        color = G57Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Divider(color = G57Border)
                    AppText("COMMUNITY DRIVER REPOSITORIES", color = G57Cyan, fontWeight = FontWeight.Bold)
                }
            }
            items(repositories) { (gpu, name, url) ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = G57Card),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = "Mali-" + gpu, tint = when (gpu) {
                                "G52" -> Color(0xFF49B7D5)
                                "G57" -> Color(0xFF9B7BFF)
                                "G615" -> Color(0xFF4ED6A0)
                                else -> Color(0xFFFFB454)
                            }, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                AppText("GPU • " + gpu, color = G57Cyan, style = MaterialTheme.typography.labelMedium)
                                AppText(name, fontWeight = FontWeight.Bold)
                            }
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
            Button(onClick = { vm.openExternalUrl("https://github.com/GunaCharanTeja/WinlatorMali") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                AppText("Winlator Download Charan")
            }
            Spacer(Modifier.height(12.dp))
            AppText("Contact Us on Telegram", color = G57Cyan, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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

    if (referenceImageUrl != null) {
        val imageUrl = referenceImageUrl!!
        AlertDialog(
            onDismissRequest = { referenceImageUrl = null; referenceImageTitle = null },
            title = { AppText(referenceImageTitle ?: "GPU reference image", color = G57Text, fontWeight = FontWeight.Bold) },
            text = {
                AndroidView(
                    factory = { webContext ->
                        WebView(webContext).apply {
                            settings.javaScriptEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            setBackgroundColor(android.graphics.Color.DKGRAY)
                            webViewClient = WebViewClient()
                            loadUrl(imageUrl)
                        }
                    },
                    update = { view -> if (view.url != imageUrl) view.loadUrl(imageUrl) },
                    modifier = Modifier.fillMaxWidth().height(420.dp)
                )
            },
            confirmButton = { TextButton(onClick = { referenceImageUrl = null; referenceImageTitle = null }) { AppText("CLOSE") } },
            dismissButton = {
                TextButton(onClick = { vm.openExternalUrl(imageUrl) }) { AppText("OPEN EXTERNALLY") }
            }
        )
    }

    if (showClearHistory) {
        AlertDialog(
            onDismissRequest = { showClearHistory = false },
            title = { AppText("Clear saved test history?", color = G57Text, fontWeight = FontWeight.Bold) },
            text = { AppText("This deletes only locally saved driver test results. It does not delete issue reports or cloud files.", color = G57Text) },
            confirmButton = {
                TextButton(onClick = {
                    records = emptyList()
                    saveDriverTestRecords(context, records)
                    showClearHistory = false
                    feedbackMessage = "Test history cleared."
                }) { AppText("CLEAR HISTORY", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearHistory = false }) { AppText("CANCEL") } }
        )
    }
}

@Composable
private fun AdminUserManagementScreen(ui: UiState, vm: G57ViewModel) {
    LaunchedEffect(Unit) { vm.loadUsers(); vm.loadIssues(true) }
    Column(Modifier.fillMaxSize()) {
        BrandHeader("USER MANAGEMENT") { vm.back() }
        AppText(
            "Registered profiles • view-only access. Screenshots and screen recording are blocked on this page to help protect usernames. Other app screens still allow screenshots.",
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
                        }
                    }
                }
            }
        }
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
private fun IssueList(
    issues: List<Issue>,
    onClick: (Issue) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    headerContent: @Composable () -> Unit = {}
) {
    var selectedGroup by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    val groups = listOf(
        "All" to issues.size,
        "Opened" to issues.count { it.status == "open" },
        "Active" to issues.count { it.status in listOf("investigating", "fix_in_progress", "testing") },
        "Fixed" to issues.count { it.status == "fixed" },
        "Closed" to issues.count { it.status == "closed" }
    )
    val filtered = remember(issues, selectedGroup, searchQuery) {
        val matchingGroup = when (selectedGroup) {
            "Opened" -> issues.filter { it.status == "open" }
            "Active" -> issues.filter { it.status in listOf("investigating", "fix_in_progress", "testing") }
            "Fixed" -> issues.filter { it.status == "fixed" }
            "Closed" -> issues.filter { it.status == "closed" }
            else -> issues
        }
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        matchingGroup.filter { issue ->
            query.isBlank() ||
                issue.title.lowercase(Locale.ROOT).contains(query) ||
                issue.game.lowercase(Locale.ROOT).contains(query) ||
                issue.problemType.lowercase(Locale.ROOT).contains(query) ||
                issue.driver.lowercase(Locale.ROOT).contains(query) ||
                issue.emulator.lowercase(Locale.ROOT).contains(query) ||
                issue.issueNumber.toString().contains(query) ||
                (issue.userUsername ?: "").lowercase(Locale.ROOT).contains(query)
        }.sortedWith(compareByDescending<Issue> { it.issueNumber })
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Search and filters stay pinned directly below the screen header.
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
            placeholder = { AppText("Search issues, game, driver, user, or #") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = G57Cyan) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            groups.forEach { (name, count) ->
                FilterChip(
                    selected = selectedGroup == name,
                    onClick = { selectedGroup = name },
                    label = { AppText("$name ($count)") }
                )
            }
        }
        Divider(color = G57Border.copy(alpha = 0.55f))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "dashboard-header") { headerContent() }
            if (filtered.isEmpty()) {
                item(key = "empty-issues") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppText(
                            when {
                                issues.isEmpty() -> emptyText
                                searchQuery.isNotBlank() -> "No matching issues. Try another search."
                                else -> "No issues in ${selectedGroup.lowercase()} yet."
                            },
                            color = G57Muted
                        )
                    }
                }
            } else {
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
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            val detectedDevice = remember(context) { DiagnosticCollector.snapshot(context) }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = G57Card,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, G57Border)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = G57Cyan)
                        Spacer(Modifier.width(8.dp))
                        AppText("AUTOMATIC DEVICE DETECTION", color = G57Cyan, fontWeight = FontWeight.Bold)
                    }
                    Detail("Device", "${detectedDevice.manufacturer} ${detectedDevice.model}")
                    Detail("Android", detectedDevice.androidVersion)
                    Detail("Chipset / SoC", detectedDevice.soc)
                    Detail("GPU renderer", detectedDevice.gpu)
                    Detail("CPU", detectedDevice.cpuModel)
                    Detail("RAM", detectedDevice.totalRamMb?.let { "${it} MB" } ?: "Not exposed by Android")
                    AppText(
                        "Detected by Android. GPU renderer and chipset details may be limited by the device or graphics driver. Winlator and driver versions below must still be entered manually because Android cannot reliably read them.",
                        color = G57Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
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
    var showChatNotificationSettings by remember(issue.id) { mutableStateOf(false) }
    var notificationModeDraft by remember(issue.id, ui.chatNotificationMode) { mutableStateOf(ui.chatNotificationMode) }
    var imagePreviewUrl by remember { mutableStateOf<String?>(null) }
    var imagePreviewName by remember { mutableStateOf<String?>(null) }
    var imagePreviewError by remember { mutableStateOf<String?>(null) }
    var imagePreviewLoading by remember { mutableStateOf(false) }
    var reopenLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(issue.id, admin) {
        if (admin && SupabaseProvider.enabled) {
            runCatching { G57Repository().markIssueViewedByAdmin(issue.id) }
        }
    }
    LaunchedEffect(issue.id, ui.chatMessages.lastOrNull()?.id) {
        if (ui.chatMessages.isNotEmpty() && SupabaseProvider.enabled) {
            delay(750)
            vm.markIssueMessagesRead(issue.id)
        }
    }
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
                        onOpen = {
                            if (attachment.type == "image" || attachment.mimeType.orEmpty().startsWith("image/")) {
                                imagePreviewLoading = true
                                imagePreviewError = null
                                scope.launch {
                                    runCatching { G57Repository().signedUrl(attachment.storagePath) }
                                        .onSuccess { url ->
                                            imagePreviewUrl = url
                                            imagePreviewName = attachment.filename
                                            imagePreviewLoading = false
                                        }
                                        .onFailure { error ->
                                            imagePreviewLoading = false
                                            imagePreviewError = "Could not load image. Check Supabase attachment access policies and admin permissions. ${error.message.orEmpty()}"
                                        }
                                }
                            } else {
                                vm.openAttachment(attachment)
                            }
                        },
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

            val counterpartCandidates = if (admin) {
                ui.presenceRecords.filter { it.userId == issue.userId }
            } else {
                ui.presenceRecords.filter { it.role.equals("admin", ignoreCase = true) }
            }
            val counterpartPresence = counterpartCandidates.firstOrNull { it.isFreshlyOnline() }
                ?: counterpartCandidates.maxByOrNull { it.lastSeenAt.orEmpty() }
            val counterpartOnline = counterpartPresence?.isFreshlyOnline() == true
            val counterpartName = if (admin) "User" else "Developer"
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                shape = RoundedCornerShape(12.dp),
                color = G57Card
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(10.dp),
                        shape = RoundedCornerShape(50),
                        color = if (counterpartOnline) Color(0xFF3DDC84) else G57Muted
                    ) { }
                    Column(Modifier.weight(1f)) {
                        AppText(
                            if (counterpartOnline) "$counterpartName online" else "$counterpartName offline",
                            fontWeight = FontWeight.SemiBold
                        )
                        val seen = counterpartPresence?.lastSeenAt?.replace("T", " ")?.take(16)
                        AppText(
                            when {
                                counterpartOnline -> "Active in GMailGPU just now • You can send a message"
                                seen != null -> "Last seen: $seen"
                                else -> "Last-seen status will appear after they open the app"
                            },
                            color = G57Muted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Icon(
                        if (counterpartOnline) Icons.Default.Chat else Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (counterpartOnline) Color(0xFF3DDC84) else G57Muted
                    )
                }
            }
            AppText(
                if (admin) "ISSUE CHAT • USER" else "CHAT WITH DEVELOPER",
                color = G57Cyan,
                fontWeight = FontWeight.Bold
            )
            AppText(
                "Messages are saved to this issue. Only the issue owner and admins can read or reply.",
                color = G57Muted,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { vm.refreshIssueMessages(issue.id) },
                    enabled = !ui.loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    AppText("REFRESH")
                }
                OutlinedButton(
                    onClick = {
                        notificationModeDraft = ui.chatNotificationMode
                        showChatNotificationSettings = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    AppText("ALERT SETTINGS")
                }
            }
            AppText(
                when (ui.chatNotificationMode) {
                    "muted" -> "Alerts muted for this issue"
                    "important" -> "Important replies only"
                    else -> "All reply alerts enabled"
                },
                color = G57Muted,
                style = MaterialTheme.typography.labelSmall
            )
            ui.chatTypingStates.firstOrNull { it.userId != ui.profile?.id }?.let { typing ->
                AppText(
                    "${typing.username.ifBlank { if (admin) "User" else "Developer" }} is typing…",
                    color = G57Cyan,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppText(
                                    when {
                                        isMine -> "You"
                                        admin -> issue.userUsername ?: "User"
                                        else -> "Developer"
                                    },
                                    color = if (isMine) G57Cyan else G57Primary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (message.isImportant) {
                                    Spacer(Modifier.width(8.dp))
                                    AppText(
                                        "IMPORTANT",
                                        color = Color(0xFFFFC857),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            AutoTranslatedMessage(message.body, ui.appLanguage)
                            message.createdAt?.let {
                                AppText(it.replace("T", " ").take(16), color = G57Muted, style = MaterialTheme.typography.labelSmall)
                            }
                            if (isMine) {
                                AppText(
                                    when {
                                        message.readAt != null -> "✓✓ Read"
                                        message.deliveredAt != null -> "✓ Delivered"
                                        else -> "✓ Sent"
                                    },
                                    color = if (message.readAt != null) G57Cyan else G57Muted,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.End)
                                )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = ui.chatMarkImportant,
                    onCheckedChange = vm::setChatMarkImportant
                )
                Column(Modifier.weight(1f)) {
                    AppText("Mark this reply as important", fontWeight = FontWeight.SemiBold)
                    AppText(
                        "Recipients using important-only alerts will be notified.",
                        color = G57Muted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
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

            if (showChatNotificationSettings) {
                AlertDialog(
                    onDismissRequest = { showChatNotificationSettings = false },
                    title = { AppText("Issue notification settings", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppText(
                                "Choose which replies alert you for this issue. Muting does not delete messages.",
                                color = G57Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                            listOf(
                                "all" to ("All replies" to "Alert me for every reply"),
                                "important" to ("Important replies only" to "Only explicitly important replies alert me"),
                                "muted" to ("Mute this issue" to "Do not send reply alerts for this issue")
                            ).forEach { (mode, copy) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = notificationModeDraft == mode,
                                        onClick = { notificationModeDraft = mode }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        AppText(copy.first, fontWeight = FontWeight.SemiBold)
                                        AppText(copy.second, color = G57Muted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showChatNotificationSettings = false
                            vm.setChatNotificationMode(issue.id, notificationModeDraft)
                        }) { AppText("SAVE") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showChatNotificationSettings = false }) { AppText("CANCEL") }
                    }
                )
            }

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

            if (!admin && issue.status == "closed") {
                Surface(
                    color = G57Card,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, G57Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText("CLOSURE NOTE", color = G57Cyan, fontWeight = FontWeight.Bold)
                        AppText(
                            issue.developerNote ?: "This issue is closed. Contact the developer through issue chat if you still need help.",
                            color = G57Text
                        )
                        Button(
                            onClick = {
                                if (reopenLoading) return@Button
                                if (!SupabaseProvider.enabled) {
                                    imagePreviewError = "Reopening an issue requires the Supabase cloud database."
                                    return@Button
                                }
                                reopenLoading = true
                                scope.launch {
                                    runCatching { G57Repository().reopenOwnIssue(issue.id) }
                                        .onSuccess {
                                            reopenLoading = false
                                            android.widget.Toast.makeText(context, "Issue reopened. Refreshing your issue list.", android.widget.Toast.LENGTH_LONG).show()
                                            vm.back()
                                            vm.loadIssues(admin = false)
                                        }
                                        .onFailure { error ->
                                            reopenLoading = false
                                            imagePreviewError = "Could not reopen this issue. Run supabase_14_day_auto_close.sql in Supabase SQL Editor. ${error.message.orEmpty()}"
                                        }
                                }
                            },
                            enabled = !ui.loading && !reopenLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            AppText(if (reopenLoading) "REOPENING…" else "REOPEN ISSUE")
                        }
                    }
                }
            }
            if (!admin && issue.status in listOf("fixed", "closed")) {
                Surface(color = Color(0xFF153322), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF63E6BE))
                        Spacer(Modifier.width(10.dp))
                        AppText(
                            if (issue.status == "closed" && issue.developerNote.orEmpty().startsWith("Closed automatically:"))
                                "This issue was automatically closed after 14 days without a developer review."
                            else "The developer marked this issue as resolved."
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (ui.attachmentPreviewText != null) {
        AlertDialog(
            onDismissRequest = vm::closeAttachmentPreview,
            title = { AppText(ui.attachmentPreviewName ?: "Log preview", fontWeight = FontWeight.Bold, color = G57Text) },
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

    if (imagePreviewUrl != null) {
        val previewUrl = imagePreviewUrl!!
        AlertDialog(
            onDismissRequest = { imagePreviewUrl = null; imagePreviewName = null },
            title = { AppText(imagePreviewName ?: "Image preview", color = G57Text, fontWeight = FontWeight.Bold) },
            text = {
                AndroidView(
                    factory = { webContext ->
                        WebView(webContext).apply {
                            settings.javaScriptEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            setBackgroundColor(android.graphics.Color.DKGRAY)
                            webViewClient = WebViewClient()
                            loadUrl(previewUrl)
                        }
                    },
                    update = { view -> if (view.url != previewUrl) view.loadUrl(previewUrl) },
                    modifier = Modifier.fillMaxWidth().height(400.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { imagePreviewUrl = null; imagePreviewName = null }) { AppText("CLOSE") }
            },
            dismissButton = {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply { data = android.net.Uri.parse(previewUrl) })
                    }
                }) { AppText("OPEN EXTERNALLY") }
            }
        )
    }

    if (imagePreviewError != null) {
        AlertDialog(
            onDismissRequest = { imagePreviewError = null },
            title = { AppText("Attachment action failed", color = G57Text, fontWeight = FontWeight.Bold) },
            text = { AppText(imagePreviewError.orEmpty(), color = G57Text) },
            confirmButton = { TextButton(onClick = { imagePreviewError = null }) { AppText("OK") } }
        )
    }
    if (imagePreviewLoading) {
        AlertDialog(
            onDismissRequest = { imagePreviewLoading = false },
            title = { AppText("Loading image…", color = G57Text) },
            text = { CircularProgressIndicator() },
            confirmButton = { TextButton(onClick = { imagePreviewLoading = false }) { AppText("CANCEL") } }
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

private fun UserPresence.isFreshlyOnline(): Boolean {
    if (!isOnline || lastSeenAt.isNullOrBlank()) return false
    val instant = runCatching { java.time.OffsetDateTime.parse(lastSeenAt).toInstant() }.getOrNull()
        ?: runCatching { java.time.Instant.parse(lastSeenAt) }.getOrNull()
        ?: return false
    return instant.isAfter(java.time.Instant.now().minusSeconds(90))
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
