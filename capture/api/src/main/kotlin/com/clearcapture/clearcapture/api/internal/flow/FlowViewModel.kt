package com.clearcapture.clearcapture.api.internal.flow

import androidx.lifecycle.ViewModel

/** Holds the flow graph across rotation; closing the flow when the Activity finishes for good. */
internal class FlowViewModel(val graph: FlowGraph) : ViewModel() {
    override fun onCleared() = graph.close()
}
