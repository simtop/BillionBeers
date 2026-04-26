package com.simtop.billionbeers.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.simtop.billionbeers.catalog_annotations.CatalogProvider
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import java.util.ServiceLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BillionBeersTheme {
                CatalogApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogApp() {
    val navController = rememberNavController()
    val allItems = remember {
        try {
            ServiceLoader.load(CatalogProvider::class.java).flatMap { it.items }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    val tabs = allItems.map { it.tab }.distinct().sorted()
    var selectedTab by remember { mutableStateOf(tabs.firstOrNull() ?: "") }
    var searchQuery by remember { mutableStateOf("") }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    Scaffold(
        topBar = {
            if (currentRoute == "list") {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                            placeholder = { Text("Search components...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                    }
                )
            }
        },
        bottomBar = {
            if (currentRoute == "list" && tabs.isNotEmpty()) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = null) },
                            label = { Text(tab) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val filteredItems = allItems.filter {
            (it.tab == selectedTab || selectedTab.isEmpty()) &&
                    it.name.contains(searchQuery, ignoreCase = true)
        }

        NavHost(
            navController = navController,
            startDestination = "list",
            modifier = Modifier.padding(padding)
        ) {
            composable("list") {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredItems) { item ->
                        ListItem(
                            headlineContent = { Text(item.name) },
                            supportingContent = { Text(item.tab, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.clickable {
                                navController.navigate("demo/${item.tab}/${item.name}")
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
            composable("demo/{tab}/{name}") { backStackEntry ->
                val tabArg = backStackEntry.arguments?.getString("tab")
                val nameArg = backStackEntry.arguments?.getString("name")
                val item = allItems.find { it.tab == tabArg && it.name == nameArg }

                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = { Text(item?.name ?: "Demo") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        item?.content?.invoke()
                    }
                }
            }
        }
    }
}
