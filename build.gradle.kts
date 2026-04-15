plugins {
    id("com.android.library") version "8.3.1" apply false
    kotlin("android") version "1.9.21" apply false
}

group = "com.niyarin"
version = "0.0.2"

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
