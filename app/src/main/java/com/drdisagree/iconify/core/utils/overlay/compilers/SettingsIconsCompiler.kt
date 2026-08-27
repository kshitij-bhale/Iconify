package com.drdisagree.iconify.core.utils.overlay.compilers

import com.drdisagree.iconify.data.common.Const.GMS_PACKAGE
import com.drdisagree.iconify.data.common.Const.SETTINGS_PACKAGE
import com.drdisagree.iconify.data.common.Const.WELLBEING_PACKAGE
import com.drdisagree.iconify.data.common.Dynamic.DATA_DIR
import com.drdisagree.iconify.data.common.Resources.TEMP_CACHE_DIR
import com.topjohnwu.superuser.Shell
import java.io.IOException

object SettingsIconsCompiler {

    private const val EXTRACT_DIR_NAME = "CompileOnDemand"
    private val extractDir = "$DATA_DIR/$EXTRACT_DIR_NAME"

    private var mIconSet = 1
    private var mIconBg = 1
    private var mForce = false
    private val mPackages = arrayOf(
        SETTINGS_PACKAGE,
        WELLBEING_PACKAGE,
        GMS_PACKAGE
    )

    @Throws(IOException::class)
    fun buildOverlay(iconSet: Int, iconBg: Int, resources: String, force: Boolean): Boolean {
        mIconSet = iconSet
        mIconBg = iconBg
        mForce = force

        val overlayNames = mPackages.indices.map { "SIP${it + 1}" }

        OverlayInstaller.prepareWorkspace(
            extractDirToClean = extractDir,
            assetPaths = mPackages.map { "$EXTRACT_DIR_NAME/$it/SIP$iconSet" },
            extraDirs = mPackages.map { "$TEMP_CACHE_DIR/$it/" },
            force = force,
            overlayPackages = overlayNames
        )
        moveOverlaysToCache()

        for (i in mPackages.indices) {
            val overlayName = overlayNames[i]
            val source = "$TEMP_CACHE_DIR/${mPackages[i]}/$overlayName"

            // Write resources (before the build ladder; aapt needs them present)
            if (resources != "" &&
                OverlayInstaller.writeResourceFile(
                    source,
                    "values/Iconify.xml",
                    resources,
                    "SettingsIcons"
                )
            ) {
                OverlayInstaller.cleanup(extractDir)
                return true
            }

            if (OverlayCompiler.buildOverlayApk(overlayName, mPackages[i], source)) {
                OverlayInstaller.cleanup(extractDir)
                return true
            }
        }

        OverlayInstaller.deploy(overlayNames, force)
        OverlayInstaller.cleanup(extractDir)
        return false
    }

    private fun moveOverlaysToCache() {
        for (i in mPackages.indices) {
            Shell.cmd(
                "mv -f \"$extractDir/${mPackages[i]}/SIP$mIconSet\" \"$TEMP_CACHE_DIR/${mPackages[i]}/SIP${i + 1}\""
            ).exec()
        }

        if (mIconBg == 1) {
            for (i in mPackages.indices) {
                val base = "$TEMP_CACHE_DIR/${mPackages[i]}/SIP${i + 1}/res"
                Shell.cmd(
                    "rm -rf \"$base/drawable\"",
                    "cp -rf \"$base/drawable-night\" \"$base/drawable\""
                ).exec()
                Shell.cmd(
                    "rm -rf \"$base/drawable-anydpi\"",
                    "cp -rf \"$base/drawable-night-anydpi\" \"$base/drawable-anydpi\""
                ).exec()
            }
        }
    }
}
