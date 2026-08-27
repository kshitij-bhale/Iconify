package com.drdisagree.iconify.core.utils.overlay.compilers

import com.drdisagree.iconify.data.common.Const.FRAMEWORK_PACKAGE
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.data.common.Dynamic.DATA_DIR
import java.io.IOException

object RoundnessCompiler {

    private val mPackages = arrayOf(FRAMEWORK_PACKAGE, SYSTEMUI_PACKAGE)
    private val mOverlayName = arrayOf("CR1", "CR2")

    private val extractDir = "$DATA_DIR/Overlays"

    @Throws(IOException::class)
    fun buildOverlay(resources: Array<String>, force: Boolean): Boolean {
        OverlayInstaller.prepareWorkspace(
            extractDirToClean = extractDir,
            assetPaths = mPackages.indices.map { "Overlays/${mPackages[it]}/${mOverlayName[it]}" },
            force = force,
            overlayPackages = mOverlayName.toList()
        )

        for (i in 0..1) {
            val overlayName = mOverlayName[i]
            val source = "$extractDir/${mPackages[i]}/$overlayName"

            // Write resources (before the build ladder; aapt needs them present)
            if (OverlayInstaller.writeResourceFile(
                    source,
                    "values/dimens.xml",
                    resources[i],
                    "UiRoundness"
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

        OverlayInstaller.deploy(mOverlayName.toList(), force)
        OverlayInstaller.cleanup(extractDir)
        return false
    }
}
