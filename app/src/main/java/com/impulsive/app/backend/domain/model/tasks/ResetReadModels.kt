package com.impulsive.app.backend.domain.model.tasks

import java.time.LocalDateTime
import kotlin.math.ceil

typealias ResetReadArticleId = String

const val RESET_READ_REMOTE_ENABLED = false
const val RESET_READ_MINIMUM_SECONDS = 90
const val RESET_READ_COMPLETED_COOLDOWN_DAYS = 7L
const val RESET_READ_SHOWN_COOLDOWN_DAYS = 2L

object ResetReadArticleTags {
    const val Urge = "urge"
    const val Craving = "craving"
    const val Mindfulness = "mindfulness"
    const val Body = "body"
    const val Emotion = "emotion"
    const val Science = "science"
    const val Pause = "pause"
    const val Friction = "friction"
    const val Environment = "environment"
    const val Habit = "habit"
    const val Replacement = "replacement"
    const val Boredom = "boredom"
    const val Dopamine = "dopamine"
    const val Reward = "reward"
    const val Anticipation = "anticipation"
    const val Sleep = "sleep"
    const val Night = "night"
    const val Breathing = "breathing"
    const val Stress = "stress"
    const val Delay = "delay"
    const val Cue = "cue"
    const val Routine = "routine"
    const val Willpower = "willpower"
    const val DecisionFatigue = "decision_fatigue"
    const val Attention = "attention"
    const val Focus = "focus"
}

data class ResetReadSessionRecord(
    val id: Long,
    val articleId: ResetReadArticleId,
    val articleTitle: String,
    val startedAt: LocalDateTime,
    val completedAt: LocalDateTime,
    val selectedDurationSeconds: Int,
    val requiredDurationSeconds: Int,
    val secondsSpent: Int,
    val selectedOptionIndex: Int,
    val validCompletion: Boolean,
    val answerText: String = "",
    val completionQuality: String = "valid",
    val failureReason: String? = null,
    val rewardApplied: Boolean? = null,
    val waitCutMinutes: Int? = null,
    val helpfulnessRating: Int? = null,
)

data class ResetReadArticleExposureRecord(
    val id: Long,
    val articleId: ResetReadArticleId,
    val shownAt: LocalDateTime,
)

sealed interface ArticleBlock {
    data class Heading(val text: String) : ArticleBlock
    data class Paragraph(val text: String) : ArticleBlock
    data class Img(val key: String, val caption: String? = null) : ArticleBlock
    data class Video(
        val assetFileName: String,
        val title: String,
        val caption: String? = null,
    ) : ArticleBlock
}

data class ResetReadArticle(
    val id: ResetReadArticleId,
    val title: String,
    val tags: Set<String>,
    val blocks: List<ArticleBlock>,
    val closingQuestion: ResetReadQuestion,
    val articleUrl: String? = null,
) {
    val estimatedReadMinutes: Int
        get() = maxOf(1, ceil(blocks.wordCount() / 200.0).toInt())

    val minimumReadSeconds: Int
        get() = RESET_READ_MINIMUM_SECONDS
}

data class ResetReadQuestion(
    val prompt: String,
    val options: List<String>,
)

