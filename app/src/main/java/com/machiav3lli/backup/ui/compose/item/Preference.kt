package com.machiav3lli.backup.ui.compose.item

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.machiav3lli.backup.OABX
import com.machiav3lli.backup.ui.compose.ifThen
import com.machiav3lli.backup.ui.item.Pref
import kotlin.math.roundToInt

@Composable
fun BasePreference(
    modifier: Modifier = Modifier,
    @StringRes titleId: Int,
    @StringRes summaryId: Int = -1,
    summary: String? = null,
    isEnabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    endWidget: (@Composable () -> Unit)? = null,
    bottomWidget: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .ifThen(onClick != null) {
                clickable(enabled = isEnabled, onClick = onClick!!)
            }
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                icon()
                Spacer(modifier = Modifier.requiredWidth(8.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .ifThen(!isEnabled) {
                        alpha(0.3f)
                    }
            ) {
                Text(
                    text = stringResource(id = titleId),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 16.sp
                )
                if (summaryId != -1 || summary != null) {
                    Text(
                        text = summary ?: stringResource(id = summaryId),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                bottomWidget?.let {
                    Spacer(modifier = Modifier.requiredWidth(8.dp))
                    bottomWidget()
                }
            }
            endWidget?.let {
                Spacer(modifier = Modifier.requiredWidth(8.dp))
                endWidget()
            }
        }
    }
}

@Composable
fun PreferencesGroupHeader(
    modifier: Modifier = Modifier,
    @StringRes titleId: Int,
    @StringRes summaryId: Int = -1,
    @DrawableRes iconId: Int,
    onClick: (() -> Unit)
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Divider(thickness = 2.dp)
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrefIcon(iconId = iconId, text = stringResource(id = titleId))
            Spacer(modifier = Modifier.requiredWidth(8.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(id = titleId),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                if (summaryId != -1) {
                    Text(
                        text = stringResource(id = summaryId),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun LaunchPreference(
    modifier: Modifier = Modifier,
    pref: Pref,
    isEnabled: Boolean = true,
    summary: String? = null,
    onClick: (() -> Unit) = {},
) {
    BasePreference(
        modifier = modifier,
        titleId = pref.titleId,
        summaryId = pref.summaryId,
        summary = summary,
        icon = {
            if (pref.iconId != -1) PrefIcon(
                iconId = pref.iconId,
                text = stringResource(id = pref.titleId),
                tint = pref.iconTint
            )
            else Spacer(modifier = Modifier.requiredWidth(36.dp))
        },
        isEnabled = isEnabled,
        onClick = onClick // TODO add Composable annotation
    )
}

@Composable
fun EnumPreference(
    modifier: Modifier = Modifier,
    pref: Pref.EnumPref,
    isEnabled: Boolean = true,
    onClick: (() -> Unit) = {},
) {
    BasePreference(
        modifier = modifier,
        titleId = pref.titleId,
        summaryId = pref.entries[OABX.prefInt(pref.key, pref.defaultValue)] ?: pref.summaryId,
        icon = {
            if (pref.iconId != -1) PrefIcon(
                iconId = pref.iconId,
                text = stringResource(id = pref.titleId),
                tint = pref.iconTint
            )
            else Spacer(modifier = Modifier.requiredWidth(36.dp))
        },
        isEnabled = isEnabled,
        onClick = onClick // TODO add Composable annotation
    )
}

@Composable
fun ListPreference(
    modifier: Modifier = Modifier,
    pref: Pref.ListPref,
    isEnabled: Boolean = true,
    onClick: (() -> Unit) = {},
) {
    BasePreference(
        modifier = modifier,
        titleId = pref.titleId,
        summaryId = pref.summaryId,
        summary = pref.entries[OABX.prefString(pref.key, pref.defaultValue)],
        icon = {
            if (pref.iconId != -1) PrefIcon(
                iconId = pref.iconId,
                text = stringResource(id = pref.titleId),
                tint = pref.iconTint
            )
            else Spacer(modifier = Modifier.requiredWidth(36.dp))
        },
        isEnabled = isEnabled,
        onClick = onClick // TODO add Composable annotation
    )
}

@Composable
fun SwitchPreference(
    modifier: Modifier = Modifier,
    pref: Pref.BooleanPref,
    isEnabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit) = {},
) {
    var checked by remember(OABX.prefFlag(pref.key, pref.defaultValue)) {
        mutableStateOf(OABX.prefFlag(pref.key, pref.defaultValue))
    }
    val check = { value: Boolean ->
        OABX.setPrefFlag(pref.key, value)
        checked = value
    }

    BasePreference(
        modifier = modifier,
        titleId = pref.titleId,
        summaryId = pref.summaryId,
        icon = {
            if (pref.iconId != -1) PrefIcon(
                iconId = pref.iconId,
                text = stringResource(id = pref.titleId),
                tint = pref.iconTint
            )
            else Spacer(modifier = Modifier.requiredWidth(36.dp))
        },
        isEnabled = isEnabled,
        onClick = {
            onCheckedChange(!checked)
            check(!checked)
        },
        endWidget = {
            Switch(
                modifier = Modifier
                    .height(24.dp),
                checked = checked,
                onCheckedChange = {
                    onCheckedChange(it)
                    check(it)
                },
                enabled = isEnabled,
            )
        }
    )
}

@Composable
fun CheckboxPreference(
    modifier: Modifier = Modifier,
    pref: Pref.BooleanPref,
    isEnabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit) = {},
) {
    var checked by remember(OABX.prefFlag(pref.key, pref.defaultValue)) {
        mutableStateOf(OABX.prefFlag(pref.key, pref.defaultValue))
    }
    val check = { checks: Boolean ->
        OABX.setPrefFlag(pref.key, checks)
        checked = checks
    }

    BasePreference(
        modifier = modifier,
        titleId = pref.titleId,
        summaryId = pref.summaryId,
        icon = {
            if (pref.iconId != -1) PrefIcon(
                iconId = pref.iconId,
                text = stringResource(id = pref.titleId),
                tint = pref.iconTint
            )
            else Spacer(modifier = Modifier.requiredWidth(36.dp))
        },
        isEnabled = isEnabled,
        onClick = {
            onCheckedChange(!checked)
            check(!checked)
        },
        endWidget = {
            Checkbox(
                modifier = Modifier
                    .height(24.dp),
                checked = checked,
                onCheckedChange = {
                    onCheckedChange(it)
                    check(it)
                },
                enabled = isEnabled,
            )
        }
    )
}

@Composable
fun SeekBarPreference(
    modifier: Modifier = Modifier,
    pref: Pref.IntPref,
    isEnabled: Boolean = true,
    onValueChange: ((Int) -> Unit) = {},
) {
    val currentValue = OABX.prefInt(pref.key, pref.defaultValue)
    var sliderPosition by remember {
        mutableStateOf(
            pref.entries.indexOfFirst { it == currentValue }.let {
                if (it < 0)
                    pref.entries.indexOfFirst { it == pref.defaultValue }
                else
                    it
            }.let {
                if (it < 0)
                    0
                else
                    it
            }
        )
    }
    val savePosition = { pos: Int ->
        val value = pref.entries[pos]
        OABX.setPrefInt(pref.key, value)
        sliderPosition = pos
    }
    val last = pref.entries.size - 1

    BasePreference(
        modifier = modifier,
        titleId = pref.titleId,
        summaryId = pref.summaryId,
        icon = {
            if (pref.iconId != -1) PrefIcon(
                iconId = pref.iconId,
                text = stringResource(id = pref.titleId),
                tint = pref.iconTint
            )
            else Spacer(modifier = Modifier.requiredWidth(36.dp))
        },
        isEnabled = isEnabled,
        bottomWidget = {
            Row {
                Slider(
                    modifier = Modifier
                        .requiredHeight(24.dp)
                        .weight(1f),
                    value = sliderPosition.toFloat(),
                    valueRange = 0.toFloat()..last.toFloat(),
                    onValueChange = { sliderPosition = it.roundToInt() },
                    onValueChangeFinished = {
                        onValueChange(sliderPosition)
                        savePosition(sliderPosition)
                    },
                    steps = last - 1,
                    enabled = isEnabled
                )
                Spacer(modifier = Modifier.requiredWidth(8.dp))
                Text(
                    text = pref.entries[sliderPosition].toString(),
                    modifier = Modifier.widthIn(min = 48.dp)
                )
            }
        }
    )
}