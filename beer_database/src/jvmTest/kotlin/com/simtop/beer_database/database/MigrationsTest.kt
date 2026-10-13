package com.simtop.beer_database.database

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationsTest {

  @Test
  fun `registry contains every supported migration in upgrade order`() {
    assertEquals(
      listOf(1 to 2, 2 to 3, 3 to 4, 4 to 5),
      ALL_MIGRATIONS.map { it.startVersion to it.endVersion },
    )
  }
}
