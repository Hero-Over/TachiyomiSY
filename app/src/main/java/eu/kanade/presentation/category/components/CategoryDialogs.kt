package eu.kanade.presentation.category.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.TextButton
import eu.kanade.tachiyomi.R

@Composable
fun CategoryCreateDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String) -> Unit,
    // SY -->
    title: String,
    extraMessage: String? = null,
    // SY <--
) {
    val (name, onNameChange) = remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onCreate(name)
                onDismissRequest()
            },) {
                Text(text = stringResource(id = R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.action_cancel))
            }
        },
        title = {
            Text(text = title)
        },
        text = {
            // SY -->
            Column {
                if (extraMessage != null) {
                    Text(extraMessage)
                }
                // SY <--
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = {
                        Text(text = stringResource(id = R.string.name))
                    },
                )
                // SY -->
            }
            // SY <--
        },
    )
}

@Composable
fun CategoryRenameDialog(
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    category: String,
) {
    val (name, onNameChange) = remember { mutableStateOf(category) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onRename(name)
                onDismissRequest()
            },) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(id = R.string.action_rename_category))
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = {
                    Text(text = stringResource(id = R.string.name))
                },
            )
        },
    )
}

@Composable
fun CategoryDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    title: String,
    text: String,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.no))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDelete()
                onDismissRequest()
            },) {
                Text(text = stringResource(R.string.yes))
            }
        },
        title = {
            Text(text = title)
        },
        text = {
            Text(text = text)
        },
    )
}
