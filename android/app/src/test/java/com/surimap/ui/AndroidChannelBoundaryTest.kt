package com.surimap.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidChannelBoundaryTest {

    @Test
    fun androidMainSourceDoesNotAddWebOnlyCommands() {
        val violations =
            mainSourceFiles().flatMap { file ->
                val source = file.readText()
                forbiddenWebCommandPatterns.mapNotNull { guard ->
                    if (guard.regex.containsMatchIn(source)) {
                        "${file.relativeTo(projectDir).path}: ${guard.reason}"
                    } else {
                        null
                    }
                }
            }

        assertTrue(violations.joinToString(separator = "\n"), violations.isEmpty())
    }

    @Test
    fun androidMainSourceDoesNotExposeAutomaticJudgementCopy() {
        val violations =
            mainSourceFiles().flatMap { file ->
                val source = file.readText()
                forbiddenAutomaticJudgementTerms.mapNotNull { term ->
                    if (source.contains(term)) {
                        "${file.relativeTo(projectDir).path}: forbidden term `$term`"
                    } else {
                        null
                    }
                }
            }

        assertTrue(violations.joinToString(separator = "\n"), violations.isEmpty())
    }

    private fun mainSourceFiles(): List<File> =
        File(projectDir, "src/main/java/com/surimap")
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .toList()

    private data class SourceGuard(
        val regex: Regex,
        val reason: String
    )

    private companion object {
        val projectDir: File = File(".")

        val forbiddenWebCommandPatterns =
            listOf(
                SourceGuard(
                    regex = writeTo("/api/incidents/import"),
                    reason = "incident import is a Web command"
                ),
                SourceGuard(
                    regex = writeTo("""/api/incidents/[^"]+/close"""),
                    reason = "incident close is a Web command"
                ),
                SourceGuard(
                    regex = writeTo("/api/search-areas"),
                    reason = "search area create/update/split/assignment is a Web command"
                ),
                SourceGuard(
                    regex = writeTo("/api/operational-periods"),
                    reason = "operational period creation is a Web command"
                ),
                SourceGuard(
                    regex = Regex("""(?i)(vehicle|walk|mode).{0,40}correction"""),
                    reason = "vehicle/walk mode correction command is Web-only"
                ),
                SourceGuard(
                    regex = Regex("""차량.{0,20}보정|도보.{0,20}보정|보정.{0,20}command"""),
                    reason = "vehicle/walk correction command is Web-only"
                )
            )

        val forbiddenAutomaticJudgementTerms =
            listOf(
                "누락 확정",
                "다음 구역 추천",
                "위험도",
                "위험도 높음",
                "자동 판단"
            )

        private fun writeTo(pathPattern: String): Regex =
            Regex(
                pattern = """method\s*=\s*"(POST|PATCH|DELETE)"[\s\S]{0,260}(path|endpoint)\s*=\s*"${pathPattern}""",
                options = setOf(RegexOption.IGNORE_CASE)
            )
    }
}
