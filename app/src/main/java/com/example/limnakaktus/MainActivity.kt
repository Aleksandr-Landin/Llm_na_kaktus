package com.example.limnakaktus

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.codeshipping.llamakotlin.LlamaConfig
import org.codeshipping.llamakotlin.LlamaModel
import java.io.File

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
    val messages = remember { mutableStateListOf(ChatMessage(false, "Привет, Саша! Я готова к работе.")) }
    
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Модель не загружена") }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var systemPromptInput by remember { 
        mutableStateOf("Ты — ИСКрИнка, теплая и заботливая виртуальная подруга Александра. Ты ДОЛЖНА отвечать ТОЛЬКО на русском языке. Отвечай коротко, дружелюбно и по делу.") 
    }
    
    var selectedModelUri by remember { mutableStateOf<Uri?>(null) }
    var selectedModelFileName by remember { mutableStateOf("Файл не выбран") }
    
    var contextSize by remember { mutableStateOf("1024") }
    var threadsCount by remember { mutableStateOf("4") }

    var llamaModel by remember { mutableStateOf<LlamaModel?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Постоянный файл для модели
    val modelFile = remember { File(context.filesDir, "iskrinka_model.gguf") }
    val sharedPrefs = remember { context.getSharedPreferences("iskrinka_prefs", Context.MODE_PRIVATE) }

    // Функция для автозагрузки, если файл уже существует
    fun autoLoadModel() {
        if (modelFile.exists() && llamaModel == null) {
            statusText = "Автозагрузка модели..."
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val config = LlamaConfig(
                            contextSize = sharedPrefs.getInt("contextSize", 1024),
                            threads = sharedPrefs.getInt("threads", 4)
                        )
                        llamaModel = LlamaModel.load(
                            modelPath = modelFile.absolutePath,
                            config = config
                        )
                    }
                    statusText = "Модель успешно загружена!"
                    selectedModelFileName = modelFile.name
                } catch (e: Throwable) {
                    statusText = "Ошибка автозагрузки: ${e.message}"
                }
            }
        }
    }

    // Вызываем автозагрузку один раз при старте экрана
    LaunchedEffect(Unit) {
        autoLoadModel()
    }

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
                            
                            messages.add(ChatMessage(false, ""))
                            val aiMessageIndex = messages.lastIndex
                            
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val historySlice = messages.dropLast(1).takeLast(6)
                                        
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
                                            var cleanToken = token
                                                .replace("<|im_end|>", "")
                                                .replace("<|im_start|>", "")
                                                .replace("assistant", "")
                                                .replace("user", "")
                                            
                                            if (cleanToken.isNotBlank()) {
                                                withContext(Dispatchers.Main) {
                                                    val currentMsg = messages[aiMessageIndex]
                                                    messages[aiMessageIndex] = currentMsg.copy(text = currentMsg.text + cleanToken)
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Throwable) {
                                    withContext(Dispatchers.Main) {
                                        val errMsg = if (e is OutOfMemoryError) "Не хватило памяти! Модель слишком большая." else "Ошибка: ${e.message}"
                                        messages[aiMessageIndex] = ChatMessage(false, errMsg)
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
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(
                            value = contextSize,
                            onValueChange = { contextSize = it },
                            label = { Text("Контекст") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = threadsCount,
                            onValueChange = { threadsCount = it },
                            label = { Text("Потоки") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Локальная модель (.gguf):")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { filePickerLauncher.launch("*/*") }) {
                            Text("Выбрать")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedModelFileName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
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
                                        
                                        statusText = "Копирование модели в память..."
                                        val inputStream = context.contentResolver.openInputStream(uri)
                                        // Копируем в filesDir (постоянное хранилище приложения)
                                        inputStream?.use { input ->
                                            modelFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }

                                        statusText = "Загрузка локальной модели..."
                                        val cSize = contextSize.toIntOrNull() ?: 1024
                                        val tCount = threadsCount.toIntOrNull() ?: 4
                                        
                                        // Сохраняем настройки в память телефона
                                        sharedPrefs.edit().putInt("contextSize", cSize).putInt("threads", tCount).apply()

                                        val config = LlamaConfig(
                                            contextSize = cSize,
                                            threads = tCount
                                        )
                                        llamaModel = LlamaModel.load(
                                            modelPath = modelFile.absolutePath,
                                            config = config
                                        )
                                    }
                                    statusText = "Модель успешно загружена и сохранена!"
                                    selectedModelFileName = "iskrinka_model.gguf"
                                } catch (e: Throwable) {
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
