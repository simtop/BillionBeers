plugins {
  id("billionbeers.kmp.feature")
}

kotlin {
  sourceSets { commonMain.dependencies { implementation(project(":beerdomain:impl")) } }
}
