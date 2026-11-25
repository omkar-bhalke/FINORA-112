package com.finora.app

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private enum class AuthPage {
    SignIn,
    Register,
    Reset,
}

internal fun authMessage(error: Exception, google: Boolean = false): String {
    if (error is FirebaseNetworkException) return "Check your internet connection and try again."
    if (error is FirebaseTooManyRequestsException)
        return "Too many attempts. Wait a little before trying again."
    if (google) return "Google sign-in couldn't finish. Try again or sign in with email."
    return when ((error as? FirebaseAuthException)?.errorCode) {
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_INVALID_LOGIN_CREDENTIALS",
        "ERROR_WRONG_PASSWORD",
        "ERROR_USER_NOT_FOUND" ->
            "Check your email and password. If you’ve forgotten your password, reset it below."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Try signing in with this email or resetting your password."
        "ERROR_WEAK_PASSWORD" -> "Choose a stronger password that meets the account requirements."
        "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
        "ERROR_USER_DISABLED" ->
            "Sign-in is disabled for this account. Contact your account administrator."
        "ERROR_OPERATION_NOT_ALLOWED" ->
            "Email authentication is currently unavailable. Please try again later."
        else -> "We couldn’t complete that request. Check your connection and try again."
    }
}

@Composable
fun Login(auth: FirebaseAuth) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var page by rememberSaveable { mutableStateOf(AuthPage.SignIn) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var policy by remember { mutableStateOf("") }
    val client =
        remember(ctx) {
            val id = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
            if (id == 0) null else ctx.getString(id).takeIf { it.isNotBlank() }
        }
    fun switch(next: AuthPage) {
        page = next
        password = ""
        visible = false
        error = ""
        notice = ""
    }
    BackHandler(page != AuthPage.SignIn && !busy) { switch(AuthPage.SignIn) }
    fun perform(google: Boolean = false, action: suspend () -> Unit) {
        if (busy) return
        keyboard?.hide()
        busy = true
        error = ""
        notice = ""
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e !is GetCredentialCancellationException) error = authMessage(e, google)
            } finally {
                busy = false
            }
        }
    }
    fun submit() {
        val address = email.trim()
        error =
            when {
                !Patterns.EMAIL_ADDRESS.matcher(address).matches() -> "Enter a valid email address."
                page != AuthPage.Reset && password.isEmpty() -> "Enter your password."
                page == AuthPage.Register && password.length < 6 ->
                    "Use at least 6 characters for your password."
                else -> ""
            }
        if (error.isNotEmpty()) return
        perform {
            when (page) {
                AuthPage.SignIn -> auth.signInWithEmailAndPassword(address, password).await()
                AuthPage.Register -> auth.createUserWithEmailAndPassword(address, password).await()
                AuthPage.Reset -> {
                    try {
                        auth.sendPasswordResetEmail(address).await()
                    } catch (e: FirebaseAuthException) {
                        if (e.errorCode != "ERROR_USER_NOT_FOUND") throw e
                    }
                    switch(AuthPage.SignIn)
                    notice = "If this email has an account, check your inbox for a reset link."
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 500.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BrandSurface {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Image(painterResource(R.drawable.finora_mark), null, Modifier.size(44.dp))
                    Text(
                        "FINORA",
                        color = Color.White,
                        fontSize = 21.sp,
                        letterSpacing = 3.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(17.dp))
                Text(
                    "Understand your money.\nControl your spending.",
                    color = Color(0xfff8f7e9),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Panel {
                val title =
                    when (page) {
                        AuthPage.SignIn -> "Welcome back"
                        AuthPage.Register -> "Create your account"
                        AuthPage.Reset -> "Reset your password"
                    }
                Heading(title)
                Text(
                    when (page) {
                        AuthPage.SignIn -> "Sign in to your FINORA account."
                        AuthPage.Register -> "Use your email to get started."
                        AuthPage.Reset -> "Enter the email you used to create your account."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    email,
                    {
                        email = it
                        error = ""
                        notice = ""
                    },
                    Modifier.fillMaxWidth(),
                    enabled = !busy,
                    label = { Text("Email address") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction =
                                if (page == AuthPage.Reset) ImeAction.Done else ImeAction.Next,
                        ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                if (page != AuthPage.Reset)
                    OutlinedTextField(
                        password,
                        {
                            password = it
                            error = ""
                        },
                        Modifier.fillMaxWidth(),
                        enabled = !busy,
                        label = { Text("Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation =
                            if (visible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        trailingIcon = {
                            TextButton({ visible = !visible }) {
                                Text(if (visible) "Hide" else "Show")
                            }
                        },
                    )
                if (error.isNotEmpty() || notice.isNotEmpty())
                    Text(
                        error.ifBlank { notice },
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (error.isEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    )
                if (page == AuthPage.SignIn)
                    TextButton(
                        { switch(AuthPage.Reset) },
                        Modifier.align(Alignment.End),
                        enabled = !busy,
                    ) {
                        Text("Forgot password?")
                    }
                Button(
                    { submit() },
                    Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    enabled = !busy,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else
                        Text(
                            when (page) {
                                AuthPage.SignIn -> "Sign in"
                                AuthPage.Register -> "Create account"
                                AuthPage.Reset -> "Send reset link"
                            }
                        )
                }
                if (page != AuthPage.Reset && client != null)
                    OutlinedButton(
                        {
                            perform(true) {
                                val request =
                                    GetCredentialRequest.Builder()
                                        .addCredentialOption(
                                            GetSignInWithGoogleOption.Builder(client).build()
                                        )
                                        .build()
                                val result =
                                    CredentialManager.create(ctx)
                                        .getCredential(ctx, request)
                                        .credential
                                require(
                                    result is CustomCredential &&
                                        result.type ==
                                            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                )
                                val token = GoogleIdTokenCredential.createFrom(result.data).idToken
                                auth
                                    .signInWithCredential(
                                        GoogleAuthProvider.getCredential(token, null)
                                    )
                                    .await()
                            }
                        },
                        Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = !busy,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Continue with Google")
                    }
                TextButton(
                    { switch(if (page == AuthPage.SignIn) AuthPage.Register else AuthPage.SignIn) },
                    Modifier.align(Alignment.CenterHorizontally),
                    enabled = !busy,
                ) {
                    Text(
                        if (page == AuthPage.SignIn) "New to FINORA? Create account"
                        else "Back to sign in"
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton({ policy = "Privacy policy" }) { Text("Privacy policy") }
                TextButton({ policy = "Terms of use" }) { Text("Terms of use") }
            }
        }
    }
    if (policy.isNotBlank())
        AlertDialog(
            { policy = "" },
            title = { Text(policy) },
            text = {
                Text(
                    if (policy == "Privacy policy")
                        "FINORA stores your profile and saved financial records in Firebase. Optional bank notification review runs on this device. Only approved imports are saved to your ledger. Optional Parent Connect sends authorized summaries to your verified recipient through WhatsApp."
                    else
                        "Review transactions and account details before saving or sharing them. FINORA calculates summaries from your recorded data. You control notification access and reporting consent."
                )
            },
            confirmButton = { TextButton({ policy = "" }) { Text("Close") } },
        )
}
