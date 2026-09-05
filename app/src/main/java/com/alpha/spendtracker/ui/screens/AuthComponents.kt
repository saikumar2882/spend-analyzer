package com.alpha.spendtracker.ui.screens

import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.alpha.spendtracker.R
import com.alpha.spendtracker.ui.theme.BrandGradientEnd
import com.alpha.spendtracker.ui.theme.BrandGradientMid
import com.alpha.spendtracker.ui.theme.BrandGradientStart
import com.alpha.spendtracker.ui.theme.isAppInDarkTheme
import com.alpha.spendtracker.util.findActivity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/**
 * Shared branded layout for the auth screens (Sign In / Register): gradient
 * background, app logo, name + tagline, and a card holding the given [content].
 * [footer] renders below the card (e.g. the "Register" / "Sign in" switch link).
 */
@Composable
fun AuthScaffold(
    title: String,
    subtitle: String,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isAppInDarkTheme
    val bgModifier = if (isDark) {
        Modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    MaterialTheme.colorScheme.background
                )
            )
        )
    } else {
        Modifier.background(MaterialTheme.colorScheme.background)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .then(bgModifier)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    if (isDark) {
                        Brush.linearGradient(
                            listOf(BrandGradientStart, BrandGradientMid, BrandGradientEnd)
                        )
                    } else {
                        Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Track. Save. Thrive.",
            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        content()

        Spacer(modifier = Modifier.height(24.dp))
        footer()
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** The shared minimal "Continue with Google" button without harsh outlines or boxes. */
@Composable
fun GoogleButton(
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.25f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(
            text = "G",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Returns a click handler that runs the Credential Manager Google sign-in flow and
 * signs into Firebase. [onSuccess] fires on success; [onError] receives a
 * user-facing message. The missing-Google-account case (common on emulators
 * without Google Play) is surfaced with a clear, actionable message.
 */
@Composable
fun rememberGoogleSignIn(
    onSuccess: () -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val googleServerClientId = stringResource(R.string.default_web_client_id)
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    return {
        scope.launch {
            try {
                android.util.Log.d("GoogleSignIn", "Starting sign-in flow")
                
                android.util.Log.d("GoogleSignIn", "Client ID: $googleServerClientId")
                
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(googleServerClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                // Resolve activity again at the time of click to be safe
                val currentActivity = context.findActivity()
                if (currentActivity == null) {
                    android.util.Log.e("GoogleSignIn", "Activity is null")
                    onError("Internal error: Activity not found")
                    return@launch
                }

                val result = credentialManager.getCredential(request = request, context = currentActivity)

                val credential = result.credential
                android.util.Log.d("GoogleSignIn", "Credential received: ${credential.type}")
                
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    
                    // No nonce provided to GoogleIdOption, so we pass null here
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

                    auth.signInWithCredential(firebaseCredential).await()
                    android.util.Log.d("GoogleSignIn", "Firebase sign-in success")
                    onSuccess()
                } else {
                    android.util.Log.e("GoogleSignIn", "Unexpected credential type: ${credential.type}")
                    onError("Unexpected credential type: ${credential.type}")
                }
            } catch (e: NoCredentialException) {
                android.util.Log.e("GoogleSignIn", "NoCredentialException: ${e.message}")
                onError(
                    "No Google accounts found or sign-in is misconfigured. "
                )
            } catch (e: GetCredentialException) {
                android.util.Log.e("GoogleSignIn", "GetCredentialException: ${e.message}")
                if (e !is GetCredentialCancellationException) {
                    onError("Google sign-in error: ${e.message}")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("GoogleSignIn", "Exception: ${e.message}")
                onError("An error occurred: ${e.message}")
            }
        }
    }
}

/**
 * "Verify your email" dialog shared by the sign-in (unverified account) and
 * registration flows. Re-authenticates with [email]/[password] to check the
 * latest verification status and to resend the verification email.
 * [onVerified] fires once the email is confirmed verified; [onDismiss] closes it.
 */
@Composable
fun EmailVerificationDialog(
    email: String,
    password: String,
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    var isVerifying by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!isVerifying && !isResending) onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verify your email", color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "We've sent a verification link to:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                                    verificationError = null
                                    onVerified()
                                } else {
                                    auth.signOut()
                                    verificationError = "Email not yet verified. Please click the link in your inbox first."
                                }
                            }
                        }
                },
                enabled = !isVerifying && !isResending,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text("I've Verified") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
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
                                        verificationError = if (sendTask.isSuccessful) {
                                            "Verification email resent to $email."
                                        } else {
                                            "Resend failed: ${sendTask.exception?.message}"
                                        }
                                    }
                            }
                    },
                    enabled = !isVerifying && !isResending,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("Resend") }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isVerifying && !isResending,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("Cancel") }
            }
        }
    )
}
