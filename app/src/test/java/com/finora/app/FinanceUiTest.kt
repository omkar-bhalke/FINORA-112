package com.finora.app

import android.app.Application
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class FinanceUiTest {
    @get:Rule val compose = createComposeRule()

    private val lunch =
        Entry(
            "lunch",
            "Lunch at college",
            12550,
            category = "Food",
            time = System.currentTimeMillis(),
        )
    private val refund =
        Entry(
            "refund",
            "Travel refund",
            20000,
            credit = true,
            category = "Travel",
            time = System.currentTimeMillis(),
        )

    @Test
    fun searchThenOpenTransaction() {
        var destination = ""
        compose.setContent {
            FinoraTheme {
                androidx.compose.foundation.layout.Column(
                    Modifier.verticalScroll(rememberScrollState())
                ) {
                    History(listOf(lunch, refund)) { destination = it }
                }
            }
        }
        compose.onNodeWithText("Search title, account, category or date").performTextInput("Lunch")
        compose.onNodeWithText("Travel refund").assertDoesNotExist()
        compose.onNodeWithText("Lunch at college").assertExists()
        compose.onNodeWithText("Details").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Edit/lunch", destination) }
    }

    @Test
    fun assistantUsesRecordedFoodExpense() {
        compose.setContent {
            FinoraTheme {
                androidx.compose.foundation.layout.Column(
                    Modifier.verticalScroll(rememberScrollState())
                ) {
                    Analysis(listOf(lunch, refund), Profile(), true)
                }
            }
        }
        compose.onNodeWithText("Ask · Highest spending category").performClick()
        compose.onNodeWithText("Food spending").performClick()
        compose.onAllNodesWithText(money(12550)).assertCountEquals(2)
    }
}
