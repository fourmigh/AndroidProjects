package org.caojun.shotocr.accounting.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.caojun.shotocr.accounting.AccountingManager
import org.caojun.shotocr.accounting.EditableReceipt
import org.caojun.shotocr.accounting.EditableReceiptItem
import java.io.ByteArrayOutputStream

class ConfirmActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShotOCR/Confirm"
        private const val IMAGE_QUALITY = 80
    }

    private var pendingDeleteUri: Uri? = null

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "Screenshot deleted successfully")
        } else {
            Log.d(TAG, "Screenshot deletion cancelled or failed")
        }
        pendingDeleteUri = null
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val receiptId = intent.getLongExtra("receipt_id", -1)
        val uriString = intent.getStringExtra("screenshot_uri") ?: ""
        val rawText = intent.getStringExtra("raw_text") ?: ""
        val amount = intent.getStringExtra("amount") ?: ""
        val discount = intent.getStringExtra("discount") ?: ""
        val originalAmount = intent.getStringExtra("original_amount") ?: ""
        val storeName = intent.getStringExtra("store_name") ?: ""
        val paymentTime = intent.getStringExtra("payment_time") ?: ""
        val paymentMethod = intent.getStringExtra("payment_method") ?: ""
        val orderNumber = intent.getStringExtra("order_number") ?: ""

        val names = intent.getStringArrayListExtra("item_names") ?: arrayListOf()
        val prices = intent.getStringArrayListExtra("item_prices") ?: arrayListOf()
        val quantities = intent.getStringArrayListExtra("item_quantities") ?: arrayListOf()

        if (receiptId > 0) {
            loadExistingReceipt(receiptId)
        } else {
            loadNewReceipt(
                uriString, rawText, amount, discount, originalAmount,
                storeName, paymentTime, paymentMethod, orderNumber,
                names, prices, quantities
            )
        }
    }

    private fun loadExistingReceipt(receiptId: Long) {
        lifecycleScope.launch {
            val receipt = AccountingManager.getById(receiptId)
            if (receipt != null) {
                setContent {
                    MaterialTheme {
                        ConfirmScreen(
                            receipt = receipt,
                            onSaved = { savedReceipt -> handleSave(savedReceipt) },
                            onCancel = { finish() }
                        )
                    }
                }
            } else {
                Log.e(TAG, "Receipt not found: $receiptId")
                finish()
            }
        }
    }

    private fun loadNewReceipt(
        uriString: String,
        rawText: String,
        amount: String,
        discount: String,
        originalAmount: String,
        storeName: String,
        paymentTime: String,
        paymentMethod: String,
        orderNumber: String,
        names: ArrayList<String>,
        prices: ArrayList<String>,
        quantities: ArrayList<String>
    ) {
        val items = names.indices.map { i ->
            EditableReceiptItem(
                name = names.getOrElse(i) { "" },
                price = prices.getOrElse(i) { "" },
                quantity = quantities.getOrElse(i) { "" },
                subtotal = ""
            )
        }

        val screenshotUri = if (uriString.isNotEmpty()) Uri.parse(uriString) else Uri.EMPTY

        lifecycleScope.launch {
            val screenshotImage = if (screenshotUri != Uri.EMPTY) {
                compressImage(screenshotUri)
            } else null

            val receipt = EditableReceipt(
                screenshotUri = screenshotUri,
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

            setContent {
                MaterialTheme {
                    ConfirmScreen(
                        receipt = receipt,
                        onSaved = { savedReceipt -> handleSave(savedReceipt) },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private fun handleSave(savedReceipt: EditableReceipt) {
        lifecycleScope.launch {
            if (savedReceipt.id > 0) {
                AccountingManager.update(savedReceipt)
            } else {
                AccountingManager.save(savedReceipt)
            }

            if (savedReceipt.screenshotUri != Uri.EMPTY) {
                deleteScreenshot(savedReceipt.screenshotUri)
            } else {
                finish()
            }
        }
    }

    private fun deleteScreenshot(screenshotUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(
                    contentResolver,
                    listOf(screenshotUri)
                )
                pendingDeleteUri = screenshotUri
                deleteLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create delete request", e)
                finish()
            }
        } else {
            try {
                contentResolver.delete(screenshotUri, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete screenshot", e)
            }
            finish()
        }
    }

    private suspend fun compressImage(screenshotUri: Uri): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(screenshotUri)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, outputStream)
                    bitmap.recycle()
                    outputStream.toByteArray()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to compress image", e)
                null
            }
        }
    }
}
