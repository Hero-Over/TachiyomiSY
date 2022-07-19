package eu.kanade.presentation.category.components.sources

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.util.horizontalPadding

@Composable
fun SourceCategoryListItem(
    category: String,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    ElevatedCard {
        Row(
            modifier = Modifier
                .padding(start = horizontalPadding, top = horizontalPadding, end = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Outlined.Label, contentDescription = "")
            Text(text = category, modifier = Modifier.padding(start = horizontalPadding))
        }
        Row {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { onRename(category) }) {
                Icon(imageVector = Icons.Outlined.Edit, contentDescription = "")
            }
            IconButton(onClick = { onDelete(category) }) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = "")
            }
        }
    }
}
