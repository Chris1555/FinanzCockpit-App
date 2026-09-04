package de.finanzcockpit.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.finanzcockpit.app.domain.formatEuro
import de.finanzcockpit.app.AnimationMode
import de.finanzcockpit.app.AppSettings
import de.finanzcockpit.app.NumberFormatMode
import de.finanzcockpit.app.VibrationMode

val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

@Composable
fun AppSettingsProvider(settings: AppSettings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppSettings provides settings, content = content)
}

@Composable
fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun <T> SelectorField(
    label: String,
    value: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(optionLabel(value))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryField(
    value: String,
    knownCategories: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = knownCategories
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy(String::lowercase)
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
    var custom by remember(value, categories) {
        mutableStateOf(value.isBlank() || categories.none { it.equals(value, ignoreCase = true) })
    }
    val customLabel = "+ Neue Kategorie"

    Column(modifier) {
        SelectorField(
            label = "Kategorie",
            value = if (custom) customLabel else value,
            options = categories + customLabel,
            optionLabel = { it },
            onSelected = { selected ->
                if (selected == customLabel) {
                    custom = true
                    onValueChange("")
                } else {
                    custom = false
                    onValueChange(selected)
                }
            },
        )
        if (custom) {
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.take(50)) },
                label = { Text("Name der Kategorie") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun Long.displayEuro(hidden: Boolean = LocalAppSettings.current.amountsHidden): String {
    if (hidden) return "•••• €"
    val formatted = formatEuro()
    return when (LocalAppSettings.current.numberFormat) {
        NumberFormatMode.GERMAN_SUFFIX -> formatted
        NumberFormatMode.EURO_PREFIX -> "€ ${formatted.replace("€", "").trim()}"
    }
}

@Composable
fun rememberAppHaptic(): (Boolean) -> Unit {
    val mode = LocalAppSettings.current.vibration
    val feedback = LocalHapticFeedback.current
    return remember(mode, feedback) {
        { strong ->
            if (mode != VibrationMode.OFF) {
                feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                if (strong && mode == VibrationMode.STRONG) {
                    feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
    }
}

@Composable
fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalAppSettings.current.animations == AnimationMode.REDUCED
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "Karte drücken",
    )
    Card(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
        content = { content() },
    )
}

fun categoryIcon(category: String): ImageVector = when {
    category.contains("wohn", true) || category.contains("miete", true) -> Icons.Outlined.Home
    category.contains("mobil", true) || category.contains("kfz", true) -> Icons.Outlined.DirectionsCar
    category.contains("einkauf", true) || category.contains("lebensmittel", true) -> Icons.Outlined.ShoppingCart
    category.contains("gesund", true) || category.contains("versicherung", true) -> Icons.Outlined.HealthAndSafety
    category.contains("abo", true) || category.contains("abonnement", true) -> Icons.Outlined.Subscriptions
    category.contains("spar", true) || category.contains("invest", true) -> Icons.Outlined.Savings
    category.contains("gehalt", true) || category.contains("einnah", true) -> Icons.Outlined.Payments
    category.isNotBlank() -> Icons.Outlined.AccountBalanceWallet
    else -> Icons.Outlined.Category
}
