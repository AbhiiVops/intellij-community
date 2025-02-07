/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class MoveFileFix implements IntentionAction {
  private final VirtualFile myFile;
  private final VirtualFile myTarget;
  private final @IntentionName String myMessage;

  public MoveFileFix(@NotNull VirtualFile file, @NotNull VirtualFile target, @NotNull @Nls String message) {
    myFile = file;
    myTarget = target;
    myMessage = message;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return IntentionPreviewInfo.moveToDirectory(myFile, myTarget);
  }

  @NotNull
  @Override
  public String getText() {
    return myMessage;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (myFile.isValid() && myTarget.isValid()) {
      try {
        myFile.move(this, myTarget);
      }
      catch (IOException e) {
        throw new IncorrectOperationException("Cannot move '" + myFile.getPath() + "' into '" + myTarget.getPath() + "'", (Throwable)e);
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}