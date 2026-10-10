package org.caojun.shotocr.classification.classifier

/**
 * 分类名近似匹配工具：用于分类管理页的搜索。
 * 相似度取「子串命中 / 编辑距离 / 字符集合 Jaccard」三者最大值，忽略大小写。
 */
object CategoryTextMatcher {

    /** 输入与分类名的相似度，范围 0~1。 */
    fun nameScore(query: String, name: String): Double {
        val q = query.trim().lowercase()
        val n = name.trim().lowercase()
        if (q.isEmpty() || n.isEmpty()) return 0.0
        if (n.contains(q)) return 1.0
        return maxOf(editSimilarity(q, n), jaccard(q, n))
    }

    /** 归一化编辑距离相似度：1 - 编辑距离 / 最大长度。 */
    fun editSimilarity(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxLen
    }

    fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[n]
    }

    /** 字符集合的 Jaccard 相似度。 */
    fun jaccard(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val sa = a.toSet()
        val sb = b.toSet()
        val union = sa.union(sb).size
        if (union == 0) return 0.0
        return sa.intersect(sb).size.toDouble() / union
    }
}
