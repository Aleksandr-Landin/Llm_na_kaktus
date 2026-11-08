package com.example.limnakaktus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cactus.CactusContextInitializer
import com.cactus.CactusLM
import com.cactus.CactusInitParams
import com.cactus.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CactusContextInitializer.initialize(this)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IskrinkaChatScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IskrinkaChatScreen() {
    var messages by remember { mutableStateOf(listOf("Привет, Саша! Я готова к работе.")) }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Модель не загружена") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    var modelNameInput by remember { mutableStateOf("qwen3-0.6") }
    var systemPromptInput by remember { 
        mutableStateOf("Ты — ИСКрИнка, теплая, заботливая и аналитическая виртуальная подруга Александра. Отвечай коротко, дружелюбно и по делу.") 
    }
    
    val lm = remember { CactusLM() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Iskrinka") },
                actions = {
                    Button(onClick = { showSettingsDialog = true }) {
                        Text("Настройки")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(messages.size) { index ->
                    Text(
                        text = messages[index],
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Напиши сообщение...") },
                    enabled = !isGenerating && statusText.contains("готова")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val userMessage = inputText
                            messages = messages + "Вы: $userMessage"
                            inputText = ""
                            isGenerating = true
                            
                            scope.launch {
                                try {
                                    val chatMessages = listOf(
                                        ChatMessage(content = systemPromptInput, role = "system"),
                                        ChatMessage(content = userMessage, role = "user")
                                    )
                                    val response = withContext(Dispatchers.IO) {
                                        lm.generateCompletion(chatMessages)
                                    }
                                    if (response != null && response.success) {
                                        messages = messages + "ИСКрИнка: ${response.response}"
                                    } else {
                                        messages = messages + "Ошибка генерации."
                                    }
                                } catch (e: Exception) {
                                    messages = messages + "Ошибка: ${e.message}"
                                } finally {
                                    isGenerating = false
                                }
                            }
                        }
                    },
                    enabled = !isGenerating && statusText.contains("готова")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Отправить")
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Настройки ИСКрИнки") },
            text = {
                Column {
                    Text("Название модели (из репозитория Cactus):")
                    OutlinedTextField(
                        value = modelNameInput,
                        onValueChange = { modelNameInput = it },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Системный промпт (Роль):")
                    OutlinedTextField(
                        value = systemPromptInput,
                        onValueChange = { systemPromptInput = it },
                        minLines = 3,
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSettingsDialog = false
                        statusText = "Скачивание модели $modelNameInput..."
                        
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    lm.downloadModel(modelNameInput)
                                    statusText = "Инициализация модели..."
                                    lm.initializeModel(CactusInitParams(model = modelNameInput, contextSize = 2048))
                                }
                                statusText = "Модель $modelNameInput готова к работе!"
                            } catch (e: Exception) {
                                statusText = "Ошибка: ${e.message}"
                            }
                        }
                    }
                ) {
                    Text("Применить и Скачать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
