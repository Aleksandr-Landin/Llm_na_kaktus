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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cactus.CactusContextInitializer
import com.cactus.CactusLM
import com.cactus.CactusInitParams
import com.cactus.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.cactus.CactusModelManager

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

// Функция для копирования выбранного файла во внутреннюю память приложения
fun copyUriToFile(context: Context, uri: Uri, destinationFile: File) {
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(destinationFile).use { output ->
            input.copyTo(output)
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
    
    // Настройки
    var modelNameInput by remember { mutableStateOf("qwen3-0.6") }
    var systemPromptInput by remember { 
        mutableStateOf("Ты — ИСКрИнка, теплая, заботливая и аналитическая виртуальная подруга Александра. Отвечай коротко, дружелюбно и по делу.") 
    }
    
    // Для локального файла
    var selectedModelUri by remember { mutableStateOf<Uri?>(null) }
    var selectedModelFileName by remember { mutableStateOf("Файл не выбран") }
    
    val lm = remember { CactusLM() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Лаунчер для выбора файла из памяти телефона
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedModelUri = uri
            selectedModelFileName = "Выбран локальный файл"
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
                    Text("Системный промпт (Роль):")
                    OutlinedTextField(
                        value = systemPromptInput,
                        onValueChange = { systemPromptInput = it },
                        minLines = 3,
                        maxLines = 5
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Вариант 1: Скачать по имени из Cactus:")
                    OutlinedTextField(
                        value = modelNameInput,
                        onValueChange = { modelNameInput = it },
                        singleLine = true,
                        enabled = selectedModelUri == null
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Вариант 2: Загрузить локальный файл (.gguf):")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { filePickerLauncher.launch("*/*") }) {
                            Text("Выбрать файл")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedModelFileName, style = MaterialTheme.typography.bodySmall)
                    }
                    if (selectedModelUri != null) {
                        TextButton(onClick = { selectedModelUri = null; selectedModelFileName = "Файл не выбран" }) {
                            Text("Сбросить выбор файла")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSettingsDialog = false
                        
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    if (selectedModelUri != null) {
                                        statusText = "Копирование локального файла..."
                                        
                                        // Получаем путь (строку) и превращаем его в объект File
                                        val modelsDirPath = CactusModelManager.getModelsDirectory()
                                        val modelsDir = File(modelsDirPath)
                                        
                                        // Создаем папку, если её нет
                                        if (!modelsDir.exists()) {
                                            modelsDir.mkdirs()
                                        }
                                        
                                        val localFile = File(modelsDir, "local_model.gguf")
                                        copyUriToFile(context, selectedModelUri!!, localFile)
                                        
                                        if (!localFile.exists() || localFile.length() == 0L) {
                                            throw Exception("Файл не скопировался. Проверь свободное место.")
                                        }
                                        
                                        val fileSizeMB = localFile.length() / (1024 * 1024)
                                        statusText = "Файл скопирован ($fileSizeMB МБ). Загрузка..."
                                        
                                        // ИСПРАВЛЕНО: передаем полный путь к файлу (absolutePath)
                                        lm.initializeModel(CactusInitParams(modelPath = localFile.absolutePath, contextSize = 2048))
                                    } else {
                                        statusText = "Скачивание модели $modelNameInput..."
                                        lm.downloadModel(modelNameInput)
                                        statusText = "Инициализация модели..."
                                        lm.initializeModel(CactusInitParams(model = modelNameInput, contextSize = 2048))
                                    }
                                }
                                statusText = "Модель готова к работе!"
                            } catch (e: Exception) {
                                statusText = "Ошибка: ${e.message}"
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
