package com.alpha.spendtracker.ui.screens

import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.alpha.spendtracker.R
import com.alpha.spendtracker.ui.theme.BrandGradientEnd
import com.alpha.spendtracker.ui.theme.BrandGradientMid
import com.alpha.spendtracker.ui.theme.BrandGradientStart
import com.alpha.spendtracker.util.findActivity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import java.security.SecureRandom

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background
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
                .size(92.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(BrandGradientStart, BrandGradientMid, BrandGradientEnd)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Track. Save. Thrive.",
            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                content()
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        footer()
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** The shared outlined "Continue with Google" button. */
@Composable
fun GoogleButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = "G",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
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

private fun generateNonce(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
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
    val activity = remember(context) { context.findActivity() }
    val googleServerClientId = stringResource(R.string.default_web_client_id)
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    return {
        scope.launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(googleServerClientId)
                    .setNonce(generateNonce())
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = activity?.let {
                    credentialManager.getCredential(request = request, context = it)
                } ?: throw Exception("Activity not found")

                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

                    auth.signInWithCredential(firebaseCredential).addOnCompleteListener { authTask ->
                        if (authTask.isSuccessful) {
                            onSuccess()
                        } else {
                            onError("Google sign-in failed: ${authTask.exception?.message}")
                        }
                    }
                } else {
                    onError("Unexpected credential type")
                }
            } catch (e: NoCredentialException) {
                onError(
                    "No Google account found on this device. Add a Google account in " +
                        "Settings, or use a device/emulator that has Google Play."
                )
            } catch (e: GetCredentialException) {
                onError("Google sign-in error: ${e.message}")
            } catch (e: Exception) {
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
        title = {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    verificationError = null
                                    onVerified()
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
                    enabled = !isVerifying && !isResending
                ) { Text("Resend") }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isVerifying && !isResending
                ) { Text("Cancel") }
            }
        }
    )
}
