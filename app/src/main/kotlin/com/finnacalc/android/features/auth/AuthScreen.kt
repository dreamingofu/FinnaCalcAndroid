//
// AuthScreen.kt
//
// Port of iOS Features/Auth/AuthView.swift — sign in / sign up sheet: the
// auth actions from the web lib/auth.tsx (email+password, name on sign-up,
// needs-confirmation handling), Google OAuth, and a password-reset action.
//
// Deviations from iOS, with reasons:
//  · No Sign in with Apple — Apple's native flow has no Android equivalent.
//  · Google sign-in launches a browser tab and completes via deep link, so
//    the sheet dismisses when the auth state flips to signed-in rather than
//    when a suspending call returns (see SupabaseAuthClient).
//

package com.finnacalc.android.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.auth.AuthException
import com.finnacalc.android.core.auth.AuthManager
import com.finnacalc.android.core.designsystem.FCBadge
import com.finnacalc.android.core.designsystem.FCBadgeVariant
import com.finnacalc.android.core.designsystem.FCButton
import com.finnacalc.android.core.designsystem.FCButtonSize
import com.finnacalc.android.core.designsystem.FCButtonVariant
import com.finnacalc.android.core.designsystem.FCTextField
import com.finnacalc.android.core.designsystem.FCWordmark
import com.finnacalc.android.core.designsystem.Theme
import kotlinx.coroutines.launch

private enum class AuthMode(val label: String) {
    SignIn("Sign in"),
    SignUp("Sign up"),
}

@Composable
fun AuthScreen(
    auth: AuthManager,
    onDismiss: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.SignIn) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var infoText by rememberSaveable { mutableStateOf<String?>(null) }
    var working by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Google completes out-of-band (deep link) — dismiss once signed in.
    val user by auth.user.collectAsState()
    LaunchedEffect(user) { if (user != null) onDismiss() }

    val canSubmit = email.trim().isNotEmpty() && password.isNotEmpty() &&
        (mode == AuthMode.SignIn || name.trim().isNotEmpty())

    fun describe(e: Exception): String = e.message ?: "Something went wrong."

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FCWordmark(size = 30, modifier = Modifier.padding(top = 8.dp))

        // Segmented Sign in / Sign up control (iOS Picker.segmented).
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Theme.colors.secondary)
                .padding(3.dp),
        ) {
            AuthMode.entries.forEach { option ->
                val selected = mode == option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (selected) Theme.colors.card else Color.Transparent)
                        .clickable {
                            mode = option
                            errorText = null
                            infoText = null
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label,
                        style = Theme.sans(Theme.FontSize.sm, if (selected) FontWeight.Bold else FontWeight.SemiBold),
                        color = if (selected) Theme.colors.foreground else Theme.colors.mutedForeground,
                    )
                }
            }
        }

        if (!auth.configured) {
            FCBadge("Accounts aren't configured yet", variant = FCBadgeVariant.Secondary)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (mode == AuthMode.SignUp) {
                FCTextField("Your name", name, { name = it }, showsPlaceholder = true)
            }
            FCTextField(
                "you@example.com", email, { email = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                showsPlaceholder = true,
            )
            FCTextField(
                if (mode == AuthMode.SignUp) "Create a password (min. 6 characters)" else "Your password",
                password, { password = it },
                isSecure = true,
                showsPlaceholder = true,
            )
        }

        if (mode == AuthMode.SignIn) {
            Text(
                "Forgot password?",
                style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                color = Theme.colors.primary,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(enabled = !working) {
                        errorText = null
                        infoText = null
                        val address = email.trim()
                        if (address.isEmpty()) {
                            errorText = "Enter your email above first, then tap Forgot password."
                        } else {
                            working = true
                            scope.launch {
                                try {
                                    auth.resetPassword(address)
                                    infoText = "Password reset email sent — check your inbox."
                                } catch (e: AuthException) {
                                    errorText = describe(e)
                                }
                                working = false
                            }
                        }
                    },
            )
        }

        errorText?.let {
            Text(
                it,
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.destructive,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        infoText?.let {
            Text(
                it,
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FCButton(
            onClick = {
                errorText = null
                infoText = null
                working = true
                scope.launch {
                    try {
                        when (mode) {
                            AuthMode.SignIn -> {
                                auth.signIn(email, password)
                                onDismiss()
                            }
                            AuthMode.SignUp -> {
                                val result = auth.signUp(email, password, name)
                                if (result.needsConfirmation) {
                                    infoText = "Check your email to confirm your account, then sign in."
                                    mode = AuthMode.SignIn
                                } else {
                                    onDismiss()
                                }
                            }
                        }
                    } catch (e: AuthException) {
                        errorText = describe(e)
                    }
                    working = false
                }
            },
            size = FCButtonSize.Lg,
            enabled = !working && canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (mode == AuthMode.SignIn) "Sign in" else "Create account")
        }

        // "or" divider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Theme.colors.border)
            )
            Text("or", style = Theme.sans(Theme.FontSize.xs), color = Theme.colors.mutedForeground)
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Theme.colors.border)
            )
        }

        FCButton(
            onClick = {
                errorText = null
                infoText = null
                working = true
                scope.launch {
                    try {
                        auth.signInWithGoogle()
                        // Completion arrives via deep link → LaunchedEffect(user).
                    } catch (e: AuthException) {
                        errorText = describe(e)
                    }
                    working = false
                }
            },
            variant = FCButtonVariant.Outline,
            size = FCButtonSize.Lg,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue with Google")
        }
    }
}
