package com.simtop.core.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EitherTest {

  @Test
  fun `mapRight transforms right values and preserves left values`() {
    val right: Either<String, String> = Either.Right("aaaaaaaa")
    val left: Either<String, String> = Either.Left("error")

    var rightCallbackCalled = false
    val mappedRight =
      right mapRight
        {
          rightCallbackCalled = true
          it.length
        }
    var leftCallbackCalled = false
    val mappedLeft =
      left mapRight
        {
          leftCallbackCalled = true
          it.length
        }

    assertEquals(Either.Right(8), mappedRight)
    assertEquals(left, mappedLeft)
    assertTrue(rightCallbackCalled)
    assertFalse(leftCallbackCalled)
  }

  @Test
  fun `mapLeft transforms left values and preserves right values`() {
    val left: Either<String, String> = Either.Left("error")
    val right: Either<String, String> = Either.Right("value")

    var leftCallbackCalled = false
    val mappedLeft =
      left mapLeft
        {
          leftCallbackCalled = true
          it.length
        }
    var rightCallbackCalled = false
    val mappedRight =
      right mapLeft
        {
          rightCallbackCalled = true
          it.length
        }

    assertEquals(Either.Left(5), mappedLeft)
    assertEquals(right, mappedRight)
    assertTrue(leftCallbackCalled)
    assertFalse(rightCallbackCalled)
  }

  @Test
  fun `either dispatches exactly one branch`() {
    val right: Either<String, String> = Either.Right("value")
    var leftCallbackCalled = false
    var rightCallbackCalled = false

    val result =
      right.either(
        {
          leftCallbackCalled = true
          "left"
        },
        {
          rightCallbackCalled = true
          it.uppercase()
        },
      )

    assertEquals("VALUE", result)
    assertFalse(leftCallbackCalled)
    assertTrue(rightCallbackCalled)
  }
}
