package com.example.limnakaktus

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.codeshipping.llamakotlin.LlamaConfig
import org.codeshipping.llamakotlin.LlamaModel
import java.io.File

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
    var messages by remember { mutableStateOf(listOf("Привет, Саша! Я готова к работе.")) }
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
                    enabled = !isGenerating && llamaModel != null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val userMessage = inputText
                        if (userMessage.isNotBlank() && llamaModel != null) {
                            messages = messages + "Вы: $userMessage"
                            inputText = ""
                            isGenerating = true
                            
                            scope.launch {
                                try {
                                    var fullResponse = ""
                                    withContext(Dispatchers.IO) {
                                        llamaModel?.generateStream(
                                            prompt = "$systemPromptInput\n\nПользователь: $userMessage\nИСКрИнка:"
                                        )?.collect { token ->
                                            fullResponse += token
                                        }
                                    }
                                    messages = messages + "ИСКрИнка: $fullResponse"
                                } catch (e: Exception) {
                                    messages = messages + "Ошибка: ${e.message}"
                                } finally {
                                    isGenerating = false
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
