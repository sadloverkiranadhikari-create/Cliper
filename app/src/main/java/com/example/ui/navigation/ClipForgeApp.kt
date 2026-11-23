package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.ProjectDetailScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.viewmodel.ClipForgeViewModel

const val ROUTE_STUDIO = "studio"
const val ROUTE_PROJECTS = "projects"
const val ROUTE_DOWNLOADS = "downloads"
const val ROUTE_PROJECT_DETAIL = "project_detail/{projectId}"

private data class TopLevelTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelTabs = listOf(
    TopLevelTab(
        route = ROUTE_STUDIO,
        labelRes = R.string.nav_studio,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_tab_studio"
    ),
    TopLevelTab(
        route = ROUTE_PROJECTS,
        labelRes = R.string.nav_projects,
        selectedIcon = Icons.Filled.FolderCopy,
        unselectedIcon = Icons.Outlined.FolderCopy,
        testTag = "nav_tab_projects"
    ),
    TopLevelTab(
        route = ROUTE_DOWNLOADS,
        labelRes = R.string.nav_downloads,
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Outlined.Download,
        testTag = "nav_tab_downloads"
    )
)

@Composable
fun ClipForgeApp(
    viewModel: ClipForgeViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val exportedClips by viewModel.exportedClips.collectAsStateWithLifecycle()
    val exportStatuses by viewModel.exportStatuses.collectAsStateWithLifecycle()
    val isBatchExporting by viewModel.isBatchExporting.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbar()
        }
    }

    val isTopLevelRoute = currentRoute in topLevelTabs.map { it.route }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (isTopLevelRoute && !useNavigationRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topLevelTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(text = stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevelRoute && useNavigationRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topLevelTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(text = stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_STUDIO,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        composable(ROUTE_STUDIO) {
                            StudioScreen(
                                uiState = studioState,
                                recentProject = projects.firstOrNull(),
                                onUrlChanged = viewModel::onUrlChanged,
                                onFocusSelected = viewModel::onFocusSelected,
                                onDurationSelected = viewModel::onDurationSelected,
                                onAnalyzeClick = {
                                    viewModel.analyzeYouTubeLink { projectId ->
                                        navController.navigate("project_detail/$projectId")
                                    }
                                },
                                onRetryAnalyze = {
                                    viewModel.clearAnalyzeError()
                                    viewModel.analyzeYouTubeLink { projectId ->
                                        navController.navigate("project_detail/$projectId")
                                    }
                                },
                                onOpenProject = { projectId ->
                                    navController.navigate("project_detail/$projectId")
                                },
                                onShowMessage = viewModel::showMessage
                            )
                        }

                        composable(ROUTE_PROJECTS) {
                            ProjectsScreen(
                                projects = projects,
                                onOpenProject = { projectId ->
                                    navController.navigate("project_detail/$projectId")
                                },
                                onDeleteProject = viewModel::deleteProject
                            )
                        }

                        composable(ROUTE_DOWNLOADS) {
                            DownloadsScreen(exportedClips = exportedClips)
                        }

                        composable(
                            route = ROUTE_PROJECT_DETAIL,
                            arguments = listOf(
                                navArgument("projectId") { type = NavType.LongType }
                            )
                        ) { entry ->
                            val projectId = entry.arguments?.getLong("projectId") ?: 0L
                            val projectFlow = remember(projectId) {
                                viewModel.observeProject(projectId)
                            }
                            val projectWithClips by projectFlow.collectAsStateWithLifecycle(
                                initialValue = null
                            )

                            ProjectDetailScreen(
                                projectWithClips = projectWithClips,
                                exportStatuses = exportStatuses,
                                isBatchExporting = isBatchExporting,
                                onBack = { navController.popBackStack() },
                                onAttachLocalVideo = { uri ->
                                    viewModel.attachLocalVideo(projectId, uri)
                                },
                                onAttachDirectUrl = { url ->
                                    viewModel.attachDirectMp4Url(projectId, url)
                                },
                                onUpdateClipRange = viewModel::updateClipRange,
                                onExportClipMp4 = viewModel::exportClipMp4,
                                onExportAllClips = viewModel::exportAllClips,
                                onDownloadManifest = { format ->
                                    viewModel.downloadManifest(projectId, format)
                                },
                                onShowMessage = viewModel::showMessage
                            )
                        }
                    }
                }
            }
        }
    }
}
