package eu.kanade.presentation.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.core.prefs.PreferenceMutableState
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.horizontalPadding

const val DIVIDER_ALPHA = 0.2f

@Composable
fun Divider(
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Divider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = DIVIDER_ALPHA),
    )
}

@Composable
fun PreferenceRow(
    modifier: Modifier = Modifier,
    title: String,
    painter: Painter? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
    // SY -->
    subtitleAnnotated: AnnotatedString? = null,
    // SY <--
) {
    val height = if (subtitle != null /* SY --> */ || subtitleAnnotated != null/* SY <-- */) 72.dp else 56.dp

    val titleTextStyle = MaterialTheme.typography.bodyLarge
    val subtitleTextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (painter != null) {
            Icon(
                painter = painter,
                modifier = Modifier
                    .padding(start = horizontalPadding, end = 16.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null,
            )
        }
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
        ) {
            Text(
                text = title,
                style = titleTextStyle,
            )
            if (subtitle != null) {
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = subtitle,
                    style = subtitleTextStyle,
                )
            }
            // SY -->
            if (subtitleAnnotated != null) {
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = subtitleAnnotated,
                    style = subtitleTextStyle,
                )
            }
            // SY <--
        }
        if (action != null) {
            Box(
                Modifier
                    .widthIn(min = 56.dp)
                    .padding(end = horizontalPadding),
            ) {
                action()
            }
        }
    }
}

@Composable
fun SwitchPreference(
    modifier: Modifier = Modifier,
    checked: Boolean,
    onClick: () -> Unit,
    title: String,
    subtitle: String? = null,
    painter: Painter? = null,
    // SY -->
    subtitleAnnotated: AnnotatedString? = null,
    // SY <--
) {
    PreferenceRow(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        painter = painter,
        action = { Switch(checked = checked, onCheckedChange = null) },
        onClick = onClick,
        // SY -->
        subtitleAnnotated = subtitleAnnotated,
        // SY <--
    )
}

@Composable
fun SwitchPreference(
    modifier: Modifier = Modifier,
    preference: PreferenceMutableState<Boolean>,
    title: String,
    subtitle: String? = null,
    painter: Painter? = null,
    // SY -->
    subtitleAnnotated: AnnotatedString? = null,
    // SY <--
) {
    SwitchPreference(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        painter = painter,
        checked = preference.value,
        onClick = { preference.value = !preference.value },
        // SY -->
        subtitleAnnotated = subtitleAnnotated,
        // SY <--
    )
}

@Preview
@Composable
private fun PreferencesPreview() {
    TachiyomiTheme {
        Column {
            PreferenceRow(
                title = "Plain",
                subtitle = "Subtitle",
            )

            Divider()

            SwitchPreference(
                title = "Switch (on)",
                subtitle = "Subtitle",
                checked = true,
                onClick = {},
            )
            SwitchPreference(
                title = "Switch (off)",
                subtitle = "Subtitle",
                checked = false,
                onClick = {},
            )
        }
    }
}
