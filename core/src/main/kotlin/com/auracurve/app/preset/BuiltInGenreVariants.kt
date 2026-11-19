package com.auracurve.app.preset

/** One preset in a genre's list, with the "when is this the best choice" text the list shows. */
data class GenreVariant(
    val preset: Preset,
    val description: String,
    val useCase: GenreUseCase? = null,
)

// More presets per genre: hand-tuned character variants (a different take on the genre's sound)
// plus the five use-case variants (GenreUseCase) derived from each genre's base. Same rules as the
// genre bases: no single boost of 9 dB or more with the macros included, headroom requested in
// line with the strongest boost, and highs tamed rather than pushed for the distorted genres.
// Folded into BuiltInPresets.all, so a device profile can bind to any of them by id.
// Deliberately does not reference BuiltInPresets (that object folds this one in, and a cycle
// between the two object initializers would leave one half null).
object BuiltInGenreVariants {
    private fun preset(
        genre: GenrePreset,
        key: String,
        label: String,
        curve: List<TargetPoint>,
        bass: Float,
        punch: Float,
        haerte: Float,
        headroom: Float,
        mbcThreshold: Float,
        mbcRatio: Float,
    ): Preset =
        genre.base.copy(
            id = "${genre.id}_style_$key",
            name = "${genre.displayName} – $label",
            targetCurve = curve,
            macroBassDb = bass,
            macroPunchDb = punch,
            macroHaerteDb = haerte,
            requestedHeadroomDb = headroom,
            mbcThresholdDb = mbcThreshold,
            mbcRatio = mbcRatio,
        )

    private fun p(
        hz: Float,
        db: Float,
    ) = TargetPoint(hz, db)

    private val terror = BuiltInGenrePresets.Terror
    private val uptempo = BuiltInGenrePresets.Uptempo
    private val gabber = BuiltInGenrePresets.Gabber
    private val early = BuiltInGenrePresets.Early
    private val frenchcore = BuiltInGenrePresets.Frenchcore
    private val dance = BuiltInGenrePresets.Dance
    private val pop = BuiltInGenrePresets.Pop
    private val rap = BuiltInGenrePresets.Rap

