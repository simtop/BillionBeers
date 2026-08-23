plugins { id("billionbeers.android.library") }

android { namespace = "com.simtop.beer_network.fixtures" }

dependencies { api(this.project(":beer_network")) }
