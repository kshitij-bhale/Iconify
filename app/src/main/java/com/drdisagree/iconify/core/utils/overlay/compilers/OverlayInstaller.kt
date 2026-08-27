package com.drdisagree.iconify.core.utils.overlay.compilers

import android.util.Log
import com.drdisagree.iconify.core.utils.AssetsUtils.copyAssets
import com.drdisagree.iconify.core.utils.FileUtils
import com.drdisagree.iconify.core.utils.Logger.writeLog
import com.drdisagree.iconify.core.utils.RootUtils.setPermissions
import com.drdisagree.iconify.core.utils.SystemUtils.mountRO
import com.drdisagree.iconify.core.utils.SystemUtils.mountRW
import com.drdisagree.iconify.core.utils.overlay.OverlayUtils.disableOverlays
import com.drdisagree.iconify.core.utils.overlay.OverlayUtils.enableOverlays
import com.drdisagree.iconify.data.common.Dynamic.DATA_DIR
import com.drdisagree.iconify.data.common.Resources.BACKUP_DIR
import com.drdisagree.iconify.data.common.Resources.OVERLAY_DIR
import com.drdisagree.iconify.data.common.Resources.SIGNED_DIR
import com.drdisagree.iconify.data.common.Resources.SYSTEM_OVERLAY_DIR
import com.drdisagree.iconify.data.common.Resources.TEMP_CACHE_DIR
import com.drdisagree.iconify.data.common.Resources.TEMP_DIR
import com.drdisagree.iconify.data.common.Resources.TEMP_OVERLAY_DIR
import com.drdisagree.iconify.data.common.Resources.UNSIGNED_DIR
import com.drdisagree.iconify.data.common.Resources.UNSIGNED_UNALIGNED_DIR
import com.drdisagree.iconify.helpers.BinaryInstaller.symLinkBinaries
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * Shared workspace + deployment steps used by every overlay compiler.
 *
 * These were copy-pasted (with tiny variations) across OnDemandCompiler,
 * SettingsIconsCompiler, RoundnessCompiler and DynamicCompiler. The shell
 * command sequences here reproduce the previous behaviour exactly; only the
 * duplication is removed.
 *
 * Overlay names are passed bare (e.g. "SIP1", "CR1", "Dynamic2"); the
 * "IconifyComponent" prefix and ".apk"/".overlay" suffixes are applied here.
 */
object OverlayInstaller {

    private val TAG = OverlayInstaller::class.java.simpleName

    private fun apkName(overlayName: String) = "IconifyComponent$overlayName.apk"
    private fun overlayPackage(overlayName: String) = "IconifyComponent$overlayName.overlay"

    /**
     * Cleans the workspace, extracts assets and (re)creates the temp dir tree.
     *
     * @param extractDirToClean the per-compiler extraction dir to wipe
     *        (e.g. "$DATA_DIR/CompileOnDemand" or "$DATA_DIR/Overlays").
     * @param assetPaths assets to extract via [copyAssets].
     * @param extraDirs additional dirs to ensure (e.g. per-package cache dirs).
     * @param force when true, disables [overlayPackages] (already installed);
     *        when false, ensures the backup dir exists.
     * @param overlayPackages bare overlay names to disable when [force].
     */
    fun prepareWorkspace(
        extractDirToClean: String,
        assetPaths: List<String>,
        extraDirs: List<String> = emptyList(),
        force: Boolean,
        overlayPackages: List<String> = emptyList()
    ) {
        // Create symbolic link
        symLinkBinaries()

        // Clean data directory
        Shell.cmd(
            "rm -rf $TEMP_DIR",
            "rm -rf $extractDirToClean"
        ).exec()

        // Extract overlay(s) from assets
        assetPaths.forEach { copyAssets(it) }

        // Create temp directory tree
        FileUtils.ensureDirs(
            TEMP_DIR,
            TEMP_OVERLAY_DIR,
            TEMP_CACHE_DIR,
            UNSIGNED_UNALIGNED_DIR,
            UNSIGNED_DIR,
            SIGNED_DIR,
            *extraDirs.toTypedArray()
        )

        if (!force) {
            FileUtils.ensureDirs(BACKUP_DIR)
        } else {
            // Disable the overlays in case they are already enabled
            disableOverlays(*overlayPackages.map { overlayPackage(it) }.toTypedArray())
        }
    }

