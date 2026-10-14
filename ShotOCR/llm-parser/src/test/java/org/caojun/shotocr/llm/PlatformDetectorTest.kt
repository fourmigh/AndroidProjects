package org.caojun.shotocr.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformDetectorTest {

    @Test
    fun detectFromLabel_mapsKnownApps() {
        assertEquals(Platform.ALIPAY, PlatformDetector.detectFromLabel("支付宝"))
        assertEquals(Platform.WECHAT, PlatformDetector.detectFromLabel("微信"))
        assertEquals(Platform.UNIONPAY, PlatformDetector.detectFromLabel("云闪付"))
        assertEquals(Platform.TAOBAO, PlatformDetector.detectFromLabel("手机淘宝"))
        assertEquals(Platform.JD, PlatformDetector.detectFromLabel("京东"))
        assertEquals(Platform.DOUYIN, PlatformDetector.detectFromLabel("抖音"))
        assertNull(PlatformDetector.detectFromLabel("某个应用"))
    }

    @Test
    fun detectFromText_matchesSignatures() {
        assertEquals(Platform.UNIONPAY, PlatformDetector.detectFromText("云闪付交易详情\n某商户"))
        assertEquals(Platform.WECHAT, PlatformDetector.detectFromText("财付通支付科技有限公司"))
        assertEquals(Platform.TAOBAO, PlatformDetector.detectFromText("进店逛逛>\n茉莉的卡券快充"))
        assertEquals(Platform.JD, PlatformDetector.detectFromText("评价得40京豆"))
        assertEquals(Platform.DOUYIN, PlatformDetector.detectFromText("抖音月付\n支付方式"))
        assertNull(PlatformDetector.detectFromText("毫无特征的文本"))
    }

    @Test
    fun detect_prefersLabelOverText() {
        assertEquals(Platform.TAOBAO, PlatformDetector.detect("淘宝", "财付通"))
    }
}
