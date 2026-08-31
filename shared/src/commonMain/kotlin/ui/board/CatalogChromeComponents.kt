package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.CatalogNavEntryId
import com.valoser.futacha.shared.util.isAndroid
import com.valoser.futacha.shared.ui.theme.LocalFutachaChromeColors

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogTopBar(
    board: BoardSummary?,
    mode: CatalogMode,
    searchQueryState: State<String>,
    isSearchActive: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onNavigationClick: () -> Unit,
    onModeSelected: (CatalogMode) -> Unit,
    onMenuAction: (CatalogMenuAction) -> Unit
) {
    val chromeColors = LocalFutachaChromeColors.current
    var isMenuExpanded by remember { mutableStateOf(false) }
    val searchQuery by searchQueryState
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = {
                AnalyticsTracker.uiControl("catalog_history_open", "履歴を開く")
                onNavigationClick()
            }) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "履歴を開く"
                )
            }
        },
        title = {
            if (isSearchActive) {
                CatalogSearchTextField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    focusRequester = focusRequester,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = board?.name ?: "カタログ",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            if (isSearchActive) {
                IconButton(
                    onClick = {
                        if (searchQuery.isNotEmpty()) {
                            AnalyticsTracker.uiControl("catalog_search_clear", "カタログ検索語をクリア")
                            onSearchQueryChange("")
                        } else {
                            AnalyticsTracker.uiControl("catalog_search_close", "カタログ検索を閉じる")
                            onSearchActiveChange(false)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "検索を閉じる"
                    )
                }
            } else {
                IconButton(onClick = {
                    AnalyticsTracker.uiControl("catalog_search_open", "カタログ検索を開く")
                    onSearchActiveChange(true)
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "検索"
                    )
                }
                Box {
                    IconButton(onClick = {
                        AnalyticsTracker.uiControl("catalog_top_menu_open", "カタログ上部メニューを開く")
                        isMenuExpanded = true
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "メニュー"
                        )
                    }
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp
                    ) {
                        CatalogMenuAction.entries.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                onClick = {
                                    AnalyticsTracker.uiControl("catalog_top_menu_action", action.label)
                                    isMenuExpanded = false
                                    onMenuAction(action)
                                }
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = chromeColors.topBar,
            titleContentColor = chromeColors.onBar,
            navigationIconContentColor = chromeColors.onBar,
            actionIconContentColor = chromeColors.onBar
        )
    )
}

@Composable
internal fun CatalogNavigationBar(
    menuEntries: List<CatalogNavEntryConfig>,
    onNavigate: (CatalogNavEntryId) -> Unit,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val chromeColors = LocalFutachaChromeColors.current
    val visibleEntries = remember(menuEntries) {
        resolveCatalogNavBarEntries(menuEntries)
    }
    NavigationBar(
        modifier = modifier,
        containerColor = chromeColors.bottomBar,
        contentColor = chromeColors.onBar,
        windowInsets = if (isAndroid()) NavigationBarDefaults.windowInsets else WindowInsets()
    ) {
        visibleEntries.forEach { entry ->
            val meta = entry.id.toMeta()
            NavigationBarItem(
                selected = false,
                enabled = !(isRefreshing && entry.id == CatalogNavEntryId.RefreshCatalog),
                onClick = {
                    AnalyticsTracker.uiControl("catalog_navigation_action", meta.label)
                    onNavigate(entry.id)
                },
                icon = {
                    if (isRefreshing && entry.id == CatalogNavEntryId.RefreshCatalog) {
                        CircularProgressIndicator(
                            // NavigationBar icons occupy a 24dp box. Keep the
                            // in-progress replacement in that same box so the
                            // refresh item does not visibly shrink mid-update.
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = chromeColors.onBar
                        )
                    } else {
                        Icon(
                            imageVector = meta.icon,
                            contentDescription = meta.label
                        )
                    }
                },
                label = {
                    Text(
                        text = if (isRefreshing && entry.id == CatalogNavEntryId.RefreshCatalog) {
                            "更新中"
                        } else {
                            meta.label
                        },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = chromeColors.onBar,
                    selectedTextColor = chromeColors.onBar,
                    unselectedIconColor = chromeColors.onBar.copy(alpha = 0.82f),
                    unselectedTextColor = chromeColors.onBar.copy(alpha = 0.82f),
                    indicatorColor = chromeColors.onBar.copy(alpha = 0.14f)
                )
            )
        }
    }
}

@Composable
private fun CatalogSearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val queryInputState = rememberStableTextInputState(
        text = query,
        onTextChange = onQueryChange,
        analyticsFieldLabel = "カタログ検索"
    )
    TextField(
        value = queryInputState.value,
        onValueChange = queryInputState.onValueChange,
        modifier = modifier
            .padding(end = 8.dp)
            .focusRequester(focusRequester),
        singleLine = true,
        placeholder = { Text("スレタイ検索") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null
            )
        },
        trailingIcon = null,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
            unfocusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onPrimary,
            unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
            cursorColor = MaterialTheme.colorScheme.onPrimary,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            focusedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            focusedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}
