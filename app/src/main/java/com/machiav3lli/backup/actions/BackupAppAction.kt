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

import android.annotation.SuppressLint
import android.content.Context
import com.machiav3lli.backup.BACKUP_DATE_TIME_FORMATTER
import com.machiav3lli.backup.BACKUP_INSTANCE_PROPERTIES
import com.machiav3lli.backup.MODE_APK
import com.machiav3lli.backup.MODE_DATA
import com.machiav3lli.backup.MODE_DATA_DE
import com.machiav3lli.backup.MODE_DATA_EXT
import com.machiav3lli.backup.MODE_DATA_MEDIA
import com.machiav3lli.backup.MODE_DATA_OBB
import com.machiav3lli.backup.OABX
import com.machiav3lli.backup.OABX.Companion.app
import com.machiav3lli.backup.PREFS_ASSEMBLE_FILE_ONE_STEP
import com.machiav3lli.backup.PREFS_BACKUPTARCMD
import com.machiav3lli.backup.PREFS_EXCLUDECACHE
import com.machiav3lli.backup.PREFS_FAKEBACKUPTIME
import com.machiav3lli.backup.dbs.entity.Backup
import com.machiav3lli.backup.handler.BackupBuilder
import com.machiav3lli.backup.handler.LogsHandler
import com.machiav3lli.backup.handler.ShellHandler
import com.machiav3lli.backup.handler.ShellHandler.Companion.isFileNotFoundException
import com.machiav3lli.backup.handler.ShellHandler.Companion.quote
import com.machiav3lli.backup.handler.ShellHandler.Companion.utilBoxQ
import com.machiav3lli.backup.handler.ShellHandler.ShellCommandFailedException
import com.machiav3lli.backup.items.ActionResult
import com.machiav3lli.backup.items.Package
import com.machiav3lli.backup.items.RootFile
import com.machiav3lli.backup.items.StorageFile
import com.machiav3lli.backup.tasks.AppActionWork
import com.machiav3lli.backup.utils.CIPHER_ALGORITHM
import com.machiav3lli.backup.utils.CryptoSetupException
import com.machiav3lli.backup.utils.FileUtils.BackupLocationInAccessibleException
import com.machiav3lli.backup.utils.StorageLocationNotConfiguredException
import com.machiav3lli.backup.utils.encryptStream
import com.machiav3lli.backup.utils.getCompressionLevel
import com.machiav3lli.backup.utils.getCryptoSalt
import com.machiav3lli.backup.utils.getEncryptionPassword
import com.machiav3lli.backup.utils.initIv
import com.machiav3lli.backup.utils.isCompressionEnabled
import com.machiav3lli.backup.utils.isEncryptionEnabled
import com.machiav3lli.backup.utils.isPauseApps
import com.machiav3lli.backup.utils.suAddFiles
import com.machiav3lli.backup.utils.suCopyFileToDocument
import com.topjohnwu.superuser.ShellUtils
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

val COMPRESSION_ALGORITHM = "gz"

