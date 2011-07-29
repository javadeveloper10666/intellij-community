package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 */
public abstract class AbstractJavaInplaceIntroducer extends AbstractInplaceIntroducer<PsiVariable, PsiExpression> {
  protected TypeSelectorManagerImpl myTypeSelectorManager;

  public AbstractJavaInplaceIntroducer(final Project project,
                                       Editor editor,
                                       PsiExpression expr,
                                       PsiVariable localVariable,
                                       PsiExpression[] occurrences,
                                       TypeSelectorManagerImpl typeSelectorManager, String title) {
    super(project, editor, expr, localVariable, occurrences, title, StdFileTypes.JAVA);
    myTypeSelectorManager = typeSelectorManager;
  }

  protected abstract PsiVariable createFieldToStartTemplateOn(String[] names, PsiType psiType);
  protected abstract String[] suggestNames(PsiType defaultType, String propName);


  @Override
  protected String[] suggestNames(boolean replaceAll, PsiVariable variable) {
    myTypeSelectorManager.setAllOccurences(replaceAll);
    final PsiType defaultType = myTypeSelectorManager.getTypeSelector().getSelectedType();
    final String propertyName = variable != null
                                ? JavaCodeStyleManager.getInstance(myProject).variableNameToPropertyName(variable.getName(), VariableKind.LOCAL_VARIABLE)
                                : null;
    return suggestNames(defaultType, propertyName);
  }


  @Override
  protected PsiVariable createFieldToStartTemplateOn(boolean replaceAll, String[] names) {
    myTypeSelectorManager.setAllOccurences(replaceAll);
    return createFieldToStartTemplateOn(names, getType());
  }

  @Override
  protected void correctExpression() {
    final PsiElement parent = getExpr().getParent();
    if (parent instanceof PsiExpressionStatement && parent.getLastChild() instanceof PsiErrorElement) {
      myExpr = ((PsiExpressionStatement)ApplicationManager.getApplication().runWriteAction(new Computable<PsiElement>() {
        @Override
        public PsiElement compute() {
          return parent.replace(JavaPsiFacade.getElementFactory(myProject).createStatementFromText(parent.getText() + ";", parent));
        }
      })).getExpression();
    }
  }

  @Override
  public PsiExpression restoreExpression(PsiFile containingFile, PsiVariable psiVariable, RangeMarker marker, String exprText) {
    return restoreExpression(containingFile, psiVariable, JavaPsiFacade.getElementFactory(myProject), marker, exprText);
  }

  @Override
  protected void restoreState(PsiVariable psiField) {
    final SmartTypePointer typePointer = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(getType());
    super.restoreState(psiField);
    try {
      myTypeSelectorManager = myExpr != null
                              ? new TypeSelectorManagerImpl(myProject, typePointer.getType(), myExpr, myOccurrences)
                              : new TypeSelectorManagerImpl(myProject, typePointer.getType(), myOccurrences);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  protected void saveSettings(PsiVariable psiVariable) {
    TypeSelectorManagerImpl.typeSelected(psiVariable.getType(), getType());//myDefaultType.getType());
  }

  protected PsiType getType() {
    return myTypeSelectorManager.getDefaultType();
  }

  @Nullable
  public static PsiExpression restoreExpression(PsiFile containingFile,
                                                PsiVariable psiVariable,
                                                PsiElementFactory elementFactory,
                                                RangeMarker marker, String exprText) {
    if (exprText == null) return null;
    if (psiVariable == null || !psiVariable.isValid()) return null;
    final PsiElement refVariableElement = containingFile.findElementAt(marker.getStartOffset());
    PsiExpression expression = PsiTreeUtil.getParentOfType(refVariableElement, PsiReferenceExpression.class);
    if (expression instanceof PsiReferenceExpression && (((PsiReferenceExpression)expression).resolve() == psiVariable ||
                                                         Comparing.strEqual(psiVariable.getName(),
                                                                            ((PsiReferenceExpression)expression).getReferenceName()))) {
      return (PsiExpression)expression.replace(elementFactory.createExpressionFromText(exprText, psiVariable));
    }
    if (expression == null) {
      expression = PsiTreeUtil.getParentOfType(refVariableElement, PsiExpression.class);
    }
    return expression != null && expression.isValid() && expression.getText().equals(exprText) ? expression : null;
  }

   public static Expression createExpression(final TypeExpression expression, final String defaultType) {
     return new Expression() {
       @Override
       public Result calculateResult(ExpressionContext context) {
         return new TextResult(defaultType);
       }

       @Override
       public Result calculateQuickResult(ExpressionContext context) {
         return new TextResult(defaultType);
       }

       @Override
       public LookupElement[] calculateLookupItems(ExpressionContext context) {
         return expression.calculateLookupItems(context);
       }

       @Override
       public String getAdvertisingText() {
         return null;
       }
     };
   }

}
