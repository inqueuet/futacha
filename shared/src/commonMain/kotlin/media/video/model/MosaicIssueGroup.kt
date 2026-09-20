package com.valoser.futacha.shared.media.video.model

internal enum class MosaicIssueFilter { ATTENTION, NO_CANDIDATE, ALL }

internal data class MosaicIssueGroup(val reason: String, val intervals: List<MosaicReviewInterval>) {
    val noCandidate: Boolean get() = reason.startsWith("候補なし")

    companion object {
        fun from(issues: List<MosaicAnalysisIssue>): List<MosaicIssueGroup> = issues.groupBy { it.reason }.map { (reason, entries) ->
            val merged = mutableListOf<MosaicReviewInterval>()
            for (issue in entries.sortedBy { it.timeUs }) {
                val last = merged.lastOrNull()
                if (last != null && issue.timeUs <= last.endUs) merged[merged.lastIndex] = last.copy(endUs = maxOf(last.endUs, issue.endUs))
                else merged += MosaicReviewInterval(issue.timeUs, issue.endUs)
            }
            MosaicIssueGroup(reason, merged)
        }.sortedWith(compareBy<MosaicIssueGroup> { it.noCandidate }.thenBy { it.intervals.first().startUs })
    }
}
