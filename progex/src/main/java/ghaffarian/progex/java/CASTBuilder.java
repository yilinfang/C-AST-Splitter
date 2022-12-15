package ghaffarian.progex.java;

import ghaffarian.graphs.Edge;
import ghaffarian.nanologger.Logger;
import ghaffarian.progex.graphs.ast.ASEdge;
import ghaffarian.progex.graphs.ast.ASNode;
import ghaffarian.progex.graphs.ast.AbstractSyntaxTree;
import ghaffarian.progex.java.parser.*;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.swing.plaf.synth.SynthEditorPaneUI;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class CASTBuilder {

    public static AbstractSyntaxTree build(String cFile, String nodeLevel) throws IOException {
        return build(new File(cFile), nodeLevel);
    }

    public static AbstractSyntaxTree build(File cFile, String nodeLevel) throws IOException {
        if (!cFile.getName().endsWith(".c"))
            throw new IOException("Not a C File!");
        InputStream inFile = new FileInputStream(cFile);
        ANTLRInputStream input = new ANTLRInputStream(inFile);
        CLexer lexer = new CLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CParser parser = new CParser(tokens);
        ParseTree tree = parser.compilationUnit();
        //show AST in console
//      System.out.println(tree.toStringTree(parser));

//      show AST in GUI
//      TreeViewer viewr = new TreeViewer(Arrays.asList(
//              parser.getRuleNames()),tree);
//      viewr.open();
        return build(cFile.getPath(), tree, null, null, nodeLevel, tokens);
    }


    public static AbstractSyntaxTree build(String filePath, ParseTree tree,
                                           String propKey, Map<ParserRuleContext, Object> ctxProps, String nodeLevel, CommonTokenStream tokens) {
        CASTBuilder.AbstractSyntaxVisitor visitor = new AbstractSyntaxVisitor(filePath, propKey, ctxProps, nodeLevel);
        Logger.debug("Visitor building AST of: " + filePath);
        return visitor.build(tree, tokens);
    }

    private static class AbstractSyntaxVisitor extends CBaseVisitor<String> {
        private CommonTokenStream tokenStream;
        private String propKey;
        private String typeModifier;
        private String memberModifier;
        private String nodeLevel;
        private Deque<ASNode> parentStack;
        private Deque<ASNode> slicedParentStack;
        private Deque<AbstractSyntaxTree> presentSubTreeStack;
        private final AbstractSyntaxTree AST;
        private Map<ASNode, AbstractSyntaxTree> slicedAST;
        private Map<String, String> vars, fields, methods;
        private AbstractSyntaxTree presentSubTree;
        private int varsCounter, fieldsCounter, methodsCounter, stmtBlockCounter;
        private Map<ParserRuleContext, Object> contexutalProperties;

        public AbstractSyntaxVisitor(String filePath, String propKey, Map<ParserRuleContext, Object> ctxProps, String nodeLevel) {
            parentStack = new ArrayDeque<>();
            slicedParentStack = new ArrayDeque<>();
            presentSubTreeStack = new ArrayDeque<>();
            AST = new AbstractSyntaxTree(filePath);
            slicedAST = new LinkedHashMap<>();
            presentSubTree = new AbstractSyntaxTree(filePath, false);
//            presentSubTree = new AbstractSyntaxTree(filePath);
            this.propKey = propKey;
            this.nodeLevel = nodeLevel;
            contexutalProperties = ctxProps;
            vars = new LinkedHashMap<>();
            fields = new LinkedHashMap<>();
            methods = new LinkedHashMap<>();
            varsCounter = 0;
            fieldsCounter = 0;
            methodsCounter = 0;
        }

        public AbstractSyntaxTree build(ParseTree tree, CommonTokenStream tokens) {
            System.out.println("Build");
            tokenStream = tokens;
            CParser.CompilationUnitContext rootCntx = (CParser.CompilationUnitContext) tree;
            AST.root.setCode(new File(AST.filePath).getName());
            parentStack.push(AST.root);
            if (rootCntx.translationUnit().externalDeclaration() != null) {
//                for (CParser.TranslationUnitContext translationUnitContext : rootCntx.translationUnit())
                for (CParser.ExternalDeclarationContext externalDeclarationContext : rootCntx.translationUnit().externalDeclaration()) {
                    if (externalDeclarationContext != null) {
                        ASNode sliceMethodNode = new ASNode(ASNode.Type.METHOD);
                        sliceMethodNode.setLineOfCode(externalDeclarationContext.getStart().getLine());
                        sliceMethodNode.setProperty("is_sliced_root", true);
                        presentSubTree.addVertex(sliceMethodNode);
                        slicedParentStack.push(sliceMethodNode);
                        visit(externalDeclarationContext);
                    }
                }
            }
            vars.clear();
            fields.clear();
            methods.clear();
            AbstractSyntaxTree compelete_sliced_AST = AST;
            for (AbstractSyntaxTree value : slicedAST.values()) {
                for (ASNode node : value.getAllVertices()) {
                    compelete_sliced_AST.addVertex(node);
                }
                for (Edge<ASNode, ASEdge> edge : value.getAllEdges()) {
                    compelete_sliced_AST.addEdge(edge.source, edge.target);
                }
            }
            return compelete_sliced_AST;
        }

        public void initNestedBlock() {
            if (presentSubTree.getAllEdges().size() > 0) {
                ASNode curentParentNode = slicedParentStack.peek();
                if (curentParentNode.getType() == ASNode.Type.BLOCK || curentParentNode.getType() == ASNode.Type.THEN) {
                    presentSubTreeStack.push(presentSubTree);
                    presentSubTree = new AbstractSyntaxTree(presentSubTree.filePath, false);

                } else {
                    slicedAST.put(slicedParentStack.pop(), presentSubTree);
                    presentSubTree = new AbstractSyntaxTree(presentSubTree.filePath, false);
                }
            }
        }

        public void initPresentSubTree() {
            if (presentSubTreeStack.peek() == null) {
                presentSubTree = new AbstractSyntaxTree(presentSubTree.filePath, false);
            } else {
                presentSubTree = presentSubTreeStack.pop();
            }
        }

        @Override
        public String visitFunctionDefinition(CParser.FunctionDefinitionContext ctx) {
            System.out.println("Function Definition");
            if (nodeLevel == "block") {
//                ASNode modifierNode = new ASNode(ASNode.Type.MODIFIER);
//                modifierNode.setCode(memberModifier);
//                modifierNode.setLineOfCode(ctx.getStart().getLine());
//                Logger.debug("Adding method modifier");
//                presentSubTree.addVertex(modifierNode);
//                presentSubTree.addEdge(slicedParentStack.peek(), modifierNode);

                ASNode retNode = new ASNode(ASNode.Type.RETURN);
                retNode.setCode(ctx.getChild(0).getText());
                retNode.setLineOfCode(ctx.getStart().getLine());
                Logger.debug("Adding method type");
                presentSubTree.addVertex(retNode);
                presentSubTree.addEdge(slicedParentStack.peek(), retNode);

                ++methodsCounter;
                ASNode nameNode = new ASNode(ASNode.Type.NAME);
                String methodName = ctx.declarator().directDeclarator().directDeclarator().Identifier().getText();
                String normalized = "$METHOD_" + methodsCounter;
                methods.put(methodName, normalized);
                nameNode.setCode(methodName);
                nameNode.setNormalizedCode(normalized);
                nameNode.setLineOfCode(ctx.getStart().getLine());
                Logger.debug("Adding method name");
                presentSubTree.addVertex(nameNode);
                presentSubTree.addEdge(slicedParentStack.peek(), nameNode);
                if (ctx.declarator().directDeclarator().parameterTypeList() != null) {
                    ASNode paramsNode = new ASNode(ASNode.Type.PARAMS);
                    paramsNode.setLineOfCode(ctx.declarator().directDeclarator().getStart().getLine());
                    Logger.debug("Adding method params node");
                    presentSubTree.addVertex(paramsNode);
                    presentSubTree.addEdge(slicedParentStack.peek(), paramsNode);
                    slicedParentStack.push(paramsNode);
                    for (CParser.ParameterDeclarationContext paramctx :
                            ctx.declarator().directDeclarator().parameterTypeList().parameterList().parameterDeclaration()) {
                        ASNode varNode = new ASNode(ASNode.Type.VARIABLE);
                        varNode.setLineOfCode(paramctx.getStart().getLine());
                        presentSubTree.addVertex(varNode);
                        presentSubTree.addEdge(slicedParentStack.peek(), varNode);

                        ASNode type = new ASNode(ASNode.Type.TYPE);
                        type.setCode(paramctx.declarationSpecifiers().getText());
                        type.setLineOfCode(paramctx.declarationSpecifiers().getStart().getLine());

                        ++varsCounter;
                        ASNode name = new ASNode(ASNode.Type.NAME);
                        normalized = "%VARL_" + varsCounter;
                        vars.put(paramctx.declarator().directDeclarator().Identifier().getText(), normalized);
                        name.setCode(paramctx.declarator().directDeclarator().getText());
                        name.setNormalizedCode(normalized);
                        name.setLineOfCode(paramctx.declarator().directDeclarator().getStart().getLine());
                        presentSubTree.addVertex(name);
                        presentSubTree.addEdge(varNode, name);
                    }
                    slicedParentStack.pop();
                }
                slicedAST.put(slicedParentStack.pop(), presentSubTree);
                presentSubTree = new AbstractSyntaxTree(presentSubTree.filePath, false);
            }
            if (ctx.compoundStatement() != null) {
                if (nodeLevel == "block") {
                    ASNode methodBody = new ASNode(ASNode.Type.METHOD_BODY);
                    methodBody.setLineOfCode(ctx.compoundStatement().getStart().getLine());
                    Logger.debug("Adding method block");
                    AST.addVertex(methodBody);
                    AST.addEdge(parentStack.peek(), methodBody);
                    parentStack.push(methodBody);
                    visitChildren(ctx.compoundStatement());
                    if (presentSubTree.getAllVertices().size() > 0) {
                        slicedAST.put(slicedParentStack.pop(), presentSubTree);
                        presentSubTree = new AbstractSyntaxTree(presentSubTree.filePath, false);

                    }
                    parentStack.pop();
//                    slicedParentStack.pop();
                    resetLocalVars();
                }
            }
            return "";
        }

        @Override
        public String visitDeclaration(CParser.DeclarationContext ctx) {
            System.out.println("Declaration");
            if (ctx.initDeclaratorList() == null) {
                ASNode varNode = new ASNode(ASNode.Type.VARIABLE);
                varNode.setLineOfCode(ctx.declarationSpecifiers().getStart().getLine());
                presentSubTree.addVertex(varNode);
                try {
                    presentSubTree.addEdge(slicedParentStack.peek(), varNode);
                } catch (Exception e) {
                    ++stmtBlockCounter;
                    String normalizedBlock = "$STMTBLOCK_" + stmtBlockCounter;

                    ASNode slicedSTMTSNode = new ASNode(ASNode.Type.STMTS);
                    slicedSTMTSNode.setLineOfCode(ctx.getStart().getLine());
                    slicedSTMTSNode.setNormalizedCode(normalizedBlock);
                    presentSubTree.addVertex(slicedSTMTSNode);
                    slicedParentStack.push(slicedSTMTSNode);

                    presentSubTree.addEdge(slicedParentStack.peek(), varNode);

                    ASNode STMTSNode = new ASNode(ASNode.Type.STMTS);
                    STMTSNode.setLineOfCode(ctx.getStart().getLine());
                    STMTSNode.setNormalizedCode(normalizedBlock);
                    AST.addVertex(STMTSNode);
                    AST.addEdge(parentStack.peek(), STMTSNode);
                }
                ASNode typeNode = new ASNode(ASNode.Type.TYPE);
                typeNode.setCode(ctx.declarationSpecifiers().declarationSpecifier(0).getText());
                typeNode.setLineOfCode(ctx.declarationSpecifiers().getStart().getLine());
                presentSubTree.addVertex(typeNode);
                presentSubTree.addEdge(varNode, typeNode);

                ++varsCounter;
                ASNode nameNode = new ASNode(ASNode.Type.NAME);
                String normalized = "$VARL_" + varsCounter;
                vars.put(ctx.declarationSpecifiers().declarationSpecifier(1).getText(), normalized);
                nameNode.setCode(ctx.declarationSpecifiers().getText());
                nameNode.setNormalizedCode(normalized);
                nameNode.setLineOfCode(ctx.declarationSpecifiers().getStart().getLine());
                presentSubTree.addVertex(nameNode);
                presentSubTree.addEdge(varNode, nameNode);
                return "";
            }
            for (CParser.InitDeclaratorContext varctx : ctx.initDeclaratorList().initDeclarator()) {
                ASNode varNode = new ASNode(ASNode.Type.VARIABLE);
                varNode.setLineOfCode(varctx.getStart().getLine());
                presentSubTree.addVertex(varNode);
                try {
                    presentSubTree.addEdge(slicedParentStack.peek(), varNode);
                } catch (Exception e) {
                    ++stmtBlockCounter;
                    String normalizedBlock = "$STMTBLOCK_" + stmtBlockCounter;

                    ASNode slicedSTMTSNode = new ASNode(ASNode.Type.STMTS);
                    slicedSTMTSNode.setLineOfCode(ctx.getStart().getLine());
                    slicedSTMTSNode.setNormalizedCode(normalizedBlock);
                    presentSubTree.addVertex(slicedSTMTSNode);
                    slicedParentStack.push(slicedSTMTSNode);

                    presentSubTree.addEdge(slicedParentStack.peek(), varNode);

                    ASNode STMTSNode = new ASNode(ASNode.Type.STMTS);
                    STMTSNode.setLineOfCode(ctx.getStart().getLine());
                    STMTSNode.setNormalizedCode(normalizedBlock);
                    AST.addVertex(STMTSNode);
                    AST.addEdge(parentStack.peek(), STMTSNode);
                }
                ASNode typeNode = new ASNode(ASNode.Type.TYPE);
                typeNode.setCode(ctx.declarationSpecifiers().getText());
                typeNode.setLineOfCode(ctx.declarationSpecifiers().getStart().getLine());
                presentSubTree.addVertex(typeNode);
                presentSubTree.addEdge(varNode, typeNode);

                ++varsCounter;
                ASNode nameNode = new ASNode(ASNode.Type.NAME);
                String normalized = "$VARL_" + varsCounter;
                vars.put(varctx.declarator().directDeclarator().Identifier().getText(), normalized);
                nameNode.setCode(varctx.declarator().directDeclarator().getText());
                nameNode.setNormalizedCode(normalized);
                nameNode.setLineOfCode(varctx.declarator().directDeclarator().getStart().getLine());
                presentSubTree.addVertex(nameNode);
                presentSubTree.addEdge(varNode, nameNode);

                if (varctx.initializer() != null) {
                    ASNode initNode = new ASNode(ASNode.Type.INIT_VALUE);
                    initNode.setCode(" = " + getOriginalCodeText(varctx.initializer()));
                    initNode.setNormalizedCode(" = " + visit(varctx.initializer()));
                    initNode.setLineOfCode(varctx.initializer().getStart().getLine());
                    presentSubTree.addVertex(initNode);
                    presentSubTree.addEdge(varNode, initNode);
                }
            }
            return "";
        }

        private String getOriginalCodeText(ParserRuleContext ctx) {
            if (ctx == null)
                return "";
            int start = ctx.start.getStartIndex();
            int stop = ctx.stop.getStopIndex();
//            FormattedText
            Interval interval = new Interval(start, stop);

//            CharStream stream = ctx.getInputStream();
//    		JavaLexer lexer = new JavaLexer(stream);
//    		CommonTokenStream   tokenStream  = new CommonTokenStream(lexer);
            List<Token> ruleTokens = tokenStream.getTokens(ctx.start.getTokenIndex(), ctx.stop.getTokenIndex());
//            String text = " ";
//            for (int i = interval.a; i <= interval.b; i++) {
//              text += tokens.get(i).getText();
//              text += " ";
//            }
//            return text;
//            String s = new String(ctx.start.getInputStream()["data"],start,stop);
            String text = " ";
            for (Token item : ruleTokens) {
                text += item.getText();
                text += " ";
//    			text += ": ";
            }
            return text;
//            return ctx.start.getInputStream().getText(interval);
        }

        private void visitStatement(ParserRuleContext ctx, String normalized) {
            System.out.println("Visiting statement");
            Logger.printf(Logger.Level.DEBUG, "Visiting: (%d)  %s", ctx.getStart().getLine(), getOriginalCodeText(ctx));
            ASNode statementNode = new ASNode(ASNode.Type.STATEMENT);
            statementNode.setCode(getOriginalCodeText(ctx));
            statementNode.setNormalizedCode(normalized);
            statementNode.setLineOfCode(ctx.getStart().getLine());
            Logger.debug("Adding statement " + ctx.getStart().getLine());
            if (nodeLevel == "statement") {
                AST.addVertex(statementNode);
                AST.addEdge(parentStack.peek(), statementNode);
            } else if (nodeLevel == "block") {
//            	presentSubTree.addVertex(slicedParentStack.peek());
                try {
                    presentSubTree.addVertex(statementNode);
                    presentSubTree.addEdge(slicedParentStack.peek(), statementNode);

                } catch (Exception e) {
                    presentSubTree.removeVertex(statementNode);
//            		presentSubTree.
                    ++stmtBlockCounter;
                    String normalizedBlock = "$STMTBLOCK_" + stmtBlockCounter;
                    //++stmtBlockCounter;
                    //String normalizedBlock = "$STMTBLOCK_" + stmtBlockCounter;
                    //nameNode.setNormalizedCode(normalized);
                    //slicedSTMTSNode.setNormalizedCode(normalizedBlock);
                    //STMTSNode.setNormalizedCode(normalizedBlock);
                    ASNode slicedSTMTSNode = new ASNode(ASNode.Type.STMTS);
                    slicedSTMTSNode.setLineOfCode(ctx.getStart().getLine());
                    slicedSTMTSNode.setNormalizedCode(normalizedBlock);
                    presentSubTree.addVertex(slicedSTMTSNode);
                    slicedParentStack.push(slicedSTMTSNode);
                    presentSubTree.addVertex(statementNode);
                    presentSubTree.addEdge(slicedParentStack.peek(), statementNode);

                    ASNode STMTSNode = new ASNode(ASNode.Type.STMTS);
                    STMTSNode.setLineOfCode(ctx.getStart().getLine());
                    STMTSNode.setNormalizedCode(normalizedBlock);

//            		ASNode  curentParentNode = slicedParentStack.peek();
//            	 	if (curentParentNode != null && curentParentNode.getType() == ASNode.Type.BLOCK) {
//                		AbstractSyntaxTree subTree = presentSubTreeStack.pop();
//                		subTree.addVertex(STMTSNode);
//                		subTree.addEdge(slicedParentStack.peek(), STMTSNode);
//                		presentSubTreeStack.push(subTree);
//                	}
//                	else {
//                        this.AST.addVertex(STMTSNode);
//                        this.AST.addEdge(parentStack.peek(), STMTSNode);
//                	}
                    AST.addVertex(STMTSNode);
                    AST.addEdge(parentStack.peek(), STMTSNode);
                }


            }
        }

        @Override
        public String visitExpressionStatement(CParser.ExpressionStatementContext ctx) {
            visitStatement(ctx, visit(ctx.expression()));
            return "";
        }

        @Override
        public String visitJumpStatement(CParser.JumpStatementContext ctx) {
            if (ctx.Identifier() == null) {
                visitStatement(ctx, null);
            } else {
                String str = ctx.Identifier().getText();
                visitStatement(ctx, str + " &LABEL");
            }
            return "";
        }

        public void processLabelStatement(CParser.LabeledStatementContext ctx, AbstractSyntaxTree tree) {
            ASNode slicedLabelNode = new ASNode(ASNode.Type.LABELED);
            if (nodeLevel == "block") {
//            	tree = new AbstractSyntaxTree(presentSubTree.filePath, false);
                ASNode labelNode = new ASNode(ASNode.Type.LABELED);
                labelNode.setLineOfCode(ctx.getStart().getLine());
                labelNode.setProperty("is_sliced_root", true);

                ASNode curentParentNode = slicedParentStack.peek();
                if (curentParentNode != null && curentParentNode.getType() == ASNode.Type.BLOCK) {
                    labelNode.setType(ASNode.Type.NESTED_LABELED);
                    slicedLabelNode.setType(ASNode.Type.NESTED_LABELED);
                    AbstractSyntaxTree subTree = presentSubTreeStack.pop();
                    subTree.addVertex(labelNode);
                    subTree.addEdge(slicedParentStack.peek(), labelNode);
                    presentSubTreeStack.push(subTree);
                } else {
                    AST.addVertex(labelNode);
                    AST.addEdge(parentStack.peek(), labelNode);
                }
                slicedParentStack.push(labelNode);
            }

            slicedLabelNode.setLineOfCode(ctx.getStart().getLine());
            tree.addVertex(slicedLabelNode);
            if (nodeLevel == "statement")
                tree.addEdge(parentStack.peek(), slicedLabelNode);
            //
            ASNode labelName = new ASNode(ASNode.Type.NAME);
            labelName.setCode(ctx.Identifier().getText());
            labelName.setNormalizedCode("$LABEL");
            labelName.setLineOfCode(ctx.getStart().getLine());
            tree.addVertex(labelName);
            tree.addEdge(slicedLabelNode, labelName);
            //


//             slicedParentStack.push(slicedLabelNode);
            slicedParentStack.push(slicedLabelNode);

            visit(ctx.statement());
//             slicedParentStack.pop();
//             ASNode slicedLabelBlockNode = new ASNode(ASNode.Type.BLOCK);
//             slicedLabelBlockNode.setLineOfCode(ctx.getStart().getLine());
//             tree.addVertex(slicedLabelBlockNode);
//             tree.addEdge(slicedLabelNode,slicedLabelBlockNode);
//             slicedParentStack.push(slicedLabelBlockNode);
        }

        @Override
        public String visitLabeledStatement(CParser.LabeledStatementContext ctx) {
//            return super.visitLabeledStatement(ctx);
            if (nodeLevel == "block") {
                initNestedBlock();
                processLabelStatement(ctx, presentSubTree);
                if (presentSubTree.getAllEdges().size() > 0) {
                    slicedAST.put(slicedParentStack.pop(), presentSubTree);
                    initPresentSubTree();
                }
            }
            return "";
        }

        public void processIfStatement(CParser.IfStatementContext ctx, AbstractSyntaxTree tree, Deque<ASNode> currentStack) {
            ASNode slicedIfNode = new ASNode(ASNode.Type.IF);
            slicedIfNode.setLineOfCode(ctx.getStart().getLine());
            if (nodeLevel == "block") {
//            	tree = new AbstractSyntaxTree(presentSubTree.filePath, false);
                ASNode ifNode = new ASNode(ASNode.Type.IF);
                ifNode.setLineOfCode(ctx.getStart().getLine());
                ifNode.setProperty("is_sliced_root", true);

                // whether it is Nested_If_statement
                ASNode curentParentNode = slicedParentStack.peek();
                if (curentParentNode != null && curentParentNode.getType() == ASNode.Type.BLOCK && presentSubTreeStack.peek() != null) {
                    ifNode.setType(ASNode.Type.NESTED_IF);
                    slicedIfNode.setType(ASNode.Type.NESTED_IF);
                    AbstractSyntaxTree subTree = presentSubTreeStack.pop();
                    subTree.addVertex(ifNode);
                    subTree.addEdge(slicedParentStack.peek(), ifNode);
                    presentSubTreeStack.push(subTree);
                } else {
                    AST.addVertex(ifNode);
                    AST.addEdge(parentStack.peek(), ifNode);
                }
                slicedParentStack.push(ifNode);
            }


            tree.addVertex(slicedIfNode);
            if (nodeLevel == "statement")
                tree.addEdge(parentStack.peek(), slicedIfNode);

            //
            ASNode cond = new ASNode(ASNode.Type.CONDITION);
            cond.setCode(getOriginalCodeText(ctx.expression()));
            cond.setNormalizedCode(visit(ctx.expression()));
            cond.setLineOfCode(ctx.getStart().getLine());
            tree.addVertex(cond);
            tree.addEdge(slicedIfNode, cond);
            //
            ASNode thenNode = new ASNode(ASNode.Type.BLOCK);
//            ASNode thenNode = new ASNode(ASNode.Type.THEN);
            thenNode.setLineOfCode(ctx.statement(0).getStart().getLine());
            tree.addVertex(thenNode);
            tree.addEdge(slicedIfNode, thenNode);
            currentStack.push(thenNode);
            visit(ctx.statement(0));
            currentStack.pop();
            //
            if (ctx.statement(1) != null) {
                ASNode elseNode = new ASNode(ASNode.Type.ELSE);
                elseNode.setLineOfCode(ctx.statement(1).getStart().getLine());
                tree.addVertex(elseNode);
                tree.addEdge(slicedIfNode, elseNode);
//                currentStack.push(elseNode);

                ASNode elseBlock = new ASNode(ASNode.Type.BLOCK);
                elseBlock.setLineOfCode(ctx.statement(1).getStart().getLine());
                tree.addVertex(elseBlock);
                tree.addEdge(elseNode, elseBlock);
                slicedParentStack.push(elseBlock);
                visit(ctx.statement(1));
                slicedParentStack.pop();

//                visit(ctx.statement(1));
////                if  ( nodeLevel=="block" )
//                currentStack.pop();
            }
        }
        @Override
        public String visitIfStatement(CParser.IfStatementContext ctx) {
            System.out.println("Visit ifStatement");
            if (nodeLevel == "statement") {
                processIfStatement(ctx, AST, parentStack);
                parentStack.pop();
            } else if (nodeLevel == "block") {
                initNestedBlock();
                processIfStatement(ctx, presentSubTree, slicedParentStack);
                if (presentSubTree.getAllEdges().size() > 0) {
                    slicedAST.put(slicedParentStack.pop(), presentSubTree);
                }
                initPresentSubTree();
            }
            return "";
        }

        private void resetLocalVars() {
            vars.clear();
            varsCounter = 0;
        }

        private String normalizedIdentifier(TerminalNode id) {

            String normalized = vars.get(id.getText());
            if (normalized == null || normalized.isEmpty())
                normalized = fields.get(id.getText());
            if (normalized == null || normalized.isEmpty())
                normalized = methods.get(id.getText());
            if (normalized == null || normalized.isEmpty())
                normalized = id.getText();
            return normalized;
        }
    }
}
