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
package com.machiav3lli.backup.pages

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.machiav3lli.backup.ICON_SIZE_SMALL
import com.machiav3lli.backup.OABX
import com.machiav3lli.backup.R
import com.machiav3lli.backup.dbs.entity.Schedule
import com.machiav3lli.backup.sheets.ScheduleSheet
import com.machiav3lli.backup.ui.compose.blockBorder
import com.machiav3lli.backup.ui.compose.icons.Phosphor
import com.machiav3lli.backup.ui.compose.icons.phosphor.CalendarPlus
import com.machiav3lli.backup.ui.compose.recycler.ScheduleRecycler
import com.machiav3lli.backup.utils.specialBackupsEnabled
import com.machiav3lli.backup.viewmodels.ScheduleViewModel
import com.machiav3lli.backup.viewmodels.SchedulerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerPage(viewModel: SchedulerViewModel) {
    val context = LocalContext.current
    val mScope = rememberCoroutineScope()
    val schedules by viewModel.schedules.collectAsState(emptyList())
    val scheduleSheetId = remember { mutableStateOf(-1L) }
    val scheduleSheetState = rememberModalBottomSheetState(true)
    val scheduleSheetVM = remember(scheduleSheetId.value) {
        ScheduleViewModel(
            scheduleSheetId.value,
            OABX.db.scheduleDao,
            OABX.app,
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(id = R.string.sched_add)) },
                icon = {
                    Icon(
                        modifier = Modifier.size(ICON_SIZE_SMALL),
                        imageVector = Phosphor.CalendarPlus,
                        contentDescription = stringResource(id = R.string.sched_add)
                    )
                },
                onClick = { viewModel.addSchedule(specialBackupsEnabled) }
            )
        }
    ) { paddingValues ->
        ScheduleRecycler(
            modifier = Modifier
                .blockBorder()
                .padding(paddingValues)
                .fillMaxSize(),
            productsList = schedules,
            onClick = { item ->
                scheduleSheetId.value = item.id
            },
            onCheckChanged = { item: Schedule, b: Boolean ->
                viewModel.updateSchedule(
                    item.copy(enabled = b),
                    true,
                )
            }
        )

        if (scheduleSheetId.value > 0L) {
            ModalBottomSheet(
                sheetState = scheduleSheetState,
                containerColor = MaterialTheme.colorScheme.background,
                dragHandle = null,
                scrimColor = Color.Transparent,
                onDismissRequest = {
                    mScope.launch { scheduleSheetState.hide() }
                    scheduleSheetId.value = -1L
                }
            ) {
                ScheduleSheet(
                    scheduleSheetVM,
                    scheduleId = scheduleSheetId.value,
                ) {
                    mScope.launch { scheduleSheetState.hide() }
                    scheduleSheetId.value = -1L
                }
            }
        }
    }
}
