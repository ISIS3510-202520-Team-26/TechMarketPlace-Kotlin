package com.techmarketplace.core.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.techmarketplace.core.designsystem.GreenDark

private val BottomBarHeight = 84.dp


@Composable
fun GreenScaffold(
    selected: BottomItem,
    onNavigateBottom: (BottomItem) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barSpace = BottomBarHeight + bottomInset

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenDark)
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = barSpace)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                content = content
            )
        }

        // Bottom bar
        Column(Modifier.align(Alignment.BottomCenter)) {
            BottomBar(selected = selected, onNavigate = onNavigateBottom)
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
