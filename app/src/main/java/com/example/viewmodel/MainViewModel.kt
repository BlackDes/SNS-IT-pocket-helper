package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CartridgeIssuance
import com.example.data.Employee
import com.example.data.EquipmentItem
import com.example.data.InstructionNote
import com.example.data.NewCartridge
import com.example.data.SnsRepository
import com.example.data.TaskItem
import com.example.data.UsedCartridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: SnsRepository

    // Native database data streams
    val newCartridges: StateFlow<List<NewCartridge>>
    val cartridgeIssuances: StateFlow<List<CartridgeIssuance>>
    val usedCartridges: StateFlow<List<UsedCartridge>>
    val equipmentItems: StateFlow<List<EquipmentItem>>
    val employees: StateFlow<List<Employee>>
    val instructions: StateFlow<List<InstructionNote>>
    val tasks: StateFlow<List<TaskItem>>

    // Sorting states for Equipment
    enum class EquipmentSortBy { NAME, SERIAL, INVENTORY, DATE, ASSIGNEE }
    val equipmentSortBy = MutableStateFlow(EquipmentSortBy.NAME)
    val equipmentSortAsc = MutableStateFlow(true)

    // Derived Equipment List (Sorted based on sort states)
    val sortedEquipmentItems: StateFlow<List<EquipmentItem>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = SnsRepository(database)

        newCartridges = repository.newCartridges.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        cartridgeIssuances = repository.cartridgeIssuances.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        usedCartridges = repository.usedCartridges.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        equipmentItems = repository.equipmentItems.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        employees = repository.employees.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        instructions = repository.instructions.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        tasks = repository.tasks.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )


        // Setup sorted items stream
        sortedEquipmentItems = combine(
            equipmentItems, equipmentSortBy, equipmentSortAsc
        ) { list, sortBy, asc ->
            val sorted = when (sortBy) {
                EquipmentSortBy.NAME -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                EquipmentSortBy.SERIAL -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.serialNumber })
                EquipmentSortBy.INVENTORY -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.inventoryNumber })
                EquipmentSortBy.DATE -> list.sortedBy { parseDate(it.purchaseDate) }
                EquipmentSortBy.ASSIGNEE -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.assignee })
            }
            if (asc) sorted else sorted.reversed()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Pre-populate data on first load
        viewModelScope.launch {
            prepopulateIfEmpty()
        }
    }

    // Helper to parse dd.MM.yyyy date to Long for sorting
    private fun parseDate(dateStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.US)
            val date = sdf.parse(dateStr)
            date?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    // Date Format utilities
    fun getTodayString(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.US)
        return sdf.format(Date())
    }

    // Checking if a date is older than 30 days compared to current date (2026-05-26)
    fun isOlderThan30Days(dateStr: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.US)
            val date = sdf.parse(dateStr) ?: return false
            
            // In Android sandbox, time is set to 26.05.2026.
            // Let's compare difference in milliseconds
            val today = Calendar.getInstance()
            // Setting explicitly to May 26th, 2026 to stay accurate with local dev metadata,
            // or we can use current live system time as fallback.
            // We'll set the reference calendar to May 26th, 2026
            val referenceDate = Calendar.getInstance().apply {
                set(2026, Calendar.MAY, 26, 12, 0, 0)
            }
            
            val diffInMillis = referenceDate.timeInMillis - date.time
            val diffInDays = diffInMillis / (1000 * 60 * 60 * 24)
            diffInDays > 30
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun prepopulateIfEmpty() {
        val empList = repository.employees.first()
        if (empList.isEmpty()) {
            // Populate Employees
            val list = listOf(
                Employee(fullName = "Сидоров Иван Петрович", position = "Системный администратор", phoneNumber = "+7 (999) 111-22-33"),
                Employee(fullName = "Козлов Дмитрий Сергеевич", position = "Ведущий инженер ИТ", phoneNumber = "+7 (999) 222-33-44"),
                Employee(fullName = "Иванова Анна Владимировна", position = "Специалист техподдержки", phoneNumber = "+7 (999) 333-44-55"),
                Employee(fullName = "Сергеев Алексей Юрьевич", position = "Сетевой администратор", phoneNumber = "+7 (999) 444-55-66")
            )
            list.forEach { repository.addEmployee(it) }

            // Populate New Cartridges stock additions
            val cartList = listOf(
                NewCartridge(name = "HP LaserJet 85A", quantityAdded = 15, dateAdded = "20.05.2026"),
                NewCartridge(name = "Canon 725 Starter", quantityAdded = 12, dateAdded = "15.05.2026"),
                NewCartridge(name = "Samsung MLT-D101S", quantityAdded = 8, dateAdded = "10.05.2026")
            )
            cartList.forEach { repository.addNewCartridge(it) }

            // Populate Cartridge Issuances
            val issueList = listOf(
                CartridgeIssuance(cartridgeName = "HP LaserJet 85A", isUsed = false, quantity = 2, issueTo = "Сидоров Иван Петрович", issueDate = "22.05.2026"),
                CartridgeIssuance(cartridgeName = "Canon 725 Starter", isUsed = true, quantity = 1, issueTo = "Иванова Анна Владимировна", issueDate = "10.04.2026"),
                CartridgeIssuance(cartridgeName = "Samsung MLT-D101S", isUsed = false, quantity = 1, issueTo = "Козлов Дмитрий Сергеевич", issueDate = "28.04.2026")
            )
            issueList.forEach { repository.addIssuance(it) }

            // Populate Used Cartridges (БУ)
            val usedList = listOf(
                UsedCartridge(inventoryNumber = "INV-2026-0001", dateAdded = "26.05.2026", dateStatusChanged = "26.05.2026", status = "Ремонт", issueTo = null),
                UsedCartridge(inventoryNumber = "INV-2026-0002", dateAdded = "15.05.2026", dateStatusChanged = "20.05.2026", status = "Выдан", issueTo = "Сергеев Алексей Юрьевич"),
                UsedCartridge(inventoryNumber = "INV-2026-0003", dateAdded = "01.05.2026", dateStatusChanged = "10.05.2026", status = "Умер", issueTo = null)
            )
            usedList.forEach { repository.addUsedCartridge(it) }

            // Populate Equipment
            val equipList = listOf(
                EquipmentItem(name = "Ноутбук ThinkPad T14", serialNumber = "SPF50W291", inventoryNumber = "EQ-10901", purchaseDate = "10.01.2025", assignee = "Сергеев Алексей Юрьевич"),
                EquipmentItem(name = "Принтер HP LaserJet Pro M402", serialNumber = "CNB2K019", inventoryNumber = "EQ-20891", purchaseDate = "20.12.2024", assignee = "Козлов Дмитрий Сергеевич"),
                EquipmentItem(name = "Монитор Dell 24 U2419H", serialNumber = "MX05C392", inventoryNumber = "EQ-30291", purchaseDate = "15.06.2024", assignee = "Иванова Анна Владимировна"),
                EquipmentItem(name = "Сервер Supermicro 2U", serialNumber = "SMX201928", inventoryNumber = "EQ-90101", purchaseDate = "05.02.2026", assignee = "Сидоров Иван Петрович")
            )
            equipList.forEach { repository.addEquipment(it) }

            // Populate Instructions / Notebook
            val instList = listOf(
                InstructionNote(
                    title = "Сброс пароля BIOS ThinkPad",
                    content = "Для сброса пароля BIOS на материнских платах Thinkpad T-серии необходимо замкнуть контакты EEPROM SDA и SCL сразу после включения системы до его инициализации. Рекомендуется отключать батарейку CMOS на 5 минут перед этой процедурой.",
                    imageUris = ""
                ),
                InstructionNote(
                    title = "Удаление ошибки 49.XXXX LaserJet",
                    content = "При возникновении критической ошибки 49 на принтерах HP LaserJet рекомендуется:\n1. Отключить сетевой шнур принтера.\n2. Перезапустить очередь печати (Print Spooler) на принт-сервере Windows.\n3. Временно отключить протокол IPv6 в веб-интерфейсе.",
                    imageUris = ""
                )
            )
            instList.forEach { repository.addInstruction(it) }

            // Populate Tasks
            val taskList = listOf(
                TaskItem(
                    title = "Установка антивируса в бухгалтерии",
                    description = "Установить антивирус Касперского на компьютеры главного бухгалтера и кассира.",
                    daysNeeded = 1,
                    createdDateMillis = System.currentTimeMillis() - 3 * 24 * 3600 * 1000L,
                    isCompleted = true,
                    completedDateMillis = System.currentTimeMillis() - 1 * 24 * 3600 * 1000L,
                    solution = "Антивирус установлен, базы обновлены. Все ОК."
                ),
                TaskItem(
                    title = "Настройка принтера HP M402 в отделе кадров",
                    description = "Повторно подключить сетевой принтер в кабинете 215, настроить двустороннюю печать по умолчанию.",
                    daysNeeded = 2,
                    createdDateMillis = System.currentTimeMillis() - 5 * 24 * 3600 * 1000L,
                    isCompleted = false
                ),
                TaskItem(
                    title = "Обновление ПО на сервере бэкапов",
                    description = "Провести резервное копирование ОС и установить накопительные обновления Windows Server.",
                    daysNeeded = 0,
                    createdDateMillis = System.currentTimeMillis()
                )
            )
            taskList.forEach { repository.addTask(it) }
        }
    }


    // ===================================
    // DB Mutations / Direct Operations
    // ===================================

    // Cartridges Mutations
    fun addNewCartridgeStock(name: String, quantity: Int) {
        viewModelScope.launch {
            repository.addNewCartridge(
                NewCartridge(name = name, quantityAdded = quantity, dateAdded = getTodayString())
            )
        }
    }

    fun issueNewCartridge(name: String, quantity: Int, toPerson: String) {
        viewModelScope.launch {
            repository.addIssuance(
                CartridgeIssuance(
                    cartridgeName = name,
                    isUsed = false,
                    quantity = quantity,
                    issueTo = toPerson,
                    issueDate = getTodayString()
                )
            )
        }
    }

    fun addUsedCartridgeForRepair(invNum: String) {
        viewModelScope.launch {
            repository.addUsedCartridge(
                UsedCartridge(
                    inventoryNumber = invNum,
                    dateAdded = getTodayString(),
                    dateStatusChanged = getTodayString(),
                    status = "Ремонт",
                    issueTo = null
                )
            )
        }
    }

    fun updateUsedCartridgeStatus(cartridge: UsedCartridge, newStatus: String, issueTo: String? = null) {
        viewModelScope.launch {
            repository.updateUsedCartridge(
                cartridge.copy(
                    status = newStatus,
                    dateStatusChanged = getTodayString(),
                    issueTo = issueTo
                )
            )
            if (newStatus == "Выдан" && issueTo != null) {
                // Record issuance history too for completeness
                repository.addIssuance(
                    CartridgeIssuance(
                        cartridgeName = "БУ (Инв: ${cartridge.inventoryNumber})",
                        isUsed = true,
                        quantity = 1,
                        issueTo = issueTo,
                        issueDate = getTodayString()
                    )
                )
            }
        }
    }

    fun deleteUsedCartridge(cartridge: UsedCartridge) {
        viewModelScope.launch {
            repository.deleteUsedCartridge(cartridge.id)
        }
    }

    // Equipment Mutations
    fun addEquipment(name: String, serial: String, invNum: String, purchaseDate: String, assignee: String) {
        viewModelScope.launch {
            repository.addEquipment(
                EquipmentItem(
                    name = name,
                    serialNumber = serial,
                    inventoryNumber = invNum,
                    purchaseDate = purchaseDate,
                    assignee = assignee
                )
            )
        }
    }

    fun updateEquipment(item: EquipmentItem) {
        viewModelScope.launch {
            repository.updateEquipment(item)
        }
    }

    fun deleteEquipment(item: EquipmentItem) {
        viewModelScope.launch {
            repository.deleteEquipment(item.id)
        }
    }

    // Employee Mutations
    fun addEmployee(name: String, position: String, phone: String) {
        viewModelScope.launch {
            repository.addEmployee(
                Employee(fullName = name, position = position, phoneNumber = phone)
            )
        }
    }

    fun updateEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.updateEmployee(employee)
        }
    }

    fun deleteEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.deleteEmployee(employee.id)
        }
    }

    // Instruction Mutations
    fun addInstruction(title: String, content: String, imageUris: String = "", localPhotosJson: String = "") {
        viewModelScope.launch {
            repository.addInstruction(
                InstructionNote(title = title, content = content, imageUris = imageUris, localPhotosJson = localPhotosJson)
            )
        }
    }

    fun updateInstruction(note: InstructionNote) {
        viewModelScope.launch {
            repository.updateInstruction(note)
        }
    }

    fun deleteInstruction(note: InstructionNote) {
        viewModelScope.launch {
            repository.deleteInstruction(note.id)
        }
    }

    // Task Mutations
    fun addTask(title: String, description: String, daysNeeded: Int) {
        viewModelScope.launch {
            repository.addTask(
                TaskItem(
                    title = title,
                    description = description,
                    daysNeeded = daysNeeded,
                    createdDateMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun completeTask(task: TaskItem, solution: String) {
        viewModelScope.launch {
            repository.updateTask(
                task.copy(
                    isCompleted = true,
                    completedDateMillis = System.currentTimeMillis(),
                    solution = solution
                )
            )
        }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            repository.deleteTask(task.id)
        }
    }

}
