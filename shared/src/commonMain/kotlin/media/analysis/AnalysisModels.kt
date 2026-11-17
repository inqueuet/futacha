package com.valoser.futacha.shared.media.analysis

/** Fixed artifacts from toshikari. Weights are neither bundled nor automatically downloaded. */
internal enum class AnalysisModel { NUDE_NET, ANIME_CENSOR, MOBILE_SAM_ENCODER, MOBILE_SAM_DECODER }

internal data class ModelDistribution(
    val url: String,
    val bytes: Long,
    val sha256: String,
    val zipEntry: String? = null
) {
    init {
        require(url.startsWith("https://"))
        require(bytes in 1..64L * 1024 * 1024)
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        require(zipEntry == null || (zipEntry.isNotEmpty() && !zipEntry.startsWith('/') &&
            zipEntry.split('/').none { it == ".." || it == "." || it.isEmpty() }))
    }
}

internal data class AnalysisModelSpec(
    val id: AnalysisModel,
    val title: String,
    val license: String,
    val page: String,
    val bytes: Long,
    val sha256: String,
    val distribution: ModelDistribution
) {
    init {
        require(bytes in 1..64L * 1024 * 1024)
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        require(distribution.zipEntry != null || (distribution.bytes == bytes && distribution.sha256 == sha256))
    }
}

internal object AnalysisModels {
    val all: List<AnalysisModelSpec> = listOf(
        AnalysisModelSpec(AnalysisModel.NUDE_NET, "実写・写実3D向け NudeNet 320n", "AGPL-3.0",
            "https://github.com/notAI-tech/NudeNet", 12_150_158,
            "c15d8273adad2d0a92f014cc69ab2d6c311a06777a55545f2c4eb46f51911f0f",
            ModelDistribution(
                "https://files.pythonhosted.org/packages/1c/ee/1aa02d44ba958cc77e16ff1e41a0aac5e721037db7bf62b9c9d124917f87/nudenet-3.4.2-py3-none-any.whl",
                10_595_223, "5937dbd84e5d8e5de038f08ffea5a1bb50a08475776bf2b4795914ce0eaf0331", "nudenet/320n.onnx")),
        direct(AnalysisModel.ANIME_CENSOR, "2D・アニメ向け Anime Censor v1.0 n", "MIT（配布元の表示）",
            "https://huggingface.co/deepghs/anime_censor_detection", 12_104_146,
            "029de0a116f6c3c73bde62d2a8354c78664795579858f3c8e28fc1b4633a891c",
            "https://huggingface.co/deepghs/anime_censor_detection/resolve/0cf62fd6b28213b40ae0c0055f92e7ae6a96bdc2/censor_detect_v1.0_n/model.onnx"),
        direct(AnalysisModel.MOBILE_SAM_ENCODER, "輪郭用 MobileSAM encoder", "MIT（配布元の表示）",
            "https://huggingface.co/Acly/MobileSAM", 28_157_093,
            "580f5fb648ea1062c0aabc26217aed56921985f03f0cbbd852bba81d760cc749",
            "https://huggingface.co/Acly/MobileSAM/resolve/0d3b403339b4674a82493d5e97964dd78089ddc8/mobile_sam_image_encoder.onnx"),
        direct(AnalysisModel.MOBILE_SAM_DECODER, "輪郭用 MobileSAM decoder", "MIT（配布元の表示）",
            "https://huggingface.co/Acly/MobileSAM", 16_496_559,
            "8976b90a87ba50a6a72217a5ff994f7d25ce16f2229fcc1ed259e1294c622ffe",
            "https://huggingface.co/Acly/MobileSAM/resolve/0d3b403339b4674a82493d5e97964dd78089ddc8/sam_mask_decoder_multi.onnx")
    )
    private fun direct(id: AnalysisModel, title: String, license: String, page: String, bytes: Long, sha: String, url: String) =
        AnalysisModelSpec(id, title, license, page, bytes, sha, ModelDistribution(url, bytes, sha))
}
