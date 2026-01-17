package com.typeassist.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.CloudflareConfig
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: AppConfig,
    client: OkHttpClient,
    onSave: (AppConfig) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    /* ---------------- STATE ---------------- */

    var selectedProvider by remember { mutableStateOf(config.provider) }

    // Gemini
    var geminiKey by remember { mutableStateOf(config.apiKey) }
    var geminiModel by remember { mutableStateOf(config.model) }

    // Cloudflare
    var cfAccountId by remember { mutableStateOf(config.cloudflareConfig.accountId) }
    var cfApiToken by remember { mutableStateOf(config.cloudflareConfig.apiToken) }
    var cfModel by remember { mutableStateOf(config.cloudflareConfig.model) }

    // OpenRouter  ✅ FIXED
    var openRouterApiKey by remember { mutableStateOf(config.apiKey) }
    var openRouterModel by remember {
        mutableStateOf(
            if (config.provider == "openrouter" && config.model.isNotBlank())
                config.model
            else
                "mistralai/mistral-7b-instruct"
        )
    }

    var isKeyVisible by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    val providers = listOf("gemini", "cloudflare", "openrouter")

    val geminiModels = listOf(
        "gemini-2.5-flash-lite",
        "gemini-2.5-flash",
        "gemini-2.5-pro",
        "gemma-3n-e2b-it",
        "gemma-3n-e4b-it"
    )

    /* ---------------- STATUS BAR ---------------- */

    val view = LocalView.current
    val primaryColor = MaterialTheme.colorScheme.primary
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = primaryColor.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = false
        }
    }

    /* ---------------- UI ---------------- */

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = primaryColor,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {

            /* -------- PROVIDER -------- */

            Text("AI Provider", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = providerExpanded,
                onExpandedChange = { providerExpanded = !providerExpanded }
            ) {
                OutlinedTextField(
                    value = selectedProvider.uppercase(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Provider") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded)
                    },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false }
                ) {
                    providers.forEach {
                        DropdownMenuItem(
                            text = { Text(it.uppercase()) },
                            onClick = {
                                selectedProvider = it
                                providerExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Divider()
            Spacer(Modifier.height(20.dp))

            /* -------- PROVIDER CONFIG -------- */

            when (selectedProvider) {

                "gemini" -> {
                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it },
                        label = { Text("Gemini API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation =
                        if (isKeyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    if (isKeyVisible) Icons.Default.Visibility
                                    else Icons.Default.VisibilityOff,
                                    null
                                )
                            }
                        }
                    )

                    Spacer(Modifier.height(16.dp))

                    ExposedDropdownMenuBox(
                        expanded = modelExpanded,
                        onExpandedChange = { modelExpanded = !modelExpanded }
                    ) {
                        OutlinedTextField(
                            value = geminiModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Model") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded)
                            },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            geminiModels.forEach {
                                DropdownMenuItem(
                                    text = { Text(it) },
                                    onClick = {
                                        geminiModel = it
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                "cloudflare" -> {
                    OutlinedTextField(
                        value = cfAccountId,
                        onValueChange = { cfAccountId = it },
                        label = { Text("Account ID") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = cfApiToken,
                        onValueChange = { cfApiToken = it },
                        label = { Text("API Token") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation =
                        if (isKeyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    if (isKeyVisible) Icons.Default.Visibility
                                    else Icons.Default.VisibilityOff,
                                    null
                                )
                            }
                        }
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = cfModel,
                        onValueChange = { cfModel = it },
                        label = { Text("Model") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("@cf/meta/llama-3-8b-instruct")
                        }
                    )
                }

                "openrouter" -> {
                    OutlinedTextField(
                        value = openRouterApiKey,
                        onValueChange = { openRouterApiKey = it },
                        label = { Text("OpenRouter API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation =
                        if (isKeyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    if (isKeyVisible) Icons.Default.Visibility
                                    else Icons.Default.VisibilityOff,
                                    null
                                )
                            }
                        }
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = openRouterModel,
                        onValueChange = { openRouterModel = it },
                        label = { Text("Model") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("mistralai/mistral-7b-instruct")
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            /* -------- SAVE -------- */

            Button(
                onClick = {
                    val newConfig = config.copy(
                        provider = selectedProvider,
                        apiKey = when (selectedProvider) {
                            "gemini" -> geminiKey.trim()
                            "openrouter" -> openRouterApiKey.trim()
                            else -> ""
                        },
                        model = when (selectedProvider) {
                            "gemini" -> geminiModel
                            "openrouter" -> openRouterModel
                            else -> ""
                        },
                        cloudflareConfig = CloudflareConfig(
                            accountId = cfAccountId.trim(),
                            apiToken = cfApiToken.trim(),
                            model = cfModel.trim()
                        )
                    )

                    onSave(newConfig)
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Save")
            }
        }
    }
}