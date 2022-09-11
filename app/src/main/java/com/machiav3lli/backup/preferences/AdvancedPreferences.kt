package com.machiav3lli.backup.preferences

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.machiav3lli.backup.MAIN_FILTER_DEFAULT
import com.machiav3lli.backup.PREFS_ALLOWDOWNGRADE
import com.machiav3lli.backup.PREFS_ALLOWSHADOWINGDEFAULT
import com.machiav3lli.backup.PREFS_ASSEMBLEFILELISTONESTEP
import com.machiav3lli.backup.PREFS_BACKUPTARCMD
import com.machiav3lli.backup.PREFS_CACHEFILELISTS
import com.machiav3lli.backup.PREFS_CACHEONUPDATE
import com.machiav3lli.backup.PREFS_CACHEPACKAGES
import com.machiav3lli.backup.PREFS_CACHEURIS
import com.machiav3lli.backup.PREFS_CANCELONSTART
import com.machiav3lli.backup.PREFS_CATCHUNCAUGHTEXCEPTION
import com.machiav3lli.backup.PREFS_COLUMNNAMESAF
import com.machiav3lli.backup.PREFS_DELAYBEFOREREFRESHAPPINFO
import com.machiav3lli.backup.PREFS_DISABLEVERIFICATION
import com.machiav3lli.backup.PREFS_ENABLESPECIALBACKUPS
import com.machiav3lli.backup.PREFS_FAKEBACKUPSECONDS
import com.machiav3lli.backup.PREFS_FINDLS
import com.machiav3lli.backup.PREFS_INVALIDATESELECTIVE
import com.machiav3lli.backup.PREFS_MAXCRASHLINES
import com.machiav3lli.backup.PREFS_MAXRETRIESPERPACKAGE
import com.machiav3lli.backup.PREFS_PAUSEAPPS
import com.machiav3lli.backup.PREFS_PMSUSPEND
import com.machiav3lli.backup.PREFS_REFRESHAPPINFOTIMEOUT
import com.machiav3lli.backup.PREFS_RESTOREAVOIDTEMPCOPY
import com.machiav3lli.backup.PREFS_RESTORETARCMD
import com.machiav3lli.backup.PREFS_RESTOREWITHALLPERMISSIONS
import com.machiav3lli.backup.PREFS_SHADOWROOTFILE
import com.machiav3lli.backup.PREFS_SHOW_INFO_LOG
import com.machiav3lli.backup.PREFS_STRICTHARDLINKS
import com.machiav3lli.backup.PREFS_USEALARMCLOCK
import com.machiav3lli.backup.PREFS_USEEXACTALARM
import com.machiav3lli.backup.PREFS_USEEXPEDITED
import com.machiav3lli.backup.PREFS_USEFOREGROUND
import com.machiav3lli.backup.R
import com.machiav3lli.backup.ui.compose.item.PreferencesGroupHeader
import com.machiav3lli.backup.ui.compose.item.SeekBarPreference
import com.machiav3lli.backup.ui.compose.item.SwitchPreference
import com.machiav3lli.backup.ui.compose.theme.AppTheme
import com.machiav3lli.backup.ui.compose.theme.ColorDeData
import com.machiav3lli.backup.ui.compose.theme.ColorSpecial
import com.machiav3lli.backup.ui.compose.theme.ColorUpdated
import com.machiav3lli.backup.ui.item.Pref
import com.machiav3lli.backup.utils.sortFilterModel

@Composable
fun AdvancedPrefsPage() {
    val context = LocalContext.current
    var (expanded, expand) = remember { mutableStateOf(false) }
    val prefs = listOf<Pref>(
        EnableSpecialsPref,
        DisableVerificationPref,
        RestoreAllPermissionsPref,
        AllowDowngradePref
    )
    val devOptions = listOf(
        ShowInfoLogBarPref,
        CachePackagePref,
        UsePackageCacheOnUpdatePref,
        UseColumnNameSAFPref,
        CancelOnStartPref,
        UseAlarmClockPref,
        UseExactAlarmPref,
        PauseAppPref,
        SuspendAppPref,
        BackupTarCmdPref,
        RestoreTarCmdPref,
        StrictHardLinksPref,
        RestoreAvoidTempCopyPref,
        ShadowRootFilePref,
        AllowShadowingDefaultPref,
        UseFindLsPref,
        AssembleFileListOneStepPref,
        CatchUncaughtExceptionPref,
        MaxCrashLinesPref,
        InvalidateSelectivePref,
        CacheUrisPref,
        CacheFileListsPref,
        MaxRetriesPerPackagePref,
        DelayBeforeRefreshAppInfoPref,
        RefreshAppInfoTimeoutPref,
        UseForegroundPref,
        UseExpeditedPref,
        FakeBackupSecondsPref
    )

    AppTheme(
        darkTheme = isSystemInDarkTheme()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = prefs) {
                when (it) {
                    EnableSpecialsPref -> SwitchPreference(pref = it as Pref.BooleanPref) {
                        val newModel = context.sortFilterModel
                        newModel.mainFilter = newModel.mainFilter and MAIN_FILTER_DEFAULT
                        context.sortFilterModel = newModel
                    }
                    is Pref.BooleanPref -> SwitchPreference(pref = it)
                    is Pref.IntPref -> SeekBarPreference(pref = it)
                }
            }
            item {
                PreferencesGroupHeader(
                    titleId = R.string.prefs_dev_settings,
                    summaryId = R.string.prefs_dev_settings_summary,
                    iconId = R.drawable.ic_force_kill
                ) {
                    expand(!expanded)
                }
            }
            // TODO add Dev options expandable holder
            if (expanded) items(items = devOptions) {
                when (it) {
                    is Pref.BooleanPref -> SwitchPreference(pref = it)
                    is Pref.IntPref -> SeekBarPreference(pref = it)
                }
            }
        }
    }
}

