package org.caojun.quotamonitor

import org.caojun.quotamonitor.data.PlatformStore
import org.caojun.quotamonitor.data.QuotaAutoDetector
import org.caojun.quotamonitor.data.QuotaRules
import org.caojun.quotamonitor.model.MetricMode
import org.caojun.quotamonitor.model.MetricRule
import org.caojun.quotamonitor.model.PlatformConfig
import org.caojun.quotamonitor.model.QuotaItem
import org.caojun.quotamonitor.model.Source
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaRuleEngineTest {

    private fun defaults(): List<PlatformConfig> {
        val text = javaClass.classLoader!!.getResourceAsStream("default_platforms.json")!!
            .bufferedReader()
            .use { it.readText() }
            .trimStart('\uFEFF')
        return PlatformStore.parseConfigs(JSONObject(text).getJSONArray("platforms"))
    }

    private fun config(id: String): PlatformConfig =
        defaults().first { it.id == id }

    // ------------------------------------------------- OpenCode 额度页（登录后）

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
    fun `opencode default config parses three metrics with reset hints`() {
        val snap = QuotaRules.parseDom(config("opencode"), openCodeQuotaScan)
        assertNotNull(snap)
        val s = snap!!
        assertEquals(3, s.items.size)

        val fiveHour = s.items.first { it.label == "5-hour window" }
        assertEquals(29.0, fiveHour.percent!!, 0.01)
        assertEquals("29", fiveHour.percentText)
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
        val scan = """
            {"url":"https://opencode.ai/console","title":"OpenCode",
             "hasPassword":false,"loginLike":false,"bars":[],
             "lines":["Everything you need to work with AI","Get started",
                      "The cheapest inference on the planet",
                      "GLM 5.3 60% off","DeepSeek V4.1 Flash 60% off",
                      "Monthly subscription packages",
                      "Est. requests / 5 hr 45,300 90,600",
                      "Monthly usage $60 $120","Quick answers"]}
        """.trimIndent()
        assertNull(QuotaRules.parseDom(config("opencode"), scan))
        assertTrue(QuotaRules.looksLikeMarketing(config("opencode"), marketingLines))
    }

    private val marketingLines = listOf(
        "Everything you need to work with AI", "Get started",
        "The cheapest inference on the planet", "Quick answers",
    )

    @Test
    fun `opencode login-looking page flagged as need login`() {
        val scan = """
            {"url":"https://opencode.ai/console","title":"Sign in",
             "hasPassword":true,"loginLike":true,"bars":[],"lines":["Sign in","Email"]}
        """.trimIndent()
        assertNull(QuotaRules.parseDom(config("opencode"), scan))
        assertTrue(
            QuotaRules.looksLikeLogin(config("opencode"), "https://opencode.ai/login", true, true, emptyList())
        )
    }

    // ------------------------------------------------- 火山方舟额度页（登录后）

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
    fun `volc default config parses three metrics with reset hints and plan info`() {
        val snap = QuotaRules.parseDom(config("volc"), volcQuotaScan)
        assertNotNull(snap)
        val s = snap!!
        assertEquals(3, s.items.size)

        val fiveHour = s.items.first { it.label == "近5小时用量" }
        assertEquals(100.0, fiveHour.percent!!, 0.01)
        assertEquals("100", fiveHour.percentText)
        assertEquals("48分后重置", fiveHour.resetHint)

        val weekly = s.items.first { it.label == "近一周用量" }
        assertEquals(13.51, weekly.percent!!, 0.01)
        assertEquals("13.51", weekly.percentText)
        assertEquals("6天10小时后重置", weekly.resetHint)

        val monthly = s.items.first { it.label == "近一月用量" }
        assertEquals(20.21, monthly.percent!!, 0.01)
        assertEquals("20.21", monthly.percentText)
        assertEquals("27天10小时后重置", monthly.resetHint)

        assertEquals("Coding Plan Lite 套餐", s.planName)
        assertEquals("28天", s.remainText)
        assertEquals("2026-12-20 23:59", s.endText)
    }

    @Test
    fun `volc login page returns null`() {
        val scan = """
            {"url":"https://console.volcengine.com/auth/login","title":"登录",
             "hasPassword":true,"loginLike":true,"bars":[],
             "lines":["火山方舟","账号登录","密码","验证码","登录","注册"]}
        """.trimIndent()
        assertNull(QuotaRules.parseDom(config("volc"), scan))
        assertTrue(
            QuotaRules.looksLikeLogin(config("volc"), "https://console.volcengine.com/auth/login", true, true, emptyList())
        )
    }

    @Test
    fun `volc other page without metrics returns null`() {
        val scan = """
            {"url":"https://console.volcengine.com/ark/models","title":"模型",
             "hasPassword":false,"loginLike":false,"bars":[],
             "lines":["模型列表"," doubao-seed-1.6 ","启用","调用"]}
        """.trimIndent()
        assertNull(QuotaRules.parseDom(config("volc"), scan))
    }

    @Test
    fun `percent scale lines alone are not quota`() {
        val scan = """
            {"url":"https://x","title":"","hasPassword":false,"loginLike":false,
             "bars":[],"lines":["0%","50%","100%"]}
        """.trimIndent()
        assertNull(QuotaRules.parseDom(config("opencode"), scan))
        assertNull(QuotaRules.parseDom(config("volc"), scan))
    }

    // ------------------------------------------------- 自定义配置（无需改代码）

    @Test
    fun `custom config with inline percent works`() {
        val custom = PlatformConfig(
            id = "custom",
            name = "Custom AI Plan",
            url = "https://example.com/usage",
            metrics = listOf(
                MetricRule(label = "Requests", mode = MetricMode.PERCENT_INLINE),
                MetricRule(label = "Tokens", mode = MetricMode.PERCENT_NEXT_NUMBER),
            ),
        )
        val scan = """
            {"url":"https://example.com/usage","title":"","hasPassword":false,"loginLike":false,
             "bars":[],
             "lines":["Requests","42% used","Tokens","42.5","%"]}
        """.trimIndent()
        val snap = QuotaRules.parseDom(custom, scan)
        assertNotNull(snap)
        val requests = snap!!.items.first { it.label == "Requests" }
        assertEquals(42.0, requests.percent!!, 0.01)
        val tokens = snap.items.first { it.label == "Tokens" }
        assertEquals(42.5, tokens.percent!!, 0.01)
    }

    @Test
    fun `custom config with used total works`() {
        val custom = PlatformConfig(
            id = "custom2",
            name = "Pair Plan",
            url = "https://example.com",
            metrics = listOf(MetricRule(label = "Calls", mode = MetricMode.USED_TOTAL)),
        )
        val scan = """
            {"url":"https://example.com","title":"","hasPassword":false,"loginLike":false,
             "bars":[],"lines":["Calls","1,234 / 5,000"]}
        """.trimIndent()
        val snap = QuotaRules.parseDom(custom, scan)
        assertNotNull(snap)
        val item = snap!!.items.first()
        assertEquals(1234.0, item.used!!, 0.01)
        assertEquals(5000.0, item.total!!, 0.01)
    }

    // ------------------------------------------------- 自动识别向导

    @Test
    fun `detector finds opencode metrics`() {
        val found = QuotaAutoDetector.detect(openCodeQuotaScan)
        val labels = found.map { it.label }
        assertTrue(labels.any { it == "5-hour window" })
        assertTrue(labels.any { it.equals("Weekly", ignoreCase = true) })
        assertTrue(labels.any { it.equals("Monthly", ignoreCase = true) })
        val five = found.first { it.label == "5-hour window" }
        assertEquals(MetricMode.PERCENT_INLINE, five.mode)
        assertEquals("29%", five.sampleText)
        assertEquals("Resets in 2h 15m", five.resetHint)
        assertTrue(five.resetRegex!!.contains("\\d+"))
    }

    @Test
    fun `detector finds volc next number metrics`() {
        val found = QuotaAutoDetector.detect(volcQuotaScan)
        val labels = found.map { it.label }
        assertTrue(labels.any { it == "近5小时用量" })
        assertTrue(labels.any { it == "近一周用量" })
        assertTrue(labels.any { it == "近一月用量" })
        val weekly = found.first { it.label == "近一周用量" }
        assertEquals(MetricMode.PERCENT_NEXT_NUMBER, weekly.mode)
        assertEquals("13.51%", weekly.sampleText)
        assertEquals("6天10小时后重置", weekly.resetHint)
    }

    @Test
    fun `detector ignores reset rows and group titles`() {
        // 截图中的真实结构：分组标题 + 指标名 + 重置行 + 大号数字
        val scan = """
            {"url":"https://console.volcengine.com/ark/region:cn-beijing/subscription/coding-plan",
             "title":"火山方舟 - Coding plan","hasPassword":false,"loginLike":false,"bars":[],
             "lines":["套餐用量限额",
                      "近5小时用量","29.9","%","4小时37分后重置","0%","50%","100%",
                      "近一周用量","44.11","%","5天8小时后重置","0%","50%","100%",
                      "近一月用量","35.51","%","26天8小时后重置","0%","50%","100%"]}
        """.trimIndent()
        val found = QuotaAutoDetector.detect(scan)
        val labels = found.map { it.label }
        // 只有三项真实指标
        assertEquals(3, found.size)
        assertTrue(labels.containsAll(listOf("近5小时用量", "近一周用量", "近一月用量")))
        // 分组标题不能成为指标
        assertFalse(labels.contains("套餐用量限额"))
        // 重置倒计时行不能成为指标
        assertFalse(labels.any { it.contains("后重置") })
    }

    @Test
    fun `engine ignores reset-like metric labels from bad configs`() {
        val custom = PlatformConfig(
            id = "bad",
            name = "Bad",
            url = "https://example.com",
            metrics = listOf(
                MetricRule(label = "近一周用量", mode = MetricMode.PERCENT_NEXT_NUMBER),
                MetricRule(label = "5天8小时后重置", mode = MetricMode.PERCENT_NEXT_NUMBER),
            ),
        )
        val snap = QuotaRules.parseDom(custom, volcQuotaScan)
        assertNotNull(snap)
        assertEquals(1, snap!!.items.size)
        assertEquals("近一周用量", snap.items.first().label)
    }

    @Test
    fun `detector returns nothing for marketing page`() {
        val scan = """
            {"url":"https://x","title":"","hasPassword":false,"loginLike":false,"bars":[],
             "lines":["Get started","GLM 5.3 60% off","Monthly usage 60 120"]}
        """.trimIndent()
        val found = QuotaAutoDetector.detect(scan)
        assertTrue(found.none { it.label.contains("Get started") })
    }

    @Test
    fun `reset regex builder replaces digits`() {
        assertEquals("\\d+分后重置", QuotaAutoDetector.buildResetRegex("48分后重置"))
        assertTrue(QuotaAutoDetector.buildResetRegex("Resets in 2h 15m").contains("\\d+"))
    }

    // ------------------------------------------------- 配置校验

    @Test
    fun `validate accepts default configs and rejects bad ones`() {
        assertNull(PlatformStore.validate(defaults().first()))
        assertNull(PlatformStore.validate(defaults().last()))
        // URL 不合法
        assertEquals(
            PlatformStore.ValidationError.INVALID_URL,
            PlatformStore.validate(defaults().first().copy(url = "ftp://x")),
        )
        // 没有指标
        assertEquals(
            PlatformStore.ValidationError.NO_METRICS,
            PlatformStore.validate(defaults().first().copy(metrics = emptyList())),
        )
        // 正则不合法
        assertEquals(
            PlatformStore.ValidationError.INVALID_REGEX,
            PlatformStore.validate(
                defaults().first().copy(
                    metrics = listOf(MetricRule(label = "x", valueRegex = "([")),
                )
            ),
        )
    }

    @Test
    fun `merge prefers dom and filters unknown network labels`() {
        val cfg = config("opencode")
        val dom = QuotaRules.parseDom(cfg, openCodeQuotaScan)!!
        val network = listOf(
            QuotaItem(label = "5-hour window", used = 1.0, total = 2.0),
            QuotaItem(label = "unknown-metric", used = 3.0, total = 9.0),
        )
        val merged = QuotaRules.merge(network, dom, cfg)!!
        assertEquals(Source.DOM, merged.source)
        val fiveHour = merged.items.first { it.label == "5-hour window" }
        assertEquals(29.0, fiveHour.percent!!, 0.01)
        assertEquals(3, merged.items.size)
    }

    @Test
    fun `formatPercent prefers raw text from web page`() {
        assertEquals("29", org.caojun.quotamonitor.ui.formatPercentText("29", 29.0))
        assertEquals("13.51", org.caojun.quotamonitor.ui.formatPercentText("13.51", 13.51))
        assertEquals("100", org.caojun.quotamonitor.ui.formatPercentText("100", 100.0))
        assertEquals("13.510", org.caojun.quotamonitor.ui.formatPercentText("13.510", 13.51))
    }

    @Test
    fun `formatPercent falls back to stripped zeros when no raw text`() {
        assertEquals("29", org.caojun.quotamonitor.ui.formatPercentText(null, 29.0))
        assertEquals("13.51", org.caojun.quotamonitor.ui.formatPercentText(null, 13.51))
        assertEquals("100", org.caojun.quotamonitor.ui.formatPercentText(null, 100.0))
        assertEquals("0.5", org.caojun.quotamonitor.ui.formatPercentText(null, 0.5))
        assertEquals("29", org.caojun.quotamonitor.ui.formatPercentText("", 29.0))
        assertEquals("29", org.caojun.quotamonitor.ui.formatPercentText("29%", 29.0))
    }

    @Test
    fun `engine truncates window at next metric to avoid cross block values`() {
        // 复现问题：页面上 5-hour window 多次出现（图例/说明），
        // 且窗口若不被截断会跨到 Monthly 的 89% 或 Weekly 的 59%。
        val scan = """
            {"url":"https://opencode.ai/console","title":"Activity | OpenCode Console",
             "hasPassword":false,"loginLike":false,"bars":[],
             "lines":["5-hour window",
                      "Limits",
                      "Monthly",
                      "89% used",
                      "5-hour window",
                      "39% used",
                      "Weekly",
                      "59% used"]}
        """.trimIndent()
        val cfg = config("opencode").copy(
            metrics = listOf(
                MetricRule(label = "Monthly", mode = MetricMode.PERCENT_INLINE),
                MetricRule(label = "5-hour window", mode = MetricMode.PERCENT_INLINE),
                MetricRule(label = "Weekly", mode = MetricMode.PERCENT_INLINE),
            ),
        )
        val snap = QuotaRules.parseDom(cfg, scan)
        assertNotNull(snap)
        val s = snap!!
        assertEquals(3, s.items.size)
        assertEquals(89.0, s.items.first { it.label == "Monthly" }.percent!!, 0.01)
        assertEquals(39.0, s.items.first { it.label == "5-hour window" }.percent!!, 0.01)
        assertEquals(59.0, s.items.first { it.label == "Weekly" }.percent!!, 0.01)
    }

    @Test
    fun `diagnostics report matched line and value`() {
        val cfg = config("opencode")
        val diag = QuotaRules.diagnose(cfg, openCodeQuotaScan)
        assertEquals(3, diag.size)
        diag.forEach { d ->
            assertTrue(d.matchedIndex >= 0)
            assertNotNull(d.value)
        }
        assertEquals("29%", diag.first { it.label == "5-hour window" }.value)
    }

    @Test
    fun `detector does not treat subtitle as reset countdown`() {
        val scan = """
            {"url":"https://opencode.ai/console","title":"","hasPassword":false,"loginLike":false,"bars":[],
             "lines":["Limits","When your Go usage resets","5-hour window","39% used","Weekly","59% used"]}
        """.trimIndent()
        val found = QuotaAutoDetector.detect(scan)
        val five = found.firstOrNull { it.label == "5-hour window" }
        assertNotNull(five)
        // “When your Go usage resets”不含数字，不能作为重置倒计时
        assertNull(five!!.resetHint)
    }

    @Test
    fun `short page lines do not masquerade as metric labels`() {
        // 页面顶部有 "-"、"5-hour" 等短行，旧版 labelsMatch 反向包含会把它们
        // 误判成 5-hour window，从而取到顶部的 89%。
        val scan = """
            {"url":"https://opencode.ai/console","title":"","hasPassword":false,"loginLike":false,"bars":[],
             "lines":["-","5-hour","89% used",
                      "Limits","When your Go usage resets",
                      "5-hour window","Resets in 2h 15m","39% used",
                      "Weekly","Resets in 5d 8h","59% used",
                      "Monthly","Renews Dec 9","89% used"]}
        """.trimIndent()
        val snap = QuotaRules.parseDom(config("opencode"), scan)
        assertNotNull(snap)
        val s = snap!!
        assertEquals(39.0, s.items.first { it.label == "5-hour window" }.percent!!, 0.01)
        assertEquals(59.0, s.items.first { it.label == "Weekly" }.percent!!, 0.01)
        assertEquals(89.0, s.items.first { it.label == "Monthly" }.percent!!, 0.01)
    }

    @Test
    fun `prefers candidate without heading between label and value`() {
        // 同名标签出现在图例处（其后隔着未配置标题 My usage 才有 89%），
        // 真正的区块在后面（39%），应选真正区块。
        val scan = """
            {"url":"https://opencode.ai/console","title":"","hasPassword":false,"loginLike":false,"bars":[],
             "lines":["5-hour window","My usage","89% used",
                      "5-hour window","Resets in 2h 15m","39% used",
                      "Weekly","59% used"]}
        """.trimIndent()
        val cfg = config("opencode").copy(
            metrics = listOf(
                MetricRule(label = "5-hour window", mode = MetricMode.PERCENT_INLINE),
                MetricRule(label = "Weekly", mode = MetricMode.PERCENT_INLINE),
            ),
        )
        val snap = QuotaRules.parseDom(cfg, scan)
        assertNotNull(snap)
        assertEquals(39.0, snap!!.items.first { it.label == "5-hour window" }.percent!!, 0.01)
    }

    @Test
    fun `template matching by host inherits advanced rules`() {
        val template = PlatformStore.matchTemplate(defaults(), "https://opencode.ai/console/")
        assertNotNull(template)
        assertNotNull(template!!.planNameRegex)
        assertNotNull(template.pageMarkerRegex)
        val volc = PlatformStore.matchTemplate(defaults(), "https://console.volcengine.com/ark/region:cn-beijing/subscription/coding-plan")
        assertNotNull(volc)
        assertEquals("剩余时间", volc!!.remainKey)
        assertNull(PlatformStore.matchTemplate(defaults(), "https://unknown.example.com/usage"))
    }

    @Test
    fun `default configs include expected platform ids`() {
        val ids = defaults().map { it.id }
        assertTrue(ids.contains("opencode"))
        assertTrue(ids.contains("volc"))
        assertFalse(defaults().any { it.metrics.isEmpty() })
    }
}