open class BackupAppAction(context: Context, work: AppActionWork?, shell: ShellHandler) :
    BaseAppAction(context, work, shell) {

    open fun run(app: Package, backupMode: Int): ActionResult {
        var backup: Backup? = null
        var ok = false
        try {
            Timber.i("Backing up: ${app.packageName} [${app.packageLabel}]")
            //invalidateCacheForPackage(app.packageName)
            work?.setOperation("pre")
            val appBackupRoot: StorageFile = try {
                app.getAppBackupRoot(true)!!
            } catch (e: BackupLocationInAccessibleException) {
                // Usually, this should never happen, but just in case...
                val realException: Exception =
                    BackupFailedException(STORAGE_LOCATION_INACCESSIBLE, e)
                return ActionResult(
                    app,
                    null,
                    "${realException.javaClass.simpleName}: ${e.message}",
                    false
                )
            } catch (e: StorageLocationNotConfiguredException) {
                val realException: Exception =
                    BackupFailedException(STORAGE_LOCATION_INACCESSIBLE, e)
                return ActionResult(
                    app,
                    null,
                    "${realException.javaClass.simpleName}: ${e.message}",
                    false
                )
            } catch (e: Throwable) {
                LogsHandler.unhandledException(e, app)
                // Usually, this should never happen, but just in case...
                val realException: Exception =
                    BackupFailedException(STORAGE_LOCATION_INACCESSIBLE, e)
                return ActionResult(
                    app,
                    null,
                    "${realException.javaClass.simpleName}: ${e.message}",
                    false
                )
            }
            val backupBuilder = BackupBuilder(app.packageInfo, appBackupRoot)
            val iv = initIv(CIPHER_ALGORITHM) // as we're using a static Cipher Algorithm
            backupBuilder.setIv(iv)

            val backupInstanceDir = backupBuilder.backupPath
            val pauseApp = context.isPauseApps
            if (pauseApp) {
                Timber.d("pre-process package (to avoid file inconsistencies during backup etc.)")
                preprocessPackage(app.packageName)
            }

            try {
                val fakeSeconds = OABX.prefInt(PREFS_FAKEBACKUPTIME, 0)
                if (fakeSeconds > 0) {

                    val actionResult: ActionResult? = null

                    val step = 1000L * 1
                    val startTime = System.currentTimeMillis()
                    do {
                        val now = System.currentTimeMillis()
                        val seconds = (now - startTime) / 1000.0
                        work?.setOperation((seconds/10).toInt().toString().padStart(3, '0'))
                        Thread.sleep(step)
                    } while (seconds < fakeSeconds)

                    val succeeded = true // random() < 0.75

                    return if (succeeded) {
                        Timber.w("package: ${app.packageName} faking success")
                        ActionResult(app, null, "faked backup succeeded", true)
                    } else {
                        Timber.w("package: ${app.packageName} faking failure")
                        ActionResult(app, null, "faked backup failed", false)
                    }
                }

                if (backupMode and MODE_APK == MODE_APK) {
                    Timber.i("$app: Backing up package")
                    work?.setOperation("apk")
                    backupPackage(app, backupInstanceDir)
                    backupBuilder.setHasApk(true)
                }
                var backupCreated: Boolean
                if (backupMode and MODE_DATA == MODE_DATA) {
                    Timber.i("$app: Backing up data")
                    work?.setOperation("dat")
                    backupCreated = backupData(app, backupInstanceDir, iv)
                    backupBuilder.setHasAppData(backupCreated)
                }
                if (backupMode and MODE_DATA_DE == MODE_DATA_DE) {
                    Timber.i("$app: Backing up device's protected data")
                    work?.setOperation("prt")
                    backupCreated = backupDeviceProtectedData(app, backupInstanceDir, iv)
                    backupBuilder.setHasDevicesProtectedData(backupCreated)
                }
                if (backupMode and MODE_DATA_EXT == MODE_DATA_EXT) {
                    Timber.i("$app: Backing up external data")
                    work?.setOperation("ext")
                    backupCreated = backupExternalData(app, backupInstanceDir, iv)
                    backupBuilder.setHasExternalData(backupCreated)
                }
                if (backupMode and MODE_DATA_OBB == MODE_DATA_OBB) {
                    Timber.i("$app: Backing up obb files")
                    work?.setOperation("obb")
                    backupCreated = backupObbData(app, backupInstanceDir, iv)
                    backupBuilder.setHasObbData(backupCreated)
                }
                if (backupMode and MODE_DATA_MEDIA == MODE_DATA_MEDIA) {
                    Timber.i("$app: Backing up media files")
                    work?.setOperation("med")
                    backupCreated = backupMediaData(app, backupInstanceDir, iv)
                    backupBuilder.setHasMediaData(backupCreated)
                }

                if (context.isCompressionEnabled()) {
                    backupBuilder.setCompressionType(COMPRESSION_ALGORITHM)
                }
                if (context.isEncryptionEnabled()) {
                    backupBuilder.setCipherType(CIPHER_ALGORITHM)
                }
                StorageFile.cacheInvalidate(backupInstanceDir)
                val backupSize = backupInstanceDir.listFiles().sumOf { it.size }
                backupBuilder.setSize(backupSize)

                backup = backupBuilder.createBackup()

                saveBackupProperties(appBackupRoot, backup)  //TODO hg42 move to Package

                ok = true

            } catch (e: BackupFailedException) {
                Timber.e("Backup failed due to ${e.javaClass.simpleName}: ${e.message}")
                Timber.d("Backup deleted: ${backupBuilder.backupPath.delete()}")
                return ActionResult(app, null, "${e.javaClass.simpleName}: ${e.message}", false)
            } catch (e: CryptoSetupException) {
                Timber.e("Backup failed due to ${e.javaClass.simpleName}: ${e.message}")
                Timber.d("Backup deleted: ${backupBuilder.backupPath.delete()}")
                return ActionResult(app, null, "${e.javaClass.simpleName}: ${e.message}", false)
            } catch (e: IOException) {
                Timber.e("Backup failed due to ${e.javaClass.simpleName}: ${e.message}")
                Timber.d("Backup deleted: ${backupBuilder.backupPath.delete()}")
                return ActionResult(app, null, "${e.javaClass.simpleName}: ${e.message}", false)
            } catch (e: Throwable) {
                LogsHandler.unhandledException(e, app)
                Timber.e("Backup failed due to ${e.javaClass.simpleName}: ${e.message}")
                Timber.d("Backup deleted: ${backupBuilder.backupPath.delete()}")
                return ActionResult(app, null, "${e.javaClass.simpleName}: ${e.message}", false)
            } finally {
                work?.setOperation("fin")
                if (pauseApp) {
                    Timber.d("post-process package (to set it back to normal operation)")
                    postprocessPackage(app.packageName)
                }
                //invalidateCacheForPackage(app.packageName)
                if (backup == null)
                    backup = backupBuilder.createBackup()
                // TODO maybe need to handle some emergant props
                if (ok)
                    app.addBackup(backup)
                else
                    app.deleteBackup(backup)
            }
        } finally {
            work?.setOperation("end")
            Timber.i("$app: Backup done: ${backup ?: app.packageName}")
        }
        return ActionResult(app, backup, "", true)
    }

    @Throws(IOException::class)
    protected fun saveBackupProperties(
        packageBackupDir: StorageFile,
        backup: Backup
    ) {
        val propertiesFileName = String.format(
            BACKUP_INSTANCE_PROPERTIES,
            BACKUP_DATE_TIME_FORMATTER.format(backup.backupDate), backup.profileId
        )
        val propertiesFile =
            packageBackupDir.createFile("application/octet-stream", propertiesFileName)
        propertiesFile.outputStream()?.use { propertiesOut ->
            propertiesOut.write(
                backup.toJSON().toByteArray(StandardCharsets.UTF_8)
            )
        }
        Timber.i("Wrote $propertiesFile file for backup: $backup")
    }

    @Throws(IOException::class, CryptoSetupException::class)
    protected fun createBackupArchiveTarApi(
        backupInstanceDir: StorageFile,
        dataType: String,
        allFilesToBackup: List<ShellHandler.FileInfo>,
        compress: Boolean,
        iv: ByteArray?
    ) {
        val password = context.getEncryptionPassword()
        val shouldCompress = compress && context.isCompressionEnabled()

        Timber.i("Creating $dataType backup via API")
        val backupFilename = getBackupArchiveFilename(
            dataType,
            shouldCompress,
            iv != null && context.isEncryptionEnabled()
        )
        val backupFile = backupInstanceDir.createFile("application/octet-stream", backupFilename)

        var outStream: OutputStream = backupFile.outputStream()!!

        if (iv != null && password.isNotEmpty() && context.isEncryptionEnabled()) {
            outStream = outStream.encryptStream(password, context.getCryptoSalt(), iv)
        }

        if (shouldCompress) {
            val compressionLevel = context.getCompressionLevel()
            val gzipParams = GzipParameters()
            gzipParams.compressionLevel = compressionLevel

            outStream = GzipCompressorOutputStream(
                outStream,
                gzipParams
            )
        }

        try {
            TarArchiveOutputStream(outStream).use { archive ->
                archive.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                archive.suAddFiles(allFilesToBackup)
            }
        } finally {
            Timber.d("Done compressing. Closing $backupFilename")
            outStream.close()
        }
    }

    @Throws(IOException::class)
    protected fun copyToBackupArchive(
        backupInstanceDir: StorageFile,
        what: String,
        allFilesToBackup: List<ShellHandler.FileInfo>
    ) {
        val backupDir = backupInstanceDir.createDirectory(what)
        backupDir.recursiveCopyFiles(allFilesToBackup)
    }

    @Throws(BackupFailedException::class)
    protected open fun backupPackage(app: Package, backupInstanceDir: StorageFile) {
        Timber.i("[${app.packageName}] Backup package apks")
        var apksToBackup = arrayOf(app.apkPath)
        if (app.apkSplits.isEmpty()) {
            Timber.d("[${app.packageName}] The app is a normal apk")
        } else {
            apksToBackup += app.apkSplits.drop(0)
            Timber.d("[${app.packageName}] Package is split into ${apksToBackup.size} apks")
        }
        Timber.d(
            "[%s] Backing up package (%d apks: %s)",
            app.packageName,
            apksToBackup.size,
            apksToBackup.joinToString(" ") { s: String -> RootFile(s).name }
        )
        for (apk in apksToBackup) {
            try {
                Timber.i("${app.packageName}: $apk")
                suCopyFileToDocument(apk, backupInstanceDir)
            } catch (e: IOException) {
                Timber.e("$app: Could not backup apk $apk: $e")
                throw BackupFailedException("Could not backup apk $apk", e)
            } catch (e: Throwable) {
                LogsHandler.unhandledException(e, app)
                throw BackupFailedException("Could not backup apk $apk", e)
            }
        }
    }

    @Throws(BackupFailedException::class)
    private fun assembleFileListTwoStep(sourcePath: String): List<ShellHandler.FileInfo> {
        // Check what are the contents to backup. No need to start working, if the directory does not exist
        return try {
            // Get a list of directories in the directory to backup
            var dirsInSource = shell.suGetDetailedDirectoryContents(sourcePath, false, null)
            // a try to exclude google's push notifications id (hg42 it's not a directory???)
            //.filter { dir: ShellHandler.FileInfo -> !dir.filename.contains(".gms.") }

            // Excludes cache and libs, when we don't want to backup'em
            // TODO maybe remove the option and force the exclusion?
            dirsInSource = dirsInSource
                .filter { dir: ShellHandler.FileInfo -> !DATA_EXCLUDED_BASENAMES.contains(dir.filename) }
                .toList()
            if (OABX.prefFlag(PREFS_EXCLUDECACHE, true)) {
                dirsInSource = dirsInSource
                    .filter { dir: ShellHandler.FileInfo -> !DATA_EXCLUDED_CACHE_DIRS.contains(dir.filename) }
                    .toList()
            }

            // if the list is empty, there is nothing to do
            val allFilesToBackup = mutableListOf<ShellHandler.FileInfo>()
            //if (dirsInSource.isEmpty())       forEach already does the same
            //    return allFilesToBackup
            dirsInSource.forEach { dir ->
                allFilesToBackup.add(dir)
                // Do not process files in the "root" directory of the app's data
                if (dir.fileType === ShellHandler.FileInfo.FileType.DIRECTORY) try {
                    allFilesToBackup.addAll(
                        shell.suGetDetailedDirectoryContents(dir.absolutePath, true, dir.filename)
                            .filterNot { file: ShellHandler.FileInfo ->
                                file.filename in DATA_EXCLUDED_NAMES
                            }
                    )
                } catch (e: ShellCommandFailedException) {
                    if (isFileNotFoundException(e)) {
                        Timber.w("Directory has been deleted during processing: $dir")
                    }
                } catch (e: Throwable) {
                    LogsHandler.unhandledException(e, dir)
                }
            }
            allFilesToBackup
        } catch (e: ShellCommandFailedException) {
            throw BackupFailedException("Could not list contents of $sourcePath", e)
        } catch (e: Throwable) {
            LogsHandler.unhandledException(e, sourcePath)
            throw BackupFailedException("Could not list contents of $sourcePath", e)
        }
    }

    @Throws(BackupFailedException::class)
    private fun assembleFileListOneStep(sourcePath: String): List<ShellHandler.FileInfo> {
        // get and filter the whole tree at once //TODO use iterator instead of list
        return try {
            val excludeCache = OABX.prefFlag(PREFS_EXCLUDECACHE, true)
            var allFilesToBackup =
                shell.suGetDetailedDirectoryContents(sourcePath, true, sourcePath)
                    .filterNot { f: ShellHandler.FileInfo -> f.filename in DATA_EXCLUDED_BASENAMES } //TODO basenames! not all levels
                    .filterNot { f: ShellHandler.FileInfo -> f.filename in DATA_EXCLUDED_NAMES }
                    .filterNot { f: ShellHandler.FileInfo -> excludeCache && f.filename in DATA_EXCLUDED_CACHE_DIRS }
            allFilesToBackup
        } catch (e: ShellCommandFailedException) {
            throw BackupFailedException("Could not list contents of $sourcePath", e)
        } catch (e: Throwable) {
            LogsHandler.unhandledException(e, sourcePath)
            throw BackupFailedException("Could not list contents of $sourcePath", e)
        }
    }

    @Throws(BackupFailedException::class)
    private fun assembleFileList(sourcePath: String): List<ShellHandler.FileInfo> {
        return if (OABX.prefFlag(PREFS_ASSEMBLE_FILE_ONE_STEP, true))
            assembleFileListOneStep(sourcePath)
        else
            assembleFileListTwoStep(sourcePath)
    }

    @Throws(BackupFailedException::class, CryptoSetupException::class)
    protected fun genericBackupDataTarApi(
        dataType: String,
        backupInstanceDir: StorageFile,
        filesToBackup: List<ShellHandler.FileInfo>,
        compress: Boolean,
        iv: ByteArray?
    ): Boolean {
        Timber.i(
            "Backing up %s got %d files to backup",
            dataType,
            filesToBackup.size
        )
        if (filesToBackup.isEmpty()) {
            Timber.i("Nothing to backup for $dataType. Skipping")
            return false
        }
        try {
            /*
            if (compress) {
                createBackupArchiveTarApi(backupInstanceDir, dataType, filesToBackup, compress, iv)
            } else {
                copyToBackupArchive(backupInstanceDir, dataType, filesToBackup)
            }
            */
            createBackupArchiveTarApi(backupInstanceDir, dataType, filesToBackup, compress, iv)
        } catch (e: IOException) {
            val message = "${e.javaClass.canonicalName} occurred on $dataType backup: $e"
            Timber.e(message)
            throw BackupFailedException(message, e)
        } catch (e: Throwable) {
            val message = "${e.javaClass.canonicalName} occurred on $dataType backup: $e"
            LogsHandler.unhandledException(e, message)
            throw BackupFailedException(message, e)
        }
        return true
    }

    @Throws(BackupFailedException::class, CryptoSetupException::class)
    fun genericBackupDataTarApi(
        dataType: String,
        backupInstanceDir: StorageFile,
        sourcePath: String,
        compress: Boolean,
        iv: ByteArray?
    ): Boolean {
        val filesToBackup = assembleFileList(sourcePath)
        return genericBackupData(dataType, backupInstanceDir, filesToBackup, compress, iv)
    }

    @SuppressLint("RestrictedApi")
    @Throws(BackupFailedException::class, CryptoSetupException::class)
    fun genericBackupDataTarCmd(
        dataType: String,
        backupInstanceDir: StorageFile,
        sourcePath: String,
        compress: Boolean,
        iv: ByteArray?
    ): Boolean {
        if (!ShellUtils.fastCmdResult("test -d ${quote(sourcePath)}"))
            return false

        val password = context.getEncryptionPassword()
        val shouldCompress = compress && context.isCompressionEnabled()

        Timber.i("Creating $dataType backup via tar")
        val backupFilename = getBackupArchiveFilename(
            dataType,
            shouldCompress,
            iv != null && context.isEncryptionEnabled()
        )
        val backupFile = backupInstanceDir.createFile("application/octet-stream", backupFilename)

        var outStream: OutputStream = backupFile.outputStream()!!

        if (iv != null && password.isNotEmpty() && context.isEncryptionEnabled()) {
            outStream = outStream.encryptStream(password, context.getCryptoSalt(), iv)
        }

        if (shouldCompress) {
            val compressionLevel = context.getCompressionLevel()
            val gzipParams = GzipParameters()
            gzipParams.compressionLevel = compressionLevel

            outStream = GzipCompressorOutputStream(
                outStream,
                gzipParams
            )
        }

        var result = false
        try {
            val tarScript = ShellHandler.findAssetFile("tar.sh").toString()
            val exclude = ShellHandler.findAssetFile(ShellHandler.EXCLUDE_FILE).toString()
            val excludeCache =
                ShellHandler.findAssetFile(ShellHandler.EXCLUDE_CACHE_FILE).toString()

            var options = ""
            options += " --exclude ${quote(exclude)}"
            if (OABX.prefFlag(PREFS_EXCLUDECACHE, true)) {
                options += " --exclude ${quote(excludeCache)}"
            }
            var suOptions = if (ShellHandler.isMountMaster) "--mount-master" else ""

            val cmd = "su $suOptions -c sh ${quote(tarScript)} create $utilBoxQ $options ${
                quote(sourcePath)
            }"
            Timber.i("SHELL: $cmd")

            val process = Runtime.getRuntime().exec(cmd)

            val shellIn = process.outputStream
            val shellOut = process.inputStream
            val shellErr = process.errorStream

            shellOut.copyTo(outStream, 65536)

            outStream.flush()

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
            result = true
        } catch (e: Throwable) {
            val message = "${e.javaClass.canonicalName} occurred on $dataType backup: $e"
            LogsHandler.unhandledException(e, message)
            throw BackupFailedException(message, e)
        } finally {
            Timber.d("Done compressing. Closing $backupFilename")
            outStream.close()
        }
        return result
    }

    @Throws(BackupFailedException::class, CryptoSetupException::class)
    protected fun genericBackupData(
        dataType: String,
        backupInstanceDir: StorageFile,
        filesToBackup: List<ShellHandler.FileInfo>,
        compress: Boolean,
        iv: ByteArray?
    ): Boolean {
        return genericBackupDataTarApi(dataType, backupInstanceDir, filesToBackup, compress, iv)
    }

    @Throws(BackupFailedException::class, CryptoSetupException::class)
    protected fun genericBackupData(
        dataType: String,
        backupInstanceDir: StorageFile,
        sourcePath: String,
        compress: Boolean,
        iv: ByteArray?
    ): Boolean {
        Timber.i("${app.packageName} <- $sourcePath")
        if (OABX.prefFlag(PREFS_BACKUPTARCMD, true)) {
            return genericBackupDataTarCmd(
                dataType,
                backupInstanceDir,
                sourcePath,
                compress,
                iv
            )
        } else {
            return genericBackupDataTarApi(
                dataType,
                backupInstanceDir,
                sourcePath,
                compress,
                iv
            )
        }
    }

    @Throws(BackupFailedException::class, CryptoSetupException::class)
    protected open fun backupData(
        app: Package,
        backupInstanceDir: StorageFile,
        iv: ByteArray?
    ): Boolean {
        val dataType = BACKUP_DIR_DATA
        Timber.i(LOG_START_BACKUP, app.packageName, dataType)
        return genericBackupData(
            dataType,
            backupInstanceDir,
            app.dataPath,
            OABX.context.isCompressionEnabled(),
            iv
        )
    }

    @Throws(BackupFailedException::class, CryptoSetupException::class)
    protected open fun backupExternalData(
        app: Package,
        backupInstanceDir: StorageFile,
        iv: ByteArray?
    ): Boolean {
        val dataType = BACKUP_DIR_EXTERNAL_FILES
        Timber.i(LOG_START_BACKUP, app.packageName, dataType)
        return try {
            genericBackupData(
                dataType,
                backupInstanceDir,
                app.getExternalDataPath(context),
                OABX.context.isCompressionEnabled(),
                iv
            )
        } catch (ex: BackupFailedException) {
            (ex.cause as ShellCommandFailedException).let {
                if (isFileNotFoundException(it)) {
                    // no such data found
                    Timber.i(LOG_NO_THING_TO_BACKUP, dataType, app.packageName)
                    return false
                }
            }
            throw ex
        }
    }

    @Throws(BackupFailedException::class, CryptoSetupException::class)
    protected open fun backupObbData(
        app: Package,
        backupInstanceDir: StorageFile,
        iv: ByteArray?
    ): Boolean {
        val dataType = BACKUP_DIR_OBB_FILES
        Timber.i(LOG_START_BACKUP, app.packageName, dataType)
        return try {
            genericBackupData(
                dataType,
                backupInstanceDir,
                app.getObbFilesPath(context),
                OABX.context.isCompressionEnabled(),
                iv
            )
        } catch (ex: BackupFailedException) {
            (ex.cause as ShellCommandFailedException).let {
                if (isFileNotFoundException(it)) {
                    // no such data found
                    Timber.i(LOG_NO_THING_TO_BACKUP, dataType, app.packageName)
                    return false
                }
            }
            throw ex
        }
    }

    @Throws(BackupFailedException::class, CryptoSetupException::class)
    protected open fun backupMediaData(
        app: Package,
        backupInstanceDir: StorageFile,
        iv: ByteArray?
    ): Boolean {
        val dataType = BACKUP_DIR_MEDIA_FILES
        Timber.i(LOG_START_BACKUP, app.packageName, dataType)
        return try {
            genericBackupData(
                dataType,
                backupInstanceDir,
                app.getMediaFilesPath(context),
                OABX.context.isCompressionEnabled(),
                iv
            )
        } catch (ex: BackupFailedException) {
            (ex.cause as ShellCommandFailedException).let {
                if (isFileNotFoundException(it)) {
                    // no such data found
                    Timber.i(LOG_NO_THING_TO_BACKUP, dataType, app.packageName)
                    return false
                }
            }
            throw ex
        }
    }

    @Throws(BackupFailedException::class, CryptoSetupException::class)
    protected open fun backupDeviceProtectedData(
        app: Package,
        backupInstanceDir: StorageFile,
        iv: ByteArray?
    ): Boolean {
        val dataType = BACKUP_DIR_DEVICE_PROTECTED_FILES
        Timber.i(LOG_START_BACKUP, app.packageName, dataType)
        return try {
            genericBackupData(
                dataType,
                backupInstanceDir,
                app.devicesProtectedDataPath,
                OABX.context.isCompressionEnabled(),
                iv
            )
        } catch (ex: BackupFailedException) {
            (ex.cause as ShellCommandFailedException).let {
                if (isFileNotFoundException(it)) {
                    // no such data found
                    Timber.i(LOG_NO_THING_TO_BACKUP, dataType, app.packageName)
                    return false
                }
            }
            throw ex
        }
    }

    class BackupFailedException(message: String?, cause: Throwable?) :
        AppActionFailedException(message, cause)

    companion object {
        const val LOG_START_BACKUP = "[%s] Starting %s backup"
        const val LOG_NO_THING_TO_BACKUP = "[%s] No %s to backup available"
        const val STORAGE_LOCATION_INACCESSIBLE =
            "Cannot backup data. Storage location not set or inaccessible"
    }
}
