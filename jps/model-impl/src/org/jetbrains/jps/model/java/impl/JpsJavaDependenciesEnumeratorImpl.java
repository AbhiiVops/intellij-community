package org.jetbrains.jps.model.java.impl;

import com.intellij.util.SmartList;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsSdkDependency;
import org.jetbrains.jps.model.module.impl.JpsDependenciesEnumeratorBase;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsJavaDependenciesEnumeratorImpl extends JpsDependenciesEnumeratorBase<JpsJavaDependenciesEnumeratorImpl> implements JpsJavaDependenciesEnumerator {
  private boolean myProductionOnly;
  private boolean myRuntimeOnly;
  private boolean myCompileOnly;
  private boolean myExportedOnly;
  private boolean myRecursivelyExportedOnly;
  private JpsJavaClasspathKind myClasspathKind;
  private final List<JpsJavaDependenciesEnumerationHandler> myHandlers;

  public JpsJavaDependenciesEnumeratorImpl(Collection<JpsModule> rootModules) {
    super(rootModules);
    List<JpsJavaDependenciesEnumerationHandler> handlers = null;
    for (JpsJavaDependenciesEnumerationHandler.Factory factory : JpsServiceManager.getInstance().getExtensions(JpsJavaDependenciesEnumerationHandler.Factory.class)) {
      JpsJavaDependenciesEnumerationHandler handler = factory.createHandler(rootModules);
      if (handler != null) {
        if (handlers == null) {
          handlers = new SmartList<JpsJavaDependenciesEnumerationHandler>();
        }
        handlers.add(handler);
      }
    }
    myHandlers = handlers != null ? handlers : Collections.<JpsJavaDependenciesEnumerationHandler>emptyList();
  }

  @Override
  public JpsJavaDependenciesEnumerator productionOnly() {
    myProductionOnly = true;
    return this;
  }

  @Override
  public JpsJavaDependenciesEnumerator compileOnly() {
    myCompileOnly = true;
    return this;
  }

  @Override
  public JpsJavaDependenciesEnumerator runtimeOnly() {
    myRuntimeOnly = true;
    return this;
  }

  @Override
  public JpsJavaDependenciesEnumerator exportedOnly() {
    if (myRecursively) {
      myRecursivelyExportedOnly = true;
    }
    else {
      myExportedOnly = true;
    }
    return this;
  }

  @Override
  public JpsJavaDependenciesEnumerator includedIn(JpsJavaClasspathKind classpathKind) {
    myClasspathKind = classpathKind;
    return this;
  }

  @Override
  public JpsJavaDependenciesRootsEnumerator classes() {
    return new JpsJavaDependenciesRootsEnumeratorImpl(this, JpsOrderRootType.COMPILED);
  }

  @Override
  public JpsJavaDependenciesRootsEnumerator sources() {
    return new JpsJavaDependenciesRootsEnumeratorImpl(this, JpsOrderRootType.SOURCES);
  }

  @Override
  protected JpsJavaDependenciesEnumeratorImpl self() {
    return this;
  }

  @Override
  protected boolean shouldProcessDependenciesRecursively() {
    for (JpsJavaDependenciesEnumerationHandler handler : myHandlers) {
      if (!handler.shouldProcessDependenciesRecursively()) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected boolean shouldProcess(JpsModule module, JpsDependencyElement element) {
    boolean exported = !(element instanceof JpsSdkDependency);
    JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance().getDependencyExtension(element);
    if (extension != null) {
      exported = extension.isExported();
      JpsJavaDependencyScope scope = extension.getScope();
      boolean forTestCompile = scope.isIncludedIn(JpsJavaClasspathKind.TEST_COMPILE) || scope == JpsJavaDependencyScope.RUNTIME &&
                                                                                        shouldAddRuntimeDependenciesToTestCompilationClasspath();
      if (myCompileOnly && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_COMPILE) && !forTestCompile
        || myRuntimeOnly && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) && !scope.isIncludedIn(JpsJavaClasspathKind.TEST_RUNTIME)
        || myClasspathKind != null && !scope.isIncludedIn(myClasspathKind) && !(myClasspathKind == JpsJavaClasspathKind.TEST_COMPILE && forTestCompile)) {
        return false;
      }
      if (myProductionOnly) {
        if (!scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_COMPILE) && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)
            || myCompileOnly && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_COMPILE)
            || myRuntimeOnly && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)) {
          return false;
        }
      }
    }
    if (!exported) {
      if (myExportedOnly) return false;
      if (myRecursivelyExportedOnly && !isEnumerationRootModule(module)) return false;
    }
    return true;
  }

  public boolean isProductionOnly() {
    return myProductionOnly || myClasspathKind == JpsJavaClasspathKind.PRODUCTION_RUNTIME || myClasspathKind == JpsJavaClasspathKind.PRODUCTION_COMPILE;
  }

  public boolean isProductionOnTests(JpsDependencyElement element) {
    for (JpsJavaDependenciesEnumerationHandler handler : myHandlers) {
      if (handler.isProductionOnTestsDependency(element)) {
        return true;
      }
    }
    return false;
  }

  public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
    for (JpsJavaDependenciesEnumerationHandler handler : myHandlers) {
      if (!handler.shouldIncludeTestsFromDependentModulesToTestClasspath()) {
        return false;
      }
    }
    return true;
  }

  public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
    for (JpsJavaDependenciesEnumerationHandler handler : myHandlers) {
      if (handler.shouldAddRuntimeDependenciesToTestCompilationClasspath()) {
        return true;
      }
    }
    return false;
  }
}
