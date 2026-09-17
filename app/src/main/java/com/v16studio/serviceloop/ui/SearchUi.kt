package com.v16studio.serviceloop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.OperationalDashboardProjection
import com.v16studio.serviceloop.domain.OperationalWorkKind
import com.v16studio.serviceloop.domain.SearchTarget
import com.v16studio.serviceloop.domain.WorkScope
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopEntityRecord
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextFieldAdapter
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons

private enum class SearchCategory(val key: String, val label: String, val type: String) {
    CUSTOMER("CUSTOMER", "Customers", "CUSTOMER"),
    SITE("SITE", "Sites", "SITE"),
    EQUIPMENT("EQUIPMENT", "Equipment", "EQUIPMENT"),
    TEMPLATE("TEMPLATE", "Templates", "TEMPLATE"),
    PLAN("PLAN", "Service plans", "PLAN"),
    VISIT("VISIT", "Visits", "VISIT"),
    FOLLOW_UP("FOLLOW_UP", "Follow-ups", "FOLLOW_UP"),
    FINAL_RECORD("FINAL_RECORD", "Final records", "FINAL_RECORD"),
}

@Composable
internal fun SearchScreen(
    results: List<SearchTarget>,
    padding: PaddingValues,
    viewModel: ServiceLoopViewModel,
    nav: NavHostController,
    operationalDashboard: OperationalDashboardProjection? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var fieldFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val preferences = remember(context) { SearchUiPreferences(context) }
    var recentQueries by remember(preferences) { mutableStateOf(preferences.recentQueries()) }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(query) { viewModel.search(query) }

    fun recordCurrentQuery(value: String = query) {
        recentQueries = preferences.recordQuery(value)
        keyboard?.hide()
    }

    val recentPanelVisible = fieldFocused && query.isBlank() && recentQueries.isNotEmpty()
    val grouped = SearchCategory.entries.mapNotNull { category ->
        results.filter { it.type == category.type }.takeIf(List<SearchTarget>::isNotEmpty)?.let { category to it }
    }
    val globalOperationalDashboard = operationalDashboard?.takeIf { it.scope == WorkScope.Global }

    LazyColumn(
        Modifier.padding(padding).testTag("search-results-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ServiceLoopTextFieldAdapter(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search names and references") },
                modifier = Modifier.fillMaxWidth().testTag("field-search-names-and-references").onFocusChanged { fieldFocused = it.isFocused },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { recordCurrentQuery() }),
                singleLine = true,
                leadingIcon = { ServiceLoopIcon(ServiceLoopIcons.Search, null, Modifier.size(ServiceLoopUiTokens.Size.icon), LocalServiceLoopTokens.current.icon) },
            )
            if (recentPanelVisible) {
                SearchRecentPanel(
                    queries = recentQueries,
                    onSelect = { selected ->
                        query = selected
                        recordCurrentQuery(selected)
                    },
                    onClear = {
                        preferences.clearRecentQueries()
                        recentQueries = emptyList()
                    },
                )
            } else if (query.isBlank()) {
                Text("Search customers, sites, equipment, templates, service plans, visits, final records, and follow-ups.")
            }
        }
        if (query.isNotBlank() && results.isEmpty()) item { Text("No matching saved records.") }
        grouped.forEach { (category, categoryResults) ->
            item(key = "search-category-${category.key}") {
                var expanded by remember(category.key) { mutableStateOf(preferences.isCategoryExpanded(category.key)) }
                Column(Modifier.fillMaxWidth()) {
                    SearchCategoryStrip(category, categoryResults.size, expanded) {
                        expanded = !expanded
                        preferences.setCategoryExpanded(category.key, expanded)
                    }
                    if (expanded) {
                        Column(
                            Modifier.fillMaxWidth().testTag("search-category-results-${category.key}"),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            categoryResults.forEach { result ->
                                SearchResultRow(result, category, globalOperationalDashboard) {
                                    recordCurrentQuery()
                                    nav.navigate(result.route())
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
private fun SearchCategoryStrip(
    category: SearchCategory,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalServiceLoopTokens.current
    Row(
        Modifier.fillMaxWidth()
            .testTag("search-category-${category.key}")
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = "${category.label}, $count results, ${if (expanded) "expanded" else "collapsed"}"
                stateDescription = if (expanded) "Expanded" else "Collapsed"
                role = Role.Button
            }
            .padding(vertical = ServiceLoopUiTokens.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${category.label} ($count)", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
        ServiceLoopIcon(
            if (expanded) ServiceLoopIcons.CaretDown else ServiceLoopIcons.CaretRight,
            null,
            Modifier.size(ServiceLoopUiTokens.Size.icon),
            colors.icon,
        )
    }
}

@Composable
private fun SearchRecentPanel(
    queries: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalServiceLoopTokens.current
    Surface(
        Modifier.fillMaxWidth().testTag("search-recent-panel"),
        color = colors.surface,
        shadowElevation = ServiceLoopUiTokens.Elevation.menu,
        shape = ServiceLoopUiTokens.Shapes.small,
        border = androidx.compose.foundation.BorderStroke(ServiceLoopUiTokens.Stroke.outline, colors.outlineDecorative),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(vertical = ServiceLoopUiTokens.Space.sm),
        ) {
            Text("Recent searches", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary, modifier = Modifier.padding(horizontal = ServiceLoopUiTokens.Space.md, vertical = ServiceLoopUiTokens.Space.xs))
            queries.forEachIndexed { index, query ->
                Row(
                    Modifier.fillMaxWidth().testTag("search-recent-$index").clickable(onClick = { onSelect(query) }).padding(horizontal = ServiceLoopUiTokens.Space.md, vertical = ServiceLoopUiTokens.Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm),
                ) {
                    ServiceLoopIcon(ServiceLoopIcons.History, null, Modifier.size(ServiceLoopUiTokens.Size.iconSmall), colors.icon)
                    Text(query, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                }
            }
            Spacer(Modifier.height(ServiceLoopUiTokens.Space.xs))
            Text(
                "Clear recent searches",
                color = colors.action,
                modifier = Modifier.fillMaxWidth().testTag("clear-recent-searches").clickable(onClick = onClear).padding(horizontal = ServiceLoopUiTokens.Space.md, vertical = ServiceLoopUiTokens.Space.sm),
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchTarget,
    category: SearchCategory,
    operationalDashboard: OperationalDashboardProjection?,
    onClick: () -> Unit,
) {
    val operationalState = when (result.type) {
        "VISIT" -> operationalDashboard?.stateFor(OperationalWorkKind.VISIT, result.id)
        "PLAN" -> operationalDashboard?.stateFor(OperationalWorkKind.SERVICE, result.id)
        "FOLLOW_UP" -> operationalDashboard?.stateFor(OperationalWorkKind.FOLLOW_UP, result.id)
        else -> null
    }
    val metadata = when {
        result.type == "TEMPLATE" -> "${result.itemCount ?: 0} items"
        result.customerType == CustomerType.ONE_TIME -> "One-time"
        else -> null
    }
    val actionDescription = buildString {
        append("Open ${category.label.removeSuffix("s").lowercase()}: ${result.reference} ${result.title}")
        operationalState?.let { append(", ${it.label}") }
    }
    ServiceLoopEntityRecord(
        title = "${result.reference} · ${result.title}",
        context = result.subtitle,
        metadata = metadata,
        status = result.status,
        modifier = Modifier.fillMaxWidth().testTag("search-result-${category.key}-${result.id}"),
        actionDescription = actionDescription,
        operationalState = operationalState,
        onClick = onClick,
    )
}

private fun SearchTarget.route(): String = when (type) {
    "CUSTOMER" -> "customer/$id"
    "SITE" -> "site/$id"
    "EQUIPMENT" -> "equipment/$id"
    "PLAN" -> "plan/$id"
    "VISIT" -> "visit/$id"
    "FINAL_RECORD" -> "record/$id"
    "FOLLOW_UP" -> "follow-up/$id"
    "TEMPLATE" -> "template/$id"
    else -> error("Unsupported search result type: $type")
}
