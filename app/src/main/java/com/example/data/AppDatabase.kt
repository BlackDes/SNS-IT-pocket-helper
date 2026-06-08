package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ==========================
// 1. Entities
// ==========================

// Cartridge Stock Additions (New Cartridges)
@Entity(tableName = "new_cartridges")
data class NewCartridge(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val quantityAdded: Int,
    val dateAdded: String // dd.MM.yyyy
)

// History of All Issued Cartridges (both New and Used/BU)
@Entity(tableName = "cartridge_issuances")
data class CartridgeIssuance(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cartridgeName: String,
    val isUsed: Boolean, // true if Used (BU), false if New
    val quantity: Int, // e.g. 2 for "NEW 2 шт"
    val issueTo: String, // Recipient employee name
    val issueDate: String // dd.MM.yyyy
)

// Used (BU) Cartridges Registry
@Entity(tableName = "used_cartridges")
data class UsedCartridge(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val inventoryNumber: String,
    val dateAdded: String, // dd.MM.yyyy (Current date upon entry)
    val dateStatusChanged: String, // dd.MM.yyyy (Updates when status changes)
    val status: String, // "Ремонт", "Выдан", "Умер"
    val issueTo: String? = null // whom it was given to if status is "Выдан"
)

// Equipment/Hardware Items Registry
@Entity(tableName = "equipment")
data class EquipmentItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val serialNumber: String,
    val inventoryNumber: String,
    val purchaseDate: String, // dd.MM.yyyy
    val assignee: String, // Full Name of employee
    val comment: String = "" // Optional comment
)

// Custom Positions List
@Entity(tableName = "custom_positions")
data class CustomPosition(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

// Employee Contact Information Registry
@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fullName: String,
    val position: String,
    val phoneNumber: String,
    val secondPhoneNumber: String = "",
    val socialMediaLabel: String? = null,
    val socialMediaLink: String? = null
)

// Predefined Cartridge Types (with custom user addition support)
@Entity(tableName = "cartridge_types")
data class CartridgeType(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String = ""
)

// Instructions / Notebook entries
@Entity(tableName = "instructions")
data class InstructionNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val imageUris: String = "", // String containing comma-separated URLs or drawing Base64
    val localPhotosJson: String = "", // String storing serialized [path^comment|path^comment]
    val allowCopy: Boolean = false
)

// Tasks Registry
@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val daysNeeded: Int, // 0 = today, etc.
    val createdDateMillis: Long, // timestamp of creation
    val isCompleted: Boolean = false,
    val completedDateMillis: Long = 0L,
    val solution: String = ""
)

// Addresses Registry
@Entity(tableName = "addresses")
data class AddressItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val address: String
)

// Passwords Registry
@Entity(tableName = "passwords")
data class PasswordItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val login: String,
    val password: String,
    val comment: String? = null
)


// ==========================
// 2. Data Access Objects (DAOs)
// ==========================

@Dao
interface CartridgeDao {
    // New Cartridges
    @Query("SELECT * FROM new_cartridges ORDER BY id DESC")
    fun getAllNewCartridges(): Flow<List<NewCartridge>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNewCartridge(cartridge: NewCartridge)

    @Update
    suspend fun updateNewCartridge(cartridge: NewCartridge)

    @Query("DELETE FROM new_cartridges WHERE id = :id")
    suspend fun deleteNewCartridgeById(id: Int)

    @Query("DELETE FROM new_cartridges WHERE name = :name")
    suspend fun deleteNewCartridgesByName(name: String)

    // Issuances
    @Query("SELECT * FROM cartridge_issuances ORDER BY id DESC")
    fun getAllIssuances(): Flow<List<CartridgeIssuance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssuance(issuance: CartridgeIssuance)

    @Update
    suspend fun updateIssuance(issuance: CartridgeIssuance)

    @Query("DELETE FROM cartridge_issuances WHERE id = :id")
    suspend fun deleteIssuanceById(id: Int)

    @Query("DELETE FROM cartridge_issuances WHERE cartridgeName = :cartridgeName")
    suspend fun deleteIssuancesByCartridgeName(cartridgeName: String)

    // Used Cartridges
    @Query("SELECT * FROM used_cartridges ORDER BY id DESC")
    fun getAllUsedCartridges(): Flow<List<UsedCartridge>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsedCartridge(used: UsedCartridge)

    @Update
    suspend fun updateUsedCartridge(used: UsedCartridge)

    @Query("DELETE FROM used_cartridges WHERE id = :id")
    suspend fun deleteUsedCartridgeById(id: Int)

