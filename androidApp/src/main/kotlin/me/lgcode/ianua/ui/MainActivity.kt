package me.lgcode.ianua.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.provider.Settings.Secure
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.lgcode.ianua.BuildConfig
import me.lgcode.ianua.R
import me.lgcode.ianua.ianua
import me.lgcode.ianua.service.DebugDump
import me.lgcode.ianua.service.IanuaAccessibilityService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { IanuaTheme { HomeScreen() } }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val app = context.ianua
    val scope = rememberCoroutineScope()
    val enabled by app.settings.enabled.collectAsStateWithLifecycle(initialValue = true)
    val rules by app.rules.current.collectAsStateWithLifecycle()
    var serviceOn by remember { mutableStateOf(isServiceEnabled(context)) }
    var youtubeInstalled by remember { mutableStateOf(isYouTubeInstalled(context)) }
    LifecycleResumeEffect(Unit) {
        // The user comes back from Accessibility settings: re-read.
        serviceOn = isServiceEnabled(context)
        youtubeInstalled = isYouTubeInstalled(context)
        onPauseOrDispose {}
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.toggle_label), Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { scope.launch { app.settings.setEnabled(it) } })
                    }
                    Text(
                        stringResource(
                            when {
                                !serviceOn -> R.string.status_service_off
                                enabled -> R.string.status_active
                                else -> R.string.status_paused
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!youtubeInstalled) Text(stringResource(R.string.youtube_missing), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.rules_version, rules.version.toString()), style = MaterialTheme.typography.labelSmall)
                }
            }

            if (!serviceOn) {
                // Prominent disclosure, shown before the user is sent to settings (ADR-0004).
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.disclosure_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.disclosure_body), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { context.startActivity(Intent(ACTION_ACCESSIBILITY_SETTINGS)) }) {
                            Text(stringResource(R.string.disclosure_accept))
                        }
                    }
                }
                if (needsRestrictedSettingsHint(context)) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.restricted_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.restricted_body), style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = {
                                context.startActivity(
                                    Intent(ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                )
                            }) { Text(stringResource(R.string.restricted_action)) }
                        }
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                TextButton(onClick = {
                    DebugDump.arm()
                    Toast.makeText(context, R.string.debug_dump_armed, Toast.LENGTH_LONG).show()
                }) { Text(stringResource(R.string.debug_dump)) }
            }
        }
    }
}

private fun isServiceEnabled(context: Context): Boolean {
    val component = ComponentName(context, IanuaAccessibilityService::class.java)
    val enabled = Secure.getString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
}

private fun isYouTubeInstalled(context: Context): Boolean = try {
    context.packageManager.getPackageInfo("com.google.android.youtube", 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

/** Android 13+ blocks accessibility for apps not installed from an app store session. */
private fun needsRestrictedSettingsHint(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val installer = runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName }.getOrNull()
    return installer != "com.android.vending"
}
