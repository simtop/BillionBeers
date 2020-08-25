package com.simtop.billionbeers.core

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.Test

//TODO test everything
class EitherTest {

    @Test
    fun `Either Right should return correct type`() {
        val result: Either.Right<String> = Either.Right("asdf")

        result shouldBeInstanceOf Either::class.java
        result.isRight shouldBeEqualTo true
        result.isLeft shouldBeEqualTo false

        result.either(
            {
            },
            { right ->
                right shouldBeInstanceOf String::class.java
                right shouldBeEqualTo "asdf"
            }
        )
    }

    @Test
    fun `test onSuccess extension function`() {

        val either: Either.Right<String> = Either.Right("aaaaaaaa")

        either
            .mapRight {
                it shouldBeEqualTo "aaaaaaaa"
            }
    }

    @Test
    fun `test onError extension function`() {

        val either: Either.Left<String> = Either.Left("bbbbbbbb")

        either
            .mapLeft {
                it shouldBeEqualTo "bbbbbbbb"
            }
    }
}