package com.simtop.billionbeers.core

sealed class Either<out L, out R> {
    data class Left<T>(val value: T) : Either<T, Nothing>()
    data class Right<T>(val value: T) : Either<Nothing, T>()

    val isRight: Boolean get() = this is Right
    val isLeft: Boolean get() = this is Left

    inline fun <T> either(fnL: (L) -> T, fnR: (R) -> T): T =
        when (this) {
            is Left -> fnL(value)
            is Right -> fnR(value)
        }
}

inline fun <T> either(f: () -> T): Either<Exception, T> =
    try {
        Either.Right(f())
    } catch (e: Exception) {
        Either.Left(e)
    }

inline infix fun <A, B, C> Either<A, B>.mapRight(f: (B) -> C): Either<A, C> = when(this) {
    is Either.Left -> this
    is Either.Right -> Either.Right(f(this.value))
}

inline infix fun <A, B, C> Either<A, B>.flatMap(f: (B) -> Either<A, C>): Either<A, C> = when(this) {
    is Either.Left -> this
    is Either.Right -> f(value)
}

inline infix fun <A, B, C> Either<A, C>.mapLeft(f: (A) -> B): Either<B, C> = when(this) {
    is Either.Left -> Either.Left(f(value))
    is Either.Right -> this
}

inline fun <A, B, C> Either<A, B>.fold(left: (A) -> C, right: (B) -> C): C = when(this) {
    is Either.Left -> left(this.value)
    is Either.Right -> right(this.value)
}

fun <T> T.asLeft(): Either.Left<T> = Either.Left(this)
fun <T> T.asRight(): Either.Right<T> = Either.Right(this)
