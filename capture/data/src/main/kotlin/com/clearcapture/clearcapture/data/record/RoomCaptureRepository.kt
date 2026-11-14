package com.clearcapture.clearcapture.data.record

import android.content.Context
import com.clearcapture.clearcapture.domain.model.CaptureRecord
import com.clearcapture.clearcapture.domain.model.CaptureStatus
import com.clearcapture.clearcapture.domain.port.CaptureRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The default [CaptureRepository], on the SDK's own Room database. The database opens on first use. */
class RoomCaptureRepository internal constructor(
    database: () -> CaptureDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : CaptureRepository {

    /** Builds the repository on the SDK's database file in [context]. */
    constructor(context: Context, ioDispatcher: CoroutineDispatcher) : this({ CaptureDatabase.create(context) }, ioDispatcher)

    private val dao by lazy { database().captures() }

    override suspend fun saveAttempt(record: CaptureRecord) = withContext(ioDispatcher) { dao.insert(RecordMapper.toEntity(record)) }

    override suspend fun markAccepted(captureId: String, finalPath: String, overrideReason: String?) = withContext(ioDispatcher) {
        dao.updateStatus(captureId, CaptureStatus.ACCEPTED.name, finalPath, overrideReason)
    }

    override suspend fun markDiscarded(captureId: String) = withContext(ioDispatcher) {
        dao.updateStatusOnly(captureId, CaptureStatus.DISCARDED.name)
    }

    override suspend fun delete(captureId: String) = withContext(ioDispatcher) { dao.delete(captureId) }

    override suspend fun purgeOlderThan(epochMillis: Long) {
        withContext(ioDispatcher) { dao.deleteOlderThan(epochMillis) }
    }

    override fun observeAll(): Flow<List<CaptureRecord>> =
        dao.observeAll().map { rows -> rows.map(RecordMapper::toDomain) }.flowOn(ioDispatcher)
}
