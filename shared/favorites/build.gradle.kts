plugins {
  id("billionbeers.kmp.feature")
}

kotlin {
  sourceSets { jvmTest.dependencies { implementation(project(":testing-utils")) } }
}
