package com.finora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.*

val accents =
    linkedMapOf(
        "Emerald Finance" to 0xff076653,
        "Midnight Black" to 0xff303539,
        "Ocean Blue" to 0xff17679b,
        "Royal Purple" to 0xff7349a0,
        "Graphite Pro" to 0xff495862,
        "Minimal White" to 0xff506065,
        "Sunset Gradient" to 0xffab5034,
        "Premium Gold" to 0xff876713,
        "Cyber Neon" to 0xff00765f,
        "Soft Lavender" to 0xff805da0,
        "Arctic Silver" to 0xff526979,
        "Deep Navy" to 0xff28476b,
    )

@Composable
fun FinoraTheme(
    theme: String = "Emerald Finance",
    mode: String = "System",
    content: @Composable () -> Unit,
) {
    val dark = mode == "Dark" || (mode == "System" && isSystemInDarkTheme())
    val accent = Color(accents[theme] ?: 0xff076653)
    val colors =
        if (dark)
            darkColorScheme(
                primary = androidx.compose.ui.graphics.lerp(accent, Color.White, .58f),
                background = Color(0xff101b18),
                surface = Color(0xff192720),
            )
        else
            lightColorScheme(
                primary = accent,
                background = Color(0xfff7f9f6),
                surface = Color.White,
                secondaryContainer = Color(0xffdaecdf),
                surfaceVariant = Color(0xffeaf0ea),
                outlineVariant = Color(0xffd6e1d9),
                onSurfaceVariant = Color(0xff495d53),
                onBackground = Color(0xff1d2a25),
            )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
fun Heading(s: String) {
    Text(s, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
fun Field(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(
        value,
        change,
        Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
    )
}

@Composable
fun Choice(label: String, value: String, options: List<String>, select: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }) { Text("$label · $value") }
        DropdownMenu(open, { open = false }) {
            options.forEach {
                DropdownMenuItem(
                    { Text(it) },
                    {
                        select(it)
                        open = false
                    },
                )
            }
        }
    }
}

val LocalReports = staticCompositionLocalOf<ReportClient> { error("Reports unavailable") }

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject lateinit var reports: ReportClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val firebase = FirebaseApp.initializeApp(this)
        if (firebase != null) configureAppCheck()
        setContent {
            val appearanceState by appearance.data.collectAsState(initial = emptyPreferences())
            FinoraTheme(
                appearanceState[stringPreferencesKey("theme")] ?: "Emerald Finance",
                appearanceState[stringPreferencesKey("mode")] ?: "System",
            ) {
                val view = LocalView.current
                val light = MaterialTheme.colorScheme.background.luminance() > .5f
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = light
                        isAppearanceLightNavigationBars = light
                    }
                }
                Surface(
                    Modifier.fillMaxSize().systemBarsPadding(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (firebase == null)
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
                            Heading("FINORA")
                            Text("Understand Your Money. Control Your Spending.")
                            Panel {
                                Text("Firebase setup required")
                                Text(
                                    "This build needs the Android registration for com.finora.app in finoraaaaaa. See the repository setup instructions."
                                )
                            }
                        }
                    else CompositionLocalProvider(LocalReports provides reports) { Session() }
                }
            }
        }
    }
}

@Composable
private fun Session() {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    val account = user
    if (account == null) Login(auth)
    else
        key(account.uid) {
            val model = remember(account.uid) { LedgerModel(account.uid) }
            DisposableEffect(model) { onDispose { model.close() } }
            Account(model, account)
        }
}

