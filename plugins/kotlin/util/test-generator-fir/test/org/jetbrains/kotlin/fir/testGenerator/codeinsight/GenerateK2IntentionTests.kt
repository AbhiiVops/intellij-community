// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.codeinsight

import org.jetbrains.kotlin.idea.k2.codeInsight.intentions.shared.AbstractSharedK2IntentionTest
import org.jetbrains.kotlin.idea.k2.intentions.tests.AbstractK2IntentionTest
import org.jetbrains.kotlin.testGenerator.model.*


internal fun MutableTWorkspace.generateK2IntentionTests() {
    val idea = "idea/tests/testData/"

    testGroup("code-insight/intentions-k2/tests", testDataPath = "../../..") {
        testClass<AbstractK2IntentionTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$")
            model("${idea}intentions/addNameToArgument", pattern = pattern)
            model("${idea}intentions/addNamesToCallArguments", pattern = pattern)
            model("${idea}intentions/addNamesToFollowingArguments", pattern = pattern)
            model("${idea}intentions/addPropertyAccessors", pattern = pattern)
            model("${idea}intentions/specifyTypeExplicitly", pattern = pattern)
            model("${idea}intentions/importAllMembers", pattern = pattern)
            model("${idea}intentions/importMember", pattern = pattern)
            model("${idea}intentions/convertToBlockBody", pattern = pattern)
            model("${idea}intentions/addWhenRemainingBranches", pattern = pattern)
            model("code-insight/intentions-k2/tests/testData/intentions", pattern = pattern)
        }
    }


    testGroup("code-insight/intentions-shared/tests/k2", testDataPath = "../testData") {
        testClass<AbstractSharedK2IntentionTest> {
            model("intentions", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$"))
        }
    }
}