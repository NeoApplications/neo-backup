package com.machiav3lli.backup.preferences

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.machiav3lli.backup.BACKUP_DATE_TIME_FORMATTER
import com.machiav3lli.backup.OABX
import com.machiav3lli.backup.PREFS_BATCH_DELETE
import com.machiav3lli.backup.PREFS_COPYSELF
import com.machiav3lli.backup.PREFS_LOGVIEWER
import com.machiav3lli.backup.PREFS_SAVEAPPSLIST
import com.machiav3lli.backup.PREFS_SCHEDULESEXPORTIMPORT
import com.machiav3lli.backup.R
import com.machiav3lli.backup.activities.PrefsActivityX
import com.machiav3lli.backup.handler.BackupRestoreHelper
import com.machiav3lli.backup.handler.showNotification
import com.machiav3lli.backup.items.Package
import com.machiav3lli.backup.ui.compose.item.LaunchPreference
import com.machiav3lli.backup.ui.compose.navigation.NavItem
import com.machiav3lli.backup.ui.compose.theme.AppTheme
import com.machiav3lli.backup.ui.compose.theme.ColorDeData
import com.machiav3lli.backup.ui.compose.theme.ColorExodus
import com.machiav3lli.backup.ui.compose.theme.ColorExtDATA
import com.machiav3lli.backup.ui.item.Pref
import com.machiav3lli.backup.utils.FileUtils.invalidateBackupLocation
import com.machiav3lli.backup.utils.applyFilter
import com.machiav3lli.backup.utils.getBackupDir
import com.machiav3lli.backup.utils.show
import com.machiav3lli.backup.utils.sortFilterModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsPrefsPage(navController: NavHostController) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val prefs = listOf(
        CleanupBackupFolderPref,
        CopySelfPref,
        ExportImportSchedulesPref,
        SaveAppsListPref,
        LogViewerPref
    )

    AppTheme(
        darkTheme = isSystemInDarkTheme()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = prefs) { pref ->
                    LaunchPreference(pref = pref) {
                        when (pref) {
                            // TODO use only compose dialogs
                            CleanupBackupFolderPref -> context.onClickUninstalledBackupsDelete(
                                snackbarHostState,
                                coroutineScope
                            )
                            CopySelfPref -> context.onClickCopySelf(
                                snackbarHostState,
                                coroutineScope
                            )
                            ExportImportSchedulesPref -> navController.navigate(NavItem.Exports.destination)
                            SaveAppsListPref -> context.onClickSaveAppsList(
                                snackbarHostState,
                                coroutineScope
                            )
                            LogViewerPref -> navController.navigate(NavItem.Logs.destination)
                        }
                    }
                }
            }
        }
    }
}

val CleanupBackupFolderPref = Pref.LinkPref(
    key = PREFS_BATCH_DELETE,
    titleId = R.string.prefs_batchdelete,
    summaryId = R.string.prefs_batchdelete_summary,
    iconId = R.drawable.ic_delete,
    //iconTint = MaterialTheme.colorScheme.secondary
)

private fun Context.onClickUninstalledBackupsDelete(
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope
): Boolean {
    val deleteList = ArrayList<Package>()
    val message = StringBuilder()
    val packageList = OABX.main?.viewModel?.packageList?.value ?: emptyList()
    if (packageList.isNotEmpty()) {
        packageList.forEach { appInfo ->
            if (!appInfo.isInstalled) {
                deleteList.add(appInfo)
                message.append(appInfo.packageLabel).append("\n")
            }
        }
    }
    if (packageList.isNotEmpty()) {
        if (deleteList.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.prefs_batchdelete)
                .setMessage(message.toString().trim { it <= ' ' })
                .setPositiveButton(R.string.dialogYes) { _: DialogInterface?, _: Int ->
                    deleteBackups(deleteList)
                    invalidateBackupLocation()
                }
                .setNegativeButton(R.string.dialogNo, null)
                .show()
        } else {
            snackbarHostState.show(
                coroutineScope,
                getString(R.string.batchDeleteNothingToDelete)
            )
        }
    } else {
        snackbarHostState.show(
            coroutineScope,
            getString(R.string.wait_noappslist),
        )
    }
    return true
}

private fun Context.deleteBackups(deleteList: List<Package>) {
    val notificationId = System.currentTimeMillis().toInt()
    deleteList.forEachIndexed { i, ai ->
        showNotification(
            this,
            PrefsActivityX::class.java,
            notificationId,
            "${getString(R.string.batchDeleteMessage)} ($i/${deleteList.size})",
            ai.packageLabel,
            false
        )
        Timber.i("deleting backups of ${ai.packageLabel}")
        ai.deleteAllBackups()
    }
    showNotification(
        this,
        PrefsActivityX::class.java,
        notificationId,
        getString(R.string.batchDeleteNotificationTitle),
        "${getString(R.string.batchDeleteBackupsDeleted)} ${deleteList.size}",
        false
    )
}

