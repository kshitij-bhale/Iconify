package com.drdisagree.iconify.core.utils.overlay.compilers

import com.drdisagree.iconify.data.common.Dynamic.DATA_DIR
import com.drdisagree.iconify.data.common.Resources.TEMP_CACHE_DIR
import com.topjohnwu.superuser.Shell
import java.io.IOException

object OnDemandCompiler {

    private const val EXTRACT_DIR_NAME = "CompileOnDemand"
    private val extractDir = "$DATA_DIR/$EXTRACT_DIR_NAME"

    private var mOverlayName: String? = null
    private var mPackage: String? = null
    private var mStyle = "0"
    private var mForce = false

    /**
     * Builds an Android overlay APK on-demand by compiling resources, creating a manifest,
     * aligning, and signing the package.
     *
     * This function manages the lifecycle of overlay creation, including environment setup,
     * resource manipulation, and optional system-level installation.
     *
     * @param overlayName The name of the overlay component to be built.
     * @param style The specific style variant identifier (e.g., "0", "1").
     * @param targetPackage The package name of the application being targeted by the overlay.
     * @param force If true, the overlay will be forcibly installed to the system and enabled;
     *              if false, it will be backed up without immediate installation.
     * @return True if an error occurred during the build process; false if the build and
     *         deployment were successful.
     * @throws IOException If an error occurs during file operations or resource extraction.
     */
    @Throws(IOException::class)
    fun buildOverlay(
        overlayName: String,
        style: String,
        targetPackage: String,
        force: Boolean
    ): Boolean {
        mOverlayName = overlayName
        mPackage = targetPackage
        mStyle = style
        mForce = force

        OverlayInstaller.prepareWorkspace(
            extractDirToClean = extractDir,
            assetPaths = listOf("$EXTRACT_DIR_NAME/$targetPackage/$overlayName$style"),
            extraDirs = listOf("$TEMP_CACHE_DIR/$targetPackage"),
            force = force,
            overlayPackages = listOf(overlayName)
        )
        moveOverlaysToCache()
        handleNewToastStyle()

        val source = "$TEMP_CACHE_DIR/$targetPackage/$overlayName"

        if (OverlayCompiler.buildOverlayApk(overlayName, targetPackage, source)) {
            OverlayInstaller.cleanup(extractDir)
            return true
        }

        OverlayInstaller.deploy(listOf(overlayName), force)
        OverlayInstaller.cleanup(extractDir)
        return false
    }

    private fun moveOverlaysToCache() {
        Shell.cmd(
            "mv -f \"$extractDir/$mPackage/$mOverlayName$mStyle\" \"$TEMP_CACHE_DIR/$mPackage/$mOverlayName\""
        ).exec().isSuccess
    }

    private fun handleNewToastStyle() {
        if (mOverlayName != "TSTFRM") return

        Shell.cmd(
            $$"find \"$$TEMP_CACHE_DIR/$$mPackage/$$mOverlayName/\" -type f -name \"*.xml\" -exec sh -c 'for file; do if echo \"$file\" | grep -q \"/[^/]*-night/\"; then sed -i \"s/?android:colorBackgroundFloating/@*android:color\\/system_neutral2_800/g\" \"$file\"; else sed -i \"s/?android:colorBackgroundFloating/@*android:color\\/system_neutral2_10/g\" \"$file\"; fi; done' sh {} +"
        ).exec()
    }
}
