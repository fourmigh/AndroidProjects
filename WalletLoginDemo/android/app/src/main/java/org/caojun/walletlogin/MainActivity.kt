package org.caojun.walletlogin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        onBind = { startActivity(Intent(this, BindWalletActivity::class.java)) },
                        onScan = { startActivity(Intent(this, ScanLoginActivity::class.java)) },
                        onMock = { startActivity(Intent(this, MockWalletActivity::class.java)) },
                        onNfcSetup = { startActivity(Intent(this, NfcSetupActivity::class.java)) },
                        onNfcRead = { startActivity(Intent(this, NfcReaderActivity::class.java)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onBind: () -> Unit,
    onScan: () -> Unit,
    onMock: () -> Unit,
    onNfcSetup: () -> Unit,
    onNfcRead: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Google Wallet 登录 Demo",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "把登录卡放进 Google Wallet，再用动态二维码扫码登录",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onBind, modifier = Modifier.fillMaxWidth()) {
            Text("1. 绑定登录卡到 Google Wallet")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Text("2. 扫码登录")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onMock, modifier = Modifier.fillMaxWidth()) {
            Text("3. 模拟钱包（无需 Google 凭据）")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onNfcSetup, modifier = Modifier.fillMaxWidth()) {
            Text("4. 绑定 NFC 登录")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onNfcRead, modifier = Modifier.fillMaxWidth()) {
            Text("5. NFC 读取（测试读卡器）")
        }
    }
}
