// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.codereview.list.ReviewListComponentFactory
import com.intellij.collaboration.ui.codereview.list.ReviewListItemPresentation
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.scroll.BoundedRangeModelThresholdListener
import com.intellij.vcs.log.ui.frame.ProgressStripe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestsListViewModel
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants
import javax.swing.event.ChangeEvent

internal class GitLabMergeRequestsPanelFactory {

  fun create(scope: CoroutineScope, vm: GitLabMergeRequestsListViewModel): JComponent {
    val listModel = CollectionListModel<GitLabMergeRequestShortDTO>()
    scope.launch {
      var firstEvent = true
      vm.listDataFlow.collect {
        when (it) {
          is GitLabMergeRequestsListViewModel.ListDataUpdate.NewBatch -> {
            if (firstEvent) listModel.add(it.newList)
            else listModel.add(it.batch)
          }
          GitLabMergeRequestsListViewModel.ListDataUpdate.Clear -> listModel.removeAll()
        }
        firstEvent = false
      }
    }

    val list = ReviewListComponentFactory(listModel).create {
      ReviewListItemPresentation.Simple(title = it.title,
                                        id = it.id.toString(),
                                        createdDate = it.createdAt,
                                        author = null,
                                        tagGroup = null,
                                        mergeableStatus = null,
                                        buildStatus = null,
                                        state = it.state.uppercase(),
                                        userGroup1 = null,
                                        userGroup2 = null,
                                        commentsCounter = null)
    }

    return createListLoaderPanel(scope, vm, list)
  }

  private fun createListLoaderPanel(scope: CoroutineScope, vm: GitLabMergeRequestsListViewModel, list: JComponent): JComponent {

    val scrollPane = ScrollPaneFactory.createScrollPane(list, true).apply {
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

      val model = verticalScrollBar.model
      val listener = object : BoundedRangeModelThresholdListener(model, 0.7f) {
        override fun onThresholdReached() {
          if (vm.canLoadMoreState.value) {
            vm.requestMore()
          }
        }
      }
      model.addChangeListener(listener)

      scope.launch {
        vm.listDataFlow.collect {
          when (it) {
            is GitLabMergeRequestsListViewModel.ListDataUpdate.NewBatch -> {
              if (isShowing) {
                listener.stateChanged(ChangeEvent(vm))
              }
            }
            GitLabMergeRequestsListViewModel.ListDataUpdate.Clear -> {
              if (isShowing) {
                vm.requestMore()
              }
            }
          }
        }
      }
    }
    val progressStripe = ProgressStripe(scrollPane, scope.nestedDisposable(),
                                        ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS).apply {
      scope.launch {
        vm.loadingState.collect {
          if (it) startLoadingImmediately() else stopLoading()
        }
      }
    }
    return progressStripe
  }
}