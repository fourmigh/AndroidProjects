package org.caojun.shotocr.accounting

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.caojun.shotocr.accounting.data.toEditableReceipt
import org.caojun.shotocr.accounting.data.toEntity
import org.caojun.shotocr.database.data.AppDatabase
import org.caojun.shotocr.database.data.ReceiptDao

object AccountingManager {

    private var receiptDao: ReceiptDao? = null
    private val _records = MutableStateFlow<List<EditableReceipt>>(emptyList())
    val records: StateFlow<List<EditableReceipt>> = _records.asStateFlow()

    fun init(context: Context) {
        receiptDao = AppDatabase.getInstance(context).receiptDao()
        CoroutineScope(Dispatchers.IO).launch {
            receiptDao?.getAll()?.collect { entities ->
                _records.value = entities.map { it.toEditableReceipt() }
            }
        }
    }

    suspend fun save(receipt: EditableReceipt) {
        receiptDao?.insert(receipt.toEntity())
    }

    suspend fun update(receipt: EditableReceipt) {
        receiptDao?.update(receipt.toEntity())
    }

    suspend fun getById(id: Long): EditableReceipt? {
        return receiptDao?.getById(id)?.toEditableReceipt()
    }

    suspend fun delete(receipt: EditableReceipt) {
        receiptDao?.delete(receipt.toEntity())
    }

    suspend fun deleteById(id: Long) {
        receiptDao?.deleteById(id)
    }

    fun getAll(): List<EditableReceipt> {
        return _records.value
    }

    fun clear() {
        _records.value = emptyList()
    }
}
