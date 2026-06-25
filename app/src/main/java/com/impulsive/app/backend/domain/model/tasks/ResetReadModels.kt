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
    data class Lottie(
        val rawResName: String,
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
            ArticleBlock.Lottie(
                rawResName = "surf_urge_wave_rise_fall",
                title = "Watch the urge rise and fall",
                caption = "The wave can move without you obeying it.",
            ),
            ArticleBlock.Paragraph("In the first seconds, an urge can feel bigger than your whole plan. Your brain starts speaking in emergency language: open it now, check it now, search it now, you will not relax until you obey. That voice feels powerful because it arrives quickly, but speed is not truth. A fire alarm is loud, but it is not always proof that the house is burning."),
            ArticleBlock.Paragraph("Surfing the urge means you stop treating the urge like a command and start treating it like a signal. You do not have to argue with it. You do not have to prove you are strong. You simply notice it clearly: this is craving, this is pressure, this is my body asking for the old shortcut."),
            ArticleBlock.Paragraph("The wave has a shape. It rises, it peaks, and then it changes. If you feed it with searching, fantasy, scrolling, testing, or bargaining, it gets fresh fuel. If you watch it without feeding it, your body slowly learns that nothing urgent has to happen right now."),
            ArticleBlock.Paragraph("Your mind may still offer reasons. It may say you already started, you cannot sleep, one look will not matter, or today has been stressful. Let those thoughts pass through the same way you let the body signal pass through. You are not required to answer every thought that knocks."),
            ArticleBlock.Paragraph("During Reset Reading, your goal is not to feel clean, holy, perfect, or fully calm. Your goal is smaller and more useful. Stay present long enough for the peak to lose its sharp edge. Even if the urge remains, it can become less automatic."),
            ArticleBlock.Paragraph("Every time you wait through one wave, you teach your brain a new fact. The urge can rise without becoming action. The body can ask without receiving. The old loop can be interrupted before it becomes a hidden search."),
            ArticleBlock.Paragraph("This is not passive. It is active control. You are choosing to stay with the discomfort without turning it into the old behaviour. That is a serious skill. The more often you practise it, the less mysterious the urge becomes. It is still uncomfortable, but it is no longer automatically in charge."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("When the pull hits, name three things quietly: where you feel it, how strong it is, and whether it is rising or falling. Then do nothing with it for one minute. No searching, no checking, no testing yourself. Just watch the wave. If it drops even slightly, you have proof that the urge can move without you obeying it."),
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
            ArticleBlock.Lottie(
                rawResName = "ninety_second_rule_peak_settle",
                title = "Let the first spike settle",
                caption = "The first emotional peak can change if you give it time.",
            ),
            ArticleBlock.Paragraph("When a trigger hits, your body reacts before your calmer mind has finished understanding what happened. Attention narrows. Your hands know where to go. The brain starts looking for the fastest relief it remembers. In that moment, the old habit can feel logical, even when you already know it will not help you tomorrow. That is why a short delay matters."),
            ArticleBlock.Paragraph("The useful idea behind a 90-second reset is simple: the first emotional surge does not stay at the same strength forever. It rises, changes, and begins to settle. What often keeps it alive is the second wave, the story you repeat after the first hit: I need this, I cannot wait, I already failed, one more time does not matter."),
            ArticleBlock.Paragraph("Reset Reading gives your body time to move out of that first spike. You are not trying to solve your entire life in ninety seconds. You are creating a pause big enough for choice to come back online. That pause is small, but it is real."),
            ArticleBlock.Paragraph("This matters because many relapses are not planned decisions. They are fast chains. Trigger, open app, search, hide, continue. The chain feels smooth because you have repeated it before. If you interrupt it early, the habit has to work harder to pull you forward."),
            ArticleBlock.Paragraph("The timer is not there to annoy you. It is there to hold the door closed while your nervous system cools down. You read, breathe, and wait while the first rush burns through some of its fuel. You do not need to win the whole battle at once."),
            ArticleBlock.Paragraph("After ninety seconds, the urge may not be gone. That is fine. The better question is whether it is exactly the same. If it is even slightly softer, slower, or more visible, control has started returning."),
            ArticleBlock.Paragraph("You can also use the reset to avoid making a permanent decision from a temporary body state. A craving feels urgent because it compresses time. It makes five minutes feel impossible. The reset expands time again. It reminds you that waiting is not losing. Waiting is the first proof that the pattern can bend."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("For the next ninety seconds, do not debate the urge. Let the timer carry the decision. Keep reading until the line feels slower in your body. When the time ends, ask one honest question: is the urge exactly as sharp as it was at the start, or has it changed even a little? A little change is enough proof to continue. That is control returning in real time."),
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
            ArticleBlock.Video(
                assetFileName = "make_it_harder_phone_away.mp4",
                title = "Create distance before the urge gets loud",
                caption = "A small obstacle gives your slower brain time to catch up.",
            ),
            ArticleBlock.Paragraph("We often imagine self-control as a dramatic moment where you stand in front of temptation and win by force. Real control is usually quieter. It happens before the hard moment becomes too hard. It happens when you move the phone, log out, close the tab, leave the room, or make the old route slightly less automatic."),
            ArticleBlock.Paragraph("Friction is not punishment. It is design. A door with a handle still opens, but the handle makes you notice that you are choosing to open it. The same idea works with habits. Every small obstacle gives your slower brain a few extra seconds to catch up with the fast impulse."),
            ArticleBlock.Paragraph("The goal is not to make life miserable. The goal is to stop the risky action from being the easiest action in the room. If the old habit can happen in two taps, two seconds, and total privacy, it will keep winning when you are tired. Add one extra step and the habit loses some of its speed."),
            ArticleBlock.Paragraph("Good friction is small, clear, and prepared before the urge arrives. Charge the phone away from bed. Remove saved passwords. Move tempting apps off the first screen. Keep the browser out of reach during late hours. Use blockers, but also use physical distance. The body obeys the room it is in."),
            ArticleBlock.Paragraph("Friction works because many urges are not deep decisions. They are fast paths. The brain sees an opening and takes it. If the opening is less smooth, you get a chance to wake up inside the pattern."),
            ArticleBlock.Paragraph("Do not confuse friction with weakness. Strong people use systems because they respect the power of the moment. They do not wait until the urge is screaming and then ask willpower to save everything alone."),
            ArticleBlock.Paragraph("The best friction is chosen calmly before the risky moment. It should not feel like punishment. It should feel like a guardrail you placed because you know the road can become slippery."),
            ArticleBlock.Paragraph("That guardrail is most useful when it is boring. The point is not drama. The point is that the old path no longer opens instantly, and the extra second gives your better intention somewhere to stand."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("Pick one small obstacle today. Not a huge life change. One step. Put the phone across the room. Remove one shortcut. Add one password you have to type slowly. When the urge arrives, notice the pause that obstacle creates. That pause is useful. It is not wasted time. It is the space where choice can return before autopilot takes over."),
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
            ArticleBlock.Video(
                assetFileName = "trade_safe_replacement_water.mp4",
                title = "Trade the old route for a safer action",
                caption = "A small replacement can interrupt the habit without leaving an empty space.",
            ),
            ArticleBlock.Paragraph("Most people try to beat a habit by saying, I will just stop. That sounds strong, but it leaves the brain with a problem. The old cue still appears. The old feeling still arrives. The old reward is still remembered. If there is no replacement, the old routine has an empty space to return to. The brain needs a next action, not only a rule."),
            ArticleBlock.Paragraph("A better move is to trade. Keep the moment, but change what happens inside it. If the habit gave quick stimulation, choose a safer quick stimulation. If it gave comfort, choose safer comfort. If it gave escape, choose a short escape that does not damage the rest of the day."),
            ArticleBlock.Paragraph("The replacement must be small enough to start when you are weak. A perfect habit that needs high motivation will fail during a real urge. A tiny action you can start immediately is more useful. Drink water. Stand up. Open Reset Reading. Hold something cold. Step into another room."),
            ArticleBlock.Paragraph("The first trade does not have to feel amazing. It only has to interrupt the old route. Your brain learns through repeated paths. If the old path is trigger, search, release, regret, then the new path can be trigger, pause, read, choose. At first, it may feel awkward. That does not mean it is failing."),
            ArticleBlock.Paragraph("Over time, the trade becomes easier to find. The brain starts learning that an urge does not always end in the same behaviour. It can end in a reset, a walk, a game, a journal line, a call, a prayer, or sleep. More exits mean less slavery to one exit."),
            ArticleBlock.Paragraph("Your goal is not to become a different person instantly. Your goal is to build one safer route and use it before the old route takes over. Small trades become proof."),
            ArticleBlock.Paragraph("The trade also protects you from all-or-nothing thinking. If you only say stop, then one difficult moment can feel like failure. If you say trade first, you always have a next move. You are not trapped between perfection and relapse. You are building a bridge between urge and control."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("Pick one trade before the next urge arrives. Not ten options. One. If I reach for the old habit, I first open Reset Reading. If I still want the old habit after the reset, I decide again with a clearer head. That small trade creates space where autopilot used to live. This is practical recovery, not theory right now."),
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
            ArticleBlock.Lottie(
                rawResName = "dopamine_promise_path",
                title = "Notice the promise path",
                caption = "The pull often comes from anticipation, not real reward.",
            ),
            ArticleBlock.Paragraph("Dopamine is often called the pleasure chemical, but that shortcut can mislead you. A lot of the pull you feel is not simple enjoyment. It is anticipation. It is your brain pointing at a possible reward and saying, go there, something good might happen. That message can feel exciting even when the real result is disappointing. The hook is anticipation, not wisdom."),
            ArticleBlock.Paragraph("That is why the chase can feel stronger than the finish. Opening the app, typing the search, refreshing the page, checking one more image, following one more link. The promise keeps moving ahead of you. The brain is not only enjoying. It is hunting."),
            ArticleBlock.Paragraph("This matters because many urges are built on prediction, not truth. Your brain predicts relief, comfort, excitement, escape, or release. But after the behaviour, the real feeling may be flat, guilty, tired, bored, or simply not worth the cost. The prediction was loud. The result was small."),
            ArticleBlock.Paragraph("The old loop survives by hiding that gap. It makes you remember the promise more than the after-feeling. It shows you the doorway, not the room after you enter. Reset Reading is a way to slow the advertisement down long enough to inspect it."),
            ArticleBlock.Paragraph("A powerful question is: am I chasing the real reward, or am I chasing the promise of a reward? You do not need a perfect answer. You only need enough doubt to pause. Doubt weakens autopilot. It gives your wiser mind a few seconds to step in."),
            ArticleBlock.Paragraph("When the promise appears, do not call yourself weak. Your brain is doing what trained reward systems do. The solution is not shame. The solution is to stop giving the promise instant obedience every time it flashes."),
            ArticleBlock.Paragraph("This is why a pause can feel strangely powerful. You are not only avoiding one action. You are breaking the sale. You are refusing to buy the promise at full price. The longer you wait, the more clearly you can see whether the reward was real or just a bright sign pointing toward the old loop."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("Remember the last time you chased something quickly. Before doing it, the promise may have felt like an eight or nine out of ten. Afterward, the real satisfaction may have been much lower. Hold that gap in mind. Tell yourself: this is the hype stage. I do not have to believe the advertisement my brain is showing me. That moment of distance is where control begins, and the chase weakens."),
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
            ArticleBlock.Video(
                assetFileName = "tired_brains_phone_charging.mp4",
                title = "Protect the tired-brain window",
                caption = "A calm night setup can protect the moment before willpower drops.",
            ),
            ArticleBlock.Paragraph("When you are exhausted, your brain does not negotiate urges from a strong position. The part that pauses, plans, compares consequences, and remembers tomorrow needs energy. When sleep is short, that system becomes slower. The faster parts of the brain, the parts that want relief now, become harder to manage."),
            ArticleBlock.Paragraph("That is why late night can feel like a different personality. In the morning, you may know exactly what matters. At night, the same promise can sound boring, distant, or impossible. The urge does not always become stronger because it is true. Sometimes it becomes stronger because your brakes are tired."),
            ArticleBlock.Paragraph("Tired brains also look for easy relief. They do not want complex plans, long reflections, or perfect discipline. They want the quickest route out of discomfort. Scrolling, searching, eating, snapping, or giving in can all start to look like solutions when the body is asking for rest."),
            ArticleBlock.Paragraph("This does not excuse the old habit, but it explains why the same trigger is more dangerous at certain hours. If you keep fighting the hardest battles when your energy is lowest, the app should help you change the battlefield. Less debate. More protection. Fewer open doors."),
            ArticleBlock.Paragraph("One useful rule is to treat tiredness as a risk signal, not a moral failure. When you notice I am tired, translate it into my control system is lower right now. That sentence removes shame and gives you a practical next step."),
            ArticleBlock.Paragraph("At night, the best recovery action may be smaller than usual. Reset Reading, a locked phone, a drink of water, lights off, or moving the device away can beat a heroic plan you will not follow."),
            ArticleBlock.Paragraph("This is why sleep protection belongs inside recovery, not outside it. A rested brain does not remove every urge, but it gives you more room between the trigger and the action."),
            ArticleBlock.Paragraph("So when the app asks you to reset at night, it is not asking for a heroic performance. It is helping you properly protect a low-battery moment from becoming the same old mistake again tonight."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("Think of one time window where you repeatedly give in. If it is late, after work, after scrolling in bed, or after poor sleep, mark it as a weak-brakes window. Do not wait for motivation there. Prepare earlier. Put distance between you and the trigger before the tired brain starts bargaining. Rest is not laziness in recovery. Rest is part of the control system."),
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
            ArticleBlock.Lottie(
                rawResName = "breathe_slower_inhale_exhale",
                title = "Slow the body first",
                caption = "A steady breathing rhythm helps the next choice become possible.",
            ),
            ArticleBlock.Paragraph("When an urge or stress spike hits, the body shifts into alarm. Heart rate rises, muscles prepare, attention tightens, and breathing often becomes quick and shallow. In that state, thinking clearly is harder because the body is acting as if something urgent is happening right now."),
            ArticleBlock.Paragraph("You cannot always command the alarm to stop by thinking. Telling yourself calm down may even make you more frustrated. But breathing gives you a physical route into the system. If you slow the breath, especially the exhale, the body receives a different message. It hears: we are not running, we are not chasing, we are not in immediate danger."),
            ArticleBlock.Paragraph("This is useful during cravings because cravings often borrow the language of emergency. They say now, quickly, before the chance disappears. Slow breathing interrupts that speed. It does not magically delete the urge, but it changes the pace of the moment. A slower body makes a slower choice more possible."),
            ArticleBlock.Paragraph("The exhale matters. A longer exhale is like lowering the volume on the alarm. Breathe in gently. Breathe out longer than you want to. Do it again. The mind may still complain, but the body starts receiving steadier instructions."),
            ArticleBlock.Paragraph("Breathing is also private. You can use it in bed, at work, in class, in a toilet cubicle, on a bus, or while holding your phone. Nobody has to know you are fighting a moment. That makes it a reliable fallback when movement or games are not possible."),
            ArticleBlock.Paragraph("Do not look for a dramatic feeling. Look for a small shift: less panic, less heat, less speed, more space. That is enough."),
            ArticleBlock.Paragraph("A slow breath also gives your hands something to wait for. Instead of moving straight toward the old habit, you are following a rhythm that makes the next ninety seconds easier to survive."),
            ArticleBlock.Paragraph("This makes breathing a bridge. It connects the body back to the mind. Once the body slows a little, the decision stops feeling like it has already been made."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("Breathe in through the nose for four counts. Breathe out slowly for six counts. Repeat it six times. Keep the shoulders loose and the jaw soft. If the urge talks while you breathe, let it talk. Your job is not to silence every thought. Your job is to slow the body enough for the next choice to become possible. When the sixth breath ends, ask whether the moment is even five percent slower. Five percent is a start."),
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
            ArticleBlock.Paragraph("A room, one treat, and a deal: wait, and you get two. The simple story missed the most useful lesson."),
            ArticleBlock.Lottie(
                rawResName = "marshmallow_waiting_choice_clock",
                title = "Wait without staring at the cue",
                caption = "Delaying works better when attention has somewhere safer to go.",
            ),
            ArticleBlock.Paragraph("The famous marshmallow experiment is often told as a story about children who had willpower and children who did not. One treat now, or two treats later. For years, people used it like a simple character test. Waiters were disciplined. Non-waiters were weak. Real life is not that simple."),
            ArticleBlock.Paragraph("Later thinking around the experiment made the useful lesson clearer. Many children who waited were not just staring at the treat with heroic strength. They changed their attention. They looked away, covered their eyes, sang, played, moved in the chair, or imagined the treat differently. They did not only resist. They redesigned the moment."),
            ArticleBlock.Paragraph("That matters for Reset Reading because the strongest strategy is often not to stare at temptation and demand strength. Staring keeps the reward bright in your mind. It gives the urge more pictures, more words, and more reasons. Looking away is not cowardice. It is strategy."),
            ArticleBlock.Paragraph("Trust also matters. Waiting is easier when you believe the better outcome is real. If your brain thinks delay gives nothing, it will push for now. That is why the app must give visible proof: a completed reset, points, progress, safer minutes, and another chance to choose. Waiting needs evidence."),
            ArticleBlock.Paragraph("Self-control is not just a muscle inside you. It is also the room, the promise, the timing, the distraction, the distance, and the reward you believe is coming. Change those things and you change the decision."),
            ArticleBlock.Paragraph("The lesson is practical. Do not fight the marshmallow by smelling it. Do not fight the urge by browsing near it. Change what your eyes, hands, and attention are doing."),
            ArticleBlock.Paragraph("The useful part is not the marshmallow. The useful part is the method. Waiting becomes easier when you stop feeding the cue and give your attention somewhere else to land."),
            ArticleBlock.Paragraph("For adults, the treat may be a phone, a search bar, a message, or a private memory. The method stays the same. Change attention before desire becomes action."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("When something tempting appears, do what the successful children often did: look away, cover the cue, change rooms, busy your hands, or start a different task. If the phone is the marshmallow, stop holding it like a test of character. Put it down, turn it over, or open Reset Reading instead. Your goal is not to prove you can stare at temptation. Your goal is to make waiting easier before the urge becomes a decision."),
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
            ArticleBlock.Lottie(
                rawResName = "habit_loop_cue_routine_reward",
                title = "Change the middle of the loop",
                caption = "Same cue, safer routine, better outcome.",
            ),
            ArticleBlock.Paragraph("Most habits are built from three pieces: a cue, a routine, and a reward. The cue starts the loop. The routine is what you do. The reward is what the brain gets from it. Repeat the same loop enough times and it begins to run before you have fully decided."),
            ArticleBlock.Paragraph("A cue can be almost anything. A time of day. A room. A feeling. A notification. A memory. Being alone. Feeling rejected. Lying in bed with the phone. The cue does not need to be dramatic. It only needs to be familiar enough for the brain to know what usually comes next."),
            ArticleBlock.Paragraph("The routine is the visible behaviour: scrolling, searching, opening a browser, checking a profile, typing a site, hiding the screen, or repeating a private pattern. The reward may be relief, stimulation, comfort, escape, control, or simply not feeling bored for a few minutes."),
            ArticleBlock.Paragraph("The mistake is trying to delete the whole loop at once. The brain does not like empty space. If the cue appears and no new routine is ready, the old routine slides back in because it is already learned."),
            ArticleBlock.Paragraph("A better strategy is to keep the cue and change the middle. Same trigger, safer routine, similar enough reward. If boredom is the cue and stimulation is the reward, use a game, a walk, a cold drink, or a short read as the replacement. If stress is the cue and relief is the reward, use breathing, prayer, journaling, or leaving the room."),
            ArticleBlock.Paragraph("The new routine may feel weaker at first because the old loop has had more practice. That is normal. Repetition is how it becomes easier."),
            ArticleBlock.Paragraph("This is how the app should help you. It should not only say stop. It should give the loop a safer middle step, then record that you used it."),
            ArticleBlock.Paragraph("When that happens, you are no longer relying on hope. You are training a repeatable pattern that can appear automatically the next time the same cue returns again under real private pressure later today."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("Pick one loop from your day. Name the cue, the routine, and the reward. For example: alone at night, open browser, feel escape. Now keep the cue and reward, but replace the middle: alone at night, open Reset Reading, feel a safer pause. Do not try to fix every loop today. Fix one. One changed loop is proof that autopilot can be edited."),
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
            ArticleBlock.Paragraph("By the end of a long day of choices, even sharp people can make sloppy ones. There is a reason."),
            ArticleBlock.Lottie(
                rawResName = "willpower_battery_recharge",
                title = "Protect the low-battery moment",
                caption = "The right system helps when willpower is tired.",
            ),
            ArticleBlock.Paragraph("Willpower can feel like a battery. You wake with clearer intentions, but each decision, delay, irritation, task, message, and temptation takes a little charge. By evening, the same choice that looked obvious in the morning can feel heavy, boring, or negotiable."),
            ArticleBlock.Paragraph("Researchers debate exactly how decision fatigue works, but the daily experience is familiar. After hours of choosing and resisting, people often slide into whatever is easiest. That is why late evening can become prime time for ah, whatever decisions. The brain does not want another debate. It wants the path with the least effort."),
            ArticleBlock.Paragraph("The answer is not to hate yourself for having a lower battery. The answer is to stop pretending the battery is full at all hours. Recovery becomes stronger when you design around your low-energy self, not only your motivated self."),
            ArticleBlock.Paragraph("Make the important choices earlier. Decide where the phone charges before night arrives. Decide which apps are blocked before you are triggered. Decide what your first fallback is before the urge starts negotiating. A decision made in the morning can protect the person you become at 11pm."),
            ArticleBlock.Paragraph("This also means fewer choices can be a form of strength. Routines are not boring when they protect you. A fixed sleep setup, a fixed charging place, a fixed fallback action, and a fixed reset rule all reduce the number of moments where you must fight from zero."),
            ArticleBlock.Paragraph("When the battery is low, use smaller actions. Do not demand a perfect transformation from an exhausted brain. Ask for one protected move: read, breathe, lock, stand up, or sleep."),
            ArticleBlock.Paragraph("The strongest plan is the one that still works when you are tired. If the plan needs perfect energy, it is not ready for the hardest hour of the day."),
            ArticleBlock.Paragraph("This is not laziness. It is engineering. You are building a system that carries your values when your mood, energy, and attention are no longer dependable at night or under private pressure alone when tempted later today."),
            ArticleBlock.Heading("Try it"),
            ArticleBlock.Paragraph("Choose one repeated decision and make it once, earlier in the day. If the decision is whether to scroll in bed, decide now: the phone charges away from the bed. If the decision is what to do when tempted, decide now: I open Reset Reading first. Later, when the battery is low, you do not need a debate. You only need to follow the decision your clearer self already made."),
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
        is ArticleBlock.Lottie -> 0
    }
}

private fun String.wordCount(): Int =
    trim()
        .split(Regex("\\s+"))
        .count { it.isNotBlank() }
