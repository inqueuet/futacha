// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

val validateQualityContracts by tasks.registering {
    group = "verification"
    description = "Validates the user-visible feature and closed-issue regression inventories."

    val featureContracts = layout.projectDirectory.file("quality/feature-contracts.tsv")
    val behaviorContracts = layout.projectDirectory.file("quality/behavior-contracts.tsv")
    val closedIssueContracts = layout.projectDirectory.file("quality/closed-issue-contracts.tsv")
    val closedIssueAssertions = layout.projectDirectory.file("quality/closed-issue-assertions.tsv")
    val referenceComponentAudit = layout.projectDirectory.file("quality/reference-component-audit.tsv")
    val referenceMenuAudit = layout.projectDirectory.file("quality/reference-menu-audit.tsv")
    val referenceManifestAudit = layout.projectDirectory.file("quality/reference-manifest-audit.tsv")
    val referenceThemeAudit = layout.projectDirectory.file("quality/reference-theme-audit.tsv")
    val referenceLayoutAudit = layout.projectDirectory.file("quality/reference-layout-audit.tsv")
    val referenceResourceAudit = layout.projectDirectory.file("quality/reference-resource-audit.tsv")
    val releaseDeviceMatrix = layout.projectDirectory.file("quality/release-device-matrix.tsv")
    val deviceRunbook = layout.projectDirectory.file("docs/device-regression-runbook.md")
    val privateDeviceEvidence = "PRIVATE_DEVICE_EVIDENCE"
    inputs.files(
        featureContracts,
        behaviorContracts,
        closedIssueContracts,
        closedIssueAssertions,
        referenceComponentAudit,
        referenceMenuAudit,
        referenceManifestAudit,
        referenceThemeAudit,
        referenceLayoutAudit,
        referenceResourceAudit,
        releaseDeviceMatrix
    )
    if (deviceRunbook.asFile.isFile) inputs.file(deviceRunbook)

    doLast {
        fun readRows(file: File, expectedColumns: Int): List<List<String>> {
            require(file.isFile) { "Missing quality contract file: ${file.path}" }
            val rows = file.readLines()
                .filter { it.isNotBlank() && !it.startsWith("# ") }
                .map { line -> line.split('\t') }
            require(rows.isNotEmpty()) { "Quality contract file is empty: ${file.path}" }
            rows.forEachIndexed { index, columns ->
                require(columns.size == expectedColumns) {
                    "${file.path}:${index + 1} expected $expectedColumns tab-separated columns, found ${columns.size}"
                }
                require(columns.none { it.isBlank() }) {
                    "${file.path}:${index + 1} contains a blank contract field"
                }
            }
            return rows.drop(1)
        }

        fun requireEvidenceExists(evidence: String, owner: String) {
            evidence.split(';').forEach { relativePath ->
                if (relativePath == privateDeviceEvidence) return@forEach
                val evidenceFile = rootProject.file(relativePath)
                require(evidenceFile.isFile) {
                    "$owner references missing evidence: $relativePath"
                }
            }
        }

        fun requireSymbolEvidenceExists(evidence: String, owner: String, level: String) {
            evidence.split(';').forEach { reference ->
                val separator = reference.lastIndexOf('#')
                require(separator > 0 && separator < reference.lastIndex) {
                    "$owner must reference an exact evidence symbol as path#symbol: $reference"
                }
                val relativePath = reference.substring(0, separator)
                val symbol = reference.substring(separator + 1)
                if (relativePath == privateDeviceEvidence) {
                    require(level == "DEVICE" && (symbol.startsWith("DEVICE-") || symbol.startsWith("ISSUE-"))) {
                        "$owner private device evidence must reference a DEVICE-* or ISSUE-* scenario"
                    }
                    if (deviceRunbook.asFile.isFile) {
                        require(symbol in deviceRunbook.asFile.readText()) {
                            "$owner references missing private device evidence symbol $symbol"
                        }
                    }
                    return@forEach
                }
                val evidenceFile = rootProject.file(relativePath)
                require(evidenceFile.isFile) {
                    "$owner references missing evidence: $relativePath"
                }
                require(symbol in evidenceFile.readText()) {
                    "$owner references missing evidence symbol $symbol in $relativePath"
                }
                if (level == "DEVICE") {
                    require("testReal" in symbol) {
                        "$owner DEVICE evidence must be private device evidence or an opt-in real-device test"
                    }
                } else {
                    require("Test" in relativePath && "/src/" in relativePath || relativePath.endsWith("Tests.swift")) {
                        "$owner $level evidence is not an executable test source: $relativePath"
                    }
                }
            }
        }

        fun expandMode(scope: String): Set<String> = when (scope) {
            "BOTH" -> setOf("FUTACHA", "TOSHIAKI_COMPAT")
            else -> setOf(scope)
        }

        fun expandPlatform(scope: String): Set<String> = when (scope) {
            "BOTH" -> setOf("ANDROID", "IOS")
            else -> setOf(scope)
        }

        val featureRows = readRows(featureContracts.asFile, expectedColumns = 6)
        val featureIds = featureRows.map { it[0] }
        require(featureIds.size == featureIds.toSet().size) { "Duplicate feature contract id" }
        val requiredFeatureIds = setOf(
            "startup-profile", "board-management", "catalog", "thread-loading", "thread-history",
            "posting", "media", "gallery-viewer", "saving", "tts", "theme", "settings",
            "background-refresh", "network-parser", "platform-navigation", "accessibility-layout"
        )
        require(featureIds.containsAll(requiredFeatureIds)) {
            "Feature inventory is missing mandatory groups: ${requiredFeatureIds - featureIds.toSet()}"
        }
        featureRows.forEach { row ->
            require(row[2] in setOf("BOTH", "FUTACHA", "TOSHIAKI_COMPAT")) {
                "${row[0]} has invalid mode scope ${row[2]}"
            }
            require(row[3] in setOf("BOTH", "ANDROID", "IOS")) {
                "${row[0]} has invalid platform scope ${row[3]}"
            }
            require(row[4] in setOf("AUTOMATED", "PARTIAL", "DEVICE")) {
                "${row[0]} has invalid coverage ${row[4]}"
            }
            requireEvidenceExists(row[5], row[0])
            if (row[4] != "DEVICE") {
                require(row[5].split(';').any { "src/" in it && "Test" in it }) {
                    "${row[0]} claims ${row[4]} coverage without automated test evidence"
                }
            }
        }

        val featureById = featureRows.associateBy { it[0] }
        val behaviorRows = readRows(behaviorContracts.asFile, expectedColumns = 7)
        val behaviorIds = behaviorRows.map { it[0] }
        require(behaviorIds.size == behaviorIds.toSet().size) { "Duplicate behavior contract id" }
        behaviorRows.forEach { row ->
            val behaviorId = row[0]
            val feature = requireNotNull(featureById[row[1]]) {
                "$behaviorId references unknown feature ${row[1]}"
            }
            require(row[3] in setOf("BOTH", "FUTACHA", "TOSHIAKI_COMPAT")) {
                "$behaviorId has invalid mode scope ${row[3]}"
            }
            require(row[4] in setOf("BOTH", "ANDROID", "IOS")) {
                "$behaviorId has invalid platform scope ${row[4]}"
            }
            require(row[5] in setOf("UNIT", "INTEGRATION", "ANDROID_UI", "IOS_UI", "DEVICE")) {
                "$behaviorId has invalid verification level ${row[5]}"
            }
            require(expandMode(row[3]).all { it in expandMode(feature[2]) }) {
                "$behaviorId mode scope ${row[3]} exceeds ${feature[0]} scope ${feature[2]}"
            }
            require(expandPlatform(row[4]).all { it in expandPlatform(feature[3]) }) {
                "$behaviorId platform scope ${row[4]} exceeds ${feature[0]} scope ${feature[3]}"
            }
            if (row[5] == "ANDROID_UI") require(row[4] == "ANDROID") {
                "$behaviorId Android UI evidence cannot claim ${row[4]}"
            }
            if (row[5] == "IOS_UI") require(row[4] == "IOS") {
                "$behaviorId iOS UI evidence cannot claim ${row[4]}"
            }
            requireSymbolEvidenceExists(row[6], behaviorId, row[5])
        }
        val behaviorsByFeature = behaviorRows.groupBy { it[1] }
        featureRows.forEach { feature ->
            val behaviors = behaviorsByFeature[feature[0]].orEmpty()
            val capabilityRequirements = feature[1]
                .split('・', '／')
                .map(String::trim)
                .filter(String::isNotEmpty)
            require(capabilityRequirements.isNotEmpty()) { "${feature[0]} has no capability requirements" }
            capabilityRequirements.forEach { requirement ->
                require(behaviors.any { requirement in it[2] }) {
                    "${feature[0]} capability '$requirement' has no directly named behavior contract"
                }
            }
            require(behaviors.any { it[5] != "DEVICE" }) {
                "${feature[0]} has no executable automated behavior contract"
            }
            val coveredModes = behaviors.flatMap { expandMode(it[3]) }.toSet()
            val coveredPlatforms = behaviors.flatMap { expandPlatform(it[4]) }.toSet()
            require(coveredModes.containsAll(expandMode(feature[2]))) {
                "${feature[0]} behavior contracts miss modes ${expandMode(feature[2]) - coveredModes}"
            }
            require(coveredPlatforms.containsAll(expandPlatform(feature[3]))) {
                "${feature[0]} behavior contracts miss platforms ${expandPlatform(feature[3]) - coveredPlatforms}"
            }
        }

        val issueRows = readRows(closedIssueContracts.asFile, expectedColumns = 7)
        val issueNumbers = issueRows.map { row ->
            require(row[0].startsWith("#")) { "Invalid issue id ${row[0]}" }
            row[0].removePrefix("#").toInt()
        }
        require(issueNumbers == (9..69).toList()) {
            "Closed-issue inventory must contain every issue from #9 through #69 in order; found $issueNumbers"
        }
        issueRows.forEach { row ->
            val issueNumber = row[0].removePrefix("#")
            require(row[1] == "https://github.com/inqueuet/futacha/issues/$issueNumber") {
                "${row[0]} has an invalid source URL ${row[1]}"
            }
            require(row[3] in setOf("BOTH", "FUTACHA", "TOSHIAKI_COMPAT")) {
                "${row[0]} has invalid mode scope ${row[3]}"
            }
            require(row[4] in setOf("BOTH", "ANDROID", "IOS")) {
                "${row[0]} has invalid platform scope ${row[4]}"
            }
            require(row[5] in setOf("AUTOMATED", "PARTIAL", "DEVICE")) {
                "${row[0]} has invalid coverage ${row[5]}"
            }
            requireEvidenceExists(row[6], row[0])
            if (row[5] != "DEVICE") {
                require(row[6].split(';').any { "src/" in it && "Test" in it }) {
                    "${row[0]} claims ${row[5]} coverage without automated test evidence"
                }
            }
        }

        val issueById = issueRows.associateBy { it[0] }
        val issueAssertionRows = readRows(closedIssueAssertions.asFile, expectedColumns = 3)
        val assertionIssueIds = issueAssertionRows.map { it[0] }
        require(assertionIssueIds == (9..69).map { "#$it" }) {
            "Closed-issue assertions must contain exactly one ordered assertion for every issue #9 through #69"
        }
        require(issueAssertionRows.map { it[1] }.toSet().size == issueAssertionRows.size) {
            "Duplicate closed-issue assertion id"
        }
        issueAssertionRows.forEach { row ->
            val issueId = row[0]
            val issue = requireNotNull(issueById[issueId]) { "$issueId assertion has no issue contract" }
            val evidencePaths = row[2].split(';').map { it.substringBeforeLast('#') }
            val declaredPaths = issue[6].split(';').toSet()
            require(evidencePaths.all { it in declaredPaths }) {
                "$issueId exact assertion evidence must also be declared by its issue contract"
            }
            val level = if (evidencePaths.all { it == privateDeviceEvidence }) "DEVICE" else "ISSUE"
            requireSymbolEvidenceExists(row[2], "$issueId/${row[1]}", level)
            if (issue[5] == "DEVICE") {
                require(level == "DEVICE") { "$issueId is DEVICE-only but its exact assertion is not a device scenario" }
            } else {
                require(level != "DEVICE") { "$issueId claims automated coverage without an exact test assertion" }
            }
        }

        val referenceRows = readRows(referenceComponentAudit.asFile, expectedColumns = 6)
        val referenceComponents = referenceRows.map { it[0] }
        require(referenceComponents == referenceComponents.sorted()) {
            "Reference component audit must stay sorted by component name"
        }
        require(referenceComponents.size == referenceComponents.toSet().size) {
            "Duplicate reference component audit entry"
        }
        require(referenceRows.size == 127) {
            "The old.apk/1.apk app-defined component inventory must contain 127 components; found ${referenceRows.size}"
        }
        val expectedReferenceKinds = mapOf(
            "ACTIVITY" to 33,
            "APPLICATION" to 1,
            "DIALOG" to 58,
            "FRAGMENT" to 29,
            "RECEIVER" to 1,
            "SERVICE" to 5
        )
        require(referenceRows.groupingBy { it[1] }.eachCount() == expectedReferenceKinds) {
            "Reference component kind totals must remain $expectedReferenceKinds"
        }
        require(referenceRows.count { it[2] == "BOTH" } == 116 &&
            referenceRows.count { it[2] == "OLD_ONLY" } == 3 &&
            referenceRows.count { it[2] == "FINAL_ONLY" } == 8
        ) {
            "Reference component APK-presence totals must remain BOTH=116, OLD_ONLY=3, FINAL_ONLY=8"
        }
        referenceRows.forEach { row ->
            val component = row[0]
            require(row[1] in expectedReferenceKinds.keys) {
                "$component has invalid component kind ${row[1]}"
            }
            require(row[2] in setOf("BOTH", "OLD_ONLY", "FINAL_ONLY")) {
                "$component has invalid APK presence ${row[2]}"
            }
            require(row[4] in setOf("PENDING", "MATCH", "FIXED", "SUPERSEDED")) {
                "$component has invalid audit status ${row[4]}"
            }
            if (row[4] == "PENDING") {
                require(row[5] == "-") { "$component is pending but already claims evidence" }
            } else {
                require(row[5] != "-") { "$component ${row[4]} has no direct evidence" }
                requireSymbolEvidenceExists(row[5], component, "INTEGRATION")
            }
        }
        require(referenceRows.none { it[4] == "PENDING" }) {
            "Reference component audit contains unresolved PENDING entries"
        }

        val referenceMenuRows = readRows(referenceMenuAudit.asFile, expectedColumns = 6)
        val referenceMenus = referenceMenuRows.map { it[0] }
        require(referenceMenus == referenceMenus.sorted()) {
            "Reference menu audit must stay sorted by menu file name"
        }
        require(referenceMenus.size == 29 && referenceMenus.size == referenceMenus.toSet().size) {
            "The old.apk/1.apk menu inventory must contain 29 unique XML files"
        }
        require(referenceMenuRows.count { it[1] == "BOTH" } == 28 &&
            referenceMenuRows.count { it[1] == "FINAL_ONLY" } == 1
        ) {
            "Reference menu APK-presence totals must remain BOTH=28, FINAL_ONLY=1"
        }
        require(referenceMenuRows.sumOf { it[2].toInt() } == 148 &&
            referenceMenuRows.sumOf { it[3].toInt() } == 181
        ) {
            "Reference menu item totals must remain old=148, final=181"
        }
        referenceMenuRows.forEach { row ->
            val menu = row[0]
            require(row[1] in setOf("BOTH", "OLD_ONLY", "FINAL_ONLY")) {
                "$menu has invalid APK presence ${row[1]}"
            }
            require(row[2].toIntOrNull() != null && row[3].toIntOrNull() != null) {
                "$menu has invalid item counts ${row[2]} / ${row[3]}"
            }
            require(row[4] in setOf("PENDING", "MATCH", "FIXED", "SUPERSEDED")) {
                "$menu has invalid audit status ${row[4]}"
            }
            if (row[4] == "PENDING") {
                require(row[5] == "-") { "$menu is pending but already claims evidence" }
            } else {
                require(row[5] != "-") { "$menu ${row[4]} has no direct evidence" }
                requireSymbolEvidenceExists(row[5], menu, "INTEGRATION")
            }
        }
        require(referenceMenuRows.none { it[4] == "PENDING" }) {
            "Reference menu audit contains unresolved PENDING entries"
        }

        val referenceManifestRows = readRows(referenceManifestAudit.asFile, expectedColumns = 6)
        val referenceManifestFacets = referenceManifestRows.map { it[0] }
        require(referenceManifestFacets == referenceManifestFacets.sorted()) {
            "Reference manifest audit must stay sorted by facet"
        }
        require(referenceManifestFacets.size == 26 &&
            referenceManifestFacets.size == referenceManifestFacets.toSet().size
        ) {
            "The old.apk/1.apk manifest audit must contain 26 unique facets"
        }
        referenceManifestRows.forEach { row ->
            val facet = row[0]
            require(row[1] in setOf("BOTH", "OLD_ONLY", "FINAL_ONLY")) {
                "$facet has invalid APK presence ${row[1]}"
            }
            require(row[4] in setOf("PENDING", "MATCH", "FIXED", "SUPERSEDED")) {
                "$facet has invalid audit status ${row[4]}"
            }
            if (row[4] == "PENDING") {
                require(row[5] == "-") { "$facet is pending but already claims evidence" }
            } else {
                require(row[5] != "-") { "$facet ${row[4]} has no direct evidence" }
                requireSymbolEvidenceExists(row[5], facet, "INTEGRATION")
            }
        }
        require(referenceManifestRows.none { it[4] == "PENDING" }) {
            "Reference manifest audit contains unresolved PENDING entries"
        }

        val referenceThemeRows = readRows(referenceThemeAudit.asFile, expectedColumns = 6)
        val referenceThemeFacets = referenceThemeRows.map { it[0] }
        require(referenceThemeFacets == referenceThemeFacets.sorted()) {
            "Reference theme audit must stay sorted by facet"
        }
        require(referenceThemeFacets.size == 18 && referenceThemeFacets.size == referenceThemeFacets.toSet().size) {
            "The old.apk/1.apk and Futacha theme audit must contain 18 unique facets"
        }
        referenceThemeRows.forEach { row ->
            val facet = row[0]
            require(row[1] in setOf("BOTH", "FUTACHA", "TOSHIAKI_COMPAT")) {
                "$facet has invalid theme scope ${row[1]}"
            }
            require(row[4] in setOf("PENDING", "MATCH", "FIXED", "SUPERSEDED")) {
                "$facet has invalid audit status ${row[4]}"
            }
            if (row[4] == "PENDING") {
                require(row[5] == "-") { "$facet is pending but already claims evidence" }
            } else {
                require(row[5] != "-") { "$facet ${row[4]} has no direct evidence" }
                requireSymbolEvidenceExists(row[5], facet, "INTEGRATION")
            }
        }
        require(referenceThemeRows.none { it[4] == "PENDING" }) {
            "Reference theme audit contains unresolved PENDING entries"
        }

        val referenceLayoutRows = readRows(referenceLayoutAudit.asFile, expectedColumns = 9)
        val referenceLayouts = referenceLayoutRows.map { it[0] }
        require(referenceLayouts == referenceLayouts.sorted()) {
            "Reference layout audit must stay sorted by layout resource name"
        }
        require(referenceLayouts.size == 115 && referenceLayouts.size == referenceLayouts.toSet().size) {
            "The old.apk/1.apk layout audit must contain 115 unique logical layouts"
        }
        require(referenceLayoutRows.count { it[1] == "BOTH" } == 93 &&
            referenceLayoutRows.count { it[1] == "OLD_ONLY" } == 1 &&
            referenceLayoutRows.count { it[1] == "FINAL_ONLY" } == 21
        ) {
            "Reference layout APK-presence totals must remain BOTH=93, OLD_ONLY=1, FINAL_ONLY=21"
        }
        require(referenceLayoutRows.sumOf { it[2].toInt() } == 106 &&
            referenceLayoutRows.sumOf { it[3].toInt() } == 117
        ) {
            "Reference layout qualifier-variant totals must remain old=106, final=117"
        }
        val sha256 = Regex("[0-9a-f]{64}")
        referenceLayoutRows.forEach { row ->
            val layoutName = row[0]
            require(row[1] in setOf("BOTH", "OLD_ONLY", "FINAL_ONLY")) {
                "$layoutName has invalid APK presence ${row[1]}"
            }
            val oldVariants = requireNotNull(row[2].toIntOrNull()) {
                "$layoutName has invalid old variant count ${row[2]}"
            }
            val finalVariants = requireNotNull(row[3].toIntOrNull()) {
                "$layoutName has invalid final variant count ${row[3]}"
            }
            require(oldVariants >= 0 && finalVariants >= 0) {
                "$layoutName has negative layout variant counts"
            }
            require((oldVariants == 0 && row[4] == "-") || (oldVariants > 0 && sha256.matches(row[4]))) {
                "$layoutName has an invalid old layout fingerprint ${row[4]}"
            }
            require((finalVariants == 0 && row[5] == "-") || (finalVariants > 0 && sha256.matches(row[5]))) {
                "$layoutName has an invalid final layout fingerprint ${row[5]}"
            }
            require(row[7] in setOf("PENDING", "MATCH", "FIXED", "SUPERSEDED")) {
                "$layoutName has invalid audit status ${row[7]}"
            }
            if (row[7] == "PENDING") {
                require(row[8] == "-") { "$layoutName is pending but already claims evidence" }
            } else {
                require(row[8] != "-") { "$layoutName ${row[7]} has no direct evidence" }
                requireSymbolEvidenceExists(row[8], layoutName, "INTEGRATION")
            }
        }
        require(referenceLayoutRows.none { it[7] == "PENDING" }) {
            "Reference layout audit contains unresolved PENDING entries"
        }

        val referenceResourceRows = readRows(referenceResourceAudit.asFile, expectedColumns = 8)
        val referenceResourceTypes = referenceResourceRows.map { it[0] }
        require(referenceResourceTypes == referenceResourceTypes.sorted() &&
            referenceResourceTypes.size == referenceResourceTypes.toSet().size
        ) {
            "Reference resource audit must contain unique resource types in sorted order"
        }
        val expectedResourceCounts = mapOf(
            "anim" to Triple(0, 1, 1),
            "array" to Triple(52, 67, 67),
            "color" to Triple(2, 3, 3),
            "dimen" to Triple(2, 2, 2),
            "drawable" to Triple(85, 109, 111),
            "integer" to Triple(2, 2, 3),
            "string" to Triple(364, 446, 454),
            "style" to Triple(2, 4, 4),
            "xml" to Triple(10, 11, 11)
        )
        require(referenceResourceTypes.toSet() == expectedResourceCounts.keys) {
            "Reference resource type inventory changed"
        }
        referenceResourceRows.forEach { row ->
            val type = row[0]
            val counts = Triple(
                requireNotNull(row[1].toIntOrNull()) { "$type has invalid old resource count" },
                requireNotNull(row[2].toIntOrNull()) { "$type has invalid final resource count" },
                requireNotNull(row[3].toIntOrNull()) { "$type has invalid union resource count" }
            )
            require(counts == expectedResourceCounts[type]) {
                "$type resource counts changed: $counts"
            }
            require(sha256.matches(row[4]) && sha256.matches(row[5])) {
                "$type has an invalid reference resource fingerprint"
            }
            require(row[6] == "REVIEWED") { "$type resource audit is not reviewed" }
            requireSymbolEvidenceExists(row[7], type, "INTEGRATION")
        }

        val matrixRows = readRows(releaseDeviceMatrix.asFile, expectedColumns = 6)
        val matrixIds = matrixRows.map { it[0] }
        require(matrixIds.size == matrixIds.toSet().size) { "Duplicate release device matrix id" }
        val requiredModes = setOf("FUTACHA", "TOSHIAKI_COMPAT")
        val requiredAndroidTargets = setOf(
            "EMULATOR_API_26", "EMULATOR_API_29", "EMULATOR_API_32", "EMULATOR_API_33",
            "EMULATOR_LATEST", "PHYSICAL_LATEST"
        )
        val requiredIosTargets = setOf(
            "SIMULATOR_SMALL_LATEST", "SIMULATOR_STANDARD_LATEST", "PHYSICAL_LATEST"
        )
        matrixRows.forEach { row ->
            require(row[1] in requiredModes) { "${row[0]} has invalid mode ${row[1]}" }
            require(row[2] in setOf("ANDROID", "IOS")) { "${row[0]} has invalid platform ${row[2]}" }
            require(row[4] in setOf("THREE_BUTTON", "GESTURE", "BOTH")) {
                "${row[0]} has invalid navigation mode ${row[4]}"
            }
            val requiredScenarioSet = setOf("COMMON", "CLOSED_ISSUES", "LAYOUT", "MEDIA")
            val scenarios = row[5].split(',').toSet()
            require(scenarios.containsAll(requiredScenarioSet)) {
                "${row[0]} misses release scenarios ${requiredScenarioSet - scenarios}"
            }
            if (row[3] == "PHYSICAL_LATEST") {
                require(scenarios.containsAll(setOf("REAL_NETWORK", "PLATFORM_SERVICES"))) {
                    "${row[0]} physical-device run must include real network and platform services"
                }
            }
        }
        requiredModes.forEach { mode ->
            require(matrixRows.filter { it[1] == mode && it[2] == "ANDROID" }.map { it[3] }.toSet()
                .containsAll(requiredAndroidTargets)) {
                "Release matrix misses Android targets for $mode"
            }
            require(matrixRows.filter { it[1] == mode && it[2] == "IOS" }.map { it[3] }.toSet()
                .containsAll(requiredIosTargets)) {
                "Release matrix misses iOS targets for $mode"
            }
        }
    }
}

