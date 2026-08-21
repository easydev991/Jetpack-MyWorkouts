package com.myworkouts.ui.screens.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.myworkouts.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.workouts_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* ponytail: FAB placeholder, вернуть onClick с реальным действием */ }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_workout)
                )
            }
        }
    ) {
    }
}
