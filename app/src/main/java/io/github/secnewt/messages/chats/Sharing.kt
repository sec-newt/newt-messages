package io.github.secnewt.messages.chats

/** The banner over the chat list while something shared from another app waits for a chat. */
object Sharing {

    /** "Send 2 pictures to…", "Send a video to…", "Send these words to…". */
    fun label(files: List<Attachment>, words: String): String {
        val pictures = files.count { it.type.startsWith("image/") }
        val videos = files.count { it.type.startsWith("video/") }
        val what = when {
            files.isEmpty() -> if (words.isNotBlank()) "these words" else "this"
            pictures == files.size -> if (pictures == 1) "a picture" else "$pictures pictures"
            videos == files.size -> if (videos == 1) "a video" else "$videos videos"
            files.size == 1 -> files.first().name
            else -> "${files.size} files"
        }
        return "Send $what to…"
    }
}
