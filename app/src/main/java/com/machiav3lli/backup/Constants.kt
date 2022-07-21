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
package com.machiav3lli.backup

import android.Manifest
import android.content.Intent
import com.machiav3lli.backup.ui.item.ChipItem
import com.machiav3lli.backup.ui.item.Legend
import com.machiav3lli.backup.ui.item.Link
import java.time.format.DateTimeFormatter

const val PREFS_SHARED_PRIVATE = "com.machiav3lli.backup"
const val EXPORTS_FOLDER_NAME = "EXPORTS"
const val LOG_FOLDER_NAME = "LOGS"

const val LOG_INSTANCE = "%s.log"
const val BACKUP_INSTANCE_PROPERTIES = "%s-user_%s.properties"
const val BACKUP_INSTANCE_DIR = "%s-user_%s"
const val EXPORTS_INSTANCE = "%s.scheds"

const val MAIN_DB_NAME = "main.db"
const val PACKAGES_LIST_GLOBAL_ID = -1L

const val ACTION_CANCEL = "cancel"
const val ACTION_SCHEDULE = "schedule"
const val ACTION_RESCHEDULE = "reschedule"
const val ACTION_CRASH = "crash"

const val NAV_MAIN = 0
const val NAV_PREFS = 1

const val PREFS_SORT_FILTER = "sortFilter"
const val PREFS_FIRST_LAUNCH = "firstLaunch"
const val PREFS_IGNORE_BATTERY_OPTIMIZATION = "ignoreBatteryOptimization"
const val PREFS_SKIPPEDENCRYPTION = "skippedEncryptionCounter"

const val PREFS_LANGUAGES = "languages"
const val PREFS_THEME = "themes"
const val PREFS_THEME_X = "appTheme"
const val PREFS_THEME_DYNAMIC = "dynamic"
const val PREFS_ACCENT_COLOR = "themeAccentColor"
const val PREFS_ACCENT_COLOR_X = "appAccentColor"
const val PREFS_SECONDARY_COLOR = "themeSecondaryColor"
const val PREFS_SECONDARY_COLOR_X = "appSecondaryColor"
const val PREFS_LANGUAGES_DEFAULT = "system"
const val PREFS_PATH_BACKUP_DIRECTORY = "pathBackupFolder"
const val PREFS_LOADINGTOASTS = "loadingToasts"
const val PREFS_DEVICELOCK = "deviceLock"
const val PREFS_BIOMETRICLOCK = "biometricLock"
const val PREFS_OLDBACKUPS = "oldBackups"
const val PREFS_REMEMBERFILTERING = "rememberFiltering"
const val PREFS_COMPRESSION_LEVEL = "compressionLevel"
const val PREFS_ENCRYPTION = "encryption"
const val PREFS_PASSWORD = "password"
const val PREFS_PASSWORD_CONFIRMATION = "passwordConfirmation"
const val PREFS_ENABLESPECIALBACKUPS = "enableSpecialBackups"
const val PREFS_SALT = "salt"
const val PREFS_EXCLUDECACHE = "excludeCache"
const val PREFS_EXTERNALDATA = "backupExternalData"
const val PREFS_OBBDATA = "backupObbData"
const val PREFS_MEDIADATA = "backupMediaData"
const val PREFS_DEVICEPROTECTEDDATA = "backupDeviceProtectedData"
const val PREFS_ENABLESESSIONINSTALLER = "enableSessionInstaller"
const val PREFS_INSTALLER_PACKAGENAME = "installationPackage"
const val PREFS_RESTOREPERMISSIONS = "restorePermissions"
const val PREFS_NUM_BACKUP_REVISIONS = "numBackupRevisions"
const val PREFS_HOUSEKEEPING_MOMENT = "housekeepingMoment"
const val PREFS_DISABLEVERIFICATION = "disableVerification"
const val PREFS_RESTOREWITHALLPERMISSIONS = "giveAllPermissions"
const val PREFS_ALLOWDOWNGRADE = "allowDowngrade"
const val PREFS_CANCELONSTART = "cancelOnStart"
const val PREFS_SHOW_INFO_LOG = "showInfoLogBar"
const val PREFS_CACHEPACKAGES = "cachePackages"
const val PREFS_CACHEONUPDATE = "usePackageCacheOnUpdate"
const val PREFS_COLUMNNAMESAF = "useColumnNameSAF"
const val PREFS_USEALARMCLOCK = "useAlarmClock"
const val PREFS_USEEXACTRALARM = "useExactAlarm"
const val PREFS_USEFOREGROUND = "useForeground"
const val PREFS_USEEXPEDITED = "useExpedited"
const val PREFS_PAUSEAPPS = "pauseApps"
const val PREFS_PMSUSPEND = "pmSuspend"
const val PREFS_BACKUPTARCMD = "backupTarCmd"
const val PREFS_RESTORETARCMD = "restoreTarCmd"
const val PREFS_STRICTHARDLINKS = "strictHardLinks"
const val PREFS_RESTOREAVOIDTEMPCOPY = "restoreAvoidTemporaryCopy"
const val PREFS_SHADOWROOTFILE = "shadowRootFileForSAF"
const val PREFS_ALLOWSHADOWINGDEFAULT = "allowShadowingDefault"
const val PREFS_FINDLS = "useFindLs"
const val PREFS_ASSEMBLEFILELISTONESTEP = "useAssembleFileListOneStep"
const val PREFS_CATCHUNCAUGHTEXCEPTION = "catchUncaughtException"
const val PREFS_MAXCRASHLINES = "maxCrashLines"
const val PREFS_INVALIDATESELECTIVE = "invalidateSelective"
const val PREFS_CACHEURIS = "cacheUris"
const val PREFS_CACHEFILELISTS = "cacheFileLists"
const val PREFS_MAXRETRIESPERPACKAGE = "maxRetriesPerPackage"
const val PREFS_DELAYBEFOREREFRESHAPPINFO = "delayBeforeRefreshAppInfo"
const val PREFS_REFRESHAPPINFOTIMEOUT = "refreshAppInfoTimeout"
const val PREFS_REFRESHTIMEOUT_DEFAULT = 30
const val PREFS_FAKEBACKUPSECONDS = "fakeBackupSeconds"
const val PREFS_BATCH_DELETE = "batchDelete"
const val PREFS_COPYSELF = "copySelfApk"
const val PREFS_SCHEDULESEXPORTIMPORT = "schedulesExportImport"
const val PREFS_SAVEAPPSLIST = "saveAppsList"
const val PREFS_LOGVIEWER = "logViewer"

