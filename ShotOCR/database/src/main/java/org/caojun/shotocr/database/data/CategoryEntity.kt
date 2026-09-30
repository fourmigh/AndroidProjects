package org.caojun.shotocr.database.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    /**
     * 兜底主名称。
     * 内置分类存的是其 [id] 占位（显示时经资源与 [namesJson] 解析，读不到本字段）；
     * 自定义分类存的是创建时按当前语言填入的名称。
     */
    @ColumnInfo val name: String,
    /**
     * 各语言名称，形如 `{"zh":"水电煤","en":"Utilities"}`。
     * 键为 `Locale.getDefault().language`（当前实现只区分 zh / en）。
     */
    @ColumnInfo val namesJson: String = "{}",
    @ColumnInfo val parentId: String? = null,
    @ColumnInfo val isDefault: Boolean = false,
    @ColumnInfo val sortOrder: Int = 0
)
