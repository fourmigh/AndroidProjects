package org.caojun.shotocr.accounting.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import org.caojun.shotocr.accounting.EditableReceipt
import org.caojun.shotocr.accounting.EditableReceiptItem

class ConfirmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uriString = intent.getStringExtra("screenshot_uri") ?: ""
        val rawText = intent.getStringExtra("raw_text") ?: ""
        val amount = intent.getStringExtra("amount") ?: ""
        val discount = intent.getStringExtra("discount") ?: ""
        val originalAmount = intent.getStringExtra("original_amount") ?: ""

        val names = intent.getStringArrayListExtra("item_names") ?: arrayListOf()
        val prices = intent.getStringArrayListExtra("item_prices") ?: arrayListOf()
        val quantities = intent.getStringArrayListExtra("item_quantities") ?: arrayListOf()

        val items = names.indices.map { i ->
            EditableReceiptItem(
                name = names.getOrElse(i) { "" },
                price = prices.getOrElse(i) { "" },
                quantity = quantities.getOrElse(i) { "" },
                subtotal = ""
            )
        }

        val receipt = EditableReceipt(
            screenshotUri = if (uriString.isNotEmpty()) Uri.parse(uriString) else Uri.EMPTY,
            rawText = rawText,
            amount = amount,
            discount = discount,
            originalAmount = originalAmount,
            items = items
        )

        setContent {
            MaterialTheme {
                ConfirmScreen(
                    receipt = receipt,
                    onSaved = { finish() },
                    onCancel = { finish() }
                )
            }
        }
    }
}
