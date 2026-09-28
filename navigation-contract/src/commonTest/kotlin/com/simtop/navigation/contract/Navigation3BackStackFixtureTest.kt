package com.simtop.navigation.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Navigation3BackStackFixtureTest {

  @Test
  fun `push and pop preserve entry order`() {
    val fixture = Navigation3BackStackFixture(PortableRoute.BeersList)
    val detail = fixture.push(PortableRoute.BeerBrowse)

    assertEquals(
      listOf(PortableRoute.BeersList, PortableRoute.BeerBrowse),
      fixture.entries.map { it.route },
    )
    assertTrue(fixture.pop())
    assertEquals(PortableRoute.BeersList, fixture.entries.single().route)
    assertNotEquals(detail.id, fixture.entries.single().id)
  }

  @Test
  fun `repeated routes receive distinct entry identities`() {
    val fixture = Navigation3BackStackFixture(PortableRoute.BeersList)
    val first = fixture.push(PortableRoute.BeerBrowse)
    val second = fixture.push(PortableRoute.BeerBrowse)

    assertEquals(first.route, second.route)
    assertNotEquals(first.id, second.id)
    assertEquals(listOf(first.id, second.id), fixture.entries.drop(1).map { it.id })
  }

  @Test
  fun `root cannot be popped`() {
    val fixture = Navigation3BackStackFixture(PortableRoute.BeersList)

    assertFalse(fixture.pop())
    assertEquals(1, fixture.entries.size)
    assertEquals(PortableRoute.BeersList, fixture.entries.single().route)
  }

  @Test
  fun `root replacement discards covered entries`() {
    val fixture = Navigation3BackStackFixture(PortableRoute.BeersList)
    fixture.push(PortableRoute.BeerBrowse)

    fixture.replaceRoot(PortableRoute.Favorites)

    assertEquals(listOf(PortableRoute.Favorites), fixture.entries.map { it.route })
    assertFalse(fixture.pop())
  }

  @Test
  fun `route stack round trips with entry identity`() {
    val fixture = Navigation3BackStackFixture(PortableRoute.BeersList)
    fixture.push(PortableRoute.BeerBrowseSelection(BrowseCategory.Style("ipa", "IPA")))
    fixture.push(PortableRoute.BeersSearch)

    val restored = Navigation3BackStackFixture.decode(fixture.encode())

    assertEquals(fixture.entries, restored.entries)
    val newEntry = restored.push(PortableRoute.Favorites)
    assertEquals(fixture.entries.maxOf { it.id } + 1, newEntry.id)
  }

  @Test
  fun `empty serialized stack is rejected`() {
    assertFailsWith<IllegalArgumentException> {
      Navigation3BackStackFixture.decode("{\"entries\":[]}")
    }
  }

  @Test
  fun `malformed serialized stack is rejected`() {
    assertFailsWith<Exception> {
      Navigation3BackStackFixture.decode("not-json")
    }
  }
}