val StarterResetReadArticles = listOf(
    ResetReadArticle(
        id = "surf_the_urge",
        title = "Surf the Urge",
        tags = setOf(
            ResetReadArticleTags.Urge,
            ResetReadArticleTags.Craving,
            ResetReadArticleTags.Mindfulness,
            ResetReadArticleTags.Body,
            ResetReadArticleTags.Pause,
        ),
        blocks = listOf(
            ArticleBlock.Paragraph("An urge is not an order. It is a wave, and waves always break."),
            ArticleBlock.Img(
                key = "steady_wave",
                caption = "An urge can rise, peak, and fall without you acting on it.",
            ),
            ArticleBlock.Paragraph("Urges feel huge and permanent in the moment, like they will only grow until you give in. They will not. An urge rises, peaks, and falls on its own, often sooner than it feels, whether or not you act on it."),
            ArticleBlock.Paragraph("Fighting an urge head on, gritting your teeth and telling yourself no, no, no, often makes it louder, because all your attention is on it. Surfing is different. You notice the urge, name it, and watch it move, like watching a wave roll in without jumping in front of it."),
            ArticleBlock.Paragraph("The trick is to stay curious instead of scared. Where do you feel it in your body? Is it getting stronger or weaker right now? Naming it, \"this is a craving,\" puts a little space between you and it."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("You are doing homework and the pull to check your phone hits hard. Instead of fighting it or grabbing the phone, you set a 2 minute timer and just watch the urge. You notice it spikes around minute one, then quietly fades. The wave broke, and you did not have to do anything."),
        ),
        closingQuestion = ResetReadQuestion(
            prompt = "When an urge hit today, did it actually last as long as it felt like it would?",
            options = listOf("No, it faded faster", "Yes, it dragged on", "I did not notice"),
        ),
    ),
    ResetReadArticle(
        id = "the_90_second_rule",
        title = "The 90 Second Rule",
        tags = setOf(
            ResetReadArticleTags.Emotion,
            ResetReadArticleTags.Science,
            ResetReadArticleTags.Pause,
            ResetReadArticleTags.Attention,
            ResetReadArticleTags.Focus,
        ),
        blocks = listOf(
            ArticleBlock.Paragraph("Your strongest feelings have a surprisingly short shelf life."),
            ArticleBlock.Img(
                key = "steady_line",
                caption = "The first emotional peak can settle if you give it a short pause.",
            ),
            ArticleBlock.Paragraph("When something sparks a big reaction, anger, craving, panic, your body floods with chemicals. But that chemical surge clears out fast. Some emotion teachers describe the body's first chemical rush as roughly 90 seconds, but the useful point is simpler: the peak changes faster than it feels."),
            ArticleBlock.Paragraph("After that first surge, what keeps the feeling alive is often a thought you are replaying, not the original trigger. That is good news. It means you can outlast the peak by doing almost nothing for a minute and a half."),
            ArticleBlock.Paragraph("This is why short, focused distractions work so well. You are not running from the feeling forever. You are giving your body the 90 seconds it needs to reset."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("A message annoys you and you want to fire back instantly. You start a 90 second game instead. By the time it ends, the heat has dropped, and the reply you would have sent feels like too much. You write a calmer one, or none at all."),
        ),
        closingQuestion = ResetReadQuestion(
            prompt = "What is one thing you could do for the next 90 seconds while a feeling settles?",
            options = listOf("Start a quick game", "Breathe slowly", "Step away", "Wait it out"),
        ),
    ),
    ResetReadArticle(
        id = "make_it_a_little_harder",
        title = "Make It a Little Harder",
        tags = setOf(
            ResetReadArticleTags.Friction,
            ResetReadArticleTags.Environment,
            ResetReadArticleTags.Habit,
            ResetReadArticleTags.Attention,
            ResetReadArticleTags.Focus,
        ),
        blocks = listOf(
            ArticleBlock.Paragraph("You do not need more willpower. You need more distance."),
            ArticleBlock.Paragraph("We like to think self-control is about being strong in the moment. Mostly, it is about the moment never getting that hard in the first place. Small obstacles change behaviour more than big promises."),
            ArticleBlock.Paragraph("Every extra step between you and an impulse gives your slower, calmer brain time to catch up. Logging out, leaving the snack in another room, putting the phone across the room to charge. Tiny friction, big effect."),
            ArticleBlock.Paragraph("This is harm reduction, not punishment. You are not banning anything. You are making the easy thing slightly less easy, so the choice becomes a real choice again."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("You always scroll in bed. Tonight you leave the phone charging in the kitchen. When the urge to scroll comes, the effort of getting up is just enough to make you notice you did not actually want it that much. You sleep instead."),
        ),
        closingQuestion = ResetReadQuestion(
            prompt = "What is one small step you could add between you and the thing you want to do less?",
            options = listOf("Move it out of reach", "Log out first", "Leave the room", "Add a short timer"),
        ),
    ),
    ResetReadArticle(
        id = "trade_do_not_just_stop",
        title = "Trade, Do Not Just Stop",
        tags = setOf(
            ResetReadArticleTags.Replacement,
            ResetReadArticleTags.Habit,
            ResetReadArticleTags.Boredom,
            ResetReadArticleTags.Routine,
            ResetReadArticleTags.Reward,
        ),
        blocks = listOf(
            ArticleBlock.Paragraph("A habit hates a vacuum. Leave an empty space and the old one moves right back in."),
            ArticleBlock.Img(
                key = "calm_task",
                caption = "A small replacement gives the old habit somewhere safer to land.",
            ),
            ArticleBlock.Paragraph("Telling yourself to just stop leaves a gap where the behaviour used to be, and gaps get filled fast, usually by the same habit. Swapping works better than stopping."),
            ArticleBlock.Paragraph("The swap should scratch a similar itch. If a habit gives you a quick hit of calm, replace it with something else quick and calming, not a chore you will avoid."),
            ArticleBlock.Paragraph("Start absurdly small. A swap you will actually do beats a perfect plan you will not. Small and repeated rewires the pattern over time."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("You reach for your phone whenever you feel bored. Instead of \"no phone,\" you keep a cheap puzzle or a stress ball next to you. Boredom hits, your hand reaches, and now it lands on the swap. Same itch, gentler scratch."),
        ),
        closingQuestion = ResetReadQuestion(
            prompt = "What is one small thing you could reach for instead, next time?",
            options = listOf("A quick game", "Water or a snack", "A short walk", "Something for my hands"),
        ),
    ),
    ResetReadArticle(
        id = "dopamine_is_not_pleasure",
        title = "Dopamine Is Not the Pleasure Chemical",
        tags = setOf(
            ResetReadArticleTags.Dopamine,
            ResetReadArticleTags.Anticipation,
            ResetReadArticleTags.Science,
            ResetReadArticleTags.Reward,
            ResetReadArticleTags.Craving,
        ),
        blocks = listOf(
            ArticleBlock.Paragraph("The buzz you feel before the reward is louder than the reward itself. That is the whole trick."),
            ArticleBlock.Img(
                key = "shortcut_map",
                caption = "Dopamine often pulls you toward the promise, not the actual reward.",
            ),
            ArticleBlock.Paragraph("Dopamine gets called the pleasure chemical, but that is not quite right. It is more about wanting and anticipation than enjoyment. It spikes when your brain expects a reward, not just when you get one."),
            ArticleBlock.Paragraph("That is why the pull to check, open, or scroll can feel stronger than the thing itself, which often turns out to be a let down. Your brain was selling you the chase, not the prize."),
            ArticleBlock.Paragraph("Knowing this gives you a small superpower. When you feel that itch, you can ask, am I actually going to enjoy this, or is my brain just hyping the maybe?"),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("You feel a strong urge to open an app to check for notifications. You pause and predict how good it will actually feel, you guess a 7. You check. It is a 2. Next time the urge comes, you remember the gap between the hype and the payoff."),
        ),
        closingQuestion = ResetReadQuestion(
            prompt = "Was the last thing you chased actually as good as the wanting made it seem?",
            options = listOf("No, it let me down", "Yes, it was worth it", "About the same", "Hard to say"),
        ),
    ),
    ResetReadArticle(
        id = "tired_brains_cave",
        title = "Tired Brains Cave",
        tags = setOf(
            ResetReadArticleTags.Sleep,
            ResetReadArticleTags.Night,
            ResetReadArticleTags.Willpower,
            ResetReadArticleTags.DecisionFatigue,
            ResetReadArticleTags.Body,
        ),
        blocks = listOf(
            ArticleBlock.Paragraph("Most willpower failures are not character flaws. They are sleep debts collecting interest."),
            ArticleBlock.Paragraph("The part of your brain that pauses and plans, the prefrontal cortex, is expensive to run. When you are short on sleep it gets underpowered, and the older, faster, want-it-now parts take the wheel."),
            ArticleBlock.Paragraph("That is why everything feels harder to resist when you are exhausted, snacks, scrolling, snapping at people. You are not weaker as a person. Your brakes just have less power."),
            ArticleBlock.Paragraph("The fix is boring and it works. Protecting your sleep is one of the most effective self-control tools there is, and it costs nothing."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("You notice you give in to almost every urge late at night, but mornings after good sleep feel easy. That is not a coincidence. You start treating \"I am tired\" as a yellow flag that your brakes are weak, and you avoid big decisions then."),
        ),
        closingQuestion = ResetReadQuestion(
            prompt = "Do your hardest moments tend to land when you are well rested, or run down?",
            options = listOf("When I am run down", "When I am rested", "About equally", "Not sure yet"),
        ),
    ),
    ResetReadArticle(
        id = "breathe_slower",
        title = "Breathe Slower Than You Want To",
        tags = setOf(
            ResetReadArticleTags.Breathing,
            ResetReadArticleTags.Body,
            ResetReadArticleTags.Stress,
            ResetReadArticleTags.Pause,
            ResetReadArticleTags.Mindfulness,
        ),
        blocks = listOf(
            ArticleBlock.Paragraph("There is one button you can press to talk to your own nervous system, and it is in your lungs."),
            ArticleBlock.Paragraph("When an urge or stress spikes, your body shifts into alarm mode, heart up, breath quick, muscles ready. You cannot order that to stop by thinking. But you can change your breathing, and your body listens."),
            ArticleBlock.Paragraph("Long, slow exhales send a simple signal: we are safe now. Breathe out for longer than you breathe in, and your heart rate and the alarm start to settle within a few breaths."),
            ArticleBlock.Paragraph("It feels almost too simple to matter. That is exactly why people skip it. Try it once during a real urge and you will feel the difference."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("A craving hits in class. You cannot get up or distract yourself, so you breathe in for 4 and out for 6, quietly, six times. By the end the craving is still there but smaller, and you are back in the driver's seat."),
        ),
        closingQuestion = ResetReadQuestion(
            prompt = "Next time stress spikes, can you make your out breath longer than your in breath?",
            options = listOf("Yes, I will try it", "Maybe", "I already do this"),
        ),
    ),
    ResetReadArticle(
        id = "marshmallow_test",
        title = "The Marshmallow Test Was Not About Marshmallows",
        tags = setOf(
            ResetReadArticleTags.Delay,
            ResetReadArticleTags.Environment,
            ResetReadArticleTags.Reward,
            ResetReadArticleTags.Attention,
            ResetReadArticleTags.Willpower,
        ),
        blocks = listOf(
            ArticleBlock.Paragraph("A room, one treat, and a deal: wait, and you get two. What happened next got the story wrong for decades."),
            ArticleBlock.Paragraph("In a famous experiment, children were offered one treat now, or two if they could wait alone with it. For years people said the waiters simply had more willpower, and that it decided their whole future."),
            ArticleBlock.Paragraph("Later research complicated that. The kids who waited often were not gritting their teeth. They distracted themselves, covered their eyes, sang, looked away. They changed the situation instead of out muscling it."),
            ArticleBlock.Paragraph("And trust mattered. Kids who had learned that adults keep their promises waited longer, because waiting was worth it. Self-control was never just an inner strength. It was strategy and circumstances."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("You want to wait before reacting to something tempting. Instead of staring it down, you do what the successful kids did, you look away, change rooms, or busy your hands. You are not stronger, you are smarter about the setup."),
        ),
        closingQuestion = ResetReadQuestion(
            prompt = "What could you look away from, instead of staring down?",
            options = listOf("My phone", "A craving", "A tab or app", "Something else"),
        ),
    ),
    ResetReadArticle(
        id = "habits_run_on_a_loop",
        title = "Habits Run on a Loop",
        tags = setOf(
            ResetReadArticleTags.Habit,
            ResetReadArticleTags.Cue,
            ResetReadArticleTags.Routine,
            ResetReadArticleTags.Reward,
            ResetReadArticleTags.Environment,
        ),
        blocks = listOf(
            ArticleBlock.Paragraph("Somewhere in your brain a tiny loop is running on autopilot, and it does not care whether the habit helps you."),
            ArticleBlock.Paragraph("Most habits follow a simple three part loop: a cue, something that triggers it, a routine, the thing you do, and a reward, what your brain gets out of it. Repeat it enough and the loop runs without you deciding."),
            ArticleBlock.Paragraph("The cue is usually a time, a place, a feeling, or something you just did. Spot your cue and you have found the loop's on switch."),
            ArticleBlock.Paragraph("You usually cannot delete a loop, but you can hijack it. Keep the cue and the reward, swap the routine in the middle. The brain keeps its payoff, and you change the behaviour."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("Every time you sit at your desk, the cue, you open a game, the routine, to feel a little relief, the reward. You keep the cue and reward but change the middle: sit down, do one tiny task, then take the relief break. Same loop, better routine."),
        ),
        closingQuestion = ResetReadQuestion(
            prompt = "Can you name the cue that kicks off one of your strongest habits?",
            options = listOf("A feeling", "A place", "A time of day", "Something I just did"),
        ),
    ),
    ResetReadArticle(
        id = "willpower_feels_like_a_battery",
        title = "Willpower Feels Like a Battery",
        tags = setOf(
            ResetReadArticleTags.Willpower,
            ResetReadArticleTags.DecisionFatigue,
            ResetReadArticleTags.Night,
            ResetReadArticleTags.Routine,
            ResetReadArticleTags.Focus,
        ),
        blocks = listOf(
            ArticleBlock.Paragraph("By the end of a long day of choices, even the sharpest people make the sloppiest ones. There is a reason."),
            ArticleBlock.Paragraph("Every decision you make, even tiny ones, can make later choices feel harder. As the day goes on, resisting impulses can take more effort. This is often called decision fatigue, and while researchers still debate how it works, the daily pattern is familiar to most people."),
            ArticleBlock.Paragraph("It is why late evening is prime time for \"ah, whatever\" choices, and why people who decide big things all day try to make the rest of their life automatic."),
            ArticleBlock.Paragraph("The move is not to grind harder when you are drained. It is to spend fewer decisions, set things up earlier in the day, and make some choices once instead of every single time."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("You keep deciding each night whether to scroll or sleep, and you usually lose by 11pm. So you decide it once, in the morning, when your head is clearer: the phone charges in the kitchen at 10. Now there is no late night decision left to lose."),
        ),
        closingQuestion = ResetReadQuestion(
            prompt = "Which choice could you make once, early, so you do not have to fight it every night?",
            options = listOf("Where my phone charges", "When I wind down", "What I do first", "Not sure yet"),
        ),
    ),
)

val ResetReadTopFallbackArticleIds: List<ResetReadArticleId> = listOf(
    "dopamine_is_not_pleasure",
    "the_90_second_rule",
    "surf_the_urge",
    "trade_do_not_just_stop",
)

fun topFallbackResetReadArticles(
    articles: List<ResetReadArticle> = StarterResetReadArticles,
): List<ResetReadArticle> {
    val articlesById = articles.associateBy { it.id }
    return ResetReadTopFallbackArticleIds.mapNotNull { articleId ->
        articlesById[articleId]
    }
}

fun fallbackResetReadArticleForDay(
    epochDay: Long,
    articles: List<ResetReadArticle> = StarterResetReadArticles,
    excludedArticleIds: Set<ResetReadArticleId> = emptySet(),
): ResetReadArticle {
    val fallbackPool = topFallbackResetReadArticles(articles).ifEmpty { articles }
    if (fallbackPool.isEmpty()) error("No reset reading fallback articles available")

    val eligiblePool = fallbackPool
        .filterNot { article -> article.id in excludedArticleIds }
        .ifEmpty { fallbackPool }

    return eligiblePool.rotatingPick(epochDay)
}

fun cooldownExcludedResetReadArticleIds(
    now: LocalDateTime,
    sessions: List<ResetReadSessionRecord> = emptyList(),
    exposures: List<ResetReadArticleExposureRecord> = emptyList(),
): Set<ResetReadArticleId> {
    val completedCutoff = now.minusDays(RESET_READ_COMPLETED_COOLDOWN_DAYS)
    val shownCutoff = now.minusDays(RESET_READ_SHOWN_COOLDOWN_DAYS)

    val recentlyCompleted = sessions
        .asSequence()
        .filter { session -> session.validCompletion && !session.completedAt.isBefore(completedCutoff) }
        .map { session -> session.articleId }

    val recentlyShown = exposures
        .asSequence()
        .filter { exposure -> !exposure.shownAt.isBefore(shownCutoff) }
        .map { exposure -> exposure.articleId }

    return (recentlyCompleted + recentlyShown).toSet()
}

fun resetReadArticleForDay(
    epochDay: Long,
    articles: List<ResetReadArticle> = StarterResetReadArticles,
): ResetReadArticle {
    if (articles.isEmpty()) error("No reset reading articles available")
    val index = epochDay.mod(articles.size.toLong()).toInt()
    return articles[index]
}

fun nextArticleFor(
    readIds: Set<ResetReadArticleId>,
    articles: List<ResetReadArticle> = StarterResetReadArticles,
): ResetReadArticle {
    return articles.firstOrNull { it.id !in readIds }
        ?: articles[(readIds.size % articles.size).coerceAtLeast(0)]
}

fun ResetReadArticle.sharesAnyTagWith(other: ResetReadArticle): Boolean {
    return tags.any { it in other.tags }
}

fun recommendedResetReadArticleForDay(
    epochDay: Long,
    articles: List<ResetReadArticle> = StarterResetReadArticles,
    sessions: List<ResetReadSessionRecord> = emptyList(),
    readIds: Set<ResetReadArticleId> = emptySet(),
    excludedArticleIds: Set<ResetReadArticleId> = emptySet(),
): ResetReadArticle {
    if (articles.isEmpty()) error("No reset reading articles available")
    val eligibleArticles = articles
        .filterNot { article -> article.id in excludedArticleIds }
        .ifEmpty { articles }

    val preferredTags = sessions
        .asSequence()
        .filter { it.validCompletion && (it.helpfulnessRating ?: 0) >= 4 }
        .mapNotNull { session -> articles.firstOrNull { article -> article.id == session.articleId } }
        .flatMap { article -> article.tags.asSequence() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key },
        )
        .map { it.key }

    if (preferredTags.isEmpty()) {
        return nextArticleFor(readIds = readIds, articles = eligibleArticles)
    }

    val unreadSimilar = eligibleArticles.filter { article ->
        article.id !in readIds && article.tags.any { tag -> tag in preferredTags }
    }

    if (unreadSimilar.isNotEmpty()) {
        return unreadSimilar.rotatingPick(epochDay)
    }

    val anySimilar = eligibleArticles.filter { article ->
        article.tags.any { tag -> tag in preferredTags }
    }

    if (anySimilar.isNotEmpty()) {
        return anySimilar.rotatingPick(epochDay)
    }

    return nextArticleFor(readIds = readIds, articles = eligibleArticles)
}

private fun List<ResetReadArticle>.rotatingPick(epochDay: Long): ResetReadArticle {
    val index = epochDay.mod(size.toLong()).toInt()
    return this[index]
}

private fun List<ArticleBlock>.wordCount(): Int = sumOf { block ->
    when (block) {
        is ArticleBlock.Heading -> block.text.wordCount()
        is ArticleBlock.Paragraph -> block.text.wordCount()
        is ArticleBlock.Img -> 0
        is ArticleBlock.Video -> 0
    }
}

private fun String.wordCount(): Int =
    trim()
        .split(Regex("\\s+"))
        .count { it.isNotBlank() }
