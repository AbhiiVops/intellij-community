/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.GrMethodElementType;

/**
 * @author ilyas
 */
public class GrMethodStub extends StubBase<GrMethod> implements NamedStub<GrMethod> {
  private final StringRef myName;
  private final String[] myAnnotations;
  private final String[] myNamedParameters;
  private final String myTypeText;

  public GrMethodStub(StubElement parent,
                      StringRef name,
                      final String[] annotations,
                      final @NotNull String[] namedParameters,
                      final GrMethodElementType elementType,
                      @Nullable String typeText) {
    super(parent, elementType);
    myName = name;
    myAnnotations = annotations;
    myNamedParameters = namedParameters;
    myTypeText = typeText;
  }

  @NotNull public String getName() {
    return StringRef.toString(myName);
  }

  public String[] getAnnotations() {
    return myAnnotations;
  }

  @NotNull
  public String[] getNamedParameters() {
    return myNamedParameters;
  }

  @Nullable
  public String getTypeText() {
    return myTypeText;
  }
}
