package dev.vtoppp.vtop.ui.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.PinVault
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.ui.theme.Motion
import kotlinx.coroutines.delay

private enum class PinMode { Unlock, Create, Confirm, Saved }

/**
 * The app-lock screen VTOP owns: a PIN pad in the spirit of a payments app,
 * so authentication reads as a first-class part of VTOP instead of a jump to
 * the OS credential sheet. The fingerprint key sits in the pad next to 0, and
 * a forgotten PIN is reset with the device lock.
 */
@Composable
fun PinPadScreen(
    pinSet: Boolean,
    biometricAvailable: Boolean,
    onUnlocked: () -> Unit,
    onEnrolled: () -> Unit,
    onCancel: () -> Unit = {},
    onForgotPin: () -> Unit,
    onBiometric: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vault = remember { PinVault(context) }
    var mode by remember { mutableStateOf(if (vault.hasPin()) PinMode.Unlock else PinMode.Create) }
    var digits by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var shakeKey by remember { mutableIntStateOf(0) }
    var lockedSeconds by remember { mutableIntStateOf(0) }
    var attempts by remember { mutableIntStateOf(0) }

    val profile = UserDatabase.profileFlow.collectAsState().value
    val greeting = profile?.name?.takeIf { it.isNotBlank() }?.let { "Hi $it" } ?: "VTOP is locked"

    // A refused PIN shakes, then the dots empty so the next try starts clean.
    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) {
            delay(320)
            digits = ""
        }
    }

    // The parent clears the PIN after a verified reset: fall back to enrolment.
    LaunchedEffect(pinSet) {
        if (!pinSet && mode == PinMode.Unlock) {
            mode = PinMode.Create
            digits = ""
            message = null
        }
    }

    // The lockout countdown is the one thing that has to tick, and only while
    // a lockout is actually running.
    LaunchedEffect(mode, attempts) {
        while (true) {
            val wait = vault.lockedUntil() - System.currentTimeMillis()
            if (wait <= 0) break
            lockedSeconds = ((wait + 999) / 1000).toInt()
            delay(500)
        }
        lockedSeconds = 0
    }

    // A strong biometric unlocks without touching the pad, so offer it as soon
    // as the screen is up. Enrolment counts too: the fingerprint replaces the
    // PIN, and the pad is only there for when the sensor says no.
    val lifecycleOwner = LocalLifecycleOwner.current
    var biometricOffered by remember { mutableStateOf(false) }
    LaunchedEffect(mode, biometricAvailable) {
        if (!biometricAvailable) return@LaunchedEffect
        if (mode != PinMode.Unlock && mode != PinMode.Create) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (biometricOffered) return@repeatOnLifecycle
            biometricOffered = true
            onBiometric()
        }
    }

    fun submit(pin: String) {
        when (mode) {
            PinMode.Unlock -> {
                if (vault.verify(pin)) {
                    message = null
                    onUnlocked()
                } else {
                    message = "Wrong PIN. Try again."
                    attempts++
                    shakeKey++
                }
            }
            PinMode.Create -> {
                firstPin = pin
                mode = PinMode.Confirm
                digits = ""
                message = null
            }
            PinMode.Confirm -> {
                if (pin == firstPin) {
                    vault.setPin(pin)
                    mode = PinMode.Saved
                    message = null
                    onEnrolled()
                } else {
                    mode = PinMode.Create
                    firstPin = null
                    message = "PINs did not match. Start again."
                    shakeKey++
                }
            }
            PinMode.Saved -> Unit
        }
    }

    fun onDigit(digit: Int) {
        if (lockedSeconds > 0 || mode == PinMode.Saved) return
        if (digits.length >= PinVault.PIN_LENGTH) return
        val next = digits + digit
        digits = next
        // The fourth digit is the last key on the pad, so it submits itself.
        if (next.length == PinVault.PIN_LENGTH) submit(next)
    }

    fun onDelete() {
        if (digits.isNotEmpty()) digits = digits.dropLast(1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.6f))

        Text(
            text = when (mode) {
                PinMode.Unlock -> greeting
                PinMode.Create -> "Create a PIN"
                PinMode.Confirm -> "Confirm your PIN"
                PinMode.Saved -> "PIN saved"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(14.dp))

        PinDots(
            filled = digits.length,
            error = message != null,
            shakeKey = shakeKey,
        )

        Spacer(Modifier.height(10.dp))

        // The line speaks up only when it has something to say: an error, the
        // lockout clock, or the note after the PIN is saved.
        val status = message
            ?: if (lockedSeconds > 0) "Too many attempts. Try again in ${lockedSeconds}s."
            else if (mode == PinMode.Saved) "Use it next time you open VTOP"
            else null
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = status,
                transitionSpec = {
                    fadeIn(tween(Motion.Quick)) togetherWith fadeOut(tween(Motion.Fast))
                },
                label = "pinMessage",
            ) { text ->
                if (text != null) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = if (message != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        Spacer(Modifier.weight(0.4f))

        if (mode == PinMode.Saved) {
            SavedActions(onDone = onUnlocked)
        } else {
            // One escape hatch above the pad: reset a forgotten PIN, or walk
            // away from setting one.
            TextButton(
                onClick = { if (mode == PinMode.Unlock) onForgotPin() else onCancel() },
            ) {
                Text(
                    text = if (mode == PinMode.Unlock) "Forgot PIN?" else "Not now",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (mode == PinMode.Unlock) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Keypad(
                showFingerprint = biometricAvailable && mode == PinMode.Unlock,
                onDigit = { onDigit(it) },
                onDelete = { onDelete() },
                onBiometric = onBiometric,
            )
        }
    }
}

/** Four dots that fill as digits arrive and shake when the PIN is refused. */
@Composable
private fun PinDots(filled: Int, error: Boolean, shakeKey: Int) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey == 0) return@LaunchedEffect
        shake.snapTo(0f)
        shake.animateTo(
            1f,
            keyframes {
                durationMillis = 280
                0f at 0
                -8f at 60
                8f at 140
                -4f at 210
                0f at 280
            },
        )
    }
    val tint by animateColorAsState(
        targetValue = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        animationSpec = tween(Motion.Quick),
        label = "pinDotTint",
    )

    Row(
        modifier = Modifier
            .offset(x = shake.value.dp)
            .semantics { contentDescription = "$filled of ${PinVault.PIN_LENGTH} entered" },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(PinVault.PIN_LENGTH) { index ->
            val on = index < filled
            val size by animateDpAsState(
                targetValue = if (on) 16.dp else 13.dp,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 900f),
                label = "pinDot$index",
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .then(
                        if (on) {
                            Modifier.background(tint)
                        } else {
                            Modifier.border(1.5.dp, tint, CircleShape)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun Keypad(
    showFingerprint: Boolean,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onBiometric: () -> Unit,
) {
    val rows = listOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6),
        listOf(7, 8, 9),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit -> NumKey(digit) { onDigit(digit) } }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                if (showFingerprint) {
                    Surface(
                        onClick = onBiometric,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_fingerprint),
                                contentDescription = "Use fingerprint",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            }
            NumKey(0) { onDigit(0) }
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_backspace),
                        contentDescription = "Delete digit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NumKey(digit: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** After enrolment: the fingerprint is offered on its own next time. */
@Composable
private fun SavedActions(onDone: () -> Unit) {
    Button(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(26.dp),
    ) {
        Text(
            text = "Continue",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
