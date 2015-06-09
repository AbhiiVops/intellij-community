/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.JetScope
import org.jetbrains.kotlin.utils.addIfNotNull

public fun JetFunctionLiteral.findLabelAndCall(): Pair<Name?, JetCallExpression?> {
    val literalParent = (this.getParent() as JetFunctionLiteralExpression).getParent()

    fun JetValueArgument.callExpression(): JetCallExpression? {
        val parent = getParent()
        return (if (parent is JetValueArgumentList) parent else this).getParent() as? JetCallExpression
    }

    when (literalParent) {
        is JetLabeledExpression -> {
            val callExpression = (literalParent.getParent() as? JetValueArgument)?.callExpression()
            return Pair(literalParent.getLabelNameAsName(), callExpression)
        }

        is JetValueArgument -> {
            val callExpression = literalParent.callExpression()
            val label = (callExpression?.getCalleeExpression() as? JetSimpleNameExpression)?.getReferencedNameAsName()
            return Pair(label, callExpression)
        }

        else -> {
            return Pair(null, null)
        }
    }
}

// returns corrected resolution scope excluding variable inside its own initializer
// will not be needed after correcting JetScope stored BindingContext (see KT-4822 Wrong scope is used for local variable name completion)
public fun BindingContext.correctedResolutionScope(expression: JetExpression): JetScope? {
    val scope = get(BindingContext.RESOLUTION_SCOPE, expression) ?: return null

    val variablesToExclude = hashSetOf<VariableDescriptor>()
    for (element in expression.parentsWithSelf) {
        if (element is JetExpression) {
            val declaration = element.getParent() as? JetVariableDeclaration ?: continue
            if (element == declaration.getInitializer()) {
                variablesToExclude.addIfNotNull(get(BindingContext.VARIABLE, declaration))
            }
        }
    }

    if (variablesToExclude.isEmpty()) return scope

    return object : JetScope by scope {
        override fun getDescriptors(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean)
                = scope.getDescriptors(kindFilter, nameFilter).filter { it !in variablesToExclude }

        //TODO: it's not correct!
        override fun getLocalVariable(name: Name): VariableDescriptor? {
            val variable = scope.getLocalVariable(name) ?: return null
            return if (variable in variablesToExclude) null else variable
        }

        override fun getProperties(name: Name) = scope.getProperties(name).filter { it !in variablesToExclude }
    }
}