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
import android.content.pm.PackageManager
import android.os.Build
import com.machiav3lli.backup.BuildConfig
import com.machiav3lli.backup.OABX
import com.machiav3lli.backup.PREFS_PMSUSPEND
import com.machiav3lli.backup.handler.LogsHandler
import com.machiav3lli.backup.handler.ShellHandler
import com.machiav3lli.backup.handler.ShellHandler.Companion.runAsRoot
import com.machiav3lli.backup.handler.ShellHandler.Companion.utilBox
import com.machiav3lli.backup.handler.ShellHandler.Companion.utilBoxQ
import com.machiav3lli.backup.handler.ShellHandler.ShellCommandFailedException
import com.machiav3lli.backup.tasks.AppActionWork
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import timber.log.Timber

abstract class BaseAppAction protected constructor(
    protected val context: Context,
    protected val work: AppActionWork?,
    protected val shell: ShellHandler
) {

    protected val deviceProtectedStorageContext: Context =
        context.createDeviceProtectedStorageContext()

    fun getBackupArchiveFilename(
        what: String,
        isCompressed: Boolean,
        isEncrypted: Boolean
    ): String {
        return "$what.tar${if (isCompressed) ".gz" else ""}${if (isEncrypted) ".enc" else ""}"
    }

    abstract class AppActionFailedException : Exception {
        protected constructor(message: String?) : super(message)
        protected constructor(message: String?, cause: Throwable?) : super(message, cause)
    }

    private fun prepostOptions(): String = if (OABX.prefFlag(PREFS_PMSUSPEND, false))
        "--suspend"
    else
        ""

    open fun preprocessPackage(packageName: String) {
        try {
            val applicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val script = ShellHandler.findAssetFile("package.sh").toString()
            Timber.w("---------------------------------------- Preprocess package $packageName uid ${applicationInfo.uid}")
            if (applicationInfo.uid < android.os.Process.FIRST_APPLICATION_UID) { // exclude several system users, e.g. system, radio
                Timber.w("Ignore processes of system user UID < ${android.os.Process.FIRST_APPLICATION_UID}")
                return
            }
            if (!packageName.matches(doNotStop)) { // will stop most activity, needs a good blacklist
                val shellResult =
                    runAsRoot("sh $script pre $utilBoxQ ${prepostOptions()} $packageName ${applicationInfo.uid}")
                stopped[packageName] = shellResult.out.asSequence()
                    .filter { line: String -> line.isNotEmpty() }
                    .toMutableList()
                Timber.w("$packageName pids: ${stopped[packageName]?.joinToString(" ")}")
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.w("$packageName does not exist. Cannot preprocess!")
        } catch (e: ShellCommandFailedException) {
            Timber.w("Could not stop package ${packageName}: ${e.shellResult.err.joinToString(" ")}")
        } catch (e: Throwable) {
            LogsHandler.unhandledException(e)
        }
    }

    open fun postprocessPackage(packageName: String) {
        try {
            val applicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val script = ShellHandler.findAssetFile("package.sh").toString()
            Timber.w("........................................ Postprocess package $packageName uid ${applicationInfo.uid}")
            if (applicationInfo.uid < android.os.Process.FIRST_APPLICATION_UID) { // exclude several system users, e.g. system, radio
                Timber.w("Ignore processes of system user UID < ${android.os.Process.FIRST_APPLICATION_UID}")
                return
            }
            stopped[packageName]?.let { pids ->
                Timber.w("Continue stopped PIDs for package ${packageName}: ${pids.joinToString(" ")}")
                runAsRoot(
                    "sh $script post $utilBoxQ ${prepostOptions()} $packageName ${applicationInfo.uid} ${
                        pids.joinToString(
                            " "
                        )
                    }"
                )
                stopped.remove(packageName)
            } ?: run {
                Timber.w("No stopped PIDs for package $packageName")
                runAsRoot("sh $script $packageName ${applicationInfo.uid}")
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.w("$packageName does not exist. Cannot post-process!")
        } catch (e: ShellCommandFailedException) {
            Timber.w("Could not continue package ${packageName}: ${e.shellResult.err.joinToString(" ")}")
        } catch (e: Throwable) {
            LogsHandler.unhandledException(e)
        }
    }

    class ScriptException(text: String) :
        AppActionFailedException(text)

    companion object {
        const val BACKUP_DIR_DATA = "data"
        const val BACKUP_DIR_DEVICE_PROTECTED_FILES = "device_protected_files"
        const val BACKUP_DIR_EXTERNAL_FILES = "external_files"
        const val BACKUP_DIR_OBB_FILES = "obb_files"
        const val BACKUP_DIR_MEDIA_FILES = "media_files"

        /* @hg42 why exclude lib? how is it restored?
           @machiav3lli libs are generally created while installing the app. Backing them up
           would result a compatibility problem between devices with different cpu_arch
         */
        val DATA_EXCLUDED_CACHE_DIRS = listOf(
            "cache",
            "code_cache"
        )
        val DATA_EXCLUDED_BASENAMES = listOf(
            "lib",      //TODO hg42 what about architecture dependent names? or may be application specific? lib* ???
            "no_backup" //TODO hg42 use Context.getNoBackupFilesDir() ??? tricky, because it's an absolute path (remove common part...)
        )
        val DATA_EXCLUDED_NAMES = listOfNotNull(
            "com.google.android.gms.appid.xml",
            "cache",
            "trash",
            ".thumbnails",
            if (utilBox.hasBugDotDotDir) "..*" else null
        )

        val ignoredPackages = ("""(?x)
              android
            | ^com\.(google\.)?android\.shell
            | ^com\.(google\.)?android\.systemui
            | ^com\.(google\.)?android\.externalstorage
            | ^com\.(google\.)?android\.mtp
            | ^com\.(google\.)?android\.providers\.downloads\.ui
            | ^com\.(google\.)?android\.gms
            | ^com\.(google\.)?android\.gsf
            | ^com\.(google\.)?android\.providers\.media\b.*
            | """ + Regex.escape(BuildConfig.APPLICATION_ID) + """
            """).toRegex()

        val doNotStop = ("""(?x)
              android
            | ^com\.(google\.)?android\.shell
            | ^com\.(google\.)?android\.systemui
            | ^com\.(google\.)?android\.externalstorage
            | ^com\.(google\.)?android\.mtp
            | ^com\.(google\.)?android\.providers\.downloads\.ui
            | ^com\.(google\.)?android\.gms
            | ^com\.(google\.)?android\.gsf
            | ^com\.(google\.)?android\.providers\.media\b.*
            | ^com\.(google\.)?android\.providers\..*
            | ^com\.topjohnwu\.magisk
            | """ + Regex.escape(BuildConfig.APPLICATION_ID) + """
            """).toRegex()

        private val stopped = mutableMapOf<String, List<String>>()

        fun extractErrorMessage(shellResult: Shell.Result): String {
            // if stderr does not say anything, try stdout
            val err = if (shellResult.err.isEmpty()) shellResult.out else shellResult.err
            return if (err.isEmpty()) {
                "Unknown Error"
            } else err[err.size - 1]
        }

        fun isSuspended(packageName: String): Boolean {
            return ShellUtils.fastCmdResult("pm dump $packageName | grep suspended=true")
        }

        fun cleanupSuspended(packageName: String) {
            Timber.i("cleanup $packageName")
            try {
                runAsRoot("pm dump $packageName | grep suspended=true && pm unsuspend ${packageName}")
            } catch (e: Throwable) {
            }
        }
    }
}