package com.simtop.billionbeers.core

import com.simtop.core.core.Either
import com.simtop.core.core.mapLeft
import com.simtop.core.core.mapRight
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import strikt.api.expect
import strikt.assertions.isEqualTo
import strikt.assertions.isSameInstanceAs

class EitherTest {

    @Test
    fun `Either Right should return correct type`() {
        val result: Either.Right<String> = Either.Right("asdf")

        expect {
            that(result) {
                get { this.javaClass }.isSameInstanceAs(Either.Right::class.java)
                get { isRight }.isEqualTo(true)
                get { isLeft }.isEqualTo(false)
            }
            result.either(
                {
                },
                { right ->
                    that(right) {
                        get { this.javaClass }.isSameInstanceAs(String::class.java)
                        get { this }.isEqualTo("asdf")
                    }
                }
            )
        }
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