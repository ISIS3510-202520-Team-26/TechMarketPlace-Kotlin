// app/src/main/java/com/techmarketplace/feature/profile/ProfileRoute.kt
package com.techmarketplace.feature.profile

import androidx.compose.runtime.Composable
import com.techmarketplace.core.ui.BottomItem

@Composable
fun ProfileRoute(
    onNavigateBottom: (BottomItem) -> Unit,
    onOpenListing: (String) -> Unit,
    onSignOut: () -> Unit
) {
    ProfileScreen(
        onNavigateBottom = onNavigateBottom,
        onOpenListing = onOpenListing,
        onSignOut = onSignOut
    )
}
