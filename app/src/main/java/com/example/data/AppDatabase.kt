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
    val assignee: String // Full Name of employee
)

// Employee Contact Information Registry
@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fullName: String,
    val position: String,
    val phoneNumber: String
)

// Instructions / Notebook entries
@Entity(tableName = "instructions")
data class InstructionNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val imageUris: String = "", // String containing comma-separated URLs or drawing Base64
    val localPhotosJson: String = "" // String storing serialized [path^comment|path^comment]
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

    // Issuances
    @Query("SELECT * FROM cartridge_issuances ORDER BY id DESC")
    fun getAllIssuances(): Flow<List<CartridgeIssuance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssuance(issuance: CartridgeIssuance)

    @Update
    suspend fun updateIssuance(issuance: CartridgeIssuance)

    @Query("DELETE FROM cartridge_issuances WHERE id = :id")
    suspend fun deleteIssuanceById(id: Int)

    // Used Cartridges
    @Query("SELECT * FROM used_cartridges ORDER BY id DESC")
    fun getAllUsedCartridges(): Flow<List<UsedCartridge>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsedCartridge(used: UsedCartridge)

    @Update
    suspend fun updateUsedCartridge(used: UsedCartridge)

    @Query("DELETE FROM used_cartridges WHERE id = :id")
    suspend fun deleteUsedCartridgeById(id: Int)
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
        TaskItem::class
    ],
    version = 1,
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
    val equipmentItems: Flow<List<EquipmentItem>> = cabinetDao.getAllEquipment()
    val employees: Flow<List<Employee>> = cabinetDao.getAllEmployees()
    val instructions: Flow<List<InstructionNote>> = cabinetDao.getAllInstructions()
    val tasks: Flow<List<TaskItem>> = cabinetDao.getAllTasks()

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
}
