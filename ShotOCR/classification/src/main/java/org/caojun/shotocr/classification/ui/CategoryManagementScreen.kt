package org.caojun.shotocr.classification.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.caojun.shotocr.classification.R
import org.caojun.shotocr.classification.model.Category
import org.caojun.shotocr.classification.model.CategoryGroup
import org.caojun.shotocr.classification.model.buildNames
import org.caojun.shotocr.classification.model.toGroups
import org.caojun.shotocr.classification.repository.CategoryRepository
import org.caojun.shotocr.classification.repository.CategoryRuleRepository
import org.caojun.shotocr.database.category.DefaultCategoryRules
import org.caojun.shotocr.translate.ui.TranslatableI18nFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val categories by remember {
        CategoryRepository.observeCategories()
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val groups = remember(categories) { categories.toGroups() }
    val expandedIds = remember { mutableStateOf(setOf<String>()) }

    var editing by remember { mutableStateOf<EditingTarget?>(null) }
    var addingSubTo by remember { mutableStateOf<Category?>(null) }
    var showAddTopDialog by remember { mutableStateOf(false) }
    var deleteBlockedNotice by remember { mutableStateOf<Category?>(null) }
    var editingKeywords by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddTopDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.category_add_top))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (groups.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.category_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            groups.forEach { group ->
                item(key = group.category.id) {
                    CategoryGroupCard(
                        group = group,
                        expanded = group.category.id in expandedIds.value,
                        onToggleExpand = {
                            expandedIds.value = expandedIds.value.let {
                                if (group.category.id in it) it - group.category.id else it + group.category.id
                            }
                        },
                        onAddChild = { addingSubTo = group.category },
                        onRename = { editing = EditingTarget(it) },
                        onEditKeywords = { editingKeywords = it },
                        onDelete = { category ->
                            val hasChildren = groups
                                .firstOrNull { it.category.id == category.id }
                                ?.children
                                ?.isNotEmpty() == true
                            if (category.isTopLevel && hasChildren) {
                                deleteBlockedNotice = category
                            } else {
                                scope.launch { CategoryRepository.deleteCustom(category) }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddTopDialog) {
        CategoryI18nDialog(
            title = stringResource(R.string.category_add_top),
            initialZh = "",
            initialEn = "",
            onConfirm = { zh, en ->
                scope.launch { CategoryRepository.addTopCategory(buildNames(zh, en)) }
                showAddTopDialog = false
            },
            onDismiss = { showAddTopDialog = false }
        )
    }

    addingSubTo?.let { parent ->
        CategoryI18nDialog(
            title = stringResource(R.string.category_add_sub),
            initialZh = "",
            initialEn = "",
            onConfirm = { zh, en ->
                scope.launch { CategoryRepository.addSubCategory(parent.id, buildNames(zh, en)) }
                addingSubTo = null
            },
            onDismiss = { addingSubTo = null }
        )
    }

    deleteBlockedNotice?.let { category ->
        AlertDialog(
            onDismissRequest = { deleteBlockedNotice = null },
            title = { Text(stringResource(R.string.category_delete_blocked_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.category_delete_blocked_message,
                        category.displayName()
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteBlockedNotice = null }) {
                    Text(stringResource(R.string.action_got_it))
                }
            }
        )
    }

    editing?.let { target ->
        val lang = Locale.getDefault().language
        val fallback = target.category.displayName()
        val names = target.category.names
        val restore: (() -> Unit)? = if (target.category.isDefault) {
            {
                scope.launch { CategoryRepository.restoreDefaultName(target.category) }
                editing = null
            }
        } else {
            null
        }
        CategoryI18nDialog(
            title = stringResource(R.string.action_rename),
            initialZh = names["zh"] ?: if (lang == "zh") fallback else "",
            initialEn = names["en"] ?: if (lang == "en") fallback else "",
            onConfirm = { zh, en ->
                scope.launch { CategoryRepository.rename(target.category, buildNames(zh, en)) }
                editing = null
            },
            onRestoreDefault = restore,
            onDismiss = { editing = null }
        )
    }

    editingKeywords?.let { category ->
        CategoryKeywordsDialog(
            category = category,
            onDismiss = { editingKeywords = null }
        )
    }
}

private data class EditingTarget(val category: Category)

@Composable
private fun CategoryGroupCard(
    group: CategoryGroup,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddChild: () -> Unit,
    onRename: (Category) -> Unit,
    onEditKeywords: (Category) -> Unit,
    onDelete: (Category) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.category_collapse else R.string.category_expand
                        )
                    )
                }
                Text(
                    text = group.category.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onAddChild() }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.category_add_sub)
                    )
                }
                IconButton(onClick = { onRename(group.category) }) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.action_rename)
                    )
                }
                if (!group.category.isDefault) {
                    IconButton(onClick = { onDelete(group.category) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (expanded) {
                if (group.children.isEmpty()) {
                    Text(
                        text = stringResource(R.string.category_no_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                    )
                } else {
                    group.children.forEach { child ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = child.displayName(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onEditKeywords(child) }) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.category_keywords_entry),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { onRename(child) }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.action_rename),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (!child.isDefault) {
                                IconButton(onClick = { onDelete(child) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 多语言分类名对话框。新增与重命名共用。
 *
 * @param initialZh 初始中文名，新增时传空串
 * @param initialEn 初始英文名，新增时传空串
 * @param onConfirm 两栏至少一栏非空才可提交，空白栏不入结果
 * @param onRestoreDefault 非空时显示「恢复默认」，仅内置分类传入
 */
@Composable
fun CategoryI18nDialog(
    title: String,
    initialZh: String,
    initialEn: String,
    onConfirm: (zh: String, en: String) -> Unit,
    onRestoreDefault: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var zhValue by remember(initialZh, initialEn) { mutableStateOf(TextFieldValue(initialZh)) }
    var enValue by remember(initialZh, initialEn) { mutableStateOf(TextFieldValue(initialEn)) }
    val zh = zhValue.text
    val en = enValue.text
    val canConfirm = zh.isNotBlank() || en.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TranslatableI18nFields(
                zhValue = zhValue,
                onZhValueChange = { zhValue = it },
                zhLabel = stringResource(R.string.category_name_zh),
                enValue = enValue,
                onEnValueChange = { enValue = it },
                enLabel = stringResource(R.string.category_name_en),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (canConfirm) onConfirm(zh.trim(), en.trim()) },
                enabled = canConfirm
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            Row {
                if (onRestoreDefault != null) {
                    TextButton(onClick = onRestoreDefault) {
                        Text(stringResource(R.string.category_restore_default))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

/**
 * 分类关键词规则编辑对话框。多行文本，每行一个关键词。
 * 内置分类提供「恢复默认」。
 */
@Composable
private fun CategoryKeywordsDialog(
    category: Category,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var canRestore by remember { mutableStateOf(false) }

    LaunchedEffect(category.id) {
        val rule = CategoryRuleRepository.getByCategory(category.id)
        text = rule?.keywords?.joinToString("\n")
            ?: DefaultCategoryRules.keywordsFor(category.id)?.joinToString("\n")
            ?: ""
        canRestore = DefaultCategoryRules.keywordsFor(category.id) != null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.category_keywords_title) + " · " + category.displayName())
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.category_keywords_label)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.category_keywords_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val keywords = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                scope.launch {
                    CategoryRuleRepository.upsert(category.id, keywords)
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            Row {
                if (canRestore) {
                    TextButton(onClick = {
                        scope.launch {
                            CategoryRuleRepository.restoreDefault(category.id)
                            onDismiss()
                        }
                    }) {
                        Text(stringResource(R.string.category_restore_default))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}