@Composable
private fun Account(model: LedgerModel, user: FirebaseUser) {
    val p by model.profile.collectAsState()
    val data by model.entries.collectAsState()
    val error by model.error.collectAsState()
    val profile = p
    if (profile == null) {
        Column(Modifier.padding(24.dp)) {
            if (error.isEmpty()) CircularProgressIndicator()
            else {
                Heading("Couldn’t load your profile")
                Text(error)
                OutlinedButton({ FirebaseAuth.getInstance().signOut() }) { Text("Back to sign in") }
            }
        }
        return
    }
    FinoraTheme(profile.theme, profile.mode) {
        val nav = rememberNavController()
        val tabs = listOf("Home", "Transactions", "Analytics", "Assistant", "Profile")
        val route = nav.currentBackStackEntryAsState().value?.destination?.route
        val ctx = LocalContext.current
        LaunchedEffect(profile.theme, profile.mode) {
            ctx.appearance.edit {
                it[stringPreferencesKey("theme")] = profile.theme
                it[stringPreferencesKey("mode")] = profile.mode
            }
        }
        LaunchedEffect(profile.banks) {
            ctx.inbox(user.uid).edit().putStringSet("selected", profile.banks.toSet()).apply()
        }
        val plans by model.plans.collectAsState()
        LaunchedEffect(plans) { plans.forEach { scheduleReminder(ctx, user.uid, it) } }
        LaunchedEffect(profile.ready) {
            val activity = ctx as? MainActivity
            if (profile.ready && activity?.intent?.getBooleanExtra("planning", false) == true) {
                activity.intent.removeExtra("planning")
                nav.navigate("Planning")
            }
        }
        if (!profile.ready) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Banks(profile, model, true) {}
                Text(error)
            }
            return@FinoraTheme
        }
        Scaffold(
            bottomBar = {
                if (route in tabs)
                    NavigationBar(
                        modifier =
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(22.dp)),
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = route == t,
                                onClick = {
                                    nav.navigate(t) {
                                        popUpTo(nav.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        listOf(
                                            Icons.Default.Home,
                                            Icons.AutoMirrored.Filled.List,
                                            Icons.Default.DateRange,
                                            Icons.Default.Star,
                                            Icons.Default.Person,
                                        )[i],
                                        contentDescription = null,
                                    )
                                },
                                label = {
                                    Text(
                                        t,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
            }
        ) { pad ->
            Column(Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize()) {
                if (error.isNotBlank())
                    TextButton({ model.error.value = "" }) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                if (route !in tabs && route != null)
                    TextButton({ nav.popBackStack() }) { Text("‹  Back") }
                NavHost(nav, "Home") {
                    (tabs +
                            listOf(
                                "Add",
                                "Edit/{id}",
                                "Banks",
                                "Access",
                                "Planning",
                                "Parent",
                                "Settings",
                                "Control",
                                "Accounts",
                                "Categories",
                                "Security",
                            ))
                        .forEach { dest ->
                            composable(dest) { back ->
                                Column(
                                    Modifier.verticalScroll(rememberScrollState())
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    val go: (String) -> Unit = { nav.navigate(it) }
                                    when (dest) {
                                        "Home" -> Home(profile, data, user, go)
                                        "Transactions" -> History(data, go)
                                        "Analytics",
                                        "Assistant" -> Analysis(data, profile, dest == "Assistant")
                                        "Profile" -> {
                                            Avatar(model, true)
                                            Heading(
                                                profile.name.ifBlank {
                                                    user.displayName ?: "Your profile"
                                                }
                                            )
                                            Text(user.email.orEmpty())
                                            NavigationGroup(
                                                "Your account",
                                                listOf(
                                                    "Settings" to "Personal information & budgets",
                                                    "Security" to "Privacy and security",
                                                    "Parent" to "Parent Connect",
                                                ),
                                                go,
                                            )
                                            NavigationGroup(
                                                "Banking",
                                                listOf(
                                                    "Banks" to "Selected banks",
                                                    "Accounts" to "Bank accounts",
                                                    "Access" to "Bank notification access",
                                                ),
                                                go,
                                            )
                                            NavigationGroup(
                                                "Planning",
                                                listOf(
                                                    "Planning" to "Bills & savings goals",
                                                    "Categories" to "Custom categories",
                                                    "Control" to "Spending control",
                                                ),
                                                go,
                                            )
                                            OutlinedButton({
                                                ctx.inbox(user.uid)
                                                    .edit()
                                                    .putBoolean("enabled", false)
                                                    .apply()
                                                FirebaseAuth.getInstance().signOut()
                                            }) {
                                                Text("Sign out")
                                            }
                                        }
                                        "Add" -> Editor(null, model) { nav.popBackStack() }
                                        "Edit/{id}" ->
                                            data
                                                .find { it.id == back.arguments?.getString("id") }
                                                ?.let { Editor(it, model) { nav.popBackStack() } }
                                        "Settings" ->
                                            Settings(profile, model) { nav.popBackStack() }
                                        "Banks" ->
                                            Banks(profile, model, false) { nav.popBackStack() }
                                        "Access" -> Access(model, profile)
                                        "Accounts" -> Accounts(model, profile, data)
                                        "Security" -> Security(model)
                                        "Categories" -> Categories(model)
                                        "Planning" -> Planning(model)
                                        "Control" -> Spending(profile, data, model)
                                        "Parent" -> Parent(model)
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}
