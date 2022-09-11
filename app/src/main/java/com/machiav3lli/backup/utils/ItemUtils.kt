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
package com.machiav3lli.backup.utils

import android.content.pm.PackageManager
import androidx.navigation.NavDestination
import com.machiav3lli.backup.R
import com.machiav3lli.backup.dbs.entity.AppExtras
import com.machiav3lli.backup.items.Package
import com.machiav3lli.backup.ui.compose.navigation.NavItem

fun getStats(appsList: MutableList<Package>): Triple<Int, Int, Int> {
    var backupsNumber = 0
    var updatedNumber = 0
    appsList.forEach {
        if (it.hasBackups) {
            backupsNumber += it.numberOfBackups
            if (it.isUpdated) updatedNumber += 1
        }
    }
    return Triple(appsList.size, backupsNumber, updatedNumber)
}

fun PackageManager.getInstalledPackagesWithPermissions() =
    getInstalledPackages(0).map { getPackageInfo(it.packageName, PackageManager.GET_PERMISSIONS) }

fun List<AppExtras>.get(packageName: String) =
    find { it.packageName == packageName } ?: AppExtras(packageName)

fun Int.itemIdToOrder(): Int = when (this) {
    R.id.backupFragment -> 1
    R.id.restoreFragment -> 2
    R.id.schedulerFragment -> 3
    else -> 0 // R.id.homeFragment
}

fun NavDestination.destinationToItem(): NavItem? = listOf(
    NavItem.UserPrefs,
    NavItem.ServicePrefs,
    NavItem.AdvancedPrefs,
    NavItem.ToolsPrefs,
    NavItem.Home,
    NavItem.Backup,
    NavItem.Restore,
    NavItem.Scheduler,
    NavItem.Settings
).find { this.route == it.destination }