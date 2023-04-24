/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.openrewrite.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.HashSet;
import java.util.Set;

@ToString
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NoStaticImport extends Recipe {

    /**
     * A method pattern that is used to find matching method invocations.
     * See {@link  MethodMatcher} for details on the expression's syntax.
     */
    @Option(displayName = "Method pattern",
            description = "A method pattern that is used to find matching method invocations.",
            example = "java.util.Collections emptyList()")
    String methodPattern;

    @Override
    public String getDisplayName() {
        return "Remove static import";
    }

    @Override
    public String getDescription() {
        return "Removes static imports and replaces them with qualified references. For example, `emptyList()` becomes `Collections.emptyList()`.";
    }

    @Override
    protected JavaVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(methodPattern);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            final Set<String> typesWithStaticImports = new HashSet<>();

            @Override
            public J.Import visitImport(J.Import _import, ExecutionContext ctx) {
                if (_import.isStatic()) {
                    typesWithStaticImports.add(_import.getTypeName());
                }
                return _import;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                MethodMatcher methodMatcher = new MethodMatcher(methodPattern);
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (m.getSelect() == null && methodMatcher.matches(m) && m.getMethodType() != null) {
                    JavaType.FullyQualified receiverType = m.getMethodType().getDeclaringType();
                    if (!typesWithStaticImports.contains(receiverType.getFullyQualifiedName())) {
                        return m;
                    }

                    RemoveImport<ExecutionContext> op = new RemoveImport<>(receiverType.getFullyQualifiedName() + "." + method.getSimpleName(),
                            true);
                    if (!getAfterVisit().contains(op)) {
                        doAfterVisit(op);
                    }

                    maybeAddImport(receiverType.getFullyQualifiedName());
                    m = m.withSelect(new J.Identifier(Tree.randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            receiverType.getClassName(),
                            receiverType,
                            null)
                    );
                }

                return m;
            }
        };
    }
}
