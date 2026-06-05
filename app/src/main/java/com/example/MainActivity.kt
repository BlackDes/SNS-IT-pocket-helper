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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.Popup
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.CartridgeIssuance
import com.example.data.CustomPosition
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
    ADDRESSES,
    PASSWORDS,
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
    
    // BACK KEY HANDLING FOR PREVENTING EXIT AND DRIVING USER FLOW TO HOME
    androidx.activity.compose.BackHandler {
        if (currentScreen != ScreenState.HOME) {
            currentScreen = ScreenState.HOME
        } else {
            // Prevent exit by intercepting the back action and doing nothing
        }
    }
    
    // Safety verification popup state
    var safetyConfirm by remember { mutableStateOf<SafetyConfirmState?>(null) }
    
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.toastMessage.collect { message ->
            // Show standard system toast
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    // Helper function to safely request modification / deletion confirmation
    val requestConfirmation: (String, String, () -> Unit) -> Unit = { keyword, message, action ->
        action()
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
                        ScreenState.ADDRESSES -> "АДРЕСА"
                        ScreenState.PASSWORDS -> "ПАРОЛИ"
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
                    ScreenState.ADDRESSES -> AddressesScreen(
                        viewModel = viewModel,
                        requestConfirm = requestConfirmation,
                        onBack = { currentScreen = ScreenState.HOME }
                    )
                    ScreenState.PASSWORDS -> PasswordsScreen(
                        viewModel = viewModel,
                        requestConfirm = requestConfirmation,
                        onBack = { currentScreen = ScreenState.HOME }
                    )
                    ScreenState.INFO -> AboutAppScreen(
                        viewModel = viewModel,
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
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SNS",
                        fontSize = 46.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SnsGreen,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Group of",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SnsSilverText,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = "companies",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SnsSilverText,
                            lineHeight = 18.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "для IT специалистов",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = SnsSecondaryText
                )
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(
                    color = SnsGreen,
                    thickness = 3.dp,
                    modifier = Modifier.fillMaxWidth(0.9f)
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
                    tint = SnsGreen,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
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
                color = SnsGreen,
                onClick = { onNavigate(ScreenState.CARTRIDGES) },
                testTag = "btn_cartridges"
            )
            
            HomeButton(
                title = "Реестр Оборудования",
                subtitle = "Учет техники, инвентарные номера, экспорт списков",
                icon = Icons.Default.Computer,
                color = SnsGreen,
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
                color = SnsGreen,
                onClick = { onNavigate(ScreenState.INSTRUCTIONS) },
                testTag = "btn_instructions"
            )

            HomeButton(
                title = "Задачи и Инциденты",
                subtitle = "Планирование работ, дедлайны, учет решений",
                icon = Icons.Default.CheckCircle,
                color = SnsGreen,
                onClick = { onNavigate(ScreenState.TASKS) },
                testTag = "btn_tasks"
            )

            HomeButton(
                title = "Реестр Адресов",
                subtitle = "Точки, филиалы, быстрое открытие карт",
                icon = Icons.Default.LocationOn,
                color = SnsGreen,
                onClick = { onNavigate(ScreenState.ADDRESSES) },
                testTag = "btn_addresses"
            )

            HomeButton(
                title = "База Паролей",
                subtitle = "Конфиденциальный просмотр, копирование логина и пароля",
                icon = Icons.Default.Lock,
                color = SnsGreen,
                onClick = { onNavigate(ScreenState.PASSWORDS) },
                testTag = "btn_passwords"
            )

            HomeButton(
                title = "О приложении",
                subtitle = "Контакты техподдержки, служба ИТ, разработчик",
                icon = Icons.Default.Info,
                color = Color(0xFF2196F3),
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
    val subfolders = listOf("Последняя выдача", "Общий подсчёт", "Новые картриджи", "БУ картриджи", "История выдачи", "Управление")
    val pagerState = rememberPagerState(pageCount = { subfolders.size })
    val coroutineScope = rememberCoroutineScope()
    
    val newCartridges by viewModel.newCartridges.collectAsStateWithLifecycle()
    val issuances by viewModel.cartridgeIssuances.collectAsStateWithLifecycle()
    val usedCartridges by viewModel.usedCartridges.collectAsStateWithLifecycle()
    val employeesList by viewModel.employees.collectAsStateWithLifecycle()
    val cartridgeTypes by viewModel.cartridgeTypes.collectAsStateWithLifecycle()

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
                0 -> LatestIssuanceTab(issuances, employeesList, usedCartridges)
                1 -> GeneralCountTab(newCartridges, issuances, usedCartridges, cartridgeTypes)
                2 -> NewCartridgesFormTab(viewModel, newCartridges, employeesList, cartridgeTypes, requestConfirm)
                3 -> UsedCartridgesTab(viewModel, usedCartridges, employeesList, requestConfirm)
                4 -> IssuanceHistoryTab(viewModel, issuances)
                5 -> ManageCartridgesTab(viewModel, newCartridges, cartridgeTypes, requestConfirm)
            }
        }
    }
}

