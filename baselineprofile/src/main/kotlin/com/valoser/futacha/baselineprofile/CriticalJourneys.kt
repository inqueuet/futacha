package com.valoser.futacha.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.valoser.futacha"
internal const val BENCHMARK_ITERATIONS = 10

enum class FutachaProfile {
    FUTACHA,
    TOSHIAKI_COMPAT
}

internal fun MacrobenchmarkScope.runCriticalNavigation(profile: FutachaProfile) {
    when (profile) {
        FutachaProfile.FUTACHA -> runFutachaNavigation()
        FutachaProfile.TOSHIAKI_COMPAT -> runToshiakiCompatNavigation()
    }
}

internal fun ensureProfile(profile: FutachaProfile) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    seedBenchmarkFixture(device, profile)
    launchTarget(device)
    dismissTransientDialogs(device)
    waitForProfileRoot(device, profile)
    normalizeToBoardRoot(device, profile)
    // Compatibility mode records the consumed change-log version asynchronously.
    // Let that tiny preference write settle before BaselineProfileRule force-stops
    // the target process for its first collection iteration.
    Thread.sleep(1_000)
    device.pressHome()
}

private fun seedBenchmarkFixture(device: UiDevice, profile: FutachaProfile) {
    val output = device.executeShellCommand(
        "am broadcast -W " +
            "-a com.valoser.futacha.action.SEED_BENCHMARK_FIXTURE " +
            "--es profile ${profile.name} " +
            "-n $TARGET_PACKAGE/.BenchmarkFixtureReceiver"
    )
    check("result=-1" in output) { "Failed to seed benchmark fixture: $output" }
}

private fun MacrobenchmarkScope.runFutachaNavigation() {
    val device = device
    normalizeToBoardRoot(device, FutachaProfile.FUTACHA)
    waitFor(device, By.desc("履歴を開く"), "ふたちゃ板一覧")
    clickText(device, "チュートリアル＠ふたちゃ")
    clickText(device, "チュートリアル")
    waitFor(device, By.desc("返信"), "ふたちゃスレッド")
    device.swipe(
        device.displayWidth / 2,
        device.displayHeight * 3 / 4,
        device.displayWidth / 2,
        device.displayHeight / 4,
        12
    )
    device.waitForIdle()
    device.pressBack()
    waitFor(device, By.textContains("チュートリアル＠ふたちゃ"), "ふたちゃカタログ")
    device.pressBack()
    waitFor(device, By.desc("履歴を開く"), "ふたちゃ板一覧への復帰")
}

private fun MacrobenchmarkScope.runToshiakiCompatNavigation() {
    val device = device
    dismissTransientDialogs(device)
    normalizeToBoardRoot(device, FutachaProfile.TOSHIAKI_COMPAT)
    waitFor(device, By.desc("ドロワー"), "としあき(仮)板一覧")
    clickText(device, "チュートリアル＠ふたちゃ")
    click(device, By.desc("チュートリアル"), "チュートリアル")
    waitFor(device, By.desc("書き込み"), "としあき(仮)スレッド")
    device.swipe(
        device.displayWidth / 2,
        device.displayHeight * 3 / 4,
        device.displayWidth / 2,
        device.displayHeight / 4,
        12
    )
    device.waitForIdle()
    device.pressBack()
    waitFor(device, By.textContains("チュートリアル＠ふたちゃ"), "としあき(仮)カタログ")
    device.pressBack()
    waitFor(device, By.desc("ドロワー"), "としあき(仮)板一覧への復帰")
}

private fun launchTarget(device: UiDevice) {
    device.executeShellCommand("am start -W $TARGET_PACKAGE/.MainActivity")
    device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 15_000)
    device.waitForIdle()
}

private fun dismissTransientDialogs(device: UiDevice) {
    repeat(4) {
        if (device.hasObject(By.text("更新履歴")) && device.hasObject(By.desc("戻る"))) {
            device.findObject(By.desc("戻る")).click()
            device.waitForIdle()
            return@repeat
        }
        val button = listOf("後で", "閉じる", "OK")
            .firstNotNullOfOrNull { label -> device.findObject(By.text(label)) }
            ?: return
        button.click()
        device.waitForIdle()
    }
}

private fun waitForProfileRoot(device: UiDevice, profile: FutachaProfile) {
    val selector = when (profile) {
        FutachaProfile.FUTACHA -> By.desc("履歴を開く")
        FutachaProfile.TOSHIAKI_COMPAT -> By.desc("ドロワー")
    }
    val deadline = System.currentTimeMillis() + 15_000
    while (System.currentTimeMillis() < deadline) {
        device.findObject(selector)?.let { return }
        if (device.hasObject(By.text("更新履歴")) && device.hasObject(By.desc("戻る"))) {
            device.findObject(By.desc("戻る")).click()
            device.waitForIdle()
        } else {
            device.wait(Until.hasObject(selector), 500)
        }
    }
    error("Timed out waiting for ${profile.name} root")
}

private fun normalizeToBoardRoot(device: UiDevice, profile: FutachaProfile) {
    val board = By.text("チュートリアル＠ふたちゃ")
    repeat(5) {
        if (device.wait(Until.hasObject(board), 2_000)) return
        dismissTransientDialogs(device)
        if (device.hasObject(board)) return
        if (
            profile == FutachaProfile.TOSHIAKI_COMPAT &&
            device.hasObject(By.desc("ドロワー")) &&
            !device.hasObject(By.desc("戻る"))
        ) {
            if (device.wait(Until.hasObject(board), 10_000)) return
            error(
                "TOSHIAKI_COMPAT board root did not receive the seeded board; " +
                    "visible=${visibleSemantics(device)}"
            )
        }
        device.pressBack()
        device.waitForIdle()
    }
    error(
        "Timed out returning ${profile.name} to the seeded board list; " +
            "visible=${visibleSemantics(device)}"
    )
}

private fun visibleSemantics(device: UiDevice): List<String> =
    device.findObjects(By.pkg(TARGET_PACKAGE))
        .flatMap { node -> listOfNotNull(node.text, node.contentDescription) }
        .filter(String::isNotBlank)
        .distinct()
        .take(30)

private fun clickText(device: UiDevice, text: String) {
    click(device, By.text(text), text)
}

private fun click(device: UiDevice, selector: androidx.test.uiautomator.BySelector, label: String) {
    waitFor(device, selector, label).click()
    device.waitForIdle()
}

private fun waitFor(
    device: UiDevice,
    selector: androidx.test.uiautomator.BySelector,
    label: String
): androidx.test.uiautomator.UiObject2 = checkNotNull(
    device.wait(Until.findObject(selector), 15_000)
) { "Timed out waiting for $label" }
