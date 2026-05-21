package com.zure.localaienginetester.ui.screen.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.zure.localaienginetester.ui.component.AppScaffold
import com.zure.localaienginetester.ui.theme.LocalAIEngineTesterTheme

private val TranslationLanguages = listOf(
    "Auto",
    "Chinese",
    "English",
    "Japanese",
    "Korean",
    "French",
    "German",
    "Spanish"
)

@Composable
fun TranslationScreen(
    navController: NavController,
    viewModel: TranslationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TranslationContent(
        uiState = uiState,
        onBackClick = { navController.popBackStack() },
        onSourceLanguageChange = viewModel::updateSourceLanguage,
        onTargetLanguageChange = viewModel::updateTargetLanguage,
        onSourceTextChange = viewModel::updateSourceText,
        onTranslateClick = viewModel::translate
    )
}

@Composable
private fun TranslationContent(
    uiState: TranslationUiData,
    onBackClick: () -> Unit,
    onSourceLanguageChange: (String) -> Unit,
    onTargetLanguageChange: (String) -> Unit,
    onSourceTextChange: (String) -> Unit,
    onTranslateClick: () -> Unit
) {
    AppScaffold(
        title = uiState.modelName,
        onBackClick = onBackClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                LanguageRow(
                    title = "原文本语言",
                    selectedLanguage = uiState.sourceLanguage,
                    onLanguageChange = onSourceLanguageChange
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.sourceText,
                    onValueChange = onSourceTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = { Text(text = "输入文本") }
                )
            }

            Button(
                onClick = onTranslateClick,
                enabled = uiState.sourceText.isNotBlank() && !uiState.isTranslating,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(text = if (uiState.isTranslating) "翻译中" else "翻译")
            }
            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                LanguageRow(
                    title = "翻译语言",
                    selectedLanguage = uiState.targetLanguage,
                    onLanguageChange = onTargetLanguageChange
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.outputText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = { Text(text = "输出文本") }
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(
    title: String,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        LanguageMenu(
            selectedLanguage = selectedLanguage,
            onLanguageChange = onLanguageChange
        )
    }
}

@Composable
private fun LanguageMenu(
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        OutlinedButton(onClick = { expanded = true }) {
            Text(text = selectedLanguage)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TranslationLanguages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(text = language) },
                    onClick = {
                        expanded = false
                        onLanguageChange(language)
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TranslationScreenPreview() {
    LocalAIEngineTesterTheme {
        TranslationContent(
            uiState = TranslationUiData(
                modelName = "Hy-MT1.5-1.8B-1.25bit-GGUF.gguf",
                sourceText = "Hello world",
                outputText = "你好，世界"
            ),
            onBackClick = {},
            onSourceLanguageChange = {},
            onTargetLanguageChange = {},
            onSourceTextChange = {},
            onTranslateClick = {}
        )
    }
}