const val THEME_LIGHT = 0
const val THEME_DARK = 1
const val THEME_SYSTEM = 2
const val THEME_DYNAMIC = 3

val themeItems = mapOf(
    THEME_LIGHT to R.string.prefs_theme_light,
    THEME_DARK to R.string.prefs_theme_dark,
    THEME_SYSTEM to R.string.prefs_theme_system,
    THEME_DYNAMIC to R.string.prefs_theme_dynamic
)

val accentColorItems = mapOf(
    0 to R.string.prefs_accent_0,
    1 to R.string.prefs_accent_1,
    2 to R.string.prefs_accent_2,
    3 to R.string.prefs_accent_3,
    4 to R.string.prefs_accent_4,
    5 to R.string.prefs_accent_5,
    6 to R.string.prefs_accent_6,
    7 to R.string.prefs_accent_7,
    8 to R.string.prefs_accent_8
)

val secondaryColorItems = mapOf(
    0 to R.string.prefs_secondary_0,
    1 to R.string.prefs_secondary_1,
    2 to R.string.prefs_secondary_2,
    3 to R.string.prefs_secondary_3,
    4 to R.string.prefs_secondary_4,
    5 to R.string.prefs_secondary_5,
    6 to R.string.prefs_secondary_6,
    7 to R.string.prefs_secondary_7,
    8 to R.string.prefs_secondary_8
)

const val ALT_MODE_UNSET = 0
const val ALT_MODE_APK = 1
const val ALT_MODE_DATA = 2
const val ALT_MODE_BOTH = 3

const val MODE_UNSET = 0b000000
const val MODE_NONE = 0b0100000
const val MODE_APK = 0b0010000
const val MODE_DATA = 0b0001000
const val MODE_DATA_DE = 0b0000100
const val MODE_DATA_EXT = 0b0000010
const val MODE_DATA_OBB = 0b0000001
const val MODE_DATA_MEDIA = 0b1000000
const val BACKUP_FILTER_DEFAULT = 0b1111111
val possibleSchedModes =
    listOf(MODE_APK, MODE_DATA, MODE_DATA_DE, MODE_DATA_EXT, MODE_DATA_OBB, MODE_DATA_MEDIA)

val scheduleBackupModeChipItems = listOf(
    ChipItem.Apk,
    ChipItem.Data,
    ChipItem.DeData,
    ChipItem.ExtData,
    ChipItem.ObbData,
    ChipItem.MediaData
)

val mainBackupModeChipItems: List<ChipItem> =
    listOf(ChipItem.None).plus(scheduleBackupModeChipItems)

const val MAIN_SORT_LABEL = 0
const val MAIN_SORT_PACKAGENAME = 1
const val MAIN_SORT_APPSIZE = 2
const val MAIN_SORT_DATASIZE = 3
const val MAIN_SORT_APPDATASIZE = 4
const val MAIN_SORT_BACKUPSIZE = 5
const val MAIN_SORT_BACKUPDATE = 6

