/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.groovy.scripts.internal;

import net.jcip.annotations.ThreadSafe;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.model.dsl.internal.transform.RuleVisitor;
import org.objectweb.asm.Opcodes;

import static org.codehaus.groovy.ast.ClassHelper.OBJECT_TYPE;

/**
 * This transforms the generated closures by adding a call to add the owner and delegate to a threadlocal stack
 *
 * This stack is used to resolve dynamic properties and methods without an exceptions based
 * control flow.
 *
 * The doCall method's body is wrapped in a try-finally block.
 * <pre>
 * {@code
 * try {
 *     ScriptClosureContextStack.Holder.push(getOwner(), getDelegate());
 *     « original method body »
 * } finally {
 *     ScriptClosureContextStack.Holder.pop();
 * }
 * }
 * </pre>
 */
@ThreadSafe
public class ClosureExecutionInterceptingTransformer extends AbstractScriptTransformer {
    private static final ClassNode CLOSURE_CONTEXT_STACK_HOLDER = new ClassNode("org.gradle.groovy.scripts.internal.ScriptClosureContextStack$Holder", Opcodes.ACC_PUBLIC, OBJECT_TYPE);

    @Override
    protected int getPhase() {
        return Phases.CANONICALIZATION;
    }

    @Override
    public void call(SourceUnit source) throws CompilationFailedException {
        AstUtils.visitScriptCode(source, new ClosureTransformer());
    }

    private static class ClosureTransformer extends CodeVisitorSupport {
        @Override
        public void visitClosureExpression(ClosureExpression expression) {
            Statement closureCode = expression.getCode();
            if (!RuleVisitor.hasRuleVisitorMetadata(closureCode)) {
                BlockStatement tryBlock = new BlockStatement();
                Expression thisExpression = buildThisExpression(ClassHelper.CLOSURE_TYPE);
                MethodCallExpression getOwnerExpression = buildCallGetMethod(thisExpression, "getOwner");
                MethodCallExpression getDelegateExpression = buildCallGetMethod(thisExpression, "getDelegate");
                tryBlock.addStatement(new ExpressionStatement(makeDirectForSpecialStaticCase(new MethodCallExpression(new ClassExpression(CLOSURE_CONTEXT_STACK_HOLDER), "push", new ArgumentListExpression(getOwnerExpression, getDelegateExpression)))));
                tryBlock.addStatement(closureCode);

                BlockStatement finallyBlock = new BlockStatement();
                finallyBlock.addStatement(new ExpressionStatement(makeDirectForSpecialStaticCase(new MethodCallExpression(new ClassExpression(CLOSURE_CONTEXT_STACK_HOLDER), "pop", new ArgumentListExpression()))));

                TryCatchStatement tryCatchStatement = new TryCatchStatement(tryBlock, finallyBlock);

                expression.setCode(tryCatchStatement);

                super.visitClosureExpression(expression);
            }
        }
    }

    private static Expression makeDirectForSpecialStaticCase(MethodCallExpression methodCallExpression) {
        ClassNode declaringClass = methodCallExpression.getObjectExpression().getType();
        // only handle Object parameters
        Parameter[] parameters = new Parameter[((TupleExpression) methodCallExpression.getArguments()).getExpressions().size()];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = new Parameter(OBJECT_TYPE, "param" + i);
        }
        MethodNode methodNode = new MethodNode(methodCallExpression.getMethodAsString(),
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            ClassHelper.VOID_TYPE,
            parameters,
            ClassNode.EMPTY_ARRAY,
            EmptyStatement.INSTANCE);
        methodNode.setDeclaringClass(declaringClass);
        methodCallExpression.setMethodTarget(methodNode);
        return methodCallExpression;
    }

    private static Expression buildThisExpression(ClassNode node) {
        return new VariableExpression("this", node);
    }

    private static MethodCallExpression buildCallGetMethod(Expression objectExpression, String methodName) {
        MethodCallExpression methodCallExpression = new MethodCallExpression(objectExpression, methodName, MethodCallExpression.NO_ARGUMENTS);
        MethodNode getterMethod = objectExpression.getType().getGetterMethod(methodName);
        if (getterMethod != null) {
            methodCallExpression.setMethodTarget(getterMethod);
        }
        return methodCallExpression;
    }


}
