package org.caojun.shotocr.classification.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.caojun.shotocr.classification.model.Category
import org.caojun.shotocr.database.category.DefaultCategories
import java.util.Locale

/**
 * 分类名称的唯一显示入口。回退顺序：
 * 1. 用户按当前语言填写的 [Category.names] 值；
 * 2. 用户填过的任一语言的名称（改名只填一栏时另一语言仍可显示）；
 * 3. 内置分类走 strings.xml 资源（随系统语言切换）；
 * 4. [Category.name] 兜底。
 *
 * 界面不得直接读取 [Category.name]。
 */
@Composable
fun Category.displayName(): String {
    names[Locale.getDefault().language]?.let { return it }
    names.values.firstOrNull()?.let { return it }
    if (isDefault) {
        DefaultCategories.nameResId(id)?.let { return stringResource(it) }
    }
    return name
}