val CopySelfPref = Pref.LinkPref(
    key = PREFS_COPYSELF,
    titleId = R.string.prefs_copyselfapk,
    summaryId = R.string.prefs_copyselfapk_summary,
    iconId = R.drawable.ic_andy,
    //iconTint = MaterialTheme.colorScheme.primary
)

private fun Context.onClickCopySelf(
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope
): Boolean {
    try {
        GlobalScope.launch(Dispatchers.IO) {
            if (BackupRestoreHelper.copySelfApk(
                    this@onClickCopySelf,
                    OABX.shellHandlerInstance!!
                )
            ) {
                showNotification(
                    this@onClickCopySelf,
                    PrefsActivityX::class.java,
                    System.currentTimeMillis().toInt(),
                    getString(R.string.copyOwnApkSuccess),
                    "",
                    false
                )
                snackbarHostState.show(
                    coroutineScope,
                    getString(R.string.copyOwnApkSuccess)
                )
            } else {
                showNotification(
                    this@onClickCopySelf,
                    PrefsActivityX::class.java,
                    System.currentTimeMillis().toInt(),
                    getString(R.string.copyOwnApkFailed),
                    "",
                    false
                )
                snackbarHostState.show(
                    coroutineScope,
                    getString(R.string.copyOwnApkFailed)
                )
            }
        }
    } catch (e: IOException) {
        Timber.e("${getString(R.string.copyOwnApkFailed)}: $e")
    } finally {
        return true
    }
}

val ExportImportSchedulesPref = Pref.LinkPref(
    key = PREFS_SCHEDULESEXPORTIMPORT,
    titleId = R.string.prefs_schedulesexportimport,
    summaryId = R.string.prefs_schedulesexportimport_summary,
    iconId = R.drawable.ic_scheduler,
    iconTint = ColorExtDATA
)

val SaveAppsListPref = Pref.LinkPref(
    key = PREFS_SAVEAPPSLIST,
    titleId = R.string.prefs_saveappslist,
    summaryId = R.string.prefs_saveappslist_summary,
    iconId = R.drawable.ic_list_ordered,
    iconTint = ColorExodus
)


private fun Context.onClickSaveAppsList(
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope
): Boolean {
    val packageList = OABX.main?.viewModel?.packageList?.value ?: emptyList()
    if (packageList.isNotEmpty()) {
        AlertDialog.Builder(this)
            .setTitle(R.string.prefs_saveappslist)
            .setPositiveButton(R.string.radio_all) { _: DialogInterface, _: Int ->
                writeAppsListFile(packageList
                    .filter { it.isSystem }
                    .map { "${it.packageLabel}: ${it.packageName} @ ${it.versionName}" },
                    false  //TODO hg42 name first because of ":", better for scripts
                )
            }
            .setNeutralButton(R.string.filtered_list) { _: DialogInterface, _: Int ->
                writeAppsListFile(
                    packageList.applyFilter(sortFilterModel, this)
                        .map { "${it.packageLabel}: ${it.packageName} @ ${it.versionName}" },
                    true
                )
            }
            .setNegativeButton(R.string.dialogNo, null)
            .show()
    } else {
        snackbarHostState.show(
            coroutineScope,
            getString(R.string.wait_noappslist),
        )
    }
    return true
}

@Throws(IOException::class)
fun Context.writeAppsListFile(appsList: List<String>, filteredBoolean: Boolean) {
    val date = LocalDateTime.now()
    val filesText = appsList.joinToString("\n")
    val fileName = "${BACKUP_DATE_TIME_FORMATTER.format(date)}.appslist"
    val listFile = getBackupDir().createFile("application/octet-stream", fileName)
    BufferedOutputStream(listFile.outputStream())
        .use { it.write(filesText.toByteArray(StandardCharsets.UTF_8)) }
    showNotification(
        this, PrefsActivityX::class.java, System.currentTimeMillis().toInt(),
        getString(
            if (filteredBoolean) R.string.write_apps_list_filtered
            else R.string.write_apps_list_all
        ), null, false
    )
    Timber.i("Wrote apps\' list file at $date")
}


val LogViewerPref = Pref.LinkPref(
    key = PREFS_LOGVIEWER,
    titleId = R.string.prefs_logviewer,
    iconId = R.drawable.ic_log,
    iconTint = ColorDeData
)
