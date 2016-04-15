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
package one.util.huntbugs.util;

import java.util.Collections;
import java.util.List;
import java.util.Objects;





import java.util.function.Predicate;

import one.util.huntbugs.flow.ValuesFlow;

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.CatchBlock;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Lambda;
import com.strobel.decompiler.ast.Node;

/**
 * @author lan
 *
 */
public class NodeChain {
    private final NodeChain parent;
    private final Node cur;

    public NodeChain(NodeChain parent, Node cur) {
        this.parent = parent;
        this.cur = Objects.requireNonNull(cur);
    }

    public NodeChain getParent() {
        return parent;
    }

    public Node getNode() {
        return cur;
    }
    
    @Override
    public String toString() {
        if(parent == null)
            return cur.toString();
        return cur + " -> "+parent;
    }
    
    public Block getRoot() {
        NodeChain nc = this;
        while(nc.getParent() != null) {
            nc = nc.getParent();
        }
        return (Block) nc.getNode();
    }
    
    public boolean isSynchronized() {
        NodeChain chain = this;
        while(chain != null) {
            if(Nodes.isSynchorizedBlock(chain.getNode()))
                return true;
            chain = chain.getParent();
        }
        return false;
    }
    
    public boolean isInCatch(String wantedException) {
        NodeChain nc = this;
        while(nc != null) {
            if(nc.getNode() instanceof CatchBlock) {
                CatchBlock catchBlock = (CatchBlock)nc.getNode();
                TypeReference exType = catchBlock.getExceptionType();
                if(exType != null && Types.isInstance(exType, wantedException))
                    return true;
                if(catchBlock.getCaughtTypes().stream().anyMatch(t -> Types.isInstance(t, wantedException)))
                    return true;
            }
            nc = nc.getParent();
        }
        return false;
    }
    
    public MethodReference getLambdaMethod() {
        NodeChain nc = this;
        while(nc != null) {
            if(nc.getNode() instanceof Lambda) {
                return ((Lambda)nc.getNode()).getMethod();
            }
            nc = nc.getParent();
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public List<Expression> findUsages(Expression expr, boolean includePhi) {
        if (cur instanceof Expression && ((Expression) cur).getCode() != AstCode.Store
            && ((Expression) cur).getArguments().stream().anyMatch(x -> expr == x)) {
            return Collections.singletonList((Expression) cur);
        }
        Predicate<Expression> has = src -> src == expr;
        Predicate<Expression> pred = includePhi ? src -> ValuesFlow.anyMatch(src, has) : has;
        return (List<Expression>)(List<?>)getRoot().getChildrenAndSelfRecursive(n -> {
            if(!(n instanceof Expression))
                return false;
            Expression e = (Expression)n;
            return e.getArguments().stream().map(ValuesFlow::getSource).anyMatch(pred);
        });
    }
}
