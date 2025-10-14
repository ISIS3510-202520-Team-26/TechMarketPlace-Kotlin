package com.techmarketplace.feature.profile

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.ui.profile.ProfileViewModel

@Composable
fun ProfileRoute(
    app: Application = LocalContext.current.applicationContext as Application,
    onNavigateBottom: (BottomItem) -> Unit,
    onOpenListing: (String) -> Unit = {},
    onSignOut: () -> Unit = {},   // MainActivity proveerá logout real (Firebase + borrar tokens)
) {
    val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(app))
    val ui by vm.ui.collectAsState()

    ProfileScreen(
        name = ui.user?.name ?: "",
        email = ui.user?.email ?: "",
        campus = ui.user?.campus,
        listings = ui.listings,
        loading = ui.loading && ui.user == null && ui.listings.isEmpty(),
        refreshing = ui.refreshing,
        hasNext = ui.hasNext,
        error = ui.error,
        onDismissError = vm::clearError,
        onRefresh = vm::refresh,
        onLoadMore = vm::loadNext,
        onOpenListing = onOpenListing,
        onSignOut = onSignOut,
        onNavigateBottom = onNavigateBottom
    )
}
