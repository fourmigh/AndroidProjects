package org.caojun.shotocr.accounting

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AccountingManager {

    private val _records = MutableStateFlow<List<EditableReceipt>>(emptyList())
    val records: StateFlow<List<EditableReceipt>> = _records.asStateFlow()

    fun save(receipt: EditableReceipt) {
        _records.value = _records.value + receipt
    }

    fun delete(index: Int) {
        if (index in _records.value.indices) {
            _records.value = _records.value.toMutableList().apply { removeAt(index) }
        }
    }

    fun getAll(): List<EditableReceipt> {
        return _records.value
    }

    fun clear() {
        _records.value = emptyList()
    }
}
