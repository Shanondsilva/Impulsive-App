package com.impulsive.app.backend.domain.model.tasks

typealias LessonId = String

data class MindLesson(
    val id: LessonId,
    val title: String,
    val cards: List<LessonCard>,
    val checkQuestion: LessonCheckQuestion,
)

sealed interface LessonCard {
    data class Text(
        val line: String,
        val illustrationKey: String? = null,
    ) : LessonCard

    data class SpotTheDifference(
        val prompt: String,
        val baseScene: SceneSpec,
        val diffHotspots: List<NormRect>,
    ) : LessonCard

    data class FindTarget(
        val prompt: String,
        val scene: SceneSpec,
        val targetHotspot: NormRect,
    ) : LessonCard
}

data class NormRect(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

data class SceneSpec(
    val kind: SceneKind,
    val seed: Int,
)

enum class SceneKind {
    Orbs,
    Tiles,
    Path,
    Window,
}

data class LessonCheckQuestion(
    val prompt: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val shortExplanationForEachOption: List<String>,
)

val StarterMindLessons = listOf(
    MindLesson(
        id = "night_spikes",
        title = "Why urges can spike at night",
        cards = listOf(
            LessonCard.Text("At night, your brain has fewer fresh inputs. Quiet space can make old loops feel louder.", "moon"),
            LessonCard.SpotTheDifference(
                prompt = "Tap the small differences in the calm night scene.",
                baseScene = SceneSpec(SceneKind.Window, seed = 11),
                diffHotspots = listOf(
                    NormRect(0.22f, 0.22f, 0.10f, 0.14f),
                    NormRect(0.68f, 0.58f, 0.12f, 0.12f),
                ),
            ),
            LessonCard.Text("A small reset works because it gives attention something clear to hold right now.", "steps"),
        ),
        checkQuestion = LessonCheckQuestion(
            prompt = "What helps most when attention is tired?",
            options = listOf("A clear small action", "Arguing with every thought", "Waiting for perfect motivation"),
            correctAnswerIndex = 0,
            shortExplanationForEachOption = listOf(
                "Yes. A small action gives attention a simple next step.",
                "That can make the loop feel more important than it is.",
                "Motivation often arrives after the first action, not before it.",
            ),
        ),
    ),
    MindLesson(
        id = "fifteen_minute_wave",
        title = "The 15-minute wave",
        cards = listOf(
            LessonCard.Text("An urge usually changes shape when you stop feeding it with attention.", "wave"),
            LessonCard.FindTarget(
                prompt = "Find the calm spot in the scene.",
                scene = SceneSpec(SceneKind.Orbs, seed = 24),
                targetHotspot = NormRect(0.56f, 0.30f, 0.14f, 0.14f),
            ),
            LessonCard.Text("A timer, a game, or a short lesson can carry you through the first wave.", "bridge"),
        ),
        checkQuestion = LessonCheckQuestion(
            prompt = "What is the point of a short reset?",
            options = listOf("Create space for the wave to shift", "Prove you never have urges", "Make the day perfect"),
            correctAnswerIndex = 0,
            shortExplanationForEachOption = listOf(
                "Right. The reset creates space for intensity to change.",
                "Urges are signals, not proof of who you are.",
                "A useful reset only needs to help the next few minutes.",
            ),
        ),
    ),
    MindLesson(
        id = "what_trigger_is",
        title = "What a trigger actually is",
        cards = listOf(
            LessonCard.Text("A trigger is a cue your brain has learned to connect with a familiar routine.", "spark"),
            LessonCard.SpotTheDifference(
                prompt = "Tap the differences in the matching cue cards.",
                baseScene = SceneSpec(SceneKind.Tiles, seed = 37),
                diffHotspots = listOf(
                    NormRect(0.18f, 0.18f, 0.12f, 0.12f),
                ),
            ),
            LessonCard.Text("Naming the cue is useful because it moves the moment from autopilot into awareness.", "label"),
        ),
        checkQuestion = LessonCheckQuestion(
            prompt = "Why name the trigger?",
            options = listOf("It creates a choice point", "It blames you", "It makes the cue disappear forever"),
            correctAnswerIndex = 0,
            shortExplanationForEachOption = listOf(
                "Yes. Naming the cue gives you a moment to choose.",
                "No. This is about information, not blame.",
                "Cues may return, but recognition makes them easier to handle.",
            ),
        ),
    ),
    MindLesson(
        id = "dopamine_plain_words",
        title = "Dopamine in plain words",
        cards = listOf(
            LessonCard.Text("Dopamine is part of how the brain marks something as worth pursuing.", "dot"),
            LessonCard.FindTarget(
                prompt = "Tap the item that stands out.",
                scene = SceneSpec(SceneKind.Path, seed = 52),
                targetHotspot = NormRect(0.64f, 0.48f, 0.12f, 0.12f),
            ),
            LessonCard.Text("Switching attention early matters because it interrupts the chase phase.", "switch"),
        ),
        checkQuestion = LessonCheckQuestion(
            prompt = "When can dopamine be active?",
            options = listOf("During wanting and searching", "Only after a result", "Only when something is healthy"),
            correctAnswerIndex = 0,
            shortExplanationForEachOption = listOf(
                "Correct. Wanting and searching are a big part of the loop.",
                "Not quite. It can rise before a result.",
                "Not quite. It is about learning and pursuit, not moral labels.",
            ),
        ),
    ),
    MindLesson(
        id = "willpower_alone",
        title = "Why willpower alone gets tired",
        cards = listOf(
            LessonCard.Text("Willpower is easier when your environment and next step are already set up.", "battery"),
            LessonCard.Text("If every urge becomes a long argument, attention gets drained.", "thread"),
            LessonCard.Text("A prepared replacement action lowers the number of decisions you need to make.", "route"),
        ),
        checkQuestion = LessonCheckQuestion(
            prompt = "What supports willpower best?",
            options = listOf("A prepared next action", "A longer argument", "Ignoring every pattern"),
            correctAnswerIndex = 0,
            shortExplanationForEachOption = listOf(
                "Yes. Prepared actions reduce decision load.",
                "Long arguments can keep attention stuck on the loop.",
                "Patterns are easier to change when you can see them.",
            ),
        ),
    ),
    MindLesson(
        id = "replacement_habit",
        title = "How a replacement habit forms",
        cards = listOf(
            LessonCard.Text("A replacement habit starts small: cue, action, and a clear finish.", "seed"),
            LessonCard.Text("The new action should be easy enough to do under stress. Simple beats impressive.", "leaf"),
            LessonCard.FindTarget(
                prompt = "Tap the calm finish point.",
                scene = SceneSpec(SceneKind.Window, seed = 71),
                targetHotspot = NormRect(0.50f, 0.62f, 0.14f, 0.14f),
            ),
        ),
        checkQuestion = LessonCheckQuestion(
            prompt = "What makes a replacement habit easier to repeat?",
            options = listOf("A simple action with a clear finish", "A huge goal", "A hidden plan"),
            correctAnswerIndex = 0,
            shortExplanationForEachOption = listOf(
                "Right. Simple and clear is easier to repeat.",
                "Huge goals can be useful later, but they are harder during an urge.",
                "Plans work better when they are visible and ready.",
            ),
        ),
    ),
)

fun nextLessonFor(
    completedIds: Set<LessonId>,
    lessons: List<MindLesson> = StarterMindLessons,
): MindLesson {
    return lessons.firstOrNull { it.id !in completedIds }
        ?: lessons[(completedIds.size % lessons.size).coerceAtLeast(0)]
}
