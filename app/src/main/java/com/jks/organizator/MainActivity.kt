package com.jks.organizator

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jks.organizator.data.Graph
import com.jks.organizator.data.Settings
import com.jks.organizator.ui.*

class MainActivity : AppCompatActivity() {
    private val unlocked = mutableStateOf(false)
    private var lastStop = 0L
    private val route = mutableStateOf<String?>(null)

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        route.value = intent.getStringExtra("route")
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            AppTheme {
                val s by Graph.prefs.flow.collectAsState(initial = null as Settings?)
                val loaded = s
                if (loaded == null) Box(Modifier.fillMaxSize().background(C.bg))
                else if (loaded.lock && !unlocked.value) {
                    LockScreen { authenticate() }
                    LaunchedEffect(Unit) { authenticate() }
                } else AppRoot(route.value) { route.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route.value = intent.getStringExtra("route")
    }

    override fun onStop() { super.onStop(); lastStop = System.currentTimeMillis() }
    override fun onStart() {
        super.onStart()
        if (lastStop != 0L && System.currentTimeMillis() - lastStop > 60_000) unlocked.value = false
    }

    private fun authenticate() {
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) { unlocked.value = true; return }
        val p = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked.value = true }
        })
        p.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle("Lični organizator").setSubtitle("Potvrdi identitet")
                .setAllowedAuthenticators(auth).build()
        )
    }
}

@Composable
private fun LockScreen(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(C.bg),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Lični organizator", style = T.title)
        Spacer(Modifier.height(16.dp))
        GoldButton("Otključaj", onClick = onRetry)
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("namaz", "Namaz", Icons.Outlined.Mosque),
    Tab("reminders", "Podsjetnici", Icons.Outlined.NotificationsNone),
    Tab("notes", "Bilješke", Icons.AutoMirrored.Outlined.StickyNote2),
    Tab("books", "Knjige", Icons.AutoMirrored.Outlined.MenuBook),
    Tab("wallet", "Novčanik", Icons.Outlined.AccountBalanceWallet),
)

@Composable
private fun AppRoot(startRoute: String?, consumed: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    LaunchedEffect(startRoute) {
        if (startRoute != null && TABS.any { it.route == startRoute }) {
            nav.navigate(startRoute) { launchSingleTop = true }; consumed()
        }
    }

    fun tab(r: String) = nav.navigate(r) { popUpTo("namaz"); launchSingleTop = true }

    Column(Modifier.fillMaxSize().background(C.bg).navigationBarsPadding().imePadding()) {
        NavHost(nav, startDestination = "namaz", modifier = Modifier.weight(1f)) {
            composable("namaz") { NamazScreen(onProfile = { nav.navigate("profile") }, onMonthly = { nav.navigate("monthly") }) }
            composable("monthly") { MonthlyScreen({ nav.popBackStack() }) { nav.navigate("report/week/-/-/15") } }
            composable("profile") { ProfileScreen({ nav.popBackStack() }) { k, f, t, m -> nav.navigate("report/$k/$f/$t/$m") } }
            composable(
                "report/{kind}/{from}/{to}/{mask}",
                arguments = listOf(navArgument("mask") { type = NavType.IntType })
            ) {
                val a = it.arguments!!
                ReportScreen(a.getString("kind")!!, a.getString("from")!!, a.getString("to")!!, a.getInt("mask")) { nav.popBackStack() }
            }
            composable("reminders") { RemindersScreen() }
            composable("notes") { NotesScreen { id -> nav.navigate("note/$id") } }
            composable("note/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                NoteEditorScreen(it.arguments!!.getLong("id")) { nav.popBackStack() }
            }
            composable("books") { BooksScreen() }
            composable("wallet") { WalletScreen(onNew = { nav.navigate("newEntry") }, onSavings = { nav.navigate("savings") }) }
            composable("newEntry") { NewEntryScreen { nav.popBackStack() } }
            composable("savings") { SavingsScreen({ nav.popBackStack() }) { id -> nav.navigate("goal/$id") } }
            composable("goal/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                GoalScreen(it.arguments!!.getLong("id")) { nav.popBackStack() }
            }
        }
        if (current in TABS.map { it.route }) {
            Row(Modifier.fillMaxWidth().background(C.surface).padding(vertical = 6.dp)) {
                TABS.forEach { t ->
                    val sel = current == t.route
                    Column(
                        Modifier.weight(1f).heightIn(min = 52.dp).clickable { tab(t.route) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        Icon(t.icon, t.label, tint = if (sel) C.gold else C.muted)
                        Text(t.label, style = T.small.copy(color = if (sel) C.gold else C.muted, fontSize = 11.sp))
                    }
                }
            }
        }
    }
}
