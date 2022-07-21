/*
 * OAndBackupX: open-source apps backup and restore app.
 * Copyright (C) 2020  Antonios Hazim
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.machiav3lli.backup.actions

import android.content.Context
import com.machiav3lli.backup.MODE_APK
import com.machiav3lli.backup.MODE_DATA
import com.machiav3lli.backup.MODE_DATA_DE
import com.machiav3lli.backup.MODE_DATA_EXT
import com.machiav3lli.backup.MODE_DATA_MEDIA
import com.machiav3lli.backup.MODE_DATA_OBB
import com.machiav3lli.backup.OABX
import com.machiav3lli.backup.PREFS_ENABLESESSIONINSTALLER
import com.machiav3lli.backup.PREFS_EXCLUDECACHE
import com.machiav3lli.backup.PREFS_INSTALLER_PACKAGENAME
import com.machiav3lli.backup.PREFS_REFRESHDELAY
import com.machiav3lli.backup.PREFS_REFRESHAPPINFOTIMEOUT
import com.machiav3lli.backup.PREFS_REFRESHTIMEOUT_DEFAULT
import com.machiav3lli.backup.PREFS_RESTOREAVOIDTEMPCOPY
import com.machiav3lli.backup.PREFS_RESTOREPERMISSIONS
import com.machiav3lli.backup.PREFS_RESTORETARCMD
import com.machiav3lli.backup.R
import com.machiav3lli.backup.dbs.entity.Backup
import com.machiav3lli.backup.handler.LogsHandler
import com.machiav3lli.backup.handler.ShellHandler
import com.machiav3lli.backup.handler.ShellHandler.Companion.findAssetFile
import com.machiav3lli.backup.handler.ShellHandler.Companion.quote
import com.machiav3lli.backup.handler.ShellHandler.Companion.quoteMultiple
import com.machiav3lli.backup.handler.ShellHandler.Companion.runAsRoot
import com.machiav3lli.backup.handler.ShellHandler.Companion.utilBoxQ
import com.machiav3lli.backup.handler.ShellHandler.ShellCommandFailedException
import com.machiav3lli.backup.handler.ShellHandler.UnexpectedCommandResult
import com.machiav3lli.backup.items.ActionResult
import com.machiav3lli.backup.items.Package
import com.machiav3lli.backup.items.RootFile
import com.machiav3lli.backup.items.StorageFile
import com.machiav3lli.backup.tasks.AppActionWork
import com.machiav3lli.backup.utils.CryptoSetupException
import com.machiav3lli.backup.utils.decryptStream
import com.machiav3lli.backup.utils.getCryptoSalt
import com.machiav3lli.backup.utils.getEncryptionPassword
import com.machiav3lli.backup.utils.isAllowDowngrade
import com.machiav3lli.backup.utils.isDisableVerification
import com.machiav3lli.backup.utils.isEncryptionEnabled
import com.machiav3lli.backup.utils.isPauseApps
import com.machiav3lli.backup.utils.isRestoreAllPermissions
import com.machiav3lli.backup.utils.suCopyFileFromDocument
import com.machiav3lli.backup.utils.suRecursiveCopyFileFromDocument
import com.machiav3lli.backup.utils.suUnpackTo
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.regex.Pattern

open class RestoreAppAction(context: Context, work: AppActionWork?, shell: ShellHandler) :
    BaseAppAction(context, work, shell) {
    fun run(
        app: Package,
        backup: Backup,
        backupDir: StorageFile?,
        backupMode: Int
    ): ActionResult {
        try {
            Timber.i("Restoring: ${app.packageName} [${app.packageLabel}]")
            work?.setOperation("pre")
            val pauseApp = context.isPauseApps
            if (pauseApp) {
                Timber.d("pre-process package (to avoid file inconsistencies during backup etc.)")
                preprocessPackage(app.packageName)
            }
            try {
                if (backupDir != null) {
                    if (backupMode and MODE_APK == MODE_APK) {
                        work?.setOperation("apk")
                        restorePackage(backupDir, backup)
                        refreshAppInfo(context, app)    // also waits for valid paths
                    }
                    if (backupMode != MODE_APK) {
                        restoreAllData(work, app, backup, backupDir, backupMode)
                    }
                } else return ActionResult(
                    app,
                    null,
                    "No backup file exists",
                    false
                )
            } catch (e: PackageManagerDataIncompleteException) {
                return ActionResult(
                    app,
                    null,
                    "${e.javaClass.simpleName}: ${e.message}. ${context.getString(R.string.error_pmDataIncompleteException_dataRestoreFailed)}",
                    false
                )
            } catch (e: RestoreFailedException) {
                // Unwrap issues with shell commands so users know what command ran and what was the issue
                val message =
                    when (val cause = e.cause) {
                        is ShellCommandFailedException -> {
                            val commandList = cause.commands.joinToString("; ")
                            "Shell command failed: ${commandList}\n${
                                extractErrorMessage(cause.shellResult)
                            }"
                        }
                        else -> {
                            "${e.javaClass.simpleName}: ${e.message}"
                        }
                    }
                return ActionResult(app, null, message, false)
            } catch (e: CryptoSetupException) {
                return ActionResult(app, null, "${e.javaClass.simpleName}: ${e.message}", false)
            } finally {
                work?.setOperation("fin")
                if (pauseApp) {
                    Timber.d("post-process package (to set it back to normal operation)")
                    postprocessPackage(app.packageName)
                    //markerFile?.delete()
                }
            }
        } finally {
            work?.setOperation("end")
            Timber.i("$app: Restore done: $backup")
        }
        return ActionResult(app, backup, "", true)
    }

    @Throws(CryptoSetupException::class, RestoreFailedException::class)
    protected open fun restoreAllData(
        work: AppActionWork?,
        app: Package,
        backup: Backup,
        backupDir: StorageFile,
        backupMode: Int
    ) {
        if (!isPlausiblePath(app.dataPath, app.packageName))
            refreshAppInfo(context, app)    // wait for valid paths
        if (backup.hasAppData && backupMode and MODE_DATA == MODE_DATA) {
            Timber.i("[${backup.packageName}] Restoring app's data")
            work?.setOperation("dat")
            restoreData(app, backup, backupDir, true)
        } else {
            Timber.i("[${backup.packageName}] Skip restoring app's data; not part of the backup or restore mode")
        }
        if (backup.hasDevicesProtectedData && backupMode and MODE_DATA_DE == MODE_DATA_DE) {
            Timber.i("[${backup.packageName}] Restoring app's device-protected data")
            work?.setOperation("prt")
            restoreDeviceProtectedData(app, backup, backupDir, true)
        } else {
            Timber.i("[${backup.packageName}] Skip restoring app's device protected data; not part of the backup or restore mode")
        }
        if (backup.hasExternalData && backupMode and MODE_DATA_EXT == MODE_DATA_EXT) {
            Timber.i("[${backup.packageName}] Restoring app's external data")
            work?.setOperation("ext")
            restoreExternalData(app, backup, backupDir, true)
        } else {
            Timber.i("[${backup.packageName}] Skip restoring app's external data; not part of the backup or restore mode")
        }
        if (backup.hasObbData && backupMode and MODE_DATA_OBB == MODE_DATA_OBB) {
            Timber.i("[${backup.packageName}] Restoring app's obb files")
            work?.setOperation("obb")
            restoreObbData(app, backup, backupDir, false)
        } else {
            Timber.i("[${backup.packageName}] Skip restoring app's obb files; not part of the backup or restore mode")
        }
        if (backup.hasMediaData && backupMode and MODE_DATA_MEDIA == MODE_DATA_MEDIA) {
            Timber.i("[${backup.packageName}] Restoring app's media files")
            work?.setOperation("med")
            restoreMediaData(app, backup, backupDir, false)
        } else {
            Timber.i("[${backup.packageName}] Skip restoring app's media files; not part of the backup or restore mode")
        }
    }

    @Throws(ShellCommandFailedException::class)
    protected fun wipeDirectory(targetPath: String, excludeDirs: List<String>) {
        if (targetPath != "/" && targetPath.isNotEmpty() && RootFile(targetPath).exists()) {
            val targetContents: MutableList<String> =
                mutableListOf(*shell.suGetDirectoryContents(RootFile(targetPath)))
            targetContents.removeAll(excludeDirs)
            if (targetContents.isEmpty()) {
                Timber.i("Nothing to remove in $targetPath")
                return
            }
            val removeTargets = targetContents
                .map { s -> RootFile(targetPath, s).absolutePath }
            Timber.d("Removing existing files in $targetPath")
            val command = "$utilBoxQ rm -rf ${quoteMultiple(removeTargets)}"
            runAsRoot(command)
        }
    }

    @Throws(RestoreFailedException::class)
    open fun restorePackage(backupDir: StorageFile, backup: Backup) {
        val packageName = backup.packageName
        Timber.i("[$packageName] Restoring from $backupDir")
        val baseApkFile = backupDir.findFile(BASE_APK_FILENAME)
            ?: throw RestoreFailedException("$BASE_APK_FILENAME is missing in backup", null)
        Timber.d("[$packageName] Found $BASE_APK_FILENAME in backup archive")
        val splitApksInBackup: Array<StorageFile> = try {
            backupDir.listFiles()
                .filter { !it.isDirectory } // Forget about dictionaries immediately
                .filter { it.name?.endsWith(".apk") == true } // Only apks are relevant
                .filter { it.name != BASE_APK_FILENAME } // Base apk is a special case
                .toTypedArray()
        } catch (e: FileNotFoundException) {
            Timber.e("Restore APKs failed: %s", e.message)
            throw RestoreFailedException(String.format("Restore APKs failed: %s", e.message), e)
        }

        // Copy all apk paths into a single array
        var apksToRestore = arrayOf(baseApkFile)
        if (splitApksInBackup.isEmpty()) {
            Timber.d("[$packageName] The backup does not contain split apks")
        } else {
            apksToRestore += splitApksInBackup.drop(0)
            Timber.i("[%s] Package is splitted into %d apks", packageName, apksToRestore.size)
        }
        /* in newer android versions selinux rules prevent system_server
         * from accessing many directories. in android 9 this prevents pm
         * install from installing from other directories that the package
         * staging directory (/data/local/tmp).
         * you can also pipe the apk data to the install command providing
         * it with a -S $apk_size value. but judging from this answer
         * https://issuetracker.google.com/issues/80270303#comment14 this
         * could potentially be unwise to use.
         */
        val stagingApkPath: RootFile?
        if (PACKAGE_STAGING_DIRECTORY.exists()) {
            // It's expected, that all SDK 24+ version of Android go this way.
            stagingApkPath = PACKAGE_STAGING_DIRECTORY
        } else {
            /*
             * pm cannot install from a file on the data partition
             * Failure [INSTALL_FAILED_INVALID_URI] is reported
             * therefore, if the backup directory is oab's own data
             * directory a temporary directory on the external storage
             * is created where the apk is then copied to.
             *
             * @Tiefkuehlpizze 2020-06-28: When does this occur? Checked it with emulator image with SDK 24. This is
             *                             a very old piece of code. Maybe it's obsolete.
             * @machiav3lli 2020-08-09: In some oem ROMs the access to data/local/tmp is not allowed, I don't know how
             *                              this has changed in the last couple of years.
             */
            stagingApkPath = RootFile(context.getExternalFilesDir(null), "apkTmp")
            Timber.w("Weird configuration. Expecting that the system does not allow installing from OABX's own data directory. Copying the apk to $stagingApkPath")
        }
        var success = false
        try {
            // Try it with a staging path. This is usually the way to go.
            // copy apks to staging dir
            apksToRestore.forEach { apkFile ->
                // The file must be touched before it can be written for some reason...
                Timber.d("[$packageName] Copying ${apkFile.name} to staging dir")
                runAsRoot(
                    "touch ${
                        quote(
                            RootFile(
                                stagingApkPath,
                                "$packageName.${apkFile.name}"
                            )
                        )
                    }"
                )
                suCopyFileFromDocument(
                    apkFile,
                    RootFile(stagingApkPath, "$packageName.${apkFile.name}").absolutePath
                )
            }
            val sb = StringBuilder()
            val disableVerification = context.isDisableVerification

            // disable verify apps over usb
            if (disableVerification)
                runAsRoot("settings put global verifier_verify_adb_installs 0")

            when {
                OABX.prefFlag(PREFS_ENABLESESSIONINSTALLER, false) -> {
                    val packageFiles = listOf(baseApkFile).plus(splitApksInBackup).map {
                        RootFile(stagingApkPath, "$packageName.${it.name}")
                    }

                    // create session
                    runAsRoot(
                        getSessionCreateCommand(
                            backup.profileId,
                            packageFiles.sumOf { it.length() })
                    ).let {
                        val sessionIdPattern = Pattern.compile("(\\d+)")
                        val sessionIdMatcher = sessionIdPattern.matcher(it.out[0])
                        val found = sessionIdMatcher.find()
                        val sessionId = sessionIdMatcher.group(1)?.toInt()

                        if (found && sessionId != null) {
                            // write each of the bundle files
                            packageFiles.forEach { rFile ->
                                sb.append(getSessionWriteCommand(rFile, sessionId)).append(" ; ")
                            }
                            // commit session
                            sb.append(getSessionCommitCommand(sessionId))
                        }
                    }
                }
                else -> {
                    // Install main package
                    sb.append(
                        getPackageInstallCommand(
                            RootFile(stagingApkPath, "$packageName.${baseApkFile.name}"),
                            backup.profileId
                        )
                    )
                    // If split apk resources exist, install them afterwards (order does not matter)
                    //TODO hg42 gather results, eventually ignore grant errors, use script?
                    if (splitApksInBackup.isNotEmpty()) {
                        splitApksInBackup.forEach {
                            sb.append(" ; ").append(
                                getPackageInstallCommand(
                                    RootFile(stagingApkPath, "$packageName.${it.name}"),
                                    backup.profileId,
                                    backup.packageName
                                )
                            )
                        }
                    }
                }
            }
            success = runAsRoot(sb.toString()).isSuccess // TODO integrate permissionsResult too

            val permissionsCmd = mutableListOf<String>()
            if (!context.isRestoreAllPermissions &&
                OABX.prefFlag(PREFS_RESTOREPERMISSIONS, true)
            ) {
                backup.permissions
                    .filterNot { it.isEmpty() }
                    .forEach { p ->
                        permissionsCmd.addAll(listOf("pm", "grant", backup.packageName, p, ";"))
                    }
                try {
                    runAsRoot(permissionsCmd.joinToString(" "))
                } catch (e: ShellCommandFailedException) {
                    val error = e.shellResult.err.joinToString { "\n" }
                    Timber.e("Restoring permissions failed: $error")
                    // TODO integrate this exception in the result
                }
            }


            // re-enable verify apps over usb
            if (disableVerification)
                runAsRoot("settings put global verifier_verify_adb_installs 1")

            // Todo: Reload package meta data; Package Manager knows everything now; Function missing
        } catch (e: ShellCommandFailedException) {
            val error = extractErrorMessage(e.shellResult)
            Timber.e("Restore APKs failed: $error")
            throw RestoreFailedException(error, e)
        } catch (e: IOException) {
            throw RestoreFailedException("Could not copy apk to staging directory", e)
        } finally {
            // Cleanup only in case of failure, otherwise it's already included
            if (!success)
                Timber.i("[$packageName] Restore unsuccessful")
            val command =
                "$utilBoxQ rm ${
                    quoteMultiple(
                        apksToRestore.map {
                            RootFile(
                                stagingApkPath,
                                "$packageName.${it.name}"
                            ).absolutePath
                        }
                    )
                }"
            try {
                runAsRoot(command)
            } catch (e: ShellCommandFailedException) {
                Timber.w(
                    "[$packageName] Cleanup after failure failed: ${
                        e.shellResult.err.joinToString(
                            "; "
                        )
                    }"
                )
            }
        }
    }

    @Throws(RestoreFailedException::class)
    private fun genericRestoreDataByCopying(
        targetPath: String,
        backupInstanceDir: StorageFile,
        what: String
    ) {
        try {
            val backupDirToRestore = backupInstanceDir.findFile(what)
                ?: throw RestoreFailedException(
                    String.format(
                        LOG_DIR_IS_MISSING_CANNOT_RESTORE,
                        what
                    )
                )
            suRecursiveCopyFileFromDocument(backupDirToRestore, targetPath)
        } catch (e: IOException) {
            throw RestoreFailedException("Could not read the input file due to IOException", e)
        } catch (e: ShellCommandFailedException) {
            val error = extractErrorMessage(e.shellResult)
            throw RestoreFailedException(
                "Shell command failed: ${e.commands.joinToString { "; " }}\n$error",
                e
            )
        }
    }

    @Throws(CryptoSetupException::class, IOException::class)
    protected fun openArchiveFile(
        archive: StorageFile,
        isCompressed: Boolean,
        isEncrypted: Boolean,
        iv: ByteArray?
    ): InputStream {
        var inputStream: InputStream = BufferedInputStream(archive.inputStream()!!)
        if (isEncrypted) {
            val password = context.getEncryptionPassword()
            if (iv != null && password.isNotEmpty() && context.isEncryptionEnabled()) {
                Timber.d("Decryption enabled")
                inputStream = inputStream.decryptStream(password, context.getCryptoSalt(), iv)
            }
        }
        if (isCompressed) {
            inputStream = GzipCompressorInputStream(inputStream)
        }
        return inputStream
    }

    @Throws(RestoreFailedException::class, CryptoSetupException::class)
    fun genericRestoreFromArchiveTarApi(
        dataType: String,
        archive: StorageFile,
        targetPath: String,
        isCompressed: Boolean,
        isEncrypted: Boolean,
        iv: ByteArray?,
        cachePath: File?,
        forceOldVersion: Boolean = false
    ) {
        // Check if the archive exists, uncompressTo can also throw FileNotFoundException
        if (!archive.exists()) {
            throw RestoreFailedException("Backup archive at $archive is missing")
        }
        var tempDir: RootFile? = null
        try {
            TarArchiveInputStream(
                openArchiveFile(archive, isCompressed, isEncrypted, iv)
            ).use { archiveStream ->
                if (OABX.prefFlag(PREFS_RESTOREAVOIDTEMPCOPY, true)) {
                    // clear the data from the final directory
                    wipeDirectory(
                        targetPath,
                        DATA_EXCLUDED_BASENAMES
                    )
                    archiveStream.suUnpackTo(RootFile(targetPath), forceOldVersion)
                } else {
                    // Create a temporary directory in OABX's cache directory and uncompress the data into it
                    //File(cachePath, "restore_${UUID.randomUUID()}").also { it.mkdirs() }.let {
                    Files.createTempDirectory(cachePath?.toPath(), "restore_")?.toFile()?.let {
                        tempDir = RootFile(it)
                        archiveStream.suUnpackTo(tempDir!!, forceOldVersion)
                        // clear the data from the final directory
                        wipeDirectory(
                            targetPath,
                            DATA_EXCLUDED_BASENAMES
                        )
                        // Move all the extracted data into the target directory
                        val command =
                            "$utilBoxQ mv -f ${quote(tempDir.toString())}/* ${quote(targetPath)}/"
                        runAsRoot(command)
                    } ?: throw IOException("Could not create temporary directory $targetPath")
                }
            }
        } catch (e: FileNotFoundException) {
            throw RestoreFailedException("Backup archive at $archive is missing", e)
        } catch (e: IOException) {
            throw RestoreFailedException(
                "Could not read the input file or write an output file due to IOException: $e",
                e
            )
        } catch (e: ShellCommandFailedException) {
            val error = extractErrorMessage(e.shellResult)
            throw RestoreFailedException(
                "Could not restore a file due to a failed root command for $targetPath: $error",
                e
            )
        } catch (e: Throwable) {
            LogsHandler.unhandledException(e)
            throw RestoreFailedException("Could not restore a file due to a failed root command", e)
        } finally {
            // Clean up the temporary directory if it was initialized
            tempDir?.let {
                try {
                    it.deleteRecursive()
                } catch (e: IOException) {
                    Timber.e("Could not delete temporary directory $it. Cache Size might be growing. Reason: $e")
                }
            }
        }
    }

    @Throws(RestoreFailedException::class, CryptoSetupException::class, NotImplementedError::class)
    fun genericRestoreFromArchiveTarCmd(
        dataType: String,
        archive: StorageFile,
        targetPath: String,
        isCompressed: Boolean,
        isEncrypted: Boolean,
        iv: ByteArray?
    ) {
        RootFile(targetPath).let { targetDir ->
            // Check if the archive exists, uncompressTo can also throw FileNotFoundException
            if (!archive.exists()) {
                throw RestoreFailedException("Backup archive at $archive is missing")
            }
            try {
                openArchiveFile(archive, isCompressed, isEncrypted, iv).use { archiveStream ->

                    targetDir.mkdirs()  // in case it doesn't exist

                    wipeDirectory(
                        targetDir.absolutePath,
                        DATA_EXCLUDED_BASENAMES
                    )

                    val tarScript = findAssetFile("tar.sh").toString()
                    val qTarScript = quote(tarScript)
                    val exclude = findAssetFile(ShellHandler.EXCLUDE_FILE).toString()
                    val excludeCache = findAssetFile(ShellHandler.EXCLUDE_CACHE_FILE).toString()

                    var options = ""
                    options += " --exclude " + quote(exclude)
                    if (OABX.prefFlag(PREFS_EXCLUDECACHE, true)
                    ) {
                        options += " --exclude " + quote(excludeCache)
                    }
                    var suOptions = "--mount-master"

                    val cmd =
                        "su $suOptions -c sh $qTarScript extract $utilBoxQ $options ${
                            quote(
                                targetDir
                            )
                        }"
                    Timber.i("SHELL: $cmd")

                    val process = Runtime.getRuntime().exec(cmd)

                    val shellIn = process.outputStream
                    val shellOut = process.inputStream
                    val shellErr = process.errorStream

                    archiveStream.copyTo(shellIn, 65536)

                    shellIn.close()

                    val err = shellErr.readBytes().decodeToString()
                    val errLines = err
                        .split("\n")
                        .filterNot { line ->
                            line.isBlank()
                                    || line.contains("tar: unknown file type") // e.g. socket 140000
                        }
                    if (errLines.isNotEmpty()) {
                        val errFiltered = errLines.joinToString("\n")
                        Timber.i(errFiltered)
                        throw ScriptException(errFiltered)
                    }
                }
            } catch (e: FileNotFoundException) {
                throw RestoreFailedException("Backup archive at $archive is missing", e)
            } catch (e: IOException) {
                throw RestoreFailedException(
                    "Could not read the input file or write an output file due to IOException: $e",
                    e
                )
            } catch (e: ShellCommandFailedException) {
                val error = extractErrorMessage(e.shellResult)
                throw RestoreFailedException(
                    "Could not restore a file due to a failed root command for $targetDir: $error",
                    e
                )
            } catch (e: Throwable) {
                LogsHandler.unhandledException(e)
                throw RestoreFailedException(
                    "Could not restore a file due to a failed root command",
                    e
                )
            }
        }
    }

    @Throws(RestoreFailedException::class, CryptoSetupException::class)
    private fun genericRestoreFromArchive(
        dataType: String,
        archive: StorageFile,
        targetPath: String,
        isCompressed: Boolean,
        isEncrypted: Boolean,
        iv: ByteArray?,
        cachePath: File?,
        forceOldVersion: Boolean = false
    ) {
        Timber.i("${OABX.app.packageName} -> $targetPath")
        if (!forceOldVersion && OABX.prefFlag(PREFS_RESTORETARCMD, true)) {
            return genericRestoreFromArchiveTarCmd(
                dataType,
                archive,
                targetPath,
                isCompressed,
                isEncrypted,
                iv
            )
        } else {
            return genericRestoreFromArchiveTarApi(
                dataType,
                archive,
                targetPath,
                isCompressed,
                isEncrypted,
                iv,
                cachePath,
                forceOldVersion
            )
        }
    }

    @Throws(RestoreFailedException::class)
    private fun genericRestorePermissions(
        dataType: String,
        targetPath: String,
        uidgidcon: Array<String>
    ) {
        try {
            val (uid, gid, con) = uidgidcon
            val gidCache = "${gid}_cache"
            Timber.i("Getting user/group info and apply it recursively on $targetPath")
            // get the contents. lib for example must be owned by root
            //TODO hg42 I think, lib is always a link
            //TODO hg42 directories we exclude would keep their uidgidcon from before
            //TODO hg42 this doesn't seem to be correct, unless the apk install would manage updating uidgidcon
            val dataContents: MutableList<String> =
                mutableListOf(*shell.suGetDirectoryContents(RootFile(targetPath)))
            // Don't exclude any files from chown, as this may cause SELINUX issues (lost of data on restart)
            // dataContents.removeAll(DATA_EXCLUDED_BASENAMES)
            dataContents.removeAll(DATA_EXCLUDED_CACHE_DIRS)    // these are not excluded but processed differently! -> cacheTargets

            // calculate a list what must be updated inside the directory
            val chownTargets = dataContents.map { s -> RootFile(targetPath, s).absolutePath }
            val cacheTargets = DATA_EXCLUDED_CACHE_DIRS.map { s -> RootFile(targetPath, s).absolutePath }
            Timber.d("Changing owner and group of '$targetPath' to $uid:$gid and selinux context to $con")
            var command =
                "$utilBoxQ chown $uid:$gid ${
                    quote(RootFile(targetPath).absolutePath)
                }"
            if (chownTargets.isNotEmpty())
                command += " ; $utilBoxQ chown -R $uid:$gid ${
                    quoteMultiple(chownTargets)
                }"
            if (cacheTargets.isNotEmpty())
                command += " ; $utilBoxQ chown -R $uid:$gidCache ${
                    quoteMultiple(cacheTargets)
                }"
            command += if (con == "?") //TODO hg42: when does it happen?
                " ; restorecon -RF -v ${quote(targetPath)}"
            else
                " ; chcon -R -h -v '$con' ${quote(targetPath)}"
            runAsRoot(command)
        } catch (e: ShellCommandFailedException) {
            val errorMessage = "Could not update permissions for $dataType"
            Timber.e(errorMessage)
            throw RestoreFailedException(errorMessage, e)
        } catch (e: UnexpectedCommandResult) {
            val errorMessage =
                "Could not extract user and group information from $dataType directory"
            Timber.e(errorMessage)
            throw RestoreFailedException(errorMessage, e)
        }
    }

    @Throws(RestoreFailedException::class, CryptoSetupException::class)
    open fun restoreData(
        app: Package,
        backup: Backup,
        backupDir: StorageFile,
        compressed: Boolean
    ) {
        val dataType = BACKUP_DIR_DATA
        val backupFilename = getBackupArchiveFilename(
            dataType,
            backup.isCompressed,
            backup.isEncrypted
        )
        Timber.d(LOG_EXTRACTING_S, backup.packageName, backupFilename)
        val backupArchive = backupDir.findFile(backupFilename)
            ?: throw RestoreFailedException(
                String.format(
                    LOG_BACKUP_ARCHIVE_MISSING,
                    backupFilename
                )
            )
        val extractTo = app.dataPath
        if (!isPlausiblePath(extractTo, app.packageName))
            throw RestoreFailedException(
                "path '$extractTo' does not contain ${app.packageName}"
            )

        if (!RootFile(extractTo).isDirectory)
            throw RestoreFailedException("directory '$extractTo' does not exist")

        // retrieve the assigned uid and gid from the data directory Android created
        val uidgidcon = shell.suGetOwnerGroupContext(extractTo)
        genericRestoreFromArchive(
            dataType,
            backupArchive,
            extractTo,
            backup.isCompressed,
            backup.isEncrypted,
            backup.iv,
            RootFile(context.cacheDir),
            isOldVersion(backup)
        )
        genericRestorePermissions(
            dataType,
            extractTo,
            uidgidcon
        )
    }

    @Throws(RestoreFailedException::class, CryptoSetupException::class)
    open fun restoreDeviceProtectedData(
        app: Package,
        backup: Backup,
        backupDir: StorageFile,
        compressed: Boolean
    ) {
        val dataType = BACKUP_DIR_DEVICE_PROTECTED_FILES
        val backupFilename = getBackupArchiveFilename(
            dataType,
            backup.isCompressed,
            backup.isEncrypted
        )
        Timber.d(LOG_EXTRACTING_S, backup.packageName, backupFilename)
        val backupArchive = backupDir.findFile(backupFilename)
            ?: throw RestoreFailedException(
                String.format(
                    LOG_BACKUP_ARCHIVE_MISSING,
                    backupFilename
                )
            )
        val extractTo = app.devicesProtectedDataPath
        if (!isPlausiblePath(extractTo, app.packageName))
            throw RestoreFailedException(
                "path '$extractTo' does not contain ${app.packageName}"
            )

        if (!RootFile(extractTo).isDirectory)
            throw RestoreFailedException("directory '$extractTo' does not exist")

        // retrieve the assigned uid and gid from the data directory Android created
        val uidgidcon = shell.suGetOwnerGroupContext(extractTo)
        genericRestoreFromArchive(
            dataType,
            backupArchive,
            extractTo,
            backup.isCompressed,
            backup.isEncrypted,
            backup.iv,
            RootFile(deviceProtectedStorageContext.cacheDir),
            isOldVersion(backup)
        )
        genericRestorePermissions(
            dataType,
            extractTo,
            uidgidcon
        )
    }

    @Throws(RestoreFailedException::class, CryptoSetupException::class)
    open fun restoreExternalData(
        app: Package,
        backup: Backup,
        backupDir: StorageFile,
        compressed: Boolean
    ) {
        val dataType = BACKUP_DIR_EXTERNAL_FILES
        val backupFilename = getBackupArchiveFilename(
            dataType,
            backup.isCompressed,
            backup.isEncrypted
        )
        Timber.d(LOG_EXTRACTING_S, backup.packageName, backupFilename)
        val backupArchive = backupDir.findFile(backupFilename)
            ?: throw RestoreFailedException(
                String.format(
                    LOG_BACKUP_ARCHIVE_MISSING,
                    backupFilename
                )
            )
        val extractTo = app.getExternalDataPath(context)
        if (!isPlausiblePath(extractTo, app.packageName))
            throw RestoreFailedException(
                "path '$extractTo' does not contain ${app.packageName}"
            )

        genericRestoreFromArchive(
            dataType,
            backupArchive,
            extractTo,
            backup.isCompressed,
            backup.isEncrypted,
            backup.iv,
            context.externalCacheDir?.let { RootFile(it) },
            isOldVersion(backup)
        )
    }

    @Throws(RestoreFailedException::class)
    open fun restoreObbData(
        app: Package,
        backup: Backup,
        backupDir: StorageFile,
        compressed: Boolean
    ) {
        val extractTo = app.getObbFilesPath(context)
        if (!isPlausiblePath(extractTo, app.packageName))
            throw RestoreFailedException(
                "path '$extractTo' does not contain ${app.packageName}"
            )

        if (isOldVersion(backup)) {

            genericRestoreDataByCopying(
                extractTo,
                backupDir,
                BACKUP_DIR_OBB_FILES
            )

        } else {

            val dataType = BACKUP_DIR_OBB_FILES
            val backupFilename = getBackupArchiveFilename(
                dataType,
                backup.isCompressed,
                backup.isEncrypted
            )
            Timber.d(LOG_EXTRACTING_S, backup.packageName, backupFilename)
            val backupArchive = backupDir.findFile(backupFilename)
                ?: throw RestoreFailedException(
                    String.format(
                        LOG_BACKUP_ARCHIVE_MISSING,
                        backupFilename
                    )
                )
            genericRestoreFromArchive(
                dataType,
                backupArchive,
                extractTo,
                backup.isCompressed,
                backup.isEncrypted,
                backup.iv,
                context.externalCacheDir?.let { RootFile(it) },
            )

        }
    }

    @Throws(RestoreFailedException::class)
    open fun restoreMediaData(
        app: Package,
        backup: Backup,
        backupDir: StorageFile,
        compressed: Boolean
    ) {
        val extractTo = app.getMediaFilesPath(context)
        if (!isPlausiblePath(extractTo, app.packageName))
            throw RestoreFailedException(
                "path '$extractTo' does not contain ${app.packageName}"
            )

        if (isOldVersion(backup)) {

            genericRestoreDataByCopying(
                extractTo,
                backupDir,
                BACKUP_DIR_MEDIA_FILES
            )

        } else {

            val dataType = BACKUP_DIR_MEDIA_FILES
            val backupFilename = getBackupArchiveFilename(
                dataType,
                backup.isCompressed,
                backup.isEncrypted
            )
            Timber.d(LOG_EXTRACTING_S, backup.packageName, backupFilename)
            val backupArchive = backupDir.findFile(backupFilename)
                ?: throw RestoreFailedException(
                    String.format(
                        LOG_BACKUP_ARCHIVE_MISSING,
                        backupFilename
                    )
                )
            genericRestoreFromArchive(
                dataType,
                backupArchive,
                extractTo,
                backup.isCompressed,
                backup.isEncrypted,
                backup.iv,
                context.externalCacheDir?.let { RootFile(it) }
            )

        }
    }

    private fun getPackageInstallCommand(
        apkPath: RootFile,
        profileId: Int,
        basePackageName: String? = null
    ): String =
        listOfNotNull(
            "cat", quote(apkPath.absolutePath),
            "|",
            "pm", "install",
            basePackageName?.let { "-p $basePackageName" },
            if (context.isRestoreAllPermissions) "-g" else null,
            if (context.isAllowDowngrade) "-d" else null,
            "-i ${OABX.prefString(PREFS_INSTALLER_PACKAGENAME, OABX.app.packageName)}",
            "-t",
            "-r",
            "-S", apkPath.length().toString(),
            "--user", profileId,
        ).joinToString(" ")


    private fun getSessionCreateCommand(
        profileId: Int,
        sumSize: Long
    ): String =
        listOfNotNull(
            "pm", "install-create",
            "-i", OABX.prefString(PREFS_INSTALLER_PACKAGENAME, OABX.app.packageName),
            "--user", profileId,
            "-r",
            "-t",
            if (context.isRestoreAllPermissions) "-g" else null,
            if (context.isAllowDowngrade) "-d" else null,
            "-S", sumSize
        ).joinToString(" ")

    private fun getSessionWriteCommand(
        apkPath: RootFile,
        sessionId: Int
    ): String =
        listOfNotNull(
            "cat", quote(apkPath.absolutePath),
            "|",
            "pm", "install-write",
            "-S", apkPath.length(),
            sessionId,
            apkPath.name,
        ).joinToString(" ")


    private fun getSessionCommitCommand(
        sessionId: Int
    ): String =
        listOfNotNull(
            "pm", "install-commit", sessionId
        ).joinToString(" ")

    @Throws(PackageManagerDataIncompleteException::class)
    private fun refreshAppInfo(context: Context, app: Package) {
        val sleepTimeMs = 1000L

        // delay before first try
        val delayMs = OABX.prefInt(PREFS_REFRESHDELAY, 0) * 1000L
        var timeWaitedMs = 0L
        do {
            Thread.sleep(sleepTimeMs)
            timeWaitedMs += sleepTimeMs
        } while (timeWaitedMs < delayMs)

        // try multiple times to get valid paths from PackageManager
        // maxWaitMs is cumulated sleep time between tries
        val maxWaitMs = OABX.prefInt(PREFS_REFRESHAPPINFOTIMEOUT, PREFS_REFRESHTIMEOUT_DEFAULT) * 1000L
        timeWaitedMs = 0L
        var attemptNo = 0
        do {
            if (timeWaitedMs > maxWaitMs) {
                throw PackageManagerDataIncompleteException(maxWaitMs / 1000L)
            }
            if (timeWaitedMs > 0) {
                Timber.d("[${app.packageName}] PackageManager returned invalid data paths, attempt $attemptNo, waited ${timeWaitedMs / 1000L} of $maxWaitMs seconds")
                Thread.sleep(sleepTimeMs)
            }
            app.refreshFromPackageManager(context)
            timeWaitedMs += sleepTimeMs
            attemptNo++
        } while (!this.isPlausiblePackageInfo(app))
    }

    private fun isPlausiblePackageInfo(app: Package): Boolean {
        return app.dataPath.isNotBlank()
                && app.apkPath.isNotBlank()
                && app.devicesProtectedDataPath.isNotBlank()
    }

    private fun isPlausiblePath(path: String, packageName: String): Boolean {
        return path.contains(packageName)
    }

    class RestoreFailedException : AppActionFailedException {
        constructor(message: String?) : super(message)
        constructor(message: String?, cause: Throwable?) : super(message, cause)
    }

    class PackageManagerDataIncompleteException(val seconds: Long) :
        Exception("PackageManager returned invalid data paths after trying $seconds seconds to retrieve them")

    companion object {
        protected val PACKAGE_STAGING_DIRECTORY = RootFile("/data/local/tmp")
        const val BASE_APK_FILENAME = "base.apk"
        const val LOG_DIR_IS_MISSING_CANNOT_RESTORE =
            "Backup directory %s is missing. Cannot restore"
        const val LOG_EXTRACTING_S = "[%s] Extracting %s"
        const val LOG_BACKUP_ARCHIVE_MISSING = "Backup archive %s is missing. Cannot restore"

        fun isOldVersion(backup: Backup) = backup.backupVersionCode < 8000
    }
}
