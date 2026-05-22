package com.alpha.spendtracker.ui.screens

import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Track if user has touched the fields to avoid showing error on empty start
    var emailTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    // Validation Helpers
    fun isValidEmail(email: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(email).matches()
    fun isValidPassword(password: String): Boolean {
        val passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$"
        return password.matches(passwordPattern.toRegex())
    }

    val emailError = emailTouched && email.isNotEmpty() && !isValidEmail(email)
    val passwordError = passwordTouched && password.isNotEmpty() && !isValidPassword(password)

    // Error Dialog
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Authentication Error") },
            text = { Text(errorMessage!!, textAlign = TextAlign.Center) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("OK") }
            }
        )
    }

    // Google Sign-In Setup
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(com.alpha.spendtracker.R.string.default_web_client_id))
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) onLoginSuccess()
                else errorMessage = "Google sign-in failed. Please try again."
            }
        } catch (e: ApiException) {
            errorMessage = "Google sign-in error: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SpendWise", 
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Master your finances with ease",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { 
                email = it
                emailTouched = true
            },
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = emailError,
            supportingText = {
                if (emailError) {
                    Text("Enter a valid email address", color = MaterialTheme.colorScheme.error)
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { 
                password = it
                passwordTouched = true
            },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = passwordError,
            supportingText = {
                if (passwordError) {
                    Text(
                        "Must be 8+ chars with Upper, Lower, Number & Symbol",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(24.dp))

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
                        .addOnCompleteListener { task ->
                            isLoading = false
                            if (task.isSuccessful) onLoginSuccess()
                            else errorMessage = task.exception?.message ?: "Login failed"
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                enabled = !emailError && !passwordError
            ) { 
                Text("Login", modifier = Modifier.padding(vertical = 4.dp)) 
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = { launcher.launch(googleSignInClient.signInIntent) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) { 
                Text("Sign in with Google", modifier = Modifier.padding(vertical = 4.dp)) 
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Box for side-by-side buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        if (!isValidEmail(email)) {
                            emailTouched = true
                            return@TextButton
                        }
                        
                        val actionCodeSettings = ActionCodeSettings.newBuilder()
                            .setUrl("https://spendwise.page.link/finishSignUp")
                            .setHandleCodeInApp(true)
                            .setAndroidPackageName("com.alpha.spendtracker", true, null)
                            .build()
                        
                        auth.sendSignInLinkToEmail(email, actionCodeSettings)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Toast.makeText(context, "Sign-in link sent to $email", Toast.LENGTH_LONG).show()
                                } else {
                                    errorMessage = task.exception?.message ?: "Could not send link"
                                }
                            }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !emailError
                ) { 
                    Text("Email Link", fontSize = 13.sp) 
                }

                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

                TextButton(
                    onClick = {
                        if (!isValidEmail(email) || !isValidPassword(password)) {
                            emailTouched = true
                            passwordTouched = true
                            return@TextButton
                        }
                        
                        isLoading = true
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) onLoginSuccess()
                                else errorMessage = task.exception?.message ?: "Registration failed"
                            }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !emailError && !passwordError
                ) { 
                    Text("Register", fontSize = 13.sp) 
                }
            }
        }
    }
}
