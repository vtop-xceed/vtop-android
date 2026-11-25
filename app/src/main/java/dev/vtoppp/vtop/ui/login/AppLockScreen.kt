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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.PinVault
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app lock VTOP owns, in two pieces:
 *
 * - [AppLockScreen] is the lock itself. A strong biometric is offered the
 *   moment the screen is up, so the pad is the fallback, not the first thing
 *   to type.
 * - [PinSetupScreen] is the one-off flow that sets a PIN. It is not part of
 *   the lock: the PIN is the extra key that opens the pad when the sensor
 *   says no.
 */

/** The two steps of setting a PIN. */
internal enum class PinSetupStep { Create, Confirm }

/** What the pad does with a full PIN. */
internal sealed interface PinSetupOutcome {
    /** The first entry is kept; ask for it once more. */
    data object NeedsConfirm : PinSetupOutcome

    /** Both entries match, so the PIN is good. */
    data object Saved : PinSetupOutcome

    /** The entries differ, so start over. */
    data object Mismatch : PinSetupOutcome
}

/**
 * The setup rule, kept pure so the flow can be checked without a device.
 */
internal fun pinSetupOutcome(
    step: PinSetupStep,
    first: String?,
    entered: String,
): PinSetupOutcome = when (step) {
    PinSetupStep.Create -> PinSetupOutcome.NeedsConfirm
    PinSetupStep.Confirm ->
        if (first == entered) PinSetupOutcome.Saved else PinSetupOutcome.Mismatch
}

/**
 * The lock screen. The fingerprint is offered on its own; a PIN, if one was
 * set, is typed on the pad below it.
 */
