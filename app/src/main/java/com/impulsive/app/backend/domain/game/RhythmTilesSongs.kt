package com.impulsive.app.backend.domain.game

/**
 * A single note in a Rhythm Tiles melody.
 *
 * @param semitone pitch as semitones relative to middle C, where C4 = 0.
 * @param beats duration in beats at the song tempo.
 * @param isRest true when this step is a musical pause and should not spawn a tile.
 */
data class RhythmNote(
    val semitone: Int,
    val beats: Float = 1f,
    val isRest: Boolean = false,
)

/**
 * A playable melody. These are simple public-domain or traditional-style
 * monophonic arrangements for the in-app synthesized Rhythm Tiles note player.
 */
data class RhythmSong(
    val id: String,
    val title: String,
    val bpm: Int,
    val notes: List<RhythmNote>,
)

object RhythmTilesCatalog {

    private val twinkleTheme = phrase(
        n(0), n(0), n(7), n(7), n(9), n(9), n(7, 2f), rest(0.5f),
        n(5), n(5), n(4), n(4), n(2), n(2), n(0, 2f), rest(0.5f),
    )

    private val twinkleResponse = phrase(
        n(7), n(7), n(5), n(5), n(4), n(4), n(2, 2f), rest(0.5f),
        n(7), n(7), n(5), n(5), n(4), n(4), n(2, 2f), rest(0.5f),
    )

    private val twinkleBridge = phrase(
        n(0), n(2), n(4), n(5), n(7, 1.5f), n(5, 0.5f), n(4), n(2),
        n(0), n(2), n(4), n(7), n(9, 1.5f), n(7, 0.5f), n(5), n(4),
        n(2), n(4), n(5), n(7), n(5), n(4), n(2), n(0, 2f), rest(1f),
    )

    private val odeTheme = phrase(
        n(4), n(4), n(5), n(7), n(7), n(5), n(4), n(2),
        n(0), n(0), n(2), n(4), n(4, 1.5f), n(2, 0.5f), n(2, 2f), rest(0.5f),
    )

    private val odeResponse = phrase(
        n(4), n(4), n(5), n(7), n(7), n(5), n(4), n(2),
        n(0), n(0), n(2), n(4), n(2, 1.5f), n(0, 0.5f), n(0, 2f), rest(0.5f),
    )

    private val odeBridge = phrase(
        n(2), n(2), n(4), n(0), n(2), n(4, 0.5f), n(5, 0.5f), n(4), n(0),
        n(2), n(4, 0.5f), n(5, 0.5f), n(4), n(2), n(0), n(2), n(-5, 2f), rest(1f),
    )

    private val furTheme = phrase(
        n(16, 0.5f), n(15, 0.5f), n(16, 0.5f), n(15, 0.5f), n(16, 0.5f),
        n(11, 0.5f), n(14, 0.5f), n(12, 0.5f), n(9, 1f), rest(0.25f),
        n(0, 0.5f), n(4, 0.5f), n(9, 0.5f), n(11, 1f),
        n(4, 0.5f), n(8, 0.5f), n(11, 0.5f), n(12, 1f), rest(0.5f),
    )

    private val furResponse = phrase(
        n(4, 0.5f), n(16, 0.5f), n(15, 0.5f), n(16, 0.5f), n(15, 0.5f), n(16, 0.5f),
        n(11, 0.5f), n(14, 0.5f), n(12, 0.5f), n(9, 1f), rest(0.25f),
        n(0, 0.5f), n(4, 0.5f), n(9, 0.5f), n(11, 1f),
        n(4, 0.5f), n(12, 0.5f), n(11, 0.5f), n(9, 1.5f), rest(0.5f),
    )

    private val furBridge = phrase(
        n(11, 0.5f), n(12, 0.5f), n(14, 0.5f), n(16, 1f),
        n(5, 0.5f), n(17, 0.5f), n(16, 0.5f), n(14, 0.5f), n(7, 1f),
        n(16, 0.5f), n(14, 0.5f), n(12, 0.5f), n(9, 1f),
        n(11, 0.5f), n(12, 0.5f), n(14, 0.5f), n(12, 0.5f), n(11, 0.5f), rest(1f),
    )

    private val maryTheme = phrase(
        n(4), n(2), n(0), n(2), n(4), n(4), n(4, 2f), rest(0.5f),
        n(2), n(2), n(2, 2f), rest(0.5f), n(4), n(7), n(7, 2f), rest(0.5f),
    )

    private val maryResponse = phrase(
        n(4), n(2), n(0), n(2), n(4), n(4), n(4), n(4),
        n(2), n(2), n(4), n(2), n(0, 3f), rest(1f),
    )

