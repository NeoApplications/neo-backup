package com.machiav3lli.backup.preferences

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.machiav3lli.backup.BACKUP_DIRECTORY_INTENT
import com.machiav3lli.backup.PREFS_ACCENT_COLOR_X
import com.machiav3lli.backup.PREFS_BIOMETRICLOCK
import com.machiav3lli.backup.PREFS_DEVICELOCK
import com.machiav3lli.backup.PREFS_LANGUAGES
import com.machiav3lli.backup.PREFS_LOADINGTOASTS
import com.machiav3lli.backup.PREFS_OLDBACKUPS
import com.machiav3lli.backup.PREFS_PATH_BACKUP_DIRECTORY
import com.machiav3lli.backup.PREFS_REMEMBERFILTERING
import com.machiav3lli.backup.PREFS_SECONDARY_COLOR_X
import com.machiav3lli.backup.PREFS_THEME_X
import com.machiav3lli.backup.R
import com.machiav3lli.backup.accentColorItems
import com.machiav3lli.backup.dialogs.BaseDialog
import com.machiav3lli.backup.dialogs.EnumDialogUI
import com.machiav3lli.backup.dialogs.ListDialogUI
import com.machiav3lli.backup.secondaryColorItems
import com.machiav3lli.backup.themeItems
import com.machiav3lli.backup.ui.compose.item.EnumPreference
import com.machiav3lli.backup.ui.compose.item.LaunchPreference
import com.machiav3lli.backup.ui.compose.item.ListPreference
import com.machiav3lli.backup.ui.compose.item.SeekBarPreference
import com.machiav3lli.backup.ui.compose.item.SwitchPreference
import com.machiav3lli.backup.ui.compose.theme.AppTheme
import com.machiav3lli.backup.ui.compose.theme.ColorDeData
import com.machiav3lli.backup.ui.compose.theme.ColorExodus
import com.machiav3lli.backup.ui.compose.theme.ColorExtDATA
import com.machiav3lli.backup.ui.compose.theme.ColorOBB
import com.machiav3lli.backup.ui.compose.theme.ColorSpecial
import com.machiav3lli.backup.ui.compose.theme.ColorUpdated
import com.machiav3lli.backup.ui.item.Pref
import com.machiav3lli.backup.utils.StorageLocationNotConfiguredException
import com.machiav3lli.backup.utils.backupDirConfigured
import com.machiav3lli.backup.utils.getLanguageList
import com.machiav3lli.backup.utils.isBiometricLockAvailable
import com.machiav3lli.backup.utils.isDeviceLockAvailable
import com.machiav3lli.backup.utils.isDeviceLockEnabled
import com.machiav3lli.backup.utils.restartApp
import com.machiav3lli.backup.utils.setBackupDir
import com.machiav3lli.backup.utils.setCustomTheme
import timber.log.Timber

@Composable
fun UserPrefsPage() {
    val context = LocalContext.current
    val openDialog = remember { mutableStateOf(false) }
    var dialogsPref by remember { mutableStateOf<Pref?>(null) }
    var backupDir by remember { mutableStateOf(context.backupDirConfigured) }
    var isDeviceLockEnabled by remember { mutableStateOf(context.isDeviceLockEnabled()) }
    val prefs = listOf(
        context.LanguagePref,
        ThemePref,
        AccentColorPref,
        SecondaryColorPref,
        BackupFolderPref,
        LoadingToastsPref,
        DeviceLockPref,
        BiometricLockPref,
        DaysOldPref,
        RememberFilterPref
    )
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.data != null && result.resultCode == Activity.RESULT_OK) {
                result.data?.let {
                    val uri = it.data ?: return@let
                    val oldDir = try {
                        context.backupDirConfigured
                    } catch (e: StorageLocationNotConfiguredException) {
                        "" // Can be ignored, this is about to set the path
                    }
                    if (oldDir != uri.toString()) {
                        val flags = it.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        context.contentResolver.takePersistableUriPermission(uri, flags)
                        Timber.i("setting uri $uri")
                        backupDir = context.setBackupDir(uri)
                    }
                }
            }
        }

    AppTheme(
        darkTheme = isSystemInDarkTheme()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = prefs) { pref ->
                when (pref) {
                    DeviceLockPref -> SwitchPreference(
                        pref = pref as Pref.BooleanPref,
                        isEnabled = context.isDeviceLockAvailable()
                    ) {
                        isDeviceLockEnabled = it
                    }
                    BiometricLockPref -> SwitchPreference(
                        pref = pref as Pref.BooleanPref,
                        isEnabled = context.isBiometricLockAvailable() && isDeviceLockEnabled
                    )
                    is Pref.ListPref -> ListPreference(pref = pref) {
                        dialogsPref = pref
                        openDialog.value = true
                    }
                    ThemePref, AccentColorPref, SecondaryColorPref -> EnumPreference(pref = pref as Pref.EnumPref) {
                        dialogsPref = pref
                        openDialog.value = true
                    }
                    BackupFolderPref -> LaunchPreference(pref = pref, summary = backupDir) {
                        launcher.launch(BACKUP_DIRECTORY_INTENT)
                    }
                    is Pref.BooleanPref -> SwitchPreference(pref = pref)
                    is Pref.IntPref -> SeekBarPreference(pref = pref)
                }
            }
        }

        if (openDialog.value) {
            BaseDialog(openDialogCustom = openDialog) {
                when (dialogsPref) {
                    is Pref.ListPref -> ListDialogUI(
                        pref = dialogsPref as Pref.ListPref,
                        openDialogCustom = openDialog,
                        onChanged = { context.restartApp() }
                    )
                    ThemePref, AccentColorPref, SecondaryColorPref -> EnumDialogUI(
                        pref = dialogsPref as Pref.EnumPref,
                        openDialogCustom = openDialog,
                        onChanged = {
                            context.setCustomTheme()
                            context.restartApp()
                        }
                    )
                }
            }
        }
    }
}

