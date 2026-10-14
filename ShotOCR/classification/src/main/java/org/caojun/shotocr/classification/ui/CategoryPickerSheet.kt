package org.caojun.shotocr.classification.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.caojun.shotocr.classification.R
import org.caojun.shotocr.classification.classifier.CategoryRule
import org.caojun.shotocr.classification.model.Category
import org.caojun.shotocr.classification.model.CategoryGroup
import org.caojun.shotocr.classification.model.toGroups
import org.caojun.shotocr.classification.repository.CategoryRepository
import org.caojun.shotocr.classification.repository.CategoryRuleRepository

/**
 * 账单分类选择卡片。显示当前分类，点击弹出选择面板。
 */
@Composable
fun CategorySelectField(
    categoryId: String,
    modifier: Modifier = Modifier,
    onCategorySelected: (String) -> Unit
) {
    val categories by remember {
        CategoryRepository.observeCategories()
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val groups = remember(categories) { categories.toGroups() }
    var showSheet by remember { mutableStateOf(false) }

    val currentName = categories.firstOrNull { it.id == categoryId }?.displayName()
        ?: stringResource(org.caojun.shotocr.database.R.string.category_uncategorized)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.category_field_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSheet = true }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.category_tap_to_edit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        HorizontalDivider()
    }

    if (showSheet) {
        CategoryPickerSheet(
            groups = groups,
            selectedId = categoryId,
            onDismiss = { showSheet = false },
            onSelect = {
                onCategorySelected(it)
                showSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    groups: List<CategoryGroup>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    var searchText by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf<List<CategoryRule>>(emptyList()) }

    LaunchedEffect(Unit) {
        rules = CategoryRuleRepository.loadRules()
    }

    val query = searchText.trim()
    val visibleGroups = remember(query, groups, rules) {
        if (query.isEmpty()) {
            groups
        } else {
            groups.mapNotNull { group ->
                val matchedChildren = group.children.filter { child ->
                    categoryScore(context, child, query, rules) >= CATEGORY_SEARCH_THRESHOLD
                }
                val groupMatches = categoryScore(context, group.category, query, rules) >= CATEGORY_SEARCH_THRESHOLD
                if (groupMatches || matchedChildren.isNotEmpty()) {
                    group.copy(children = matchedChildren.ifEmpty { group.children })
                } else {
                    null
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text(stringResource(R.string.category_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (visibleGroups.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.category_search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
                visibleGroups.forEach { group ->
                    item(key = "group-${group.category.id}") {
                        Text(
                            text = group.category.displayName(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                        )
                    }
                    items(group.children, key = { it.id }) { child ->
                        CategoryPickerRow(
                            category = child,
                            selected = child.id == selectedId,
                            onClick = { onSelect(child.id) }
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryPickerRow(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category.displayName(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.category_selected),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