    private val maryBridge = phrase(
        n(0), n(2), n(4), n(7), n(9), n(7), n(4, 2f), rest(0.5f),
        n(7), n(9), n(7), n(4), n(2), n(0), n(2, 2f), rest(0.5f),
    )

    private val bridgeTheme = phrase(
        n(7), n(9), n(7), n(5), n(4), n(5), n(7, 2f), rest(0.5f),
        n(2), n(4), n(5, 2f), rest(0.5f), n(4), n(5), n(7, 2f), rest(0.5f),
    )

    private val bridgeResponse = phrase(
        n(7), n(9), n(7), n(5), n(4), n(5), n(7, 2f), rest(0.5f),
        n(2), n(7), n(4), n(0, 3f), rest(1f),
    )

    private val bridgeMiddle = phrase(
        n(0), n(2), n(4), n(5), n(7), n(9), n(7, 2f), rest(0.5f),
        n(9), n(7), n(5), n(4), n(2), n(4), n(5, 2f), rest(0.5f),
    )

    private val jingleTheme = phrase(
        n(4), n(4), n(4, 2f), rest(0.25f), n(4), n(4), n(4, 2f), rest(0.25f),
        n(4), n(7), n(0), n(2), n(4, 4f), rest(0.5f),
    )

    private val jingleResponse = phrase(
        n(5), n(5), n(5), n(5), n(5), n(4), n(4), n(4),
        n(4), n(2), n(2), n(4), n(2, 2f), n(7, 2f), rest(0.5f),
    )

    private val jingleBridge = phrase(
        n(7), n(7), n(5), n(2), n(0, 2f), rest(0.5f),
        n(7), n(7), n(5), n(2), n(4, 2f), rest(0.5f),
        n(9), n(9), n(7), n(5), n(4), n(2), n(0, 2f), rest(0.5f),
    )

    private val fifthTheme = phrase(
        rest(0.5f), n(7, 0.5f), n(7, 0.5f), n(7, 0.5f), n(3, 2f), rest(0.5f),
        n(5, 0.5f), n(5, 0.5f), n(5, 0.5f), n(2, 2f), rest(0.5f),
    )

    private val fifthResponse = phrase(
        n(7, 0.5f), n(7, 0.5f), n(7, 0.5f), n(3, 1f),
        n(5, 0.5f), n(5, 0.5f), n(5, 0.5f), n(2, 1f),
        n(3, 0.5f), n(5, 0.5f), n(7, 0.5f), n(8, 1f),
        n(7, 0.5f), n(5, 0.5f), n(3, 1.5f), rest(0.5f),
    )

    private val fifthBridge = phrase(
        n(7, 0.5f), n(7, 0.5f), n(7, 0.5f), n(3, 1f),
        n(8, 0.5f), n(8, 0.5f), n(8, 0.5f), n(5, 1f),
        n(10, 0.5f), n(10, 0.5f), n(10, 0.5f), n(7, 1.5f), rest(0.5f),
    )

    private val saintsTheme = phrase(
        n(0), n(4), n(5), n(7, 2f), rest(0.5f),
        n(0), n(4), n(5), n(7, 2f), rest(0.5f),
    )

    private val saintsResponse = phrase(
        n(0), n(4), n(5), n(7), n(4), n(0), n(4), n(2, 2f), rest(0.5f),
    )

    private val saintsBridge = phrase(
        n(4), n(4), n(2), n(0), n(0), n(4), n(7), n(7),
        n(7), n(5), n(4), n(5), n(7), n(4), n(0), n(2), n(0, 2f), rest(0.5f),
    )

