// "Import extension function 'State.getValue'" "true"
// WITH_STDLIB
// ERROR: Type 'TypeVariable(R)' has no method 'getValue(Nothing?, KProperty<*>)' and thus it cannot serve as a delegate

package import

import base.State
import base.getValue

fun test() {
    val y by <selection><caret></selection>run {
        State("Inside run")
    }
}

/* IGNORE_FIR */