    /** Hand-tuned character variants per genre id, in list order. */
    val styles: Map<String, List<GenreVariant>> =
        mapOf(
            terror.id to
                listOf(
                    // Already clipped masters: more cut at 2.5-8 kHz instead of more low end.
                    GenreVariant(
                        preset(
                            terror,
                            "tamed",
                            "Tamed Rage",
                            listOf(
                                p(50f, 3.0f),
                                p(100f, 2.0f),
                                p(300f, -3.5f),
                                p(2500f, -2.0f),
                                p(4500f, -3.0f),
                                p(8000f, -2.0f),
                                p(12000f, -1.5f),
                            ),
                            bass = 1.0f,
                            punch = 1.0f,
                            haerte = -1.5f,
                            headroom = 5.5f,
                            mbcThreshold = -10f,
                            mbcRatio = 3.5f,
                        ),
                        "For the harshest, most distorted terror tracks and long sessions: the screeching 2.5–8 kHz " +
                            "range is cut further, the kick keeps its weight. Also good on bright earbuds.",
                    ),
                    GenreVariant(
                        preset(
                            terror,
                            "sub",
                            "Sub Pressure",
                            listOf(
                                p(40f, 4.0f),
                                p(60f, 3.5f),
                                p(120f, 1.0f),
                                p(300f, -3.0f),
                                p(2500f, -1.0f),
                                p(6000f, -2.0f),
                                p(12000f, -1.0f),
                            ),
                            bass = 2.0f,
                            punch = 0.5f,
                            haerte = -1.0f,
                            headroom = 6.0f,
                            mbcThreshold = -11f,
                            mbcRatio = 3.8f,
                        ),
                        "Deep sub pressure under the distortion: for headphones, a subwoofer or a soundbar that " +
                            "reaches 40 Hz. Too boomy for a small speaker; in a car choose Terror – Car instead.",
                    ),
                ),
            uptempo.id to
                listOf(
                    // Uptempo leads (screeches) sit at 2.5-5 kHz and get piercing when loud.
                    GenreVariant(
                        preset(
                            uptempo,
                            "screech",
                            "Screech Control",
                            listOf(
                                p(55f, 3.0f),
                                p(105f, 2.0f),
                                p(280f, -2.0f),
                                p(2500f, -1.0f),
                                p(4000f, -2.0f),
                                p(8000f, -1.0f),
                                p(12000f, 0.5f),
                            ),
                            bass = 1.5f,
                            punch = 2.0f,
                            haerte = -0.5f,
                            headroom = 4.0f,
                            mbcThreshold = -7f,
                            mbcRatio = 2.5f,
                        ),
                        "Same kick as Uptempo, but the screeches and leads at 2.5–5 kHz are tamed, with a bit of " +
                            "air on top. For long sets, loud listening and bright speakers.",
                    ),
                    // At 200+ BPM a long sub tail smears into the next kick: peak at the kick, less sub.
                    GenreVariant(
                        preset(
                            uptempo,
                            "tight",
                            "Tight Low",
                            listOf(p(45f, 1.5f), p(90f, 3.0f), p(140f, 1.5f), p(250f, -3.0f), p(500f, -1.0f), p(3500f, 1.0f), p(9000f, 0f)),
                            bass = 0.5f,
                            punch = 2.0f,
                            haerte = 0.5f,
                            headroom = 4.0f,
                            mbcThreshold = -8f,
                            mbcRatio = 3.0f,
                        ),
                        "Short, controlled bass for the fastest tracks (200+ BPM): every kick stays separate " +
                            "instead of blurring into a rumble. Good in the car and on rooms that boom.",
                    ),
                ),
            gabber.id to
                listOf(
                    GenreVariant(
                        preset(
                            gabber,
                            "doorlussen",
                            "Doorlussen",
                            listOf(p(50f, 2.0f), p(130f, 4.0f), p(300f, -3.0f), p(800f, -0.5f), p(1500f, 1.0f), p(5000f, -0.5f)),
                            bass = 0.5f,
                            punch = 3.0f,
                            haerte = 0.5f,
                            headroom = 5.0f,
                            mbcThreshold = -8f,
                            mbcRatio = 3.0f,
                        ),
                        "The distorted kick body at 120–150 Hz pushed forward, the boxy 300 Hz scooped. Hits hard " +
                            "on speakers and in the car, where the sub would only boom.",
                    ),
                    // 90s gabber kicks are thinner and more midrange; adding sub to them only muddies.
                    GenreVariant(
                        preset(
                            gabber,
                            "oldschool",
                            "Old School",
                            listOf(
                                p(40f, 0f),
                                p(80f, 2.0f),
                                p(150f, 3.0f),
                                p(350f, -2.0f),
                                p(1500f, 1.5f),
                                p(3500f, 1.0f),
                                p(10000f, -1.0f),
                            ),
                            bass = 0f,
                            punch = 2.0f,
                            haerte = 0.5f,
                            headroom = 4.0f,
                            mbcThreshold = -7f,
                            mbcRatio = 2.5f,
                        ),
                        "For 90s gabber and classic records: no extra sub (the old masters have little), more " +
                            "midrange bite at 1–3 kHz and softened top end against hiss.",
                    ),
                ),
            early.id to
                listOf(
                    GenreVariant(
                        preset(
                            early,
                            "warm",
                            "Warm Rumble",
                            listOf(
                                p(45f, 3.5f),
                                p(80f, 3.0f),
                                p(160f, 1.0f),
                                p(350f, -1.5f),
                                p(2000f, 0f),
                                p(6000f, -1.0f),
                                p(10000f, -1.0f),
                            ),
                            bass = 2.0f,
                            punch = 1.0f,
                            haerte = -0.5f,
                            headroom = 5.0f,
                            mbcThreshold = -8f,
                            mbcRatio = 2.8f,
                        ),
                        "Warm, rolling reverb bass with smooth highs: for relaxed listening on headphones or a " +
                            "soundbar. Booms in a small car, there pick Early – Car.",
                    ),
                    GenreVariant(
                        preset(
                            early,
                            "crisp",
                            "Crisp Kick",
                            listOf(p(50f, 2.5f), p(100f, 2.5f), p(300f, -2.5f), p(1500f, 1.0f), p(3500f, 1.5f), p(8000f, 0.5f)),
                            bass = 1.0f,
                            punch = 2.0f,
                            haerte = 1.0f,
                            headroom = 4.5f,
                            mbcThreshold = -7f,
                            mbcRatio = 2.8f,
                        ),
                        "A drier, clicking kick and clearer vocals: for busy mixes and speakers that sound dull. " +
                            "Can get sharp on bright earbuds.",
                    ),
                ),
            frenchcore.id to
                listOf(
                    GenreVariant(
                        preset(
                            frenchcore,
                            "melodic",
                            "Melodic",
                            listOf(
                                p(50f, 2.0f),
                                p(100f, 2.0f),
                                p(260f, -1.5f),
                                p(1000f, 0.5f),
                                p(3000f, 1.0f),
                                p(6000f, 0.5f),
                                p(10000f, 1.0f),
                            ),
                            bass = 0.5f,
                            punch = 1.5f,
                            haerte = 0.5f,
                            headroom = 3.5f,
                            mbcThreshold = -6f,
                            mbcRatio = 2.5f,
                        ),
                        "For melodic breaks, violins and vocals: the kick steps back a little, mids and air open " +
                            "up. Good at moderate volume and on headphones.",
                    ),
                    GenreVariant(
                        preset(
                            frenchcore,
                            "rush",
                            "Kick Rush",
                            listOf(p(50f, 3.0f), p(100f, 3.5f), p(260f, -3.0f), p(2000f, 1.0f), p(4500f, 1.0f), p(9000f, -1.5f)),
                            bass = 1.0f,
                            punch = 3.0f,
                            haerte = 1.0f,
                            headroom = 5.5f,
                            mbcThreshold = -6f,
                            mbcRatio = 3.4f,
                        ),
                        "All about the fast kick: maximum punch at 100 Hz with a tight compressor. For loud " +
                            "listening on speakers that can take it; turn Punch down if it pumps.",
                    ),
                ),
            dance.id to
                listOf(
                    GenreVariant(
                        preset(
                            dance,
                            "lead",
                            "Hardstyle Lead",
                            listOf(
                                p(60f, 2.0f),
                                p(120f, 1.5f),
                                p(300f, -1.5f),
                                p(1000f, 0f),
                                p(3000f, 1.0f),
                                p(8000f, 1.0f),
                                p(12000f, 1.5f),
                            ),
                            bass = 0.5f,
                            punch = 1.0f,
                            haerte = 0.5f,
                            headroom = 3.5f,
                            mbcThreshold = -6f,
                            mbcRatio = 2.2f,
                        ),
                        "Euphoric leads and vocals in front, with sparkle on top: for hardstyle and hard dance " +
                            "anthems at medium volume.",
                    ),
                    GenreVariant(
                        preset(
                            dance,
                            "club",
                            "Club Sub",
                            listOf(p(40f, 3.0f), p(65f, 3.0f), p(120f, 1.0f), p(300f, -2.0f), p(4000f, 0.5f), p(10000f, 0.5f)),
                            bass = 1.0f,
                            punch = 0.75f,
                            haerte = 0.25f,
                            headroom = 4.0f,
                            mbcThreshold = -7f,
                            mbcRatio = 2.3f,
                        ),
                        "Club-style sub weight under the kick: for headphones, a subwoofer or a soundbar. Not for " +
                            "small speakers.",
                    ),
                ),
            pop.id to
                listOf(
                    GenreVariant(
                        preset(
                            pop,
                            "vocal",
                            "Vocal Clarity",
                            listOf(
                                p(60f, 1.0f),
                                p(200f, -1.0f),
                                p(400f, -0.5f),
                                p(1000f, 0f),
                                p(2500f, 1.5f),
                                p(5000f, 1.0f),
                                p(10000f, 1.0f),
                            ),
                            bass = 0.2f,
                            punch = 0.2f,
                            haerte = 0.3f,
                            headroom = 2.0f,
                            mbcThreshold = -8f,
                            mbcRatio = 1.8f,
                        ),
                        "Clear, forward vocals and less low-mid mud: for podcasts, singer-songwriter and noisy " +
                            "places where words get lost.",
                    ),
                    GenreVariant(
                        preset(
                            pop,
                            "warm",
                            "Warm",
                            listOf(p(60f, 2.0f), p(150f, 1.0f), p(400f, 0f), p(3000f, -0.5f), p(7000f, -0.5f), p(12000f, 0.5f)),
                            bass = 0.5f,
                            punch = 0.3f,
                            haerte = 0f,
                            headroom = 2.5f,
                            mbcThreshold = -8f,
                            mbcRatio = 1.8f,
                        ),
                        "Warmer and softer: for bright or loudly mastered pop, long listening and cheap " +
                            "earbuds that sound thin.",
                    ),
                ),
            rap.id to
                listOf(
                    GenreVariant(
                        preset(
                            rap,
                            "808",
                            "808 Deep",
                            listOf(
                                p(35f, 3.5f),
                                p(55f, 3.5f),
                                p(100f, 1.0f),
                                p(250f, -1.5f),
                                p(2500f, 1.0f),
                                p(5000f, 0.5f),
                                p(9000f, -0.5f),
                            ),
                            bass = 1.5f,
                            punch = 0.3f,
                            haerte = 0f,
                            headroom = 5.0f,
                            mbcThreshold = -9f,
                            mbcRatio = 2.3f,
                        ),
                        "Long, deep 808s for trap and modern rap: for headphones, subwoofers and soundbars. On " +
                            "a small speaker the 808 is too deep to play, pick Rap – Bluetooth speaker there.",
                    ),
                    GenreVariant(
                        preset(
                            rap,
                            "boombap",
                            "Boom Bap",
                            listOf(p(60f, 2.0f), p(100f, 2.5f), p(250f, -1.5f), p(1500f, 0.5f), p(3000f, 1.5f), p(8000f, 0.5f)),
                            bass = 0.5f,
                            punch = 1.0f,
                            haerte = 0.3f,
                            headroom = 3.5f,
                            mbcThreshold = -7f,
                            mbcRatio = 2.2f,
                        ),
                        "Punchy kick and snare with crisp vocals: for 90s rap, boom bap and German rap where the " +
                            "words matter.",
                    ),
                ),
        )

    /** The five use-case variants of a genre, in GenreUseCase order. */
    fun useCases(genre: GenrePreset): List<GenreVariant> =
        GenreUseCase.entries.map { useCase -> GenreVariant(GenreUseCaseTuning.apply(genre, useCase), useCase.description, useCase) }

    private val useCasesByGenre: Map<String, List<GenreVariant>> = BuiltInGenrePresets.all.associate { it.id to useCases(it) }

    /** Character variants, then use cases, of one genre. */
    fun variantsOf(genre: GenrePreset): List<GenreVariant> = styles[genre.id].orEmpty() + (useCasesByGenre[genre.id] ?: useCases(genre))

    /** Every preset this object adds (none of them duplicates an existing built-in id). */
    val allPresets: List<Preset> = BuiltInGenrePresets.all.flatMap { genre -> variantsOf(genre).map { it.preset } }
}
