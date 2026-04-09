package com.tonbil.termostat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tonbil.termostat.data.repository.TonbilRepository
import com.tonbil.termostat.ui.theme.TonbilBackground
import com.tonbil.termostat.ui.theme.TonbilOnSurface
import com.tonbil.termostat.ui.theme.TonbilOnSurfaceVariant
import com.tonbil.termostat.ui.theme.TonbilPrimary
import com.tonbil.termostat.ui.theme.TonbilSurfaceVariant
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    repository: TonbilRepository = koinInject(),
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun doLogin() {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "E-posta ve sifre gerekli"
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = repository.login(email, password)
            isLoading = false
            result.fold(
                onSuccess = { onLoginSuccess() },
                onFailure = { e ->
                    errorMessage = when {
                        e.message?.contains("401") == true -> "Hatali e-posta veya sifre"
                        e.message?.contains("timeout", ignoreCase = true) == true -> "Sunucuya ulasilamiyor"
                        else -> "Giris basarisiz: ${e.message}"
                    }
                },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TonbilBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.DeviceThermostat,
                contentDescription = null,
                tint = TonbilPrimary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "TonbilTerm",
                style = MaterialTheme.typography.headlineLarge,
                color = TonbilPrimary,
            )
            Text(
                text = "Akilli Termostat Sistemi",
                style = MaterialTheme.typography.bodyMedium,
                color = TonbilOnSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(40.dp))

            val textFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TonbilPrimary,
                unfocusedBorderColor = TonbilSurfaceVariant,
                focusedLabelColor = TonbilPrimary,
                unfocusedLabelColor = TonbilOnSurfaceVariant,
                cursorColor = TonbilPrimary,
                focusedTextColor = TonbilOnSurface,
                unfocusedTextColor = TonbilOnSurface,
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-posta") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = TonbilOnSurfaceVariant)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Sifre") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = TonbilOnSurfaceVariant)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = TonbilOnSurfaceVariant,
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { doLogin() }),
            )

            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { doLogin() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = TonbilPrimary),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = "Giris Yap",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}
