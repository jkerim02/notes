package com.jks.organizator.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun Page(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: (@Composable () -> Unit)? = null,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp)
        ) {
            if (title != null || onBack != null) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Nazad", tint = C.text) }
                    }
                    if (title != null) Text(title, style = T.title, modifier = Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                    actions()
                }
            }
            content()
            Spacer(Modifier.height(if (fab != null) 96.dp else 24.dp))
        }
        if (fab != null) Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) { fab() }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = C.surface,
    border: Color? = C.line,
    radius: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    var m = modifier.clip(shape).background(color, shape)
    if (border != null) m = m.border(1.dp, border, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

@Composable
fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit = {}, accent: Color = C.gold) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.heightIn(min = 40.dp).clip(shape)
            .background(if (selected) accent else C.surface2, shape)
            .border(1.dp, if (selected) accent else C.line, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, style = T.body.copy(color = if (selected) C.bg else C.text2, fontWeight = androidx.compose.ui.text.font.FontWeight.W500)) }
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.gold, contentColor = C.bg, disabledContainerColor = C.surface2, disabledContentColor = C.muted)
    ) { Text(text, style = T.medium.copy(color = if (enabled) C.bg else C.muted, fontWeight = androidx.compose.ui.text.font.FontWeight.W600)) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, color: Color = C.text, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, C.line), colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) { Text(text, style = T.medium.copy(color = color)) }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = C.muted) =
    Text(text.uppercase(), style = T.label.copy(color = color), modifier = modifier)

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, minLines: Int = 1, suffix: String? = null,
    textStyle: TextStyle = T.body
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier,
        singleLine = singleLine, minLines = minLines, textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        suffix = suffix?.let { { Text(it, style = T.small) } },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.gold, unfocusedBorderColor = C.line, focusedContainerColor = C.surface2,
            unfocusedContainerColor = C.surface2, focusedLabelColor = C.gold, unfocusedLabelColor = C.muted,
            cursorColor = C.gold, focusedTextColor = C.text, unfocusedTextColor = C.text
        )
    )
}

@Composable
fun Bar(progress: Float, color: Color = C.teal, height: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(C.surface2)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).clip(CircleShape).background(color))
    }
}

@Composable
fun SwitchRow(title: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.body)
            if (sub != null) Text(sub, style = T.small)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = C.gold, checkedThumbColor = C.bg, uncheckedTrackColor = C.surface2, uncheckedThumbColor = C.muted, uncheckedBorderColor = C.line)
        )
    }
}

@Composable
fun Circle(done: Boolean, next: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .background(if (done) C.teal else Color.Transparent, CircleShape)
                .border(2.dp, if (done) C.teal else if (next) C.gold else C.line, CircleShape),
            contentAlignment = Alignment.Center
        ) { if (done) Text("✓", style = T.body.copy(color = C.bg)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var open by remember { mutableStateOf(false) }
    PickerBox(label, "${date.dayOfMonth}.${date.monthValue}.${date.year}.", modifier) { open = true }
    if (open) {
        val st = rememberDatePickerState(date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("U redu", color = C.gold) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Odustani", color = C.muted) } },
            colors = DatePickerDefaults.colors(containerColor = C.surface)
        ) { DatePicker(st, colors = DatePickerDefaults.colors(containerColor = C.surface, selectedDayContainerColor = C.gold, selectedDayContentColor = C.bg, todayDateBorderColor = C.gold, todayContentColor = C.gold)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, time: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var open by remember { mutableStateOf(false) }
    PickerBox(label, String.format("%02d:%02d", time.hour, time.minute), modifier) { open = true }
    if (open) {
        val st = rememberTimePickerState(time.hour, time.minute, true)
        AlertDialog(
            onDismissRequest = { open = false }, containerColor = C.surface,
            confirmButton = { TextButton(onClick = { onChange(LocalTime.of(st.hour, st.minute)); open = false }) { Text("U redu", color = C.gold) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Odustani", color = C.muted) } },
            text = {
                TimePicker(st, colors = TimePickerDefaults.colors(
                    clockDialSelectedContentColor = C.bg, selectorColor = C.gold, timeSelectorSelectedContainerColor = C.gold,
                    timeSelectorSelectedContentColor = C.bg, timeSelectorUnselectedContainerColor = C.surface2,
                    timeSelectorUnselectedContentColor = C.text, clockDialColor = C.surface2, clockDialUnselectedContentColor = C.text
                ))
            }
        )
    }
}

@Composable
private fun PickerBox(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.heightIn(min = 56.dp).clip(shape).background(C.surface2, shape).border(1.dp, C.line, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, style = T.small)
        Text(value, style = T.body)
    }
}

@Composable
fun Confirm(title: String, text: String, ok: String = "Obriši", onOk: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.surface,
        title = { Text(title, style = T.h2) }, text = { Text(text, style = T.body) },
        confirmButton = { TextButton(onClick = { onOk(); onDismiss() }) { Text(ok, color = C.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Odustani", color = C.muted) } }
    )
}

@Composable
fun InputDialog(
    title: String, label: String, initial: String = "", keyboard: KeyboardType = KeyboardType.Text,
    onOk: (String) -> Unit, onDismiss: () -> Unit
) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.surface,
        title = { Text(title, style = T.h2) },
        text = { Field(v, { v = it }, label, keyboard = keyboard) },
        confirmButton = { TextButton(onClick = { if (v.isNotBlank()) { onOk(v.trim()); onDismiss() } }) { Text("Sačuvaj", color = C.gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Odustani", color = C.muted) } }
    )
}

@Composable
fun GeminiCard(title: String, text: String?, loading: Boolean, error: String?) {
    AppCard(color = C.surface2) {
        Label("✦ $title", color = C.gold)
        Spacer(Modifier.height(6.dp))
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = C.gold, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp)); Text("Gemini radi…", style = T.small)
            }
            error != null -> Text(error, style = T.body.copy(color = C.danger))
            text != null -> Text(text, style = T.body.copy(color = C.text2))
        }
    }
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))
