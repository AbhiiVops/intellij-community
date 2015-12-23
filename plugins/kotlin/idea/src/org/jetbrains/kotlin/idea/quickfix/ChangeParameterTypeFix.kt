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

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.refactoring.changeSignature.*
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.KotlinType

class ChangeParameterTypeFix(element: KtParameter, private val type: KotlinType) : KotlinQuickFixAction<KtParameter>(element) {
    private val containingDeclarationName: String?
    private val isPrimaryConstructorParameter: Boolean

    init {
        val declaration = PsiTreeUtil.getParentOfType(element, KtNamedDeclaration::class.java)
        val declarationFQName = declaration?.fqName
        isPrimaryConstructorParameter = declaration is KtPrimaryConstructor
        containingDeclarationName = if (declarationFQName != null) declarationFQName.asString() else declaration?.name
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        return super.isAvailable(project, editor, file) && containingDeclarationName != null
    }

    override fun getText(): String {
        val renderedType = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_IN_TYPES.renderType(type)
        return if (isPrimaryConstructorParameter)
            KotlinBundle.message("change.primary.constructor.parameter.type", element.name, containingDeclarationName, renderedType)
        else
            KotlinBundle.message("change.function.parameter.type", element.name, containingDeclarationName, renderedType)
    }

    override fun getFamilyName() = KotlinBundle.message("change.type.family")

    public override operator fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val function = element.getStrictParentOfType<KtFunction>() ?: return
        val parameterIndex = function.valueParameters.indexOf(element)
        val context = function.analyze()
        val descriptor = context[BindingContext.DECLARATION_TO_DESCRIPTOR, function] as? FunctionDescriptor ?: return
        val configuration = object : KotlinChangeSignatureConfiguration {
            override fun configure(originalDescriptor: KotlinMethodDescriptor) = originalDescriptor.apply {
                parameters[if (receiver != null) parameterIndex + 1 else parameterIndex].currentTypeInfo = KotlinTypeInfo(false, type)
            }

            override fun performSilently(affectedFunctions: Collection<PsiElement>) = true
        }
        runChangeSignature(element.project, descriptor, configuration, element, text)
    }
}