val EnableSpecialsPref = Pref.BooleanPref(
    key = PREFS_ENABLESPECIALBACKUPS,
    titleId = R.string.prefs_enablespecial,
    summaryId = R.string.prefs_enablespecial_summary,
    iconId = R.drawable.ic_special,
    iconTint = ColorSpecial,
    defaultValue = false
)

val DisableVerificationPref = Pref.BooleanPref(
    key = PREFS_DISABLEVERIFICATION,
    titleId = R.string.prefs_disableverification,
    summaryId = R.string.prefs_disableverification_summary,
    iconId = R.drawable.ic_andy,
    iconTint = ColorUpdated,
    defaultValue = true
)

val RestoreAllPermissionsPref = Pref.BooleanPref(
    key = PREFS_RESTOREWITHALLPERMISSIONS,
    titleId = R.string.prefs_restoreallpermissions,
    summaryId = R.string.prefs_restoreallpermissions_summary,
    iconId = R.drawable.ic_de_data,
    iconTint = ColorDeData,
    defaultValue = false
)

val AllowDowngradePref = Pref.BooleanPref(
    key = PREFS_ALLOWDOWNGRADE,
    titleId = R.string.prefs_allowdowngrade,
    summaryId = R.string.prefs_allowdowngrade_summary,
    iconId = R.drawable.ic_restore,
    defaultValue = false
)


// dev settings

val ShowInfoLogBarPref = Pref.BooleanPref(
    key = PREFS_SHOW_INFO_LOG,
    titleId = R.string.prefs_showinfologbar,
    summaryId = R.string.prefs_showinfologbar_summary,
    defaultValue = false
)

val CachePackagePref = Pref.BooleanPref(
    key = PREFS_CACHEPACKAGES,
    titleId = R.string.prefs_cachepackages,
    summaryId = R.string.prefs_cachepackages_summary,
    defaultValue = true
)

val UsePackageCacheOnUpdatePref = Pref.BooleanPref(
    key = PREFS_CACHEONUPDATE,
    titleId = R.string.prefs_usepackagecacheonupdate,
    summaryId = R.string.prefs_usepackagecacheonupdate_summary,
    defaultValue = false
)

val UseColumnNameSAFPref = Pref.BooleanPref(
    key = PREFS_COLUMNNAMESAF,
    titleId = R.string.prefs_usecolumnnamesaf,
    summaryId = R.string.prefs_usecolumnnamesaf_summary,
    defaultValue = true
)

val CancelOnStartPref = Pref.BooleanPref(
    key = PREFS_CANCELONSTART,
    titleId = R.string.prefs_cancelonstart,
    summaryId = R.string.prefs_cancelonstart_summary,
    defaultValue = false
)

val UseAlarmClockPref = Pref.BooleanPref(
    key = PREFS_USEALARMCLOCK,
    titleId = R.string.prefs_usealarmclock,
    summaryId = R.string.prefs_usealarmclock_summary,
    defaultValue = false
)

val UseExactAlarmPref = Pref.BooleanPref(
    key = PREFS_USEEXACTALARM,
    titleId = R.string.prefs_useexactalarm,
    summaryId = R.string.prefs_useexactalarm_summary,
    defaultValue = false
)

val PauseAppPref = Pref.BooleanPref(
    key = PREFS_PAUSEAPPS,
    titleId = R.string.prefs_pauseapps,
    summaryId = R.string.prefs_pauseapps_summary,
    defaultValue = true
)

val SuspendAppPref = Pref.BooleanPref(
    key = PREFS_PMSUSPEND,
    titleId = R.string.prefs_pmsuspend,
    summaryId = R.string.prefs_pmsuspend_summary,
    defaultValue = false
)

val BackupTarCmdPref = Pref.BooleanPref(
    key = PREFS_BACKUPTARCMD,
    titleId = R.string.prefs_backuptarcmd,
    summaryId = R.string.prefs_backuptarcmd_summary,
    defaultValue = true
)

val RestoreTarCmdPref = Pref.BooleanPref(
    key = PREFS_RESTORETARCMD,
    titleId = R.string.prefs_restoretarcmd,
    summaryId = R.string.prefs_restoretarcmd_summary,
    defaultValue = true
)

