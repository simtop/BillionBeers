plugins { id("billionbeers.android.library") }

android { namespace = "com.simtop.beer_network.fixtures" }

dependencies { api(project(":beer_network")) }
