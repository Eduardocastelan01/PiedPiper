package com.lostf1sh.pixelplayeross.presentation.components

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lostf1sh.pixelplayeross.BottomNavItem
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateToTopLevelSafely
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Material 3 wide navigation rail used instead of the bottom bar on medium/expanded
 * window widths (tablets, unfolded foldables). The header button expands it to show
 * labels beside the icons. Mirrors the navigation semantics of
 * [PlayerInternalNavigationBar], including the double-tap-on-Search shortcut.
 */
@Composable
fun PlayerNavigationRail(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    onSearchIconDoubleTap: () -> Unit = {},
    onReselectTab: (String) -> Unit = {}
) {
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)
    val latestOnReselectTab by rememberUpdatedState(onReselectTab)
    val latestNavigationEnabled by rememberUpdatedState(currentRoute != null)
    val scope = rememberCoroutineScope()
    var lastSearchTapTimestamp by remember { mutableLongStateOf(0L) }
    val railState = rememberWideNavigationRailState()
    val isExpanded = railState.targetValue == WideNavigationRailValue.Expanded

    WideNavigationRail(
        modifier = modifier.fillMaxHeight(),
        state = railState,
        arrangement = Arrangement.Center,
        header = {
            IconButton(
                onClick = { scope.launch { railState.toggle() } },
                modifier = Modifier.padding(start = 24.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.AutoMirrored.Rounded.MenuOpen else Icons.Rounded.Menu,
                    contentDescription = stringResource(
                        if (isExpanded) R.string.navigation_rail_collapse else R.string.navigation_rail_expand
                    )
                )
            }
        }
    ) {
        navItems.forEach { item ->
            val isSelected = currentRoute != null && currentRoute == item.screen.route
            val label = stringResource(item.labelResId)
            val iconPainterResId = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                item.selectedIconResId
            } else {
                item.iconResId
            }
            val onClickLambda: () -> Unit = remember(item.screen.route, navController, scope) {
                click@{
                    if (!latestNavigationEnabled) {
                        lastSearchTapTimestamp = 0L
                        return@click
                    }

                    val itemRoute = item.screen.route
                    val isSearchTab = itemRoute == Screen.Search.route
                    val isAlreadySelected = latestCurrentRoute == itemRoute

                    if (isSearchTab) {
                        val now = SystemClock.elapsedRealtime()
                        val isDoubleTap = now - lastSearchTapTimestamp <= 350L
                        lastSearchTapTimestamp = now

                        if (!isAlreadySelected) {
                            if (!navController.navigateToTopLevelSafely(itemRoute)) {
                                lastSearchTapTimestamp = 0L
                                return@click
                            }
                        } else if (!isDoubleTap) {
                            latestOnReselectTab(itemRoute)
                        }

                        if (isDoubleTap) {
                            lastSearchTapTimestamp = 0L
                            if (isAlreadySelected) {
                                latestOnSearchIconDoubleTap()
                            } else {
                                scope.launch {
                                    delay(160L)
                                    latestOnSearchIconDoubleTap()
                                }
                            }
                        }
                    } else if (!isAlreadySelected) {
                        lastSearchTapTimestamp = 0L
                        navController.navigateToTopLevelSafely(itemRoute)
                    } else {
                        lastSearchTapTimestamp = 0L
                        latestOnReselectTab(itemRoute)
                    }
                }
            }
            WideNavigationRailItem(
                selected = isSelected,
                onClick = onClickLambda,
                enabled = currentRoute != null,
                railExpanded = isExpanded,
                icon = {
                    Icon(
                        painter = painterResource(id = iconPainterResId),
                        contentDescription = label
                    )
                },
                label = { Text(label) }
            )
        }
    }
}
