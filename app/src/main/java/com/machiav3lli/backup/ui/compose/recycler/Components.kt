package com.machiav3lli.backup.ui.compose.recycler

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.flowlayout.FlowRow
import com.machiav3lli.backup.R
import com.machiav3lli.backup.ui.compose.item.ButtonIcon
import com.machiav3lli.backup.ui.item.ChipItem

@Composable
fun <T> VerticalItemList(
    modifier: Modifier = Modifier.fillMaxSize(),
    list: List<T>?,
    itemKey: ((T) -> Any)? = null,
    itemContent: @Composable LazyItemScope.(T) -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = if (list.isNullOrEmpty()) Alignment.Center else Alignment.TopStart
    ) {
        when {
            list == null -> Text(
                text = stringResource(id = R.string.loading_list),
                color = MaterialTheme.colorScheme.onBackground
            )
            list.isEmpty() -> Text(
                text = stringResource(id = R.string.empty_filtered_list),
                color = MaterialTheme.colorScheme.onBackground
            )
            else -> {
                // TODO add scrollbars
                LazyColumn(
                    verticalArrangement = Arrangement.Absolute.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                ) {
                    items(items = list, key = itemKey, itemContent = itemContent)
                }
            }
        }
    }
}

@Composable
fun <T> SizedItemList(
    modifier: Modifier = Modifier,
    itemHeight: Int,
    list: List<T>?,
    itemKey: ((T) -> Any)? = null,
    itemContent: @Composable LazyItemScope.(T) -> Unit
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .requiredHeight(if (list != null) (list.size * (8 + itemHeight) + 8).dp else 20.dp),
        contentAlignment = if (list.isNullOrEmpty()) Alignment.Center else Alignment.TopStart
    ) {
        when {
            list == null -> Text(
                text = stringResource(id = R.string.loading_list),
                color = MaterialTheme.colorScheme.onBackground
            )
            list.isEmpty() -> Text(
                text = stringResource(id = R.string.empty_filtered_list),
                color = MaterialTheme.colorScheme.onBackground
            )
            else -> {
                // TODO add scrollbars
                LazyColumn(
                    verticalArrangement = Arrangement.Absolute.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                ) {
                    items(items = list, key = itemKey, itemContent = itemContent)
                }
            }
        }
    }
}

@Composable
fun <T> HorizontalItemList(
    modifier: Modifier = Modifier,
    list: List<T>?,
    itemKey: ((T) -> Any)? = null,
    itemContent: @Composable LazyItemScope.(T) -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (list.isNullOrEmpty()) Alignment.Center else Alignment.CenterStart
    ) {
        when {
            list == null -> Text(
                text = stringResource(id = R.string.loading_list),
                color = MaterialTheme.colorScheme.onBackground
            )
            list.isEmpty() -> Text(
                text = stringResource(id = R.string.empty_filtered_list),
                color = MaterialTheme.colorScheme.onBackground
            )
            else -> {
                LazyRow(
                    horizontalArrangement = Arrangement.Absolute.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                ) {
                    items(items = list, key = itemKey, itemContent = itemContent)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectableChipGroup(
    modifier: Modifier = Modifier,
    list: List<ChipItem>,
    selectedFlag: Int,
    onClick: (Int) -> Unit
) {
    val (selectedFlag, setFlag) = remember { mutableStateOf(selectedFlag) }

    FlowRow(
        modifier = modifier
            .fillMaxWidth(),
        mainAxisSpacing = 8.dp
    ) {
        list.forEach {
            val colors = FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurface,
                selectedContainerColor = Color.Transparent,
                selectedLabelColor = MaterialTheme.colorScheme.primary,
                iconColor = MaterialTheme.colorScheme.onSurface,
                selectedLeadingIconColor = colorResource(id = it.colorId)
            )

            FilterChip(
                colors = colors,
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = MaterialTheme.colorScheme.onSurface,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.dp
                ),
                selected = it.flag == selectedFlag,
                leadingIcon = {
                    ButtonIcon(it.iconId, it.textId)
                },
                onClick = {
                    setFlag(it.flag)
                    onClick(it.flag)
                },
                label = {
                    Text(
                        text = stringResource(id = it.textId),
                        color = if (it.flag == selectedFlag) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectableChipGroup(
    modifier: Modifier = Modifier,
    list: List<ChipItem>,
    selectedFlags: Int,
    onClick: (Int) -> Unit
) {
    val (selectedFlags, setFlag) = remember { mutableStateOf(selectedFlags) }

    FlowRow(
        modifier = modifier
            .fillMaxWidth(),
        mainAxisSpacing = 8.dp
    ) {
        list.forEach {
            val colors = FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurface,
                selectedContainerColor = Color.Transparent,
                selectedLabelColor = MaterialTheme.colorScheme.primary,
                iconColor = MaterialTheme.colorScheme.onSurface,
                selectedLeadingIconColor = colorResource(id = it.colorId)
            )

            FilterChip(
                colors = colors,
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = MaterialTheme.colorScheme.onSurface,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.dp
                ),
                selected = it.flag and selectedFlags != 0,
                leadingIcon = {
                    ButtonIcon(it.iconId, it.textId)
                },
                onClick = {
                    onClick(it.flag)
                    setFlag(selectedFlags xor it.flag)
                },
                label = {
                    Text(
                        text = stringResource(id = it.textId),
                        color = if (it.flag and selectedFlags != 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            )
        }
    }
}