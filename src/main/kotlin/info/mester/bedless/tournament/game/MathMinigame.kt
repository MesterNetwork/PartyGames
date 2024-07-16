package info.mester.bedless.tournament.game

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.objecthunter.exp4j.ExpressionBuilder
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.*
import kotlin.random.Random

fun getRandomInt(min: Int, max: Int): Int {
    return Random.nextInt(min, max + 1)
}

fun getRandomOperator(): String {
    val operators = listOf("+", "-", "*")
    return operators[Random.nextInt(operators.size)]
}

fun generateExpression(): String {
    val numTerms = getRandomInt(2, 4)
    val expressionBuilder = StringBuilder()

    expressionBuilder.append(getRandomInt(1, 10)) // Start with the first random term

    for (i in 1 until numTerms) {
        val operator = getRandomOperator()
        val term = getRandomInt(1, 10)
        expressionBuilder.append(" $operator $term")
    }

    return expressionBuilder.toString()
}

fun generateMathQuestion(): MathQuestion {
    val question = generateExpression()
    val expression = ExpressionBuilder(question).build()
    val result = expression.evaluate().toInt()
    return MathQuestion(question, result)
}

data class MathQuestion(val question: String, val answer: Int)

class MathMinigame(private val _game: Game) : Minigame(_game) {
    private val questions = mutableMapOf<UUID, MathQuestion>()
    private val correctAnswers = mutableMapOf<UUID, Int>()

    init {
        _startPos = game.plugin.config.getLocation("locations.minigames.math")!!
    }

    override fun start() {
        super.start()

        _game.players().forEach { player ->
            correctAnswers[player.uniqueId] = 0

            val question = generateMathQuestion()
            questions[player.uniqueId] = question
            player.sendMessage(Component.text("Question: ${question.question} = ?", NamedTextColor.GREEN))
        }

        // end the minigame after a minute
        _game.plugin.server.scheduler.runTaskLater(game.plugin, Runnable {
            end()
        }, 60 * 20)
    }

    fun validateAnswer(player: Player, answer: Int) {
        val question = questions[player.uniqueId]!!

        if (question.answer == answer) {
            player.sendMessage(Component.text("Correct!", NamedTextColor.GREEN))
            // play orb pickup sound
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)

            correctAnswers[player.uniqueId] = correctAnswers[player.uniqueId]!! + 1

            val newQuestion = generateMathQuestion()
            questions[player.uniqueId] = newQuestion
            player.sendMessage(Component.text("Question: ${newQuestion.question} = ?", NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text("Incorrect!", NamedTextColor.RED))
            // play angry villager sound
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    override fun end() {
        // sort the correct answers by the number of correct answers
        val sortedCorrectAnswers = correctAnswers.toList().sortedByDescending { it.second }

        _game.players().forEach { player ->
            player.sendMessage(
                Component.text(
                    "Correct answers: ${correctAnswers[player.uniqueId]}",
                    NamedTextColor.GREEN
                )
            )

            // get the position of the player in the list
            val position = sortedCorrectAnswers.indexOfFirst { it.first == player.uniqueId }

            // scoring system: 1 point for each correct answer, +5 for top 1, +3 for top 3, +1 for top 10
            val score = correctAnswers[player.uniqueId]!! + when (position) {
                0 -> 5
                1, 2 -> 3
                in 3..9 -> 1
                else -> 0
            }

            _game.playerData(player.uniqueId)!!.score += score
            player.sendMessage(Component.text("You scored $score points!", NamedTextColor.GREEN))
        }

        super.end()
    }

    override
    val name: Component
        get() = Component.text("Math", NamedTextColor.AQUA)

    override
    val description: Component
        get() = Component.text(
            "You have a minute to solve various math problems. Simply type the answer in chat!",
            NamedTextColor.AQUA
        )
}