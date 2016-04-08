/*
 * Copyright 2015, 2016 Tagir Valeev
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package one.util.huntbugs.registry;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.anno.AstBodyVisitor;
import one.util.huntbugs.registry.anno.AstExpressionVisitor;
import one.util.huntbugs.registry.anno.AstNodeVisitor;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.warning.WarningType;

/**
 * @author lan
 *
 */
public class Detector {
    private static final MethodHandle ALWAYS_TRUE = MethodHandles.constant(boolean.class, true);
    private static final MethodType BASE_TYPE = MethodType.methodType(boolean.class, Object.class, Node.class,
        NodeChain.class, MethodContext.class, MethodDefinition.class, TypeDefinition.class);

    private final Map<String, WarningType> wts;
    private final Class<?> clazz;
    final List<MethodHandle> astVisitors = new ArrayList<>();

    static enum VisitorType {
        AST_NODE_VISITOR(AstNodeVisitor.class, MethodType.methodType(boolean.class, Object.class, Node.class,
            NodeChain.class, MethodContext.class, MethodDefinition.class, TypeDefinition.class), null),
        AST_EXPRESSION_VISITOR(AstExpressionVisitor.class, MethodType.methodType(boolean.class, Object.class,
            Expression.class, NodeChain.class, MethodContext.class, MethodDefinition.class, TypeDefinition.class), "runExpression"),
        AST_BODY_VISITOR(AstBodyVisitor.class, MethodType.methodType(void.class, Object.class, Block.class,
            MethodContext.class, MethodDefinition.class, TypeDefinition.class), "runBody");

        Class<? extends Annotation> anno;
        private MethodType wantedType;
        private MethodHandle adapter;

        static boolean runBody(MethodHandle mh, Object det, Node n, NodeChain nc, MethodContext mc, MethodDefinition md, TypeDefinition td) throws Throwable {
            if(nc == null) {
                mh.invoke(det, (Block)n, mc, md, td);
            }
            return true;
        }
        
        static boolean runExpression(MethodHandle mh, Object det, Node n, NodeChain nc, MethodContext mc, MethodDefinition md, TypeDefinition td) throws Throwable {
            if(n instanceof Expression) {
                return (boolean) mh.invoke(det, (Expression)n, nc, mc, md, td);
            }
            return true;
        }
        
        private VisitorType(Class<? extends Annotation> anno, MethodType wantedType, String adapter) {
            this.anno = anno;
            this.wantedType = wantedType;
            if(adapter != null) {
                try {
                    this.adapter = MethodHandles.lookup().findStatic(getClass(), adapter,
                        BASE_TYPE.insertParameterTypes(0, MethodHandle.class));
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new InternalError(e);
                }
            }
        }

        MethodHandle adapt(MethodHandle mh) {
            mh = adapt(mh, wantedType);
            if(adapter == null)
                return mh;
            return adapter.bindTo(mh);
        }

        private static MethodHandle adapt(MethodHandle mh, MethodType wantedType) {
            MethodType type = mh.type();
            MethodHandle result = MethodHandles.explicitCastArguments(mh, type.changeParameterType(0, Object.class));
            if (wantedType.returnType() == boolean.class) {
                if (type.returnType() == void.class) {
                    result = MethodHandles.filterReturnValue(result, ALWAYS_TRUE);
                    type = mh.type();
                } else if (type.returnType() != boolean.class) {
                    throw new IllegalStateException(mh + ": Unexpected return type " + type.returnType());
                }
            } else {
                if (type.returnType() != wantedType.returnType()) {
                    throw new IllegalStateException(mh + ": Unexpected return type " + type.returnType());
                }
            }
            List<Class<?>> wantedTypes = new ArrayList<>(wantedType.parameterList());
            int[] map = new int[wantedTypes.size()];
            Arrays.fill(map, -1);
            map[0] = 0;
            wantedTypes.set(0, null); // self-reference
            Class<?>[] types = type.parameterArray();
            for (int i = 1; i < types.length; i++) {
                int pos = wantedTypes.indexOf(types[i]);
                if (pos < 0)
                    throw new IllegalStateException(mh + ": Unexpected argument of type " + types[i]);
                wantedTypes.set(pos, null);
                map[i] = pos;
            }
            Class<?>[] missingClasses = wantedTypes.stream().filter(Objects::nonNull).toArray(Class<?>[]::new);
            if (missingClasses.length > 0) {
                int pos = 0;
                for (int i = types.length; i < map.length; i++) {
                    while (wantedTypes.get(pos) == null)
                        pos++;
                    map[i] = pos++;
                }
                result = MethodHandles.dropArguments(result, types.length, missingClasses);
            }
            result = MethodHandles.permuteArguments(result, wantedType, map);
            return result;
        }
    }

    public Detector(Map<String, WarningType> wts, Class<?> clazz) throws IllegalAccessException {
        this.wts = Objects.requireNonNull(wts);
        this.clazz = Objects.requireNonNull(clazz);
        for (Method m : clazz.getMethods()) {
            for (VisitorType type : VisitorType.values()) {
                if (m.getAnnotation(type.anno) != null) {
                    astVisitors.add(type.adapt(MethodHandles.publicLookup().unreflect(m)));
                }
            }
        }
    }

    public WarningType getWarningType(String typeName) {
        return wts.get(typeName);
    }

    @Override
    public String toString() {
        return clazz.getName();
    }

    public Object newInstance() {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }
}
