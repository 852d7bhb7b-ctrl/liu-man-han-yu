pluginManagement {
    repositories {
        maven("https://dl.google.com/dl/android/maven2/")
        maven("https://maven.google.com")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://dl.google.com/dl/android/maven2/")
        google()
        mavenCentral()
    }
}

rootProject.name = "流氓汉语"
include(":app")
