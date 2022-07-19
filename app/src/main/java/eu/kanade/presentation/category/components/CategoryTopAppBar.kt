package eu.kanade.presentation.category.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.tachiyomi.R

@Composable
fun CategoryTopAppBar(
    topAppBarScrollBehavior: TopAppBarScrollBehavior,
    navigateUp: () -> Unit,
    title: String,
) {
    SmallTopAppBar(
        navigationIcon = {
            IconButton(onClick = navigateUp) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.abc_action_bar_up_description),
                )
            }
        },
        title = {
            Text(text = title)
        },
        scrollBehavior = topAppBarScrollBehavior,
    )
}
