/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SingletonUtil {

  private SingletonUtil() {}

  public static boolean isSingleton(@NotNull PsiClass aClass) {
    if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
      return false;
    }
    if (aClass instanceof PsiTypeParameter || aClass instanceof PsiAnonymousClass) {
      return false;
    }
    final PsiMethod[] constructors = getIfOnlyInvisibleConstructors(aClass);
    if (constructors.length == 0) {
      return false;
    }
    final PsiField selfInstance = getIfOneStaticSelfInstance(aClass);
    return selfInstance != null && newOnlyAssignsToStaticSelfInstance(constructors[0], selfInstance);
  }

  private static PsiField getIfOneStaticSelfInstance(PsiClass aClass) {
    final Stream<PsiField> fieldStream = Stream.concat(Arrays.stream(aClass.getFields()),
                                                       Arrays.stream(aClass.getInnerClasses())
                                                         .filter(innerClass -> innerClass.hasModifierProperty(PsiModifier.STATIC))
                                                         .flatMap(innerClass -> Arrays.stream(innerClass.getFields())));
    final List<PsiField> fields = fieldStream.filter(field -> resolveToSingletonField(aClass, field)).limit(2).collect(Collectors.toList());
    return fields.size() == 1 ? fields.get(0) : null;
  }

  private static boolean resolveToSingletonField(PsiClass aClass, PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    final PsiType type = field.getType();
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType)type;
    final PsiClass targetClass = classType.resolve();
    return aClass.equals(targetClass);
  }

  private static PsiMethod[] getIfOnlyInvisibleConstructors(PsiClass aClass) {
    final PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      return PsiMethod.EMPTY_ARRAY;
    }
    for (final PsiMethod constructor : constructors) {
      if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
        return PsiMethod.EMPTY_ARRAY;
      }
      if (!constructor.hasModifierProperty(PsiModifier.PRIVATE) &&
          !constructor.hasModifierProperty(PsiModifier.PROTECTED)) {
        return PsiMethod.EMPTY_ARRAY;
      }
    }
    return constructors;
  }

  private static boolean newOnlyAssignsToStaticSelfInstance(PsiMethod method, final PsiField field) {
    final Query<PsiReference> search = MethodReferencesSearch.search(method, field.getUseScope(), false);
    final NewOnlyAssignedToFieldProcessor processor = new NewOnlyAssignedToFieldProcessor(field);
    search.forEach(processor);
    return processor.isNewOnlyAssignedToField();
  }

  private static class NewOnlyAssignedToFieldProcessor implements Processor<PsiReference> {

    private boolean newOnlyAssignedToField = true;
    private final PsiField field;

    public NewOnlyAssignedToFieldProcessor(PsiField field) {
      this.field = field;
    }

    @Override
    public boolean process(PsiReference reference) {
      final PsiElement element = reference.getElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiNewExpression)) {
        newOnlyAssignedToField = false;
        return false;
      }
      final PsiElement grandParent = parent.getParent();
      if (field.equals(grandParent)) {
        return true;
      }
      if (!(grandParent instanceof PsiAssignmentExpression)) {
        newOnlyAssignedToField = false;
        return false;
      }
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)grandParent;
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        newOnlyAssignedToField = false;
        return false;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
      final PsiElement target = referenceExpression.resolve();
      if (!field.equals(target)) {
        newOnlyAssignedToField = false;
        return false;
      }
      return true;
    }

    public boolean isNewOnlyAssignedToField() {
      return newOnlyAssignedToField;
    }
  }
}
