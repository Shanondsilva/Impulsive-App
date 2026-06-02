package com.impulsive.app.backend.domain.model.tasks

import kotlin.math.ceil

typealias ResetReadArticleId = String

const val RESET_READ_REMOTE_ENABLED = false

sealed interface ArticleBlock {
    data class Heading(val text: String) : ArticleBlock
    data class Paragraph(val text: String) : ArticleBlock
    data class Img(val key: String, val caption: String? = null) : ArticleBlock
}

data class ResetReadArticle(
    val id: ResetReadArticleId,
    val title: String,
    val blocks: List<ArticleBlock>,
    val closingQuestion: ResetReadQuestion,
    val articleUrl: String? = null,
) {
    val estimatedReadMinutes: Int
        get() = maxOf(1, ceil(blocks.wordCount() / 200.0).toInt())

    val minimumReadSeconds: Int
        get() = minOf(estimatedReadMinutes, 3) * 60
}

data class ResetReadQuestion(
    val prompt: String,
    val options: List<String>,
)

val StarterResetReadArticles = listOf(
    ResetReadArticle(
        id = "night_window_reset",
        title = "A steadier plan for the late window",
        blocks = listOf(
            ArticleBlock.Heading("What the late window is"),
            ArticleBlock.Paragraph("Late hours can feel different because the day has less structure. Fewer tasks are arriving. Fewer people are asking for attention. That quiet can make an old loop feel closer than it was earlier in the day, even when nothing outside you has changed."),
            ArticleBlock.Paragraph("The late window is not a moral test. It is a setting. In a quieter setting, attention often reaches for the most rehearsed path. That is why a clear next step matters more than a long explanation."),
            ArticleBlock.Img("lavender_window", "A calm window with a soft glow"),
            ArticleBlock.Heading("Make the first move smaller"),
            ArticleBlock.Paragraph("A helpful plan starts with one move that is easy to begin. Stand up. Fill a glass. Open a timer. Put the phone down for one minute. The first action works because it creates a pause when it feels like you need to decide everything at once."),
            ArticleBlock.Paragraph("If the first move is too large, the mind keeps comparing it with the familiar loop. Small moves reduce that comparison. They are easier to start, easier to repeat, and easier to finish without turning the moment into a debate."),
            ArticleBlock.Img("soft_steps", "A path of small dots across a calm surface"),
            ArticleBlock.Heading("Use an if-then plan"),
            ArticleBlock.Paragraph("An if-then plan is simple: if the cue shows up, then I do the next action I already chose. If it is late and the room feels heavy, then I move to a different space. If my attention feels foggy, then I open the short task already waiting."),
            ArticleBlock.Paragraph("This kind of plan works because it removes delay. You do not need to invent the response while the moment feels loud. You only need to carry out the next small step you prepared earlier."),
            ArticleBlock.Paragraph("A short recap: the late window is quieter, quiet can make a loop feel nearer, and one easy action can change the scene. You are not trying to become perfect. You are making the next ten minutes easier to live through."),
            ArticleBlock.Img("steady_line", "A simple line that stays level"),
        ),
        closingQuestion = defaultResetReadQuestion(),
    ),
    ResetReadArticle(
        id = "attention_needs_a_task",
        title = "Attention needs a job",
        blocks = listOf(
            ArticleBlock.Heading("Why idle attention drifts"),
            ArticleBlock.Paragraph("When attention has no clear job, it tends to return to whatever is most familiar. That is not a failure. It is a habit of the mind. The loop feels loud because it is well rehearsed, not because it is the only thing available."),
            ArticleBlock.Paragraph("A useful reset gives attention something else to hold. Not a perfect replacement. Just a clear one. The mind does better with a simple task than with a vague command to stop thinking."),
            ArticleBlock.Img("calm_task", "A soft circle with a centered point"),
            ArticleBlock.Heading("What a useful task looks like"),
            ArticleBlock.Paragraph("The best next task is concrete. It can be a timer, a short game, a note, a glass of water, a walk to another room, or a few lines of reading. It should be easy to start and obvious when it is done."),
            ArticleBlock.Paragraph("A task does not need to be dramatic to work. It only needs enough shape to hold the next few minutes. That shape keeps attention from drifting back into the same track before you have had time to settle."),
            ArticleBlock.Img("task_stack", "Stacked cards in calm lavender and teal"),
            ArticleBlock.Heading("Reduce choices before the cue arrives"),
            ArticleBlock.Paragraph("A good plan is easier when it is already in place. Keep the next action visible. Put a focus screen on the phone. Leave a book open. Keep the next task in one tap rather than buried in a menu. Lowering friction matters because the first minute is usually the hardest minute."),
            ArticleBlock.Paragraph("If the next action is easy to reach, attention has somewhere else to go before the old path builds momentum. That is the point. You are not forcing the mind into silence. You are giving it a cleaner job."),
            ArticleBlock.Paragraph("A short recap: idle attention drifts, concrete tasks hold attention, and preparation matters before the cue shows up. A small, visible job is often enough to carry the next ten minutes."),
            ArticleBlock.Img("clear_bell", "A small bell shape with a soft glow"),
        ),
        closingQuestion = defaultResetReadQuestion(),
    ),
    ResetReadArticle(
        id = "the_first_minute",
        title = "The first minute matters",
        blocks = listOf(
            ArticleBlock.Heading("A choice point, not a verdict"),
            ArticleBlock.Paragraph("The first minute after a cue is a choice point. It is often easier to change direction there than after several minutes of scrolling, searching, or replaying the same thought. Early moves are simpler because the loop has not had time to gather speed."),
            ArticleBlock.Paragraph("That is why a short reset can matter more than a long explanation. The goal is not to solve your whole day. The goal is to steer the next minute toward something more useful."),
            ArticleBlock.Img("first_turn", "A curve that turns gently left"),
            ArticleBlock.Heading("Tiny moves change the scene"),
            ArticleBlock.Paragraph("A tiny move can change the whole context. Lock the screen. Stand up. Put water on the table. Open a timer. Start a short task. Each action breaks the chain just enough to make the next choice easier."),
            ArticleBlock.Paragraph("If you miss the first minute, the moment is still not lost. You have the next minute. Returning to plan is often a series of small returns, not a single perfect stop. That is why the app asks for one helpful action rather than a flawless one."),
            ArticleBlock.Img("door_open", "An open doorway with soft light"),
            ArticleBlock.Heading("Build a rule you can trust later"),
            ArticleBlock.Paragraph("A useful rule is one you can remember when attention is busy. For example: if the cue appears, then I will start the short lesson before I decide anything else. Or: if the room feels stuck, then I will move to a different space first."),
            ArticleBlock.Paragraph("Rules like that work because they save time. You do not need to solve the same question from scratch every time. You already chose a direction. The next action is simply the first step in that direction."),
            ArticleBlock.Paragraph("A short recap: the first minute is a choice point, tiny moves change the scene, and one clear rule helps you return faster next time."),
            ArticleBlock.Img("steady_gate", "A small gate standing open"),
        ),
        closingQuestion = defaultResetReadQuestion(),
    ),
    ResetReadArticle(
        id = "why_patterns_repeat",
        title = "Why patterns repeat",
        blocks = listOf(
            ArticleBlock.Heading("A learned shortcut"),
            ArticleBlock.Paragraph("A repeated pattern is usually a learned shortcut. The mind notices a cue and offers the route it knows best. That makes the pattern feel automatic even when you do not want it to run."),
            ArticleBlock.Paragraph("Shortcuts are not permanent. They are learned, which means they can be updated. The update starts the moment you connect the cue to a different action, even if that action is very small."),
            ArticleBlock.Img("shortcut_map", "Two paths diverging across a soft field"),
            ArticleBlock.Heading("Make the new route easy to choose"),
            ArticleBlock.Paragraph("The new route needs to be easy enough to use under pressure. If it is too complicated, the old shortcut stays stronger. A replacement action works best when it is obvious, quick to start, and easy to finish."),
            ArticleBlock.Paragraph("This is why the app keeps suggesting short, concrete steps. They are not meant to impress anyone. They are meant to be available at the exact moment when your attention is tired or crowded."),
            ArticleBlock.Img("soft_branches", "A branch with one calm offshoot"),
            ArticleBlock.Heading("Repetition changes the default"),
            ArticleBlock.Paragraph("Each time you choose the replacement action, you make that path a little easier to find next time. Not by force. By repetition. The default becomes less automatic, and your chosen response becomes easier to remember."),
            ArticleBlock.Paragraph("Progress can look small from the outside: one fewer detour, one shorter pause, one earlier turn toward a different task. Those small shifts matter because they change what the mind expects next."),
            ArticleBlock.Paragraph("A short recap: patterns repeat because they are learned shortcuts, shortcuts can be updated, and repetition makes the new route easier to use."),
            ArticleBlock.Img("gentle_loop", "A loop with a new branch opening away from it"),
        ),
        closingQuestion = defaultResetReadQuestion(),
    ),
    ResetReadArticle(
        id = "a_private_comeback",
        title = "A private comeback",
        blocks = listOf(
            ArticleBlock.Heading("Quiet progress counts"),
            ArticleBlock.Paragraph("A comeback often looks quiet from the outside. It can be one person changing rooms, starting a timer, and getting through the next ten minutes. That may not look dramatic, but it is real practice."),
            ArticleBlock.Paragraph("You are teaching yourself that a cue does not have to decide the rest of the evening. That lesson is useful because it is repeatable. You can return to it the next time the same feeling shows up."),
            ArticleBlock.Img("private_room", "A soft room with one lit corner"),
            ArticleBlock.Heading("Measure what changed"),
            ArticleBlock.Paragraph("Progress is not only perfect days. It is also shorter loops, earlier exits, calmer resets, and more honest noticing. These are visible signs that the path is changing even if the day is not flawless."),
            ArticleBlock.Paragraph("A private comeback does not need an audience. It only needs a next action. That action can be tiny. It can be brief. It can be enough. The important part is that you used it when it mattered."),
            ArticleBlock.Img("small_win", "A tiny bright point inside a larger shape"),
            ArticleBlock.Heading("Keep the next ten minutes in view"),
            ArticleBlock.Paragraph("The next ten minutes are enough to practice. Pick one action you can actually do now. Put the phone down. Start a focus session. Write one line. Open a game that uses your attention well. Let the plan be simple enough to use without thinking hard."),
            ArticleBlock.Paragraph("That is the shape of a private comeback: small, repeatable, and practical. The work is not about proving anything. It is about making the immediate moment easier to carry."),
            ArticleBlock.Paragraph("A short recap: quiet progress counts, small wins matter, and the next ten minutes are enough to practice a better route."),
            ArticleBlock.Img("calm_finish", "A calm finish line in soft lavender"),
        ),
        closingQuestion = defaultResetReadQuestion(),
    ),
    ResetReadArticle(
        id = "make_the_next_action_visible",
        title = "Make the next action visible",
        blocks = listOf(
            ArticleBlock.Heading("Visible beats vague"),
            ArticleBlock.Paragraph("A plan is easier to use when it is visible. If your replacement action is hidden or vague, the old routine has less competition. The mind usually reaches for what it can see first."),
            ArticleBlock.Paragraph("That means the helpful action should be placed where it is easy to notice. Open the focus screen before the cue. Leave a note in view. Put the next step in a place that does not require extra thinking."),
            ArticleBlock.Img("visible_note", "A note card standing upright"),
            ArticleBlock.Heading("Shape the environment"),
            ArticleBlock.Paragraph("The environment does some of the work for you. If the next action is already visible, attention does not need to search for it. Search costs time. Time is where a lot of loops gain momentum."),
            ArticleBlock.Paragraph("A visible plan can be simple. It can be one button, one page, one book, one timer, or one short article. Simplicity is not a weakness here. It is the reason the plan survives a tired moment."),
            ArticleBlock.Img("quiet_setup", "A tidy setup with soft blocks and a timer"),
            ArticleBlock.Heading("Make the first step obvious"),
            ArticleBlock.Paragraph("The first step should answer the question, 'What do I do now?' without making you think. If the answer is already visible, attention can move faster. That is useful because the habit loop loses strength when it does not get extra time."),
            ArticleBlock.Paragraph("You are not removing every cue from life. You are making the helpful action easier to start. That shift can be enough to change the next few minutes, which is the part that matters in the app."),
            ArticleBlock.Paragraph("A short recap: visible beats vague, the environment can reduce friction, and the first step should be obvious enough to use when you are tired."),
            ArticleBlock.Img("visible_route", "A bright line leading to a calm path"),
        ),
        closingQuestion = defaultResetReadQuestion(),
    ),
)

fun nextArticleFor(
    readIds: Set<ResetReadArticleId>,
    articles: List<ResetReadArticle> = StarterResetReadArticles,
): ResetReadArticle {
    return articles.firstOrNull { it.id !in readIds }
        ?: articles[(readIds.size % articles.size).coerceAtLeast(0)]
}

private fun defaultResetReadQuestion(): ResetReadQuestion = ResetReadQuestion(
    prompt = "What's one thing you'll do for the next 10 minutes instead?",
    options = listOf(
        "Play a quick game",
        "Step away from the screen",
        "Start a focus session",
        "Write it down",
    ),
)

private fun List<ArticleBlock>.wordCount(): Int = sumOf { block ->
    when (block) {
        is ArticleBlock.Heading -> block.text.wordCount()
        is ArticleBlock.Paragraph -> block.text.wordCount()
        is ArticleBlock.Img -> 0
    }
}

private fun String.wordCount(): Int =
    trim()
        .split(Regex("\\s+"))
        .count { it.isNotBlank() }
