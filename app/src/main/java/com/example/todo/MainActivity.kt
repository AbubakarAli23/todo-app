package com.example.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Entity(tableName = "todos")
data class Todo(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val completed: Boolean = false)

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY completed ASC, id DESC") fun observeAll(): Flow<List<Todo>>
    @Insert suspend fun insert(todo: Todo)
    @Update suspend fun update(todo: Todo)
    @Delete suspend fun delete(todo: Todo)
}

@Database(entities = [Todo::class], version = 1, exportSchema = false)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    companion object { @Volatile private var instance: TodoDatabase? = null
        fun get(context: android.content.Context) = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context, TodoDatabase::class.java, "todos.db").build().also { instance = it } }
    }
}

class TodoViewModel(private val dao: TodoDao) : ViewModel() {
    val todos = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun add(title: String) { viewModelScope.launch { dao.insert(Todo(title = title.trim())) } }
    fun toggle(todo: Todo) { viewModelScope.launch { dao.update(todo.copy(completed = !todo.completed)) } }
    fun delete(todo: Todo) { viewModelScope.launch { dao.delete(todo) } }
}

@Composable fun TodoApp(vm: TodoViewModel) {
    val todos by vm.todos.collectAsState()
    var input by remember { mutableStateOf("") }
    val remaining = todos.count { !it.completed }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF6750A4), background = Color(0xFFF9F7FF))) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(Modifier.padding(padding).padding(horizontal = 22.dp).fillMaxSize()) {
                Spacer(Modifier.height(24.dp))
                Text("My tasks", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(if (remaining == 0) "All caught up" else "$remaining task${if (remaining == 1) "" else "s"} remaining", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("What needs doing?") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = { if (input.isNotBlank()) { vm.add(input); input = "" } }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) { Text("Add") }
                }
                Spacer(Modifier.height(20.dp))
                if (todos.isEmpty()) EmptyState() else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(todos, key = { it.id }) { todo -> TodoRow(todo, { vm.toggle(todo) }, { vm.delete(todo) }) }
                }
            }
        }
    }
}

@Composable fun TodoRow(todo: Todo, toggle: () -> Unit, delete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(todo.completed, { toggle() })
            Text(todo.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (todo.completed) Color.Gray else Color.Unspecified)
            TextButton(onClick = delete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
}
@Composable fun EmptyState() { Box(Modifier.fillMaxWidth().padding(top = 70.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("No tasks yet", style = MaterialTheme.typography.titleLarge); Text("Add something to get started", color = Color.Gray) } } }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); val dao = TodoDatabase.get(applicationContext).todoDao(); setContent { TodoApp(TodoViewModel(dao)) } }
}
