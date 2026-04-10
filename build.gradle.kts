plugins {
    id("com.android.library") version "8.3.1" apply false
    kotlin("android") version "1.9.21" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

