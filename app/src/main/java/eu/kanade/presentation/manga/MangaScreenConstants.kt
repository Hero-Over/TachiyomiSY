package eu.kanade.presentation.manga

enum class DownloadAction {
    NEXT_1_CHAPTER,
    NEXT_5_CHAPTERS,
    NEXT_10_CHAPTERS,
    CUSTOM,
    UNREAD_CHAPTERS,
    ALL_CHAPTERS
}

enum class ChapterDownloadAction {
    START,
    START_NOW,
    CANCEL,
    DELETE,
}

enum class EditCoverAction {
    EDIT,
    DELETE,
}

enum class MangaScreenItem {
    INFO_BOX,
    ACTION_ROW,

    // SY -->
    METADATA_INFO,

    // SY <--
    DESCRIPTION_WITH_TAG,

    // SY -->
    INFO_BUTTONS,

    // SY <--
    CHAPTER_HEADER,
    CHAPTER,
}
