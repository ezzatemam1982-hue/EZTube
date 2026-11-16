package com.clipflow.app.player

import com.deniscerri.ytdl.database.models.GithubReleaseAsset
import com.deniscerri.ytdl.util.UpdateUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfUpdaterRepositoryTest {

    @Test
    fun bothSelfUpdatersTargetCustomEzTubeGitHubRepository() {
        assertEquals(
            "https://api.github.com/repos/ezzatemam1982-hue/EZTube/releases",
            UpdateUtil.RELEASES_URL,
        )
        assertEquals(
            "https://api.github.com/repos/ezzatemam1982-hue/EZTube/releases?per_page=30",
            AppUpdate.RELEASES_URL,
        )
    }

    @Test
    fun updateUtilComparesSemanticVersionsWithoutOverflowAndSelectsAbiOrFallbackApk() {
        assertTrue(UpdateUtil.compareVersionTags("1.0.0", "v1.0.1") < 0)
        assertTrue(UpdateUtil.compareVersionTags("1.0.0", "v2.2.0") < 0)
        assertTrue(UpdateUtil.compareVersionTags("v1.10.0", "v1.9.0") > 0)
        assertEquals(0, UpdateUtil.compareVersionTags("1.0.0", "v1.0.0"))

        val abiAsset = GithubReleaseAsset(
            name = "EZTube-1.1.0-arm64-v8a.apk",
            browser_download_url = "https://github.com/ezzatemam1982-hue/EZTube/releases/download/v1.1.0/EZTube-1.1.0-arm64-v8a.apk",
            size = 1024L,
        )
        val universalAsset = GithubReleaseAsset(
            name = "EZTube.apk",
            browser_download_url = "https://github.com/ezzatemam1982-hue/EZTube/releases/download/v1.1.0/EZTube.apk",
            size = 2048L,
        )
        val fossAsset = GithubReleaseAsset(
            name = "EZTube-foss.apk",
            browser_download_url = "https://github.com/ezzatemam1982-hue/EZTube/releases/download/v1.1.0/EZTube-foss.apk",
            size = 512L,
        )

        assertEquals(abiAsset, UpdateUtil.selectApkAsset(listOf(fossAsset, universalAsset, abiAsset), abi = "arm64-v8a"))
        assertEquals(universalAsset, UpdateUtil.selectApkAsset(listOf(fossAsset, universalAsset), abi = "armeabi-v7a"))
    }

    @Test
    fun appUpdateSelectsEzTubeAndAbiAssetsAndComparesSuffixedVersions() {
        assertEquals(
            "EZTube.apk",
            AppUpdate.selectApkAssetName(listOf("EZTube-1.1.0-foss.apk", "EZTube.apk"), abi = "arm64-v8a"),
        )
        assertEquals(
            "ClipFlow-1.1.0-arm64-v8a-release-github.apk",
            AppUpdate.selectApkAssetName(
                listOf("EZTube-1.1.0-izzy.apk", "ClipFlow-1.1.0-armeabi-v7a-release-github.apk", "ClipFlow-1.1.0-arm64-v8a-release-github.apk"),
                abi = "arm64-v8a",
            ),
        )
        assertTrue(AppUpdate.compareVersions("1.0.1-github", "1.0.0") > 0)
        assertTrue(AppUpdate.compareVersions("v1.2.0", "1.1.9") > 0)
    }
}
