package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.document.ui.DocumentApp
import com.example.document.ui.DocumentUiState
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class GreetingScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun documentLoginScreen_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme {
                DocumentApp(
                    state = DocumentUiState(initialLoading = false),
                    onGoogleSignIn = {},
                    onViewerSignIn = { _, _ -> },
                    onConnectDrive = {},
                    onRefresh = {},
                    onUploadPdf = { _, _, _ -> },
                    onOpenPdf = {},
                    onToggleSigned = {},
                    onUpdateRevision = { _, _ -> },
                    onConfigureDrive = { _, _ -> },
                    onCreateViewer = { _, _, _ -> },
                    onSignOut = {},
                    onClosePdf = {},
                    onClearFeedback = {},
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/document-login.png",
        )
    }
}
