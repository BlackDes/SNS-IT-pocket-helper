package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import com.example.data.TaskItem
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.CartridgeIssuance
import com.example.data.Employee
import com.example.data.EquipmentItem
import com.example.data.InstructionNote
import com.example.data.NewCartridge
import com.example.data.UsedCartridge
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SnsDarkBg
import com.example.ui.theme.SnsRedAccent
import com.example.ui.theme.SnsSilverText
import com.example.ui.theme.SnsSecondaryText
import com.example.ui.theme.SnsCardBg
import com.example.ui.theme.SnsBorder
import com.example.ui.theme.SnsOrange
import com.example.ui.theme.SnsGreen
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

// High level navigation screen states
enum class ScreenState {
    HOME,
    CARTRIDGES,
    EQUIPMENT,
    EMPLOYEES,
    INSTRUCTIONS,
    TASKS,
    INFO
}

// Confirmation state model for our safety keys ("ИЗМЕНИТЬ" / "УДАЛИТЬ")
data class SafetyConfirmState(
    val keywordRequired: String, // "ИЗМЕНИТЬ" or "УДАЛИТЬ"
    val promptMessage: String,
    val onConfirm: () -> Unit
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContainer()
            }
        }
    }
}

// Standalone Custom Top Bar to avoid material exp annotations
@Composable
fun CategoryTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(top = 16.dp, bottom = 10.dp, start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("back_to_home")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = SnsRedAccent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            color = SnsRedAccent,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// Global container managing high level UI layout and safe area offsets
@Composable
fun MainAppContainer() {
    val viewModel: MainViewModel = viewModel()
    var currentScreen by remember { mutableStateOf(ScreenState.HOME) }
    
    // Safety verification popup state
    var safetyConfirm by remember { mutableStateOf<SafetyConfirmState?>(null) }
    
    // Helper function to safely request modification / deletion confirmation
    val requestConfirmation: (String, String, () -> Unit) -> Unit = { keyword, message, action ->
        safetyConfirm = SafetyConfirmState(
            keywordRequired = keyword,
            promptMessage = message,
            onConfirm = {
                action()
                safetyConfirm = null
            }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Background color matching custom theme background
                drawRect(SnsDarkBg)
                // Accent green ambient glowing orb (top-right)
                drawCircle(
                    color = SnsRedAccent.copy(alpha = 0.15f),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.95f, size.height * 0.1f)
                )
                // Orange ambient glowing orb (bottom-left)
                drawCircle(
                    color = SnsOrange.copy(alpha = 0.12f),
                    radius = size.width * 0.65f,
                    center = Offset(size.width * 0.05f, size.height * 0.8f)
                )
                // Soft secondary green/gray ambient light (middle-right)
                drawCircle(
                    color = SnsSecondaryText.copy(alpha = 0.12f),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.9f, size.height * 0.45f)
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = Color.Transparent,
        topBar = {
            if (currentScreen != ScreenState.HOME) {
                CategoryTopBar(
                    title = when (currentScreen) {
                        ScreenState.CARTRIDGES -> "КАРТРИДЖИ"
                        ScreenState.EQUIPMENT -> "ОБОРУДОВАНИЕ"
                        ScreenState.EMPLOYEES -> "СОТРУДНИКИ"
                        ScreenState.INSTRUCTIONS -> "ИНСТРУКЦИИ"
                        ScreenState.TASKS -> "ЗАДАЧИ"
                        ScreenState.INFO -> "МЕНЮ ИНФО"
                        else -> ""
                    },
                    onBack = { currentScreen = ScreenState.HOME }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen switching
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "ScreenSwitch"
            ) { screen ->
                when (screen) {
                    ScreenState.HOME -> HomeScreen(
                        onNavigate = { currentScreen = it }
                    )
                    ScreenState.CARTRIDGES -> CartridgesPagerScreen(
                        viewModel = viewModel,
                        requestConfirm = requestConfirmation,
                        onBack = { currentScreen = ScreenState.HOME }
                    )
                    ScreenState.EQUIPMENT -> EquipmentPagerScreen(
                        viewModel = viewModel,
                        requestConfirm = requestConfirmation,
                        onBack = { currentScreen = ScreenState.HOME }
                    )
                    ScreenState.EMPLOYEES -> EmployeesPagerScreen(
                        viewModel = viewModel,
                        requestConfirm = requestConfirmation,
                        onBack = { currentScreen = ScreenState.HOME }
                    )
                    ScreenState.INSTRUCTIONS -> InstructionsPagerScreen(
                        viewModel = viewModel,
                        requestConfirm = requestConfirmation,
                        onBack = { currentScreen = ScreenState.HOME }
                    )
                    ScreenState.TASKS -> TasksPagerScreen(
                        viewModel = viewModel,
                        requestConfirm = requestConfirmation,
                        onBack = { currentScreen = ScreenState.HOME }
                    )
                    ScreenState.INFO -> AboutAppScreen(
                        onBack = { currentScreen = ScreenState.HOME }
                    )
                }
            }


            // Global safety dialog popup
            safetyConfirm?.let { state ->
                SafetyConfirmDialog(
                    state = state,
                    onDismiss = { safetyConfirm = null }
                )
            }
        }
    }
}

// HOME SCREEN UI
@Composable
fun HomeScreen(onNavigate: (ScreenState) -> Unit) {
    val scrollState = rememberScrollState()
    
    // Import Theme Manager state
    val darkState = com.example.ui.theme.ThemeManager.isDark
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        
        // Brand Header (SNS Group of companies with Theme Toggle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SNS",
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    color = SnsRedAccent,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Group of companies",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = SnsSilverText,
                    letterSpacing = 0.5.sp
                )
            }
            
            // Interactive Theme switcher
            IconButton(
                onClick = { com.example.ui.theme.ThemeManager.isDark = !darkState },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("toggle_theme")
            ) {
                Icon(
                    imageVector = if (darkState) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Сменить тему",
                    tint = SnsRedAccent,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        // Horizontal stylish theme divider
        HorizontalDivider(color = SnsRedAccent, thickness = 4.dp, modifier = Modifier.padding(vertical = 10.dp))
        
        // Pocket assistant Subtitle
        Text(
            text = "для IT специалистов",
            fontSize = 15.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Serif,
            color = SnsSecondaryText,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 16.dp)
        )
        
        // Scrollable functional buttons grid for IT assistants
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeButton(
                title = "База Картриджей",
                subtitle = "Общий учет, поступление, история списания БУ",
                icon = Icons.Default.Print,
                color = SnsRedAccent,
                onClick = { onNavigate(ScreenState.CARTRIDGES) },
                testTag = "btn_cartridges"
            )
            
            HomeButton(
                title = "Реестр Оборудования",
                subtitle = "Учет техники, инвентарные номера, экспорт списков",
                icon = Icons.Default.Computer,
                color = SnsOrange,
                onClick = { onNavigate(ScreenState.EQUIPMENT) },
                testTag = "btn_equipment"
            )
            
            HomeButton(
                title = "Контакты Сотрудников",
                subtitle = "Список коллег, быстрый вызов, копирование телефонов",
                icon = Icons.Default.People,
                color = SnsGreen,
                onClick = { onNavigate(ScreenState.EMPLOYEES) },
                testTag = "btn_employees"
            )
            
            HomeButton(
                title = "Инструкции и Скетчи",
                subtitle = "Блокнот инженера, графические заметки и ошибки",
                icon = Icons.Default.Book,
                color = SnsSilverText,
                onClick = { onNavigate(ScreenState.INSTRUCTIONS) },
                testTag = "btn_instructions"
            )

            HomeButton(
                title = "Задачи и Инциденты",
                subtitle = "Планирование работ, дедлайны, учет решений",
                icon = Icons.Default.CheckCircle,
                color = SnsRedAccent,
                onClick = { onNavigate(ScreenState.TASKS) },
                testTag = "btn_tasks"
            )

            HomeButton(
                title = "О приложении",
                subtitle = "Контакты техподдержки, служба ИТ, разработчик",
                icon = Icons.Default.Info,
                color = SnsOrange,
                onClick = { onNavigate(ScreenState.INFO) },
                testTag = "btn_info"
            )
        }
        
        Text(
            text = "SNS Group IT Pocket Tool v2.2 • Offline Secure Local DB",
            color = SnsSecondaryText,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    }
}

