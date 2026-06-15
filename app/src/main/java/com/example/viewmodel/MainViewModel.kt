package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CartridgeIssuance
import com.example.data.CartridgeType
import com.example.data.Employee
import com.example.data.EquipmentItem
import com.example.data.CustomPosition
import com.example.data.InstructionNote
import com.example.data.NewCartridge
import com.example.data.SnsRepository
import com.example.data.TaskItem
import com.example.data.UsedCartridge
import com.example.data.AddressItem
import com.example.data.PasswordItem
import com.example.data.BoardItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    // Dynamic Toast Notifications
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    fun showToast(msg: String) {
        viewModelScope.launch {
            _toastMessage.emit(msg)
        }
    }

    // Native database data streams
    val newCartridges: StateFlow<List<NewCartridge>>
    val cartridgeIssuances: StateFlow<List<CartridgeIssuance>>
    val usedCartridges: StateFlow<List<UsedCartridge>>
    val cartridgeTypes: StateFlow<List<CartridgeType>>
    val equipmentItems: StateFlow<List<EquipmentItem>>
    val employees: StateFlow<List<Employee>>
    val customPositions: StateFlow<List<CustomPosition>>
    val boards: StateFlow<List<BoardItem>>
    val instructions: StateFlow<List<InstructionNote>>
    val tasks: StateFlow<List<TaskItem>>
    val addresses: StateFlow<List<AddressItem>>
    val passwords: StateFlow<List<PasswordItem>>

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
        cartridgeTypes = repository.cartridgeTypes.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        equipmentItems = repository.equipmentItems.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        employees = repository.employees.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        customPositions = repository.customPositions.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        boards = repository.boards.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        instructions = repository.instructions.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        tasks = repository.tasks.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        addresses = repository.addresses.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        passwords = repository.passwords.stateIn(
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

        viewModelScope.launch {
            tasks.collect { list ->
                checkTasksForNotifications(list)
            }
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
                Employee(fullName = "Сидоров Иван Петрович", position = "[СВ] Супервайзер", phoneNumber = "+7 (999) 111-22-33", socialMediaLabel = "Telegram", socialMediaLink = "@sidorov_sv"),
                Employee(fullName = "Козлов Дмитрий Сергеевич", position = "[ТП] Торговый представитель", phoneNumber = "+7 (999) 222-33-44", socialMediaLabel = "VK", socialMediaLink = "@gymbober"),
                Employee(fullName = "Иванова Анна Владимировна", position = "[БК] Бухгалтер-кассир", phoneNumber = "+7 (999) 333-44-55"),
                Employee(fullName = "Сергеев Алексей Юрьевич", position = "[ЗС] Заведующий складом филиала", phoneNumber = "+7 (999) 444-55-66")
            )
            list.forEach { repository.addEmployee(it) }

            // Populate Custom Positions
            val positionList = listOf(
                "[ГДФ] Генеральный директор филиала",
                "[ГБФ] Главный бухгалтер филиала",
                "[БК] Бухгалтер-кассир",
                "[БО] Бухгалтер-операционист",
                "[СФ] Секретарь филиала",
                "[СВ] Супервайзер",
                "[ЗС] Заведующий складом филиала",
                "[ТП] Торговый представитель"
            )
            positionList.forEach { repository.addCustomPosition(CustomPosition(name = it)) }

            // Populate Cartridge Types
            val typeList = listOf(
                CartridgeType(name = "[HP651 ЧБ] Принтер HP202"),
                CartridgeType(name = "HP LaserJet 85A"),
                CartridgeType(name = "Canon 725 Starter"),
                CartridgeType(name = "Samsung MLT-D101S")
            )
            typeList.forEach { repository.addCartridgeType(it) }

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
    fun addNewCartridgeStock(name: String, quantity: Int, customDate: String? = null) {
        viewModelScope.launch {
            repository.addNewCartridge(
                NewCartridge(name = name, quantityAdded = quantity, dateAdded = if (!customDate.isNullOrBlank()) customDate else getTodayString())
            )
            showToast("Успешно зачислено поступление: $name ($quantity шт)")
        }
    }

    fun issueNewCartridge(name: String, quantity: Int, toPerson: String, customDate: String? = null) {
        viewModelScope.launch {
            repository.addIssuance(
                CartridgeIssuance(
                    cartridgeName = name,
                    isUsed = false,
                    quantity = quantity,
                    issueTo = toPerson,
                    issueDate = if (!customDate.isNullOrBlank()) customDate else getTodayString()
                )
            )
            showToast("Выдан новый картридж: $name борту $toPerson")
        }
    }

    fun updateCartridgeIssuance(issuance: CartridgeIssuance) {
        viewModelScope.launch {
            repository.updateIssuance(issuance)
            showToast("Выдача картриджа успешно обновлена")
        }
    }

    fun addUsedCartridgeForRepair(invNum: String, customDate: String? = null) {
        viewModelScope.launch {
            val finalDate = if (!customDate.isNullOrBlank()) customDate else getTodayString()
            repository.addUsedCartridge(
                UsedCartridge(
                    inventoryNumber = invNum,
                    dateAdded = finalDate,
                    dateStatusChanged = finalDate,
                    status = "Ремонт",
                    issueTo = null
                )
            )
            showToast("Картридж с инв. номером $invNum отправлен в ремонт")
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
                showToast("Выдан БУ картридж ${cartridge.inventoryNumber} борту $issueTo")
            } else {
                showToast("Статус БУ картриджа ${cartridge.inventoryNumber} изменен на: $newStatus")
            }
        }
    }

    fun deleteUsedCartridge(cartridge: UsedCartridge) {
        viewModelScope.launch {
            repository.deleteUsedCartridge(cartridge.id)
            showToast("БУ картридж ${cartridge.inventoryNumber} удален из системы")
        }
    }

    // Equipment Mutations
    fun addEquipment(name: String, serial: String, invNum: String, purchaseDate: String, assignee: String, comment: String = "") {
        viewModelScope.launch {
            repository.addEquipment(
                EquipmentItem(
                    name = name,
                    serialNumber = serial,
                    inventoryNumber = invNum,
                    purchaseDate = purchaseDate,
                    assignee = assignee,
                    comment = comment
                )
            )
            showToast("Оборудование $name закреплено за $assignee")
        }
    }

    fun updateEquipment(item: EquipmentItem) {
        viewModelScope.launch {
            repository.updateEquipment(item)
            showToast("Запись об оборудовании ${item.name} обновлена")
        }
    }

    fun deleteEquipment(item: EquipmentItem) {
        viewModelScope.launch {
            repository.deleteEquipment(item.id)
            showToast("Оборудование ${item.name} списано/удалено")
        }
    }

    // Employee Mutations
    fun addEmployee(
        name: String, 
        position: String, 
        phone: String, 
        secondPhone: String = "", 
        socialLabel: String? = null, 
        socialLink: String? = null,
        boardNumber: String = "",
        isStpInactive: Boolean = false
    ) {
        viewModelScope.launch {
            repository.addEmployee(
                Employee(
                    fullName = name, 
                    position = position, 
                    phoneNumber = phone, 
                    secondPhoneNumber = secondPhone, 
                    socialMediaLabel = socialLabel, 
                    socialMediaLink = socialLink,
                    boardNumber = boardNumber,
                    isStpInactive = isStpInactive
                )
            )
            showToast("Сотрудник $name сохранен в базу")
        }
    }

    fun addCustomPosition(name: String) {
        viewModelScope.launch {
            repository.addCustomPosition(CustomPosition(name = name))
            showToast("Добавлена должность: $name")
        }
    }

    private fun getShortPosition(position: String): String {
        if (position.startsWith("[")) {
            val index = position.indexOf("]")
            if (index != -1) {
                return position.substring(1, index)
            }
        }
        return position.take(4).uppercase()
    }

    fun updateCustomPosition(pos: CustomPosition) {
        viewModelScope.launch {
            val oldPos = customPositions.value.find { it.id == pos.id }
            val oldName = oldPos?.name ?: ""
            
            repository.updateCustomPosition(pos)
            
            if (oldName.isNotEmpty() && oldName != pos.name) {
                val oldShort = getShortPosition(oldName)
                val newShort = getShortPosition(pos.name)
                
                val currentEmployees = employees.value
                currentEmployees.forEach { employee ->
                    if (employee.position == oldName) {
                        var newFullName = employee.fullName
                        if (oldShort.isNotEmpty() && newShort.isNotEmpty() && oldShort != newShort) {
                            if (employee.fullName.startsWith("$oldShort V")) {
                                newFullName = "$newShort V" + employee.fullName.substring(oldShort.length + 2)
                            } else if (employee.fullName.startsWith("$oldShort ")) {
                                newFullName = "$newShort " + employee.fullName.substring(oldShort.length + 1)
                            } else if (employee.fullName == oldShort) {
                                newFullName = newShort
                            }
                        }
                        repository.updateEmployee(
                            employee.copy(
                                position = pos.name,
                                fullName = newFullName
                            )
                        )
                    }
                }
            }
            showToast("Должность изменена")
        }
    }

    fun deleteCustomPosition(pos: CustomPosition) {
        viewModelScope.launch {
            repository.deleteCustomPosition(pos.id)
            showToast("Должность удалена")
        }
    }

    fun updateEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.updateEmployee(employee)
            showToast("Инфо о сотруднике ${employee.fullName} изменено")
        }
    }

    fun deleteEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.deleteEmployee(employee.id)
            showToast("Сотрудник ${employee.fullName} удален из списка")
        }
    }

    // Board Mutations
    fun addBoard(boardNumber: String) {
        viewModelScope.launch {
            val trimmed = boardNumber.trim()
            val formatted = if (trimmed.uppercase().startsWith("V")) trimmed.uppercase() else "V$trimmed"
            repository.addBoard(BoardItem(boardNumber = formatted))
            showToast("Добавлен борт: $formatted")
        }
    }

    fun updateBoard(board: BoardItem) {
        viewModelScope.launch {
            repository.updateBoard(board)
            showToast("Борт обновлен: ${board.boardNumber}")
        }
    }

    fun deleteBoard(board: BoardItem) {
        viewModelScope.launch {
            repository.deleteBoard(board.id)
            showToast("Борт ${board.boardNumber} удален")
        }
    }

    // Cartridge Type Mutations
    fun addCartridgeType(name: String, description: String = "") {
        viewModelScope.launch {
            repository.addCartridgeType(CartridgeType(name = name, description = description))
            showToast("Добавлен вид картриджа: $name")
        }
    }

    fun updateCartridgeType(cartridgeType: CartridgeType) {
        viewModelScope.launch {
            repository.updateCartridgeType(cartridgeType)
            showToast("Обновлен вид картриджа: ${cartridgeType.name}")
        }
    }

    fun deleteCartridgeType(cartridgeType: CartridgeType) {
        viewModelScope.launch {
            repository.deleteNewCartridgesByName(cartridgeType.name)
            repository.deleteIssuancesByCartridgeName(cartridgeType.name)
            repository.deleteCartridgeType(cartridgeType.id)
            showToast("Удален вид картриджа: ${cartridgeType.name} и все связанные записи")
        }
    }

    // Instruction Mutations
    fun addInstruction(title: String, content: String, imageUris: String = "", localPhotosJson: String = "", allowCopy: Boolean = false) {
        viewModelScope.launch {
            repository.addInstruction(
                InstructionNote(title = title, content = content, imageUris = imageUris, localPhotosJson = localPhotosJson, allowCopy = allowCopy)
            )
            showToast("Заметка '$title' успешно создана")
        }
    }

    fun updateInstruction(note: InstructionNote) {
        viewModelScope.launch {
            repository.updateInstruction(note)
            showToast("Заметка '${note.title}' обновлена")
        }
    }

    fun deleteInstruction(note: InstructionNote) {
        viewModelScope.launch {
            repository.deleteInstruction(note.id)
            showToast("Заметка '${note.title}' удалена")
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
            showToast("Задача '$title' добавлена")
        }
    }

    fun updateTask(task: TaskItem) {
        viewModelScope.launch {
            repository.updateTask(task)
            showToast("Задача '${task.title}' обновлена")
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
            showToast("Задача '${task.title}' выполнена!")
        }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            repository.deleteTask(task.id)
            showToast("Задача '${task.title}' удалена")
        }
    }

    // Address Mutations
    fun addAddress(name: String, address: String) {
        viewModelScope.launch {
            repository.addAddress(com.example.data.AddressItem(name = name, address = address))
            showToast("Адрес '$name' успешно добавлен")
        }
    }

    fun updateAddress(item: com.example.data.AddressItem) {
        viewModelScope.launch {
            repository.updateAddress(item)
            showToast("Адрес '${item.name}' обновлен")
        }
    }

    fun deleteAddress(item: com.example.data.AddressItem) {
        viewModelScope.launch {
            repository.deleteAddress(item.id)
            showToast("Адрес '${item.name}' удален")
        }
    }

    // Password Mutations
    fun addPassword(name: String, login: String, pass: String, comment: String? = null) {
        viewModelScope.launch {
            repository.addPassword(com.example.data.PasswordItem(name = name, login = login, password = pass, comment = comment))
            showToast("Пароль '$name' успешно добавлен")
        }
    }

    fun updatePassword(item: com.example.data.PasswordItem) {
        viewModelScope.launch {
            repository.updatePassword(item)
            showToast("Пароль '${item.name}' обновлен")
        }
    }

    fun deletePassword(item: com.example.data.PasswordItem) {
        viewModelScope.launch {
            repository.deletePassword(item.id)
            showToast("Пароль '${item.name}' удален")
        }
    }

    // ===================================
    // DB Backup Export & Import (JSON)
    // ===================================

    fun exportDatabase(context: Context, onComplete: (Uri?) -> Unit) {
        viewModelScope.launch {
            try {
                // Fetch current snapshots of all tables
                val newCarts = repository.newCartridges.first()
                val issuances = repository.cartridgeIssuances.first()
                val usedCarts = repository.usedCartridges.first()
                val types = repository.cartridgeTypes.first()
                val equipment = repository.equipmentItems.first()
                val emps = repository.employees.first()
                val notes = repository.instructions.first()
                val taskList = repository.tasks.first()
                val addrs = repository.addresses.first()
                val pwds = repository.passwords.first()

                val rootJson = org.json.JSONObject()

                // 1. new_cartridges
                val newCartsArray = org.json.JSONArray()
                newCarts.forEach { item ->
                    val obj = org.json.JSONObject()
                    obj.put("name", item.name)
                    obj.put("quantityAdded", item.quantityAdded)
                    obj.put("dateAdded", item.dateAdded)
                    newCartsArray.put(obj)
                }
                rootJson.put("new_cartridges", newCartsArray)

                // 2. cartridge_issuances
                val issuancesArray = org.json.JSONArray()
                issuances.forEach { item ->
                    val obj = org.json.JSONObject()
                    obj.put("cartridgeName", item.cartridgeName)
                    obj.put("isUsed", item.isUsed)
                    obj.put("quantity", item.quantity)
                    obj.put("issueTo", item.issueTo)
                    obj.put("issueDate", item.issueDate)
                    issuancesArray.put(obj)
                }
                rootJson.put("cartridge_issuances", issuancesArray)

                // 3. used_cartridges
                val usedCartsArray = org.json.JSONArray()
                usedCarts.forEach { item ->
                    val obj = org.json.JSONObject()
                    obj.put("inventoryNumber", item.inventoryNumber)
                    obj.put("dateAdded", item.dateAdded)
                    obj.put("dateStatusChanged", item.dateStatusChanged)
                    obj.put("status", item.status)
                    obj.put("issueTo", item.issueTo ?: org.json.JSONObject.NULL)
                    usedCartsArray.put(obj)
                }
                rootJson.put("used_cartridges", usedCartsArray)

                // 4. cartridge_types
                val typesArray = org.json.JSONArray()
                types.forEach { item ->
                    val obj = org.json.JSONObject()
                    obj.put("name", item.name)
                    obj.put("description", item.description)
                    typesArray.put(obj)
                }
                rootJson.put("cartridge_types", typesArray)

                // 5. equipment
                val equipArray = org.json.JSONArray()
                equipment.forEach { item ->
                    val obj = org.json.JSONObject()
                    obj.put("name", item.name)
                    obj.put("serialNumber", item.serialNumber)
                    obj.put("inventoryNumber", item.inventoryNumber)
                    obj.put("purchaseDate", item.purchaseDate)
                    obj.put("assignee", item.assignee)
                    equipArray.put(obj)
                }
                rootJson.put("equipment", equipArray)

                // 6. employees
                val empsArray = org.json.JSONArray()
                emps.forEach { item ->
                    val obj = org.json.JSONObject()
                    obj.put("fullName", item.fullName)
                    obj.put("position", item.position)
                    obj.put("phoneNumber", item.phoneNumber)
                    obj.put("secondPhoneNumber", item.secondPhoneNumber)
                    obj.put("socialMediaLabel", item.socialMediaLabel ?: org.json.JSONObject.NULL)
                    obj.put("socialMediaLink", item.socialMediaLink ?: org.json.JSONObject.NULL)
                    empsArray.put(obj)
                }
                rootJson.put("employees", empsArray)

                // 7. instructions
                val notesArray = org.json.JSONArray()
                notes.forEach { item ->
                    val obj = org.json.JSONObject()
                    obj.put("title", item.title)
                    obj.put("content", item.content)
                    obj.put("imageUris", item.imageUris)
                    obj.put("localPhotosJson", item.localPhotosJson)
                    notesArray.put(obj)
                }
                rootJson.put("instructions", notesArray)

                // 8. tasks
                val tasksArray = org.json.JSONArray()
                taskList.forEach { item ->
                    val obj = org.json.JSONObject()
                    obj.put("title", item.title)
                    obj.put("description", item.description)
                    obj.put("daysNeeded", item.daysNeeded)
                    obj.put("createdDateMillis", item.createdDateMillis)
                    obj.put("isCompleted", item.isCompleted)
                    obj.put("completedDateMillis", item.completedDateMillis)
                    obj.put("solution", item.solution)
                    tasksArray.put(obj)
                }
                rootJson.put("tasks", tasksArray)

                // 9. addresses
                val addrsArray = org.json.JSONArray()
                addrs.forEach { item ->
                    val obj = org.json.JSONObject()
                    obj.put("name", item.name)
                    obj.put("address", item.address)
                    addrsArray.put(obj)
                }
                rootJson.put("addresses", addrsArray)

                // 10. passwords
                val pwdsArray = org.json.JSONArray()
                pwds.forEach { item ->
                    val obj = org.json.JSONObject()
                    obj.put("name", item.name)
                    obj.put("login", item.login)
                    obj.put("password", item.password)
                    obj.put("comment", item.comment ?: org.json.JSONObject.NULL)
                    pwdsArray.put(obj)
                }
                rootJson.put("passwords", pwdsArray)

                // Save JSON to temporary file in cache directory
                val outputString = rootJson.toString(4)
                val cacheFile = java.io.File(context.cacheDir, "sns_it_backup.json")
                cacheFile.writeText(outputString, Charsets.UTF_8)

                // Create content/file provider URI
                val authority = "com.example.fileprovider"
                val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, cacheFile)
                onComplete(uri)
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Ошибка при экспорте: ${e.message}")
                onComplete(null)
            }
        }
    }

    fun importDatabase(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                val jsonText = inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                
                if (jsonText.isNullOrEmpty()) {
                    onResult(false, "Выбран пустой файл или не удалось его прочесть")
                    return@launch
                }

                val rootJson = org.json.JSONObject(jsonText)

                // 1. new_cartridges
                val newCartsArray = rootJson.optJSONArray("new_cartridges")
                if (newCartsArray != null) {
                    val currentList = repository.newCartridges.first()
                    currentList.forEach { repository.deleteNewCartridge(it.id) }
                    for (i in 0 until newCartsArray.length()) {
                        val obj = newCartsArray.getJSONObject(i)
                        repository.addNewCartridge(
                            NewCartridge(
                                name = obj.getString("name"),
                                quantityAdded = obj.getInt("quantityAdded"),
                                dateAdded = obj.getString("dateAdded")
                            )
                        )
                    }
                }

                // 2. cartridge_issuances
                val issuancesArray = rootJson.optJSONArray("cartridge_issuances")
                if (issuancesArray != null) {
                    val currentList = repository.cartridgeIssuances.first()
                    currentList.forEach { repository.deleteIssuance(it.id) }
                    for (i in 0 until issuancesArray.length()) {
                        val obj = issuancesArray.getJSONObject(i)
                        repository.addIssuance(
                            CartridgeIssuance(
                                cartridgeName = obj.getString("cartridgeName"),
                                isUsed = obj.getBoolean("isUsed"),
                                quantity = obj.getInt("quantity"),
                                issueTo = obj.getString("issueTo"),
                                issueDate = obj.getString("issueDate")
                            )
                        )
                    }
                }

                // 3. used_cartridges
                val usedCartsArray = rootJson.optJSONArray("used_cartridges")
                if (usedCartsArray != null) {
                    val currentList = repository.usedCartridges.first()
                    currentList.forEach { repository.deleteUsedCartridge(it.id) }
                    for (i in 0 until usedCartsArray.length()) {
                        val obj = usedCartsArray.getJSONObject(i)
                        repository.addUsedCartridge(
                            UsedCartridge(
                                inventoryNumber = obj.getString("inventoryNumber"),
                                dateAdded = obj.getString("dateAdded"),
                                dateStatusChanged = obj.getString("dateStatusChanged"),
                                status = obj.getString("status"),
                                issueTo = if (obj.isNull("issueTo")) null else obj.getString("issueTo")
                            )
                        )
                    }
                }

                // 4. cartridge_types
                val typesArray = rootJson.optJSONArray("cartridge_types")
                if (typesArray != null) {
                    val currentList = repository.cartridgeTypes.first()
                    currentList.forEach { repository.deleteCartridgeType(it.id) }
                    for (i in 0 until typesArray.length()) {
                        val obj = typesArray.getJSONObject(i)
                        repository.addCartridgeType(
                            CartridgeType(
                                name = obj.getString("name"),
                                description = obj.optString("description", "")
                            )
                        )
                    }
                }

                // 5. equipment
                val equipArray = rootJson.optJSONArray("equipment")
                if (equipArray != null) {
                    val currentList = repository.equipmentItems.first()
                    currentList.forEach { repository.deleteEquipment(it.id) }
                    for (i in 0 until equipArray.length()) {
                        val obj = equipArray.getJSONObject(i)
                        repository.addEquipment(
                            EquipmentItem(
                                name = obj.getString("name"),
                                serialNumber = obj.getString("serialNumber"),
                                inventoryNumber = obj.getString("inventoryNumber"),
                                purchaseDate = obj.getString("purchaseDate"),
                                assignee = obj.getString("assignee")
                            )
                        )
                    }
                }

                // 6. employees
                val empsArray = rootJson.optJSONArray("employees")
                if (empsArray != null) {
                    val currentList = repository.employees.first()
                    currentList.forEach { repository.deleteEmployee(it.id) }
                    for (i in 0 until empsArray.length()) {
                        val obj = empsArray.getJSONObject(i)
                        repository.addEmployee(
                            Employee(
                                fullName = obj.getString("fullName"),
                                position = obj.getString("position"),
                                phoneNumber = obj.getString("phoneNumber"),
                                secondPhoneNumber = if (obj.has("secondPhoneNumber")) obj.getString("secondPhoneNumber") else "",
                                socialMediaLabel = if (obj.isNull("socialMediaLabel")) null else obj.getString("socialMediaLabel"),
                                socialMediaLink = if (obj.isNull("socialMediaLink")) null else obj.getString("socialMediaLink")
                            )
                        )
                    }
                }

                // 7. instructions
                val notesArray = rootJson.optJSONArray("instructions")
                if (notesArray != null) {
                    val currentList = repository.instructions.first()
                    currentList.forEach { repository.deleteInstruction(it.id) }
                    for (i in 0 until notesArray.length()) {
                        val obj = notesArray.getJSONObject(i)
                        repository.addInstruction(
                            InstructionNote(
                                title = obj.getString("title"),
                                content = obj.getString("content"),
                                imageUris = obj.optString("imageUris", ""),
                                localPhotosJson = obj.optString("localPhotosJson", "")
                            )
                        )
                    }
                }

                // 8. tasks
                val tasksArray = rootJson.optJSONArray("tasks")
                if (tasksArray != null) {
                    val currentList = repository.tasks.first()
                    currentList.forEach { repository.deleteTask(it.id) }
                    for (i in 0 until tasksArray.length()) {
                        val obj = tasksArray.getJSONObject(i)
                        repository.addTask(
                            TaskItem(
                                title = obj.getString("title"),
                                description = obj.getString("description"),
                                daysNeeded = obj.getInt("daysNeeded"),
                                createdDateMillis = obj.getLong("createdDateMillis"),
                                isCompleted = obj.getBoolean("isCompleted"),
                                completedDateMillis = obj.optLong("completedDateMillis", 0L),
                                solution = obj.optString("solution", "")
                            )
                        )
                    }
                }

                // 9. addresses
                val addrsArray = rootJson.optJSONArray("addresses")
                if (addrsArray != null) {
                    val currentList = repository.addresses.first()
                    currentList.forEach { repository.deleteAddress(it.id) }
                    for (i in 0 until addrsArray.length()) {
                        val obj = addrsArray.getJSONObject(i)
                        repository.addAddress(
                            com.example.data.AddressItem(
                                name = obj.getString("name"),
                                address = obj.getString("address")
                            )
                        )
                    }
                }

                // 10. passwords
                val pwdsArray = rootJson.optJSONArray("passwords")
                if (pwdsArray != null) {
                    val currentList = repository.passwords.first()
                    currentList.forEach { repository.deletePassword(it.id) }
                    for (i in 0 until pwdsArray.length()) {
                        val obj = pwdsArray.getJSONObject(i)
                        repository.addPassword(
                            com.example.data.PasswordItem(
                                name = obj.getString("name"),
                                login = obj.getString("login"),
                                password = obj.getString("password"),
                                comment = if (obj.isNull("comment")) null else obj.getString("comment")
                            )
                        )
                    }
                }

                showToast("Импорт завершен успешно! База данных синхронизирована.")
                onResult(true, "Данные успешно импортированы!")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Ошибка при импорте: ${e.message}")
                onResult(false, "Ошибка при импорте: ${e.message}")
            }
        }
    }

    private val notifiedTaskIds = mutableSetOf<Int>()

    private fun checkTasksForNotifications(taskList: List<TaskItem>) {
        val todayDay = System.currentTimeMillis() / 86400000L
        taskList.forEach { task ->
            if (!task.isCompleted) {
                val createdDay = task.createdDateMillis / 86400000L
                val deadlineDay = createdDay + task.daysNeeded
                val daysRemaining = deadlineDay - todayDay
                if (daysRemaining == 1L) {
                    if (!notifiedTaskIds.contains(task.id)) {
                        notifiedTaskIds.add(task.id)
                        sendLocalNotification(task)
                    }
                }
            }
        }
    }

    private fun sendLocalNotification(task: TaskItem) {
        val context = getApplication<Application>().applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        // Create channel for Android O+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelId = "task_deadlines"
            val channelName = "Дедлайны задач"
            val channel = android.app.NotificationChannel(
                channelId,
                channelName,
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления за день до окончания задачи"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create an intent to open the app on click
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            task.id,
            intent ?: android.content.Intent(),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or if (android.os.Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = androidx.core.app.NotificationCompat.Builder(context, "task_deadlines")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Дедлайн ИТ-задачи!")
            .setContentText("Остался всего 1 день до закрытия задачи: \"${task.title}\"")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(task.id, builder.build())
    }

    fun shareSingleInstruction(context: Context, note: InstructionNote) {
        viewModelScope.launch {
            try {
                val obj = org.json.JSONObject()
                obj.put("type", "single_instruction")
                obj.put("title", note.title)
                obj.put("content", note.content)
                obj.put("imageUris", note.imageUris)
                obj.put("localPhotosJson", note.localPhotosJson)

                val outputString = obj.toString(4)
                val sanitizedTitle = note.title.replace(Regex("[^a-zA-Zа-яА-Я0-9_]"), "_")
                val filename = "instruction_${sanitizedTitle}.json"
                val cacheFile = java.io.File(context.cacheDir, filename)
                cacheFile.writeText(outputString, Charsets.UTF_8)

                val authority = "com.example.fileprovider"
                val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, cacheFile)

                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Инструкция: ${note.title}")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Поделиться инструкцией"))
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Ошибка при экспорте инструкции: ${e.message}")
            }
        }
    }

    fun importSingleInstruction(context: Context, uri: Uri, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                val jsonText = inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }

                if (jsonText.isNullOrEmpty()) {
                    onComplete(false, "Файл пуст")
                    return@launch
                }

                val obj = org.json.JSONObject(jsonText)
                if (obj.optString("type") == "single_instruction" || obj.has("title")) {
                    val title = obj.getString("title")
                    val content = obj.getString("content")
                    val imageUris = obj.optString("imageUris", "")
                    val localPhotosJson = obj.optString("localPhotosJson", "")

                    repository.addInstruction(
                        InstructionNote(
                            title = title,
                            content = content,
                            imageUris = imageUris,
                            localPhotosJson = localPhotosJson
                        )
                    )
                    onComplete(true, "Инструкция '$title' успешно импортирована")
                } else {
                    onComplete(false, "Файл не содержит корректную инструкцию")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false, "Ошибка импорта: ${e.message}")
            }
        }
    }

}
