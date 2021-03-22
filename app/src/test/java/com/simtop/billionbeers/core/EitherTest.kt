package com.simtop.billionbeers.core

import com.simtop.core.core.Either
import com.simtop.core.core.mapLeft
import com.simtop.core.core.mapRight
import org.junit.Test

class EitherTest {

    @Test
    fun `Either Right should return correct type`() {
        val result: com.simtop.core.core.Either.Right<String> = com.simtop.core.core.Either.Right("asdf")

        result shouldBeInstanceOf com.simtop.core.core.Either::class.java
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

        val either: com.simtop.core.core.Either.Right<String> = com.simtop.core.core.Either.Right("aaaaaaaa")

        either
            .mapRight {
                it shouldBeEqualTo "aaaaaaaa"
            }
    }

    @Test
    fun `test onError extension function`() {

        val either: com.simtop.core.core.Either.Left<String> = com.simtop.core.core.Either.Left("bbbbbbbb")

        either
            .mapLeft {
                it shouldBeEqualTo "bbbbbbbb"
            }
    }
}