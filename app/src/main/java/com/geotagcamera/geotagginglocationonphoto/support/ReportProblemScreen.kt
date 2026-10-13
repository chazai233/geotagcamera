package com.geotagcamera.geotagginglocationonphoto.support

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.geotagcamera.geotagginglocationonphoto.ui.common.AppScaffold
import com.geotagcamera.geotagginglocationonphoto.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Write a report and hand it to the phone's email app, addressed to the
 * developer. Shows exactly what will be included; nothing is sent from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportProblemScreen(onBack: () -> Unit, fromCrash: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val crash = remember { if (fromCrash) CrashCatcher.pending(context) else null }

    var kind by rememberSaveable { mutableStateOf(ProblemReport.Kind.PROBLEM) }
    var message by rememberSaveable { mutableStateOf(if (crash != null) "The app closed unexpectedly. What I was doing: " else "") }
    var includeDevice by rememberSaveable { mutableStateOf(true) }
    var includeLog by rememberSaveable { mutableStateOf(false) }
    var includeCrash by rememberSaveable { mutableStateOf(crash != null) }

    AppScaffold(title = "Report a problem", onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.L, vertical = Spacing.S),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.fillMaxWidth().widthIn(max = 640.dp)) {
                Text(
                    "Your report opens in your email app, addressed to ${ProblemReport.EMAIL}. Nothing is sent until you press send there.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(Spacing.L))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ProblemReport.Kind.entries.forEachIndexed { i, k ->
                        SegmentedButton(
                            selected = kind == k,
                            onClick = { kind = k },
                            shape = SegmentedButtonDefaults.itemShape(i, ProblemReport.Kind.entries.size),
                            label = { Text(k.label) }
                        )
                    }
                }
                Spacer(Modifier.size(Spacing.M))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(4000) },
                    label = { Text(if (kind == ProblemReport.Kind.PROBLEM) "What happened, and what did you expect?" else "Your message") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
                )
                Spacer(Modifier.size(Spacing.S))
                Check("Include device details", ProblemReport.deviceInfo().replace("\n", " · "), includeDevice) { includeDevice = it }
                Check("Attach app log", "Recent messages from this app only. Map tile addresses are removed.", includeLog) { includeLog = it }
                if (crash != null) Check("Include crash details", "From the last time the app closed unexpectedly.", includeCrash) { includeCrash = it }
                Text(
                    "Never included: your photos, locations or capture list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.S)
                )
                Button(
                    onClick = {
                        scope.launch {
                            val log = if (includeLog) withContext(Dispatchers.IO) { ProblemReport.captureLog(context) } else null
                            val sent = ProblemReport.send(
                                context,
                                ProblemReport.subject(kind, message.ifBlank { kind.label }),
                                ProblemReport.body(message, includeDevice, if (includeCrash) crash else null),
                                log
                            )
                            if (sent) {
                                if (crash != null) CrashCatcher.clear(context)
                            } else {
                                snackbar.showSnackbar("No email app found. Use GitHub issues instead, or email ${ProblemReport.EMAIL}.")
                            }
                        }
                    },
                    enabled = message.trim().length >= 5,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.S)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(Spacing.S))
                    Text("Continue in email")
                }
                OutlinedButton(onClick = { ProblemReport.openIssues(context) }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.S)) {
                    Text("Report on GitHub instead")
                }
            }
        }
    }
}

@Composable
private fun Check(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Checkbox)
            .padding(vertical = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.size(Spacing.M))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
