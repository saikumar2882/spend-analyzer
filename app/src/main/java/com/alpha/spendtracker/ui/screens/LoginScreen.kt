package com.alpha.spendtracker.ui.screens

import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.alpha.spendtracker.R
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.util.findActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import java.security.SecureRandom
import android.util.Base64

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onShowNotification: (String, NotificationType) -> Unit,
    onRegisteringStart: () -> Unit = {},
    onRegisteringFinished: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showVerificationModal by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }
    var isSendingReset by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    var emailTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    fun isValidEmail(email: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(email).matches()
    fun isValidPassword(password: String): Boolean {
        // Minimum 6 characters for testing, but ideally stronger
        return password.length >= 6
    }

    val emailError = emailTouched && email.isNotEmpty() && !isValidEmail(email)
    val passwordError = passwordTouched && password.isNotEmpty() && !isValidPassword(password)

    if (showVerificationModal) {
        AlertDialog(
            onDismissRequest = {
                if (!isVerifying && !isResending) {
                    showVerificationModal = false
                    verificationError = null
                    onRegisteringFinished()
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify your email")
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "We've sent a verification link to:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        email,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Open the email and click the verification link. Then come back and tap \"I've Verified\" to sign in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (verificationError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            verificationError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (isVerifying || isResending) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isVerifying = true
                        verificationError = null
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener { signInTask ->
                                if (!signInTask.isSuccessful) {
                                    isVerifying = false
                                    verificationError = "Sign-in failed: ${signInTask.exception?.message}"
                                    return@addOnCompleteListener
                                }
                                auth.currentUser?.reload()?.addOnCompleteListener { reloadTask ->
                                    isVerifying = false
                                    if (!reloadTask.isSuccessful) {
                                        verificationError = "Couldn't refresh status: ${reloadTask.exception?.message}"
                                        return@addOnCompleteListener
                                    }
                                    if (auth.currentUser?.isEmailVerified == true) {
                                        showVerificationModal = false
                                        verificationError = null
                                        onRegisteringFinished()
                                        onLoginSuccess()
                                    } else {
                                        auth.signOut()
                                        verificationError = "Email not yet verified. Please click the link in your inbox first."
                                    }
                                }
                            }
                    },
                    enabled = !isVerifying && !isResending
                ) { Text("I've Verified") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            isResending = true
                            verificationError = null
                            auth.signInWithEmailAndPassword(email, password)
                                .addOnCompleteListener { signInTask ->
                                    if (!signInTask.isSuccessful) {
                                        isResending = false
                                        verificationError = "Could not resend: ${signInTask.exception?.message}"
                                        return@addOnCompleteListener
                                    }
                                    auth.currentUser?.sendEmailVerification()
                                        ?.addOnCompleteListener { sendTask ->
                                            auth.signOut()
                                            isResending = false
                                            if (sendTask.isSuccessful) {
                                                verificationError = "Verification email resent to $email."
                                            } else {
                                                verificationError = "Resend failed: ${sendTask.exception?.message}"
                                            }
                                        }
                                }
                        },
                        enabled = !isVerifying && !isResending
                    ) { Text("Resend") }
                    TextButton(
                        onClick = {
                            showVerificationModal = false
                            verificationError = null
                            onRegisteringFinished()
                        },
                        enabled = !isVerifying && !isResending
                    ) { Text("Cancel") }
                }
            }
        )
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

    if (infoMessage != null) {
        AlertDialog(
            onDismissRequest = { infoMessage = null },
            icon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Check your inbox") },
            text = { Text(infoMessage!!) },
            confirmButton = {
                TextButton(onClick = { infoMessage = null }) { Text("OK") }
            }
        )
    }

    fun generateNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    val onGoogleSignIn: () -> Unit = {
        scope.launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(context.getString(R.string.default_web_client_id))
                    .setNonce(generateNonce())
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = context
                )

                val credential = result.credential
                if (credential is androidx.credentials.CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    
                    auth.signInWithCredential(firebaseCredential).addOnCompleteListener { authTask ->
                        if (authTask.isSuccessful) {
                            onShowNotification("Google Sign-In successful!", NotificationType.SUCCESS)
                            onLoginSuccess()
                        } else {
                            onShowNotification("Google sign-in failed: ${authTask.exception?.message}", NotificationType.ERROR)
                            errorMessage = authTask.exception?.message
                        }
                    }
                } else {
                    onShowNotification("Unexpected credential type", NotificationType.ERROR)
                }
            } catch (e: GetCredentialException) {
                onShowNotification("Google sign-in error: ${e.message}", NotificationType.ERROR)
                errorMessage = "Google sign-in error: ${e.message}"
            } catch (e: Exception) {
                onShowNotification("An error occurred: ${e.message}", NotificationType.ERROR)
                errorMessage = e.message
            }
        }
    }

    val registerAction: () -> Unit = {
        if (!isValidEmail(email) || !isValidPassword(password)) {
            emailTouched = true
            passwordTouched = true
            onShowNotification("Enter a valid email and a password of at least 6 characters", NotificationType.ERROR)
        } else {
            onRegisteringStart()
            isLoading = true
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { createTask ->
                    if (!createTask.isSuccessful) {
                        isLoading = false
                        onRegisteringFinished()
                        onShowNotification("Error: ${createTask.exception?.message}", NotificationType.ERROR)
                        errorMessage = createTask.exception?.message
                        return@addOnCompleteListener
                    }
                    auth.currentUser?.sendEmailVerification()
                        ?.addOnCompleteListener { sendTask ->
                            isLoading = false
                            auth.signOut()
                            if (sendTask.isSuccessful) {
                                verificationError = null
                                showVerificationModal = true
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Track. Save. Thrive.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome back",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sign in to continue managing your spending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        emailTouched = true
                    },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = emailError,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isSendingReset) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(
                        onClick = {
                            if (!isValidEmail(email)) {
                                emailTouched = true
                                onShowNotification("Enter a valid email address to receive a reset link.", NotificationType.ERROR)
                                errorMessage = "Enter a valid email address to receive a reset link."
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
                                        onShowNotification(task.exception?.message ?: "Could not send reset email.", NotificationType.ERROR)
                                        errorMessage = task.exception?.message
                                            ?: "Could not send reset email. Please try again."
                                    }
                                }
                        },
                        enabled = !isSendingReset && !isLoading
                    ) {
                        Text("Forgot Password?")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = {
                            if (!isValidEmail(email) || password.isEmpty()) {
                                emailTouched = true
                                passwordTouched = true
                                return@Button
                            }
                            isLoading = true
                            auth.signInWithEmailAndPassword(email, password)
                                .addOnCompleteListener { signInTask ->
                                    if (!signInTask.isSuccessful) {
                                        isLoading = false
                                        onShowNotification(signInTask.exception?.message ?: "Login failed.", NotificationType.ERROR)
                                        errorMessage = signInTask.exception?.message ?: "Login failed. Please check your credentials or internet connection."
                                        return@addOnCompleteListener
                                    }
                                    auth.currentUser?.reload()?.addOnCompleteListener { reloadTask ->
                                        isLoading = false
                                        if (reloadTask.isSuccessful) {
                                            if (auth.currentUser?.isEmailVerified == true) {
                                                onShowNotification("Login successful!", NotificationType.SUCCESS)
                                                onLoginSuccess()
                                            } else {
                                                auth.signOut()
                                                verificationError = null
                                                showVerificationModal = true
                                                onShowNotification("Please verify your email.", NotificationType.INFO)
                                            }
                                        } else {
                                            // Even if reload fails (e.g. network), try to check current state
                                            if (auth.currentUser?.isEmailVerified == true) {
                                                onShowNotification("Login successful!", NotificationType.SUCCESS)
                                                onLoginSuccess()
                                            } else {
                                                onShowNotification("Sign-in successful, but couldn't verify email status.", NotificationType.ERROR)
                                                errorMessage = "Sign-in successful, but couldn't verify email status: ${reloadTask.exception?.message}"
                                                auth.signOut()
                                            }
                                        }
                                    }
                                }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            "Sign In",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
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

                    OutlinedButton(
                        onClick = onGoogleSignIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "G",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Continue with Google",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

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
                onClick = registerAction,
                enabled = !isLoading
            ) {
                Text(
                    "Register",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
