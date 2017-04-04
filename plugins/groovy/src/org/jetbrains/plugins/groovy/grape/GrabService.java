/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.grape;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.swing.event.HyperlinkEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.groovy.grape.GrapeHelper.NOTIFICATION_GROUP;
import static org.jetbrains.plugins.groovy.grape.GrapeHelper.findGrabAnnotations;

@State(name = "GrabService", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class GrabService implements PersistentStateComponent<GrabService.PersistentState> {
  private static final Logger LOG = Logger.getInstance(GrabService.class);

  private final GrabClassFinder grabClassFinder;
  private final Project myProject;
  public Map<String, List<VirtualFile>> grapeState = new ConcurrentHashMap<>();
  public HashMap<VirtualFile, List<String>> grabs = new HashMap<>();

  AtomicBoolean notified = new AtomicBoolean(false);
  private DumbService myDumbService;


  public GrabService(@NotNull Project project, @NotNull DumbService dumbService) {
    grabClassFinder = Extensions.findExtension(PsiElementFinder.EP_NAME, project, GrabClassFinder.class);
    myProject = project;
    myDumbService = dumbService;

    project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event: events) {
          VirtualFile file = event.getFile();
          scheduleUpdate(project, file);
        }
      }
    });

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
      @Override
      public void beforeDocumentChange(DocumentEvent event) {
      }

      @Override
      public void documentChanged(DocumentEvent event) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());

        scheduleUpdate(project, file);
      }
    });
  }

  void scheduleUpdate(@NotNull Project project, @Nullable VirtualFile file) {
    if (file != null && file.getFileType().equals(GroovyFileType.GROOVY_FILE_TYPE)) { //should be optimized
      runUpdate(project, GlobalSearchScope.fileScope(project, file));
    }
  }

  private void runUpdate(Project project, GlobalSearchScope scope) {
    if (project.isDisposed()) return;
    myDumbService = DumbService.getInstance(project);
    if (myDumbService.isDumb()) {
      myDumbService.runWhenSmart(() -> runUpdate(project, scope));
      return;
    }
    LOG.trace("Scheduled");

    ProgressIndicatorUtils.scheduleWithWriteActionPriority(new ReadTask() {
      @Override
      public void onCanceled(@NotNull ProgressIndicator indicator) {
        runUpdate(project, scope);
      }

      @Override
      public void computeInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
        LOG.trace("Execution started");
        updateGrabsInScope(project, scope);
        LOG.trace("Execution finished");
      }
    });
  }

  @NotNull
  public static GrabService getInstance(@NotNull Project project) {
    return ObjectUtils.notNull(ServiceManager.getService(project, GrabService.class));
  }

  void updateGrabsInScope(@NotNull Project project, @NotNull SearchScope scope) {
    boolean notify = false;
    List<PsiAnnotation> annotations = findGrabAnnotations(project, scope);
    Map<VirtualFile, List<String>> updatedGrabQueries = new HashMap<>();

    for (PsiAnnotation annotation: annotations) {
      String grabQuery = GrapeHelper.grabQuery(annotation);
      if (grapeState.get(grabQuery) == null) {
        notify = true;
      }
      VirtualFile file = annotation.getContainingFile().getVirtualFile();
      List<String> grabQueries = updatedGrabQueries.get(file);
      if (grabQueries == null) {
        grabQueries = new ArrayList<>();
        updatedGrabQueries.put(file, grabQueries);
      }
      grabQueries.add(grabQuery);
    }
    updatedGrabQueries.forEach((file, grabQueries) -> grabs.put(file, Collections.unmodifiableList(grabQueries)));
    updateResolve();
    if (notify) {
      showNotification(project);
    }
  }

  public void showNotification(@NotNull Project project) {
    if (!notified.compareAndSet(false, true)) return;
    String title = GroovyBundle.message("process.grab.annotations.title");
    String message = GroovyBundle.message("process.grab.annotations.message");
    NOTIFICATION_GROUP.createNotification(title, message, NotificationType.INFORMATION, new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
        notification.expire();
        downloadProjectDependencies(project);
        notified.compareAndSet(true, false);
      }
    }).notify(project);
  }

  public void downloadProjectDependencies(Project project) {
    grapeState.clear();
    GrapeHelper.processGrabs(project, GlobalSearchScope.allScope(project), new GrapeHelper.ResultHandler() {
      @Override
      public void accept(String grabText, GrapeHelper.GrapeProcessHandler handler) {
        int count = handler.getJarFiles().size();
        if (count != 0) {
          final String title = count + " Grape dependency jar" + (count == 1 ? "" : "s") + " added";
          NOTIFICATION_GROUP.createNotification(title, handler.getMessages(), NotificationType.INFORMATION, null).notify(project);
          addDependencies(grabText, handler.getJarFiles().stream().map(VirtualFile::getPath).collect(Collectors.toList()));
          updateResolve();
        } else {
          NOTIFICATION_GROUP.createNotification("Download @Grab dependency error ", handler.getMessages(), NotificationType.ERROR, null).notify(project);
        }
      }

      @Override
      public void finish() {

      }
    });
  }

  @NotNull
  @Override
  public PersistentState getState() {
    return new PersistentState(grapeState);
  }

  @Override
  public void loadState(@NotNull PersistentState persistentState) {
    if (persistentState.fileMap != null) {
      persistentState.fileMap.forEach(this::addDependencies);
      updateResolve();
    }
  }

  public void addDependencies(@NotNull String grabQuery, @NotNull List<String> paths) {
    List<VirtualFile> files = new ArrayList<>();
    JarFileSystem lfs = JarFileSystem.getInstance();
    paths.forEach(path -> {
      VirtualFile file = lfs.findLocalVirtualFileByPath(path);
      if (file != null) {
        files.add(file);
      }
    });
    grapeState.put(grabQuery, Collections.unmodifiableList(files));
  }

  void updateResolve() {
    grabClassFinder.clearCache();
    PsiManager.getInstance(myProject).dropResolveCaches();

    ApplicationManager.getApplication().invokeLater(
      () -> DaemonCodeAnalyzer.getInstance(myProject).restart(),
      ModalityState.NON_MODAL,
      myProject.getDisposed()
    );
  }


  @NotNull
  public List<VirtualFile> getDependencies(@NotNull SearchScope scope) {
    List<VirtualFile> result = new ArrayList<>();
    grabs.forEach((file, queries) -> {
      if (scope.contains(file)) {
        result.addAll(getDependencies(queries));
      }
    });
    return result;
  }

  @NotNull
  public List<VirtualFile> getDependencies(@NotNull VirtualFile file) {
    List<String> strings = grabs.get(file);
    return strings != null ? getDependencies(strings) : Collections.emptyList();
  }

  public List<VirtualFile> getDependencies(@NotNull List<String> queries) {
    List<VirtualFile> files = new ArrayList<>();
    for(String query: queries) {
      if (grapeState.get(query) != null) {
        files.addAll(grapeState.get(query));
      }
    }

    return files;
  }

  public static class PersistentState {
    public Map<String, List<String>> fileMap;

    public PersistentState(Map<String, List<VirtualFile>> map) {
      fileMap = new HashMap<>();
      map.forEach((s, files) -> fileMap.put(s, files.stream().map(VirtualFile::getCanonicalPath).collect(Collectors.toList())));
    }

    @SuppressWarnings("unused")
    public PersistentState() {
    }
  }
}
