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
package one.util.huntbugs.detect;

import java.util.Set;






import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Variable;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="ParameterOverwritten", maxScore=60)
@WarningDefinition(category="RedundantCode", name="DeadStoreInReturn", maxScore=50)
@WarningDefinition(category="RedundantCode", name="DeadIncrementInReturn", maxScore=60)
@WarningDefinition(category="RedundantCode", name="DeadIncrementInAssignment", maxScore=60)
public class DeadLocalStore {
    @AstVisitor(nodes=AstNodes.ROOT)
    public void visitBody(Block body, MethodContext mc, MethodDefinition md) {
        if(!mc.isAnnotated())
            return;
        for(ParameterDefinition pd : md.getParameters()) {
            // looks like autogenerated by jay
            if(pd.getName().equals("yyVal") && Types.isObject(pd.getParameterType()))
                continue;
            Set<Expression> usages = mc.getParameterUsages(pd);
            if(usages != null && usages.isEmpty()) {
                Node overwrite = Nodes.find(body, n -> {
                    if(!(n instanceof Expression)) return false;
                    Expression expr = (Expression) n;
                    return expr.getCode() == AstCode.Store
                            && ((Variable)expr.getOperand()).getOriginalParameter() == pd;
                });
                if(overwrite != null) {
                    mc.report("ParameterOverwritten", Methods.findSuperMethod(md) == null ? 0 : 20, overwrite);
                }
            }
        }
    }
    
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.Return && expr.getArguments().size() == 1) {
            Expression arg = expr.getArguments().get(0);
            if(arg.getCode() == AstCode.Store) {
                mc.report("DeadStoreInReturn", 0, arg);
            } else if(arg.getCode() == AstCode.PreIncrement || arg.getCode() == AstCode.PostIncrement) {
                Expression var = arg.getArguments().get(0);
                if(var.getOperand() instanceof Variable)
                    mc.report("DeadIncrementInReturn", 0, var);
            }
        }
        if(expr.getCode() == AstCode.Store) {
            Variable var = (Variable) expr.getOperand();
            Expression arg = expr.getArguments().get(0);
            if(arg.getCode() == AstCode.PostIncrement) { // XXX: bug in Procyon? Seems that should be PreIncrement
                Expression load = arg.getArguments().get(0);
                if(load.getCode() == AstCode.Load && var.equals(load.getOperand()) && Integer.valueOf(1).equals(arg.getOperand())) {
                    mc.report("DeadIncrementInAssignment", 0, expr, new WarningAnnotation<>("EXPRESSION", var.getName()
                        + " = " + var.getName() + "++"));
                }
            }
        }
    }
}
