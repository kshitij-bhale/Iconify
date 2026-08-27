package com.drdisagree.iconify.core.utils.overlay.compilers

import android.util.Log
import com.drdisagree.iconify.core.utils.FileUtils
import com.drdisagree.iconify.core.utils.Logger
import com.drdisagree.iconify.core.utils.SystemUtils
import com.drdisagree.iconify.core.utils.overlay.OverlayUtils
import com.drdisagree.iconify.core.utils.overlay.resource.ResourceManager
import com.drdisagree.iconify.core.utils.overlay.resource.ResourceType
import com.drdisagree.iconify.data.common.Const.FRAMEWORK_PACKAGE
import com.drdisagree.iconify.data.common.Const.LAUNCHER3_PACKAGE
import com.drdisagree.iconify.data.common.Const.PIXEL_LAUNCHER_PACKAGE
import com.drdisagree.iconify.data.common.Const.SETTINGS_PACKAGE
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.data.common.Dynamic.DATA_DIR
import com.drdisagree.iconify.data.common.Resources.BACKUP_DIR
import com.drdisagree.iconify.data.common.Resources.TEMP_CACHE_DIR
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

object DynamicCompiler {

    private val TAG = DynamicCompiler::class.java.simpleName
    private val extractDir = "$DATA_DIR/Overlays"

    private var mForce = false
    private var mPackage: String? = null
    private var mOverlayName: String? = null
    private val mResource: MutableMap<ResourceType, ArrayList<String>> = mutableMapOf()

    @Throws(IOException::class)
    suspend fun buildDynamicOverlay(
        packagesToUpdate: List<String> = emptyList(),
        force: Boolean = true,
    ): Boolean {
        mForce = force

        try {
            val resourcesMap = ResourceManager.generateXmlStructureForAllResources(
                packagesToUpdate.ifEmpty {
                    listOf(
                        FRAMEWORK_PACKAGE,
                        SYSTEMUI_PACKAGE,
                        PIXEL_LAUNCHER_PACKAGE,
                        LAUNCHER3_PACKAGE,
                        SETTINGS_PACKAGE
                    )
                }
            )

            // Create overlay for each package
            for (packageName in resourcesMap.keys) {
                Log.i(TAG, "Generating dynamic overlay for $packageName")

                mPackage = packageName

                mResource.clear()
                mResource[ResourceType.PORTRAIT] = ArrayList(
                    resourcesMap[packageName]!![ResourceType.PORTRAIT]!!
                )
                resourcesMap[packageName]!![ResourceType.LANDSCAPE]?.let {
                    mResource[ResourceType.LANDSCAPE] = ArrayList(it)
                }
                resourcesMap[packageName]!![ResourceType.NIGHT]?.let {
                    mResource[ResourceType.NIGHT] = ArrayList(it)
                }
                resourcesMap[packageName]!![ResourceType.NIGHT_LANDSCAPE]?.let {
                    mResource[ResourceType.NIGHT_LANDSCAPE] = ArrayList(it)
                }

                mOverlayName = getOverlayName(packageName)
                val source = "$TEMP_CACHE_DIR/$mPackage/$mOverlayName"

                // Per-package prep: clean, extract, ensure dirs + backup. Force
                // is intentionally false here — the dynamic flow defers
                // disable/install/enable to the single batch after this loop.
                withContext(Dispatchers.IO) {
                    OverlayInstaller.prepareWorkspace(
                        extractDirToClean = extractDir,
                        assetPaths = listOf("Overlays/$mPackage/$mOverlayName"),
                        extraDirs = listOf("$TEMP_CACHE_DIR/$mPackage"),
                        force = false
                    )
                }
                moveOverlaysToCache()
                writeDynamicResources(source)

                if (OverlayCompiler.buildOverlayApk(mOverlayName!!, mPackage, source)) {
                    postExecute(true)
                    return true
                }

                postExecute(false)
            }

            if (mForce) {
                Shell.cmd("rm -rf $BACKUP_DIR").exec()

                val overlayNames = resourcesMap.keys.map { getOverlayName(it) }
                val overlayPackageNames = overlayNames
                    .map { "IconifyComponent$it.overlay" }
                    .toTypedArray()

                // Disable the overlays in case they are already enabled
                OverlayUtils.disableOverlays(*overlayPackageNames)

                // Install from files dir
                overlayNames.forEach { OverlayInstaller.installStaged(it) }

                // Move to system overlay dir
                SystemUtils.mountRW()
                overlayNames.forEach { OverlayInstaller.copyToSystem(it) }
                SystemUtils.mountRO()

                // Enable the overlays
                OverlayUtils.enableOverlays(*overlayPackageNames)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build overlay! Exiting...", e)
            Logger.writeLog(
                tag = TAG,
                header = "Dynamic overlay build failed",
                exception = e
            )
            postExecute(true)
            return true
        }

        return false
    }

    private fun postExecute(hasErroredOut: Boolean) {
        if (!hasErroredOut) {
            // Copy to module overlay dir; stage to data (force) or back up.
            OverlayInstaller.copyToModule(mOverlayName!!)
            if (mForce) {
                OverlayInstaller.stageToData(mOverlayName!!)
            } else {
                OverlayInstaller.copyToBackup(mOverlayName!!)
            }
        }

        OverlayInstaller.cleanup(extractDir)
    }

    private fun moveOverlaysToCache(): Boolean {
        return try {
            val source = Paths.get("$extractDir/$mPackage/$mOverlayName")
            val target = Paths.get("$TEMP_CACHE_DIR/$mPackage/$mOverlayName")

            Files.createDirectories(target.parent)

            Files.move(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING
            )

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move overlay", e)
            Logger.writeLog(
                tag = TAG,
                header = "Dynamic overlay moving failed",
                exception = e
            )
            false
        }
    }

    /**
     * Writes the per-qualifier resource XML files (values, values-land,
     * values-night, values-land-night). The manifest + build is handled by
     * [OverlayCompiler.buildOverlayApk], which runs after this.
     */
    private fun writeDynamicResources(source: String) {
        FileUtils.ensureDirs("$source/res")

        val resourceTypes = arrayOf(
            ResourceType.PORTRAIT to "values",
            ResourceType.LANDSCAPE to "values-land",
            ResourceType.NIGHT to "values-night",
            ResourceType.NIGHT_LANDSCAPE to "values-land-night"
        )

        resourceTypes.forEach { (resourceType, folderName) ->
            val dir = File("$source/res/$folderName")
            val file = File(dir, "iconify.xml")

            val resourceList = mResource[resourceType]?.let { ArrayList(it) }

            if (!resourceList.isNullOrEmpty()) {
                FileUtils.ensureDirs(dir.absolutePath)
                file.writeText(resourceList.joinToString("\n"))
            }
        }
    }

    private fun getOverlayName(packageName: String): String {
        return when (packageName) {
            FRAMEWORK_PACKAGE -> "Dynamic1"
            SYSTEMUI_PACKAGE -> "Dynamic2"
            PIXEL_LAUNCHER_PACKAGE -> "Dynamic3"
            LAUNCHER3_PACKAGE -> "Dynamic4"
            SETTINGS_PACKAGE -> "Dynamic5"
            else -> throw Exception("Unknown package: $packageName")
        }
    }
}
