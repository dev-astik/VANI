package com.example.vani.ui.wakeword

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Live status of the wake word system, shown in Settings so you can SEE what is happening
 * (model loading, listening, mic level, errors, what was heard).
 */
object WakeWordStatus {
    var text by mutableStateOf("Off")
    var micLevel by mutableFloatStateOf(0f) // 0..1, moves when the mic hears sound
    var lastHeard by mutableStateOf("")
}