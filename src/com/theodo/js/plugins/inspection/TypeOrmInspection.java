package com.theodo.js.plugins.inspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.javascript.inspections.JSInspection;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;


public class TypeOrmInspection extends JSInspection {
    @NotNull
    @Override
    protected PsiElementVisitor createVisitor(@NotNull ProblemsHolder problemsHolder,
                                              @NotNull LocalInspectionToolSession localInspectionToolSession) {
        return new JSElementVisitor() {
            @Override
            public void visitJSCallExpression(JSCallExpression callExpression) {
                JSExpression methodExpression = callExpression.getMethodExpression();
                if (methodExpression instanceof JSReferenceExpression) {
                    JSReferenceExpression jsReferenceExpression = (JSReferenceExpression) methodExpression;
                    PsiElement resolve = jsReferenceExpression.resolve();
                    if (resolve instanceof JSFunction) {
                        PsiElement parent = resolve.getParent();
                        if (parent instanceof JSClass) {
                            JSClass jsClassExpression = (JSClass) parent;
                            String name = jsClassExpression.getName();
                            String path = jsClassExpression.getContainingFile().getContainingDirectory().getVirtualFile().getPath();

                            if (path.contains("typeorm") && "Connection".equals(name)) {
                                checkArguments(callExpression, (JSFunction) resolve, problemsHolder);
                            }
                            if (path.contains("typeorm") && "SelectQueryBuilder".equals(name)) {
                                checkArguments(callExpression, (JSFunction) resolve, problemsHolder);
                            }
                        }
                    }
                }
            }

            private void checkArguments(JSCallExpression callExpression, JSFunction jsFunction, ProblemsHolder problemsHolder) {
                JSArgumentList argumentList = callExpression.getArgumentList();
                if (argumentList == null) return;

                JSParameterList parameterList = jsFunction.getParameterList();
                if (parameterList == null) return;

                JSParameterListElement[] parameters = parameterList.getParameters();
                JSExpression[] arguments = argumentList.getArguments();
                int argumentIdx = 0;
                for (JSExpression argument : arguments) {
                    JSParameterListElement parameter = parameters[argumentIdx];
                    String parameterName = parameter.getName();
                    if ("condition".equalsIgnoreCase(parameterName)
                            || "where".equalsIgnoreCase(parameterName)
                            || "query".equalsIgnoreCase(parameterName)) {

                        if (!isConstant(argument, null)) {
                            problemsHolder.registerProblem(argument,
                                    "Parameter named '" + parameterName +"' used in method '" + jsFunction.getName() +
                                            "' seems not to be a constant value. You should use parameters instead.");
                        }
                    }
                    argumentIdx++;
                }
            }


        };
    }

    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
        return GroupNames.SECURITY_GROUP_NAME;
    }

    @NotNull
    public String getDisplayName() {
        return "Search for SQLi in TypeORM usages";
    }

    private static boolean isConstant(PsiElement jsExpression, Object index) {
        if (jsExpression == null) return false;

        ConstantDetectionVisitor psiElementVisitor = new ConstantDetectionVisitor(index);
        jsExpression.accept(psiElementVisitor);
        return psiElementVisitor.isConstant;
    }

    private static class ConstantDetectionVisitor extends JSElementVisitor {
        private final Object index;
        private boolean isConstant = false;

        private ConstantDetectionVisitor(Object index) {
            this.index = index;
        }

        @Override
        public void visitJSLiteralExpression(JSLiteralExpression node) {
            isConstant = true;
        }

        @Override
        public void visitJSStringTemplateExpression(JSStringTemplateExpression stringTemplateExpression) {
            isConstant = true;
            for (JSExpression argument : stringTemplateExpression.getArguments()) {
                isConstant &= isConstant(argument, null);
            }
        }

        @Override
        public void visitJSBinaryExpression(JSBinaryExpression node) {
            isConstant = isConstant(node.getLOperand(), null) && isConstant(node.getROperand(), null);
        }

        @Override
        public void visitJSReferenceExpression(JSReferenceExpression node) {
            PsiElement resolve = node.resolve();
            isConstant = isConstant(resolve, index);
        }

        @Override
        public void visitJSIndexedPropertyAccessExpression(JSIndexedPropertyAccessExpression node) {
            JSExpression indexExpression = node.getIndexExpression();
            isConstant = isConstant(indexExpression, index);

            Object index = null;
            if (indexExpression instanceof JSLiteralExpression) {
                index = ((JSLiteralExpression) indexExpression).getValue();
            }

            JSExpression qualifier = node.getQualifier();
            isConstant &= isConstant(qualifier, index);
        }

        @Override
        public void visitJSArrayLiteralExpression(JSArrayLiteralExpression node) {
            isConstant = true;
            JSExpression[] expressions = node.getExpressions();
            if (index instanceof Number) {
                isConstant &= isConstant(expressions[((Number)index).intValue()], null);
            } else {
                for (JSExpression expression : expressions) {
                    isConstant &= isConstant(expression, null);
                }
            }
        }

        @Override
        public void visitJSVariable(JSVariable node) {
            isConstant = node.isConst();
            JSExpression initializer = node.getInitializer();
            if (initializer != null) {
                isConstant &= isConstant(initializer, null);
            }
        }
    }
}