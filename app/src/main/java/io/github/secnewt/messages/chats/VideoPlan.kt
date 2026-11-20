package io.github.secnewt.messages.chats

/**
 * How small a video has to become to fit in a picture message. Carriers allow very little
 * (often 300 KB to 1 MB in all), so a video is made smaller in picture and lower in quality,
 * enough to fit with a little room to spare.
 */
object VideoPlan {

    /** Sound, kept low and mono: speech stays clear. */
    const val AUDIO_BITS_PER_SECOND = 32_000

    /** Below this the picture is too blurry to be worth sending. */
    const val MIN_VIDEO_BITS_PER_SECOND = 48_000

    data class Plan(val videoBitsPerSecond: Int, val height: Int)

    /**
     * The quality and height that fit [maxBytes] for a video [durationMillis] long, or null if
     * it's too long to fit at any watchable quality.
     */
    fun plan(durationMillis: Long, maxBytes: Long): Plan? {
        if (durationMillis <= 0 || maxBytes <= 0) return null
        val seconds = durationMillis / 1000.0
        // 85%: the container and the encoder's overshoot need some room.
        val total = maxBytes * 8 * 0.85 / seconds
        val video = (total - AUDIO_BITS_PER_SECOND).toInt()
        if (video < MIN_VIDEO_BITS_PER_SECOND) return null
        val height = when {
            video >= 1_000_000 -> 720
            video >= 500_000 -> 480
            video >= 200_000 -> 360
            else -> 240
        }
        return Plan(minOf(video, 2_000_000), height)
    }

    /** The longest video that could fit [maxBytes], in whole seconds, for the error message. */
    fun longestSeconds(maxBytes: Long): Long =
        (maxBytes * 8 * 0.85 / (MIN_VIDEO_BITS_PER_SECOND + AUDIO_BITS_PER_SECOND)).toLong()
}
