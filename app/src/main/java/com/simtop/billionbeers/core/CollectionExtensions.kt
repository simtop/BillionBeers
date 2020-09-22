package com.simtop.billionbeers.core

infix fun <T: Any> Collection<T>.intersects(other: Collection<T>) = any(other::contains)