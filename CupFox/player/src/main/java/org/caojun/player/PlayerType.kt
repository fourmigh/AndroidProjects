package org.caojun.player

/** 播放方式。 */
enum class PlayerType(val storedName: String) {
    WEB("web"),
    MEDIA3("media3"),
    EXTERNAL("external");

    companion object {
        fun fromStored(value: String?): PlayerType =
            entries.firstOrNull { it.storedName == value } ?: WEB
    }
}
