// Miroir Google Cloud de Maven Central : Maven Central (repo.maven.apache.org)
// renvoie des 429 dans cet environnement. Le miroir sert les mêmes artefacts.
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        mavenCentral()
    }
}

rootProject.name = "stopaddict"

// Modules publiés progressivement. Seul core:model est pur JVM et testable
// sans SDK Android ; les modules Android s'ajoutent aux étapes suivantes.
include(":core:model")
include(":core:crypto")
include(":core:network")
include(":core:data")
include(":core:designsystem")
include(":app-user")
include(":app-admin")
include(":protection:rules")
