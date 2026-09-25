package me.lgcode.ianua.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.lgcode.ianua.gate.GateCopy

/** The door: same copy and countdown as the extension's gate.html. */
@Composable
fun GateScreen(remainingMs: () -> Long, onGoBack: () -> Unit, onContinue: () -> Unit) {
    var remaining by remember { mutableLongStateOf(remainingMs()) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(200)
            remaining = remainingMs()
        }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🚪", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text(GateCopy.TITLE, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                GateCopy.MESSAGE,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onGoBack, modifier = Modifier.widthIn(min = 200.dp)) { Text(GateCopy.GO_BACK) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onContinue,
                enabled = remaining == 0L,
                modifier = Modifier.widthIn(min = 200.dp),
            ) {
                Text(if (remaining == 0L) GateCopy.CONTINUE else GateCopy.waiting((remaining + 999) / 1000))
            }
        }
    }
}

@Preview
@Composable
private fun GateScreenPreview() {
    IanuaTheme { GateScreen(remainingMs = { 7_000 }, onGoBack = {}, onContinue = {}) }
}
