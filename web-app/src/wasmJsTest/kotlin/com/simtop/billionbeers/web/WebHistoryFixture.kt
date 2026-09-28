package com.simtop.billionbeers.web

import com.simtop.navigation.contract.PortableRoute

/**
 * Test-only model of the Web host's hash-based history adapter. It deliberately models URL history,
 * not a production navigation stack, so target-specific behavior can be compared without a browser
 * or Compose host.
 */
internal class WebHistoryFixture(initialHash: String) {
  constructor(initialRoute: PortableRoute) : this(initialRoute.toWebHash())

  private val hashes = mutableListOf(initialHash)
  private var cursor = 0

  val currentHash: String
    get() = hashes[cursor]

  val historySize: Int
    get() = hashes.size

  fun push(route: PortableRoute) {
    val hash = route.toWebHash()
    if (hash == currentHash) return
    hashes.subList(cursor + 1, hashes.size).clear()
    hashes += hash
    cursor++
  }

  fun replace(route: PortableRoute) {
    hashes[cursor] = route.toWebHash()
  }

  fun back(): Boolean = moveTo(cursor - 1)

  fun forward(): Boolean = moveTo(cursor + 1)

  fun reloadRoot(): WebRouteDestination? = parseWebHash(currentHash)

  private fun moveTo(nextCursor: Int): Boolean {
    if (nextCursor !in hashes.indices) return false
    cursor = nextCursor
    return true
  }
}
