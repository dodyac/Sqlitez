buildscript {
    repositories {
        google()
        jcenter()
    }
    dependencies {
        classpath(Config.Dependencies.androidPlugin)
        classpath(Config.Dependencies.kotlinPlugin)
    }
}

allprojects {
    repositories {
        google()
        jcenter()
        maven { url = uri(Config.Repositories.gradleMaven)  }
    }
}

tasks.register("clean",Delete::class){ delete(rootProject.buildDir) }