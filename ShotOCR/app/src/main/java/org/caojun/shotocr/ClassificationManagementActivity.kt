package org.caojun.shotocr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.caojun.shotocr.classification.repository.CategoryRepository
import org.caojun.shotocr.classification.ui.CategoryManagementScreen
import org.caojun.shotocr.ui.theme.ShotOCRTheme

class ClassificationManagementActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CategoryRepository.init(this)
        setContent {
            ShotOCRTheme {
                CategoryManagementScreen(onBack = { finish() })
            }
        }
    }
}
