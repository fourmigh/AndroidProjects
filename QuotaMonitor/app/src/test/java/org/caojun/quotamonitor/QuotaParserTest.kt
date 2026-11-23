package org.caojun.quotamonitor

import org.caojun.quotamonitor.data.QuotaParser
import org.caojun.quotamonitor.model.Platform
import org.caojun.quotamonitor.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaParserTest {

    // ------------------------------------------------- OpenCode 额度页（登录后）

    /** 真实页面结构：Limits 卡片，三项指标 + 各自重置倒计时 + n% used。 */
    private val openCodeQuotaScan = """
        {"url":"https://opencode.ai/console","title":"Console",
         "hasPassword":false,"loginLike":false,"bars":[],
         "lines":["Limits","When your Go usage resets",
                  "5-hour window","Resets in 2h 15m","29% used",
                  "Weekly","Resets in 6d 18h","11% used",
                  "Monthly","Resets in 16d 2h","65% used",
                  "Go","Go Plus"]}
    """.trimIndent()

    @Test
    fun `opencode quota page parses three metrics with reset hints`() {
        val snap = QuotaParser.parseDom(Platform.OPENCODE, openCodeQuotaScan)
        assertNotNull(snap)
        val s = snap!!
        assertEquals(3, s.items.size)

        val fiveHour = s.items.first { it.label == "5-hour window" }
        assertEquals(29.0, fiveHour.percent!!, 0.01)
        assertEquals("Resets in 2h 15m", fiveHour.resetHint)

        val weekly = s.items.first { it.label == "Weekly" }
        assertEquals(11.0, weekly.percent!!, 0.01)
        assertEquals("Resets in 6d 18h", weekly.resetHint)

        val monthly = s.items.first { it.label == "Monthly" }
        assertEquals(65.0, monthly.percent!!, 0.01)
        assertEquals("Resets in 16d 2h", monthly.resetHint)

        assertEquals("Go", s.planName)
    }

    @Test
    fun `opencode marketing page returns null not fake data`() {
        // 未登录时的宣传页：60% off、Go 价格示例等绝不能被当成额度。
        val scan = """
            {"url":"https://opencode.ai/console","title":"OpenCode",
             "hasPassword":false,"loginLike":false,"bars":[],
             "lines":["Everything you need to work with AI","Get started",
                      "The cheapest inference on the planet",
                      "GLM 5.3 60% off","DeepSeek V4.1 Flash 60% off",
                      "Monthly subscription packages",
                      "Go · Go Plus · Updated 26m ago",
                      "Est. requests / 5 hr 45,300 90,600",
                      "Monthly usage $60 $120","Quick answers"]}
        """.trimIndent()
        assertNull(QuotaParser.parseDom(Platform.OPENCODE, scan))
    }

    @Test
    fun `opencode marketing lines detected as marketing`() {
        val lines = listOf(
            "Everything you need to work with AI", "Get started",
            "The cheapest inference on the planet", "Quick answers",
        )
        assertTrue(QuotaParser.looksLikeMarketing(lines))
        assertFalse(
            QuotaParser.looksLikeMarketing(
                listOf("Limits", "When your Go usage resets", "5-hour window"),
            ),
        )
    }

    // ------------------------------------------------- 火山引擎额度页（登录后）

    /** 真实页面结构：套餐信息 + 三项用量（大号百分比单独一行，刻度行紧随其后）。 */
    private val volcQuotaScan = """
        {"url":"https://console.volcengine.com/ark/region:cn-beijing/subscription/coding-plan",
         "title":"方舟","hasPassword":false,"loginLike":false,"bars":[],
         "lines":["Coding Plan Lite 套餐",
                  "开始时间","2026-11-20 08:58",
                  "结束时间","2026-12-20 23:59",
                  "剩余时间","28天",
                  "计费模式","包月",
                  "套餐用量限额",
                  "近5小时用量","48分后重置","100","%","0%","50%","100%",
                  "近一周用量","6天10小时后重置","13.51","%","0%","50%","100%",
                  "近一月用量","27天10小时后重置","20.21","%","0%","50%","100%",
                  "续费","升级"]}
    """.trimIndent()

    @Test
    fun `volc quota page parses three metrics with reset hints and plan info`() {
        val snap = QuotaParser.parseDom(Platform.VOLC, volcQuotaScan)
        assertNotNull(snap)
        val s = snap!!
        assertEquals(3, s.items.size)

        val fiveHour = s.items.first { it.label == "近5小时用量" }
        assertEquals(100.0, fiveHour.percent!!, 0.01)
        assertEquals("48分后重置", fiveHour.resetHint)

        val weekly = s.items.first { it.label == "近一周用量" }
        assertEquals(13.51, weekly.percent!!, 0.01)
        assertEquals("6天10小时后重置", weekly.resetHint)

        val monthly = s.items.first { it.label == "近一月用量" }
        assertEquals(20.21, monthly.percent!!, 0.01)
        assertEquals("27天10小时后重置", monthly.resetHint)

        assertEquals("Coding Plan Lite 套餐", s.planName)
        assertTrue(s.resetAt!!.contains("剩余 28天"))
        assertTrue(s.resetAt!!.contains("结束于 2026-12-20 23:59"))
    }

    @Test
    fun `volc login page returns null`() {
        val scan = """
            {"url":"https://console.volcengine.com/auth/login","title":"登录",
             "hasPassword":true,"loginLike":true,"bars":[],
             "lines":["火山引擎","账号登录","密码","验证码","登录","注册"]}
        """.trimIndent()
        assertNull(QuotaParser.parseDom(Platform.VOLC, scan))
    }

    @Test
    fun `volc other page without metrics returns null`() {
        val scan = """
            {"url":"https://console.volcengine.com/ark/models","title":"模型",
             "hasPassword":false,"loginLike":false,"bars":[],
             "lines":["模型列表"," doubao-seed-1.6 ","启用","调用"]}
        """.trimIndent()
        assertNull(QuotaParser.parseDom(Platform.VOLC, scan))
    }

    @Test
    fun `percent scale lines alone are not quota`() {
        // 只有 0%/50%/100% 刻度、没有指标名 → 不应识别为额度页。
        val scan = """
            {"url":"https://x","title":"","hasPassword":false,"loginLike":false,
             "bars":[],"lines":["0%","50%","100%"]}
        """.trimIndent()
        assertNull(QuotaParser.parseDom(Platform.OPENCODE, scan))
        assertNull(QuotaParser.parseDom(Platform.VOLC, scan))
    }

    // ------------------------------------------------- 网络层

    @Test
    fun `network json with used and limit pairs`() {
        val body = """
            {"plan":"Go","usage":[{"metric":"5-hour window","used":45300,"limit":90600},
             {"metric":"monthly","used":60,"limit":120}]}
        """.trimIndent()
        val items = QuotaParser.parseNetwork(Platform.OPENCODE, "https://opencode.ai/api/quota", body)
        assertNotNull(items)
        assertEquals(2, items!!.size)
        assertTrue(items.any { it.used == 45300.0 && it.total == 90600.0 })
        assertTrue(items.any { it.used == 60.0 && it.total == 120.0 })
    }

    @Test
    fun `network json with remaining derives used`() {
        val body = """{"name":"near-5h","remaining":25,"limit":100}"""
        val items = QuotaParser.parseNetwork(Platform.VOLC, "https://console.volcengine.com/api/subscription", body)
        assertNotNull(items)
        val item = items!!.first()
        assertEquals(75.0, item.used!!, 0.01)
        assertEquals(100.0, item.total!!, 0.01)
        assertEquals("near-5h", item.label)
    }

    @Test
    fun `non quota json returns null`() {
        val body = """{"theme":"dark","locale":"zh-CN"}"""
        assertNull(QuotaParser.parseNetwork(Platform.OPENCODE, "https://opencode.ai/api/preferences", body))
    }

    @Test
    fun `invalid json returns null`() {
        assertNull(QuotaParser.parseNetwork(Platform.OPENCODE, "https://x/api", "<html>oops</html>"))
    }

    // ------------------------------------------------- 网络数据准入（防误报）

    @Test
    fun `known metric labels accepted`() {
        assertTrue(QuotaParser.isKnownMetricLabel("5-hour window"))
        assertTrue(QuotaParser.isKnownMetricLabel("Weekly"))
        assertTrue(QuotaParser.isKnownMetricLabel("Monthly"))
        assertTrue(QuotaParser.isKnownMetricLabel("近5小时用量"))
        assertTrue(QuotaParser.isKnownMetricLabel("近一周用量"))
        assertTrue(QuotaParser.isKnownMetricLabel("近一月用量"))
        // 宣传页/普通字段不得混入
        assertFalse(QuotaParser.isKnownMetricLabel("额度"))
        assertFalse(QuotaParser.isKnownMetricLabel("theme"))
        assertFalse(QuotaParser.isKnownMetricLabel("requests"))
    }

    // ------------------------------------------------- 合并

    @Test
    fun `merge prefers dom items and keeps reset hints`() {
        val dom = QuotaParser.parseDom(Platform.OPENCODE, openCodeQuotaScan)!!
        val network = listOf(
            org.caojun.quotamonitor.model.QuotaItem(label = "5-hour window", used = 1.0, total = 2.0),
            org.caojun.quotamonitor.model.QuotaItem(label = "extra-metric", used = 3.0, total = 9.0),
        )
        val merged = QuotaParser.merge(network, dom)!!
        assertEquals(Source.DOM, merged.source)
        // DOM 的 29% 与 resetHint 不被网络数据覆盖
        val fiveHour = merged.items.first { it.label == "5-hour window" }
        assertEquals(29.0, fiveHour.percent!!, 0.01)
        assertEquals("Resets in 2h 15m", fiveHour.resetHint)
        // 网络独有指标作为补充
        assertTrue(merged.items.any { it.label == "extra-metric" })
    }

    @Test
    fun `merge with empty network returns dom as is`() {
        val dom = QuotaParser.parseDom(Platform.VOLC, volcQuotaScan)!!
        assertEquals(dom, QuotaParser.merge(emptyList(), dom))
    }

    @Test
    fun `merge without dom returns network snapshot`() {
        val network = listOf(org.caojun.quotamonitor.model.QuotaItem(label = "Monthly", percent = 65.0))
        val merged = QuotaParser.merge(network, null)!!
        assertEquals(Source.NETWORK, merged.source)
        assertEquals(1, merged.items.size)
    }

    @Test
    fun `merge with nothing returns null`() {
        assertNull(QuotaParser.merge(emptyList(), null))
    }
}
