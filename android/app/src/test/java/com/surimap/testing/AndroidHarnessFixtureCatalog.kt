package com.surimap.testing

import org.json.JSONArray
import org.json.JSONObject

data class AndroidHarnessFixtureEntry(
    val fixtureId: String,
    val ownerPath: String,
    val usesScenarios: Set<String>,
    val usesLanes: Set<String>
)

class AndroidHarnessFixtureCatalog private constructor(
    private val entries: List<AndroidHarnessFixtureEntry>
) {
    fun requireFixture(fixtureId: String): AndroidHarnessFixtureEntry =
        entries.firstOrNull { it.fixtureId == fixtureId }
            ?: error("Fixture $fixtureId is not registered in common-fixtures.json catalogIndex")

    companion object {
        fun load(resourceName: String = "common-fixtures.json"): AndroidHarnessFixtureCatalog {
            val stream = AndroidHarnessFixtureCatalog::class.java.classLoader
                ?.getResourceAsStream(resourceName)
                ?: error("$resourceName is not available as a test resource")
            val json = stream.bufferedReader().use { it.readText() }
            return parse(json)
        }

        internal fun parse(json: String): AndroidHarnessFixtureCatalog {
            val catalogIndex = JSONObject(json).getJSONArray("catalogIndex")
            val entries = (0 until catalogIndex.length()).map { index ->
                val entry = catalogIndex.getJSONObject(index)
                AndroidHarnessFixtureEntry(
                    fixtureId = entry.getString("fixtureId"),
                    ownerPath = entry.getString("ownerPath"),
                    usesScenarios = entry.getJSONArray("usesScenarios").toStringSet(),
                    usesLanes = entry.getJSONArray("usesLanes").toStringSet()
                )
            }

            require(entries.isNotEmpty()) {
                "common-fixtures.json catalogIndex did not contain any parseable fixture entries"
            }
            return AndroidHarnessFixtureCatalog(entries)
        }

        private fun JSONArray.toStringSet(): Set<String> =
            (0 until length()).map { index -> getString(index) }.toSet()
    }
}
