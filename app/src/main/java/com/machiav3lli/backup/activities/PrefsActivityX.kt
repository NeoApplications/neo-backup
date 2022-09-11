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
package com.machiav3lli.backup.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.machiav3lli.backup.NAV_PREFS
import com.machiav3lli.backup.OABX
import com.machiav3lli.backup.R
import com.machiav3lli.backup.dbs.ODatabase
import com.machiav3lli.backup.fragments.HelpSheet
import com.machiav3lli.backup.ui.compose.item.TopBar
import com.machiav3lli.backup.ui.compose.item.TopBarButton
import com.machiav3lli.backup.ui.compose.navigation.BottomNavBar
import com.machiav3lli.backup.ui.compose.navigation.NavItem
import com.machiav3lli.backup.ui.compose.navigation.PrefsNavHost
import com.machiav3lli.backup.ui.compose.theme.AppTheme
import com.machiav3lli.backup.utils.destinationToItem
import com.machiav3lli.backup.utils.setCustomTheme
import com.machiav3lli.backup.viewmodels.ExportsViewModel
import com.machiav3lli.backup.viewmodels.LogViewModel

class PrefsActivityX : BaseActivity() {
    private var helpSheet: HelpSheet? = null
    private val exportsViewModel: ExportsViewModel by viewModels {
        ExportsViewModel.Factory(ODatabase.getInstance(applicationContext).scheduleDao, application)
    }
    private val logsViewModel: LogViewModel by viewModels {
        LogViewModel.Factory(application)
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        OABX.activity = this
        setCustomTheme()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(
                darkTheme = isSystemInDarkTheme()
            ) {
                val navController = rememberAnimatedNavController()
                var pageTitle: Int? by remember {
                    mutableStateOf(NavItem.Settings.title)
                }

                navController.addOnDestinationChangedListener { _, destination, _ ->
                    pageTitle = destination.destinationToItem()?.title
                }

                Scaffold(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    topBar = {
                        TopBar(
                            title = stringResource(id = pageTitle ?: NavItem.Settings.title)
                        ) {
                            TopBarButton(
                                icon = painterResource(id = R.drawable.ic_info),
                                description = stringResource(id = R.string.help),
                            ) {
                                if (helpSheet != null && helpSheet!!.isVisible) helpSheet?.dismissAllowingStateLoss()
                                helpSheet = HelpSheet()
                                helpSheet!!.showNow(supportFragmentManager, "HELPSHEET")
                            }
                        }
                    },
                    bottomBar = { BottomNavBar(page = NAV_PREFS, navController = navController) }
                ) { paddingValues ->
                    PrefsNavHost(
                        modifier = Modifier.padding(paddingValues),
                        navController = navController,
                        logsViewModel = logsViewModel,
                        exportsViewModel = exportsViewModel
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        OABX.activity = this
    }
}
