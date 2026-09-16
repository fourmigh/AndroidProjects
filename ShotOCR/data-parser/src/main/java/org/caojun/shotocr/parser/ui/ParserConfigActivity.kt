package org.caojun.shotocr.parser.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import org.caojun.shotocr.database.data.AppDatabase

class ParserConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = AppDatabase.getInstance(this)
        val dao = database.parserConfigDao()
        setContent {
            MaterialTheme {
                ParserConfigScreen(dao = dao, onBack = { finish() })
            }
        }
    }
}
