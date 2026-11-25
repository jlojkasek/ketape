pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral(); mavenLocal()

    }
}
rootProject.name = "KarooStackedFields"
include(":app")

include(":karoo-ext")
project(":karoo-ext").projectDir = file("vendor/karoo-ext")
