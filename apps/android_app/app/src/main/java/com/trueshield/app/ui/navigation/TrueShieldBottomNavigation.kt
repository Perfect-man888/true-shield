package com.trueshield.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal data class BottomNavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

internal val TrueShieldBottomNavigationItems = listOf(
    BottomNavigationItem(
        route = AppRoute.Home.route,
        label = "首页",
        icon = Icons.Default.Home,
    ),
    BottomNavigationItem(
        route = AppRoute.RiskDetectionHub.route,
        label = "检测",
        icon = Icons.Default.Search,
    ),
    BottomNavigationItem(
        route = AppRoute.FamilyHub.route,
        label = "家庭",
        icon = Icons.Default.Groups,
    ),
    BottomNavigationItem(
        route = AppRoute.MessageCenter.route,
        label = "消息",
        icon = Icons.Default.Notifications,
    ),
    BottomNavigationItem(
        route = AppRoute.ProfileHub.route,
        label = "我的",
        icon = Icons.Default.Person,
    ),
)

@Composable
internal fun TrueShieldBottomNavigation(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        TrueShieldBottomNavigationItems.forEach { item ->
            val selected = currentRoute == item.route

            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
                label = {
                    Text(text = item.label)
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
