package com.standroid.shoppingapp.feature.search.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.standroid.shoppingapp.feature.search.R
import com.standroid.shoppingapp.feature.search.model.SearchUiEvent
import com.standroid.shoppingapp.feature.search.model.SearchUiState
import kotlinx.coroutines.launch

@Composable
internal fun SearchBar(
    uiState: SearchUiState,
    onEvent: (SearchUiEvent) -> Unit,
    gridState: LazyGridState
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            SearchInput(
                query = uiState.query,
                onEvent = { event ->
                    onEvent(event)
                    if (event == SearchUiEvent.SearchSubmitted && uiState.query.isNotBlank()) {
                        keyboard?.hide()
                        focus.clearFocus()
                        scope.launch { gridState.scrollToItem(0) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun SearchInput(
    query: String,
    onEvent: (SearchUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = { onEvent(SearchUiEvent.QueryChanged(it)) },
        modifier = modifier.testTag("search-input"),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = {
            Icon(
                painterResource(R.drawable.ic_search),
                contentDescription = null
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onEvent(SearchUiEvent.QueryChanged("")) }) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        stringResource(R.string.clear_search)
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onEvent(SearchUiEvent.SearchSubmitted) })
    )
}
