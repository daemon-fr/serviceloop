package com.v16studio.serviceloop.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopEntityRecord

@Composable
internal fun SearchScreen(results: List<SearchTarget>, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var query by rememberSaveable { mutableStateOf("") }; LaunchedEffect(query) { viewModel.search(query) }
    LazyColumn(Modifier.padding(padding).testTag("search-results-list"), contentPadding=PaddingValues(16.dp),verticalArrangement=androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)){ item { DailyField(query,{query=it},"Search names and references"); if(query.isBlank()) Text("Search customers, sites, equipment, plans, visits, final records, and follow-ups.") }; if(query.isNotBlank()&&results.isEmpty()) item { Text("No matching saved records.") }; items(results){ result -> ServiceLoopEntityRecord("${result.reference} · ${result.title}",result.subtitle,metadata=buildString { if (result.customerType == CustomerType.ONE_TIME) append("One-time · "); append(result.type.replace('_',' ')) }){ val route=when(result.type){"CUSTOMER"->"customer/${result.id}";"SITE"->"site/${result.id}";"EQUIPMENT"->"equipment/${result.id}";"PLAN"->"plan/${result.id}";"VISIT"->"visit/${result.id}";"FINAL_RECORD"->"record/${result.id}";"FOLLOW_UP"->"follow-up/${result.id}";else->null}; route?.let(nav::navigate) } } }
}
