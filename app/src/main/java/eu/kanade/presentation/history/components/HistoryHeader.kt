package eu.kanade.presentation.history.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.tachiyomi.util.lang.toRelativeString
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryHeader(
    modifier: Modifier = Modifier,
    date: Date,
    relativeTime: Int,
    dateFormat: DateFormat,
) {
    val context = LocalContext.current
    Text(
        modifier = modifier
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        text = remember {
            date.toRelativeString(
                context,
                relativeTime,
                dateFormat,
            )
        },
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}