    val songs: List<RhythmSong> = listOf(
        song(
            id = "TWINKLE_TWINKLE",
            title = "Twinkle Twinkle",
            bpm = 120,
            theme = twinkleTheme,
            response = twinkleResponse,
            bridge = twinkleBridge,
            ending = phrase(n(7), n(5), n(4), n(2), n(0, 4f), rest(2f)),
        ),
        song(
            id = "ODE_TO_JOY",
            title = "Ode to Joy",
            bpm = 124,
            theme = odeTheme,
            response = odeResponse,
            bridge = odeBridge,
            ending = phrase(n(4), n(5), n(7), n(5), n(4), n(2), n(0, 4f), rest(2f)),
        ),
        song(
            id = "FUR_ELISE",
            title = "Fur Elise",
            bpm = 124,
            theme = furTheme,
            response = furResponse,
            bridge = furBridge,
            ending = phrase(n(16, 0.5f), n(15, 0.5f), n(16, 0.5f), n(11, 0.5f), n(14), n(12), n(9, 4f), rest(2f)),
        ),
        song(
            id = "MARY_LAMB",
            title = "Mary Lamb",
            bpm = 122,
            theme = maryTheme,
            response = maryResponse,
            bridge = maryBridge,
            ending = phrase(n(4), n(2), n(0), n(2), n(4), n(2), n(0, 4f), rest(2f)),
        ),
        song(
            id = "LONDON_BRIDGE",
            title = "London Bridge",
            bpm = 124,
            theme = bridgeTheme,
            response = bridgeResponse,
            bridge = bridgeMiddle,
            ending = phrase(n(7), n(5), n(4), n(2), n(0, 4f), rest(2f)),
        ),
        song(
            id = "JINGLE_BELLS",
            title = "Jingle Bells",
            bpm = 128,
            theme = jingleTheme,
            response = jingleResponse,
            bridge = jingleBridge,
            ending = phrase(n(4), n(7), n(0), n(2), n(4, 4f), rest(2f)),
        ),
        song(
            id = "BEETHOVEN_FIFTH",
            title = "Beethoven 5th",
            bpm = 132,
            theme = fifthTheme,
            response = fifthResponse,
            bridge = fifthBridge,
            ending = phrase(n(7, 0.5f), n(7, 0.5f), n(7, 0.5f), n(3, 1f), n(5, 0.5f), n(5, 0.5f), n(5, 0.5f), n(2, 1f), n(0, 4f), rest(2f)),
        ),
        song(
            id = "WHEN_THE_SAINTS",
            title = "When the Saints",
            bpm = 128,
            theme = saintsTheme,
            response = saintsResponse,
            bridge = saintsBridge,
            ending = phrase(n(0), n(4), n(5), n(7), n(4), n(0), n(2), n(0, 4f), rest(2f)),
        ),
    )

    fun byId(id: String): RhythmSong? = songs.firstOrNull { it.id == id }

    private fun song(
        id: String,
        title: String,
        bpm: Int,
        theme: List<RhythmNote>,
        response: List<RhythmNote>,
        bridge: List<RhythmNote>,
        ending: List<RhythmNote>,
    ): RhythmSong {
        return RhythmSong(
            id = id,
            title = title,
            bpm = bpm,
            notes = fullArrangement(
                theme = theme,
                response = response,
                bridge = bridge,
                ending = ending,
            ),
        )
    }

    private fun fullArrangement(
        theme: List<RhythmNote>,
        response: List<RhythmNote>,
        bridge: List<RhythmNote>,
        ending: List<RhythmNote>,
    ): List<RhythmNote> {
        return buildList {
            addAll(theme)
            addAll(response)
            addAll(bridge)
            addAll(vary(theme, transpose = 12, holdEvery = 6))
            addAll(vary(response, transpose = 12, holdEvery = 5))
            addAll(vary(bridge, transpose = 0, holdEvery = 7))
            addAll(vary(theme, transpose = 7, holdEvery = 5))
            addAll(vary(response, transpose = 7, holdEvery = 4))
            addAll(vary(bridge, transpose = -5, holdEvery = 6))
            addAll(vary(theme, transpose = -12, holdEvery = 4))
            addAll(vary(response, transpose = 0, holdEvery = 3))
            addAll(vary(bridge, transpose = 12, holdEvery = 6))
            addAll(vary(theme, transpose = 0, holdEvery = 3))
            addAll(vary(response, transpose = -5, holdEvery = 4))
            addAll(ending)
            addAll(vary(ending, transpose = 12, holdEvery = 3))
        }
    }

    private fun vary(
        notes: List<RhythmNote>,
        transpose: Int = 0,
        holdEvery: Int = 0,
    ): List<RhythmNote> {
        return notes.mapIndexed { index, note ->
            if (note.isRest) {
                note
            } else {
                note.copy(
                    semitone = note.semitone + transpose,
                    beats = if (holdEvery > 0 && (index + 1) % holdEvery == 0) {
                        note.beats + 0.5f
                    } else {
                        note.beats
                    },
                )
            }
        }
    }

    private fun phrase(vararg notes: RhythmNote): List<RhythmNote> = notes.toList()

    private fun n(
        semitone: Int,
        beats: Float = 1f,
    ): RhythmNote = RhythmNote(
        semitone = semitone,
        beats = beats,
    )

    private fun rest(beats: Float): RhythmNote = RhythmNote(
        semitone = 0,
        beats = beats,
        isRest = true,
    )
}
