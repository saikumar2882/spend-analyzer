package com.alpha.spendtracker.ui.screens

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.ui.components.NotificationType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException

@Composable
fun RegisterScreen(
    initialEmail: String = "",
    onEmailChange: (String) -> Unit = {},
    onLoginSuccess: () -> Unit,
    onShowNotification: (String, NotificationType) -> Unit,
    onRegisteringStart: () -> Unit = {},
    onRegisteringFinished: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {}
) {
    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Set when the email is already registered — offers to switch to Sign In.
    var signInPromptMessage by remember { mutableStateOf<String?>(null) }
    var showVerification by remember { mutableStateOf(false) }

    var emailTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }

    val auth = remember { FirebaseAuth.getInstance() }

    fun isValidEmail(value: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(value).matches()
    fun isValidPassword(value: String): Boolean = value.length >= 6

    val emailError = emailTouched && email.isNotEmpty() && !isValidEmail(email)
    val passwordError = passwordTouched && password.isNotEmpty() && !isValidPassword(password)

    val onGoogleSignIn = rememberGoogleSignIn(
        onSuccess = {
            onShowNotification("Signed in with Google!", NotificationType.SUCCESS)
            onLoginSuccess()
        },
        onError = { msg ->
            onShowNotification(msg, NotificationType.ERROR)
            errorMessage = msg
        }
    )

    val registerAction: () -> Unit = {
        if (!isValidEmail(email) || !isValidPassword(password)) {
            emailTouched = true
            passwordTouched = true
            val msg = when {
                !isValidEmail(email) && !isValidPassword(password) ->
                    "Enter a valid email address and a password of at least 6 characters."
                !isValidEmail(email) -> "Enter a valid email address."
                else -> "Password must be at least 6 characters."
            }
            onShowNotification(msg, NotificationType.ERROR)
            errorMessage = msg
        } else {
            onRegisteringStart()
            isLoading = true
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { createTask ->
                    if (!createTask.isSuccessful) {
                        isLoading = false
                        onRegisteringFinished()
                        val ex = createTask.exception
                        if (ex is FirebaseAuthUserCollisionException) {
                            signInPromptMessage =
                                "An account already exists for $email. Please sign in instead."
                        } else {
                            val msg = ex?.message ?: "Registration failed. Please try again."
                            onShowNotification("Error: $msg", NotificationType.ERROR)
                            errorMessage = msg
                        }
                        return@addOnCompleteListener
                    }
                    auth.currentUser?.sendEmailVerification()
                        ?.addOnCompleteListener { sendTask ->
                            isLoading = false
                            auth.signOut()
                            if (sendTask.isSuccessful) {
                                showVerification = true
                                onShowNotification("Verification email sent!", NotificationType.SUCCESS)
                            } else {
                                onRegisteringFinished()
                                onShowNotification("Error: ${sendTask.exception?.message}", NotificationType.ERROR)
                                errorMessage = "Account created, but the verification email could not be sent: " +
                                    "${sendTask.exception?.message}. Try signing in and tap \"Resend\"."
                            }
                        }
                }
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Error") },
            text = { Text(errorMessage!!) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("OK") }
            }
        )
    }

    if (signInPromptMessage != null) {
        AlertDialog(
            onDismissRequest = { signInPromptMessage = null },
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Already registered") },
            text = { Text(signInPromptMessage!!) },
            confirmButton = {
                Button(onClick = {
                    signInPromptMessage = null
                    onNavigateToSignIn()
                }) { Text("Sign in") }
            },
            dismissButton = {
                TextButton(onClick = { signInPromptMessage = null }) { Text("Cancel") }
            }
        )
    }

    if (showVerification) {
        EmailVerificationDialog(
            email = email,
            password = password,
            onVerified = {
                showVerification = false
                onRegisteringFinished()
                onLoginSuccess()
            },
            onDismiss = {
                showVerification = false
                onRegisteringFinished()
            }
        )
    }

    AuthScaffold(
        title = "Create your account",
        subtitle = "Sign up to start tracking your spending",
        footer = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Already have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onNavigateToSignIn,
                    enabled = !isLoading
                ) {
                    Text(
                        "Sign in",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    ) {
        OutlinedTextField(
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
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            supportingText = if (emailError) {
                { Text("Please enter a valid email address") }
            } else null
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordTouched = true
            },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
            isError = passwordError,
            shape = RoundedCornerShape(14.dp),
            supportingText = if (passwordError) {
                { Text("Password must be at least 6 characters") }
            } else null
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            Button(
                onClick = registerAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
            ) {
                Text(
                    "Create account",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "  OR  ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(20.dp))

            GoogleButton(text = "Continue with Google", onClick = onGoogleSignIn)
        }
    }
}
