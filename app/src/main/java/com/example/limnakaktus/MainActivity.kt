package com.example.limnakaktus

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.codeshipping.llamakotlin.LlamaConfig
import org.codeshipping.llamakotlin.LlamaModel
import java.io.File

// Новая правильная структура для сообщений
data class ChatMessage(
    val isUser: Boolean,
    val text: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    // Теперь используем список объектов ChatMessage
    val messages = remember { mutableStateListOf(ChatMessage(false, "Привет, Саша! Я готова к работе.")) }
    
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Модель не загружена") }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var systemPromptInput by remember { 
        mutableStateOf("Ты — ИСКрИнка, теплая, заботливая и аналитическая виртуальная подруга Александра. Отвечай коротко, дружелюбно и по делу.") 
    }
    var selectedModelUri by remember { mutableStateOf<Uri?>(null) }
    var selectedModelFileName by remember { mutableStateOf("Файл не выбран") }

    var llamaModel by remember { mutableStateOf<LlamaModel?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedModelUri = uri
            selectedModelFileName = uri.lastPathSegment ?: "Выбран файл"
        }
    }

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
            
            // Красивый вывод сообщений
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(messages) { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = if (message.isUser) message.text else "ИСКрИнка: ${message.text}",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Напиши сообщение...") },
                    enabled = !isGenerating && llamaModel != null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val userMessage = inputText
                        if (userMessage.isNotBlank() && llamaModel != null) {
                            messages.add(ChatMessage(true, userMessage))
                            inputText = ""
                            isGenerating = true
                            
                            // Добавляем пустое сообщение от ИСКрИнки, которое будем наполнять токенами
                            messages.add(ChatMessage(false, ""))
                            val aiMessageIndex = messages.lastIndex
                            
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        // Берем последние 10 сообщений для контекста
                                        val historySlice = messages.dropLast(1).takeLast(10)
                                        
                                        // Формируем правильный ChatML промпт
                                        val historyPrompt = historySlice.joinToString("\n") { msg ->
                                            if (msg.isUser) "<|im_start|>user\n${msg.text}<|im_end|>"
                                            else "<|im_start|>assistant\n${msg.text}<|im_end|>"
                                        }

                                        val prompt = """
                                            <|im_start|>system
                                            $systemPromptInput<|im_end|>
                                            $historyPrompt
                                            <|im_start|>assistant
                                        """.trimIndent()

                                        llamaModel?.generateStream(prompt = prompt)?.collect { token ->
                                            if (!token.contains("<|im_end|>") && !token.contains("<|im_start|>")) {
                                                // Обновляем сообщение прямо на экране в реальном времени
                                                withContext(Dispatchers.Main) {
                                                    val currentMsg = messages[aiMessageIndex]
                                                    messages[aiMessageIndex] = currentMsg.copy(text = currentMsg.text + token)
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        messages[aiMessageIndex] = ChatMessage(false, "Ошибка: ${e.message}")
                                    }
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        isGenerating = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isGenerating && llamaModel != null
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
                    Text("Системный промпт (Роль):")
                    OutlinedTextField(
                        value = systemPromptInput,
                        onValueChange = { systemPromptInput = it },
                        minLines = 3,
                        maxLines = 5
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Локальная модель (.gguf):")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { filePickerLauncher.launch("*/*") }) {
                            Text("Выбрать файл")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedModelFileName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSettingsDialog = false
                        selectedModelUri?.let { uri ->
                            statusText = "Подготовка к загрузке..."
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        llamaModel?.close() 
                                        
                                        statusText = "Копирование модели..."
                                        val inputStream = context.contentResolver.openInputStream(uri)
                                        val tempFile = File(context.cacheDir, "local_model.gguf")
                                        inputStream?.use { input ->
                                            tempFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }

                                        statusText = "Загрузка локальной модели..."
                                        val config = LlamaConfig(
                                            contextSize = 2048,
                                            threads = 4
                                        )
                                        llamaModel = LlamaModel.load(
                                            modelPath = tempFile.absolutePath,
                                            config = config
                                        )
                                    }
                                    statusText = "Модель успешно загружена!"
                                } catch (e: Exception) {
                                    statusText = "Ошибка загрузки: ${e.message}"
                                    llamaModel = null
                                }
                            }
                        }
                    }
                ) {
                    Text("Применить")
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
