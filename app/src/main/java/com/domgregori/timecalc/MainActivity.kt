package com.domgregori.timecalc

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalClipboardManager
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domgregori.timecalc.ui.theme.TimeCalcTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimeCalcTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TimeCalculatorScreen(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding
                    )
                }
            }
        }
    }
}

enum class InputMode {
    HOURS, MINUTES, SECONDS, MULTIPLIER
}

data class HistoryEntry(
    val expression: String,
    val result: String
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun TimeCalculatorScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var display by remember { mutableStateOf("00:00:00") }
    var currentInput by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf(0) }
    var minutes by remember { mutableStateOf(0) }
    var seconds by remember { mutableStateOf(0) }
    var inputMode by remember { mutableStateOf(InputMode.HOURS) }

    var storedTime by remember { mutableStateOf<Time?>(null) }
    var operation by remember { mutableStateOf<String?>(null) }
    var showingResult by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("timecalc_settings", Context.MODE_PRIVATE) }
    var useSeconds by remember { mutableStateOf(prefs.getBoolean("use_seconds", true)) }
    var showActionsDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    var replaceOnNextInput by remember { mutableStateOf(false) }
    val historyTextSizeSp = 20f

    LaunchedEffect(useSeconds) {
        prefs.edit().putBoolean("use_seconds", useSeconds).apply()
    }
    var keypadHeightPx by remember { mutableStateOf(0) }

    fun updateDisplay() {
        fun formatTimeDisplay(h: Int, m: Int, s: Int): String {
            val totalSeconds = Time(h, m, s).toSeconds()
            val sign = if (totalSeconds < 0) "-" else ""
            val absTime = Time.fromSeconds(kotlin.math.abs(totalSeconds))
            return if (useSeconds) {
                String.format("%s%02d:%02d:%02d", sign, absTime.hours, absTime.minutes, absTime.seconds)
            } else {
                String.format("%s%02d:%02d", sign, absTime.hours, absTime.minutes)
            }
        }

        display = if (showingResult) {
            formatTimeDisplay(hours, minutes, seconds)
        } else {
            when (inputMode) {
                InputMode.HOURS -> {
                    if (useSeconds) {
                        String.format("%s:%02d:%02d", currentInput.ifEmpty { "00" }, minutes, seconds)
                    } else {
                        String.format("%s:%02d", currentInput.ifEmpty { "00" }, minutes)
                    }
                }
                InputMode.MINUTES -> {
                    if (useSeconds) {
                        String.format("%02d:%s:%02d", hours, currentInput.ifEmpty { "00" }, seconds)
                    } else {
                        String.format("%02d:%s", hours, currentInput.ifEmpty { "00" })
                    }
                }
                InputMode.SECONDS -> String.format("%02d:%02d:%s", hours, minutes, currentInput.ifEmpty { "00" })
                InputMode.MULTIPLIER -> {
                    val timeStr = if (useSeconds) {
                        storedTime?.let { formatTimeDisplay(it.hours, it.minutes, it.seconds) } ?: "00:00:00"
                    } else {
                        storedTime?.let { formatTimeDisplay(it.hours, it.minutes, it.seconds) } ?: "00:00"
                    }
                    val opSymbol = operation ?: "×"
                    String.format("%s %s %s", timeStr, opSymbol, currentInput.ifEmpty { "0" })
                }
            }
        }
    }

    fun handleNumberInput(number: String) {
        if (showingResult) {
            hours = 0
            minutes = 0
            seconds = 0
            currentInput = ""
            showingResult = false
            inputMode = InputMode.HOURS
        }

        currentInput = if (replaceOnNextInput) {
            replaceOnNextInput = false
            number
        } else {
            currentInput + number
        }

        when (inputMode) {
            InputMode.HOURS -> {
                val value = currentInput.toIntOrNull() ?: 0
                if (currentInput.length >= 2) {
                    hours = value
                    currentInput = minutes.toString()
                    inputMode = InputMode.MINUTES
                    replaceOnNextInput = true
                }
            }
            InputMode.MINUTES -> {
                val value = currentInput.toIntOrNull() ?: 0
                if (currentInput.length >= 2 || value >= 6) {
                    minutes = value.coerceAtMost(59)
                    if (useSeconds) {
                        currentInput = seconds.toString()
                        inputMode = InputMode.SECONDS
                        replaceOnNextInput = true
                    } else {
                        showingResult = true
                    }
                }
            }
            InputMode.SECONDS -> {
                val value = currentInput.toIntOrNull() ?: 0
                if (currentInput.length >= 2 || value >= 6) {
                    seconds = value.coerceAtMost(59)
                    currentInput = ""
                    showingResult = true
                }
            }
            InputMode.MULTIPLIER -> {
                if (currentInput.count { it == '.' } > 1) {
                    currentInput = currentInput.dropLast(1)
                }
            }
        }
        updateDisplay()
    }

    fun handleOperation(op: String) {
        if (currentInput.isNotEmpty()) {
            when (inputMode) {
                InputMode.HOURS -> hours = currentInput.toIntOrNull() ?: 0
                InputMode.MINUTES -> minutes = currentInput.toIntOrNull()?.coerceAtMost(59) ?: 0
                InputMode.SECONDS -> seconds = currentInput.toIntOrNull()?.coerceAtMost(59) ?: 0
                InputMode.MULTIPLIER -> {}
            }
            currentInput = ""
        }

        if (op == "×" || op == "÷") {
            storedTime = Time(hours, minutes, seconds)
            operation = op
            inputMode = InputMode.MULTIPLIER
            currentInput = ""
            showingResult = false
            updateDisplay()
        } else {
            storedTime = Time(hours, minutes, seconds)
            operation = op
            hours = 0
            minutes = 0
            seconds = 0
            inputMode = InputMode.HOURS
            display = "00:00:00"
        }
    }

    fun calculate() {
        if (currentInput.isNotEmpty()) {
            when (inputMode) {
                InputMode.HOURS -> hours = currentInput.toIntOrNull() ?: 0
                InputMode.MINUTES -> minutes = currentInput.toIntOrNull()?.coerceAtMost(59) ?: 0
                InputMode.SECONDS -> seconds = currentInput.toIntOrNull()?.coerceAtMost(59) ?: 0
                InputMode.MULTIPLIER -> {}
            }
        }

        val currentTime = Time(hours, minutes, seconds)
        val result = when (operation) {
            "+" -> storedTime?.let { TimeCalculator.add(it, currentTime) }
            "-" -> storedTime?.let { TimeCalculator.subtract(it, currentTime) }
            "×" -> {
                val multiplier = currentInput.toDoubleOrNull() ?: 1.0
                storedTime?.let { TimeCalculator.multiply(it, multiplier) }
            }
            "÷" -> {
                val divisor = currentInput.toDoubleOrNull() ?: 1.0
                storedTime?.let { TimeCalculator.divide(it, divisor) }
            }
            else -> currentTime
        }

        result?.let {
            fun formatHistoryTime(time: Time): String {
                return if (useSeconds) {
                    time.toFormattedString()
                } else {
                    String.format("%02d:%02d", time.hours, time.minutes)
                }
            }

            val expression = when (operation) {
                "+" -> "${storedTime?.let { formatHistoryTime(it) }} + ${formatHistoryTime(currentTime)}"
                "-" -> "${storedTime?.let { formatHistoryTime(it) }} - ${formatHistoryTime(currentTime)}"
                "×" -> "${storedTime?.let { formatHistoryTime(it) }} × ${currentInput}"
                "÷" -> "${storedTime?.let { formatHistoryTime(it) }} ÷ ${currentInput}"
                else -> ""
            }

            if (expression.isNotEmpty()) {
                history = history + HistoryEntry(expression, formatHistoryTime(it))
            }

            hours = it.hours
            minutes = it.minutes
            seconds = it.seconds
            showingResult = true
            updateDisplay()
        }

        currentInput = ""
        storedTime = null
        operation = null
        inputMode = InputMode.HOURS
    }

    fun clear() {
        if (currentInput.isNotEmpty()) {
            currentInput = ""
            updateDisplay()
            return
        }

        if (hours == 0 && minutes == 0 && seconds == 0) {
            storedTime = null
            operation = null
            showingResult = false
            updateDisplay()
            return
        }

        hours = 0
        minutes = 0
        seconds = 0
        inputMode = InputMode.HOURS
        showingResult = false
        updateDisplay()
    }

    fun backspace() {
        if (inputMode == InputMode.MULTIPLIER) {
            if (currentInput.isNotEmpty()) {
                currentInput = currentInput.dropLast(1)
                updateDisplay()
            }
            return
        }

        if (currentInput.isNotEmpty()) {
            currentInput = currentInput.dropLast(1)
            updateDisplay()
            return
        }

        if (showingResult) {
            showingResult = false
        }

        fun backspaceSegment(value: Int): String {
            val updated = value.toString().dropLast(1)
            return updated
        }

        when (inputMode) {
            InputMode.SECONDS -> {
                if (seconds > 0) {
                    currentInput = backspaceSegment(seconds)
                    seconds = 0
                    updateDisplay()
                } else {
                    inputMode = InputMode.MINUTES
                    backspace()
                }
            }
            InputMode.MINUTES -> {
                if (minutes > 0) {
                    currentInput = backspaceSegment(minutes)
                    minutes = 0
                    updateDisplay()
                } else {
                    inputMode = InputMode.HOURS
                    backspace()
                }
            }
            InputMode.HOURS -> {
                if (hours > 0) {
                    currentInput = backspaceSegment(hours)
                    hours = 0
                    updateDisplay()
                }
            }
            InputMode.MULTIPLIER -> {}
        }
    }

    fun clearHistory() {
        history = emptyList()
    }

    fun loadFromHistory(resultTime: String) {
        val parts = resultTime.split(":")
        if (parts.size == 3) {
            hours = parts[0].toIntOrNull() ?: 0
            minutes = parts[1].toIntOrNull() ?: 0
            seconds = parts[2].toIntOrNull() ?: 0
            currentInput = ""
            inputMode = InputMode.HOURS
            showingResult = true
            display = resultTime
        } else if (parts.size == 2) {
            hours = parts[0].toIntOrNull() ?: 0
            minutes = parts[1].toIntOrNull() ?: 0
            seconds = 0
            currentInput = ""
            inputMode = InputMode.HOURS
            showingResult = true
            display = resultTime
        }
    }

    fun modulus24() {
        if (currentInput.isNotEmpty()) {
            when (inputMode) {
                InputMode.HOURS -> hours = currentInput.toIntOrNull() ?: 0
                InputMode.MINUTES -> minutes = currentInput.toIntOrNull()?.coerceAtMost(59) ?: 0
                InputMode.SECONDS -> seconds = currentInput.toIntOrNull()?.coerceAtMost(59) ?: 0
                InputMode.MULTIPLIER -> {}
            }
        }

        val currentTime = Time(hours, minutes, seconds)
        val totalSeconds = currentTime.toSeconds()
        val secondsIn24Hours = 24L * 60 * 60
        val moduloSeconds = ((totalSeconds % secondsIn24Hours) + secondsIn24Hours) % secondsIn24Hours
        val result = Time.fromSeconds(moduloSeconds)

        hours = result.hours
        minutes = result.minutes
        seconds = result.seconds
        currentInput = ""
        inputMode = InputMode.HOURS
        showingResult = true
        updateDisplay()
    }

    fun loadCurrentTime() {
        val calendar = Calendar.getInstance()
        hours = calendar.get(Calendar.HOUR_OF_DAY)
        minutes = calendar.get(Calendar.MINUTE)
        seconds = calendar.get(Calendar.SECOND)
        currentInput = ""
        inputMode = InputMode.HOURS
        showingResult = true
        updateDisplay()
    }

    fun parseTimeInput(text: String): Time? {
        val trimmed = text.trim()
        if (!trimmed.contains(":")) return null

        val parts = trimmed.split(":")
        if (parts.size !in 2..3) return null

        val parsed = parts.map { it.toIntOrNull() }
        if (parsed.any { it == null }) return null

        val h = parsed[0] ?: return null
        val m = parsed[1] ?: return null
        val s = if (parts.size == 3) parsed[2] ?: return null else 0

        if (m !in 0..59 || s !in 0..59) return null
        return Time(h, m, s)
    }

    fun pasteFromClipboard() {
        val text = clipboardManager.getText()?.text?.trim().orEmpty()
        if (text.isEmpty()) return

        if (inputMode == InputMode.MULTIPLIER) {
            val number = text.toDoubleOrNull()
            if (number != null) {
                currentInput = text
                updateDisplay()
            }
            return
        }

        val time = parseTimeInput(text) ?: return
        hours = time.hours
        minutes = time.minutes
        seconds = time.seconds
        currentInput = ""
        showingResult = true
        updateDisplay()
    }

    // Root container for main layout + overlays.
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Main vertical layout: history, display, gap, keypad.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp)
        ) {
            if (history.isNotEmpty()) {
                // Scrollable history list (latest at bottom).
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = true
            ) {
                items(history.reversed()) { entry ->
                    // History entry card (tap to load, long-press to copy).
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { loadFromHistory(entry.result) },
                                    onLongPress = {
                                        clipboardManager.setText(AnnotatedString(entry.result))
                                    }
                                )
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = "${entry.expression} = ${entry.result}",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = historyTextSizeSp.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            }

            // Push display + keypad toward the bottom when history is short.
            Spacer(modifier = Modifier.weight(1f))

            // Display area container (holds operation line + time display).
            Box(modifier = Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    // Display card content: operation line + time display + action button.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                if (operation != null) {
                    fun formatOpTime(time: Time): String {
                        val totalSeconds = time.toSeconds()
                        val sign = if (totalSeconds < 0) "-" else ""
                        val absTime = Time.fromSeconds(kotlin.math.abs(totalSeconds))
                        return if (useSeconds) {
                            String.format("%s%02d:%02d:%02d", sign, absTime.hours, absTime.minutes, absTime.seconds)
                        } else {
                            String.format("%s%02d:%02d", sign, absTime.hours, absTime.minutes)
                        }
                    }
                    Text(
                        text = "${storedTime?.let { formatOpTime(it) } ?: ""} ${operation ?: ""}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.End
                    )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    val highlightColor = MaterialTheme.colorScheme.primary
                    val normalColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

                    val annotatedDisplay = if (showingResult) {
                        buildAnnotatedString { append(display) }
                    } else if (inputMode != InputMode.MULTIPLIER) {
                        buildAnnotatedString {
                            val hoursStr = String.format("%02d", hours)
                            val minutesStr = String.format("%02d", minutes)
                            val secondsStr = String.format("%02d", seconds)
                            val currentInputStr = currentInput.ifEmpty { "00" }

                            when (inputMode) {
                                InputMode.HOURS -> {
                                    withStyle(style = SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold)) {
                                        append(currentInputStr)
                                    }
                                    append(":")
                                    withStyle(style = SpanStyle(color = normalColor)) {
                                        append(minutesStr)
                                    }
                                    if (useSeconds) {
                                        append(":")
                                        withStyle(style = SpanStyle(color = normalColor)) {
                                            append(secondsStr)
                                        }
                                    }
                                }
                                InputMode.MINUTES -> {
                                    withStyle(style = SpanStyle(color = normalColor)) {
                                        append(hoursStr)
                                    }
                                    append(":")
                                    withStyle(style = SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold)) {
                                        append(currentInputStr)
                                    }
                                    if (useSeconds) {
                                        append(":")
                                        withStyle(style = SpanStyle(color = normalColor)) {
                                            append(secondsStr)
                                        }
                                    }
                                }
                                InputMode.SECONDS -> {
                                    withStyle(style = SpanStyle(color = normalColor)) {
                                        append(hoursStr)
                                    }
                                    append(":")
                                    withStyle(style = SpanStyle(color = normalColor)) {
                                        append(minutesStr)
                                    }
                                    append(":")
                                    withStyle(style = SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold)) {
                                        append(currentInputStr)
                                    }
                                }
                                else -> {}
                            }
                        }
                    } else {
                        buildAnnotatedString { append(display) }
                    }

                    // Row containing action button and the current time display.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val actionRotation by animateFloatAsState(
                        targetValue = if (showActionsDialog) 180f else 0f,
                        label = "actionRotation"
                    )
                    IconButton(
                        onClick = { showActionsDialog = !showActionsDialog },
                        modifier = Modifier.size(36.dp)
                    ) {
                            ActionDotsIcon(
                                modifier = Modifier.graphicsLayer { rotationZ = actionRotation }
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Time display container aligned to the end.
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                        if (inputMode == InputMode.MULTIPLIER) {
                            Text(
                                text = annotatedDisplay,
                                style = MaterialTheme.typography.displayLarge,
                                fontSize = 48.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            val totalSeconds = Time(hours, minutes, seconds).toSeconds()
                            val sign = if (showingResult && totalSeconds < 0) "-" else ""
                            val absTime = if (showingResult) {
                                Time.fromSeconds(kotlin.math.abs(totalSeconds))
                            } else {
                                Time(hours, minutes, seconds)
                            }

                            val hoursStr = if (inputMode == InputMode.HOURS && !showingResult) {
                                currentInput.ifEmpty { "00" }
                            } else {
                                String.format("%02d", absTime.hours)
                            }
                            val minutesStr = if (inputMode == InputMode.MINUTES && !showingResult) {
                                currentInput.ifEmpty { "00" }
                            } else {
                                String.format("%02d", absTime.minutes)
                            }
                            val secondsStr = if (inputMode == InputMode.SECONDS && !showingResult) {
                                currentInput.ifEmpty { "00" }
                            } else {
                                String.format("%02d", absTime.seconds)
                            }

                            // Segment display row (hours : minutes : seconds).
                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (sign.isNotEmpty()) {
                                    Text(
                                        text = sign,
                                        style = MaterialTheme.typography.displayLarge,
                                        fontSize = 48.sp,
                                        color = normalColor
                                    )
                                }
                                Text(
                                    text = hoursStr,
                                    style = MaterialTheme.typography.displayLarge,
                                        fontSize = 48.sp,
                                        color = if (inputMode == InputMode.HOURS) highlightColor else normalColor,
                                        fontWeight = if (inputMode == InputMode.HOURS) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.clickable {
                                        inputMode = InputMode.HOURS
                                        currentInput = absTime.hours.toString()
                                        showingResult = false
                                        replaceOnNextInput = true
                                        updateDisplay()
                                    }
                                )
                                    Text(
                                        text = ":",
                                        style = MaterialTheme.typography.displayLarge,
                                        fontSize = 48.sp,
                                        color = normalColor
                                    )
                                    Text(
                                        text = minutesStr,
                                        style = MaterialTheme.typography.displayLarge,
                                        fontSize = 48.sp,
                                        color = if (inputMode == InputMode.MINUTES) highlightColor else normalColor,
                                        fontWeight = if (inputMode == InputMode.MINUTES) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.clickable {
                                        inputMode = InputMode.MINUTES
                                        currentInput = absTime.minutes.toString()
                                        showingResult = false
                                        replaceOnNextInput = true
                                        updateDisplay()
                                    }
                                )
                                    if (useSeconds) {
                                        Text(
                                            text = ":",
                                            style = MaterialTheme.typography.displayLarge,
                                            fontSize = 48.sp,
                                            color = normalColor
                                        )
                                        Text(
                                            text = secondsStr,
                                            style = MaterialTheme.typography.displayLarge,
                                            fontSize = 48.sp,
                                            color = if (inputMode == InputMode.SECONDS) highlightColor else normalColor,
                                            fontWeight = if (inputMode == InputMode.SECONDS) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.clickable {
                                            inputMode = InputMode.SECONDS
                                            currentInput = absTime.seconds.toString()
                                            showingResult = false
                                            replaceOnNextInput = true
                                            updateDisplay()
                                        }
                                    )
                                }
                                }
                            }
                        }
                    }
                }
            }
        }

            // Gap between display card and keypad.
            Spacer(modifier = Modifier.height(8.dp))

            // Keypad area (measured for actions dialog height).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        keypadHeightPx = coords.size.height
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: clear/backspace/mod/divide.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                CalculatorButton(
                    text = "C",
                    modifier = Modifier.weight(1f),
                    isOperation = true,
                    onClick = { clear() }
                )
                    CalculatorButton(
                        text = "⌫",
                        modifier = Modifier.weight(1f),
                        isOperation = true,
                        onClick = { backspace() }
                    )
                    CalculatorButton(
                        text = "MOD",
                        modifier = Modifier.weight(1f),
                        isOperation = true,
                        onClick = { modulus24() }
                    )
                    CalculatorButton(
                        text = "÷",
                        modifier = Modifier.weight(1f),
                        isOperation = true,
                        onClick = { handleOperation("÷") }
                    )
                }

                // Row 2: 7/8/9/multiply.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalculatorButton(
                        text = "7",
                        modifier = Modifier.weight(1f),
                        onClick = { handleNumberInput("7") }
                    )
                    CalculatorButton(
                        text = "8",
                        modifier = Modifier.weight(1f),
                        onClick = { handleNumberInput("8") }
                    )
                    CalculatorButton(
                        text = "9",
                        modifier = Modifier.weight(1f),
                        onClick = { handleNumberInput("9") }
                    )
                    CalculatorButton(
                        text = "×",
                        modifier = Modifier.weight(1f),
                        isOperation = true,
                        onClick = { handleOperation("×") }
                    )
                }

                // Row 3: 4/5/6/minus.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalculatorButton(
                        text = "4",
                        modifier = Modifier.weight(1f),
                        onClick = { handleNumberInput("4") }
                    )
                    CalculatorButton(
                        text = "5",
                        modifier = Modifier.weight(1f),
                        onClick = { handleNumberInput("5") }
                    )
                    CalculatorButton(
                        text = "6",
                        modifier = Modifier.weight(1f),
                        onClick = { handleNumberInput("6") }
                    )
                    CalculatorButton(
                        text = "-",
                        modifier = Modifier.weight(1f),
                        isOperation = true,
                        onClick = { handleOperation("-") }
                    )
                }

                // Row 4: 1/2/3/plus.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalculatorButton(
                        text = "1",
                        modifier = Modifier.weight(1f),
                        onClick = { handleNumberInput("1") }
                    )
                    CalculatorButton(
                        text = "2",
                        modifier = Modifier.weight(1f),
                        onClick = { handleNumberInput("2") }
                    )
                    CalculatorButton(
                        text = "3",
                        modifier = Modifier.weight(1f),
                        onClick = { handleNumberInput("3") }
                    )
                    CalculatorButton(
                        text = "+",
                        modifier = Modifier.weight(1f),
                        isOperation = true,
                        onClick = { handleOperation("+") }
                    )
                }

                // Row 5: 0/:/NOW/=.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalculatorButton(
                        text = "0",
                        modifier = Modifier.weight(1f),
                        onClick = { handleNumberInput("0") }
                    )
                    CalculatorButton(
                        text = if (inputMode == InputMode.MULTIPLIER) "." else ":",
                        modifier = Modifier.weight(1f),
                        isOperation = true,
                        onClick = {
                            if (inputMode == InputMode.MULTIPLIER) {
                                if (!currentInput.contains(".")) {
                                    handleNumberInput(".")
                                }
                        } else {
                            when (inputMode) {
                                InputMode.HOURS -> {
                                    hours = currentInput.toIntOrNull() ?: 0
                                    currentInput = minutes.toString()
                                    inputMode = InputMode.MINUTES
                                    replaceOnNextInput = true
                                }
                                InputMode.MINUTES -> {
                                    minutes = currentInput.toIntOrNull()?.coerceAtMost(59) ?: 0
                                    if (useSeconds) {
                                        currentInput = seconds.toString()
                                        inputMode = InputMode.SECONDS
                                        replaceOnNextInput = true
                                    } else {
                                        currentInput = ""
                                        inputMode = InputMode.HOURS
                                    }
                                }
                                else -> {}
                            }
                            updateDisplay()
                            }
                        }
                    )
                    CalculatorButton(
                        text = "NOW",
                        modifier = Modifier.weight(1f),
                        isOperation = true,
                        onClick = { loadCurrentTime() }
                    )
                    CalculatorButton(
                        text = "=",
                        modifier = Modifier.weight(1f),
                        isOperation = true,
                        onClick = { calculate() }
                    )
                }
            }
        }

        if (showActionsDialog) {
            // Full-screen tap-catcher to dismiss actions dialog.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { showActionsDialog = false }
                    .zIndex(1f)
            )
        }

        val keypadHeightDp = with(LocalDensity.current) {
            if (keypadHeightPx > 0) keypadHeightPx.toDp() else null
        }
        val contentBottomPadding = contentPadding.calculateBottomPadding()
        // Bottom actions dialog (slides up over keypad).
        androidx.compose.animation.AnimatedVisibility(
            visible = showActionsDialog,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(2f)
        ) {
            val gapHeight = 8.dp
            val dialogHeight = if (keypadHeightDp != null) {
                keypadHeightDp + gapHeight + contentBottomPadding + 16.dp
            } else {
                null
            }
            val surfaceModifier = if (dialogHeight != null) {
                Modifier.fillMaxWidth().height(dialogHeight)
            } else {
                Modifier.fillMaxWidth()
            }
            val isDarkTheme = isSystemInDarkTheme()
            val baseColor = MaterialTheme.colorScheme.surfaceVariant
            val dialogColor = if (isDarkTheme) {
                lerp(baseColor, Color.White, 0.10f)
            } else {
                lerp(baseColor, Color.Black, 0.10f)
            }
            // Actions dialog container.
            Surface(
                modifier = surfaceModifier,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = dialogColor
            ) {
                // Actions dialog content: grouped pill rows.
                val snapshot = run {
                    var h = hours
                    var m = minutes
                    var s = seconds
                    if (currentInput.isNotEmpty()) {
                        when (inputMode) {
                            InputMode.HOURS -> h = currentInput.toIntOrNull() ?: h
                            InputMode.MINUTES -> m = currentInput.toIntOrNull()?.coerceAtMost(59) ?: m
                            InputMode.SECONDS -> s = currentInput.toIntOrNull()?.coerceAtMost(59) ?: s
                            InputMode.MULTIPLIER -> {}
                        }
                    }
                    Time(h, m, s)
                }
                val totalSeconds = snapshot.toSeconds().toDouble()
                val totalMinutes = totalSeconds / 60.0
                val totalHours = totalSeconds / 3600.0
                val totalDays = totalSeconds / 86400.0

                // Dialog column holding grouped cards.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    data class Pill(val label: String, val value: String)

                    fun pillValueText(valueText: String): String {
                        return if (valueText == "0" || valueText == "0.0") "" else valueText
                    }

                    @Composable
                    // Renders a row of pills with wrapping.
                    fun PillsRow(pills: List<Pill>) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pills.forEach { pill ->
                                val displayText = if (pill.value.isBlank()) {
                                    pill.label
                                } else {
                                    "${pill.label} ${pill.value}"
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .clickable {
                                                clipboardManager.setText(AnnotatedString(pill.value))
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Renders clear history + copy/paste pills.
                    @Composable
                    fun ActionPillsRow() {
                        var clearConfirm by remember { mutableStateOf(false) }
                        LaunchedEffect(clearConfirm) {
                            if (clearConfirm) {
                                delay(3000)
                                clearConfirm = false
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val clearLabel = if (clearConfirm) "CONFIRM" else "Clear History"
                            val actions = listOf(clearLabel, "Copy", "Paste")
                            actions.forEach { label ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .clickable {
                                                if (label == clearLabel) {
                                                    if (clearConfirm) {
                                                        clearHistory()
                                                        clearConfirm = false
                                                    } else {
                                                        clearConfirm = true
                                                    }
                                                } else if (label == "Copy") {
                                                    clipboardManager.setText(AnnotatedString(display))
                                                } else {
                                                    pasteFromClipboard()
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    

                    val mod24Seconds = ((totalSeconds % 86400) + 86400) % 86400
                    val mod24Time = Time.fromSeconds(mod24Seconds.toLong())
                    val mod24Text = if (useSeconds) {
                        String.format("%02d:%02d:%02d", mod24Time.hours, mod24Time.minutes, mod24Time.seconds)
                    } else {
                        String.format("%02d:%02d", mod24Time.hours, mod24Time.minutes)
                    }
                    val mod12Hour = ((mod24Time.hours + 11) % 12) + 1
                    val amPm = if (mod24Time.hours < 12) "AM" else "PM"
                    val mod12Text = if (useSeconds) {
                        String.format("%02d:%02d:%02d %s", mod12Hour, mod24Time.minutes, mod24Time.seconds, amPm)
                    } else {
                        String.format("%02d:%02d %s", mod12Hour, mod24Time.minutes, amPm)
                    }

                    val basePills = listOf(
                        Pill("D", pillValueText("%.1f".format(totalDays))),
                        Pill("H", pillValueText("%.1f".format(totalHours))),
                        Pill("M", pillValueText("%.1f".format(totalMinutes))),
                        Pill("S", pillValueText("%.0f".format(totalSeconds)))
                    )
                    val modPills = listOf(
                        Pill("24h", mod24Text),
                        Pill("12h", mod12Text)
                    )

                    // Group 1: D/H/M/S pills.
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.0f)
                        )
                    ) {
                        // Centered pill row container.
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PillsRow(basePills)
                        }
                    }

                    // Separator line between groups.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    )

                    // Group 2: MOD24/MOD12 pills.
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.0f)
                        )
                    ) {
                        // Centered pill row container.
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PillsRow(modPills)
                        }
                    }

                    // Separator line between groups.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    )

                    // Group 3: Copy/Paste pills.
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.0f)
                        )
                    ) {
                        // Centered action pill row container.
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ActionPillsRow()
                        }
                    }

                    // Separator line between groups.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    )

                    // Group 4: Seconds toggle.
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.0f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (useSeconds)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "Secs On",
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .clickable {
                                                if (!useSeconds) {
                                                    useSeconds = true
                                                    updateDisplay()
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (!useSeconds)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "Secs Off",
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .clickable {
                                                if (useSeconds) {
                                                    useSeconds = false
                                                    updateDisplay()
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                }
            }
        }
    }
}

@Composable
fun ActionDotsIcon(modifier: Modifier = Modifier) {
    val dotSize = 6.dp
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier.size(16.dp)) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .align(Alignment.TopCenter)
                .background(dotColor, shape = MaterialTheme.shapes.small)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .align(Alignment.BottomStart)
                .background(dotColor, shape = MaterialTheme.shapes.small)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .align(Alignment.BottomEnd)
                .background(dotColor, shape = MaterialTheme.shapes.small)
        )
    }
}

@Composable
fun CalculatorButton(
    text: String,
    modifier: Modifier = Modifier,
    isOperation: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .height(70.dp)
            .pointerInput(onLongClick) {
                if (onLongClick != null) {
                    detectTapGestures(onLongPress = { onLongClick() })
                }
            }
            .clickable { onClick() },
        shape = MaterialTheme.shapes.small,
        color = if (isOperation)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 24.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TimeCalculatorPreview() {
    TimeCalcTheme {
        TimeCalculatorScreen()
    }
}
