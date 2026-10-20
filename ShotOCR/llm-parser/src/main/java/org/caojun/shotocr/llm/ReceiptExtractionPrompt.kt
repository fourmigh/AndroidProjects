package org.caojun.shotocr.llm

/**
 * 分平台账单字段抽取提示词：各平台共用 [BASE_RULES]，再拼该平台的专属规则与 1 个短示例，
 * 以适配模型较小的上下文（约 1280 tokens）。
 */
object ReceiptExtractionPrompt {

    const val BASE_RULES = """你是账单信息抽取助手。只输出一个 JSON 对象（无解释、无 markdown），字段：
amount 实付, discount 优惠, originalAmount 原价, storeName 店名, paymentTime 支付时间(yyyy-MM-dd HH:mm:ss), paymentMethod 付款方式, orderNumber 订单号, sourceApp 来源App。
规则：所有字段必须来自输入 OCR；示例只说明格式，严禁照抄示例中的数值、店名、时间、单号。
金额只填数字、去负号取绝对值（-0.03→0.03），未知填 null；无原价则 originalAmount=实付+优惠。
amount 取商户下方带负号的实付金额；若该行数字残缺，用 originalAmount - discount 计算。
originalAmount 取"订单金额/原价"；discount 取优惠/立减/红包/补贴/支付遇财神/福利等负数的绝对值之和。
"共减/共省/优惠合计"是优惠总额，勿与明细重复累加；"立减/优惠/补贴/积分/奖励"不是店名、也不是付款方式。
paymentTime 取"支付时间"后的日期时间；日期与时间之间没有空格或用 T 分隔也要识别，统一输出 yyyy-MM-dd HH:mm:ss；找不到填 null。
orderNumber 取"订单号/订单编号/交易单号/交易流水号/交易号"后的长数字（号码可能在标签的上一行或下一行；勿取"商户单号"）。"""

    private val PLATFORM_EXTRAS: Map<Platform, String> = mapOf(
        Platform.ALIPAY to """支付宝：店名是"账单详情"下的商户名；付款方式取"付款方式"行。
示例 输入：
账单详情
示例商户
-8.88
9.00
订单金额
支付遇财神-0.12
花呗
付款方式
支付时间
2026-10-0823:01:46
订单号
2026100823001465011447014045
输出：{"amount":8.88,"discount":0.12,"originalAmount":9.0,"storeName":"示例商户","paymentTime":"2026-10-08 23:01:46","paymentMethod":"花呗","orderNumber":"2026100823001465011447014045","sourceApp":"支付宝"}""",
        Platform.WECHAT to """微信：店名是"全部账单"下的商户名；付款方式取"支付方式"下的银行卡；orderNumber 取"交易单号"对应数字（可能在标签上一行或下一行），勿取"商户单号"。
示例 输入：
全部账单
上海市闵行中学
-461.38
原价
¥462.00
银行卡立减金优惠¥0.62
支付时间
2026-10-1317:11:55
兴业银行储蓄卡(5719)
支付方式
交易单号
4500000426202610139083205733
输出：{"amount":461.38,"discount":0.62,"originalAmount":462.0,"storeName":"上海市闵行中学","paymentTime":"2026-10-13 17:11:55","paymentMethod":"兴业银行储蓄卡(5719)","orderNumber":"4500000426202610139083205733","sourceApp":"微信"}""",
        Platform.UNIONPAY to """云闪付：店名是"云闪付交易详情/账单详情"下的商户名；付款方式取"付款方式"行；orderNumber 取"交易流水号"。
示例 输入：
云闪付交易详情
上海公共交通卡股份有限公司
-￥2.50
尤惠信息
动-¥0.50
¥3.00
订单金额
上海银行银联信用卡9320
付款方式
交易流水号
334425102113135896808
输出：{"amount":2.5,"discount":0.5,"originalAmount":3.0,"storeName":"上海公共交通卡股份有限公司","paymentTime":null,"paymentMethod":"上海银行银联信用卡9320","orderNumber":"334425102113135896808","sourceApp":"云闪付"}""",
        Platform.TAOBAO to """淘宝/天猫：店名是"进店逛逛"上一行的商家名；付款方式取"支付方式"行；orderNumber 取"支付宝交易号"。
示例 输入：
充值成功
茉莉的卡券快充
进店逛逛>
哈啰单车月卡
实付价¥21.8
订单信息共6项
支付宝交易号
2026100823001165011453404071
抖音月付
支付方式
输出：{"amount":21.8,"discount":null,"originalAmount":21.8,"storeName":"茉莉的卡券快充","paymentTime":null,"paymentMethod":"抖音月付","orderNumber":"2026100823001165011453404071","sourceApp":"淘宝"}""",
        Platform.JD to """京东：店名常含"旗舰店"；付款方式取"支付方式"行（如"白条账单"）；orderNumber 取"订单编号"。
示例 输入：
郑远元旗舰店
实付款
共减¥28.63合计¥126.37
订单编号
3648468013999049
支付方式
白条账单
输出：{"amount":126.37,"discount":28.63,"originalAmount":155.0,"storeName":"郑远元旗舰店","paymentTime":null,"paymentMethod":"白条账单","orderNumber":"3648468013999049","sourceApp":"京东"}""",
        Platform.DOUYIN to """抖音：付款方式取"支付方式"行（如"抖音月付"）；"抖音支付优惠"不是店名；orderNumber 取"交易单号"。
示例 输入：
库迪咖啡（北京）有限公司
-8.29
支付成功
¥0.01
抖音支付优惠
抖音月付
支付方式
交易单号
2001022610130706741247834599
输出：{"amount":8.29,"discount":0.01,"originalAmount":8.3,"storeName":"库迪咖啡（北京）有限公司","paymentTime":null,"paymentMethod":"抖音月付","orderNumber":"2001022610130706741247834599","sourceApp":"抖音"}""",
        Platform.GENERIC to """示例 输入：
账单详情
阿宝烧烤&红烧牛肉面
-15.97
16.00 订单金额
百次立减 -0.03
中国银行信用卡(7957)
支付时间
2026-10-1306:56:41
付款方式
订单号
2026101322001465011438637803
输出：{"amount":15.97,"discount":0.03,"originalAmount":16.0,"storeName":"阿宝烧烤&红烧牛肉面","paymentTime":"2026-10-13 06:56:41","paymentMethod":"中国银行信用卡(7957)","orderNumber":"2026101322001465011438637803","sourceApp":null}"""
    )

    const val USER_PREFIX = "请从下面的 OCR 文本中抽取账单字段，只输出 JSON：\n"

    fun systemInstruction(platform: Platform): String =
        BASE_RULES + "\n" + (PLATFORM_EXTRAS[platform] ?: PLATFORM_EXTRAS.getValue(Platform.GENERIC))

    fun userMessage(ocrText: String): String = USER_PREFIX + ocrText
}
