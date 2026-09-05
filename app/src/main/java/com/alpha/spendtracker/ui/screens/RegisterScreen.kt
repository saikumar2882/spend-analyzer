package com.alpha.spendtracker.ui.screens

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.TextButton
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

    if (signInPromptMessage != null) {
        AlertDialog(
            onDismissRequest = { signInPromptMessage = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Already registered", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(signInPromptMessage!!, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        signInPromptMessage = null
                        onNavigateToSignIn()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text("Sign in") }
            },
            dismissButton = {
                TextButton(
                    onClick = { signInPromptMessage = null },
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
                    enabled = !isLoading,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
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
            isError = passwordError,
            colors = textFieldColors,
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
                    .heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
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
