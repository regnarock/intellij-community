/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author yole
 */
public class PyYieldExpressionImpl extends PyElementImpl implements PyYieldExpression {
  public PyYieldExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyYieldExpression(this);
  }

  @Override
  public PyExpression getExpression() {
    final PyExpression[] expressions = PsiTreeUtil.getChildrenOfType(this, PyExpression.class);
    return (expressions != null && expressions.length > 0) ? expressions[0] : null;
  }

  @Override
  public boolean isDelegating() {
    return getNode().findChildByType(PyTokenTypes.FROM_KEYWORD) != null;
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyExpression e = getExpression();
    PyType type = e != null ? context.getType(e) : null;
    if (isDelegating()) {
      final PyClassLikeType classType = as(type, PyClassLikeType.class);
      final PyCollectionType collectionType = as(type, PyCollectionType.class);
      if (classType != null && collectionType != null) {
        if (PyTypingTypeProvider.GENERATOR.equals(classType.getClassQName())) {
          final List<PyType> elementTypes = collectionType.getElementTypes(context);
          if (elementTypes.size() == 3) {
            return elementTypes.get(2);
          }
        }
      }
      return PyNoneType.INSTANCE;
    }
    return type;
  }
}
