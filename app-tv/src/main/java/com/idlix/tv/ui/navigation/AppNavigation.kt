package com.idlix.tv.ui.navigation

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.idlix.tv.ui.components.TopBarTab
import com.idlix.tv.ui.components.TvTopBar
import com.idlix.tv.ui.components.TvTopBarHeight
import com.idlix.tv.ui.screens.detail.DetailScreen
import com.idlix.tv.ui.screens.devtools.DevToolsScreen
import com.idlix.tv.ui.screens.collections.CollectionDetailScreen
import com.idlix.tv.ui.screens.genres.GenreDetailScreen
import com.idlix.tv.ui.screens.genres.GenresScreen
import com.idlix.tv.ui.screens.history.ContinueWatchingScreen
import com.idlix.tv.ui.screens.home.HomeScreen
import com.idlix.tv.ui.screens.movies.MoviesScreen
import com.idlix.tv.ui.screens.player.PlayerScreen
import com.idlix.tv.ui.screens.search.SearchScreen
import com.idlix.tv.ui.screens.series.SeriesScreen
import com.idlix.tv.ui.screens.settings.SettingsScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    val selectedTab = TopBarTab.fromRoute(currentRoute)
    val settingsSelected = currentRoute.startsWith(Screen.Settings.route)

    val isFullScreenRoute = currentRoute.startsWith("player") || currentRoute.startsWith("detail")

    // Same-card refocus keys, one per tab. Hoisted ABOVE the dual-NavHost
    // switch: navigating to detail swaps the whole host, disposing the tab
    // screens AND their remember/rememberSaveable state — an in-screen key
    // dies there and back-nav always falls back to hero Play. AppNavigation
    // itself stays composed across the swap, so plain remember survives.
    var homeRefocusKey by remember { mutableStateOf<String?>(null) }
    var moviesRefocusKey by remember { mutableStateOf<String?>(null) }
    var seriesRefocusKey by remember { mutableStateOf<String?>(null) }
    var genresRefocusKey by remember { mutableStateOf<String?>(null) }
    var genreDetailRefocusKey by remember { mutableStateOf<String?>(null) }
    var collectionDetailRefocusKey by remember { mutableStateOf<String?>(null) }
    // Same-card return from DevTools: Settings disposes under DevTools, so the
    // key lives here (mirrors the detail-swap hoisting above).
    var settingsRefocusKey by remember { mutableStateOf<String?>(null) }
    var searchRefocusKey by remember { mutableStateOf<String?>(null) }
    var continueWatchingRefocusKey by remember { mutableStateOf<String?>(null) }

    // Scroll states, hoisted for the same reason as the refocus keys: the tab
    // screens dispose on detail, so their lazy scroll would reset to 0 and an
    // off-screen originating card would never recompose to take focus back.
    // The return effect scrolls these back to the originating rail + card.
    val homeListState = rememberLazyListState()
    val moviesListState = rememberLazyListState()
    val seriesListState = rememberLazyListState()
    val genresGridState = rememberLazyGridState()
    val genreDetailListState = rememberLazyListState()
    val collectionDetailListState = rememberLazyListState()
    val searchGridState = rememberLazyGridState()
    val continueWatchingGridState = rememberLazyGridState()
    // Rail states by "tab:railId", created on demand and stable thereafter.
    val railListStates = remember { mutableMapOf<String, LazyListState>() }
    val homeRailStates = remember { { railId: String -> railListStates.getOrPut("home:$railId") { LazyListState() } } }
    val moviesRailStates = remember { { railId: String -> railListStates.getOrPut("movies:$railId") { LazyListState() } } }
    val seriesRailStates = remember { { railId: String -> railListStates.getOrPut("series:$railId") { LazyListState() } } }
    val genreDetailRailStates = remember { { railId: String -> railListStates.getOrPut("genre:$railId") { LazyListState() } } }
    val collectionDetailRailStates = remember { { railId: String -> railListStates.getOrPut("collection:$railId") { LazyListState() } } }

    // Bar-to-content entry link: plain focus search dies crossing the
    // floating-bar overlay boundary into LazyColumn content, so every tab/gear
    // DOWN targets this requester. Each bar-route screen attaches it to its
    // entry node (alongside its local requester) — one destination is composed
    // at a time, so the shared instance is unambiguous.
    val contentEntryLink = remember { FocusRequester() }
    // Content-to-bar escape link: content scrolled under the transparent bar
    // escapes preview-UP to the selected tab/gear via topEdgeBarEscape (plain
    // UP search has no strictly-above candidate there). Attached by TvTopBar
    // to the selected tab only — again one node at a time.
    val barTabLink = remember { FocusRequester() }
    // Gamepad B mirrors system Back on routes where Back otherwise does
    // nothing for gamepads. Remote paths untouched.
    val gamepadBackHandler = Modifier.onKeyEvent { keyEvent ->
        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
            keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_B
        ) {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
                true
            } else false
        } else false
    }

    if (isFullScreenRoute) {
        // Render without top bar
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = modifier.then(gamepadBackHandler)
        ) {
            setupNavRoutes(
                navController,
                currentRoute,
                homeRefocusKey, { homeRefocusKey = it },
                moviesRefocusKey, { moviesRefocusKey = it },
                seriesRefocusKey, { seriesRefocusKey = it },
                genresRefocusKey, { genresRefocusKey = it },
                genreDetailRefocusKey, { genreDetailRefocusKey = it },
                collectionDetailRefocusKey, { collectionDetailRefocusKey = it },
                settingsRefocusKey, { settingsRefocusKey = it },
                searchRefocusKey, { searchRefocusKey = it },
                continueWatchingRefocusKey, { continueWatchingRefocusKey = it },
                homeListState, homeRailStates,
                moviesListState, moviesRailStates,
                seriesListState, seriesRailStates,
                genresGridState,
                genreDetailListState, genreDetailRailStates,
                collectionDetailListState, collectionDetailRailStates,
                searchGridState,
                continueWatchingGridState,
                contentEntryLink, barTabLink
            )
        }
    } else {
        // Non-fullscreen routes: content viewport starts BELOW the floating
        // bar (settle guard STUCK-1). The bar still draws as a transparent
        // overlay (web-style), but the NavHost no longer slides under it, so
        // a focused card settled at the viewport top rests at window
        // y≈TvTopBarHeight (below the tab zone y≈11–74) — plain UP search
        // always has a strictly-above target and never dies from overlap.
        // Screens keep their own 12dp top headroom for the focus ring, for a
        // total bar+12dp rest below y≈80. Hero bleed under the bar is gone
        // (art starts below the chrome) — focus reliability wins.
        Box(modifier = modifier.then(gamepadBackHandler)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = TvTopBarHeight)
            ) {
                setupNavRoutes(
                    navController,
                    currentRoute,
                    homeRefocusKey, { homeRefocusKey = it },
                    moviesRefocusKey, { moviesRefocusKey = it },
                    seriesRefocusKey, { seriesRefocusKey = it },
                    genresRefocusKey, { genresRefocusKey = it },
                    genreDetailRefocusKey, { genreDetailRefocusKey = it },
                    collectionDetailRefocusKey, { collectionDetailRefocusKey = it },
                    settingsRefocusKey, { settingsRefocusKey = it },
                    searchRefocusKey, { searchRefocusKey = it },
                    continueWatchingRefocusKey, { continueWatchingRefocusKey = it },
                    homeListState, homeRailStates,
                    moviesListState, moviesRailStates,
                    seriesListState, seriesRailStates,
                    genresGridState,
                    genreDetailListState, genreDetailRailStates,
                    collectionDetailListState, collectionDetailRailStates,
                    searchGridState,
                    continueWatchingGridState,
                    contentEntryLink, barTabLink
                )
            }
            TvTopBar(
                selectedTab = selectedTab,
                downFocusRequester = contentEntryLink,
                tabFocusLink = barTabLink,
                onTabSelected = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(Screen.Home.route) { inclusive = tab == TopBarTab.HOME }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route) {
                        popUpTo(Screen.Home.route)
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                settingsSelected = settingsSelected,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.setupNavRoutes(
    navController: androidx.navigation.NavHostController,
    currentRoute: String,
    homeRefocusKey: String?,
    onHomeRefocusKeyChange: (String?) -> Unit,
    moviesRefocusKey: String?,
    onMoviesRefocusKeyChange: (String?) -> Unit,
    seriesRefocusKey: String?,
    onSeriesRefocusKeyChange: (String?) -> Unit,
    genresRefocusKey: String?,
    onGenresRefocusKeyChange: (String?) -> Unit,
    genreDetailRefocusKey: String?,
    onGenreDetailRefocusKeyChange: (String?) -> Unit,
    collectionDetailRefocusKey: String?,
    onCollectionDetailRefocusKeyChange: (String?) -> Unit,
    settingsRefocusKey: String?,
    onSettingsRefocusKeyChange: (String?) -> Unit,
    searchRefocusKey: String?,
    onSearchRefocusKeyChange: (String?) -> Unit,
    continueWatchingRefocusKey: String?,
    onContinueWatchingRefocusKeyChange: (String?) -> Unit,
    homeListState: LazyListState,
    homeRailStates: (String) -> LazyListState,
    moviesListState: LazyListState,
    moviesRailStates: (String) -> LazyListState,
    seriesListState: LazyListState,
    seriesRailStates: (String) -> LazyListState,
    genresGridState: LazyGridState,
    genreDetailListState: LazyListState,
    genreDetailRailStates: (String) -> LazyListState,
    collectionDetailListState: LazyListState,
    collectionDetailRailStates: (String) -> LazyListState,
    searchGridState: LazyGridState,
    continueWatchingGridState: LazyGridState,
    contentEntryLink: FocusRequester,
    barTabLink: FocusRequester
) {
    composable(Screen.Home.route) {
        HomeScreen(
            onNavigateToDetail = { type, slug ->
                navController.navigate(Screen.Detail.createRoute(type, slug))
            },
            onNavigateToPlayer = { type, id, title, poster, backdrop, slug, season, episode ->
                navController.navigate(Screen.Player.createRoute(type, id, title, poster, backdrop, slug, season, episode))
            },
            onNavigateToCollection = { slug, name ->
                navController.navigate(Screen.CollectionDetail.createRoute(slug, name))
            },
            refocusKey = homeRefocusKey,
            onRefocusKeyChange = onHomeRefocusKeyChange,
            listState = homeListState,
            railListStates = homeRailStates,
            entryLinkRequester = contentEntryLink,
            barLinkRequester = barTabLink
        )
    }

    composable(Screen.ContinueWatching.route) {
        ContinueWatchingScreen(
            onNavigateToPlayer = { type, id, title, poster, backdrop, slug, season, episode ->
                navController.navigate(Screen.Player.createRoute(type, id, title, poster, backdrop, slug, season, episode))
            },
            onNavigateToHome = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = true }
                }
            },
            refocusKey = continueWatchingRefocusKey,
            onRefocusKeyChange = onContinueWatchingRefocusKeyChange,
            gridState = continueWatchingGridState,
            entryLinkRequester = contentEntryLink,
            barLinkRequester = barTabLink
        )
    }

    composable(Screen.Movies.route) {
        MoviesScreen(
            onNavigateToDetail = { type, slug ->
                navController.navigate(Screen.Detail.createRoute(type, slug))
            },
            onNavigateToPlayer = { type, id, title, poster, backdrop, slug ->
                navController.navigate(Screen.Player.createRoute(type, id, title, poster, backdrop, slug))
            },
            refocusKey = moviesRefocusKey,
            onRefocusKeyChange = onMoviesRefocusKeyChange,
            listState = moviesListState,
            railListStates = moviesRailStates,
            entryLinkRequester = contentEntryLink,
            barLinkRequester = barTabLink
        )
    }

    composable(Screen.Series.route) {
        SeriesScreen(
            onNavigateToDetail = { type, slug ->
                navController.navigate(Screen.Detail.createRoute(type, slug))
            },
            onNavigateToPlayer = { type, id, title, poster, backdrop, slug ->
                navController.navigate(Screen.Player.createRoute(type, id, title, poster, backdrop, slug))
            },
            refocusKey = seriesRefocusKey,
            onRefocusKeyChange = onSeriesRefocusKeyChange,
            listState = seriesListState,
            railListStates = seriesRailStates,
            entryLinkRequester = contentEntryLink,
            barLinkRequester = barTabLink
        )
    }

    composable(Screen.Search.route) {
        SearchScreen(
            onNavigateToDetail = { type, slug ->
                navController.navigate(Screen.Detail.createRoute(type, slug))
            },
            refocusKey = searchRefocusKey,
            onRefocusKeyChange = onSearchRefocusKeyChange,
            gridState = searchGridState,
            entryLinkRequester = contentEntryLink,
            barLinkRequester = barTabLink
        )
    }

    composable(Screen.Genres.route) {
        GenresScreen(
            onNavigateToGenre = { slug, name ->
                navController.navigate(Screen.GenreDetail.createRoute(slug, name))
            },
            refocusKey = genresRefocusKey,
            onRefocusKeyChange = onGenresRefocusKeyChange,
            gridState = genresGridState,
            entryLinkRequester = contentEntryLink,
            barLinkRequester = barTabLink
        )
    }

    composable(
        route = Screen.GenreDetail.route,
        arguments = listOf(
            navArgument("slug") { type = NavType.StringType },
            navArgument("name") {
                type = NavType.StringType
                nullable = true
                defaultValue = ""
            }
        )
    ) { backStackEntry ->
        val slug = backStackEntry.arguments?.getString("slug") ?: ""
        val rawName = backStackEntry.arguments?.getString("name") ?: ""
        val name = try {
            URLDecoder.decode(rawName, StandardCharsets.UTF_8.toString())
        } catch (e: Exception) {
            rawName
        }
        GenreDetailScreen(
            genreSlug = slug,
            genreName = name.ifBlank { slug },
            onNavigateToDetail = { type, detailSlug ->
                navController.navigate(Screen.Detail.createRoute(type, detailSlug))
            },
            onBack = { navController.popBackStack() },
            refocusKey = genreDetailRefocusKey,
            onRefocusKeyChange = onGenreDetailRefocusKeyChange,
            listState = genreDetailListState,
            railListStates = genreDetailRailStates,
            entryLinkRequester = contentEntryLink,
            barLinkRequester = barTabLink
        )
    }

    composable(
        route = Screen.CollectionDetail.route,
        arguments = listOf(
            navArgument("slug") { type = NavType.StringType },
            navArgument("name") {
                type = NavType.StringType
                nullable = true
                defaultValue = ""
            }
        )
    ) { backStackEntry ->
        val slug = backStackEntry.arguments?.getString("slug") ?: ""
        val rawName = backStackEntry.arguments?.getString("name") ?: ""
        val name = try {
            URLDecoder.decode(rawName, StandardCharsets.UTF_8.toString())
        } catch (e: Exception) {
            rawName
        }
        CollectionDetailScreen(
            collectionSlug = slug,
            collectionName = name.ifBlank { slug },
            onNavigateToDetail = { type, detailSlug ->
                navController.navigate(Screen.Detail.createRoute(type, detailSlug))
            },
            onBack = { navController.popBackStack() },
            refocusKey = collectionDetailRefocusKey,
            onRefocusKeyChange = onCollectionDetailRefocusKeyChange,
            listState = collectionDetailListState,
            railListStates = collectionDetailRailStates,
            entryLinkRequester = contentEntryLink,
            barLinkRequester = barTabLink
        )
    }

    composable(Screen.Settings.route) {
        SettingsScreen(
            entryLinkRequester = contentEntryLink,
            barLinkRequester = barTabLink,
            refocusKey = settingsRefocusKey,
            onRefocusKeyChange = onSettingsRefocusKeyChange,
            onOpenDevTools = { navController.navigate(Screen.DevTools.route) }
        )
    }

    composable(Screen.DevTools.route) {
        DevToolsScreen(
            currentRoute = currentRoute,
            entryLinkRequester = contentEntryLink,
            barLinkRequester = barTabLink,
            onBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.Detail.route,
        arguments = listOf(
            navArgument("contentType") { type = NavType.StringType },
            navArgument("slug") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val contentType = backStackEntry.arguments?.getString("contentType") ?: "movie"
        val slug = backStackEntry.arguments?.getString("slug") ?: ""

        DetailScreen(
            contentType = contentType,
            slug = slug,
            onNavigateToPlayer = { type, id, title, poster, backdrop, slugParam, season, episode ->
                navController.navigate(Screen.Player.createRoute(type, id, title, poster, backdrop, slugParam, season, episode))
            },
            onBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.Player.route,
        arguments = listOf(
            navArgument("contentType") { type = NavType.StringType },
            navArgument("contentId") { type = NavType.StringType },
            navArgument("title") { type = NavType.StringType },
            navArgument("poster") {
                type = NavType.StringType
                nullable = true
                defaultValue = ""
            },
            navArgument("backdrop") {
                type = NavType.StringType
                nullable = true
                defaultValue = ""
            },
            navArgument("slug") {
                type = NavType.StringType
                nullable = true
                defaultValue = ""
            },
            navArgument("season") {
                type = NavType.StringType
                nullable = true
                defaultValue = ""
            },
            navArgument("episode") {
                type = NavType.StringType
                nullable = true
                defaultValue = ""
            }
        )
    ) { backStackEntry ->
        val contentType = backStackEntry.arguments?.getString("contentType") ?: "movie"
        val contentId = backStackEntry.arguments?.getString("contentId") ?: ""
        val rawTitle = backStackEntry.arguments?.getString("title") ?: ""
        val title = try {
            URLDecoder.decode(rawTitle, StandardCharsets.UTF_8.toString())
        } catch (e: Exception) {
            rawTitle
        }
        val rawPoster = backStackEntry.arguments?.getString("poster") ?: ""
        val poster = try {
            URLDecoder.decode(rawPoster, StandardCharsets.UTF_8.toString()).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            rawPoster.takeIf { it.isNotBlank() }
        }
        val rawBackdrop = backStackEntry.arguments?.getString("backdrop") ?: ""
        val backdrop = try {
            URLDecoder.decode(rawBackdrop, StandardCharsets.UTF_8.toString()).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            rawBackdrop.takeIf { it.isNotBlank() }
        }
        val rawSlug = backStackEntry.arguments?.getString("slug") ?: ""
        val slug = try {
            URLDecoder.decode(rawSlug, StandardCharsets.UTF_8.toString()).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            rawSlug.takeIf { it.isNotBlank() }
        }
        val season = backStackEntry.arguments?.getString("season")?.toIntOrNull()
        val episode = backStackEntry.arguments?.getString("episode")?.toIntOrNull()

        PlayerScreen(
            contentType = contentType,
            contentId = contentId,
            title = title,
            posterUrl = poster,
            backdropUrl = backdrop,
            slug = slug,
            seasonNumber = season,
            episodeNumber = episode,
            onBack = { navController.popBackStack() }
        )
    }
}
