package app.youlauncher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.youlauncher.ui.components.TextFieldRow
import app.youlauncher.ui.screens.instance.EnvText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TextFieldRowTest {
    @Test
    fun `typing survives a model that answers late and in another form`() = runComposeUiTest {
        var saved: List<String> = emptyList()
        setContent {
            // Модель как у экземпляра: сохраняет с задержкой и хранит теги списком.
            var tags by remember { mutableStateOf(emptyList<String>()) }
            val scope = rememberCoroutineScope()
            TextFieldRow("Tags", tags.joinToString(", "), { v ->
                scope.launch {
                    delay(30)
                    tags = v.split(',').map(String::trim).filter(String::isNotEmpty)
                    saved = tags
                }
            })
        }
        val field = onNode(hasSetTextAction())
        field.performClick()
        "alpha, beta".forEach { field.performTextInput(it.toString()) }
        waitForIdle()
        mainClock.advanceTimeBy(200)
        waitForIdle()
        field.assertTextContains("alpha, beta")
        assertEquals(listOf("alpha", "beta"), saved)
    }

    @Test
    fun `unfinished environment line is kept while typing`() = runComposeUiTest {
        setContent {
            var env by remember { mutableStateOf(emptyMap<String, String>()) }
            TextFieldRow("Env", EnvText.format(env), { v -> env = EnvText.parse(v) }, singleLine = false)
        }
        val field = onNode(hasSetTextAction())
        field.performClick()
        "JAVA".forEach { field.performTextInput(it.toString()) }
        waitForIdle()
        field.assertTextContains("JAVA")
    }
}
