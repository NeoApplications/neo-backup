package com.machiav3lli.backup.ui.compose.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.machiav3lli.backup.NAV_MAIN
import com.machiav3lli.backup.NAV_PREFS

@Composable
fun BottomNavBar(page: Int = NAV_MAIN, navController: NavController) {
    val items = when (page) {
        NAV_PREFS -> listOf(
            NavItem.UserPrefs,
            NavItem.ServicePrefs,
            NavItem.AdvancedPrefs,
            NavItem.ToolsPrefs
        )
        else -> listOf(
            NavItem.Home,
            NavItem.Backup,
            NavItem.Restore,
            NavItem.Scheduler,
            NavItem.Settings
        )
    }

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination?.route

        items.forEach { item ->
            val selected = currentDestination == item.destination

            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(id = item.icon),
                        contentDescription = stringResource(id = item.title),
                        modifier = Modifier.size(if (selected) 36.dp else 28.dp),
                    )
                },
                label = {
                    if (!selected)
                        Text(
                            text = stringResource(id = item.title),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary
                ),
                alwaysShowLabel = true,
                selected = selected,
                onClick = {
                    navController.navigate(item.destination) {
                        navController.currentDestination?.id?.let {
                            popUpTo(it) {
                                inclusive = true
                                saveState = true
                            }
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}