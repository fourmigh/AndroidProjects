package org.caojun.quotamonitor

import org.caojun.quotamonitor.data.PlatformNaming
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformNamingTest {

    @Test
    fun `keeps full page title`() {
        // OpenCode 浏览器标签：Activity | OpenCode Console —— 完整保留
        assertEquals(
            "Activity | OpenCode Console",
            PlatformNaming.derive("Activity | OpenCode Console", "https://opencode.ai/console/"),
        )
        // 火山方舟浏览器标签：火山方舟 - Coding plan —— 完整保留
        assertEquals(
            "火山方舟 - Coding plan",
            PlatformNaming.derive("火山方舟 - Coding plan", "https://console.volcengine.com/ark/"),
        )
        assertEquals("Dashboards", PlatformNaming.derive("Dashboards", "https://example.com"))
    }

    @Test
    fun `trims whitespace in title`() {
        assertEquals("Console", PlatformNaming.derive("  Console  ", "https://example.com"))
    }

    @Test
    fun `falls back to host when title empty`() {
        assertEquals("console.volcengine.com", PlatformNaming.derive("", "https://console.volcengine.com/ark"))
        assertEquals("example.com", PlatformNaming.derive(null, "https://www.example.com/usage"))
    }

    @Test
    fun `falls back to url when host unavailable`() {
        // URI 解析可得到 host 时优先 host；完全无法解析 host 时退回原始 URL
        assertEquals("example.com", PlatformNaming.derive(null, "https://example.com"))
        assertEquals("not-a-url", PlatformNaming.derive("", "not-a-url"))
    }

    @Test
    fun `extremely long title is truncated safely`() {
        val long = "A".repeat(120)
        assertEquals(PlatformNaming.MAX_LEN, PlatformNaming.derive(long, "https://example.com").length)
    }

    @Test
    fun `leading separator is preserved`() {
        // 不再按分隔符裁剪，行首符号也原样保留
        assertEquals("- Leading Dash", PlatformNaming.derive("- Leading Dash", "https://example.com"))
    }
}
