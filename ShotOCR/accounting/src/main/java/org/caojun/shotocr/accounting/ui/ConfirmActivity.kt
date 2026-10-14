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
import org.caojun.shotocr.accounting.CorrectionHooks
import org.caojun.shotocr.accounting.EditableReceipt
import org.caojun.shotocr.classification.repository.CategoryRepository
import org.caojun.shotocr.classification.repository.CategoryRuleRepository
import org.caojun.shotocr.classification.classifier.CategoryClassifier
import java.io.ByteArrayOutputStream

class ConfirmActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShotOCR/Confirm"
        private const val IMAGE_QUALITY = 80
    }

    private var pendingDeleteUri: Uri? = null
    private var deleteFinishesActivity = true

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "Screenshot deleted successfully")
        } else {
            Log.d(TAG, "Screenshot deletion cancelled or failed")
        }
        pendingDeleteUri = null
        if (deleteFinishesActivity) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CategoryRepository.init(this)
        CategoryRuleRepository.init(this)

        val deleteUri = intent.getStringExtra("delete_screenshot_uri")
        val receiptId = intent.getLongExtra("receipt_id", -1)

        if (receiptId > 0) {
            loadExistingReceipt(receiptId, deleteUri?.takeIf { it.isNotEmpty() })
            return
        }

        if (!deleteUri.isNullOrEmpty()) {
            deleteScreenshot(Uri.parse(deleteUri))
            return
        }

        val uriString = intent.getStringExtra("screenshot_uri") ?: ""
        val rawText = intent.getStringExtra("raw_text") ?: ""
        val amount = intent.getStringExtra("amount") ?: ""
        val discount = intent.getStringExtra("discount") ?: ""
        val originalAmount = intent.getStringExtra("original_amount") ?: ""
        val storeName = intent.getStringExtra("store_name") ?: ""
        val paymentTime = intent.getStringExtra("payment_time") ?: ""
        val paymentMethod = intent.getStringExtra("payment_method") ?: ""
        val orderNumber = intent.getStringExtra("order_number") ?: ""
        val sourceApp = intent.getStringExtra("source_app") ?: ""

        loadNewReceipt(
            uriString, rawText, amount, discount, originalAmount,
            storeName, paymentTime, paymentMethod, orderNumber, sourceApp
        )
    }

    private fun loadExistingReceipt(receiptId: Long, duplicateScreenshotUri: String?) {
        lifecycleScope.launch {
            val receipt = AccountingManager.getById(receiptId)
            if (receipt != null) {
                val deleteUri = duplicateScreenshotUri?.let { Uri.parse(it) }
                setContent {
                    MaterialTheme {
                        ConfirmScreen(
                            receipt = receipt,
                            onSaved = { savedReceipt -> handleSave(savedReceipt) },
                            onCancel = { finish() },
                            onDeleteScreenshot = {
                                deleteUri?.let { deleteScreenshot(it, finishAfter = false) }
                            },
                            initialDeleteScreenshot = deleteUri != null,
                            onRememberCorrection = { json ->
                                CorrectionHooks.remember?.invoke(receipt.rawText, json)
                            }
                        )
                    }
                }
            } else {
                Log.e(TAG, "Receipt not found: $receiptId")
                if (!duplicateScreenshotUri.isNullOrEmpty()) {
                    deleteScreenshot(Uri.parse(duplicateScreenshotUri))
                } else {
                    finish()
                }
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
        sourceApp: String
    ) {
        val screenshotUri = if (uriString.isNotEmpty()) Uri.parse(uriString) else Uri.EMPTY

        lifecycleScope.launch {
            val screenshotImage = if (screenshotUri != Uri.EMPTY) {
                compressImage(screenshotUri)
            } else null

            val suggestedCategoryId = CategoryClassifier.suggest(storeName, rawText)

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
                sourceApp = sourceApp,
                categoryId = suggestedCategoryId ?: EditableReceipt.DEFAULT_CATEGORY_ID
            )

            setContent {
                MaterialTheme {
                    ConfirmScreen(
                        receipt = receipt,
                        onSaved = { savedReceipt -> handleSave(savedReceipt) },
                        onCancel = { finish() },
                        onDeleteScreenshot = { deleteScreenshot(receipt.screenshotUri) },
                        askDeleteOnCancel = receipt.screenshotUri != Uri.EMPTY,
                        autoSuggestedCategoryId = suggestedCategoryId,
                        onRememberCorrection = { json ->
                            CorrectionHooks.remember?.invoke(rawText, json)
                        }
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

    private fun deleteScreenshot(screenshotUri: Uri, finishAfter: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(
                    contentResolver,
                    listOf(screenshotUri)
                )
                pendingDeleteUri = screenshotUri
                deleteFinishesActivity = finishAfter
                deleteLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create delete request", e)
                if (finishAfter) finish()
            }
        } else {
            try {
                contentResolver.delete(screenshotUri, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete screenshot", e)
            }
            if (finishAfter) finish()
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