val StrictHardLinksPref = Pref.BooleanPref(
    key = PREFS_STRICTHARDLINKS,
    titleId = R.string.prefs_stricthardlinks,
    summaryId = R.string.prefs_stricthardlinks_summary,
    defaultValue = false
)

val RestoreAvoidTempCopyPref = Pref.BooleanPref(
    key = PREFS_RESTOREAVOIDTEMPCOPY,
    titleId = R.string.prefs_restoreavoidtempcopy,
    summaryId = R.string.prefs_restoreavoidtempcopy_summary,
    defaultValue = false
)

val ShadowRootFilePref = Pref.BooleanPref(
    key = PREFS_SHADOWROOTFILE,
    titleId = R.string.prefs_shadowrootfile,
    summaryId = R.string.prefs_shadowrootfile_summary,
    defaultValue = false
)

val AllowShadowingDefaultPref = Pref.BooleanPref(
    key = PREFS_ALLOWSHADOWINGDEFAULT,
    titleId = R.string.prefs_allowshadowingdefault,
    summaryId = R.string.prefs_allowshadowingdefault_summary,
    defaultValue = false
)

val UseFindLsPref = Pref.BooleanPref(
    key = PREFS_FINDLS,
    titleId = R.string.prefs_usefindls,
    summaryId = R.string.prefs_usefindls_summary,
    defaultValue = true
)

val AssembleFileListOneStepPref = Pref.BooleanPref(
    key = PREFS_ASSEMBLEFILELISTONESTEP,
    titleId = R.string.prefs_assemblefilelistonestep,
    summaryId = R.string.prefs_assemblefilelistonestep_summary,
    defaultValue = true
)

val CatchUncaughtExceptionPref = Pref.BooleanPref(
    key = PREFS_CATCHUNCAUGHTEXCEPTION,
    titleId = R.string.prefs_catchuncaughtexception,
    summaryId = R.string.prefs_catchuncaughtexception_summary,
    defaultValue = false
)

val MaxCrashLinesPref = Pref.IntPref(
    key = PREFS_MAXCRASHLINES,
    titleId = R.string.prefs_maxcrashlines,
    summaryId = R.string.prefs_maxcrashlines_summary,
    entries = (10..200 step 10).toList(),
    defaultValue = 50
)

val InvalidateSelectivePref = Pref.BooleanPref(
    key = PREFS_INVALIDATESELECTIVE,
    titleId = R.string.prefs_invalidateselective,
    summaryId = R.string.prefs_invalidateselective_summary,
    defaultValue = true
)

val CacheUrisPref = Pref.BooleanPref(
    key = PREFS_CACHEURIS,
    titleId = R.string.prefs_cacheuris,
    summaryId = R.string.prefs_cacheuris_summary,
    defaultValue = true
)

val CacheFileListsPref = Pref.BooleanPref(
    key = PREFS_CACHEFILELISTS,
    titleId = R.string.prefs_cachefilelists,
    summaryId = R.string.prefs_cachefilelists_summary,
    defaultValue = true
)

val MaxRetriesPerPackagePref = Pref.IntPref(
    key = PREFS_MAXRETRIESPERPACKAGE,
    titleId = R.string.prefs_maxretriesperpackage,
    summaryId = R.string.prefs_maxretriesperpackage_summary,
    entries = (0..10).toList(),
    defaultValue = 1
)

val DelayBeforeRefreshAppInfoPref = Pref.IntPref(
    key = PREFS_DELAYBEFOREREFRESHAPPINFO,
    titleId = R.string.prefs_delaybeforerefreshappinfo,
    summaryId = R.string.prefs_delaybeforerefreshappinfo_summary,
    entries = (0..30).toList(),
    defaultValue = 0
)

val RefreshAppInfoTimeoutPref = Pref.IntPref(
    key = PREFS_REFRESHAPPINFOTIMEOUT,
    titleId = R.string.prefs_refreshappinfotimeout,
    summaryId = R.string.prefs_refreshappinfotimeout_summary,
    entries = ((0..9 step 1) + (10..120 step 10)).toList(),
    defaultValue = 30
)

val UseForegroundPref = Pref.BooleanPref(
    key = PREFS_USEFOREGROUND,
    titleId = R.string.prefs_useforeground,
    summaryId = R.string.prefs_useforeground_summary,
    defaultValue = true
)

val UseExpeditedPref = Pref.BooleanPref(
    key = PREFS_USEEXPEDITED,
    titleId = R.string.prefs_useexpedited,
    summaryId = R.string.prefs_useexpedited_summary,
    defaultValue = true
)

val FakeBackupSecondsPref = Pref.IntPref(
    key = PREFS_FAKEBACKUPSECONDS,
    titleId = R.string.prefs_fakebackupseconds,
    summaryId = R.string.prefs_fakebackupseconds_summary,
    entries = ((0..9 step 1) + (10..50 step 10) + (60..1200 step 60)).toList(),
    defaultValue = 0
)
