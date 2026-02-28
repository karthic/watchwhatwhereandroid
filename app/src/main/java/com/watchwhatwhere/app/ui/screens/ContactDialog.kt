package com.watchwhatwhere.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.watchwhatwhere.app.data.api.WatchWhatWhereApi
import kotlinx.coroutines.launch

/**
 * Full-screen contact form. Submits to /inputs/contact_submit.php.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(
    onBack: () -> Unit,
    api: WatchWhatWhereApi,
    prefillName: String = "",
    prefillEmail: String = ""
) {
    var name by remember { mutableStateOf(prefillName) }
    var email by remember { mutableStateOf(prefillEmail) }
    var message by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Contact Us") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSending) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (resultMessage != null) {
                // Result state
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = resultMessage!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSuccess) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (isSuccess) {
                    Button(onClick = onBack) {
                        Text("Done")
                    }
                } else {
                    OutlinedButton(onClick = { resultMessage = null }) {
                        Text("Try Again")
                    }
                }
            } else {
                // Form state
                Text(
                    text = "Have a question, issue, or suggestion? Send us a message and we'll get back to you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSending
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSending
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    maxLines = 10,
                    enabled = !isSending
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isSending = true
                            try {
                                val response = api.submitContact(
                                    name = name.trim(),
                                    email = email.trim(),
                                    message = message.trim()
                                )
                                response.close()
                                isSuccess = true
                                resultMessage = "✓ Message sent! We'll get back to you soon."
                            } catch (e: Exception) {
                                isSuccess = false
                                resultMessage = "Failed to send: ${e.message}"
                            } finally {
                                isSending = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isSending && name.isNotBlank() && email.isNotBlank() && message.isNotBlank(),
                    shape = MaterialTheme.shapes.large
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "Send Message",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
