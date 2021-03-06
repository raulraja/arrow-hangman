package io.github.lordraydenmk.part2

import arrow.Kind
import arrow.core.toOption
import arrow.effects.IO
import arrow.effects.ObservableK
import arrow.effects.SingleK
import arrow.effects.fix
import arrow.effects.instances.io.monadDefer.monadDefer
import arrow.effects.observablek.monadDefer.monadDefer
import arrow.effects.singlek.monadDefer.monadDefer
import arrow.effects.typeclasses.MonadDefer
import arrow.typeclasses.binding
import java.io.IOException
import kotlin.random.Random
import kotlin.streams.toList

fun <F> MonadDefer<F>.putStrLn(line: String): Kind<F, Unit> = invoke {
    println(line)
}

fun <F> MonadDefer<F>.getStrLn(): Kind<F, String> = defer {
    readLine().toOption()
            .fold(
                    { raiseError<String>(IOException("Failed to read input!")) },
                    { just(it) }
            )
}

class Hangman<F>(private val M: MonadDefer<F>): MonadDefer<F> by M {

    data class State(val name: String, val guesses: Set<Char> = emptySet(), val word: String) {

        val failures: Int = (guesses.toSet().minus(word.toSet())).size

        val playerLost: Boolean = failures > 8

        val playerWon: Boolean = (word.toSet().minus(guesses)).isEmpty()
    }

    val dictionary: List<String> by lazy {
        javaClass.classLoader.getResource("words.txt")
                .openStream()
                .bufferedReader()
                .lines()
                .toList()
    }

    val hangman: Kind<F, Unit> = binding {
        putStrLn("Welcome to purely functional hangman").bind()
        val name = getName.bind()
        putStrLn("Welcome $name. Let's begin!").bind()
        val word = chooseWord.bind()
        val state = State(name, word = word)
        renderState(state).bind()
        gameLoop(state).bind()
        Unit
    }

    fun gameLoop(state: State): Kind<F, State> = binding {
        val guess = getChoice().bind()
        val updatedState = state.copy(guesses = state.guesses.plus(guess))
        renderState(updatedState).bind()
        val loop = when {
            updatedState.playerWon -> putStrLn("Congratulations ${state.name} you won the game").map { false }
            updatedState.playerLost -> putStrLn("Sorry ${state.name} you lost the game. The word was ${state.word}").map { false }
            updatedState.word.contains(guess) -> putStrLn("You guessed correctly!").map { true }
            else -> putStrLn("That's wrong, but keep trying").map { true }
        }.bind()
        if (loop) gameLoop(updatedState).bind() else updatedState
    }

    val getName: Kind<F, String> = binding {
        putStrLn("What is your name: ").bind()
        getStrLn().bind()
    }

    fun getChoice(): Kind<F, Char> = binding {
        putStrLn("Please enter a letter").bind()
        val line = getStrLn().bind()
        val char = line.toLowerCase().first().toOption().fold(
                {
                    putStrLn("Please enter a letter")
                            .flatMap { getChoice() }
                },
                { char ->
                    invoke { char }
                }
        ).bind()
        char
    }

    fun nextInt(max: Int): Kind<F, Int> = invoke { Random.nextInt(max) }

    val chooseWord: Kind<F, String> = binding {
        val rand = nextInt(dictionary.size).bind()
        dictionary[rand]
    }

    fun renderState(state: State): Kind<F, Unit> {
        val word = state.word.toList().map { if (state.guesses.contains(it)) " $it " else "   " }
                .joinToString("")
        val line = state.word.map { " - " }.joinToString("")
        val guesses = "Guesses: ${state.guesses.toList().sorted().joinToString("")}"
        val text = "$word\n$line\n\n$guesses\n"
        return putStrLn(text)
    }
}

fun main() {
    Hangman(SingleK.monadDefer()).hangman.fix()
            .single
            .subscribe()
}