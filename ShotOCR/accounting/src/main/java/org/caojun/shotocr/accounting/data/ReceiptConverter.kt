package org.caojun.shotocr.accounting.data

import android.net.Uri
import org.caojun.shotocr.accounting.EditableReceipt
import org.caojun.shotocr.accounting.EditableReceiptItem
import org.caojun.shotocr.database.data.ReceiptEntity
import org.json.JSONArray
import org.json.JSONObject

fun ReceiptEntity.toEditableReceipt(): EditableReceipt {
    val items = parseItemsJson(itemsJson)
    return EditableReceipt(
        id = id,
        screenshotUri = if (screenshotUri.isNotEmpty()) Uri.parse(screenshotUri) else Uri.EMPTY,
        screenshotImage = screenshotImage,
        rawText = rawText,
        amount = amount,
        discount = discount,
        originalAmount = originalAmount,
        storeName = storeName,
        paymentTime = paymentTime,
        paymentMethod = paymentMethod,
        orderNumber = orderNumber,
        items = items
    )
}

fun EditableReceipt.toEntity(): ReceiptEntity {
    return ReceiptEntity(
        id = id,
        screenshotUri = screenshotUri.toString(),
        screenshotImage = screenshotImage,
        rawText = rawText,
        amount = amount,
        discount = discount,
        originalAmount = originalAmount,
        storeName = storeName,
        paymentTime = paymentTime,
        paymentMethod = paymentMethod,
        orderNumber = orderNumber,
        itemsJson = itemsToJson(items)
    )
}

private fun itemsToJson(items: List<EditableReceiptItem>): String {
    val array = JSONArray()
    items.forEach { item ->
        val obj = JSONObject()
        obj.put("name", item.name)
        obj.put("price", item.price)
        obj.put("quantity", item.quantity)
        obj.put("subtotal", item.subtotal)
        array.put(obj)
    }
    return array.toString()
}

private fun parseItemsJson(json: String): List<EditableReceiptItem> {
    val items = mutableListOf<EditableReceiptItem>()
    try {
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            items.add(
                EditableReceiptItem(
                    name = obj.optString("name", ""),
                    price = obj.optString("price", ""),
                    quantity = obj.optString("quantity", ""),
                    subtotal = obj.optString("subtotal", "")
                )
            )
        }
    } catch (_: Exception) {
    }
    return items
}
