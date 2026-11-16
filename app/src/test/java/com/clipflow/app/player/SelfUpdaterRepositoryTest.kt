package com.clipflow.app.player

import com.deniscerri.ytdl.database.models.GithubRelease
import com.deniscerri.ytdl.database.models.GithubReleaseAsset
import com.deniscerri.ytdl.util.UpdateUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SelfUpdaterRepositoryTest {

    @Test
    fun bothSelfUpdatersTargetCustomEzTubeGitHubRepositoryAndUserAgent() {
        assertEquals(
            "https://api.github.com/repos/ezzatemam1982-hue/EZTube/releases",
            UpdateUtil.RELEASES_URL,
        )
        assertEquals("EZTube-App", UpdateUtil.USER_AGENT)
        assertEquals(
            "https://api.github.com/repos/ezzatemam1982-hue/EZTube/releases?per_page=30",
            AppUpdate.RELEASES_URL,
        )
    }

    @Test
    fun updateUtilStripsVPrefixHandlesEmptyReleasesAndComparesSemanticVersions() {
        assertNull(
            UpdateUtil.selectNewRelease(
                releases = emptyList(),
                currentVersion = "1.0.1",
                useBeta = false,
                skippedVersions = emptyList(),
            ),
        )
        val sameVersionRelease = GithubRelease(
            html_url = "https://github.com/ezzatemam1982-hue/EZTube/releases/tag/v1.0.1",
            tag_name = "v1.0.1",
            body = "",
            published_at = Date(),
            assets = emptyList(),
        )
        assertNull(
            UpdateUtil.selectNewRelease(
                releases = listOf(sameVersionRelease),
                currentVersion = "1.0.1",
                useBeta = false,
                skippedVersions = emptyList(),
            ),
        )
        val newerRelease = GithubRelease(
            html_url = "https://github.com/ezzatemam1982-hue/EZTube/releases/tag/v1.0.2",
            tag_name = "v1.0.2",
            body = "",
            published_at = Date(),
            assets = emptyList(),
        )
        val selected = UpdateUtil.selectNewRelease(
            releases = listOf(sameVersionRelease, newerRelease),
            currentVersion = "1.0.1",
            useBeta = false,
            skippedVersions = emptyList(),
        )
        assertNotNull(selected)
        assertEquals("v1.0.2", selected!!.tag_name)

        assertTrue(UpdateUtil.compareVersionTags("1.0.1", "v1.0.2") < 0)
        assertTrue(UpdateUtil.compareVersionTags("1.0.1", "v2.2.0") < 0)
        assertTrue(UpdateUtil.compareVersionTags("v1.10.0", "v1.9.0") > 0)
        assertEquals(0, UpdateUtil.compareVersionTags("1.0.1", "v1.0.1"))

        val abiAsset = GithubReleaseAsset(
            name = "EZTube-1.0.2-arm64-v8a.apk",
            browser_download_url = "https://github.com/ezzatemam1982-hue/EZTube/releases/download/v1.0.2/EZTube-1.0.2-arm64-v8a.apk",
            size = 1024L,
        )
        val universalAsset = GithubReleaseAsset(
            name = "EZTube.apk",
            browser_download_url = "https://github.com/ezzatemam1982-hue/EZTube/releases/download/v1.0.2/EZTube.apk",
            size = 2048L,
        )
        val fossAsset = GithubReleaseAsset(
            name = "EZTube-foss.apk",
            browser_download_url = "https://github.com/ezzatemam1982-hue/EZTube/releases/download/v1.0.2/EZTube-foss.apk",
            size = 512L,
        )

        assertEquals(abiAsset, UpdateUtil.selectApkAsset(listOf(fossAsset, universalAsset, abiAsset), abi = "arm64-v8a"))
        assertEquals(universalAsset, UpdateUtil.selectApkAsset(listOf(fossAsset, universalAsset), abi = "armeabi-v7a"))
    }

    @Test
    fun appUpdateSelectsEzTubeAndAbiAssetsAndComparesSuffixedVersions() {
        assertEquals(
            "EZTube.apk",
            AppUpdate.selectApkAssetName(listOf("EZTube-1.0.2-foss.apk", "EZTube.apk"), abi = "arm64-v8a"),
        )
        assertEquals(
            "ClipFlow-1.0.2-arm64-v8a-release-github.apk",
            AppUpdate.selectApkAssetName(
                listOf("EZTube-1.0.2-izzy.apk", "ClipFlow-1.0.2-armeabi-v7a-release-github.apk", "ClipFlow-1.0.2-arm64-v8a-release-github.apk"),
                abi = "arm64-v8a",
            ),
        )
        assertTrue(AppUpdate.compareVersions("1.0.2-github", "1.0.1") > 0)
        assertTrue(AppUpdate.compareVersions("v1.1.0", "1.0.9") > 0)
    }
}
