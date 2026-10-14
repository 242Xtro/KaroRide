import java.util.Properties

pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
val sdkCredentials = Properties().apply {
    val source = file("secrets/sdk.properties")
    if (source.isFile) source.inputStream().use { load(it) }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        exclusiveContent {
            forRepository {
                maven {
                    name = "Insta360CameraSdk"
                    url = uri("https://androidsdk.insta360.com/repository/maven-public/")
                    credentials {
                        username = sdkCredentials.getProperty("username", "")
                        password = sdkCredentials.getProperty("password", "")
                    }
                }
            }
            filter {
                includeGroupByRegex("com\\.arashivision.*")
                includeGroupByRegex("com\\.insta360.*")
            }
        }
    }
}
rootProject.name = "KaroRide"
include(":core", ":app", ":karoo-ext")
project(":karoo-ext").projectDir = file("vendor/karoo-ext")

