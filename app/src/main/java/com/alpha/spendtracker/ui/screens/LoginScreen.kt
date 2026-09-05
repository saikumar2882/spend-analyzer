package com.alpha.spendtracker.ui.screens

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.ui.components.NotificationType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

@Composable
fun LoginScreen(
    initialEmail: String = "",
    onEmailChange: (String) -> Unit = {},
    onLoginSuccess: () -> Unit,
    onShowNotification: (String, NotificationType) -> Unit,
    onNavigateToRegister: (String) -> Unit = {}
) {
    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isSendingReset by remember { mutableStateOf(false) }
    // Set when sign-in fails in a way that suggests the user should register instead.
    var registerPromptMessage by remember { mutableStateOf<String?>(null) }
    // Set when an existing account signs in but hasn't verified its email yet.
    var showVerification by remember { mutableStateOf(false) }

    var emailTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }

    val auth = remember { FirebaseAuth.getInstance() }

    fun isValidEmail(value: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(value).matches()

    val emailError = emailTouched && email.isNotEmpty() && !isValidEmail(email)

    val onGoogleSignIn = rememberGoogleSignIn(
        onSuccess = {
            onShowNotification("Google Sign-In successful!", NotificationType.SUCCESS)
            onLoginSuccess()
        },
        onError = { msg ->
            onShowNotification(msg, NotificationType.ERROR)
            errorMessage = msg
        }
    )

    val signInAction: () -> Unit = {
        if (!isValidEmail(email) || password.isEmpty()) {
            emailTouched = true
            passwordTouched = true
            val msg = if (!isValidEmail(email)) "Enter a valid email address." else "Enter your password."
            onShowNotification(msg, NotificationType.ERROR)
            errorMessage = msg
        } else {
            isLoading = true
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { signInTask ->
                    if (!signInTask.isSuccessful) {
                        isLoading = false
                        when (val ex = signInTask.exception) {
                            is FirebaseAuthInvalidUserException -> {
                                // ERROR_USER_NOT_FOUND / ERROR_USER_DISABLED
                                if (ex.errorCode == "ERROR_USER_DISABLED") {
                                    errorMessage = "This account has been disabled. Please contact support."
                                } else {
                                    registerPromptMessage =
                                        "We couldn't find an account for $email. Would you like to register?"
                                }
                            }
                            is FirebaseAuthInvalidCredentialsException -> {
                                // Wrong password, or — when Email Enumeration Protection is
                                // enabled — a generic credential error that can also mean the
                                // email isn't registered. Offer registration either way.
                                registerPromptMessage =
                                    "Incorrect email or password. If you're new here, you can register instead."
                            }
                            else -> {
                                val msg = ex?.message
                                    ?: "Login failed. Please check your credentials or internet connection."
                                onShowNotification(msg, NotificationType.ERROR)
                                errorMessage = msg
                            }
                        }
                        return@addOnCompleteListener
                    }
                    auth.currentUser?.reload()?.addOnCompleteListener { reloadTask ->
                        isLoading = false
                        val verified = auth.currentUser?.isEmailVerified == true
                        if (verified) {
                            onShowNotification("Login successful!", NotificationType.SUCCESS)
                            onLoginSuccess()
                        } else if (reloadTask.isSuccessful) {
                            // Sign back out so MainActivity keeps showing the auth screen
                            // (and this dialog) instead of dropping straight to the Dashboard.
                            auth.signOut()
                            showVerification = true
                            onShowNotification("Please verify your email.", NotificationType.INFO)
                        } else {
                            val msg = "Sign-in successful, but couldn't verify email status: ${reloadTask.exception?.message}"
                            onShowNotification(msg, NotificationType.ERROR)
                            errorMessage = msg
                            auth.signOut()
                        }
                    }
                }
        }
    }

    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
        unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = MaterialTheme.colorScheme.error,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
    )

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Error", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(errorMessage!!, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = { errorMessage = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text("OK") }
            }
        )
    }

    if (infoMessage != null) {
        AlertDialog(
            onDismissRequest = { infoMessage = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Check your inbox", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(infoMessage!!, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = { infoMessage = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text("OK") }
            }
        )
    }

    if (registerPromptMessage != null) {
        AlertDialog(
            onDismissRequest = { registerPromptMessage = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Sign-in failed", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(registerPromptMessage!!, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        registerPromptMessage = null
                        onNavigateToRegister(email)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text("Register") }
            },
            dismissButton = {
                TextButton(
                    onClick = { registerPromptMessage = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) { Text("Cancel") }
            }
        )
    }

    if (showVerification) {
        EmailVerificationDialog(
            email = email,
            password = password,
            onVerified = {
                showVerification = false
                onShowNotification("Login successful!", NotificationType.SUCCESS)
                onLoginSuccess()
            },
            onDismiss = { showVerification = false }
        )
    }

    AuthScaffold(
        title = "Welcome back",
        subtitle = "Sign in to continue managing your spending",
        footer = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Don't have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { onNavigateToRegister(email) },
                    enabled = !isLoading,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        "Register",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    ) {
        TextField(
            value = email,
            onValueChange = {
                email = it
                emailTouched = true
                onEmailChange(it)
            },
            label = { Text("Email Address") },
            leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = emailError,
            colors = textFieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            supportingText = if (emailError) {
                { Text("Please enter a valid email address") }
            } else null
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = password,
            onValueChange = {
                password = it
                passwordTouched = true
            },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = null
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = textFieldColors
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            if (isSendingReset) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            TextButton(
                onClick = {
                    if (!isValidEmail(email)) {
                        emailTouched = true
                        val msg = "Enter a valid email address to receive a reset link."
                        onShowNotification(msg, NotificationType.ERROR)
                        errorMessage = msg
                        return@TextButton
                    }
                    isSendingReset = true
                    auth.sendPasswordResetEmail(email)
                        .addOnCompleteListener { task ->
                            isSendingReset = false
                            if (task.isSuccessful) {
                                onShowNotification("A password reset link has been sent to $email.", NotificationType.SUCCESS)
                                infoMessage = "A password reset link has been sent to $email. Please check your inbox (and spam folder)."
                            } else {
                                val msg = task.exception?.message ?: "Could not send reset email. Please try again."
                                onShowNotification(msg, NotificationType.ERROR)
                                errorMessage = msg
                            }
                        }
                },
                enabled = !isSendingReset && !isLoading,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    "Forgot Password?",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            Button(
                onClick = signInAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
            ) {
                Text(
                    "Sign In",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = "  OR  ",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            GoogleButton(text = "Continue with Google", onClick = onGoogleSignIn)
        }
    }
}
