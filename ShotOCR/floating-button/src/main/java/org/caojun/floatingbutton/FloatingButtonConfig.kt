package org.caojun.floatingbutton

import androidx.annotation.DrawableRes

enum class FloatingButtonEdgeTapAction(val id: String) {
    TRIGGER("trigger"),
    REVEAL_FIRST("reveal_first");

    companion object {
        fun fromId(id: String?): FloatingButtonEdgeTapAction =
            entries.firstOrNull { it.id == id } ?: TRIGGER
    }
}

enum class FloatingButtonShape(val id: String) {
    CIRCLE("circle"),
    ROUNDED_RECT("rounded_rect"),
    SQUARE("square");

    companion object {
        fun fromId(id: String?): FloatingButtonShape =
            entries.firstOrNull { it.id == id } ?: CIRCLE
    }
}

enum class FloatingButtonIcon(val id: String, @param:DrawableRes val drawableRes: Int) {
    CAMERA("camera", android.R.drawable.ic_menu_camera),
    IMAGE("image", android.R.drawable.ic_menu_gallery),
    CROP("crop", android.R.drawable.ic_menu_crop),
    SEARCH("search", android.R.drawable.ic_menu_search),
    SETTINGS("settings", android.R.drawable.ic_menu_preferences),
    EDIT("edit", android.R.drawable.ic_menu_edit),
    CALENDAR("calendar", android.R.drawable.ic_menu_my_calendar),
    AGENDA("agenda", android.R.drawable.ic_menu_agenda),
    ADD("add", android.R.drawable.ic_menu_add),
    SHARE("share", android.R.drawable.ic_menu_share),
    RECENT("recent", android.R.drawable.ic_menu_recent_history),
    INFO("info", android.R.drawable.ic_menu_info_details),
    DELETE("delete", android.R.drawable.ic_menu_delete),
    SAVE("save", android.R.drawable.ic_menu_save);

    companion object {
        fun fromId(id: String?): FloatingButtonIcon =
            entries.firstOrNull { it.id == id } ?: CAMERA
    }
}

data class FloatingButtonConfig(
    val sizeDp: Int = DEFAULT_SIZE_DP,
    val edgeTapAction: FloatingButtonEdgeTapAction = FloatingButtonEdgeTapAction.TRIGGER,
    val edgeAutoHideMs: Long = DEFAULT_EDGE_AUTO_HIDE_MS,
    val shape: FloatingButtonShape = FloatingButtonShape.CIRCLE,
    val backgroundColor: Int = DEFAULT_BACKGROUND_COLOR,
    val opacity: Float = DEFAULT_OPACITY,
    val icon: FloatingButtonIcon = FloatingButtonIcon.CAMERA
) {

    fun normalized(): FloatingButtonConfig = copy(
        sizeDp = sizeDp.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP),
        edgeAutoHideMs = edgeAutoHideMs.coerceIn(MIN_EDGE_AUTO_HIDE_MS, MAX_EDGE_AUTO_HIDE_MS),
        backgroundColor = backgroundColor or OPAQUE_MASK,
        opacity = opacity.coerceIn(MIN_OPACITY, MAX_OPACITY)
    )

    companion object {
        const val MIN_SIZE_DP = 40
        const val MAX_SIZE_DP = 80
        const val DEFAULT_SIZE_DP = 56

        const val MIN_EDGE_AUTO_HIDE_MS = 1_000L
        const val MAX_EDGE_AUTO_HIDE_MS = 10_000L
        const val DEFAULT_EDGE_AUTO_HIDE_MS = 2_500L

        const val MIN_OPACITY = 0.2f
        const val MAX_OPACITY = 1f
        const val DEFAULT_OPACITY = 0.9f

        val DEFAULT_BACKGROUND_COLOR = 0xFF212121.toInt()
        private val OPAQUE_MASK = 0xFF000000.toInt()
    }
}
