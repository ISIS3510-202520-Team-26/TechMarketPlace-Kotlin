package com.techmarketplace.data.storage.pendingqueue

import android.content.Context
import android.net.Uri
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/* ========================== MODELOS ========================== */

enum class PendingStatus { PENDING, SENDING, SENT, FAILED }

@Entity(tableName = "pending_listings")
data class PendingListingEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    val title: String,
    val description: String,
    val categoryId: String,
    val brandId: String?,
    val priceCents: Long,
    val currency: String,
    val condition: String,
    val quantity: Int,

    /** Guardamos el Uri como String para evitar type converters de Uri */
    val imageUri: String?,

    val status: PendingStatus = PendingStatus.PENDING,
    val attemptCount: Int = 0,
    val errorMessage: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/* ======================== TYPE CONVERTERS ======================== */

class PendingConverters {
    @TypeConverter
    fun statusToString(s: PendingStatus): String = s.name

    @TypeConverter
    fun stringToStatus(s: String): PendingStatus = PendingStatus.valueOf(s)
}

/* ============================ DAO ============================ */

@Dao
interface PendingListingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingListingEntity)

    @Query(
        """
        SELECT * FROM pending_listings
        WHERE status = :s1 OR status = :s2
        ORDER BY createdAt ASC
        """
    )
    fun observeDrafts(s1: String = PendingStatus.PENDING.name, s2: String = PendingStatus.SENDING.name): Flow<List<PendingListingEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM pending_listings
        WHERE status = :s1 OR status = :s2
        """
    )
    fun observeDraftCount(s1: String = PendingStatus.PENDING.name, s2: String = PendingStatus.SENDING.name): Flow<Int>

    @Query(
        """
        SELECT * FROM pending_listings
        WHERE status = :status
        ORDER BY createdAt ASC
        LIMIT :limit
        """
    )
    suspend fun nextBatch(status: String = PendingStatus.PENDING.name, limit: Int = 3): List<PendingListingEntity>

    @Query(
        """
        UPDATE pending_listings
        SET status = :status, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun setStatus(id: String, status: String, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE pending_listings
        SET status = :status,
            attemptCount = attemptCount + 1,
            errorMessage = :error,
            updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun markFailed(id: String, status: String = PendingStatus.FAILED.name, error: String?, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM pending_listings WHERE id = :id")
    suspend fun deleteById(id: String)
}

/* ========================= DATABASE ========================= */

@Database(
    entities = [PendingListingEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(PendingConverters::class)
abstract class PendingDb : RoomDatabase() {
    abstract fun dao(): PendingListingDao

    companion object {
        @Volatile private var INSTANCE: PendingDb? = null

        fun get(context: Context): PendingDb {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PendingDb::class.java,
                    "pending_publish.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

/* ====================== REPOSITORY (API) ====================== */

class PendingPublishRepository(context: Context) {

    private val dao = PendingDb.get(context).dao()

    /** Encola una publicación para enviar cuando haya internet. */
    suspend fun enqueue(
        title: String,
        description: String,
        categoryId: String,
        brandId: String?,
        priceCents: Long,
        currency: String,
        condition: String,
        quantity: Int,
        imageUri: Uri?
    ): String {
        val entity = PendingListingEntity(
            title = title,
            description = description,
            categoryId = categoryId,
            brandId = brandId,
            priceCents = priceCents,
            currency = currency,
            condition = condition,
            quantity = quantity,
            imageUri = imageUri?.toString(), // guardamos como String
            status = PendingStatus.PENDING
        )
        dao.upsert(entity)
        return entity.id
    }

    /** Flujo de borradores (PENDING + SENDING) para mostrar en Home. */
    fun draftsFlow(): Flow<List<PendingListingEntity>> = dao.observeDrafts()

    /** Conteo observable de lo que está en cola (PENDING + SENDING). */
    fun countFlow(): Flow<Int> = dao.observeDraftCount()

    /** Siguiente lote de pendientes a enviar. */
    suspend fun nextBatch(limit: Int = 3): List<PendingListingEntity> = dao.nextBatch(limit = limit)

    /** Marca un draft como enviándose (no incrementa intentos). */
    suspend fun markSending(id: String) {
        dao.setStatus(id, PendingStatus.SENDING.name, now = System.currentTimeMillis())
    }

    /** Marca como enviado (lo removemos). */
    suspend fun markSent(id: String) {
        dao.deleteById(id)
    }

    /** Marca como fallido e incrementa intentos con mensaje de error. */
    suspend fun markFailed(id: String, errorMessage: String?) {
        dao.markFailed(id = id, error = errorMessage)
    }
}