    /**
     * Deploys the freshly-signed overlays.
     *
     * Always copies each APK into the module overlay dir. When [force], also
     * installs via `pm`, pushes into the system overlay dir and enables them;
     * otherwise stashes a copy in the backup dir.
     */
    fun deploy(overlayNames: List<String>, force: Boolean) {
        overlayNames.forEach { copyToModule(it) }

        if (force) {
            overlayNames.forEach { installFromData(it) }

            mountRW()
            overlayNames.forEach { copyToSystem(it) }
            mountRO()

            enableOverlays(*overlayNames.map { overlayPackage(it) }.toTypedArray())
        } else {
            overlayNames.forEach { copyToBackup(it) }
        }
    }

    /** Wipes the temp dir tree and the per-compiler extraction dir. */
    fun cleanup(extractDirToClean: String) {
        Shell.cmd(
            "rm -rf $TEMP_DIR",
            "rm -rf $extractDirToClean"
        ).exec()
    }

    /** Copies a signed overlay into the magisk module overlay dir. */
    fun copyToModule(overlayName: String) {
        val name = apkName(overlayName)
        Shell.cmd("cp -rf $SIGNED_DIR/$name $OVERLAY_DIR/$name").exec()
        setPermissions(644, "$OVERLAY_DIR/$name")
    }

    /** Stashes a signed overlay in the backup dir (non-force path). */
    fun copyToBackup(overlayName: String) {
        val name = apkName(overlayName)
        Shell.cmd("cp -rf $SIGNED_DIR/$name $BACKUP_DIR/$name").exec()
    }

    /** Stages a signed overlay to the data dir and installs it via `pm`. */
    fun installFromData(overlayName: String) {
        val name = apkName(overlayName)
        Shell.cmd("cp -rf $SIGNED_DIR/$name $DATA_DIR/$name").exec()
        setPermissions(644, "$DATA_DIR/$name")
        Shell.cmd("pm install -r $DATA_DIR/$name").exec()
        Shell.cmd("rm -rf $DATA_DIR/$name").exec()
    }

    /**
     * Stages a signed overlay into the data dir (copy + perms) WITHOUT
     * installing it. Used by the dynamic compiler, which stages every overlay
     * first and `pm install`s them in a batch afterwards via [installStaged].
     */
    fun stageToData(overlayName: String) {
        val name = apkName(overlayName)
        Shell.cmd("cp -rf $SIGNED_DIR/$name $DATA_DIR/$name").exec()
        setPermissions(644, "$DATA_DIR/$name")
    }

    /** Installs a previously [stageToData]'d overlay via `pm` and removes it. */
    fun installStaged(overlayName: String) {
        val name = apkName(overlayName)
        Shell.cmd(
            "pm install -r $DATA_DIR/$name",
            "rm -rf $DATA_DIR/$name"
        ).exec()
    }

    /** Pushes a signed overlay into the read-only system overlay dir. */
    fun copyToSystem(overlayName: String) {
        val name = apkName(overlayName)
        Shell.cmd("cp -rf $SIGNED_DIR/$name $SYSTEM_OVERLAY_DIR/$name").exec()
        setPermissions(644, "$SYSTEM_OVERLAY_DIR/$name")
    }

    /**
     * Writes a resource file under `<source>/res/<subPath>`, replacing any
     * existing file. Returns true on failure (recording a [CompilerFailure]).
     */
    fun writeResourceFile(
        source: String,
        subPath: String,
        contents: String,
        label: String
    ): Boolean {
        return try {
            val file = File("$source/res/$subPath")
            file.parentFile?.let { FileUtils.ensureDirs(it.absolutePath) }
            if (file.exists()) file.delete()
            file.writeText(contents, Charsets.UTF_8)
            Log.i("$TAG - WriteResources", "Successfully written resources for $label")
            false
        } catch (e: Exception) {
            val output = listOf(e.message ?: e.toString())
            Log.e("$TAG - WriteResources", "Failed to write resources for $label", e)
            writeLog(
                "$TAG - WriteResources",
                "Failed to write resources for $label",
                output
            )
            CompilerErrorStore.record(
                CompilerFailure(
                    stage = "WriteResources",
                    target = label,
                    message = "Failed to write resources for $label",
                    output = output
                )
            )
            true
        }
    }
}
