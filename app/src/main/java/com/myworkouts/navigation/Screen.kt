package com.myworkouts.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.graphics.vector.ImageVector
import com.myworkouts.R

sealed class Screen(
    val route: String,
    val icon: ImageVector,
    val titleResId: Int
) {
    object Main : Screen(
        route = "main",
        icon = Icons.Filled.Home,
        titleResId = R.string.workouts_title
    )

    object More : Screen(
        route = "more",
        icon = Icons.Filled.Menu,
        titleResId = R.string.more_title
    )

    companion object {
        val AllTabs: List<Screen> = listOf(Main, More)
    }
}