    // Cartridge Types
    @Query("SELECT * FROM cartridge_types ORDER BY name ASC")
    fun getAllCartridgeTypes(): Flow<List<CartridgeType>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartridgeType(type: CartridgeType)

    @Update
    suspend fun updateCartridgeType(type: CartridgeType)

    @Query("DELETE FROM cartridge_types WHERE id = :id")
    suspend fun deleteCartridgeTypeById(id: Int)
}

@Dao
interface CabinetDao {
    // Equipment
    @Query("SELECT * FROM equipment ORDER BY name ASC")
    fun getAllEquipment(): Flow<List<EquipmentItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEquipment(item: EquipmentItem)

    @Update
    suspend fun updateEquipment(item: EquipmentItem)

    @Query("DELETE FROM equipment WHERE id = :id")
    suspend fun deleteEquipmentById(id: Int)

    // Employees
    @Query("SELECT * FROM employees ORDER BY fullName ASC")
    fun getAllEmployees(): Flow<List<Employee>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee)

    @Update
    suspend fun updateEmployee(employee: Employee)

    @Query("DELETE FROM employees WHERE id = :id")
    suspend fun deleteEmployeeById(id: Int)

    // Custom Positions
    @Query("SELECT * FROM custom_positions ORDER BY name ASC")
    fun getAllCustomPositions(): Flow<List<CustomPosition>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomPosition(pos: CustomPosition)

    @Update
    suspend fun updateCustomPosition(pos: CustomPosition)

    @Query("DELETE FROM custom_positions WHERE id = :id")
    suspend fun deleteCustomPositionById(id: Int)

    // Instructions
    @Query("SELECT * FROM instructions ORDER BY id DESC")
    fun getAllInstructions(): Flow<List<InstructionNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstruction(note: InstructionNote)

    @Update
    suspend fun updateInstruction(note: InstructionNote)

    @Query("DELETE FROM instructions WHERE id = :id")
    suspend fun deleteInstructionById(id: Int)

    // Tasks
    @Query("SELECT * FROM tasks ORDER BY createdDateMillis DESC")
    fun getAllTasks(): Flow<List<TaskItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskItem)

    @Update
    suspend fun updateTask(task: TaskItem)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    // Addresses
    @Query("SELECT * FROM addresses ORDER BY name ASC")
    fun getAllAddresses(): Flow<List<AddressItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddress(address: AddressItem)

    @Update
    suspend fun updateAddress(address: AddressItem)

    @Query("DELETE FROM addresses WHERE id = :id")
    suspend fun deleteAddressById(id: Int)

    // Passwords
    @Query("SELECT * FROM passwords ORDER BY name ASC")
    fun getAllPasswords(): Flow<List<PasswordItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(passwd: PasswordItem)

    @Update
    suspend fun updatePassword(passwd: PasswordItem)

    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun deletePasswordById(id: Int)

}

// ==========================
// 3. Database Abstract Class
// ==========================