val sortChipItems = listOf(
    ChipItem.Label,
    ChipItem.PackageName,
    ChipItem.AppSize,
    ChipItem.DataSize,
    ChipItem.AppDataSize,
    ChipItem.BackupSize,
    ChipItem.BackupDate
)

const val MAIN_FILTER_DEFAULT = 0b111
const val MAIN_FILTER_DEFAULT_WITHOUT_SPECIAL = 0b110
const val MAIN_FILTER_UNSET = 0b000
const val MAIN_FILTER_SYSTEM = 0b100
const val MAIN_FILTER_USER = 0b010
const val MAIN_FILTER_SPECIAL = 0b001
val possibleMainFilters = listOf(MAIN_FILTER_SYSTEM, MAIN_FILTER_USER, MAIN_FILTER_SPECIAL)

val mainFilterChipItems = listOf(ChipItem.System, ChipItem.User, ChipItem.Special)

const val SPECIAL_FILTER_ALL = 0
const val SPECIAL_FILTER_LAUNCHABLE = 1
const val SPECIAL_FILTER_NEW_UPDATED = 2
const val SPECIAL_FILTER_OLD = 3
const val SPECIAL_FILTER_NOT_INSTALLED = 4
const val SPECIAL_FILTER_DISABLED = 5

val schedSpecialFilterChipItems = listOf(
    ChipItem.All,
    ChipItem.Launchable,
    ChipItem.NewUpdated,
    ChipItem.Old,
    ChipItem.Disabled
)

val mainSpecialFilterChipItems = schedSpecialFilterChipItems.plus(ChipItem.NotInstalled)

val IGNORED_PERMISSIONS = listOfNotNull(
    Manifest.permission.ACCESS_WIFI_STATE,
    Manifest.permission.ACCESS_NETWORK_STATE,
    Manifest.permission.ACCESS_NETWORK_STATE,
    Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
    if (OABX.minSDK(28)) Manifest.permission.FOREGROUND_SERVICE else null,
    Manifest.permission.INSTALL_SHORTCUT,
    Manifest.permission.INTERNET,
    if (OABX.minSDK(30)) Manifest.permission.QUERY_ALL_PACKAGES else null,
    Manifest.permission.REQUEST_DELETE_PACKAGES,
    Manifest.permission.RECEIVE_BOOT_COMPLETED,
    Manifest.permission.READ_SYNC_SETTINGS,
    Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
    Manifest.permission.USE_FINGERPRINT,
    Manifest.permission.WAKE_LOCK,
)

const val BUNDLE_USERS = "users"

const val HELP_CHANGELOG = "https://github.com/NeoApplications/Neo-Backup/blob/master/CHANGELOG.md"
const val HELP_TELEGRAM = "https://t.me/neo_backup"
const val HELP_ELEMENT =
    "https://matrix.to/#/!PiXJUneYCnkWAjekqX:matrix.org?via=matrix.org&via=chat.astafu.de&via=zerc.net"
const val HELP_LICENSE = "https://github.com/NeoApplications/Neo-Backup/blob/master/LICENSE.md"
const val HELP_ISSUES = "https://github.com/NeoApplications/Neo-Backup/blob/master/ISSUES.md"
const val HELP_FAQ = "https://github.com/NeoApplications/Neo-Backup/blob/master/FAQ.md"

val linksList =
    listOf(Link.Changelog, Link.Telegram, Link.Matrix, Link.License, Link.Issues, Link.FAQ)

val legendList = listOf(
    Legend.Exodus,
    Legend.Launch,
    Legend.Disable,
    Legend.Enable,
    Legend.System,
    Legend.User,
    Legend.Special,
    Legend.APK,
    Legend.Data,
    Legend.DE_Data,
    Legend.External,
    Legend.OBB,
    Legend.Media,
    Legend.Updated
)

val BACKUP_DATE_TIME_FORMATTER_OLD: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
val BACKUP_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS")

val BACKUP_DIRECTORY_INTENT = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    .addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)

fun classAddress(address: String): String = PREFS_SHARED_PRIVATE + address

fun exodusUrl(app: String): String = "https://reports.exodus-privacy.eu.org/reports/$app/latest"

enum class HousekeepingMoment(val value: String) {
    BEFORE("before"), AFTER("after");

    companion object {
        fun fromString(value: String): HousekeepingMoment {
            for (enumValue in values()) {
                if (enumValue.value == value) {
                    return enumValue
                }
            }
            throw IllegalArgumentException("No constant with value '$value'")
        }
    }
}