@Composable
fun AppLockScreen(
    pinSet: Boolean,
    biometricAvailable: Boolean,
    banner: String? = null,
    onUnlocked: () -> Unit,
    onForgotPin: () -> Unit,
    onDeviceAuth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vault = remember { PinVault(context) }
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var digits by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var shakeKey by remember { mutableIntStateOf(0) }
    var lockedSeconds by remember { mutableIntStateOf(0) }
    var attempts by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val profile = UserDatabase.profileFlow.collectAsState().value
    val greeting = profile?.name?.takeIf { it.isNotBlank() }?.let { "Hi $it" } ?: "VTOP is locked"

    // A refused PIN shakes, then the dots empty so the next try starts clean.
    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) {
            delay(320)
            digits = ""
        }
    }

    // The lockout countdown is the one thing that has to tick, and only while
    // a lockout is actually running.
    LaunchedEffect(attempts) {
        while (true) {
            val wait = vault.lockedUntil() - System.currentTimeMillis()
            if (wait <= 0) break
            lockedSeconds = ((wait + 999) / 1000).toInt()
            delay(500)
        }
        lockedSeconds = 0
    }

    // A strong biometric unlocks without touching the pad, so offer it as soon
    // as the screen is up.
    var biometricOffered by remember { mutableStateOf(false) }
    LaunchedEffect(biometricAvailable) {
        if (!biometricAvailable) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (biometricOffered) return@repeatOnLifecycle
            biometricOffered = true
            onDeviceAuth()
        }
    }

    fun submit(pin: String) {
        if (isChecking) return
        isChecking = true
        scope.launch {
            val ok = withContext(Dispatchers.Default) {
                vault.verify(pin)
            }
            isChecking = false
            if (ok) {
                message = null
                onUnlocked()
            } else {
                message = "Wrong PIN. Try again."
                attempts++
                shakeKey++
            }
        }
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
            text = greeting,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(14.dp))

        if (pinSet) {
            PinDots(
                filled = digits.length,
                error = message != null,
                shakeKey = shakeKey,
            )

            Spacer(Modifier.height(10.dp))
        }

        StatusLine(
            text = message
                ?: if (lockedSeconds > 0) "Too many attempts. Try again in ${lockedSeconds}s."
                else banner,
            isError = message != null || lockedSeconds > 0,
        )

        Spacer(Modifier.weight(0.4f))

        if (pinSet) {
            TextButton(onClick = onForgotPin) {
                Text(
                    text = "Forgot PIN?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Keypad(
                showFingerprint = biometricAvailable,
                onDigit = { digit ->
                    if (isChecking || lockedSeconds > 0) return@Keypad
                    if (digits.length >= PinVault.PIN_LENGTH) return@Keypad
                    val next = digits + digit
                    digits = next
                    // The fourth digit is the last key on the pad, so it
                    // submits itself.
                    if (next.length == PinVault.PIN_LENGTH) submit(next)
                },
                onDelete = { if (!isChecking && digits.isNotEmpty()) digits = digits.dropLast(1) },
                onDeviceAuth = onDeviceAuth,
            )
        } else {
            // No PIN: the device itself is the lock, so one key takes the
            // whole stage.
            AuthKey(
                onClick = onDeviceAuth,
                icon = if (biometricAvailable) R.drawable.ic_fingerprint else R.drawable.ic_lock,
                size = 72.dp,
                iconSize = 34.dp,
                description = "Unlock",
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (biometricAvailable) {
                    "Touch the sensor to unlock"
                } else {
                    "Use your screen lock to unlock"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Sets a PIN: type it, type it again, done. The fingerprint never appears
 * here, because a PIN is the fallback for when the sensor says no.
 */
@Composable
fun PinSetupScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vault = remember { PinVault(context) }
    var step by remember { mutableStateOf(PinSetupStep.Create) }
    var first by remember { mutableStateOf<String?>(null) }
    var digits by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var shakeKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) {
            delay(320)
            digits = ""
        }
    }

    fun submit(pin: String) {
        when (pinSetupOutcome(step, first, pin)) {
            PinSetupOutcome.NeedsConfirm -> {
                first = pin
                step = PinSetupStep.Confirm
                digits = ""
                message = null
            }
            PinSetupOutcome.Saved -> {
                vault.setPin(pin)
                message = null
                onDone()
            }
            PinSetupOutcome.Mismatch -> {
                first = null
                step = PinSetupStep.Create
                message = "PINs did not match. Start again."
                shakeKey++
            }
        }
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
            text = if (step == PinSetupStep.Create) "Create a PIN" else "Confirm your PIN",
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

        StatusLine(text = message, isError = message != null)

        Spacer(Modifier.weight(0.4f))

        TextButton(onClick = onCancel) {
            Text(
                text = "Not now",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Keypad(
            showFingerprint = false,
            onDigit = { digit ->
                if (digits.length >= PinVault.PIN_LENGTH) return@Keypad
                val next = digits + digit
                digits = next
                if (next.length == PinVault.PIN_LENGTH) submit(next)
            },
            onDelete = { if (digits.isNotEmpty()) digits = digits.dropLast(1) },
            onDeviceAuth = {},
        )
    }
}

/** One line of feedback: an error, a lockout clock, or a note. */
@Composable
private fun StatusLine(text: String?, isError: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                fadeIn(tween(Motion.Quick)) togetherWith fadeOut(tween(Motion.Fast))
            },
            label = "pinMessage",
        ) { shown ->
            if (shown != null) {
                Text(
                    text = shown,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
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

/**
 * The pad. The fingerprint key sits left of 0 and backspace right of it, and
 * the fourth digit submits itself.
 */
@Composable
private fun Keypad(
    showFingerprint: Boolean,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onDeviceAuth: () -> Unit,
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
                    AuthKey(
                        onClick = onDeviceAuth,
                        icon = R.drawable.ic_fingerprint,
                        size = 56.dp,
                        iconSize = 26.dp,
                        description = "Use fingerprint",
                    )
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

/** The one round key that is not a digit: the fingerprint or the screen lock. */
@Composable
private fun AuthKey(
    onClick: () -> Unit,
    icon: Int,
    size: Dp,
    iconSize: Dp,
    description: String,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