val Context.LanguagePref: Pref.ListPref
    get() = Pref.ListPref(
        key = PREFS_LANGUAGES,
        titleId = R.string.prefs_languages,
        iconId = R.drawable.ic_languages,
        iconTint = ColorOBB,
        entries = getLanguageList(),
        defaultValue = "system"
    )

val ThemePref = Pref.EnumPref(
    key = PREFS_THEME_X,
    titleId = R.string.prefs_theme,
    iconId = R.drawable.ic_theme,
    iconTint = ColorSpecial,
    entries = themeItems,
    defaultValue = 0
)

val AccentColorPref = Pref.EnumPref(
    key = PREFS_ACCENT_COLOR_X,
    titleId = R.string.prefs_accent_color,
    iconId = R.drawable.ic_color_accent,
    //iconTint = MaterialTheme.colorScheme.primary,
    entries = accentColorItems,
    defaultValue = 0
)

val SecondaryColorPref = Pref.EnumPref(
    key = PREFS_SECONDARY_COLOR_X,
    titleId = R.string.prefs_secondary_color,
    iconId = R.drawable.ic_color_secondary,
    //iconTint = MaterialTheme.colorScheme.secondary,
    entries = secondaryColorItems,
    defaultValue = 0
)

val BackupFolderPref = Pref.StringPref(
    key = PREFS_PATH_BACKUP_DIRECTORY,
    titleId = R.string.prefs_pathbackupfolder,
    iconId = R.drawable.ic_folder,
    iconTint = ColorExtDATA,
    defaultValue = ""
)

val LoadingToastsPref = Pref.BooleanPref(
    key = PREFS_LOADINGTOASTS,
    titleId = R.string.prefs_loadingtoasts,
    summaryId = R.string.prefs_loadingtoasts_summary,
    iconId = R.drawable.ic_label,
    defaultValue = false
)

val DeviceLockPref = Pref.BooleanPref(
    key = PREFS_DEVICELOCK,
    titleId = R.string.prefs_devicelock,
    summaryId = R.string.prefs_devicelock_summary,
    iconId = R.drawable.ic_encryption,
    iconTint = ColorUpdated,
    defaultValue = false
)

val BiometricLockPref = Pref.BooleanPref(
    key = PREFS_BIOMETRICLOCK,
    titleId = R.string.prefs_biometriclock,
    summaryId = R.string.prefs_biometriclock_summary,
    iconId = R.drawable.ic_biometric,
    iconTint = ColorDeData,
    defaultValue = false
)

val DaysOldPref = Pref.IntPref(
    key = PREFS_OLDBACKUPS,
    titleId = R.string.prefs_oldbackups,
    summaryId = R.string.prefs_oldbackups_summary,
    iconId = R.drawable.ic_old,
    iconTint = ColorExodus,
    entries = (1..30).toList(),
    defaultValue = 2
)

val RememberFilterPref = Pref.BooleanPref(
    key = PREFS_REMEMBERFILTERING,
    titleId = R.string.prefs_rememberfiltering,
    summaryId = R.string.prefs_rememberfiltering_summary,
    iconId = R.drawable.ic_filter,
    defaultValue = false
)
