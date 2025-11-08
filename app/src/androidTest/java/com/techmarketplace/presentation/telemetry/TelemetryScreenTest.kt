package com.techmarketplace.presentation.telemetry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.techmarketplace.presentation.telemetry.view.TelemetryScreen
import com.techmarketplace.presentation.telemetry.viewmodel.SellerRankingRowUi
import com.techmarketplace.presentation.telemetry.viewmodel.SellerResponseMetricsUi
import com.techmarketplace.presentation.telemetry.viewmodel.TelemetryUiState
import org.junit.Rule
import org.junit.Test

class TelemetryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shows_offline_banner_when_flag_enabled() {
        val metrics = SellerResponseMetricsUi(
            responseRatePercent = 90,
            averageResponseMinutes = 15.0,
            totalConversations = 12,
            lastUpdatedMillis = System.currentTimeMillis(),
            ranking = listOf(SellerRankingRowUi(1, "Ana", 90, 12.0)),
            updatedAtIso = null
        )

        composeRule.setContent {
            TelemetryScreen(
                state = TelemetryUiState(
                    isLoading = false,
                    metrics = metrics,
                    errorMessage = null,
                    isOffline = true
                ),
                onBack = {},
                onRetry = {}
            )
        }

        composeRule.onNodeWithText("Sin conexión. Se muestran datos en caché.").assertIsDisplayed()
    }
}
