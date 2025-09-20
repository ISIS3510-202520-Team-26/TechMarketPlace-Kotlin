package com.techmarketplace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.techmarketplace.core.designsystem.OffWhite

@Composable
fun PageDots(total: Int, selectedIndex: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(total) { i ->
            val selected = i == selectedIndex
            Box(
                modifier = Modifier
                    .size(if (selected) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (selected) OffWhite else OffWhite.copy(alpha = 0.5f))
            )
        }
    }
}