tasks.register("verifyReleaseReadiness") {
    group = "verification"
    description = "Fails unless every required emulator, simulator, and physical-device cell passed for the current commit."
    dependsOn(validateQualityContracts)

    val matrixFile = layout.projectDirectory.file("quality/release-device-matrix.tsv")
    val resultsPath = providers.gradleProperty("releaseEvidenceFile")
    inputs.file(matrixFile)
    inputs.property("releaseEvidenceFile", resultsPath.orElse(""))

    doLast {
        require(resultsPath.isPresent) {
            "Missing -PreleaseEvidenceFile=<results.tsv>; release readiness cannot be inferred from unit tests"
        }
        val resultsFile = rootProject.file(resultsPath.get())
        require(resultsFile.isFile) { "Release evidence file does not exist: ${resultsFile.path}" }
        val requiredIds = matrixFile.asFile.readLines().drop(1).filter { it.isNotBlank() }
            .map { it.substringBefore('\t') }.toSet()
        val rows = resultsFile.readLines().filter { it.isNotBlank() }
        require(rows.firstOrNull() == "matrix_id\tbuild_sha\tresult\tevidence") {
            "Release evidence header must be: matrix_id\\tbuild_sha\\tresult\\tevidence"
        }
        val parsed = rows.drop(1).associate { line ->
            val columns = line.split('\t')
            require(columns.size == 4) { "Invalid release evidence row: $line" }
            require(columns.none { it.isBlank() }) { "Release evidence row contains a blank field: $line" }
            columns[0] to columns
        }
        require(parsed.size == rows.size - 1) { "Release evidence contains duplicate matrix ids" }
        require(parsed.keys == requiredIds) {
            "Release evidence matrix mismatch; missing=${requiredIds - parsed.keys}, unexpected=${parsed.keys - requiredIds}"
        }
        val dirtyPaths = ProcessBuilder("git", "status", "--porcelain")
            .directory(rootProject.projectDir)
            .start().inputStream.bufferedReader().use { it.readText().trim() }
        require(dirtyPaths.isEmpty()) {
            "Release evidence is only valid for a clean worktree; commit or remove pending changes first"
        }
        val currentSha = ProcessBuilder("git", "rev-parse", "HEAD")
            .directory(rootProject.projectDir)
            .start().inputStream.bufferedReader().use { it.readText().trim() }
        parsed.forEach { (matrixId, columns) ->
            require(columns[1].matches(Regex("[0-9a-f]{40}"))) {
                "$matrixId build_sha must be a full 40-character lowercase Git SHA"
            }
            require(columns[1] == currentSha) { "$matrixId evidence is for ${columns[1]}, expected $currentSha" }
            require(columns[2] == "PASS") { "$matrixId is not PASS: ${columns[2]}" }
            val evidence = columns[3]
            require(evidence.startsWith("https://") || rootProject.file(evidence).exists()) {
                "$matrixId evidence must be an existing path or HTTPS artifact URL: $evidence"
            }
        }
    }
}

tasks.register("qualityGate") {
    group = "verification"
    description = "Runs contract validation, deterministic tests, Android lint, and the debug build."
    dependsOn(
        validateQualityContracts,
        ":shared:check",
        ":app-android:testDebugUnitTest",
        ":app-android:lintDebug",
        ":app-android:assembleDebug"
    )
}