@Composable
fun HomeButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SnsCardBg),
        border = BorderStroke(1.dp, SnsBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f))
                    .border(1.dp, color, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = SnsSilverText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = SnsSecondaryText,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2
                )
            }
            
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = SnsSecondaryText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// CUSTOM SWIPING TAB HEADER
@Composable
fun CategorySwipeHeader(
    subfolders: List<String>,
    currentIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(SnsCardBg, RoundedCornerShape(24.dp))
            .border(1.dp, SnsBorder, RoundedCornerShape(24.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // PREVIOUS SUBFOLDER TEXT (Left Column)
        Box(
            modifier = Modifier
                .weight(0.35f)
                .clickable { if (currentIndex > 0) onTabSelected(currentIndex - 1) },
            contentAlignment = Alignment.CenterStart
        ) {
            if (currentIndex > 0) {
                Text(
                    text = "• " + subfolders[currentIndex - 1],
                    color = SnsSilverText.copy(alpha = 0.40f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Light,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
        }

        // CURRENT CENTRAL SUBFOLDER TEXT (Center Column)
        Box(
            modifier = Modifier.weight(0.3f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = subfolders[currentIndex],
                color = SnsRedAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        }

        // NEXT SUBFOLDER TEXT (Right Column)
        Box(
            modifier = Modifier
                .weight(0.35f)
                .clickable { if (currentIndex < subfolders.size - 1) onTabSelected(currentIndex + 1) },
            contentAlignment = Alignment.CenterEnd
        ) {
            if (currentIndex < subfolders.size - 1) {
                Text(
                    text = subfolders[currentIndex + 1] + " •",
                    color = SnsSilverText.copy(alpha = 0.40f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.End,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
        }
    }
}

// ==========================================
// CARTRIDGES TABBED SECTION
// ==========================================
@Composable
fun CartridgesPagerScreen(
    viewModel: MainViewModel,
    requestConfirm: (String, String, () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val subfolders = listOf("Общий подсчёт", "Новые картриджи", "БУ картриджи", "История выдачи")
    val pagerState = rememberPagerState(pageCount = { subfolders.size })
    val coroutineScope = rememberCoroutineScope()
    
    val newCartridges by viewModel.newCartridges.collectAsStateWithLifecycle()
    val issuances by viewModel.cartridgeIssuances.collectAsStateWithLifecycle()
    val usedCartridges by viewModel.usedCartridges.collectAsStateWithLifecycle()
    val employeesList by viewModel.employees.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        CategorySwipeHeader(
            subfolders = subfolders,
            currentIndex = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            }
        )
        
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> GeneralCountTab(newCartridges, issuances, usedCartridges)
                1 -> NewCartridgesFormTab(viewModel, newCartridges, employeesList, requestConfirm)
                2 -> UsedCartridgesTab(viewModel, usedCartridges, employeesList, requestConfirm)
                3 -> IssuanceHistoryTab(viewModel, issuances)
            }
        }
    }
}

@Composable
fun GeneralCountTab(
    newCartridges: List<NewCartridge>,
    issuances: List<CartridgeIssuance>,
    usedCartridges: List<UsedCartridge>
) {
    // Reactive Stock Calculation
    val remainingStock = remember(newCartridges, issuances) {
        val stockMap = mutableMapOf<String, Int>()
        newCartridges.forEach {
            stockMap[it.name] = (stockMap[it.name] ?: 0) + it.quantityAdded
        }
        issuances.filter { !it.isUsed }.forEach {
            stockMap[it.cartridgeName] = (stockMap[it.cartridgeName] ?: 0) - it.quantity
        }
        stockMap.filter { it.key.isNotBlank() }
    }

    val usedRepair = usedCartridges.count { it.status == "Ремонт" }
    val usedIssued = usedCartridges.count { it.status == "Выдан" }
    val usedDead = usedCartridges.count { it.status == "Умер" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "ОСТАТОК НОВЫХ КАРТРИДЖЕЙ",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = SnsSecondaryText
            )
        }
        
        if (remainingStock.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SnsCardBg)
                ) {
                    Text(
                        text = "Новые картриджи отсутствуют на складе",
                        fontSize = 12.sp,
                        color = SnsSecondaryText,
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(remainingStock.toList()) { (name, stock) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SnsCardBg),
                    border = BorderStroke(1.dp, SnsBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = name, fontWeight = FontWeight.SemiBold, color = SnsSilverText, fontSize = 15.sp)
                            Text(
                                text = if (stock <= 2) "Требуется пополнение!" else "В достатке",
                                color = if (stock <= 2) SnsOrange else SnsSecondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (stock <= 2) SnsRedAccent.copy(alpha = 0.2f) else SnsGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$stock шт",
                                fontWeight = FontWeight.Bold,
                                color = if (stock <= 2) SnsRedAccent else SnsGreen,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // BU Cartridge summary counts
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "СОСТОЯНИЕ БУ КАРТРИДЖЕЙ",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = SnsSecondaryText
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Repair Card
                StatusCounterCard(
                    title = "В ремонте",
                    count = usedRepair,
                    color = SnsOrange,
                    modifier = Modifier.weight(1f)
                )
                // Issued Card
                StatusCounterCard(
                    title = "Выдан БУ",
                    count = usedIssued,
                    color = SnsGreen,
                    modifier = Modifier.weight(1f)
                )
                // Dead Card
                StatusCounterCard(
                    title = "Списание (Умер)",
                    count = usedDead,
                    color = SnsSecondaryText,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatusCounterCard(title: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SnsCardBg),
        border = BorderStroke(1.dp, SnsBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 11.sp, color = SnsSecondaryText, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = count.toString(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
fun NewCartridgesFormTab(
    viewModel: MainViewModel,
    additions: List<NewCartridge>,
    employees: List<Employee>,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    var cartNameInput by remember { mutableStateOf("") }
    var cartQuantityInput by remember { mutableStateOf("") }
    
    // Issue forms
    var issueSelectedName by remember { mutableStateOf("") }
    var issueQuantityInput by remember { mutableStateOf("") }
    var issueToEmployee by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form: Addition
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SnsCardBg),
                border = BorderStroke(1.dp, SnsBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "ПОСТУПЛЕНИЕ НОВЫХ КАРТРИДЖЕЙ", fontWeight = FontWeight.Bold, color = SnsRedAccent, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = cartNameInput,
                        onValueChange = { cartNameInput = it },
                        label = { Text("Наименование картриджа") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_cart_name"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SnsRedAccent,
                            cursorColor = SnsRedAccent
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    OutlinedTextField(
                        value = cartQuantityInput,
                        onValueChange = { cartQuantityInput = it },
                        label = { Text("Количество (шт)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_cart_qty"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SnsRedAccent,
                            cursorColor = SnsRedAccent
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Дата зачисления: Автоматически сегодня (${viewModel.getTodayString()})", fontSize = 11.sp, color = SnsSecondaryText)
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Button(
                        onClick = {
                            val qty = cartQuantityInput.toIntOrNull()
                            if (cartNameInput.isNotBlank() && qty != null && qty > 0) {
                                requestConfirm("ИЗМЕНИТЬ", "Записать приход новых картриджей: $cartNameInput в количестве $qty шт.?") {
                                    viewModel.addNewCartridgeStock(cartNameInput, qty)
                                    cartNameInput = ""
                                    cartQuantityInput = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_new_cart")
                    ) {
                        Text("Оформить поступление")
                    }
                }
            }
        }

        // Form: Issuance
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SnsCardBg),
                border = BorderStroke(1.dp, SnsBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "ВЫДАЧА НОВОГО КАРТРИДЖА", fontWeight = FontWeight.Bold, color = SnsGreen, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = issueSelectedName,
                        onValueChange = { issueSelectedName = it },
                        label = { Text("Модель картриджа") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("issue_cart_name"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SnsGreen,
                            cursorColor = SnsGreen
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    OutlinedTextField(
                        value = issueQuantityInput,
                        onValueChange = { issueQuantityInput = it },
                        label = { Text("Количество для выдачи") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("issue_cart_qty"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SnsGreen,
                            cursorColor = SnsGreen
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    OutlinedTextField(
                        value = issueToEmployee,
                        onValueChange = { issueToEmployee = it },
                        label = { Text("Кому выдать (ФИО сотрудника)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("issue_recipient"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SnsGreen,
                            cursorColor = SnsGreen
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Button(
                        onClick = {
                            val qty = issueQuantityInput.toIntOrNull()
                            if (issueSelectedName.isNotBlank() && qty != null && qty > 0 && issueToEmployee.isNotBlank()) {
                                requestConfirm("ИЗМЕНИТЬ", "Зафиксировать выдачу нового картриджа $issueSelectedName ($qty шт) сотруднику $issueToEmployee?") {
                                    viewModel.issueNewCartridge(issueSelectedName, qty, issueToEmployee)
                                    issueSelectedName = ""
                                    issueQuantityInput = ""
                                    issueToEmployee = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_issue_cart")
                    ) {
                        Text("Зарегистрировать выдачу")
                    }
                }
            }
        }
        
        // Log additions list
        item {
            Text(text = "ИСТОРИЯ ПОПОЛНЕНИЙ", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SnsSecondaryText, fontFamily = FontFamily.Monospace)
        }
        
        if (additions.isEmpty()) {
            item {
                Text(text = "Записей о пополнениях нет", fontSize = 12.sp, color = SnsSecondaryText)
            }
        } else {
            items(additions) { add ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, SnsBorder, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = add.name, fontWeight = FontWeight.Medium, color = SnsSilverText)
                        Text(text = "Дата зачисления: ${add.dateAdded}", fontSize = 11.sp, color = SnsSecondaryText)
                    }
                    Text(text = "+${add.quantityAdded} шт", color = SnsGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun UsedCartridgesTab(
    viewModel: MainViewModel,
    usedCartridges: List<UsedCartridge>,
    employees: List<Employee>,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    var invInput by remember { mutableStateOf("") }
    var searchInput by remember { mutableStateOf("") }
    
    // Status issue dialogue
    var selectedCartridgeToIssue by remember { mutableStateOf<UsedCartridge?>(null) }
    var issueRecipientBU by remember { mutableStateOf("") }

    val filteredList = remember(usedCartridges, searchInput) {
        if (searchInput.isBlank()) usedCartridges
        else usedCartridges.filter { it.inventoryNumber.contains(searchInput, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form to add BU Cartridge
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SnsCardBg),
                border = BorderStroke(1.dp, SnsBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "ЗАНЕСЕНИЕ БУ КАРТРИДЖА", fontWeight = FontWeight.Bold, color = SnsOrange, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    OutlinedTextField(
                        value = invInput,
                        onValueChange = { invInput = it },
                        label = { Text("Инвентарный номер") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bu_inv_number"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SnsOrange,
                            cursorColor = SnsOrange
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Button(
                        onClick = {
                            if (invInput.isNotBlank()) {
                                requestConfirm("ИЗМЕНИТЬ", "Зарегистрировать БУ картридж с инвентарным номером $invInput в ремонт?") {
                                    viewModel.addUsedCartridgeForRepair(invInput)
                                    invInput = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsOrange),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_bu")
                    ) {
                        Text("Оформить БУ картридж")
                    }
                }
            }
        }

        // Search inventory
        item {
            OutlinedTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                label = { Text("Поиск БУ картриджа по инвентарнику") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SnsRedAccent,
                    cursorColor = SnsRedAccent
                )
            )
        }

        // Used Cartridges list
        if (filteredList.isEmpty()) {
            item {
                Text(text = "Картриджи БУ не найдены", color = SnsSecondaryText, modifier = Modifier.padding(vertical = 12.dp))
            }
        } else {
            items(filteredList) { cart ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SnsCardBg),
                    border = BorderStroke(1.dp, SnsBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "№ ${cart.inventoryNumber}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = SnsSilverText
                                )
                                Text(
                                    text = "Добавлен: ${cart.dateAdded} | Изменен: ${cart.dateStatusChanged}",
                                    fontSize = 11.sp,
                                    color = SnsSecondaryText
                                )
                            }
                            
                            // Status Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when (cart.status) {
                                            "Ремонт" -> SnsOrange.copy(alpha = 0.15f)
                                            "Выдан" -> SnsGreen.copy(alpha = 0.15f)
                                            else -> SnsRedAccent.copy(alpha = 0.15f)
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cart.status,
                                    color = when (cart.status) {
                                        "Ремонт" -> SnsOrange
                                        "Выдан" -> SnsGreen
                                        else -> SnsRedAccent
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (cart.status == "Выдан" && cart.issueTo != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Выдан сотруднику: ${cart.issueTo}",
                                fontSize = 12.sp,
                                color = SnsGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = SnsBorder)

                        // Action Buttons to shift statuses
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (cart.status != "Ремонт") {
                                    IconButton(
                                        onClick = {
                                            requestConfirm("ИЗМЕНИТЬ", "Вернуть БУ картридж ${cart.inventoryNumber} в статус Ремонт?") {
                                                viewModel.updateUsedCartridgeStatus(cart, "Ремонт", null)
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Build, contentDescription = "В ремонт", tint = SnsOrange)
                                    }
                                }
                                
                                if (cart.status != "Выдан") {
                                    IconButton(
                                        onClick = {
                                            selectedCartridgeToIssue = cart
                                        }
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Выдать", tint = SnsGreen)
                                    }
                                }

                                if (cart.status != "Умер") {
                                    IconButton(
                                        onClick = {
                                            requestConfirm("ИЗМЕНИТЬ", "Списать (Утилизировать) БУ картридж ${cart.inventoryNumber}?") {
                                                viewModel.updateUsedCartridgeStatus(cart, "Умер", null)
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = "Списать", tint = SnsRedAccent)
                                    }
                                }
                            }

                            // Raw Trash delete button
                            IconButton(
                                onClick = {
                                    requestConfirm("УДАЛИТЬ", "Полностью стереть запись о БУ картридже ${cart.inventoryNumber}?") {
                                        viewModel.deleteUsedCartridge(cart)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Стереть", tint = SnsSecondaryText)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal popup to designate whom the used cartridge is issued to
    selectedCartridgeToIssue?.let { cartToIssue ->
        AlertDialog(
            onDismissRequest = { selectedCartridgeToIssue = null },
            title = { Text("Кому выдать БУ картридж?", color = SnsRedAccent) },
            text = {
                Column {
                    Text("Картридж инв: ${cartToIssue.inventoryNumber}. Введите ФИО сотрудника:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = issueRecipientBU,
                        onValueChange = { issueRecipientBU = it },
                        label = { Text("ФИО Специалиста/Служащего") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (issueRecipientBU.isNotBlank()) {
                            requestConfirm("ИЗМЕНИТЬ", "Выдать БУ картридж сотруднику $issueRecipientBU?") {
                                viewModel.updateUsedCartridgeStatus(cartToIssue, "Выдан", issueRecipientBU)
                                selectedCartridgeToIssue = null
                                issueRecipientBU = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnsGreen)
                ) {
                    Text("Выдать")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedCartridgeToIssue = null }) {
                    Text("Отмена")
                }
            },
            containerColor = SnsCardBg
        )
    }
}

@Composable
fun IssuanceHistoryTab(viewModel: MainViewModel, issuances: List<CartridgeIssuance>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "ИСТОРИЯ ВЫДАЧИ КАРТРИДЖЕЙ",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = SnsSecondaryText
            )
        }
        
        if (issuances.isEmpty()) {
            item {
                Text(text = "Выдачи отсутствуют", color = SnsSecondaryText)
            }
        } else {
            items(issuances) { issue ->
                val isExpired = viewModel.isOlderThan30Days(issue.issueDate)
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isExpired) Color(0x35EF5350) else SnsCardBg
                    ),
                    border = BorderStroke(
                        1.dp, 
                        if (isExpired) SnsRedAccent.copy(alpha = 0.6f) else SnsBorder
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = issue.cartridgeName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isExpired) Color(0xFFFF8A80) else SnsSilverText
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = "Кому: ${issue.issueTo}", fontSize = 13.sp, color = SnsSilverText)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (issue.isUsed) SnsOrange.copy(alpha = 0.2f) else SnsGreen.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (issue.isUsed) "БУ" else "НОВЫЙ",
                                    color = if (issue.isUsed) SnsOrange else SnsGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        
                        HorizontalDivider(color = SnsBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 10.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Количество: ${issue.quantity} шт",
                                fontSize = 12.sp,
                                color = SnsSecondaryText
                            )
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = SnsSecondaryText,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = issue.issueDate,
                                    fontSize = 12.sp,
                                    color = SnsSecondaryText,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (isExpired) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Прошло более 30 дней с момента выдачи!",
                                    fontSize = 11.sp,
                                    color = Color(0xFFEF5350),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// ==========================================
// EQUIPMENT REGISTRY SECTION
// ==========================================
@Composable
fun EquipmentPagerScreen(
    viewModel: MainViewModel,
    requestConfirm: (String, String, () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val subfolders = listOf("Реестр техники", "Ввод оборудования")
    val pagerState = rememberPagerState(pageCount = { subfolders.size })
    val coroutineScope = rememberCoroutineScope()
    
    val sortedEquipment by viewModel.sortedEquipmentItems.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        CategorySwipeHeader(
            subfolders = subfolders,
            currentIndex = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> EquipmentListTab(viewModel, sortedEquipment, requestConfirm)
                1 -> AddEquipmentFormTab(viewModel, requestConfirm)
            }
        }
    }
}

@Composable
fun EquipmentListTab(
    viewModel: MainViewModel,
    items: List<EquipmentItem>,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val currentSort by viewModel.equipmentSortBy.collectAsStateWithLifecycle()
    val ascOrder by viewModel.equipmentSortAsc.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Print Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    printEquipmentRegistry(context, items)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_print_equipment")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Распечатать лист")
            }
        }

        // Sorting options Row
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SnsCardBg),
            border = BorderStroke(1.dp, SnsBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "Сортировка по полю:", fontSize = 12.sp, color = SnsSecondaryText, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val sorts = listOf(
                        "Имя" to MainViewModel.EquipmentSortBy.NAME,
                        "Серия" to MainViewModel.EquipmentSortBy.SERIAL,
                        "Инв" to MainViewModel.EquipmentSortBy.INVENTORY,
                        "Дата" to MainViewModel.EquipmentSortBy.DATE,
                        "Ответств" to MainViewModel.EquipmentSortBy.ASSIGNEE
                    )
                    
                    for ((label, variant) in sorts) {
                        val isSelected = currentSort == variant
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) SnsRedAccent else SnsBorder)
                                .clickable {
                                    if (currentSort == variant) {
                                        viewModel.equipmentSortAsc.value = !ascOrder
                                    } else {
                                        viewModel.equipmentSortBy.value = variant
                                        viewModel.equipmentSortAsc.value = true
                                    }
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else SnsSilverText
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = if (ascOrder) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Registry list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (items.isEmpty()) {
                item {
                    Text(text = "Реестр оборудования пуст", color = SnsSecondaryText)
                }
            } else {
                items(items) { equip ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = SnsCardBg),
                        border = BorderStroke(1.dp, SnsBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = equip.name,
                                    color = SnsSilverText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                
                                IconButton(
                                    onClick = {
                                        requestConfirm("УДАЛИТЬ", "Удалить оборудование ${equip.name} c инв: ${equip.inventoryNumber}?") {
                                            viewModel.deleteEquipment(equip)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = SnsSecondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            EquipmentDetailRow(label = "Серийный номер:", value = equip.serialNumber)
                            EquipmentDetailRow(label = "Инвентарный №:", value = equip.inventoryNumber, isBold = true)
                            EquipmentDetailRow(label = "Дата покупки:", value = equip.purchaseDate)
                            EquipmentDetailRow(label = "На ком записан:", value = equip.assignee, isSpecial = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EquipmentDetailRow(label: String, value: String, isBold: Boolean = false, isSpecial: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = SnsSecondaryText)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = if (isBold || isSpecial) FontWeight.Bold else FontWeight.Normal,
            color = if (isSpecial) SnsOrange else SnsSilverText
        )
    }
}

@Composable
fun AddEquipmentFormTab(
    viewModel: MainViewModel,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    var inv by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(viewModel.getTodayString()) }
    var assignee by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "ВВОД НОВОЙ ТЕХНИКИ", fontWeight = FontWeight.Bold, color = SnsOrange, fontSize = 14.sp)
        }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Наименование (например, Монитор Dell)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eq_name"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsOrange)
            )
        }

        item {
            OutlinedTextField(
                value = serial,
                onValueChange = { serial = it },
                label = { Text("Серийный номер (S/N)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eq_serial"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsOrange)
            )
        }

        item {
            OutlinedTextField(
                value = inv,
                onValueChange = { inv = it },
                label = { Text("Инвентарный номер") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eq_inv"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsOrange)
            )
        }

        item {
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Дата ввода / покупки") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eq_date"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsOrange)
            )
        }

        item {
            OutlinedTextField(
                value = assignee,
                onValueChange = { assignee = it },
                label = { Text("Сотрудник, за кем закрепить") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eq_assignee"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsOrange)
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (name.isNotBlank() && serial.isNotBlank() && inv.isNotBlank() && assignee.isNotBlank()) {
                        requestConfirm("ИЗМЕНИТЬ", "Записать в реестр новое оборудование $name ($inv) на сотрудника $assignee?") {
                            viewModel.addEquipment(name, serial, inv, date, assignee)
                            name = ""
                            serial = ""
                            inv = ""
                            date = viewModel.getTodayString()
                            assignee = ""
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SnsOrange),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_save_equipment")
            ) {
                Text("Закрепить и сохранить")
            }
        }
    }
}


// ==========================================
// EMPLOYEES COMPARTMENT SECTION
// ==========================================
@Composable
fun EmployeesPagerScreen(
    viewModel: MainViewModel,
    requestConfirm: (String, String, () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val subfolders = listOf("Список сотрудников", "Новый сотрудник")
    val pagerState = rememberPagerState(pageCount = { subfolders.size })
    val coroutineScope = rememberCoroutineScope()
    
    val employeesList by viewModel.employees.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        CategorySwipeHeader(
            subfolders = subfolders,
            currentIndex = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> EmployeesListTab(viewModel, employeesList, requestConfirm)
                1 -> AddEmployeeFormTab(viewModel, requestConfirm)
            }
        }
    }
}

@Composable
fun EmployeesListTab(
    viewModel: MainViewModel,
    employees: List<Employee>,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var searchInput by remember { mutableStateOf("") }

    val filtered = remember(employees, searchInput) {
        if (searchInput.isBlank()) employees
        else employees.filter { it.fullName.contains(searchInput, ignoreCase = true) || it.position.contains(searchInput, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchInput,
            onValueChange = { searchInput = it },
            label = { Text("Поиск сотрудника") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(text = "Сотрудники не найдены", color = SnsSecondaryText, modifier = Modifier.padding(16.dp))
                }
            } else {
                items(filtered) { emp ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                triggerPhoneCall(context, emp.phoneNumber)
                            },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = SnsCardBg),
                        border = BorderStroke(1.dp, SnsBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = emp.fullName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = SnsSilverText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = emp.position,
                                        color = SnsOrange,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
                                IconButton(
                                    onClick = {
                                        requestConfirm("УДАЛИТЬ", "Удалить сотрудника ${emp.fullName}?") {
                                            viewModel.deleteEmployee(emp)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = SnsSecondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = SnsBorder)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = null,
                                        tint = SnsGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = emp.phoneNumber, fontSize = 14.sp, color = SnsSilverText, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = { triggerPhoneCall(context, emp.phoneNumber) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SnsGreen),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Позвонить", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddEmployeeFormTab(
    viewModel: MainViewModel,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pos by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "РЕГИСТРАЦИЯ НОВОГО СОТРУДНИКА", fontWeight = FontWeight.Bold, color = SnsGreen, fontSize = 14.sp)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("ФИО сотрудника") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emp_name"),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
        )

        OutlinedTextField(
            value = pos,
            onValueChange = { pos = it },
            label = { Text("Должность") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emp_position"),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Номер телефона (например, +79991234567)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emp_phone"),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
        )

        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = {
                if (name.isNotBlank() && pos.isNotBlank() && phone.isNotBlank()) {
                    requestConfirm("ИЗМЕНИТЬ", "Зарегистрировать нового сотрудника $name на должность $pos?") {
                        viewModel.addEmployee(name, pos, phone)
                        name = ""
                        pos = ""
                        phone = ""
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = SnsGreen),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_save_employee")
        ) {
            Text("Сохранить сотрудника")
        }
    }
}


// ==========================================
// INSTRUCTIONS & NOTEPADS SECTION
// ==========================================
@Composable
fun InstructionsPagerScreen(
    viewModel: MainViewModel,
    requestConfirm: (String, String, () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val subfolders = listOf("Блокнот", "Новая запись")
    val pagerState = rememberPagerState(pageCount = { subfolders.size })
    val coroutineScope = rememberCoroutineScope()
    
    val instructionsList by viewModel.instructions.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        CategorySwipeHeader(
            subfolders = subfolders,
            currentIndex = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> NotebookListTab(viewModel, instructionsList, requestConfirm)
                1 -> AddNoteTab(viewModel, requestConfirm)
            }
        }
    }
}

@Composable
fun NotebookListTab(
    viewModel: MainViewModel,
    notes: List<InstructionNote>,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    var showGalleryDialog by remember { mutableStateOf(false) }
    var selectedGalleryPhotos by remember { mutableStateOf<List<PhotoAttachment>>(emptyList()) }
    var selectedGalleryIndex by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (notes.isEmpty()) {
                item {
                    Text(text = "Записей в блокноте нет", color = SnsSecondaryText)
                }
            } else {
                items(notes) { note ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = SnsCardBg),
                        border = BorderStroke(1.dp, SnsBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = note.title,
                                    color = SnsRedAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                
                                IconButton(
                                    onClick = {
                                        requestConfirm("УДАЛИТЬ", "Удалить заметку '${note.title}' с концами?") {
                                            viewModel.deleteInstruction(note)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = SnsSecondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = note.content, fontSize = 13.sp, color = SnsSilverText, lineHeight = 18.sp)

                            // Parse and render photo attachments gallery
                            val galleryPhotos = remember(note.localPhotosJson) {
                                if (note.localPhotosJson.isNullOrBlank()) emptyList()
                                else {
                                    note.localPhotosJson.split("|").mapNotNull { part ->
                                        val segments = part.split("^")
                                        if (segments.isNotEmpty()) {
                                            val path = segments[0]
                                            val cmt = if (segments.size >= 2) segments[1] else ""
                                            PhotoAttachment(path, cmt)
                                        } else null
                                    }
                                }
                            }

                            if (galleryPhotos.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = "Прикрепленные фото с комментариями (нажмите для просмотра):", fontSize = 11.sp, color = SnsSecondaryText, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(92.dp)
                                ) {
                                    itemsIndexed(galleryPhotos) { index, photo ->
                                        val bitmap = rememberLocalImagePainter(photo.filePath)
                                        Box(
                                            modifier = Modifier
                                                .size(90.dp)
                                                .background(SnsDarkBg, RoundedCornerShape(12.dp))
                                                .border(1.dp, SnsBorder, RoundedCornerShape(12.dp))
                                                .clickable {
                                                    selectedGalleryPhotos = galleryPhotos
                                                    selectedGalleryIndex = index
                                                    showGalleryDialog = true
                                                }
                                        ) {
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap,
                                                    contentDescription = photo.comment,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.BrokenImage,
                                                        contentDescription = null,
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                            
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.BottomCenter)
                                                    .background(Color.Black.copy(alpha = 0.65f))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = photo.comment.ifBlank { "Фото" },
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Render scribbled Canvas paths if present
                            if (note.imageUris.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = "Прикрепленные скетчи/схемы:", fontSize = 11.sp, color = SnsSecondaryText, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .background(SnsDarkBg, RoundedCornerShape(16.dp))
                                        .border(1.dp, SnsBorder, RoundedCornerShape(16.dp))
                                ) {
                                    StaticSketchCanvas(serializedPaths = note.imageUris)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Slideshow viewer popup
        if (showGalleryDialog && selectedGalleryPhotos.isNotEmpty()) {
            GalleryDialog(
                photos = selectedGalleryPhotos,
                initialIndex = selectedGalleryIndex,
                onDismiss = { showGalleryDialog = false }
            )
        }
    }
}

// Compact structural sketches renderer
@Composable
fun StaticSketchCanvas(serializedPaths: String) {
    val lines = remember(serializedPaths) {
        val lists = mutableListOf<List<Offset>>()
        val subParts = serializedPaths.split("|")
        for (part in subParts) {
            if (part.isBlank()) continue
            val coords = part.split(";")
            val currentLine = mutableListOf<Offset>()
            for (coord in coords) {
                val pair = coord.split(",")
                if (pair.size == 2) {
                    val x = pair[0].toFloatOrNull()
                    val y = pair[1].toFloatOrNull()
                    if (x != null && y != null) {
                        currentLine.add(Offset(x, y))
                    }
                }
            }
            if (currentLine.isNotEmpty()) {
                lists.add(currentLine)
            }
        }
        lists
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (line in lines) {
            if (line.size < 2) continue
            val path = Path().apply {
                moveTo(line[0].x, line[0].y)
                for (i in 1 until line.size) {
                    lineTo(line[i].x, line[i].y)
                }
            }
            drawPath(
                path = path,
                color = SnsSilverText,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun AddNoteTab(
    viewModel: MainViewModel,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    
    // Staged list of photos to attach to the note
    val attachedPhotos = remember { mutableStateListOf<PhotoAttachment>() }
    
    var showCommentDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var tempComment by remember { mutableStateOf("") }
    
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            pendingUri = uri
            tempComment = ""
            showCommentDialog = true
        }
    }

    // Interactive whiteboard sketching lists
    val currentLine = remember { mutableStateListOf<Offset>() }
    val accumulatedLines = remember { mutableStateListOf<List<Offset>>() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(text = "НОВАЯ ЗАПИСЬ / ИНСТРУКЦИЯ", fontWeight = FontWeight.Bold, color = SnsRedAccent, fontSize = 14.sp)
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название инструкции (Обязательно)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsRedAccent)
                )
            }

            item {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Содержание памятки") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("note_content"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsRedAccent)
                )
            }

            // Photo attachment button and thumbnails listing
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "ЛОКАЛЬНЫЕ ФОТО-ПРИЛОЖЕНИЯ:", fontSize = 11.sp, color = SnsSecondaryText, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = SnsGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("+ Добавить фото", fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    if (attachedPhotos.isEmpty()) {
                        Text(
                            text = "К этой заметке еще нет прикрепленных фотографий. Они сохранятся в базе данных приложения.",
                            fontSize = 12.sp,
                            color = SnsSecondaryText
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                        ) {
                            items(attachedPhotos) { photo ->
                                val bitmap = rememberLocalImagePainter(photo.filePath)
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .background(SnsDarkBg, RoundedCornerShape(12.dp))
                                        .border(1.dp, SnsBorder, RoundedCornerShape(12.dp))
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = photo.comment,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    
                                    // Cross button top-right to remove attachment
                                    IconButton(
                                        onClick = { attachedPhotos.remove(photo) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(22.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(bottomStart = 8.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Удалить",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = photo.comment.ifBlank { "Без комп." },
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Scribble Interactive Canvas
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "ИНТЕРАКТИВНЫЙ СХЕМАТИЧЕСКИЙ НАБРОСОК:", fontSize = 11.sp, color = SnsSecondaryText, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = {
                                currentLine.clear()
                                accumulatedLines.clear()
                            }
                        ) {
                            Text("Очистить скетч X", color = SnsRedAccent, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(SnsDarkBg, RoundedCornerShape(24.dp))
                            .border(1.dp, SnsBorder, RoundedCornerShape(24.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentLine.add(offset)
                                    },
                                    onDragEnd = {
                                        if (currentLine.isNotEmpty()) {
                                            accumulatedLines.add(currentLine.toList())
                                            currentLine.clear()
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        currentLine.add(change.position)
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Render accumulated completed segments
                            for (line in accumulatedLines) {
                                if (line.size < 2) continue
                                val p = Path().apply {
                                    moveTo(line[0].x, line[0].y)
                                    for (i in 1 until line.size) {
                                        lineTo(line[i].x, line[i].y)
                                    }
                                }
                                drawPath(p, color = SnsSilverText, style = Stroke(width = 4f, cap = StrokeCap.Round))
                            }
                            
                            // Render dynamic currently drawn segment
                            if (currentLine.size >= 2) {
                                val activePath = Path().apply {
                                    moveTo(currentLine[0].x, currentLine[0].y)
                                    for (i in 1 until currentLine.size) {
                                        lineTo(currentLine[i].x, currentLine[i].y)
                                    }
                                }
                                drawPath(activePath, color = SnsRedAccent, style = Stroke(width = 5f, cap = StrokeCap.Round))
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            // Serialize canvas paths
                            val serializedPathsText = buildString {
                                for (line in accumulatedLines) {
                                    val segmentStr = line.joinToString(";") { "${it.x},${it.y}" }
                                    append(segmentStr).append("|")
                                }
                            }
                            
                            // Serialize attached photo entities
                            val serializedPhotosText = attachedPhotos.joinToString("|") { "${it.filePath}^${it.comment}" }
                            
                            requestConfirm("ИЗМЕНИТЬ", "Добавить новую инструкцию '$title' в блокнот?") {
                                viewModel.addInstruction(title, content, serializedPathsText, serializedPhotosText)
                                title = ""
                                content = ""
                                accumulatedLines.clear()
                                currentLine.clear()
                                attachedPhotos.clear()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_save_note")
                ) {
                    Text("Записать в блокнот")
                }
            }
        }

        // Popup comment dialog for photo
        if (showCommentDialog && pendingUri != null) {
            AlertDialog(
                onDismissRequest = {
                    showCommentDialog = false
                    pendingUri = null
                },
                title = { Text("Описание фотографии", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SnsSilverText) },
                text = {
                    Column {
                        Text(
                            text = "Укажите краткое описание или комментарий к прикрепляемому изображению (оно будет доступно при просмотре):",
                            fontSize = 12.sp,
                            color = SnsSecondaryText,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = tempComment,
                            onValueChange = { tempComment = it },
                            placeholder = { Text("Например: Маркировка оптического кросса") },
                            modifier = Modifier.fillMaxWidth().testTag("photo_comment_input"),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsRedAccent)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val savedPath = saveUriToInternalStorage(context, pendingUri!!)
                            if (savedPath != null) {
                                attachedPhotos.add(PhotoAttachment(savedPath, tempComment.trim()))
                            } else {
                                android.widget.Toast.makeText(context, "Разрешение на импорт фото отсутствует или произошел сбой", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            showCommentDialog = false
                            pendingUri = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent)
                    ) {
                        Text("Прикрепить")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCommentDialog = false
                            pendingUri = null
                        }
                    ) {
                        Text("Отмена", color = SnsSilverText)
                    }
                },
                containerColor = SnsCardBg,
                textContentColor = SnsSilverText
            )
        }
    }
}


// ==========================================
// CENTRAL SAFETY CONFIRMATION DIALOG component
// ==========================================
@Composable
fun SafetyConfirmDialog(
    state: SafetyConfirmState,
    onDismiss: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val isMatched = textInput.trim() == state.keywordRequired

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (state.keywordRequired == "УДАЛИТЬ") SnsRedAccent else SnsOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.keywordRequired == "УДАЛИТЬ") "ПОДТВЕРЖДЕНИЕ УДАЛЕНИЯ" else "ПОДТВЕРЖДЕНИЕ ИЗМЕНЕНИЯ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SnsSilverText,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column {
                Text(
                    text = state.promptMessage,
                    fontSize = 14.sp,
                    color = SnsSilverText,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Text(
                    text = "Введите слово '${state.keywordRequired}' для подтверждения операции:",
                    fontSize = 11.sp,
                    color = SnsSecondaryText,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    singleLine = true,
                    placeholder = { Text(state.keywordRequired) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("safety_confirmation_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (state.keywordRequired == "УДАЛИТЬ") SnsRedAccent else SnsOrange,
                        cursorColor = if (state.keywordRequired == "УДАЛИТЬ") SnsRedAccent else SnsOrange
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isMatched) {
                        state.onConfirm()
                    }
                },
                enabled = isMatched,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.keywordRequired == "УДАЛИТЬ") SnsRedAccent else SnsOrange,
                    disabledContainerColor = SnsBorder
                ),
                modifier = Modifier.testTag("confirm_safety_btn")
            ) {
                Text("Подтвердить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = SnsSilverText)
            }
        },
        containerColor = SnsCardBg,
        textContentColor = SnsSilverText
    )
}

// Utility function to print / share selected equipment registry list
fun printEquipmentRegistry(context: Context, items: List<EquipmentItem>) {
    val report = buildString {
        append("========================================\n")
        append("      РЕЕСТР ОБОРУДОВАНИЯ - SNS         \n")
        append("========================================\n")
        append("Сформирован: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}\n")
        append("Всего позиций: ${items.size}\n\n")
        
        items.forEachIndexed { idx, item ->
            append("${idx + 1}. ${item.name}\n")
            append("   • S/N: ${item.serialNumber}\n")
            append("   • Инв. №: ${item.inventoryNumber}\n")
            append("   • Дата покупки: ${item.purchaseDate}\n")
            append("   • Ответственный: ${item.assignee}\n")
            append("----------------------------------------\n")
        }
    }
    
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, report)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Распечатать / Поделиться реестром")
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка отправки отчета", Toast.LENGTH_SHORT).show()
    }
}

// Utility function to call employees and copy phone numbers
fun triggerPhoneCall(context: Context, phoneNumber: String) {
    try {
        // Copy to clipboard
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SNS Phone Number", phoneNumber)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Номер скопирован: $phoneNumber", Toast.LENGTH_SHORT).show()
        
        // Launch Phone dialer Intent
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${phoneNumber.replace(" ", "")}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка открытия телефона", Toast.LENGTH_SHORT).show()
    }
}

// ==========================================
// PERSISTENT LOCAL PHOTO ATTACHMENT MODELS & UTILS
// ==========================================

data class PhotoAttachment(
    val filePath: String,
    val comment: String
)

@Composable
fun rememberLocalImagePainter(filePath: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(filePath) {
        try {
            val file = java.io.File(filePath)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                bitmap?.asImageBitmap()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

fun saveUriToInternalStorage(context: android.content.Context, uri: Uri): String? {
    return try {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        
        // Ensure directory is created in sandbox area
        val dir = java.io.File(context.filesDir, "instruction_photos")
        if (!dir.exists()) {
            dir.mkdir()
        }
        
        // Save replicated physical file to local app scope sandbox
        val file = java.io.File(dir, "photo_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")
        val outputStream = java.io.FileOutputStream(file)
        
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ==========================================
// INTERACTIVE SLIDESHOW GALLERY COMPONENT
// ==========================================

@Composable
fun GalleryDialog(
    photos: List<PhotoAttachment>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(initialIndex) }
    val currentPhoto = photos.getOrNull(currentIndex)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Галерея (${currentIndex + 1} из ${photos.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SnsSilverText,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть", tint = SnsSilverText)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentPhoto != null) {
                    val bitmap = rememberLocalImagePainter(currentPhoto.filePath)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(Color.Black, RoundedCornerShape(16.dp))
                            .border(1.dp, SnsBorder, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = currentPhoto.comment,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Default.BrokenImage, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Изображение не найдено", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SnsDarkBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                            Text(
                                text = "Описание / Комментарий ИТ-инженера:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SnsRedAccent,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentPhoto.comment.ifBlank { "Комментарий к фотографии отсутствует." },
                                fontSize = 13.sp,
                                color = SnsSilverText
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { currentIndex = (currentIndex - 1 + photos.size) % photos.size },
                    enabled = photos.size > 1
                ) {
                    Text("← НАЗАД", color = SnsRedAccent, fontWeight = FontWeight.Bold)
                }
                
                TextButton(
                    onClick = { currentIndex = (currentIndex + 1) % photos.size },
                    enabled = photos.size > 1
                ) {
                    Text("ВПЕРЕД →", color = SnsRedAccent, fontWeight = FontWeight.Bold)
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = SnsCardBg,
        tonalElevation = 8.dp
    )
}

// ==========================================
// APPLICATION INFO SCREEN COMPONENT
// ==========================================

fun triggerTelegram(context: android.content.Context, username: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/${username.replace("@", "")}"))
        context.startActivity(intent)
    } catch (e: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Telegram Username", username)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Никнейм Телеграм скопирован в буфер", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AboutAppScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SnsCardBg),
            border = BorderStroke(1.dp, SnsBorder)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(SnsRedAccent.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SnsRedAccent,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "О ПРИЛОЖЕНИИ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SnsSilverText,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "SNS Group IT Assistant • v2.2",
                    fontSize = 13.sp,
                    color = SnsSecondaryText
                )
                
                HorizontalDivider(color = SnsBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
                
                Text(
                    text = "Приложение создано для автоматизации ежедневных задач ИТ-специалистов компании SNS. Локальная база данных работает автономно на вашем устройстве.",
                    fontSize = 13.sp,
                    color = SnsSilverText,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                ContactCard(
                    label = "Личный контактный номер",
                    value = "+7 (928) 455-20-03",
                    icon = Icons.Default.Phone,
                    onClick = { triggerPhoneCall(context, "+7 (928) 455-20-03") }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                ContactCard(
                    label = "Рабочий контактный номер",
                    value = "+7 (962) 884-10-35",
                    icon = Icons.Default.Phone,
                    onClick = { triggerPhoneCall(context, "+7 (962) 884-10-35") }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                ContactCard(
                    label = "Телеграм для связи",
                    value = "@GodBober",
                    icon = Icons.Default.Send,
                    onClick = { triggerTelegram(context, "@GodBober") }
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_back_from_info"),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Вернуться в меню", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun ContactCard(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SnsDarkBg),
        border = BorderStroke(1.dp, SnsBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(SnsRedAccent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SnsRedAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = SnsSecondaryText,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 14.sp,
                    color = SnsSilverText,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SnsSecondaryText,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ==========================================
// TASKS & INCIDENTS PAGE SCREEN COMPONENT
// ==========================================

fun formatMillisToDate(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}

@Composable
fun TasksPagerScreen(
    viewModel: MainViewModel,
    requestConfirm: (String, String, () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val subfolders = listOf("Активные задачи", "Выполненные")
    val pagerState = rememberPagerState(pageCount = { subfolders.size })
    val coroutineScope = rememberCoroutineScope()
    
    val tasksList by viewModel.tasks.collectAsStateWithLifecycle()
    
    val activeTasks = remember(tasksList) { tasksList.filter { !it.isCompleted } }
    val completedTasks = remember(tasksList) { tasksList.filter { it.isCompleted } }
    
    var showAddTaskDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        CategorySwipeHeader(
            subfolders = subfolders,
            currentIndex = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            }
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> ActiveTasksTab(
                        viewModel = viewModel,
                        tasks = activeTasks,
                        onAddTaskClick = { showAddTaskDialog = true },
                        requestConfirm = requestConfirm
                    )
                    1 -> CompletedTasksTab(
                        viewModel = viewModel,
                        tasks = completedTasks,
                        requestConfirm = requestConfirm
                    )
                }
            }
        }
    }
    
    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, desc, days ->
                viewModel.addTask(title, desc, days)
                showAddTaskDialog = false
            }
        )
    }
}

@Composable
fun ActiveTasksTab(
    viewModel: MainViewModel,
    tasks: List<TaskItem>,
    onAddTaskClick: () -> Unit,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    var showCompleteDialog by remember { mutableStateOf<TaskItem?>(null) }
    var solutionInput by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ИТ-Задачи в работе (${tasks.size})",
                        fontWeight = FontWeight.Bold,
                        color = SnsSilverText,
                        fontSize = 15.sp
                    )
                    Button(
                        onClick = onAddTaskClick,
                        colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("+ Новая задача", fontSize = 12.sp, color = Color.White)
                    }
                }
            }

            if (tasks.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SnsCardBg.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, SnsBorder)
                    ) {
                        Text(
                            text = "Отлично! Активных задач нет. Все инциденты закрыты.",
                            modifier = Modifier.padding(16.dp),
                            color = SnsSecondaryText,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(tasks) { task ->
                    val createdDay = task.createdDateMillis / 86400000L
                    val todayDay = System.currentTimeMillis() / 86400000L
                    val deadlineDay = createdDay + task.daysNeeded
                    val isOverdue = todayDay > deadlineDay
                    
                    val cardBorderColor = if (isOverdue) Color(0xFFEF4444) else SnsBorder
                    val cardBgColor = if (isOverdue) Color(0x1AEF4444) else SnsCardBg

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = BorderStroke(1.5.dp, cardBorderColor),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = if (isOverdue) Color(0xFFEF4444) else SnsSilverText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = task.description,
                                        fontSize = 13.sp,
                                        color = SnsSilverText.copy(alpha = 0.9f)
                                    )
                                }
                                
                                if (isOverdue) {
                                    Text(
                                        text = "🚨 ПРОСРОЧЕНО",
                                        color = Color(0xFFEF4444),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier
                                            .background(Color(0x33EF4444), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = SnsBorder.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Добавлена: ${formatMillisToDate(task.createdDateMillis)}",
                                        fontSize = 11.sp,
                                        color = SnsSecondaryText
                                    )
                                    Text(
                                        text = "Срок выполнения: ${if (task.daysNeeded == 0) "Сегодня" else "${task.daysNeeded} дн."} (до ${formatMillisToDate(task.createdDateMillis + task.daysNeeded * 86400000L)})",
                                        fontSize = 11.sp,
                                        color = if (isOverdue) Color(0xFFEF4444) else SnsSecondaryText,
                                        fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = {
                                            requestConfirm("УДАЛИТЬ", "Удалить задачу '${task.title}'?") {
                                                viewModel.deleteTask(task)
                                            }
                                        }
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Удалить", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                    }
                                    
                                    Button(
                                        onClick = {
                                            solutionInput = ""
                                            showCompleteDialog = task
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SnsGreen),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Решено ✓", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCompleteDialog != null) {
            AlertDialog(
                onDismissRequest = { showCompleteDialog = null },
                title = { Text("Закрытие задачи", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SnsSilverText) },
                text = {
                    Column {
                        Text(
                            text = "Пожалуйста, опишите решение или результат выполнения этой задачи перед её закрытием:",
                            fontSize = 12.sp,
                            color = SnsSecondaryText,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = solutionInput,
                            onValueChange = { solutionInput = it },
                            placeholder = { Text("Например: Маршрутизатор прошит на RouterOS v7.14, конфиг залит, пинги идут.") },
                            modifier = Modifier.fillMaxWidth().height(100.dp).testTag("solution_input"),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.completeTask(showCompleteDialog!!, solutionInput.trim())
                            showCompleteDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsGreen)
                    ) {
                        Text("Закрыть задачу", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCompleteDialog = null }) {
                        Text("Отмена", color = SnsSilverText)
                    }
                },
                containerColor = SnsCardBg,
                textContentColor = SnsSilverText
            )
        }
    }
}

@Composable
fun CompletedTasksTab(
    viewModel: MainViewModel,
    tasks: List<TaskItem>,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Архив выполненных задач (${tasks.size})",
                    fontWeight = FontWeight.Bold,
                    color = SnsSilverText,
                    fontSize = 15.sp
                )
            }

            if (tasks.isEmpty()) {
                item {
                    Text(
                        text = "В архиве пока пусто. Закройте свои первые ИТ-задачи в активной вкладке!",
                        color = SnsSecondaryText,
                        fontSize = 13.sp
                    )
                }
            } else {
                items(tasks) { task ->
                    val createdDay = task.createdDateMillis / 86400000L
                    val completedDay = task.completedDateMillis / 86400000L
                    val targetDay = createdDay + task.daysNeeded
                    val isOverdueClosed = completedDay > targetDay
                    
                    val cardBorderColor = if (isOverdueClosed) Color(0xFFEF4444) else SnsBorder
                    val cardBgColor = if (isOverdueClosed) Color(0x0DEF4444) else SnsCardBg

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = BorderStroke(1.dp, cardBorderColor),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = SnsSilverText,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = task.description,
                                        fontSize = 12.sp,
                                        color = SnsSecondaryText
                                    )
                                }

                                if (isOverdueClosed) {
                                    Text(
                                        text = "⚠️ ПРОСРОЧЕНО ПРИ ЗАКРЫТИИ",
                                        color = Color(0xFFEF4444),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(Color(0x22EF4444), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                } else {
                                    Text(
                                        text = "✓ В СРОК",
                                        color = SnsGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(SnsGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = SnsDarkBg),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SnsBorder.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "РЕШЕНИЕ / РЕЗУЛЬТАТ:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SnsRedAccent,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = task.solution.ifBlank { "Решение не указано" },
                                        fontSize = 12.sp,
                                        color = SnsSilverText
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = SnsBorder.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Создана: ${formatMillisToDate(task.createdDateMillis)}",
                                        fontSize = 11.sp,
                                        color = SnsSecondaryText
                                    )
                                    Text(
                                        text = "Закрыта: ${formatMillisToDate(task.completedDateMillis)} (срок был ${if (task.daysNeeded == 0) "сегодня" else "${task.daysNeeded} дн."})",
                                        fontSize = 11.sp,
                                        color = if (isOverdueClosed) Color(0xFFEF4444) else SnsSecondaryText
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        requestConfirm("УДАЛИТЬ", "Безвозвратно удалить из архива выполненную задачу '${task.title}'?") {
                                            viewModel.deleteTask(task)
                                        }
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Удалить вовсе", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var daysInput by remember { mutableStateOf("1") }
    
    val isReady = title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Новая ИТ-Задача", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SnsSilverText, fontFamily = FontFamily.Monospace)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Краткое название (Обязательно)") },
                    modifier = Modifier.fillMaxWidth().testTag("add_task_title"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsRedAccent)
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Описание задачи/инцидента") },
                    modifier = Modifier.fillMaxWidth().height(80.dp).testTag("add_task_desc"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsRedAccent)
                )

                OutlinedTextField(
                    value = daysInput,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            daysInput = input
                        }
                    },
                    label = { Text("Срок на реализацию (в днях, 0 = сегодня)") },
                    placeholder = { Text("Например: 1") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("add_task_days"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsRedAccent)
                )
                
                Text(
                    text = "* Дата постановки устанавливается автоматически. Срок будет рассчитан от сегодняшнего дня.",
                    fontSize = 11.sp,
                    color = SnsSecondaryText
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = daysInput.toIntOrNull() ?: 0
                    onConfirm(title.trim(), desc.trim(), days)
                },
                enabled = isReady,
                colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent)
            ) {
                Text("Создать", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = SnsSilverText)
            }
        },
        containerColor = SnsCardBg,
        textContentColor = SnsSilverText
    )
}