@Database(
    entities = [
        NewCartridge::class,
        CartridgeIssuance::class,
        UsedCartridge::class,
        EquipmentItem::class,
        Employee::class,
        InstructionNote::class,
        TaskItem::class,
        CartridgeType::class,
        AddressItem::class,
        PasswordItem::class,
        CustomPosition::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cartridgeDao(): CartridgeDao
    abstract fun cabinetDao(): CabinetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sns_pocket_assistant_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ==========================
// 4. Repository Pattern implementation
// ==========================

class SnsRepository(private val db: AppDatabase) {
    private val cartridgeDao = db.cartridgeDao()
    private val cabinetDao = db.cabinetDao()

    // Flow streams of data
    val newCartridges: Flow<List<NewCartridge>> = cartridgeDao.getAllNewCartridges()
    val cartridgeIssuances: Flow<List<CartridgeIssuance>> = cartridgeDao.getAllIssuances()
    val usedCartridges: Flow<List<UsedCartridge>> = cartridgeDao.getAllUsedCartridges()
    val cartridgeTypes: Flow<List<CartridgeType>> = cartridgeDao.getAllCartridgeTypes()
    val equipmentItems: Flow<List<EquipmentItem>> = cabinetDao.getAllEquipment()
    val employees: Flow<List<Employee>> = cabinetDao.getAllEmployees()
    val customPositions: Flow<List<CustomPosition>> = cabinetDao.getAllCustomPositions()
    val instructions: Flow<List<InstructionNote>> = cabinetDao.getAllInstructions()
    val tasks: Flow<List<TaskItem>> = cabinetDao.getAllTasks()
    val addresses: Flow<List<AddressItem>> = cabinetDao.getAllAddresses()
    val passwords: Flow<List<PasswordItem>> = cabinetDao.getAllPasswords()

    // 4.1 New Cartridges Operations
    suspend fun addNewCartridge(cartridge: NewCartridge) {
        cartridgeDao.insertNewCartridge(cartridge)
    }
    suspend fun updateNewCartridge(cartridge: NewCartridge) {
        cartridgeDao.updateNewCartridge(cartridge)
    }
    suspend fun deleteNewCartridge(id: Int) {
        cartridgeDao.deleteNewCartridgeById(id)
    }
    suspend fun deleteNewCartridgesByName(name: String) {
        cartridgeDao.deleteNewCartridgesByName(name)
    }

    // 4.2 Issuances Operations
    suspend fun addIssuance(issuance: CartridgeIssuance) {
        cartridgeDao.insertIssuance(issuance)
    }
    suspend fun updateIssuance(issuance: CartridgeIssuance) {
        cartridgeDao.updateIssuance(issuance)
    }
    suspend fun deleteIssuance(id: Int) {
        cartridgeDao.deleteIssuanceById(id)
    }
    suspend fun deleteIssuancesByCartridgeName(cartridgeName: String) {
        cartridgeDao.deleteIssuancesByCartridgeName(cartridgeName)
    }

    // 4.3 Used Cartridges Operations
    suspend fun addUsedCartridge(used: UsedCartridge) {
        cartridgeDao.insertUsedCartridge(used)
    }
    suspend fun updateUsedCartridge(used: UsedCartridge) {
        cartridgeDao.updateUsedCartridge(used)
    }
    suspend fun deleteUsedCartridge(id: Int) {
        cartridgeDao.deleteUsedCartridgeById(id)
    }

    // Cartridge Types Operations
    suspend fun addCartridgeType(type: CartridgeType) {
        cartridgeDao.insertCartridgeType(type)
    }
    suspend fun updateCartridgeType(type: CartridgeType) {
        cartridgeDao.updateCartridgeType(type)
    }
    suspend fun deleteCartridgeType(id: Int) {
        cartridgeDao.deleteCartridgeTypeById(id)
    }

    // 4.4 Equipment Operations
    suspend fun addEquipment(item: EquipmentItem) {
        cabinetDao.insertEquipment(item)
    }
    suspend fun updateEquipment(item: EquipmentItem) {
        cabinetDao.updateEquipment(item)
    }
    suspend fun deleteEquipment(id: Int) {
        cabinetDao.deleteEquipmentById(id)
    }

    // 4.5 Employee Operations
    suspend fun addEmployee(employee: Employee) {
        cabinetDao.insertEmployee(employee)
    }
    suspend fun updateEmployee(employee: Employee) {
        cabinetDao.updateEmployee(employee)
    }
    suspend fun deleteEmployee(id: Int) {
        cabinetDao.deleteEmployeeById(id)
    }

    // Custom Positions Operations
    suspend fun addCustomPosition(pos: CustomPosition) {
        cabinetDao.insertCustomPosition(pos)
    }
    suspend fun updateCustomPosition(pos: CustomPosition) {
        cabinetDao.updateCustomPosition(pos)
    }
    suspend fun deleteCustomPosition(id: Int) {
        cabinetDao.deleteCustomPositionById(id)
    }

    // 4.6 Instruction Notes Operations
    suspend fun addInstruction(note: InstructionNote) {
        cabinetDao.insertInstruction(note)
    }
    suspend fun updateInstruction(note: InstructionNote) {
        cabinetDao.updateInstruction(note)
    }
    suspend fun deleteInstruction(id: Int) {
        cabinetDao.deleteInstructionById(id)
    }

    // 4.7 Task Operations
    suspend fun addTask(task: TaskItem) {
        cabinetDao.insertTask(task)
    }
    suspend fun updateTask(task: TaskItem) {
        cabinetDao.updateTask(task)
    }
    suspend fun deleteTask(id: Int) {
        cabinetDao.deleteTaskById(id)
    }

    // 4.8 Address Operations
    suspend fun addAddress(addr: AddressItem) {
        cabinetDao.insertAddress(addr)
    }
    suspend fun updateAddress(addr: AddressItem) {
        cabinetDao.updateAddress(addr)
    }
    suspend fun deleteAddress(id: Int) {
        cabinetDao.deleteAddressById(id)
    }

    // 4.9 Password Operations
    suspend fun addPassword(passwd: PasswordItem) {
        cabinetDao.insertPassword(passwd)
    }
    suspend fun updatePassword(passwd: PasswordItem) {
        cabinetDao.updatePassword(passwd)
    }
    suspend fun deletePassword(id: Int) {
        cabinetDao.deletePasswordById(id)
    }
}