@Composable
fun LatestIssuanceTab(
    issuances: List<CartridgeIssuance>,
    employees: List<com.example.data.Employee>,
    usedCartridges: List<com.example.data.UsedCartridge>
) {
    val employeeToShortPos = remember(employees) {
        employees.associate { it.fullName to getShortPosition(it.position) }
    }
    
    val tableRows = remember(employees, issuances) {
        val list = mutableListOf<String>()
        
        // Find routes from current employees
        val routes = mutableSetOf<String>()
        employees.forEach { emp ->
            val shortPos = getShortPosition(emp.position)
            if (shortPos == "ТП" || shortPos == "ЛТП" || shortPos == "УДФ ТП" || shortPos == "СТП") {
                val parts = emp.fullName.split(" ")
                val vPart = parts.find { it.startsWith("V") }
                if (vPart != null) {
                    routes.add(vPart)
                }
            }
        }
        
        // Let's also keep routes from historical issuances ONLY if they match existing employees 
        // who might have historical registrations or simply always base it on active employees.
        // We will sorted-ly add all employee route numbers.
        routes.toList().sorted().forEach { route ->
            list.add("ТП $route")
        }
        list
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SnsCardBg),
                border = BorderStroke(1.dp, SnsBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ПОСЛЕДНЯЯ ВЫДАЧА",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = SnsRedAccent,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Headers Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SnsBorder.copy(alpha = 0.3f))
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "ТП", color = SnsSecondaryText, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
                        Text(text = "Новый", color = SnsSecondaryText, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
                        Text(text = "БУ", color = SnsSecondaryText, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                    }
                    
                    HorizontalDivider(color = SnsBorder)
                    
                    if (tableRows.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("Нет заведённых ТП в списке сотрудников", color = SnsSecondaryText, fontSize = 13.sp)
                        }
                    } else {
                        tableRows.forEach { rowLabel ->
                            // Filter issuances belonging to respective recipient roles/labels
                            val posIssuances = issuances.filter { 
                                val shortP = getShortPositionByRecipient(it.issueTo, employeeToShortPos)
                                val cleanShort = if (shortP.contains("ТП")) "ТП" else shortP
                                
                                if (rowLabel == "СВ") {
                                    cleanShort == "СВ" || it.issueTo.contains("[СВ]")
                                } else if (rowLabel.startsWith("ТП V")) {
                                    val routeCode = rowLabel.substringAfter("ТП ").trim() // e.g., "V101"
                                    it.issueTo.contains(routeCode)
                                } else {
                                    // General fallback row label is "ТП"
                                    cleanShort == "ТП" || cleanShort == "ЛТП" || cleanShort == "СТП" || cleanShort == "УДФ ТП" ||
                                    it.issueTo.contains("[ТП]") || it.issueTo.contains("[ЛТП]") || it.issueTo.contains("[СТП]") || it.issueTo.contains("[УДФ ТП]")
                                }
                            }
                            
                            // Latest New Cartridge Issuance
                            val latestNew = posIssuances.filter { !it.isUsed }.maxByOrNull { it.id }
                            // Latest BU Cartridge Issuance
                            val latestUsed = posIssuances.filter { it.isUsed }.maxByOrNull { it.id }

                            // Check if BU cartridge is written off (status == "Умер")
                            val isWrittenOff = if (latestUsed != null) {
                                val inv = latestUsed.cartridgeName.substringAfter("Инв: ", "").substringBefore(")", "")
                                if (inv.isNotBlank()) {
                                    usedCartridges.any { it.inventoryNumber == inv && it.status == "Умер" }
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = rowLabel, 
                                    color = SnsSilverText, 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 14.sp, 
                                    modifier = Modifier.weight(1.2f)
                                )
                                
                                Text(
                                    text = latestNew?.issueDate ?: "—", 
                                    color = if (latestNew != null) SnsGreen else SnsSecondaryText, 
                                    fontWeight = if (latestNew != null) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp, 
                                    modifier = Modifier.weight(1.2f),
                                    textAlign = TextAlign.Center
                                )
                                
                                Text(
                                    text = latestUsed?.issueDate ?: "—", 
                                    color = if (latestUsed != null) {
                                        if (isWrittenOff) Color(0xFFEF4444) else SnsOrange
                                    } else SnsSecondaryText, 
                                    fontWeight = if (latestUsed != null) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp, 
                                    modifier = Modifier.weight(1.2f),
                                    textAlign = TextAlign.End
                                )
                            }
                            HorizontalDivider(color = SnsBorder.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GeneralCountTab(
    newCartridges: List<NewCartridge>,
    issuances: List<CartridgeIssuance>,
    usedCartridges: List<UsedCartridge>,
    cartridgeTypes: List<com.example.data.CartridgeType>
) {
    // Reactive Stock Calculation
    val remainingStock = remember(newCartridges, issuances, cartridgeTypes) {
        val stockMap = mutableMapOf<String, Int>()
        // Initialize all registered kinds
        cartridgeTypes.forEach { type ->
            stockMap[type.name] = 0
        }
        // Add additions
        newCartridges.forEach {
            stockMap[it.name] = (stockMap[it.name] ?: 0) + it.quantityAdded
        }
        // Subtract issuances
        issuances.filter { !it.isUsed }.forEach {
            stockMap[it.cartridgeName] = (stockMap[it.cartridgeName] ?: 0) - it.quantity
        }
        stockMap.filter { it.key.isNotBlank() }
    }

    val usedRepair = usedCartridges.count { it.status == "Ремонт" }
    val usedOk = usedCartridges.count { it.status == "Исправен" }
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
                val isCritical = stock <= 3
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCritical) Color(0xFFEF4444).copy(alpha = 0.05f) else SnsCardBg
                    ),
                    border = BorderStroke(1.5.dp, if (isCritical) Color(0xFFEF4444) else SnsBorder)
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
                                text = if (isCritical) "Критический остаток!" else "В достатке",
                                color = if (isCritical) Color(0xFFEF4444) else SnsSecondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCritical) Color(0xFFEF4444).copy(alpha = 0.15f) else SnsGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$stock шт",
                                fontWeight = FontWeight.Bold,
                                color = if (isCritical) Color(0xFFEF4444) else SnsGreen,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Repair Card
                StatusCounterCard(
                    title = "В ремонте",
                    count = usedRepair,
                    color = SnsOrange,
                    modifier = Modifier.weight(1f)
                )
                // Repaired Card
                StatusCounterCard(
                    title = "Исправен",
                    count = usedOk,
                    color = Color(0xFF3B82F6),
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
                    title = "Списание",
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
    cartridgeTypes: List<com.example.data.CartridgeType>,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var cartNameInput by remember { mutableStateOf("") }
    var cartQuantityInput by remember { mutableStateOf("") }
    var cartDateInput by remember { mutableStateOf("") }

    val editingCartridgeType by remember { mutableStateOf<com.example.data.CartridgeType?>(null) }
    val editTypeNameInput by remember { mutableStateOf("") }
    
    // Issue forms
    var issueSelectedName by remember { mutableStateOf("") }
    var issueQuantityInput by remember { mutableStateOf("") }
    var issueToEmployee by remember { mutableStateOf("") }
    var issueDateInput by remember { mutableStateOf("") }

    // Dropdowns and Dialogs
    var expandedTypeMenuAdd by remember { mutableStateOf(false) }
    var expandedTypeMenuIssue by remember { mutableStateOf(false) }
    var showAddTypeDialog by remember { mutableStateOf(false) }
    var newTypeNameInput by remember { mutableStateOf("") }

    // Recipient Validation for SV / TP (including summer, remote, shift subtypes)
    val eligibleEmployees = remember(employees) {
        employees.filter { isEligibleForCartridge(it.position) }
    }
    val matchedEmployee = remember(issueToEmployee, eligibleEmployees) {
        matchEmployeeByNameOrFormatted(issueToEmployee, eligibleEmployees)
    }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "ПОСТУПЛЕНИЕ НОВЫХ КАРТРИДЖЕЙ", fontWeight = FontWeight.Bold, color = SnsRedAccent, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Selector OutlinedTextField for ADD name
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedTypeMenuAdd = true }
                    ) {
                        OutlinedTextField(
                            value = cartNameInput,
                            onValueChange = {},
                            enabled = false,
                            label = { Text("Выберите вид картриджа") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_cart_name"),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = SnsSilverText,
                                disabledBorderColor = SnsBorder,
                                disabledLabelColor = SnsSecondaryText,
                                disabledTrailingIconColor = SnsSecondaryText,
                                focusedBorderColor = SnsRedAccent,
                                cursorColor = SnsRedAccent
                            ),
                            trailingIcon = {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Картриджи", tint = SnsSecondaryText)
                            }
                        )

                        androidx.compose.material3.DropdownMenu(
                            expanded = expandedTypeMenuAdd,
                            onDismissRequest = { expandedTypeMenuAdd = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(SnsCardBg)
                        ) {
                            if (cartridgeTypes.isNotEmpty()) {
                                cartridgeTypes.forEach { type ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(type.name, color = SnsSilverText, fontSize = 14.sp) },
                                        onClick = {
                                            cartNameInput = type.name
                                            expandedTypeMenuAdd = false
                                        }
                                    )
                                }
                                androidx.compose.material3.HorizontalDivider(color = SnsBorder.copy(alpha = 0.5f))
                            }
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("+ Добавить вид картриджа", color = SnsRedAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                                onClick = {
                                    showAddTypeDialog = true
                                    expandedTypeMenuAdd = false
                                }
                            )
                        }
                    }
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
                    OutlinedTextField(
                        value = cartDateInput,
                        onValueChange = { cartDateInput = it },
                        label = { Text("Дата поступления вручную (ДД.ММ.ГГГГ)") },
                        placeholder = { Text("Пусто: сегодня (${viewModel.getTodayString()})") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                showDatePicker(context, System.currentTimeMillis(), null) { selectedMillis ->
                                    cartDateInput = formatMillisToDate(selectedMillis)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Выбрать дату",
                                    tint = SnsRedAccent
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SnsRedAccent,
                            cursorColor = SnsRedAccent
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Button(
                        onClick = {
                            val qty = cartQuantityInput.toIntOrNull()
                            if (cartNameInput.isNotBlank() && qty != null && qty > 0) {
                                requestConfirm("ИЗМЕНИТЬ", "Записать приход новых картриджей: $cartNameInput в количестве $qty шт.?") {
                                    viewModel.addNewCartridgeStock(cartNameInput, qty, cartDateInput.trim())
                                    cartNameInput = ""
                                    cartQuantityInput = ""
                                    cartDateInput = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_new_cart"),
                        enabled = cartNameInput.isNotBlank() && cartQuantityInput.isNotBlank()
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "ВЫДАЧА НОВОГО КАРТРИДЖА", fontWeight = FontWeight.Bold, color = SnsGreen, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Selector OutlinedTextField for ISSUE name
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedTypeMenuIssue = true }
                    ) {
                        OutlinedTextField(
                            value = issueSelectedName,
                            onValueChange = {},
                            enabled = false,
                            label = { Text("Выберите модель картриджа") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("issue_cart_name"),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = SnsSilverText,
                                disabledBorderColor = SnsBorder,
                                disabledLabelColor = SnsSecondaryText,
                                disabledTrailingIconColor = SnsSecondaryText,
                                focusedBorderColor = SnsGreen,
                                cursorColor = SnsGreen
                            ),
                            trailingIcon = {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Модели", tint = SnsSecondaryText)
                            }
                        )

                        androidx.compose.material3.DropdownMenu(
                            expanded = expandedTypeMenuIssue,
                            onDismissRequest = { expandedTypeMenuIssue = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(SnsCardBg)
                        ) {
                            if (cartridgeTypes.isNotEmpty()) {
                                cartridgeTypes.forEach { type ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(type.name, color = SnsSilverText, fontSize = 14.sp) },
                                        onClick = {
                                            issueSelectedName = type.name
                                            expandedTypeMenuIssue = false
                                        }
                                    )
                                }
                                androidx.compose.material3.HorizontalDivider(color = SnsBorder.copy(alpha = 0.5f))
                            }
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("+ Добавить вид картриджа", color = SnsGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                                onClick = {
                                    showAddTypeDialog = true
                                    expandedTypeMenuIssue = false
                                }
                            )
                        }
                    }
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
                    
                    EmployeeAutocompleteTextField(
                        value = issueToEmployee,
                        onValueChange = { issueToEmployee = it },
                        label = "Кому выдать (ФИО сотрудника)",
                        employees = employees,
                        restrictToCartridges = true
                    )

                    // Error text warning if the recipient is not an eligible SV / TP
                    if (issueToEmployee.isNotBlank() && matchedEmployee == null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Выдача разрешена ТОЛЬКО с должностью СВ или ТП!",
                            color = SnsRedAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    OutlinedTextField(
                        value = issueDateInput,
                        onValueChange = { issueDateInput = it },
                        label = { Text("Дата выдачи вручную (ДД.ММ.ГГГГ)") },
                        placeholder = { Text("Пусто: сегодня (${viewModel.getTodayString()})") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                showDatePicker(context, System.currentTimeMillis(), null) { selectedMillis ->
                                    issueDateInput = formatMillisToDate(selectedMillis)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Выбрать дату",
                                    tint = SnsGreen
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SnsGreen,
                            cursorColor = SnsGreen
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Button(
                        onClick = {
                            val qty = issueQuantityInput.toIntOrNull()
                            if (issueSelectedName.isNotBlank() && qty != null && qty > 0 && matchedEmployee != null) {
                                requestConfirm("ИЗМЕНИТЬ", "Зафиксировать выдачу нового картриджа $issueSelectedName ($qty шт) сотруднику $issueToEmployee?") {
                                    viewModel.issueNewCartridge(issueSelectedName, qty, issueToEmployee, issueDateInput.trim())
                                    issueSelectedName = ""
                                    issueQuantityInput = ""
                                    issueToEmployee = ""
                                    issueDateInput = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_issue_cart"),
                        enabled = issueSelectedName.isNotBlank() && issueQuantityInput.isNotBlank() && matchedEmployee != null
                    ) {
                        Text("Зарегистрировать выдачу")
                    }
                }
            }
        }

        // Section C: Log additions list (История пополнений)
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "ИСТОРИЯ ПОПОЛНЕНИЙ", 
                fontWeight = FontWeight.Bold, 
                fontSize = 13.sp, 
                color = SnsSecondaryText, 
                fontFamily = FontFamily.Monospace
            )
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
fun ManageCartridgesTab(
    viewModel: MainViewModel,
    additions: List<NewCartridge>,
    cartridgeTypes: List<com.example.data.CartridgeType>,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    var newTypeNameInput by remember { mutableStateOf("") }
    var editingCartridgeType by remember { mutableStateOf<com.example.data.CartridgeType?>(null) }
    var editTypeNameInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section A: Add new cartridge type
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SnsCardBg),
                border = BorderStroke(1.dp, SnsBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ДОБАВИТЬ НОВЫЙ ВИД КАРТРИДЖА",
                        fontWeight = FontWeight.Bold,
                        color = SnsGreen,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newTypeNameInput,
                        onValueChange = { newTypeNameInput = it },
                        label = { Text("Наименование вида (например: Canon 725)") },
                        modifier = Modifier.fillMaxWidth().testTag("add_cart_type_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SnsGreen,
                            focusedLabelColor = SnsGreen,
                            cursorColor = SnsGreen
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (newTypeNameInput.isNotBlank()) {
                                viewModel.addCartridgeType(newTypeNameInput.trim())
                                newTypeNameInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsGreen),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = newTypeNameInput.isNotBlank()
                    ) {
                        Text("Зарегистрировать вид")
                    }
                }
            }
        }

        // Section B: Registered Cartridge Types list with Delete/Edit option
        item {
            Text(
                text = "ЗАРЕГИСТРИРОВАННЫЕ ВИДЫ КАРТРИДЖЕЙ",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = SnsSecondaryText,
                fontFamily = FontFamily.Monospace
            )
        }
        
        if (cartridgeTypes.isEmpty()) {
            item {
                Text(text = "Список зарегистрированных видов пуст", fontSize = 12.sp, color = SnsSecondaryText)
            }
        } else {
            items(cartridgeTypes) { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, SnsBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = type.name, 
                        fontWeight = FontWeight.SemiBold, 
                        color = SnsSilverText, 
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                editingCartridgeType = type
                                editTypeNameInput = type.name
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Редактировать вид картриджа",
                                tint = SnsSecondaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                requestConfirm("УДАЛИТЬ", "Вы действительно хотите удалить вид картриджа '${type.name}'?") {
                                    viewModel.deleteCartridgeType(type)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить вид картриджа",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (editingCartridgeType != null) {
        AlertDialog(
            onDismissRequest = { editingCartridgeType = null },
            title = { Text("Редактировать вид картриджа", color = SnsSilverText, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editTypeNameInput,
                    onValueChange = { editTypeNameInput = it },
                    label = { Text("Наименование вида") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val t = editingCartridgeType
                        if (t != null && editTypeNameInput.isNotBlank()) {
                            viewModel.updateCartridgeType(t.copy(name = editTypeNameInput.trim()))
                            editingCartridgeType = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnsGreen)
                ) {
                    Text("Сохранить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCartridgeType = null }) {
                    Text("Отмена", color = SnsSilverText)
                }
            },
            containerColor = SnsCardBg,
            textContentColor = SnsSilverText
        )
    }
}

@Composable
fun UsedCartridgesTab(
    viewModel: MainViewModel,
    usedCartridges: List<UsedCartridge>,
    employees: List<Employee>,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var invInput by remember { mutableStateOf("") }
    var searchInput by remember { mutableStateOf("") }
    var usedDateInput by remember { mutableStateOf("") }
    
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
                    Text(text = "ЗАНЕСЕНИЕ БУ КАРТРИДЖА", fontWeight = FontWeight.Bold, color = SnsRedAccent, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    OutlinedTextField(
                        value = invInput,
                        onValueChange = { invInput = it },
                        label = { Text("Инвентарный номер") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bu_inv_number"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SnsRedAccent,
                            cursorColor = SnsRedAccent
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = usedDateInput,
                        onValueChange = { usedDateInput = it },
                        label = { Text("Дата регистрации вручную (ДД.ММ.ГГГГ)") },
                        placeholder = { Text("Пусто: сегодня (${viewModel.getTodayString()})") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                showDatePicker(context, System.currentTimeMillis(), null) { selectedMillis ->
                                    usedDateInput = formatMillisToDate(selectedMillis)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Выбрать дату",
                                    tint = SnsRedAccent
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SnsRedAccent,
                            cursorColor = SnsRedAccent
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Button(
                        onClick = {
                            if (invInput.isNotBlank()) {
                                requestConfirm("ИЗМЕНИТЬ", "Зарегистрировать БУ картридж с инвентарным номером $invInput в ремонт?") {
                                    viewModel.addUsedCartridgeForRepair(invInput, usedDateInput.trim())
                                    invInput = ""
                                    usedDateInput = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent),
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
                                            "Исправен" -> Color(0xFF3B82F6).copy(alpha = 0.15f)
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
                                        "Исправен" -> Color(0xFF3B82F6)
                                        "Выдан" -> SnsGreen
                                        else -> SnsRedAccent
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (cart.issueTo != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Выдан сотруднику: ${cart.issueTo}",
                                fontSize = 12.sp,
                                color = if (cart.status == "Выдан") SnsGreen else SnsSecondaryText,
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

                                if (cart.status != "Исправен") {
                                    IconButton(
                                        onClick = {
                                            requestConfirm("ИЗМЕНИТЬ", "Перевести БУ картридж ${cart.inventoryNumber} в статус Исправен?") {
                                                viewModel.updateUsedCartridgeStatus(cart, "Исправен", null)
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Исправен", tint = Color(0xFF3B82F6))
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
                                                viewModel.updateUsedCartridgeStatus(cart, "Умер", cart.issueTo)
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
        val eligibleEmployees = remember(employees) {
            employees.filter { isEligibleForCartridge(it.position) }
        }
        val matchedEmployee = remember(issueRecipientBU, eligibleEmployees) {
            matchEmployeeByNameOrFormatted(issueRecipientBU, eligibleEmployees)
        }

        AlertDialog(
            onDismissRequest = { selectedCartridgeToIssue = null },
            title = { Text("Кому выдать БУ картридж?", color = SnsRedAccent) },
            text = {
                Column {
                    Text("Картридж инв: ${cartToIssue.inventoryNumber}. Введите ФИО сотрудника:")
                    Spacer(modifier = Modifier.height(12.dp))
                    EmployeeAutocompleteTextField(
                        value = issueRecipientBU,
                        onValueChange = { issueRecipientBU = it },
                        label = "ФИО Специалиста/Служащего",
                        employees = employees,
                        restrictToCartridges = true
                    )

                    if (issueRecipientBU.isNotBlank() && matchedEmployee == null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Выдача разрешена ТОЛЬКО с должностью СВ или ТП!",
                            color = SnsRedAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (issueRecipientBU.isNotBlank() && matchedEmployee != null) {
                            requestConfirm("ИЗМЕНИТЬ", "Выдать БУ картридж сотруднику $issueRecipientBU?") {
                                viewModel.updateUsedCartridgeStatus(cartToIssue, "Выдан", issueRecipientBU)
                                selectedCartridgeToIssue = null
                                issueRecipientBU = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnsGreen),
                    enabled = issueRecipientBU.isNotBlank() && matchedEmployee != null
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
    var query by remember { mutableStateOf("") }
    var editingIssuance by remember { mutableStateOf<CartridgeIssuance?>(null) }
    
    val filtered = remember(issuances, query) {
        if (query.isBlank()) {
            issuances
        } else {
            issuances.filter {
                it.cartridgeName.contains(query, ignoreCase = true) ||
                it.issueTo.contains(query, ignoreCase = true)
            }
        }
    }

    if (editingIssuance != null) {
        val issueItem = editingIssuance!!
        val context = LocalContext.current
        var editCartridgeName by remember { mutableStateOf(issueItem.cartridgeName) }
        var editIssueTo by remember { mutableStateOf(issueItem.issueTo) }
        var editQuantityStr by remember { mutableStateOf(issueItem.quantity.toString()) }
        var editIsUsed by remember { mutableStateOf(issueItem.isUsed) }
        var editDate by remember { mutableStateOf(issueItem.issueDate) }
        
        AlertDialog(
            onDismissRequest = { editingIssuance = null },
            title = { Text("Редактировать выдачу", color = SnsSilverText, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editCartridgeName,
                        onValueChange = { editCartridgeName = it },
                        label = { Text("Модель картриджа") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen, focusedLabelColor = SnsGreen, cursorColor = SnsGreen)
                    )
                    
                    OutlinedTextField(
                        value = editIssueTo,
                        onValueChange = { editIssueTo = it },
                        label = { Text("Кому выдано (ФИО)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen, focusedLabelColor = SnsGreen, cursorColor = SnsGreen)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(), 
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editQuantityStr,
                            onValueChange = { editQuantityStr = it },
                            label = { Text("Кол-во") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen, focusedLabelColor = SnsGreen, cursorColor = SnsGreen)
                        )
                        OutlinedTextField(
                            value = editDate,
                            onValueChange = { editDate = it },
                            label = { Text("Дата") },
                            modifier = Modifier.weight(1.5f),
                            trailingIcon = {
                                IconButton(onClick = {
                                    showDatePicker(context, System.currentTimeMillis(), null) { selectedMillis ->
                                        editDate = formatMillisToDate(selectedMillis)
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Выбрать дату",
                                        tint = SnsGreen
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen, focusedLabelColor = SnsGreen, cursorColor = SnsGreen)
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Checkbox(
                            checked = editIsUsed,
                            onCheckedChange = { editIsUsed = it },
                            colors = CheckboxDefaults.colors(checkedColor = SnsOrange)
                        )
                        Text("БУ картридж", color = SnsSilverText, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = editQuantityStr.toIntOrNull() ?: 1
                        val updated = issueItem.copy(
                            cartridgeName = editCartridgeName,
                            issueTo = editIssueTo,
                            quantity = qty,
                            isUsed = editIsUsed,
                            issueDate = editDate
                        )
                        viewModel.updateCartridgeIssuance(updated)
                        editingIssuance = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnsGreen)
                ) {
                    Text("Сохранить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingIssuance = null }) {
                    Text("Отмена", color = SnsSilverText)
                }
            },
            containerColor = SnsCardBg
        )
    }

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

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск по выданным картриджам / ФИО") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_issuances"),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
            )
        }
        
        if (filtered.isEmpty()) {
            item {
                Text(text = "Выдачи не найдены", color = SnsSecondaryText, modifier = Modifier.padding(16.dp))
            }
        } else {
            items(filtered) { issue ->
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
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { editingIssuance = issue },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Изменить",
                                        tint = SnsGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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
                0 -> EquipmentListTab(viewModel, sortedEquipment, requestConfirm)
                1 -> AddEquipmentFormTab(viewModel, employeesList, requestConfirm)
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

    var searchQuery by remember { mutableStateOf("") }
    var filterAssignee by remember { mutableStateOf("Все лица") }
    var showFilters by remember { mutableStateOf(false) }

    val allUniqueAssignees = remember(items) {
        listOf("Все лица") + items.map { it.assignee }.distinct().filter { it.isNotBlank() }
    }

    val filteredItems = remember(items, searchQuery, filterAssignee) {
        items.filter { item ->
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                item.name.contains(searchQuery, ignoreCase = true) ||
                        item.serialNumber.contains(searchQuery, ignoreCase = true) ||
                        item.inventoryNumber.contains(searchQuery, ignoreCase = true)
            }
            val matchesAssignee = if (filterAssignee == "Все лица") {
                true
            } else {
                item.assignee.equals(filterAssignee, ignoreCase = true)
            }
            matchesSearch && matchesAssignee
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filter Toggle Button
            Button(
                onClick = { showFilters = !showFilters },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showFilters) SnsRedAccent else SnsCardBg
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SnsBorder),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (showFilters) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = null,
                    tint = if (showFilters) Color.White else SnsSilverText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (showFilters) "Скрыть фильтры" else "Открыть фильтры",
                    color = if (showFilters) Color.White else SnsSilverText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Print Button
            Button(
                onClick = {
                    val pdfFile = generateEquipmentPdf(context, filteredItems)
                    if (pdfFile != null) {
                        printEquipmentRegistryPdf(context, pdfFile)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Печать таблицы", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Action panel to Clear Filters when they are active
        if (searchQuery.isNotEmpty() || filterAssignee != "Все лица") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        searchQuery = ""
                        filterAssignee = "Все лица"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Очистить",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Сбросить фильтры", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showFilters) {
            // Filters Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SnsCardBg),
                border = BorderStroke(1.dp, SnsBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Выборка / Фильтры реестра:", fontSize = 12.sp, color = SnsSecondaryText, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                Spacer(modifier = Modifier.height(8.dp))
                
                // Search field (Filters "only by equipment" name/serial/inventory)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск техники по названию / номеру...") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsRedAccent),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Поиск", tint = SnsSecondaryText) }
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Assignee picker (Filters "by single material person")
                var pickerExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth().clickable { pickerExpanded = true }) {
                    OutlinedTextField(
                        value = filterAssignee,
                        onValueChange = {},
                        enabled = false,
                        label = { Text("Материально ответственное лицо") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = SnsSilverText,
                            disabledBorderColor = SnsBorder,
                            disabledLabelColor = SnsSecondaryText,
                            disabledTrailingIconColor = SnsSecondaryText
                        ),
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SnsSecondaryText) }
                    )
                    
                    DropdownMenu(
                        expanded = pickerExpanded,
                        onDismissRequest = { pickerExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f).background(SnsCardBg)
                    ) {
                        allUniqueAssignees.forEach { person ->
                            DropdownMenuItem(
                                text = { Text(person, color = SnsSilverText, fontSize = 14.sp) },
                                onClick = {
                                    filterAssignee = person
                                    pickerExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Sorting options Card
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
    }

        // Registry list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredItems.isEmpty()) {
                item {
                    Text(text = "Реестр оборудования пуст или нет совпадений", color = SnsSecondaryText)
                }
            } else {
                items(filteredItems) { equip ->
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
                            if (equip.comment.isNotBlank()) {
                                EquipmentDetailRow(label = "Комментарий:", value = equip.comment)
                            }
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
            color = if (isSpecial) SnsGreen else SnsSilverText
        )
    }
}

@Composable
fun AddEquipmentFormTab(
    viewModel: MainViewModel,
    employees: List<Employee>,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    var inv by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var assignee by remember { mutableStateOf("") }
    var purchasesDateInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "ВВОД НОВОЙ ТЕХНИКИ", fontWeight = FontWeight.Bold, color = SnsRedAccent, fontSize = 14.sp)
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
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Комментарий (необязательно)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eq_comment"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsOrange)
            )
        }

        item {
            EmployeeAutocompleteTextField(
                value = assignee,
                onValueChange = { assignee = it },
                label = "Сотрудник, за кем закрепить",
                employees = employees,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eq_assignee"),
                restrictToCartridges = false,
                focusedBorderColor = SnsOrange
            )
        }

        item {
            OutlinedTextField(
                value = purchasesDateInput,
                onValueChange = { purchasesDateInput = it },
                label = { Text("Дата покупки вручную (ДД.ММ.ГГГГ)") },
                placeholder = { Text("Пусто: сегодня (${viewModel.getTodayString()})") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        showDatePicker(context, System.currentTimeMillis(), null) { selectedMillis ->
                            purchasesDateInput = formatMillisToDate(selectedMillis)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Выбрать дату",
                            tint = SnsOrange
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsOrange)
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (name.isNotBlank() && serial.isNotBlank() && inv.isNotBlank() && assignee.isNotBlank()) {
                        requestConfirm("ИЗМЕНИТЬ", "Записать в реестр новое оборудование $name ($inv) на сотрудника $assignee?") {
                            val finalDate = if (purchasesDateInput.isBlank()) viewModel.getTodayString() else purchasesDateInput.trim()
                            viewModel.addEquipment(name, serial, inv, finalDate, assignee, comment)
                            name = ""
                            serial = ""
                            inv = ""
                            comment = ""
                            assignee = ""
                            purchasesDateInput = ""
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
    val subfolders = listOf("Список сотрудников", "Новый сотрудник", "Должности")
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
                2 -> PositionsTab(viewModel, requestConfirm)
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
    var editingEmployee by remember { mutableStateOf<Employee?>(null) }
    var sortByPosition by remember { mutableStateOf(false) }
    var expandedEmployeeId by remember { mutableStateOf<Int?>(null) }

    val filtered = remember(employees, searchInput) {
        if (searchInput.isBlank()) employees
        else employees.filter { it.fullName.contains(searchInput, ignoreCase = true) || it.position.contains(searchInput, ignoreCase = true) }
    }

    val sortedFiltered = remember(filtered, sortByPosition) {
        if (sortByPosition) {
            filtered.sortedBy { it.position }
        } else {
            filtered.sortedBy { it.fullName }
        }
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

        // Sorting Toggle Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Сортировка:", color = SnsSecondaryText, fontSize = 12.sp)
            
            @Composable
            fun Chip(label: String, isSelected: Boolean, onClick: () -> Unit) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) SnsGreen.copy(alpha = 0.25f) else SnsBorder.copy(alpha = 0.2f))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = label, 
                        color = if (isSelected) SnsGreen else SnsSilverText, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 11.sp
                    )
                }
            }
            
            Chip("По ФИО", !sortByPosition) { sortByPosition = false }
            Chip("По должности", sortByPosition) { sortByPosition = true }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sortedFiltered.isEmpty()) {
                item {
                    Text(text = "Сотрудники не найдены", color = SnsSecondaryText, modifier = Modifier.padding(16.dp))
                }
            } else {
                items(sortedFiltered) { emp ->
                    val isExpanded = expandedEmployeeId == emp.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedEmployeeId = if (isExpanded) null else emp.id
                            },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = SnsCardBg),
                        border = BorderStroke(1.dp, SnsBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Top Row: Short Position and Name
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SnsGreen.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = getShortPosition(emp.position),
                                            color = SnsGreen,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = emp.fullName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = SnsSilverText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                                    tint = SnsSecondaryText
                                )
                            }

                            // Full position description and rest of details are visible only when expanded
                            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = getFullPositionName(emp.position),
                                        color = SnsSilverText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 2.dp)
                                    )

                                    // Quick Messenger buttons for social link
                                    if (!emp.socialMediaLink.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        val label = emp.socialMediaLabel ?: "Мессенджер"
                                        val link = emp.socialMediaLink ?: ""
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SnsOrange.copy(alpha = 0.1f))
                                                .clickable {
                                                    try {
                                                        val uriStr = if (link.startsWith("@")) {
                                                            val username = link.replace("@", "")
                                                            if (label.lowercase().contains("vk")) "https://vk.com/$username"
                                                            else "https://t.me/$username"
                                                        } else {
                                                            if (!link.startsWith("http")) "https://$link" else link
                                                        }
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uriStr))
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {}
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Send,
                                                contentDescription = null,
                                                tint = SnsRedAccent,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "$label: $link",
                                                color = SnsRedAccent,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = SnsBorder)

                                    // First Phone Number
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = null,
                                                tint = SnsGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = emp.phoneNumber,
                                                fontSize = 14.sp,
                                                color = SnsSilverText,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(
                                                            SnsGreen,
                                                            Color(0xFF10B981)
                                                        )
                                                    )
                                                )
                                                .clickable { triggerPhoneCall(context, emp.phoneNumber) }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Call,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "Позвонить",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }

                                    // Second Phone Number (if present)
                                    if (emp.secondPhoneNumber.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(
                                                    imageVector = Icons.Default.Call,
                                                    contentDescription = null,
                                                    tint = SnsGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = emp.secondPhoneNumber,
                                                    fontSize = 14.sp,
                                                    color = SnsSilverText,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            colors = listOf(
                                                                SnsGreen,
                                                                Color(0xFF10B981)
                                                            )
                                                        )
                                                    )
                                                    .clickable { triggerPhoneCall(context, emp.secondPhoneNumber) }
                                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Call,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "Позвонить 2",
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = SnsBorder)

                                    // Edit / Delete Actions
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { editingEmployee = emp }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Изменить",
                                                tint = SnsGreen,
                                                modifier = Modifier.size(18.dp)
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
                                                tint = SnsRedAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingEmployee != null) {
        val emp = editingEmployee!!
        
        // Define clean parse logic
        val parsed = remember(emp) {
            val position = emp.position
            val fullName = emp.fullName
            val shortPos = getShortPosition(position)
            val isTp = shortPos == "ТП" || shortPos == "ЛТП" || shortPos == "УДФ ТП" || shortPos == "СТП" || position.contains("ТП") || position.contains("Торговый")
            if (!isTp) {
                Triple(fullName, "", Triple(false, false, false))
            } else {
                val pCode = when (shortPos) {
                    "ЛТП" -> "ЛТП"
                    "УДФ ТП" -> "УДФ ТП"
                    "СТП" -> "СТП"
                    else -> "ТП"
                }
                val isSummer = pCode == "ЛТП"
                val isRemote = pCode == "УДФ ТП"
                val isShift = pCode == "СТП"
                
                val parts = fullName.split(" ")
                var routeVal = ""
                val vPart = parts.find { it.startsWith("V") }
                if (vPart != null) {
                    routeVal = vPart.removePrefix("V")
                }
                
                var cleanName = fullName
                val prefixToMatches = listOf(
                    "ЛТП V$routeVal ", "УДФ ТП V$routeVal ", "СТП V$routeVal ", "ТП V$routeVal ",
                    "ЛТП V$routeVal", "УДФ ТП V$routeVal", "СТП V$routeVal", "ТП V$routeVal",
                    "ЛТП ", "УДФ ТП ", "СТП ", "ТП "
                )
                for (prefix in prefixToMatches) {
                    if (cleanName.startsWith(prefix)) {
                        cleanName = cleanName.substring(prefix.length)
                        break
                    }
                }
                Triple(cleanName.trim(), routeVal, Triple(isSummer, isRemote, isShift))
            }
        }

        var editName by remember(emp) { mutableStateOf(parsed.first) }
        var editPos by remember(emp) { mutableStateOf(emp.position) }
        var editPhone by remember(emp) { mutableStateOf(emp.phoneNumber) }
        var editSecondPhone by remember(emp) { mutableStateOf(emp.secondPhoneNumber) }
        var editSocialLabel by remember(emp) { mutableStateOf(emp.socialMediaLabel ?: "") }
        var editSocialLink by remember(emp) { mutableStateOf(emp.socialMediaLink ?: "") }

        var editRouteNumber by remember(emp) { mutableStateOf(parsed.second) }
        var editIsSummer by remember(emp) { mutableStateOf(parsed.third.first) }
        var editIsRemote by remember(emp) { mutableStateOf(parsed.third.second) }
        var editIsShift by remember(emp) { mutableStateOf(parsed.third.third) }

        var expandedEditPosMenu by remember { mutableStateOf(false) }
        var showCustomEditPosDialog by remember { mutableStateOf(false) }
        var customEditPosInput by remember { mutableStateOf("") }

        var expandedSocialMenu by remember { mutableStateOf(false) }
        var showCustomSocialLabelDialog by remember { mutableStateOf(false) }
        var customSocialLabelInput by remember { mutableStateOf("") }

        val currentCustomPositions by viewModel.customPositions.collectAsStateWithLifecycle()
        val predefinedPositions = remember(currentCustomPositions) {
            currentCustomPositions.map { it.name }
        }
        val isTpSelectedEdit = editPos.contains("ТП") || editPos.contains("Торговый")

        AlertDialog(
            onDismissRequest = { editingEmployee = null },
            title = {
                Text(
                    text = "ИЗМЕНЕНИЕ СОТРУДНИКА",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = SnsGreen,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("ФИО") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
                    )

                    // Dropdown Position selector field for Edit dialog
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedEditPosMenu = true }
                    ) {
                        OutlinedTextField(
                            value = editPos,
                            onValueChange = {},
                            label = { Text("Должность") },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = SnsSilverText,
                                disabledBorderColor = SnsBorder,
                                disabledLabelColor = SnsSecondaryText,
                                disabledTrailingIconColor = SnsSecondaryText,
                                focusedBorderColor = SnsGreen
                            ),
                            trailingIcon = {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Категории", tint = SnsSecondaryText)
                            }
                        )

                        androidx.compose.material3.DropdownMenu(
                            expanded = expandedEditPosMenu,
                            onDismissRequest = { expandedEditPosMenu = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SnsCardBg)
                        ) {
                            predefinedPositions.forEach { item ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(item, color = SnsSilverText, fontSize = 14.sp) },
                                    onClick = {
                                        editPos = item
                                        expandedEditPosMenu = false
                                    }
                                )
                            }
                            
                            androidx.compose.material3.HorizontalDivider(color = SnsBorder)
                            
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("[+] Добавить должность...", color = SnsGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    expandedEditPosMenu = false
                                    showCustomEditPosDialog = true
                                }
                            )
                        }
                    }

                    // Conditional Trade Representative fields UI for Edit dialog
                    if (isTpSelectedEdit) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SnsDarkBg),
                            border = BorderStroke(1.dp, SnsBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Параметры Торгового Представителя (ТП)",
                                    fontWeight = FontWeight.Bold,
                                    color = SnsRedAccent,
                                    fontSize = 12.sp
                                )

                                OutlinedTextField(
                                    value = editRouteNumber,
                                    onValueChange = { editRouteNumber = it },
                                    label = { Text("Номер маршрута (например: 15)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = editIsSummer,
                                        onCheckedChange = {
                                            editIsSummer = it
                                            if (it) { editIsRemote = false; editIsShift = false }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = SnsGreen)
                                    )
                                    Text("Летний ТП [Л] -> ЛТП", color = SnsSilverText, fontSize = 13.sp)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = editIsRemote,
                                        onCheckedChange = {
                                            editIsRemote = it
                                            if (it) { editIsSummer = false; editIsShift = false }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = SnsGreen)
                                    )
                                    Text("Удаленный ТП [УДФ] -> УДФ ТП", color = SnsSilverText, fontSize = 13.sp)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = editIsShift,
                                        onCheckedChange = {
                                            editIsShift = it
                                            if (it) { editIsSummer = false; editIsRemote = false }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = SnsGreen)
                                    )
                                    Text("Сменный ТП [СТП] -> СТП", color = SnsSilverText, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Телефон") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
                    )

                    OutlinedTextField(
                        value = editSecondPhone,
                        onValueChange = { editSecondPhone = it },
                        label = { Text("Второй телефон (необязательно)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
                    )
                    
                    // Platform dropdown picker
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedSocialMenu = true }
                    ) {
                        OutlinedTextField(
                            value = editSocialLabel,
                            onValueChange = {},
                            label = { Text("Выберите платформу") },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = SnsSilverText,
                                disabledBorderColor = SnsBorder,
                                disabledLabelColor = SnsSecondaryText,
                                disabledTrailingIconColor = SnsSecondaryText,
                                focusedBorderColor = SnsGreen
                            ),
                            trailingIcon = {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Выбрать платформу", tint = SnsSecondaryText)
                            }
                        )

                        androidx.compose.material3.DropdownMenu(
                            expanded = expandedSocialMenu,
                            onDismissRequest = { expandedSocialMenu = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SnsCardBg)
                        ) {
                            listOf("Telegram", "WhatsApp", "Viber", "VK").forEach { platform ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(platform, color = SnsSilverText, fontSize = 14.sp) },
                                    onClick = {
                                        editSocialLabel = platform
                                        expandedSocialMenu = false
                                    }
                                )
                            }
                            
                            androidx.compose.material3.HorizontalDivider(color = SnsBorder)
                            
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("[+] Добавить новое", color = SnsGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    expandedSocialMenu = false
                                    showCustomSocialLabelDialog = true
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editSocialLink,
                        onValueChange = { editSocialLink = it },
                        label = { Text("Имя контакта (например @username)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalPos: String
                        val finalName: String
                        if (isTpSelectedEdit) {
                            val pCode = when {
                                editIsSummer -> "ЛТП"
                                editIsRemote -> "УДФ ТП"
                                editIsShift -> "СТП"
                                else -> "ТП"
                            }
                            finalPos = "[$pCode] Торговый представитель"
                            finalName = when {
                                editIsSummer -> "ЛТП V$editRouteNumber $editName"
                                editIsRemote -> "УДФ ТП V$editRouteNumber $editName"
                                editIsShift -> "СТП V$editRouteNumber $editName"
                                else -> if (editRouteNumber.isNotBlank()) "ТП V$editRouteNumber $editName" else "ТП $editName"
                            }
                        } else {
                            finalPos = editPos
                            finalName = editName
                        }

                        val updated = emp.copy(
                            fullName = finalName.trim(),
                            position = finalPos.trim(),
                            phoneNumber = editPhone.trim(),
                            secondPhoneNumber = editSecondPhone.trim(),
                            socialMediaLabel = editSocialLabel.trim().ifBlank { null },
                            socialMediaLink = editSocialLink.trim().ifBlank { null }
                        )
                        viewModel.updateEmployee(updated)
                        editingEmployee = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnsGreen),
                    enabled = editName.isNotBlank() && editPos.isNotBlank() && editPhone.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEmployee = null }) {
                    Text("Отмена", color = SnsSilverText)
                }
            },
            containerColor = SnsCardBg,
            textContentColor = SnsSilverText
        )

        if (showCustomEditPosDialog) {
            AlertDialog(
                onDismissRequest = { showCustomEditPosDialog = false },
                title = { Text("ДОБАВИТЬ ДОЛЖНОСТЬ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SnsGreen, fontFamily = FontFamily.Monospace) },
                text = {
                    OutlinedTextField(
                        value = customEditPosInput,
                        onValueChange = { customEditPosInput = it },
                        label = { Text("Введите название должности") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (customEditPosInput.isNotBlank()) {
                                editPos = customEditPosInput.trim()
                                customEditPosInput = ""
                            }
                            showCustomEditPosDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsGreen)
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomEditPosDialog = false }) {
                        Text("Отмена", color = SnsSilverText)
                    }
                },
                containerColor = SnsCardBg,
                textContentColor = SnsSilverText
            )
        }

        if (showCustomSocialLabelDialog) {
            AlertDialog(
                onDismissRequest = { showCustomSocialLabelDialog = false },
                title = { Text("ДОБАВИТЬ НОВОЕ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SnsGreen, fontFamily = FontFamily.Monospace) },
                text = {
                    OutlinedTextField(
                        value = customSocialLabelInput,
                        onValueChange = { customSocialLabelInput = it },
                        label = { Text("Введите название платформы") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (customSocialLabelInput.isNotBlank()) {
                                editSocialLabel = customSocialLabelInput.trim()
                                customSocialLabelInput = ""
                            }
                            showCustomSocialLabelDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsGreen)
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomSocialLabelDialog = false }) {
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
fun PositionsTab(
    viewModel: MainViewModel,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    val positions by viewModel.customPositions.collectAsStateWithLifecycle()
    var newPosName by remember { mutableStateOf("") }
    var editingPos by remember { mutableStateOf<CustomPosition?>(null) }
    var renamePosInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Form: Add Position
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SnsCardBg),
            border = BorderStroke(1.dp, SnsBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ДОБАВЛЕНИЕ НОВОЙ ДОЛЖНОСТИ",
                    fontWeight = FontWeight.Bold,
                    color = SnsGreen,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = newPosName,
                    onValueChange = { newPosName = it },
                    placeholder = { Text("Название должности (например, [СВ] Супервайзер)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_position_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        if (newPosName.isNotBlank()) {
                            viewModel.addCustomPosition(newPosName.trim())
                            newPosName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnsGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_save_position"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Добавить должность", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "СУЩЕСТВУЮЩИЕ ДОЛЖНОСТИ",
            fontWeight = FontWeight.Bold,
            color = SnsSilverText,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (positions.isEmpty()) {
                item {
                    Text(
                        text = "Список должностей пуст",
                        color = SnsSecondaryText,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            } else {
                items(positions) { pos ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SnsCardBg),
                        border = BorderStroke(1.dp, SnsBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = pos.name,
                                color = SnsSilverText,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            
                            Row {
                                IconButton(
                                    onClick = {
                                        editingPos = pos
                                        renamePosInput = pos.name
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Редактировать",
                                        tint = SnsGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                
                                IconButton(
                                    onClick = {
                                        requestConfirm("УДАЛИТЬ", "Удалить должность '${pos.name}'?") {
                                            viewModel.deleteCustomPosition(pos)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = SnsRedAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for renaming position
    val currentPos = editingPos
    if (currentPos != null) {
        AlertDialog(
            onDismissRequest = { editingPos = null },
            title = {
                Text(
                    text = "ИЗМЕНЕНИЕ НАЗВАНИЯ ДОЛЖНОСТИ",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = SnsGreen,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = renamePosInput,
                        onValueChange = { renamePosInput = it },
                        label = { Text("Новое название") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renamePosInput.isNotBlank()) {
                            viewModel.updateCustomPosition(currentPos.copy(name = renamePosInput.trim()))
                            editingPos = null
                        }
                    }
                ) {
                    Text("СОХРАНИТЬ", color = SnsGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPos = null }) {
                    Text("ОТМЕНА", color = SnsSecondaryText)
                }
            },
            containerColor = SnsCardBg,
            shape = RoundedCornerShape(24.dp)
        )
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
    var phone2 by remember { mutableStateOf("") }

    // Dropdown state for position options derived from DB
    val currentCustomPositions by viewModel.customPositions.collectAsStateWithLifecycle()
    val predefinedPositions = remember(currentCustomPositions) {
        currentCustomPositions.map { it.name }
    }
    var expandedPosMenu by remember { mutableStateOf(false) }
    var showCustomPosDialog by remember { mutableStateOf(false) }
    var customPosInput by remember { mutableStateOf("") }

    // Specific Trade Representative (ТП) fields
    val isTpSelected = pos.contains("ТП") || pos.contains("Торговый")
    var routeNumber by remember { mutableStateOf("") }
    var isSummer by remember { mutableStateOf(false) }
    var isRemote by remember { mutableStateOf(false) }
    var isShift by remember { mutableStateOf(false) }

    // Optional social details
    var socialLabel by remember { mutableStateOf("") }
    var socialLink by remember { mutableStateOf("") }
    
    var expandedSocialMenu by remember { mutableStateOf(false) }
    var showCustomSocialLabelDialog by remember { mutableStateOf(false) }
    var customSocialLabelInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "РЕГИСТРАЦИЯ НОВОГО СОТРУДНИКА", fontWeight = FontWeight.Bold, color = SnsGreen, fontSize = 14.sp)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("ФИО сотрудника (например: Петров Петр Петрович)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emp_name"),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
        )

        // Dropdown Position selector field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandedPosMenu = true }
        ) {
            OutlinedTextField(
                value = pos,
                onValueChange = {},
                label = { Text("Должность") },
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("emp_position"),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = SnsSilverText,
                    disabledBorderColor = SnsBorder,
                    disabledLabelColor = SnsSecondaryText,
                    disabledTrailingIconColor = SnsSecondaryText,
                    focusedBorderColor = SnsGreen
                ),
                trailingIcon = {
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Категории", tint = SnsSecondaryText)
                }
            )

            androidx.compose.material3.DropdownMenu(
                expanded = expandedPosMenu,
                onDismissRequest = { expandedPosMenu = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(SnsCardBg)
            ) {
                predefinedPositions.forEach { item ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(item, color = SnsSilverText, fontSize = 14.sp) },
                        onClick = {
                            pos = item
                            expandedPosMenu = false
                        }
                    )
                }
                
                androidx.compose.material3.HorizontalDivider(color = SnsBorder)
                
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("[+] Добавить должность...", color = SnsGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                    onClick = {
                        expandedPosMenu = false
                        showCustomPosDialog = true
                    }
                )
            }
        }

        // Conditional Trade Representative fields UI
        if (isTpSelected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SnsDarkBg),
                border = BorderStroke(1.dp, SnsBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Параметры Торгового Представителя (ТП)",
                        fontWeight = FontWeight.Bold,
                        color = SnsRedAccent,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = routeNumber,
                        onValueChange = { routeNumber = it },
                        label = { Text("Номер маршрута (например: 15)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSummer,
                            onCheckedChange = {
                                isSummer = it
                                if (it) { isRemote = false; isShift = false }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = SnsGreen)
                        )
                        Text("Летний ТП [Л] -> ЛТП", color = SnsSilverText, fontSize = 13.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isRemote,
                            onCheckedChange = {
                                isRemote = it
                                if (it) { isSummer = false; isShift = false }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = SnsGreen)
                        )
                        Text("Удаленный ТП [УДФ] -> УДФ ТП", color = SnsSilverText, fontSize = 13.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isShift,
                            onCheckedChange = {
                                isShift = it
                                if (it) { isSummer = false; isRemote = false }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = SnsGreen)
                        )
                        Text("Сменный ТП [СТП] -> СТП", color = SnsSilverText, fontSize = 13.sp)
                    }
                }
            }
        }

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Номер телефона (например, +79991234567)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emp_phone"),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
        )

        OutlinedTextField(
            value = phone2,
            onValueChange = { phone2 = it },
            label = { Text("Второй номер телефона (необязательно)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emp_phone_2"),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
        )

        // Social details card with platforms selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SnsDarkBg),
            border = BorderStroke(1.dp, SnsBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Дополнительная соцсеть или мессенджер",
                    fontWeight = FontWeight.Bold,
                    color = SnsGreen,
                    fontSize = 12.sp
                )

                // Platform Dropdown
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedSocialMenu = true }
                ) {
                    OutlinedTextField(
                        value = socialLabel,
                        onValueChange = {},
                        label = { Text("Выберите платформу") },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = SnsSilverText,
                            disabledBorderColor = SnsBorder,
                            disabledLabelColor = SnsSecondaryText,
                            disabledTrailingIconColor = SnsSecondaryText,
                            focusedBorderColor = SnsGreen
                        ),
                        trailingIcon = {
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Выбрать платформу", tint = SnsSecondaryText)
                        }
                    )

                    androidx.compose.material3.DropdownMenu(
                        expanded = expandedSocialMenu,
                        onDismissRequest = { expandedSocialMenu = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(SnsCardBg)
                    ) {
                        listOf("Telegram", "WhatsApp", "Viber", "VK").forEach { platform ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(platform, color = SnsSilverText, fontSize = 14.sp) },
                                onClick = {
                                    socialLabel = platform
                                    expandedSocialMenu = false
                                }
                            )
                        }
                        
                        androidx.compose.material3.HorizontalDivider(color = SnsBorder)
                        
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("[+] Добавить новое", color = SnsGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                            onClick = {
                                expandedSocialMenu = false
                                showCustomSocialLabelDialog = true
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = socialLink,
                    onValueChange = { socialLink = it },
                    label = { Text("Никнейм или ссылка (например, @gymbober)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
                )
            }
        }

        if (showCustomSocialLabelDialog) {
            AlertDialog(
                onDismissRequest = { showCustomSocialLabelDialog = false },
                title = { Text("ДОБАВИТЬ НОВОЕ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SnsGreen, fontFamily = FontFamily.Monospace) },
                text = {
                    OutlinedTextField(
                        value = customSocialLabelInput,
                        onValueChange = { customSocialLabelInput = it },
                        label = { Text("Введите название платформы") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (customSocialLabelInput.isNotBlank()) {
                                socialLabel = customSocialLabelInput.trim()
                                customSocialLabelInput = ""
                            }
                            showCustomSocialLabelDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsGreen)
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomSocialLabelDialog = false }) {
                        Text("Отмена", color = SnsSilverText)
                    }
                },
                containerColor = SnsCardBg,
                textContentColor = SnsSilverText
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = {
                if (name.isNotBlank() && pos.isNotBlank() && phone.isNotBlank()) {
                    val finalPos: String
                    val finalName: String
                    if (isTpSelected) {
                        val pCode = when {
                            isSummer -> "ЛТП"
                            isRemote -> "УДФ ТП"
                            isShift -> "СТП"
                            else -> "ТП"
                        }
                        finalPos = "[$pCode] Торговый представитель"
                        finalName = when {
                            isSummer -> "ЛТП V$routeNumber $name"
                            isRemote -> "УДФ ТП V$routeNumber $name"
                            isShift -> "СТП V$routeNumber $name"
                            else -> if (routeNumber.isNotBlank()) "ТП V$routeNumber $name" else "ТП $name"
                        }
                    } else {
                        finalPos = pos
                        finalName = name
                    }

                    // Directly add employee without confirmation dialog
                    viewModel.addEmployee(
                        finalName, 
                        finalPos, 
                        phone, 
                        phone2,
                        socialLabel.ifBlank { null }, 
                        socialLink.ifBlank { null }
                    )
                    
                    name = ""
                    pos = ""
                    phone = ""
                    phone2 = ""
                    routeNumber = ""
                    isSummer = false
                    isRemote = false
                    isShift = false
                    socialLabel = ""
                    socialLink = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = SnsGreen),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_save_employee"),
            enabled = name.isNotBlank() && pos.isNotBlank() && phone.isNotBlank()
        ) {
            Text("Сохранить сотрудника")
        }
    }

    // Custom Position dialog
    if (showCustomPosDialog) {
        AlertDialog(
            onDismissRequest = { showCustomPosDialog = false },
            title = { Text("Своя должность", color = SnsSilverText, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = customPosInput,
                    onValueChange = { customPosInput = it },
                    label = { Text("Какая должность?") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customPosInput.isNotBlank()) {
                            pos = customPosInput.trim()
                            showCustomPosDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnsGreen)
                ) {
                    Text("Выбрать", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPosDialog = false }) {
                    Text("Отмена", color = SnsSilverText)
                }
            },
            containerColor = SnsCardBg,
            textContentColor = SnsSilverText
        )
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

    // State for viewing note in full screen Pager Reader
    var selectedNoteIndexForView by remember { mutableStateOf<Int?>(null) }

    var editingNote by remember { mutableStateOf<InstructionNote?>(null) }
    var editNoteTitle by remember { mutableStateOf("") }
    var editNoteContent by remember { mutableStateOf("") }

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
                itemsIndexed(notes) { index, note ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedNoteIndexForView = index
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SnsCardBg),
                        border = BorderStroke(1.dp, SnsBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = note.title,
                                color = SnsRedAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        editingNote = note
                                        editNoteTitle = note.title
                                        editNoteContent = note.content
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Редактировать",
                                        tint = SnsSecondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

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
                            }
                        }
                    }
                }
            }
        }

        // Full Screen Note Reader modal
        selectedNoteIndexForView?.let { index ->
            FullScreenNotePagerViewer(
                notes = notes,
                initialIndex = index,
                onDismiss = { selectedNoteIndexForView = null },
                viewModel = viewModel,
                requestConfirm = requestConfirm
            )
        }

        // Slideshow image lightbox dialog
        if (showGalleryDialog && selectedGalleryPhotos.isNotEmpty()) {
            FullScreenImageLightbox(
                photos = selectedGalleryPhotos,
                initialIndex = selectedGalleryIndex,
                onDismiss = { showGalleryDialog = false }
            )
        }

        editingNote?.let { noteToEdit ->
            EditNoteDialog(
                note = noteToEdit,
                onDismiss = { editingNote = null },
                onSave = { updatedTitle, updatedContent, updatedPhotosJson ->
                    viewModel.updateInstruction(
                        noteToEdit.copy(
                            title = updatedTitle,
                            content = updatedContent,
                            localPhotosJson = updatedPhotosJson
                        )
                    )
                    editingNote = null
                }
            )
        }
    }

@Composable
fun EditNoteDialog(
    note: InstructionNote,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, photosJson: String) -> Unit
) {
    var titleState by remember { mutableStateOf(note.title) }
    var contentState by remember { mutableStateOf(note.content) }
    val photosList = remember { 
        mutableStateListOf<PhotoAttachment>().apply {
            if (!note.localPhotosJson.isNullOrBlank()) {
                val parsed = note.localPhotosJson.split("|").mapNotNull { part ->
                    val segments = part.split("^")
                    if (segments.isNotEmpty() && segments[0].isNotBlank()) {
                        val path = segments[0]
                        val cmt = if (segments.size >= 2) segments[1] else ""
                        val pos = if (segments.size >= 3) segments[2] else "BOTTOM"
                        PhotoAttachment(path, cmt, pos)
                    } else null
                }
                addAll(parsed)
            }
        }
    }
    
    var showCommentDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var tempComment by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            pendingUri = uri
            tempComment = ""
            showCommentDialog = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать заметку", color = SnsSilverText, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = titleState,
                    onValueChange = { titleState = it },
                    label = { Text("Название заметки") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsRedAccent)
                )
                
                OutlinedTextField(
                    value = contentState,
                    onValueChange = { contentState = it },
                    label = { Text("Текст заметки") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsRedAccent),
                    maxLines = 6
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ИЗОБРАЖЕНИЯ:", fontSize = 11.sp, color = SnsSecondaryText, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Text("+ Добавить фото", color = SnsRedAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                if (photosList.isEmpty()) {
                    Text("Изображений нет", fontSize = 12.sp, color = SnsSecondaryText)
                } else {
                    photosList.forEachIndexed { index, photo ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SnsDarkBg),
                            border = BorderStroke(1.dp, SnsBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bitmap = rememberLocalImagePainter(photo.filePath)
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(Color.Black, RoundedCornerShape(8.dp))
                                        .border(1.dp, SnsBorder, RoundedCornerShape(8.dp))
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.BrokenImage,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = photo.comment,
                                        onValueChange = { newVal ->
                                            photosList[index] = photo.copy(comment = newVal)
                                        },
                                        placeholder = { Text("Подпись...", fontSize = 11.sp) },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = SnsSilverText, fontSize = 11.sp),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = SnsRedAccent,
                                            unfocusedBorderColor = SnsBorder
                                        ),
                                        maxLines = 1
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Вверх",
                                            color = if (index > 0) SnsRedAccent else SnsSecondaryText.copy(alpha = 0.5f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable(enabled = index > 0) {
                                                val temp = photosList[index]
                                                photosList[index] = photosList[index - 1]
                                                photosList[index - 1] = temp
                                            }
                                        )
                                        Text(
                                            text = "Вниз",
                                            color = if (index < photosList.size - 1) SnsRedAccent else SnsSecondaryText.copy(alpha = 0.5f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable(enabled = index < photosList.size - 1) {
                                                val temp = photosList[index]
                                                photosList[index] = photosList[index + 1]
                                                photosList[index + 1] = temp
                                            }
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Расположение:", fontSize = 10.sp, color = SnsSecondaryText)
                                        listOf("Вверху" to "TOP", "В тексте" to "MIDDLE", "Внизу" to "BOTTOM").forEach { (label, value) ->
                                            val isSelected = photo.position == value
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isSelected) SnsRedAccent else SnsBorder)
                                                    .clickable {
                                                        photosList[index] = photo.copy(position = value)
                                                    }
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 9.sp,
                                                    color = if (isSelected) Color.White else SnsSilverText,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                IconButton(
                                    onClick = { photosList.removeAt(index) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить фото",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (titleState.isNotBlank() && contentState.isNotBlank()) {
                        val serialized = photosList.joinToString("|") { "${it.filePath}^${it.comment}^${it.position}" }
                        onSave(titleState.trim(), contentState.trim(), serialized)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent),
                enabled = titleState.isNotBlank() && contentState.isNotBlank()
            ) {
                Text("Сохранить", color = Color.White)
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

    if (showCommentDialog && pendingUri != null) {
        AlertDialog(
            onDismissRequest = {
                showCommentDialog = false
                pendingUri = null
            },
            title = { Text("Описание фотографии", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SnsSilverText) },
            text = {
                OutlinedTextField(
                    value = tempComment,
                    onValueChange = { tempComment = it },
                    label = { Text("Что изображено?") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsRedAccent)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingUri
                        if (uri != null) {
                            val savedPath = saveUriToInternalStorage(context, uri)
                            if (savedPath != null) {
                                photosList.add(PhotoAttachment(savedPath, tempComment.trim()))
                            }
                            pendingUri = null
                            showCommentDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent)
                ) {
                    Text("Готово", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCommentDialog = false
                    pendingUri = null
                }) {
                    Text("Отмена", color = SnsSilverText)
                }
            },
            containerColor = SnsCardBg,
            textContentColor = SnsSilverText
        )
    }
}

@Composable
fun FullScreenNotePagerViewer(
    notes: List<InstructionNote>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    viewModel: MainViewModel,
    requestConfirm: (String, String, () -> Unit) -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { notes.size })

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SnsDarkBg) // Clean midnight black writer paper
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
            val note = notes[page]
            
            // Local state to view this note's image slideshow
            var showLightbox by remember { mutableStateOf(false) }
            var lightboxIndex by remember { mutableStateOf(0) }
            
            val attachedPhotos = remember(note.localPhotosJson) {
                if (note.localPhotosJson.isNullOrBlank()) emptyList()
                else {
                    note.localPhotosJson.split("|").mapNotNull { part ->
                        val segments = part.split("^")
                        if (segments.isNotEmpty() && segments[0].isNotBlank()) {
                            val path = segments[0]
                            val cmt = if (segments.size >= 2) segments[1] else ""
                            val pos = if (segments.size >= 3) segments[2] else "BOTTOM"
                            PhotoAttachment(path, cmt, pos)
                        } else null
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Reader Space Header (Telegra.ph layout)
                Spacer(modifier = Modifier.height(48.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "РЕЖИМ ПРОСМОТРА",
                        fontSize = 10.sp,
                        color = SnsRedAccent,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(SnsBorder.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = SnsSilverText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Big elegant title
                Text(
                    text = note.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.W800,
                    color = SnsSilverText,
                    lineHeight = 34.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Anonymous/Author info & paging counter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "IT Assistant • Страница ${page + 1} из ${notes.size}",
                        fontSize = 12.sp,
                        color = SnsSecondaryText,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = "<- свипнуть ->",
                        fontSize = 11.sp,
                        color = SnsBorder,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SnsBorder.copy(alpha = 0.6f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(20.dp))
                
                // Separately filter attached photos by their positions
                val topPhotos = attachedPhotos.filter { it.position == "TOP" }
                val midPhotos = attachedPhotos.filter { it.position == "MIDDLE" }
                val bottomPhotos = attachedPhotos.filter { it.position == "BOTTOM" }

                // 1. TOP images
                if (topPhotos.isNotEmpty()) {
                    topPhotos.forEachIndexed { _, photo ->
                        NoteImageBlock(photo) {
                            lightboxIndex = attachedPhotos.indexOf(photo)
                            showLightbox = true
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 2. Body Text with MIDDLE images intercalated between paragraphs
                val paragraphs = note.content.split("\n\n")
                paragraphs.forEachIndexed { pIdx, paragraph ->
                    if (paragraph.isNotBlank()) {
                        Text(
                            text = paragraph,
                            fontSize = 16.sp,
                            color = SnsSilverText,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    
                    // Insert MIDDLE images after the middle paragraph
                    if (midPhotos.isNotEmpty() && pIdx == paragraphs.size / 2 - 1) {
                        midPhotos.forEach { photo ->
                            NoteImageBlock(photo) {
                                lightboxIndex = attachedPhotos.indexOf(photo)
                                showLightbox = true
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // If only 1 paragraph exists, middle photos go right after it
                if (paragraphs.size == 1 && midPhotos.isNotEmpty()) {
                    midPhotos.forEach { photo ->
                        NoteImageBlock(photo) {
                            lightboxIndex = attachedPhotos.indexOf(photo)
                            showLightbox = true
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 3. BOTTOM images
                if (bottomPhotos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Прикрепленные изображения:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SnsSecondaryText,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    bottomPhotos.forEach { photo ->
                        NoteImageBlock(photo) {
                            lightboxIndex = attachedPhotos.indexOf(photo)
                            showLightbox = true
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(100.dp))
            }
            
            if (showLightbox && attachedPhotos.isNotEmpty()) {
                FullScreenImageLightbox(
                    photos = attachedPhotos,
                    initialIndex = lightboxIndex,
                    onDismiss = { showLightbox = false }
                )
            }
        }
    }
}
}

@Composable
fun NoteImageBlock(photo: PhotoAttachment, onClick: () -> Unit) {
    val bitmap = rememberLocalImagePainter(photo.filePath)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SnsDarkBg),
        border = BorderStroke(1.dp, SnsBorder.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = photo.comment,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            if (photo.comment.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = photo.comment,
                        color = SnsSecondaryText,
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreenImageLightbox(
    photos: List<PhotoAttachment>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { photos.size })

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black) // Dark lightbox theme
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
            val photo = photos[page]
            val bitmap = rememberLocalImagePainter(photo.filePath)
            
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = photo.comment,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Изображение не найдено", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
        }

        // Overlay controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ПРОСМОТР ФОТО",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Кадр ${pagerState.currentPage + 1} из ${photos.size}",
                    color = SnsSecondaryText,
                    fontSize = 11.sp
                )
            }
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Color.White
                )
            }
        }

        // Caption overlay at bottom
        val currentPhoto = photos.getOrNull(pagerState.currentPage)
        if (currentPhoto != null && currentPhoto.comment.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(24.dp)
            ) {
                Text(
                    text = currentPhoto.comment,
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    text = "РЕДАКТОР ИНСТРУКЦИЙ И ЗАМЕТОК",
                    fontWeight = FontWeight.Bold,
                    color = SnsRedAccent,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            // High-end minimalist design: Large title field with transparent-like borders
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Название инструкции", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SnsSecondaryText) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SnsSilverText),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SnsRedAccent,
                        unfocusedBorderColor = SnsBorder,
                        focusedLabelColor = SnsRedAccent,
                        cursorColor = SnsRedAccent
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // Main editor paper content block
            item {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("Описание или содержание инструкции...", fontSize = 15.sp, color = SnsSecondaryText) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = SnsSilverText, lineHeight = 22.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .testTag("note_content"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SnsRedAccent,
                        unfocusedBorderColor = SnsBorder,
                        focusedLabelColor = SnsRedAccent,
                        cursorColor = SnsRedAccent
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // Media insertion row
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SnsDarkBg),
                    border = BorderStroke(1.dp, SnsBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "ИЗОБРАЖЕНИЯ И СХЕМЫ", fontSize = 11.sp, color = SnsSecondaryText, fontWeight = FontWeight.Bold)
                                Text(text = "Добавьте photo к содержанию", fontSize = 10.sp, color = SnsSecondaryText)
                            }
                            Button(
                                onClick = { galleryLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = SnsGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("+ фото", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        if (attachedPhotos.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
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
            }

            item {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            // Serialize attached photo entities
                            val serializedPhotosText = attachedPhotos.joinToString("|") { "${it.filePath}^${it.comment}^${it.position}" }
                            
                            // Send empty drawing string, since sketch pad was removed!
                            viewModel.addInstruction(title, content, "", serializedPhotosText)
                            title = ""
                            content = ""
                            attachedPhotos.clear()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_save_note")
                ) {
                    Text("Записать")
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (state.keywordRequired == "УДАЛИТЬ") Icons.Default.Delete else Icons.Default.Check,
                    contentDescription = null,
                    tint = if (state.keywordRequired == "УДАЛИТЬ") SnsRedAccent else SnsOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.keywordRequired == "УДАЛИТЬ") "ПОДТВЕРЖДЕНИЕ УДАЛЕНИЯ" else "ПОДТВЕРЖДЕНИЕ ДЕЙСТВИЯ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SnsSilverText,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Text(
                text = state.promptMessage,
                fontSize = 14.sp,
                color = SnsSilverText
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    state.onConfirm()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.keywordRequired == "УДАЛИТЬ") SnsRedAccent else SnsOrange
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

// Utility function to generate a professional PDF document for the equipment registry
fun generateEquipmentPdf(context: Context, items: List<EquipmentItem>): java.io.File? {
    try {
        val pdfDoc = android.graphics.pdf.PdfDocument()
        
        // A4 page size: 595 x 842 points. Row height is 30 points.
        val itemHeight = 30
        val itemsPerPage = 20
        val pageCount = if (items.isEmpty()) 1 else (items.size + itemsPerPage - 1) / itemsPerPage
        
        val paintHeader = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintCell = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 9f
            isAntiAlias = true
        }
        val paintTitle = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintSub = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 10f
            isAntiAlias = true
        }
        val paintFillHeader = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(230, 235, 240) // soft professional gray-blue
            style = android.graphics.Paint.Style.FILL
        }
        val paintLine = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(180, 180, 180)
            strokeWidth = 1f
            style = android.graphics.Paint.Style.STROKE
        }
        val paintAccent = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(21, 128, 61) // SNS Green
            strokeWidth = 2f
            style = android.graphics.Paint.Style.STROKE
        }
        
        for (pageIdx in 0 until pageCount) {
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageIdx + 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas
            
            // Draw visual branding and headers
            canvas.drawText("РЕЕСТР ОБОРУДОВАНИЯ - SNS", 40f, 60f, paintTitle)
            canvas.drawLine(40f, 75f, 555f, 75f, paintAccent)
            
            val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            canvas.drawText("Сформирован: $dateStr | Страница: ${pageIdx + 1} из $pageCount", 40f, 95f, paintSub)
            
            fun drawCellText(text: String, startX: Float, endX: Float, y: Float, isHeader: Boolean = false) {
                val padding = 5f
                val maxWidth = endX - startX - (padding * 2)
                val paintToUse = if (isHeader) paintHeader else paintCell
                val count = paintToUse.breakText(text, true, maxWidth, null)
                val drawStr = if (count < text.length) {
                    text.substring(0, maxOf(0, count - 3)) + "..."
                } else {
                    text
                }
                canvas.drawText(drawStr, startX + padding, y, paintToUse)
            }
            
            val startIdx = pageIdx * itemsPerPage
            val endIdx = minOf(startIdx + itemsPerPage, items.size)
            val numRows = endIdx - startIdx
            val tableBottom = 150f + (numRows * 30f)
            
            // Draw header background
            canvas.drawRect(30f, 120f, 565f, 150f, paintFillHeader)
            
            // Draw Table Header Texts
            drawCellText("Наименование", 30f, 155f, 138f, isHeader = true)
            drawCellText("Серийный номер", 155f, 250f, 138f, isHeader = true)
            drawCellText("Инвентарный номер", 250f, 345f, 138f, isHeader = true)
            drawCellText("Дата покупки", 345f, 430f, 138f, isHeader = true)
            drawCellText("Материальное лицо", 430f, 565f, 138f, isHeader = true)
            
            if (items.isEmpty()) {
                canvas.drawText("Нет зарегистрированного оборудования", 40f, 180f, paintCell)
            } else {
                for (i in startIdx until endIdx) {
                    val item = items[i]
                    val rowTop = 150f + (i - startIdx) * 30f
                    val textY = rowTop + 18f
                    
                    drawCellText(item.name, 30f, 155f, textY)
                    drawCellText(item.serialNumber, 155f, 250f, textY)
                    drawCellText(item.inventoryNumber, 250f, 345f, textY)
                    drawCellText(item.purchaseDate, 345f, 430f, textY)
                    drawCellText(item.assignee, 430f, 565f, textY)
                }
            }
            
            // Draw Table Grid Borders
            // Outer rectangle
            canvas.drawRect(30f, 120f, 565f, tableBottom, paintLine)
            
            // Header bottom border
            canvas.drawLine(30f, 150f, 565f, 150f, paintLine)
            
            // Data row bottom borders
            for (r in 1..numRows) {
                val lineY = 150f + r * 30f
                canvas.drawLine(30f, lineY, 565f, lineY, paintLine)
            }
            
            // Vertical column borders
            val columnsX = listOf(155f, 250f, 345f, 430f)
            columnsX.forEach { colX ->
                canvas.drawLine(colX, 120f, colX, tableBottom, paintLine)
            }
            
            pdfDoc.finishPage(page)
        }
        
        val pdfFile = java.io.File(context.cacheDir, "equipment_registry.pdf")
        if (pdfFile.exists()) {
            pdfFile.delete()
        }
        val out = java.io.FileOutputStream(pdfFile)
        pdfDoc.writeTo(out)
        out.close()
        pdfDoc.close()
        return pdfFile
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

// Utility function to trigger system Print Dialogue from direct PDF File
fun printEquipmentRegistryPdf(context: Context, pdfFile: java.io.File) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
        val jobName = "${context.getString(R.string.app_name)} - Реестр оборудования"
        
        val printAdapter = object : android.print.PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: android.print.PrintAttributes?,
                newAttributes: android.print.PrintAttributes,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                
                val builder = android.print.PrintDocumentInfo.Builder("equipment_registry.pdf")
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(android.print.PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                
                callback.onLayoutFinished(builder.build(), true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback
            ) {
                var input: java.io.FileInputStream? = null
                var output: java.io.FileOutputStream? = null
                try {
                    input = java.io.FileInputStream(pdfFile)
                    output = java.io.FileOutputStream(destination.fileDescriptor)
                    val buf = ByteArray(1024)
                    var bytesRead: Int
                    while (input.read(buf).also { bytesRead = it } > 0) {
                        output.write(buf, 0, bytesRead)
                    }
                    callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.toString())
                } finally {
                    input?.close()
                    output?.close()
                }
            }
        }
        
        printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка печати: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

// Share generated PDF using standard FileProvider Uri (Import/Save PDF)
fun exportEquipmentToPdf(context: Context, file: java.io.File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "com.example.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Экспорт таблицы в PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка при экспорте PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
    val comment: String,
    val position: String = "BOTTOM"
)

@Composable
fun rememberLocalImagePainter(filePath: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(filePath) {
        try {
            val file = java.io.File(filePath)
            if (file.exists()) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
                
                var sampleSize = 1
                val maxDim = 1024
                while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }
                
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
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
fun AboutAppScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importDatabase(context, uri) { success, msg ->
                // This triggers the global success dialog automatically
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
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

                HorizontalDivider(color = SnsBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))

                Text(
                    text = "РЕЗЕРВНОЕ КОПИРОВАНИЕ И ПЕРЕНОС",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SnsGreen,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Вы можете экспортировать локальную базу данных в файл JSON для переноса на другое устройство или импортировать ранее сохраненную копию.",
                    fontSize = 11.sp,
                    color = SnsSecondaryText,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.exportDatabase(context) { uri ->
                                if (uri != null) {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Поделиться базой данных SNS IT"))
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Экспорт", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = {
                            importLauncher.launch(arrayOf("*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Импорт", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                ContactCard(
                    label = "Рабочий контактный номер",
                    value = "+7 (962) 884-10-35",
                    icon = Icons.Default.Phone,
                    onClick = { triggerPhoneCall(context, "+7 (962) 884-10-35") }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                ContactCard(
                    label = "Рабочая почта",
                    value = "Vladimir.Dolgih@sochi.sns.ru",
                    icon = Icons.Default.Email,
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:Vladimir.Dolgih@sochi.sns.ru")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    }
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

    var editingTaskItem by remember { mutableStateOf<TaskItem?>(null) }
    var editTaskTitle by remember { mutableStateOf("") }
    var editTaskDesc by remember { mutableStateOf("") }
    var editTaskDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    var expandedTaskIds by remember { mutableStateOf(emptySet<Int>()) }

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
                    val isExpanded = expandedTaskIds.contains(task.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedTaskIds = if (expandedTaskIds.contains(task.id)) {
                                    expandedTaskIds - task.id
                                } else {
                                    expandedTaskIds + task.id
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = BorderStroke(1.5.dp, cardBorderColor),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isOverdue) Color(0xFFEF4444) else SnsSilverText
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Срок выполнения: до ${formatMillisToDate(task.createdDateMillis + task.daysNeeded * 86400000L)}",
                                        fontSize = 12.sp,
                                        color = if (isOverdue) Color(0xFFEF4444) else SnsSecondaryText
                                    )
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isOverdue) {
                                        Text(
                                            text = "🚨 ПРОСРОЧЕНО",
                                            color = Color(0xFFEF4444),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier
                                                .background(Color(0x33EF4444), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Развернуть",
                                        tint = SnsSecondaryText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = SnsBorder.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = task.description.ifBlank { "Описание отсутствует" },
                                    fontSize = 13.sp,
                                    color = SnsSilverText.copy(alpha = 0.9f)
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = SnsBorder.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Добавлена: ${formatMillisToDate(task.createdDateMillis)}",
                                        fontSize = 11.sp,
                                        color = SnsSecondaryText
                                    )
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                editingTaskItem = task
                                                editTaskTitle = task.title
                                                editTaskDesc = task.description
                                                editTaskDateMillis = task.createdDateMillis + task.daysNeeded * 86400000L
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Редактировать задачу",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                requestConfirm("УДАЛИТЬ", "Удалить задачу '${task.title}'?") {
                                                    viewModel.deleteTask(task)
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete, 
                                                contentDescription = "Удалить", 
                                                tint = Color.Gray, 
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        
                                        IconButton(
                                            onClick = {
                                                solutionInput = ""
                                                showCompleteDialog = task
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .background(SnsGreen.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check, 
                                                    contentDescription = "Выполнено", 
                                                    tint = SnsGreen, 
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
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
                            modifier = Modifier.fillMaxWidth().testTag("solution_input"),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen),
                            minLines = 3,
                            maxLines = 5
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

        editingTaskItem?.let { task ->
            val context = LocalContext.current
            val calendar = java.util.Calendar.getInstance()
            
            AlertDialog(
                onDismissRequest = { editingTaskItem = null },
                title = { Text("Редактировать задачу", color = SnsSilverText, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = editTaskTitle,
                            onValueChange = { editTaskTitle = it },
                            label = { Text("Краткое название задачи") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsRedAccent)
                        )
                        OutlinedTextField(
                            value = editTaskDesc,
                            onValueChange = { editTaskDesc = it },
                            label = { Text("Описание инцидента/задачи") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsRedAccent),
                            minLines = 3,
                            maxLines = 5
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDatePicker(
                                        context = context,
                                        initialTimeMillis = editTaskDateMillis,
                                        minTimeMillis = task.createdDateMillis - 86400000L,
                                        onDateSelected = { editTaskDateMillis = it }
                                    )
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, SnsBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Срок выполнения (выберите дату)",
                                        fontSize = 11.sp,
                                        color = SnsSecondaryText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatMillisToDate(editTaskDateMillis),
                                        fontSize = 15.sp,
                                        color = SnsSilverText,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Выбрать дату",
                                    tint = SnsRedAccent
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetCal = java.util.Calendar.getInstance().apply { timeInMillis = editTaskDateMillis }
                            targetCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            targetCal.set(java.util.Calendar.MINUTE, 0)
                            targetCal.set(java.util.Calendar.SECOND, 0)
                            targetCal.set(java.util.Calendar.MILLISECOND, 0)
                            
                            val createdCal = java.util.Calendar.getInstance().apply { timeInMillis = task.createdDateMillis }
                            createdCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            createdCal.set(java.util.Calendar.MINUTE, 0)
                            createdCal.set(java.util.Calendar.SECOND, 0)
                            createdCal.set(java.util.Calendar.MILLISECOND, 0)
                            
                            val diffMs = targetCal.timeInMillis - createdCal.timeInMillis
                            val calculatedDays = (diffMs / 86400000L).coerceAtLeast(0).toInt()
                            
                            if (editTaskTitle.isNotBlank()) {
                                viewModel.updateTask(
                                    task.copy(
                                        title = editTaskTitle.trim(),
                                        description = editTaskDesc.trim(),
                                        daysNeeded = calculatedDays
                                    )
                                )
                                editingTaskItem = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnsRedAccent)
                    ) {
                        Text("Сохранить", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingTaskItem = null }) {
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
    var expandedTaskIds by remember { mutableStateOf(emptySet<Int>()) }

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
                    val isExpanded = expandedTaskIds.contains(task.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedTaskIds = if (expandedTaskIds.contains(task.id)) {
                                    expandedTaskIds - task.id
                                } else {
                                    expandedTaskIds + task.id
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = BorderStroke(1.dp, cardBorderColor),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = SnsSilverText,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Закрыта: ${formatMillisToDate(task.completedDateMillis)}",
                                        fontSize = 12.sp,
                                        color = SnsSecondaryText
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Развернуть",
                                        tint = SnsSecondaryText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = SnsBorder.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = task.description.ifBlank { "Описание отсутствует" },
                                    fontSize = 12.sp,
                                    color = SnsSecondaryText
                                )

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
                                            text = "Срок был: ${if (task.daysNeeded == 0) "сегодня" else "${task.daysNeeded} дн."}",
                                            fontSize = 11.sp,
                                            color = SnsSecondaryText
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
}

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedDateMillis by remember { 
        mutableStateOf(System.currentTimeMillis() + 86400000L) // Default to tomorrow
    }
    
    val isReady = title.isNotBlank()
    val context = LocalContext.current
    val calendar = java.util.Calendar.getInstance()

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
                    modifier = Modifier.fillMaxWidth().testTag("add_task_desc"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsRedAccent),
                    minLines = 3,
                    maxLines = 5
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showDatePicker(
                                context = context,
                                initialTimeMillis = selectedDateMillis,
                                minTimeMillis = System.currentTimeMillis() - 86400000L,
                                onDateSelected = { selectedDateMillis = it }
                            )
                        },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, SnsBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Срок выполнения (выберите дату)",
                                fontSize = 11.sp,
                                color = SnsSecondaryText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatMillisToDate(selectedDateMillis),
                                fontSize = 15.sp,
                                color = SnsSilverText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Выбрать дату",
                            tint = SnsRedAccent
                        )
                    }
                }
                
                Text(
                    text = "* Дата постановки устанавливается автоматически. Срок будет рассчитан от сегодняшнего дня до выбранной даты.",
                    fontSize = 11.sp,
                    color = SnsSecondaryText
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetCal = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                    targetCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    targetCal.set(java.util.Calendar.MINUTE, 0)
                    targetCal.set(java.util.Calendar.SECOND, 0)
                    targetCal.set(java.util.Calendar.MILLISECOND, 0)
                    
                    val todayCal = java.util.Calendar.getInstance()
                    todayCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    todayCal.set(java.util.Calendar.MINUTE, 0)
                    todayCal.set(java.util.Calendar.SECOND, 0)
                    todayCal.set(java.util.Calendar.MILLISECOND, 0)
                    
                    val diffMs = targetCal.timeInMillis - todayCal.timeInMillis
                    val calculatedDays = (diffMs / 86400000L).coerceAtLeast(0).toInt()
                    
                    onConfirm(title.trim(), desc.trim(), calculatedDays)
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

// Helpers for positions parsing
fun getShortPosition(position: String): String {
    if (position.startsWith("[")) {
        val index = position.indexOf("]")
        if (index != -1) {
            return position.substring(1, index)
        }
    }
    return position.take(4).uppercase()
}

fun getFullPositionName(position: String): String {
    if (position.startsWith("[")) {
        val index = position.indexOf("]")
        if (index != -1) {
            return position.substring(index + 1).trim()
        }
    }
    return position
}

fun isEligibleForCartridge(positionStr: String): Boolean {
    val shortPos = getShortPosition(positionStr)
    return shortPos == "СВ" || shortPos == "ТП" || shortPos == "ЛТП" || shortPos == "УДФ ТП" || shortPos == "СТП"
}

fun matchEmployeeByNameOrFormatted(input: String, employees: List<com.example.data.Employee>): com.example.data.Employee? {
    val trimmed = input.trim()
    val firstMatch = employees.find { it.fullName.equals(trimmed, ignoreCase = true) }
    if (firstMatch != null) return firstMatch
    
    if (trimmed.startsWith("[")) {
        val closeBracket = trimmed.indexOf("]")
        if (closeBracket != -1) {
            val nameOnly = trimmed.substring(closeBracket + 1).trim()
            return employees.find { it.fullName.equals(nameOnly, ignoreCase = true) }
        }
    }
    return null
}

fun getShortPositionByRecipient(issueTo: String, employeeToShortPos: Map<String, String>): String {
    val trimmed = issueTo.trim()
    val direct = employeeToShortPos[trimmed]
    if (direct != null) return direct
    
    if (trimmed.startsWith("[")) {
        val closeBracket = trimmed.indexOf("]")
        if (closeBracket != -1) {
            val nameOnly = trimmed.substring(closeBracket + 1).trim()
            return employeeToShortPos[nameOnly] ?: getShortPosition(trimmed)
        }
    }
    return ""
}

// Reusable Autocomplete Employee text field component with built-in restriction support
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EmployeeAutocompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    employees: List<com.example.data.Employee>,
    modifier: Modifier = Modifier,
    restrictToCartridges: Boolean = false,
    focusedBorderColor: Color = SnsGreen
) {
    var expanded by remember { mutableStateOf(false) }
    
    // Suggest candidates matching typed prefix other than the focus rules
    val suggestions = remember(employees, value, restrictToCartridges) {
        val source = if (restrictToCartridges) {
            employees.filter { isEligibleForCartridge(it.position) }
        } else {
            employees
        }
        val query = if (value.isNotEmpty() && value.startsWith("[")) {
            val idx = value.indexOf("]")
            if (idx != -1) value.substring(idx + 1).trim() else value
        } else {
            value
        }
        if (query.isBlank()) {
            source
        } else {
            source.filter { it.fullName.contains(query, ignoreCase = true) }
        }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    expanded = focusState.isFocused
                }
                .clickable { expanded = true },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Показать сотрудников",
                        tint = focusedBorderColor
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = focusedBorderColor,
                cursorColor = focusedBorderColor
            )
        )

        androidx.compose.material3.DropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(SnsDarkBg)
        ) {
            suggestions.forEach { emp ->
                androidx.compose.material3.DropdownMenuItem(
                    onClick = {
                        onValueChange("[${getShortPosition(emp.position)}] ${emp.fullName}")
                        expanded = false
                    },
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = SnsGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = emp.fullName,
                                color = SnsSilverText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = getShortPosition(emp.position),
                                color = SnsSecondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                )
            }
        }
    }
}

// ==========================
// 5. Addresses Screen Section
// ==========================
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddressesScreen(
    viewModel: MainViewModel,
    requestConfirm: (String, String, () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val addresses by viewModel.addresses.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    val filteredAddresses = remember(addresses, searchQuery) {
        if (searchQuery.isBlank()) addresses
        else addresses.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.address.contains(searchQuery, ignoreCase = true) 
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<com.example.data.AddressItem?>(null) }
    
    var titleInput by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    titleInput = ""
                    addressInput = ""
                    editingItem = null
                    showAddDialog = true
                },
                containerColor = SnsGreen,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Добавить адрес")
            }
        }
    ) { padValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Поиск адреса...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = SnsSecondaryText)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Очистить", tint = SnsSecondaryText)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SnsGreen,
                    cursorColor = SnsGreen
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (filteredAddresses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = SnsSecondaryText.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Список адресов пуст" else "Ничего не найдено",
                            color = SnsSecondaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredAddresses) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openAddressInMaps(context, item.address)
                                },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, SnsBorder),
                            colors = CardDefaults.cardColors(containerColor = SnsCardBg)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Map avatar icon
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(SnsGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = SnsGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(14.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = SnsSilverText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.address,
                                        fontSize = 13.sp,
                                        color = SnsSecondaryText
                                    )
                                }
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            editingItem = item
                                            titleInput = item.name
                                            addressInput = item.address
                                            showAddDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Редактировать",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = {
                                            requestConfirm("УДАЛИТЬ", "Удалить адрес '${item.name}'?") {
                                                viewModel.deleteAddress(item)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Удалить",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = if (editingItem == null) "Добавить адрес" else "Редактировать адрес",
                    color = SnsSilverText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Наименование (например, Офис-1)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
                    )
                    
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        label = { Text("Адрес") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsGreen)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (titleInput.isNotBlank() && addressInput.isNotBlank()) {
                            val item = editingItem
                            if (item != null) {
                                viewModel.updateAddress(item.copy(name = titleInput, address = addressInput))
                            } else {
                                viewModel.addAddress(titleInput, addressInput)
                            }
                            showAddDialog = false
                        } else {
                            viewModel.showToast("Пожалуйста, заполните поля")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnsGreen)
                ) {
                    Text("Сохранить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Отмена", color = SnsSecondaryText)
                }
            },
            containerColor = SnsCardBg,
            textContentColor = SnsSilverText
        )
    }
}

fun openAddressInMaps(context: Context, address: String) {
    try {
        val encodedAddress = Uri.encode(address)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedAddress"))
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallbackWeb = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(address)}"))
            context.startActivity(fallbackWeb)
        } catch (e2: Exception) {
            Toast.makeText(context, "Не удалось запустить карты", Toast.LENGTH_SHORT).show()
        }
    }
}


// ==========================
// 6. Passwords Screen Section
// ==========================
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PasswordsScreen(
    viewModel: MainViewModel,
    requestConfirm: (String, String, () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val passwords by viewModel.passwords.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    val filteredPasswords = remember(passwords, searchQuery) {
        if (searchQuery.isBlank()) passwords
        else passwords.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            (it.comment != null && it.comment.contains(searchQuery, ignoreCase = true))
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<com.example.data.PasswordItem?>(null) }
    
    var nameInput by remember { mutableStateOf("") }
    var loginInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var commentInput by remember { mutableStateOf("") }

    // Track which item id is currently expanded!
    var expandedItemId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    nameInput = ""
                    loginInput = ""
                    passwordInput = ""
                    commentInput = ""
                    editingItem = null
                    showAddDialog = true
                },
                containerColor = SnsOrange,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Добавить пароль")
            }
        }
    ) { padValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Поиск паролей...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = SnsSecondaryText)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Очистить", tint = SnsSecondaryText)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SnsOrange,
                    cursorColor = SnsOrange
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (filteredPasswords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = SnsSecondaryText.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Список паролей пуст" else "Ничего не найдено",
                            color = SnsSecondaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredPasswords) { item ->
                        val isExpanded = expandedItemId == item.id
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedItemId = if (isExpanded) null else item.id
                                },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, SnsBorder),
                            colors = CardDefaults.cardColors(containerColor = SnsCardBg)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Lock avatar icon
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .background(SnsOrange.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.LockOpen else Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = SnsOrange,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(14.dp))
                                    
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = SnsSilverText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                editingItem = item
                                                nameInput = item.name
                                                loginInput = item.login
                                                passwordInput = item.password
                                                commentInput = item.comment ?: ""
                                                showAddDialog = true
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Редактировать",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        
                                        IconButton(
                                            onClick = {
                                                requestConfirm("УДАЛИТЬ", "Удалить пароль для '${item.name}'?") {
                                                    viewModel.deletePassword(item)
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Удалить",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                
                                // Expandable Details (Login and Password, comment)
                                if (isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(SnsDarkBg.copy(alpha = 0.3f))
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Login field
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("ЛОГИН", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SnsSecondaryText, fontFamily = FontFamily.Monospace)
                                                Text(item.login, fontSize = 15.sp, color = SnsSilverText, fontWeight = FontWeight.SemiBold)
                                            }
                                            IconButton(
                                                onClick = {
                                                    copyTextToClipboard(context, item.login)
                                                    viewModel.showToast("Логин скопирован")
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Копировать логин", tint = SnsOrange, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        
                                        // Password field with secure reveal and copy action
                                        var passwordVisible by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("ПАРОЛЬ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SnsSecondaryText, fontFamily = FontFamily.Monospace)
                                                Text(
                                                    text = if (passwordVisible) item.password else "•".repeat(item.password.length.coerceAtLeast(6)),
                                                    fontSize = 15.sp,
                                                    color = SnsSilverText,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontFamily = if (passwordVisible) FontFamily.Default else FontFamily.Monospace
                                                )
                                            }
                                            
                                            Row {
                                                IconButton(
                                                    onClick = { passwordVisible = !passwordVisible },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = "Переключить видимость",
                                                        tint = SnsSecondaryText,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                
                                                IconButton(
                                                    onClick = {
                                                        copyTextToClipboard(context, item.password)
                                                        viewModel.showToast("Пароль скопирован")
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Копировать пароль", tint = SnsOrange, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                        
                                        // Comment if present
                                        if (!item.comment.isNullOrBlank()) {
                                            HorizontalDivider(color = SnsBorder.copy(alpha = 0.4f))
                                            Column {
                                                Text("КОММЕНТАРИЙ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SnsSecondaryText, fontFamily = FontFamily.Monospace)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(item.comment, fontSize = 13.sp, color = SnsSilverText)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = if (editingItem == null) "Добавить пароль" else "Редактировать пароль",
                    color = SnsSilverText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Наименование (например, Сервер 1С)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsOrange)
                    )
                    
                    OutlinedTextField(
                        value = loginInput,
                        onValueChange = { loginInput = it },
                        label = { Text("Логин") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsOrange)
                    )

                    var pwdVisibleInCreate by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Пароль") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (pwdVisibleInCreate) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { pwdVisibleInCreate = !pwdVisibleInCreate }) {
                                Icon(
                                    imageVector = if (pwdVisibleInCreate) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = SnsSecondaryText
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsOrange)
                    )

                    OutlinedTextField(
                        value = commentInput,
                        onValueChange = { commentInput = it },
                        label = { Text("Комментарий (необязательно)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SnsOrange)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank() && loginInput.isNotBlank() && passwordInput.isNotBlank()) {
                            val item = editingItem
                            val cmt = commentInput.ifBlank { null }
                            if (item != null) {
                                viewModel.updatePassword(item.copy(name = nameInput, login = loginInput, password = passwordInput, comment = cmt))
                            } else {
                                viewModel.addPassword(nameInput, loginInput, passwordInput, cmt)
                            }
                            showAddDialog = false
                        } else {
                            viewModel.showToast("Пожалуйста, заполните обязательные поля")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnsOrange)
                ) {
                    Text("Сохранить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Отмена", color = SnsSecondaryText)
                }
            },
            containerColor = SnsCardBg,
            textContentColor = SnsSilverText
        )
    }
}

private fun copyTextToClipboard(context: Context, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SNS IT Tool", text)
        clipboard.setPrimaryClip(clip)
    } catch (e: Exception) {
        // Safe wrap
    }
}

fun showDatePicker(
    context: android.content.Context,
    initialTimeMillis: Long,
    minTimeMillis: Long?,
    onDateSelected: (Long) -> Unit
) {
    try {
        val activity = context.findActivity()
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = initialTimeMillis }
        val rYear = calendar.get(java.util.Calendar.YEAR)
        val rMonth = calendar.get(java.util.Calendar.MONTH)
        val rDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)

        val dpd = android.app.DatePickerDialog(
            activity ?: context,
            { _, year, month, dayOfMonth ->
                val targetCal = java.util.Calendar.getInstance()
                targetCal.set(year, month, dayOfMonth, 0, 0, 0)
                onDateSelected(targetCal.timeInMillis)
            },
            rYear,
            rMonth,
            rDay
        )
        if (minTimeMillis != null) {
            dpd.datePicker.minDate = minTimeMillis
        }
        dpd.show()
    } catch (e: Exception) {
        e.printStackTrace()
        try {
            android.widget.Toast.makeText(context, "Не удалось открыть выбор даты", android.widget.Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            // Suppress fallback errors
        }
    }
}

fun android.content.Context.findActivity(): android.app.Activity? {
    var currentContext = this
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is android.app.Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

