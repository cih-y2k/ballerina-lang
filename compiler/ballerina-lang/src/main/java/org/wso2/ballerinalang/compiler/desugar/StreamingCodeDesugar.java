/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.ballerinalang.compiler.desugar;

import org.ballerinalang.model.TreeBuilder;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.model.symbols.SymbolKind;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.clauses.HavingNode;
import org.ballerinalang.model.tree.clauses.OrderByVariableNode;
import org.ballerinalang.model.tree.clauses.SelectExpressionNode;
import org.ballerinalang.model.tree.clauses.StreamingInput;
import org.ballerinalang.model.tree.expressions.ExpressionNode;
import org.ballerinalang.model.tree.statements.StatementNode;
import org.ballerinalang.model.tree.types.TypeNode;
import org.ballerinalang.util.diagnostic.DiagnosticCode;
import org.wso2.ballerinalang.compiler.semantics.analyzer.SymbolEnter;
import org.wso2.ballerinalang.compiler.semantics.analyzer.SymbolResolver;
import org.wso2.ballerinalang.compiler.semantics.analyzer.TypeChecker;
import org.wso2.ballerinalang.compiler.semantics.model.BLangBuiltInMethod;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolEnv;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BAttachedFunction;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BObjectTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BPackageSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BVarSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.types.BArrayType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BField;
import org.wso2.ballerinalang.compiler.semantics.model.types.BInvokableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BMapType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BObjectType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BRecordType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BStreamType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BTableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BUnionType;
import org.wso2.ballerinalang.compiler.tree.BLangFunction;
import org.wso2.ballerinalang.compiler.tree.BLangIdentifier;
import org.wso2.ballerinalang.compiler.tree.BLangNode;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.tree.BLangSimpleVariable;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangGroupBy;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangHaving;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangJoinStreamingInput;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangOrderBy;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangOrderByVariable;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangSelectClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangSelectExpression;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangStreamAction;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangStreamingInput;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangWhere;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangWindow;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangArrayLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangBinaryExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangFieldBasedAccess;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangIndexBasedAccess;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLambdaFunction;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangNamedArgsExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangVariableReference;
import org.wso2.ballerinalang.compiler.tree.statements.BLangAssignment;
import org.wso2.ballerinalang.compiler.tree.statements.BLangBlockStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangExpressionStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangForeach;
import org.wso2.ballerinalang.compiler.tree.statements.BLangForever;
import org.wso2.ballerinalang.compiler.tree.statements.BLangReturn;
import org.wso2.ballerinalang.compiler.tree.statements.BLangSimpleVariableDef;
import org.wso2.ballerinalang.compiler.tree.statements.BLangStatement;
import org.wso2.ballerinalang.compiler.tree.statements.BLangStreamingQueryStatement;
import org.wso2.ballerinalang.compiler.tree.types.BLangType;
import org.wso2.ballerinalang.compiler.tree.types.BLangUserDefinedType;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.FieldKind;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.compiler.util.Names;
import org.wso2.ballerinalang.compiler.util.TypeTags;
import org.wso2.ballerinalang.compiler.util.diagnotic.BLangDiagnosticLog;
import org.wso2.ballerinalang.compiler.util.diagnotic.DiagnosticPos;
import org.wso2.ballerinalang.util.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Class responsible for desugar streaming pipeline into actual Ballerina code.
 *
 * @since 0.980.0
 */
public class StreamingCodeDesugar extends BLangNodeVisitor {

    private static final String FUNC_CALLER = "$lambda$streaming";
    private static final String OUTPUT_FUNC_REFERENCE = "$lambda$streaming$output$function";
    private static final String OUTPUT_FUNC_VAR_ARG = "$lambda$streaming$output$function$var$arg";
    private static final String WINDOW_FUNC_REFERENCE = "$lambda$streaming$window$reference";
    private static final String OUTPUT_PROCESS_FUNC_REFERENCE = "$lambda$streaming$output$process";
    private static final String ORDER_BY_PROCESS_FUNC_REFERENCE = "$lambda$streaming$ordering$process";
    private static final String ORDERING_FUNC_ARRAY_REFERENCE = "$lambda$streaming$orderby$funcarray$variable";
    private static final String ORDERING_FUNC_VAR_ARG = "$lambda$streaming$orderby$func$var$arg";
    private static final String FILTER_FUNC_REFERENCE = "$lambda$streaming$filter";
    private static final String SELECT_WITH_GROUP_BY_FUNC_REFERENCE = "$lambda$streaming$groupby$select";
    private static final String JOIN_PROCESS_FUNC_REFERENCE = "$lambda$streaming$join$process";
    private static final String TABLE_JOIN_PROCESS_FUNC_REFERENCE = "$lambda$streaming$table$join$process";
    private static final String INPUT_STREAM_PARAM_REFERENCE = "$lambda$streaming$input$variable";
    private static final String FILTER_LAMBDA_PARAM_REFERENCE = "$lambda$streaming$filter$input$variable";
    private static final String HAVING_LAMBDA_PARAM_REFERENCE = "$lambda$streaming$having$input$variable";
    private static final String SELECT_LAMBDA_PARAM_REFERENCE = "$lambda$streaming$simple$select$input$variable";
    private static final String TABLE_JOIN_LAMBDA_PARAM_REFERENCE = "$lambda$streaming$table$join$input$variable";
    private static final String JOIN_CONDITION_LAMBDA_PARAM_REFERENCE =
            "$lambda$streaming$join$onCondition$input$variable";
    private static final String SELECT_WITH_GROUP_BY_LAMBDA_PARAM_REFERENCE =
            "$lambda$streaming$groupby$select$input$variable";
    private static final String STREAM_EVENT_ARRAY_PARAM_REFERENCE = "$lambda$streaming$stream$event$variable";
    private static final String OUTPUT_EVENT_SELECTOR_PARAM_REFERENCE =
            "$lambda$streaming$output$event$selector$variable";
    private static final String VAR_OUTPUT_EVENTS = "$lambda$streaming$output$process$output$events$variable";
    private static final String VAR_RESULTS_TABLE = "$lambda$streaming$table$join$on$condition$result$variable";
    private static final String VAR_FOREACH_VAL = "$lambda$streaming$foreach$key$val$variable";
    private static final String NEXT_PROCESS_METHOD_NAME = "process";
    private static final String STREAM_EVENT_OBJECT_NAME = "StreamEvent";
    private static final String FILTER_OBJECT_NAME = "Filter";
    private static final String WINDOW_OBJECT_NAME = "Window";
    private static final String AGGREGATOR_OBJECT_NAME = "Aggregator";
    private static final String OUTPUT_PROCESS_OBJECT_NAME = "OutputProcess";
    private static final String ORDER_BY_PROCESS_OBJECT_NAME = "OrderBy";
    private static final String CREATE_OUTPUT_PROCESS_METHOD_NAME = "createOutputProcess";
    private static final String CREATE_FILTER_METHOD_NAME = "createFilter";
    private static final String SELECT_WITH_GROUP_BY_OBJECT_NAME = "Select";
    private static final String JOIN_PROCESS_OBJECT_NAME = "JoinProcesor";
    private static final String TABLE_JOIN_PROCESS_OBJECT_NAME = "TableJoinProcessor";
    private static final String CREATE_SELECT_WITH_GROUP_BY_METHOD_NAME = "createSelect";
    private static final String CREATE_ORDER_BY_METHOD_NAME = "createOrderBy";
    private static final String CREATE_STREAM_JOIN_PROCESS_METHOD_NAME = "createStreamJoinProcessor";
    private static final String CREATE_TABLE_JOIN_PROCESS_METHOD_NAME = "createTableJoinProcessor";
    private static final String SET_JOIN_PROPERTIES_METHOD_NAME = "setJoinProperties";
    private static final String SET_RHS_METHOD_NAME = "setRHS";
    private static final String SET_LHS_METHOD_NAME = "setLHS";
    private static final String LENGTH_WINDOW_METHOD_NAME = "length";
    private static final String EVENT_DATA_VARIABLE_NAME = "data";
    private static final String EVENT_TYPE_VARIABLE_NAME = "eventType";
    private static final String BUILD_STREAM_EVENT_METHOD_NAME = "buildStreamEvent";
    private static final String STREAM_SUBSCRIBE_METHOD_NAME = "stream.subscribe";
    private static final String JOIN_TYPE = "JoinType";

    private static final CompilerContext.Key<StreamingCodeDesugar> STREAMING_DESUGAR_KEY =
            new CompilerContext.Key<>();
    private static final String ORDER_BY_FIELD_ATTR = "fieldFuncs";
    private static final String STR_ENDING = "ENDING";
    private static final String ORDER_TYPE_ASC = "ASC" + STR_ENDING;
    private static final String NEXT_PROCESS_POINTER_ARG_NAME = "nextProcessPointer";
    private static final String ON_CONDITION_NAMED_ARG_NAME = "conditionFunc";
    private static final int STREAM_EVENT_DATA_FIELD_INDEX = 2;

    private final SymbolTable symTable;
    private final SymbolResolver symResolver;
    private final SymbolEnter symbolEnter;
    private TypeChecker typeChecker;
    private Desugar desugar;
    private final Names names;
    private final StreamsPreSelectDesuagr preSelectDesuagr;
    private final StreamsPostSelectDesugar postSelectDesugar;
    private BLangDiagnosticLog dlog;
    private int lambdaFunctionCount = 0;
    private SymbolEnv env;
    private List<BLangStatement> stmts;
    private BLangVariableReference rhsStream, lhsStream;
    private Stack<BLangExpression> exprStack = new Stack<>();
    private BType outputEventType;
    private Stack<BVarSymbol> nextProcessVarSymbolStack = new Stack<>();
    private Stack<BVarSymbol> joinProcessorStack = new Stack<>();
    private boolean isJoin = false;
    private boolean isInJoin = false;
    private boolean isTableJoin = false;
    // Contains the StreamEvent.data variable args in conditional lambda functions like where and join on condition
    private Map<String, String> streamAliasMap;

    private StreamingCodeDesugar(CompilerContext context) {
        context.put(STREAMING_DESUGAR_KEY, this);
        this.symTable = SymbolTable.getInstance(context);
        this.symResolver = SymbolResolver.getInstance(context);
        this.symbolEnter = SymbolEnter.getInstance(context);
        this.names = Names.getInstance(context);
        this.typeChecker = TypeChecker.getInstance(context);
        this.dlog = BLangDiagnosticLog.getInstance(context);
        this.desugar = Desugar.getInstance(context);
        this.preSelectDesuagr = StreamsPreSelectDesuagr.getInstance(context);
        this.postSelectDesugar = StreamsPostSelectDesugar.getInstance(context);
    }

    public static StreamingCodeDesugar getInstance(CompilerContext context) {
        StreamingCodeDesugar desugar = context.get(STREAMING_DESUGAR_KEY);
        if (desugar == null) {
            desugar = new StreamingCodeDesugar(context);
        }

        return desugar;
    }

    public BLangBlockStmt desugar(BLangForever foreverStatement) {

        this.env = foreverStatement.getEnv();
        stmts = new ArrayList<>();
        List<? extends StatementNode> statementNodes = foreverStatement.getStreamingQueryStatements();

        // Generate Streaming Consumer Function
        statementNodes.forEach(statementNode -> ((BLangStatement) statementNode).accept(this));
        return ASTBuilderUtil.createBlockStmt(foreverStatement.pos, stmts);
    }

    @Override
    public void visit(BLangStreamingQueryStatement queryStmt) {

        outputEventType = null;
        rhsStream = null;
        lhsStream = null;
        joinProcessorStack.clear();
        streamAliasMap = new HashMap<>();
        exprStack.empty();
        //Construct the elements to publish events to output stream
        BLangStreamAction streamAction = (BLangStreamAction) queryStmt.getStreamingAction();
        streamAction.accept(this);

        BLangOrderBy orderBy = (BLangOrderBy) queryStmt.getOrderbyClause();
        if (orderBy != null) {
            orderBy.accept(this);
        }

        BLangSelectClause selectClause = (BLangSelectClause) queryStmt.getSelectClause();
        resolveSelectExpressions(selectClause, queryStmt.getStreamingInput());
        selectClause.accept(this);

        BLangJoinStreamingInput joinStreamingInput = (BLangJoinStreamingInput) queryStmt.getJoiningInput();
        if (joinStreamingInput != null) {
            isJoin = true;
            isInJoin = true;
            BLangStreamingInput streamingInput = (BLangStreamingInput) joinStreamingInput.getStreamingInput();
            createStreamAliasMap(streamingInput);
            resolveJoinProperties(queryStmt.getStreamingInput().getStreamReference(),
                                  joinStreamingInput.getStreamingInput().getStreamReference());
            joinStreamingInput.accept(this);
            isInJoin = false;
        }

        //Build elements to consume events from input stream
        BLangStreamingInput streamingInput = (BLangStreamingInput) queryStmt.getStreamingInput();
        createStreamAliasMap(streamingInput);
        streamingInput.accept(this);
    }

    private void resolveSelectExpressions(BLangSelectClause selectClause, StreamingInput streamingInput) {
        if (selectClause.isSelectAll()) {
            List<SelectExpressionNode> selectExprList = new ArrayList<>();
            BLangSimpleVarRef input = (BLangSimpleVarRef) streamingInput.getStreamReference();
            List<BField> inputStructFieldList = ((BRecordType) ((BStreamType) input.type).constraint).fields;
            for (BField field : inputStructFieldList) {
                BLangSelectExpression selectExpr = (BLangSelectExpression) TreeBuilder.createSelectExpressionNode();
                BLangFieldBasedAccess expr = (BLangFieldBasedAccess) TreeBuilder.createFieldBasedAccessNode();
                expr.expr = ASTBuilderUtil.createVariableRef(selectClause.pos, input.symbol);
                expr.symbol = field.symbol;
                expr.type = field.symbol.type;
                expr.pos = selectClause.pos;
                expr.field = ASTBuilderUtil.createIdentifier(selectClause.pos, field.name.value);
                selectExpr.setExpression(expr);
                selectExprList.add(selectExpr);
            }
            selectClause.setSelectExpressions(selectExprList);
        }
    }

    private void resolveJoinProperties(ExpressionNode lhsStreamReference, ExpressionNode rhsStreamReference) {
        if (isTableReference(lhsStreamReference)) {
            lhsStream = (BLangVariableReference) rhsStreamReference;
            rhsStream = (BLangVariableReference) lhsStreamReference;
            exprStack.push((BLangExpression) lhsStreamReference);
            isTableJoin = true;
        } else if (isTableReference(rhsStreamReference)) {
            lhsStream = (BLangVariableReference) lhsStreamReference;
            rhsStream = (BLangVariableReference) rhsStreamReference;
            exprStack.push((BLangExpression) rhsStreamReference);
            isTableJoin = true;
        } else {
            lhsStream = (BLangVariableReference) lhsStreamReference;
            rhsStream = (BLangVariableReference) rhsStreamReference;
            isTableJoin = false;
        }
    }

    private boolean isTableReference(ExpressionNode streamReference) {
        if (streamReference.getKind() == NodeKind.INVOCATION) {
            return ((BLangInvocation) streamReference).type.tsymbol.type == symTable.tableType;
        } else {
            return ((BLangVariableReference) streamReference).type.tsymbol.type == symTable.tableType;
        }
    }

    @Override
    public void visit(BLangOrderBy orderBy) {
        //creating function pointer array which represents the ordering fields
        BObjectTypeSymbol orderByObjSymbol = (BObjectTypeSymbol) symResolver.
                resolvePkgSymbol(orderBy.pos, env, Names.STREAMS_MODULE).
                scope.lookup(new Name(ORDER_BY_PROCESS_OBJECT_NAME)).symbol;
        BType orderingFuncArrayType = getSortFuncArrayType((BObjectType) orderByObjSymbol.type);

        //create symbol representing the ordering function pointer array
        BVarSymbol orderingFuncArrayVarSymbol =
                new BVarSymbol(0, new Name(getVariableName(ORDERING_FUNC_ARRAY_REFERENCE)),
                        orderByObjSymbol.pkgID, orderingFuncArrayType, env.scope.owner);

        //create RHS expression for the ordering function array
        BLangArrayLiteral orderingFuncArrExpr = (BLangArrayLiteral) TreeBuilder.createArrayLiteralNode();
        orderingFuncArrExpr.exprs = new ArrayList<>();
        orderingFuncArrExpr.type = orderingFuncArrayType;

        BLangArrayLiteral sortModesArrayExpr = (BLangArrayLiteral) TreeBuilder.createArrayLiteralNode();
        sortModesArrayExpr.exprs = new ArrayList<>();
        sortModesArrayExpr.type = new BArrayType(symTable.stringType);

        BLangSimpleVariableDef orderingFuncArrDef =
                createOrderingFuncArrayVarDef(orderBy, orderingFuncArrayVarSymbol, orderingFuncArrExpr);
        stmts.add(orderingFuncArrDef);

        for (OrderByVariableNode node : orderBy.getVariables()) {
            BLangOrderByVariable orderByVariable = (BLangOrderByVariable) node;
            orderingFuncArrExpr.exprs.add(createOrderingFunctionPointer((BLangExpression) orderByVariable
                    .getVariableReference()));
            String fieldOrderType = orderByVariable.getOrderByType() + STR_ENDING;
            if (fieldOrderType.isEmpty()) {
                fieldOrderType = ORDER_TYPE_ASC;
            }
            sortModesArrayExpr.exprs.add(ASTBuilderUtil.createLiteral(orderByVariable.pos, symTable.stringType,
                    fieldOrderType.toLowerCase()));
        }

        //Create OrderBy process definition
        BLangSimpleVariableDef orderByProcessInvokableTypeVariableDef =
                createOrderByProcessDef(orderBy, orderingFuncArrDef.var, sortModesArrayExpr);
        stmts.add(orderByProcessInvokableTypeVariableDef);
    }

    private BLangSimpleVariableDef createOrderingFuncArrayVarDef(BLangOrderBy orderBy,
                                                                 BVarSymbol orderingFuncArrayVarSymbol,
                                                                 BLangArrayLiteral orderingFuncArrExpr) {
        BLangSimpleVariable orderingFuncArrVariable = ASTBuilderUtil.
                createVariable(orderBy.pos, getVariableName(ORDERING_FUNC_ARRAY_REFERENCE), orderingFuncArrExpr.type,
                        orderingFuncArrExpr, orderingFuncArrayVarSymbol);

        orderingFuncArrVariable.typeNode = ASTBuilderUtil.createTypeNode(orderingFuncArrExpr.type);
        return ASTBuilderUtil.createVariableDef(orderBy.pos, orderingFuncArrVariable);
    }

    private BLangSimpleVariableDef createOrderByProcessDef(BLangOrderBy orderBy,
                                                           BLangSimpleVariable orderingFuncArrVariable,
                                                           BLangArrayLiteral orderingTypeArrExpr) {
        BLangSimpleVarRef orderingFuncPointerArr = ASTBuilderUtil.createVariableRef(orderBy.pos,
                orderingFuncArrVariable.symbol);
        List<BLangExpression> args = new ArrayList<>();
        args.add(createNextProcessFuncPointer(orderBy.pos));
        args.add(orderingFuncPointerArr);
        args.add(orderingTypeArrExpr);

        BInvokableSymbol orderByProcessInvokableSymbol = (BInvokableSymbol) symResolver.
                resolvePkgSymbol(orderBy.pos, env, Names.STREAMS_MODULE).
                scope.lookup(new Name(CREATE_ORDER_BY_METHOD_NAME)).symbol;
        BType orderByProcessInvokableType = orderByProcessInvokableSymbol.type.getReturnType();
        BVarSymbol orderByProcessInvokableTypeVarSymbol = new BVarSymbol(0,
                new Name(getVariableName(ORDER_BY_PROCESS_FUNC_REFERENCE)), orderByProcessInvokableSymbol.pkgID,
                orderByProcessInvokableType, env.scope.owner);
        nextProcessVarSymbolStack.push(orderByProcessInvokableTypeVarSymbol);

        BLangInvocation orderByProcessMethodInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(orderBy.pos, orderByProcessInvokableSymbol, args, symResolver);
        orderByProcessMethodInvocation.argExprs = args;
        BLangSimpleVariable orderByProcessInvokableTypeVariable = ASTBuilderUtil.
                createVariable(orderBy.pos, getVariableName(ORDER_BY_PROCESS_FUNC_REFERENCE),
                        orderByProcessInvokableType, orderByProcessMethodInvocation,
                        orderByProcessInvokableTypeVarSymbol);

        BLangUserDefinedType userDefinedType = (BLangUserDefinedType) TreeBuilder.createUserDefinedTypeNode();
        userDefinedType.typeName = ASTBuilderUtil.createIdentifier(orderBy.pos, ORDER_BY_PROCESS_OBJECT_NAME);
        userDefinedType.type = orderByProcessInvokableType;
        orderByProcessInvokableTypeVariable.setTypeNode(userDefinedType);
        return ASTBuilderUtil.createVariableDef(orderBy.pos, orderByProcessInvokableTypeVariable);
    }

    private BType getSortFuncArrayType(BObjectType orderByType) {
        BType orderingFuncArrayType;
        List<BField> fields = orderByType.fields;
        //get the ordering functions array type
        //e.g. (function (map<anydata>)returns any)[] ...
        orderingFuncArrayType = fields.stream().filter(field -> field.name.value.equals(ORDER_BY_FIELD_ATTR))
                .findFirst().map(field -> field.type).orElse(null);
        return orderingFuncArrayType;
    }

    private BLangExpression createOrderingFunctionPointer(BLangExpression expr) {
        BLangSimpleVariable orderFuncMapVariable = createMapTypeVariable(getVariableName(ORDERING_FUNC_VAR_ARG),
                expr.pos, env);
        BLangType valueType = ASTBuilderUtil.createTypeNode(symTable.anydataType);
        BLangLambdaFunction orderingFunc = createLambdaWithVarArg(expr.pos, new BLangSimpleVariable[]
                {orderFuncMapVariable}, valueType);
        BLangBlockStmt lambdaBody = orderingFunc.function.body;

        BLangExpression refactoredExpr = desugar.addConversionExprIfRequired((BLangExpression) postSelectDesugar
                .rewrite(expr, orderFuncMapVariable.symbol, outputEventType), symTable.anydataType);

        addReturnStmt(expr.pos, lambdaBody, refactoredExpr);
        return orderingFunc;
    }

    @Override
    public void visit(BLangStreamAction streamAction) {
        BLangLambdaFunction lambdaFunction = (BLangLambdaFunction) streamAction.getInvokableBody();
        lambdaFunction.accept(this);
    }

    //
    // This method creates the constructs to publish output events.
    //
    // eg: Below query,
    //
    //        => (TeacherOutput[] outputEvents) {
    //            foreach var e in outputEvents {
    //                outputStream.publish(e);
    //            }
    //        }
    //
    // convert into below constructs.
    //
    //       function (map[]) outputFunc = function (map[] events) {
    //          foreach var m in events {
    //              TeacherOutput t = <TeacherOutput>TeacherOutput.create(m);
    //              outputStream.publish(t);
    //          }
    //      };
    //
    //      streams:OutputProcess outputProcess = streams:createOutputProcess(outputFunc);
    //
    //
    @Override
    public void visit(BLangLambdaFunction lambdaFunction) {
        //Create lambda function Variable
        BLangSimpleVariable outputFuncArg =
                (BLangSimpleVariable) lambdaFunction.getFunctionNode().getParameters().get(0);
        outputEventType = ((BArrayType) outputFuncArg.type).eType;

        TypeNode typeNode = ASTBuilderUtil.createTypeNode(symTable.nilType);
        BType anydataMapType = symTable.mapAnydataType;
        //create a wrapper lambda expression to invoke the actual streamAction lambda function
        BLangLambdaFunction outputLambdaFunc = createLambdaWithVarArg(lambdaFunction.pos, new BLangSimpleVariable[]{
                ASTBuilderUtil.createVariable(outputFuncArg.pos, getVariableName(OUTPUT_FUNC_VAR_ARG),
                        new BArrayType(anydataMapType), null, new BVarSymbol(0,
                                names.fromString(getVariableName(OUTPUT_FUNC_VAR_ARG)),
                                lambdaFunction.function.symbol.pkgID, new BArrayType(anydataMapType),
                                lambdaFunction.function.symbol.owner))}, typeNode);
        // create `T[] outputEvents`
        BLangSimpleVarRef outputArrayRef =
                createResultArrayRefInForEach(outputLambdaFunc.function.pos, outputEventType,
                                              outputLambdaFunc.function.symbol, outputLambdaFunc.function.body);
        BLangForeach foreach =
                createForEachStmtForArrayConversion(outputLambdaFunc.function.pos, outputLambdaFunc.function.symbol,
                outputLambdaFunc.function.symbol.params.get(0), outputArrayRef);
        outputLambdaFunc.function.body.stmts.add(foreach);

        // pass `T[] outputEvents` created above, to the streaming action
        BLangInvocation streamActionInvocation = ASTBuilderUtil.createInvocationExprForMethod(lambdaFunction.pos,
                lambdaFunction.function.symbol, Lists.of(outputArrayRef), symResolver);
        BLangExpressionStmt streamActionInvocationStmt =
                ASTBuilderUtil.createExpressionStmt(lambdaFunction.function.pos, outputLambdaFunc.function.body);
        streamActionInvocationStmt.expr = streamActionInvocation;

        //Create wrapper lambda expression definition statement
        BLangSimpleVariable outputStreamFunctionVariable = ASTBuilderUtil.
                createVariable(outputLambdaFunc.pos, getVariableName(OUTPUT_FUNC_REFERENCE),
                        outputLambdaFunc.function.symbol.type, outputLambdaFunc, outputLambdaFunc.function.symbol);
        outputStreamFunctionVariable.typeNode = ASTBuilderUtil.createTypeNode(outputLambdaFunc.function.symbol.type);
        BLangSimpleVariableDef outputStreamFunctionVarDef = ASTBuilderUtil.createVariableDef(outputLambdaFunc.pos,
                outputStreamFunctionVariable);
        stmts.add(outputStreamFunctionVarDef);

        //Create output event process definition
        BLangSimpleVarRef outputStreamFunctionSimpleVarRef = ASTBuilderUtil.createVariableRef(outputLambdaFunc.pos,
                outputStreamFunctionVariable.symbol);
        BInvokableSymbol outputProcessInvokableSymbol = (BInvokableSymbol) symResolver.
                resolvePkgSymbol(outputLambdaFunc.pos, env, Names.STREAMS_MODULE).
                scope.lookup(new Name(CREATE_OUTPUT_PROCESS_METHOD_NAME)).symbol;
        BType outputProcessInvokableType = outputProcessInvokableSymbol.type.getReturnType();
        BVarSymbol outputProcessInvokableTypeVarSymbol = new BVarSymbol(0,
                new Name(getVariableName(OUTPUT_PROCESS_FUNC_REFERENCE)), outputProcessInvokableSymbol.pkgID,
                outputProcessInvokableType, env.scope.owner);
        nextProcessVarSymbolStack.push(outputProcessInvokableTypeVarSymbol);

        List<BLangExpression> args = new ArrayList<>();
        args.add(outputStreamFunctionSimpleVarRef);
        BLangInvocation outputProcessMethodInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(outputLambdaFunc.pos, outputProcessInvokableSymbol, args, symResolver);
        outputProcessMethodInvocation.argExprs = args;
        BLangSimpleVariable outputProcessInvokableTypeVariable = ASTBuilderUtil.
                createVariable(lambdaFunction.pos, getVariableName(OUTPUT_PROCESS_FUNC_REFERENCE),
                        outputProcessInvokableType, outputProcessMethodInvocation, outputProcessInvokableTypeVarSymbol);

        BLangUserDefinedType userDefinedType = (BLangUserDefinedType) TreeBuilder.createUserDefinedTypeNode();
        userDefinedType.typeName = ASTBuilderUtil.createIdentifier(outputLambdaFunc.pos, OUTPUT_PROCESS_OBJECT_NAME);
        userDefinedType.type = outputProcessInvokableType;
        outputProcessInvokableTypeVariable.setTypeNode(userDefinedType);
        BLangSimpleVariableDef outputProcessInvokableTypeVariableDef = ASTBuilderUtil.
                createVariableDef(outputLambdaFunc.pos, outputProcessInvokableTypeVariable);
        stmts.add(outputProcessInvokableTypeVariableDef);
    }

    private BLangSimpleVarRef createResultArrayRefInForEach(DiagnosticPos pos, BType outputType, BSymbol symbol,
                                                            BLangBlockStmt body) {
        BType outputArrayType = new BArrayType(outputType);
        BLangArrayLiteral arrayLiteralExpr = (BLangArrayLiteral) TreeBuilder.createArrayLiteralNode();
        arrayLiteralExpr.exprs = new ArrayList<>();
        arrayLiteralExpr.type = outputArrayType;
        BLangSimpleVariable outputArrayVariable =
                ASTBuilderUtil.createVariable(pos, VAR_OUTPUT_EVENTS, outputArrayType, arrayLiteralExpr, null);
        defineVariable(outputArrayVariable, symbol.pkgID, symbol);
        BLangSimpleVariableDef outputArrayVarDef =
                ASTBuilderUtil.createVariableDef(pos, outputArrayVariable);
        body.addStatement(outputArrayVarDef);
        return ASTBuilderUtil.createVariableRef(pos, outputArrayVariable.symbol);
    }

    private BLangForeach createForEachStmtForArrayConversion(DiagnosticPos pos, BSymbol owner, BSymbol
            inputCollectionSymbol, BLangSimpleVarRef outputArrayRef) {
        // foreach v in events {
        //     outputEvents[outputEvents.length()] =  <T>T.convert(v);
        // }
        BLangBlockStmt foreachBody = ASTBuilderUtil.createBlockStmt(pos);

        // Note - int $length$ = outputEvents.length();
        BLangInvocation lengthInvocation = createLengthInvocation(pos, (BVarSymbol) outputArrayRef.symbol);

        final BLangSimpleVariable foreachVariable = createForeachVariables(pos, owner);
        BLangSimpleVarRef foreachVarRef = ASTBuilderUtil.createVariableRef(pos, foreachVariable.symbol);

        // create `outputEvents[k] = <T>T.convert(v);` assignment stmt
        BLangIndexBasedAccess indexAccessExpr = ASTBuilderUtil.createIndexAccessExpr(outputArrayRef, lengthInvocation);
        indexAccessExpr.type = ((BArrayType) outputArrayRef.type).eType;

        // e.g. TeacherOutput.convert(m);
        BLangExpression createInvocation = buildCreateInvocation(foreachVarRef, indexAccessExpr.type);

        //<TeacherOutput>TeacherOutput.convert(m);
        BLangExpression outputTypeConversionExpr = desugar.addConversionExprIfRequired(createInvocation,
                indexAccessExpr.type);

        //TeacherOutput t = <TeacherOutput>TeacherOutput.convert(m);
        BLangAssignment assignment = ASTBuilderUtil.createAssignmentStmt(pos, foreachBody);
        assignment.setExpression(outputTypeConversionExpr);
        assignment.varRef = indexAccessExpr;
        BLangForeach foreach = (BLangForeach) TreeBuilder.createForeachNode();
        foreach.pos = pos;
        foreach.body = foreachBody;
        foreach.collection = ASTBuilderUtil.createVariableRef(pos, inputCollectionSymbol);
        foreach.variableDefinitionNode = ASTBuilderUtil.createVariableDef(foreachVariable.pos, foreachVariable);
        foreach.isDeclaredWithVar = true;
        foreach.varType = foreachVariable.type;
        foreach.resultType = indexAccessExpr.type;
        LinkedHashSet<BType> memberTypes = new LinkedHashSet<>();
        memberTypes.add(indexAccessExpr.type);
        foreach.nillableResultType = new BUnionType(null, memberTypes, true);
        return foreach;
    }

    private BLangExpression buildCreateInvocation(BLangSimpleVarRef mapVarRef, BType outputEventType) {
        BVarSymbol typeSymbol = new BVarSymbol(0, outputEventType.tsymbol.name, mapVarRef.symbol.pkgID,
                                               outputEventType, mapVarRef.symbol.owner);
        BLangSimpleVarRef outputTypeRef = ASTBuilderUtil.createVariableRef(mapVarRef.pos, typeSymbol);
        //special case for varRefs of Types;
        outputTypeRef.type = symTable.typeDesc;
        BSymbol createMethodSymbol =
                symResolver.createSymbolForConvertOperator(mapVarRef.pos,
                        names.fromBuiltInMethod(BLangBuiltInMethod.CONVERT), Lists.of(mapVarRef), outputTypeRef);
        BLangInvocation createMethodInvocation = ASTBuilderUtil.createInvocationExprForMethod(mapVarRef.pos,
                (BInvokableSymbol) createMethodSymbol, Lists.of(mapVarRef), symResolver);
        createMethodInvocation.expr = outputTypeRef;
        createMethodInvocation.argExprs = Lists.of(mapVarRef);
        createMethodInvocation.builtInMethod = BLangBuiltInMethod.CONVERT;
        createMethodInvocation.builtinMethodInvocation = true;
        return createMethodInvocation;
    }


 /*  This method converts the select clause of the streaming query in to Ballerina native constructs.

     eg: Below query,
               select inputStream.name, inputStream.age, sum (inputStream.age) as sumAge, count() as count

     convert into below constructs.
                streams:Select select = streams:createSelect(outputProcess.process, [streams:sum(), streams:count()],
                    (streams:StreamEvent e) => string {
                        return <string>e.data["inputStream.name"];
                    },
                    (streams:StreamEvent e, streams:Aggregator[] aggregatorArr)  => map {
                        return {
                            "name": e.data["inputStream.name"],
                            "age": e.data["inputStream.age"],
                            "sumAge": aggregatorArr[0].process(e.data["inputStream.age"], e.eventType),
                            "count": aggregatorArr[1].process((), e.eventType)
                        };
                    });
 */

    @Override
    public void visit(BLangSelectClause selectClause) {

        // If exists, visit having node first
        HavingNode havingNode = selectClause.getHaving();
        if (havingNode != null) {
            ((BLangHaving) havingNode).accept(this);
        }

        // Create select statement
        createSelectStatement(selectClause);
    }

    private void createSelectStatement(BLangSelectClause selectClause) {

        // 1st arg for createSelect
        BLangExpression nextProcessMethodAccess = createNextProcessFuncPointer(selectClause.pos);

        // [streams:sum(), streams:count(), ... etc], 2nd arg
        BLangExpression aggregateArray = createAggregatorArray(selectClause);

        // ((streams:StreamEvent e) returns string)[], 3rd arg
        BLangExpression groupingLambda;
        if (selectClause.getGroupBy() != null) {
            groupingLambda = createGroupByLambdas(selectClause);
        } else {
            groupingLambda = ASTBuilderUtil.createLiteral(selectClause.pos, symTable.nilType, Names.NIL_VALUE);
        }

        // (streams:StreamEvent e, streams:Aggregator[] aggregatorArr)  returns map<anydata>, 4th arg of createSelect
        BLangExpression aggregatorLambda = createAggregatorLambda(selectClause);


        BInvokableSymbol groupBySelectInvokableSymbol = (BInvokableSymbol) symResolver.
                resolvePkgSymbol(selectClause.pos, env, Names.STREAMS_MODULE).
                scope.lookup(new Name(CREATE_SELECT_WITH_GROUP_BY_METHOD_NAME)).symbol;

        BType selectWithGroupByInvokableType = groupBySelectInvokableSymbol.type.getReturnType();
        BVarSymbol selectWithGroupByInvokableTypeVarSymbol = new BVarSymbol(0,
                new Name(getVariableName(SELECT_WITH_GROUP_BY_FUNC_REFERENCE)), groupBySelectInvokableSymbol.pkgID,
                selectWithGroupByInvokableType, env.scope.owner);
        nextProcessVarSymbolStack.push(selectWithGroupByInvokableTypeVarSymbol);

        List<BLangExpression> args = new ArrayList<>();
        args.add(nextProcessMethodAccess);
        args.add(aggregateArray);
        args.add(groupingLambda);
        args.add(aggregatorLambda);

        // streams:createSelect( ... )
        BLangInvocation selectWithGroupByInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(selectClause.pos, groupBySelectInvokableSymbol, args, symResolver);
        selectWithGroupByInvocation.argExprs = args;

        // streams:Select variable name
        BLangSimpleVariable selectWithGroupByInvokableTypeVariable = ASTBuilderUtil.
                createVariable(selectClause.pos, getVariableName(SELECT_WITH_GROUP_BY_FUNC_REFERENCE),
                        selectWithGroupByInvokableType, selectWithGroupByInvocation,
                        selectWithGroupByInvokableTypeVarSymbol);

        // streams:Select - user defined data type node
        BLangUserDefinedType userDefinedType = (BLangUserDefinedType) TreeBuilder.createUserDefinedTypeNode();
        userDefinedType.typeName = ASTBuilderUtil.createIdentifier(selectClause.pos, SELECT_WITH_GROUP_BY_OBJECT_NAME);
        userDefinedType.type = selectWithGroupByInvokableType;
        selectWithGroupByInvokableTypeVariable.setTypeNode(userDefinedType);

        // streams:Select select = streams:createSelect(...);
        BLangSimpleVariableDef selectWithGroupByInvokableTypeVariableDef = ASTBuilderUtil.
                createVariableDef(selectClause.pos, selectWithGroupByInvokableTypeVariable);
        stmts.add(selectWithGroupByInvokableTypeVariableDef);
    }

    // [streams:sum(), streams:count(), .. etc ]
    private BLangArrayLiteral createAggregatorArray(BLangSelectClause selectClause) {
        BLangArrayLiteral expr = (BLangArrayLiteral) TreeBuilder.createArrayLiteralNode();
        expr.exprs = new ArrayList<>();

        List<SelectExpressionNode> selectExpressions = selectClause.getSelectExpressions();
        for (SelectExpressionNode select : selectExpressions) {
            ExpressionNode selectExpr = select.getExpression();
            if (selectExpr.getKind() == NodeKind.INVOCATION && ((BLangInvocation) selectExpr).expr == null) {
                BLangInvocation invocation = (BLangInvocation) selectExpr;
                BInvokableSymbol aggregatorInvokableSymbol =
                        getInvokableSymbol(invocation, AGGREGATOR_OBJECT_NAME);
                if (aggregatorInvokableSymbol != null) {
                    if (isReturnTypeMatching(invocation.pos, AGGREGATOR_OBJECT_NAME, aggregatorInvokableSymbol)) {
                        BLangInvocation aggregatorInvocation = ASTBuilderUtil.
                                createInvocationExprForMethod(invocation.pos, aggregatorInvokableSymbol,
                                        Collections.emptyList(), symResolver);
                        expr.exprs.add(aggregatorInvocation);
                    }
                } else {
                    dlog.error(invocation.pos, DiagnosticCode.UNDEFINED_FUNCTION, invocation.name);
                }
            }
        }
        expr.type = new BArrayType(symResolver.resolvePkgSymbol(selectClause.pos, env, Names.STREAMS_MODULE)
                .scope.lookup(new Name(AGGREGATOR_OBJECT_NAME)).symbol.type);
        return expr;
    }

    private boolean isReturnTypeMatching(DiagnosticPos pos, String objectName, BInvokableSymbol invokableSymbol) {
        BSymbol expectedRetType = symResolver.
                resolvePkgSymbol(pos, env, Names.STREAMS_MODULE).
                scope.lookup(new Name(objectName)).symbol;
        BSymbol actualRetType = invokableSymbol.getType().retType.tsymbol;
        return expectedRetType == actualRetType;
    }

    // Object.process
    private BLangFieldBasedAccess createNextProcessFuncPointer(DiagnosticPos pos) {
        BVarSymbol nextProcessInvokableTypeVarSymbol = nextProcessVarSymbolStack.pop();
        BInvokableSymbol nextProcessInvokableSymbol = getNextProcessFunctionSymbol(nextProcessInvokableTypeVarSymbol);

        BLangSimpleVarRef nextProcessSimpleVarRef = ASTBuilderUtil.createVariableRef(pos,
                nextProcessInvokableTypeVarSymbol);
        BLangFieldBasedAccess nextProcessMethodAccess = (BLangFieldBasedAccess)
                TreeBuilder.createFieldBasedAccessNode();
        nextProcessMethodAccess.expr = nextProcessSimpleVarRef;
        nextProcessMethodAccess.symbol = nextProcessInvokableSymbol;
        nextProcessMethodAccess.type = nextProcessInvokableSymbol.type;
        nextProcessMethodAccess.pos = pos;
        nextProcessMethodAccess.field = ASTBuilderUtil.createIdentifier(pos, NEXT_PROCESS_METHOD_NAME);
        return nextProcessMethodAccess;
    }

    private BLangLambdaFunction createAggregatorLambda(BLangSelectClause selectClause) {

        //streams:StreamEvent e,
        BLangSimpleVariable varSelectFnStreamEvent = this.createStreamEventArgVariable(
                getVariableName(SELECT_LAMBDA_PARAM_REFERENCE), selectClause.pos, env);

        //streams:Aggregator[] aggregatorArr
        BLangSimpleVariable varAggregatorArray =
                this.createAggregatorTypeVariable(getVariableName(SELECT_WITH_GROUP_BY_LAMBDA_PARAM_REFERENCE),
                        selectClause.pos, env);

        /* (streams:StreamEvent e, streams:Aggregator[] aggregatorArr)  => map<any> {

            });
        */
        BLangLambdaFunction selectWithGroupBy = createAggregatorLambdaWithParams(varAggregatorArray,
                varSelectFnStreamEvent, selectClause.pos);

        //lambda function body
        createAggregatorLambdaBody(selectClause, selectWithGroupBy);

        return selectWithGroupBy;
    }

    private void createAggregatorLambdaBody(BLangSelectClause selectClause, BLangLambdaFunction selectWithGroupBy) {

        BLangBlockStmt selectLambdaBody = selectWithGroupBy.function.body;
        BLangSimpleVariable varStreamEvent = selectWithGroupBy.function.requiredParams.get(0);
        BLangSimpleVariable varAggregatorArray = selectWithGroupBy.function.requiredParams.get(1);

        /* TeacherOutput teacherOutput = {
                        name: e.data["inputStream.name"],
                        age: e.data["inputStream.age"],
                        sumAge: aggregatorArr1[0].process(e.data["inputStream.age"], e.eventType),
                        count: aggregatorArr1[1].process((), e.eventType),
                        ...
                        ...
                    };
        */
        BLangSimpleVariableDef outputEventObjectVariableDef = addOutputObjectCreationStmt(selectClause,
                selectLambdaBody, varStreamEvent, varAggregatorArray);

        // return teacherOutput;
        addReturnStmt(selectClause.pos, selectLambdaBody,
                ASTBuilderUtil.createVariableRef(selectClause.pos, outputEventObjectVariableDef.var.symbol));
    }

    private void addReturnStmt(DiagnosticPos pos, BLangBlockStmt targetBody, BLangExpression expr) {
        BLangReturn returnStmt = (BLangReturn) TreeBuilder.createReturnNode();
        returnStmt.pos = pos;
        returnStmt.expr = expr;
        targetBody.stmts.add(returnStmt);
    }

    private BLangSimpleVariableDef addOutputObjectCreationStmt(BLangSelectClause selectClause,
                                                               BLangBlockStmt selectLambdaBody,
                                                               BLangSimpleVariable varStreamEvent,
                                                               BLangSimpleVariable varAggregatorArray) {

        BLangRecordLiteral outputEventRecordLiteral = ASTBuilderUtil.createEmptyRecordLiteral(selectClause.pos,
                symTable.mapAnydataType);
        /* {
                    name: e.data["inputStream.name"]
                    age: e.data["inputStream.age"],
                    sumAge: check <int> aggregatorArr1[0].process(e.data["inputStream.age"], e.eventType),
                    count: check <int> aggregatorArr1[1].process((), e.eventType),
                    ...
           }
        */
        List<BLangRecordLiteral.BLangRecordKeyValue> recordKeyValueList =
                getFieldListInSelectClause(selectClause.pos, selectClause.getSelectExpressions(), varStreamEvent.symbol,
                        varAggregatorArray.symbol, (BLangGroupBy) selectClause.getGroupBy());

        BVarSymbol outputEventVarSymbol =
                new BVarSymbol(0, new Name(getVariableName(OUTPUT_EVENT_SELECTOR_PARAM_REFERENCE)),
                        varStreamEvent.symbol.pkgID, symTable.mapAnydataType, env.scope.owner);

        // TeacherOutput teacherOutput;
        BLangSimpleVariable outputEventObjectVariable = ASTBuilderUtil.
                createVariable(selectClause.pos, getVariableName(OUTPUT_EVENT_SELECTOR_PARAM_REFERENCE),
                        symTable.mapAnydataType, outputEventRecordLiteral, outputEventVarSymbol);

        outputEventRecordLiteral.keyValuePairs = recordKeyValueList;

        BLangSimpleVariableDef outputEventObjectVariableDef = ASTBuilderUtil.createVariableDef(selectClause.pos,
                outputEventObjectVariable);
        selectLambdaBody.stmts.add(outputEventObjectVariableDef);

        return outputEventObjectVariableDef;
    }

    private BLangFieldBasedAccess createEventDataFieldAccessExpr(DiagnosticPos pos,
                                                                 BVarSymbol streamEventSymbol) {
        BLangFieldBasedAccess eventDataField = (BLangFieldBasedAccess) TreeBuilder.createFieldBasedAccessNode();
        eventDataField.expr = ASTBuilderUtil.createVariableRef(pos, streamEventSymbol);
        eventDataField.type = symTable.mapAnydataType;
        eventDataField.symbol = ((BObjectType) (streamEventSymbol).type).fields.get(STREAM_EVENT_DATA_FIELD_INDEX)
                .symbol;
        eventDataField.fieldKind = FieldKind.SINGLE;
        eventDataField.pos = pos;
        eventDataField.field = ASTBuilderUtil.createIdentifier(pos, EVENT_DATA_VARIABLE_NAME);
        return eventDataField;
    }

    private BLangLambdaFunction createAggregatorLambdaWithParams(BLangSimpleVariable varAggregatorArray,
                                                                 BLangSimpleVariable varSelectFnStreamEvent,
                                                                 DiagnosticPos pos) {
        Set<BVarSymbol> selectLambdaClosureVarSymbols = new LinkedHashSet<>();
        selectLambdaClosureVarSymbols.add(varSelectFnStreamEvent.symbol);
        selectLambdaClosureVarSymbols.add(varAggregatorArray.symbol);

        BLangType selectLambdaReturnType = ASTBuilderUtil.createTypeNode(symTable.mapAnydataType);

        return createLambdaFunction(pos, new ArrayList<>(Arrays.asList(varSelectFnStreamEvent, varAggregatorArray)),
                selectLambdaClosureVarSymbols, selectLambdaReturnType);
    }

    private BLangExpression createGroupByLambdas(BLangSelectClause selectClause) {
        BLangArrayLiteral arr = (BLangArrayLiteral) TreeBuilder.createArrayLiteralNode();
        arr.exprs = new ArrayList<>();
        selectClause.getGroupBy().getVariables()
                .forEach(groupingVar -> arr.exprs.add(createGroupByLambda((BLangExpression) groupingVar)));
        arr.type = new BArrayType(arr.exprs.get(0).type);
        arr.pos = ((BLangGroupBy) selectClause.getGroupBy()).pos;
        return arr;
    }

    private BLangExpression createGroupByLambda(BLangExpression expr) {
        BLangSimpleVariable varStreamEvent = this.createStreamEventArgVariable(
                getVariableName(SELECT_LAMBDA_PARAM_REFERENCE), expr.pos, env);
        BLangType typeNode = ASTBuilderUtil.createTypeNode(symTable.anydataType);
        // (streams:StreamEvent e) returns anydata { .. }
        BLangLambdaFunction groupingLambda = createLambdaWithVarArg(expr.pos, new BLangSimpleVariable[]{varStreamEvent},
                typeNode);
        BLangBlockStmt groupByLambda = groupingLambda.function.body;
        BLangExpression mapAccessExpr;

        mapAccessExpr = (BLangExpression) preSelectDesuagr.rewrite(expr,
                new BSymbol[] {createEventDataFieldAccessExpr(expr.pos, varStreamEvent.symbol).symbol}, streamAliasMap,
                rhsStream, outputEventType);

        // return <anydata>e.data[<fieldName in string>];
        BLangExpression conversionExpr = desugar.addConversionExprIfRequired(mapAccessExpr, symTable.anydataType);
        addReturnGroupByFieldStmt(groupByLambda, conversionExpr);
        return groupingLambda;
    }

    private void addReturnGroupByFieldStmt(BLangBlockStmt groupByLambda, BLangExpression expr) {
        addReturnStmt(expr.pos, groupByLambda, expr);
    }

    private  BLangLambdaFunction createLambdaWithVarArg(DiagnosticPos pos, BLangSimpleVariable[] varArgs,
                                                       TypeNode typeNode) {
        Set<BVarSymbol> varArgClosureSymbols = Arrays.stream(varArgs).map(varArg -> varArg.symbol)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return createLambdaFunction(pos, Arrays.asList(varArgs),
                varArgClosureSymbols, typeNode);
    }

    @Override
    public void visit(BLangJoinStreamingInput joinStreamingInput) {
        BLangBinaryExpr onExpr = (BLangBinaryExpr) joinStreamingInput.getOnExpression();
        if (onExpr != null && !isTableJoin) {
            BLangSimpleVariable lhsDataMap =
                    createMapTypeVariable(getVariableName(JOIN_CONDITION_LAMBDA_PARAM_REFERENCE), onExpr.lhsExpr.pos,
                                          env);
            BLangSimpleVariable rhsDataMap = createMapTypeVariable(
                    getVariableName(JOIN_CONDITION_LAMBDA_PARAM_REFERENCE) + 1, onExpr.rhsExpr.pos, env);
            TypeNode typeNode = ASTBuilderUtil.createTypeNode(symTable.booleanType);
            BLangLambdaFunction conditionFunc = createLambdaWithVarArg(joinStreamingInput.pos, new BLangSimpleVariable[]
                    {lhsDataMap, rhsDataMap}, typeNode);
            BLangBlockStmt funcBody = conditionFunc.function.body;
            BType lhsType = onExpr.lhsExpr.type;
            BType rhsType = onExpr.rhsExpr.type;
            BLangBinaryExpr refactoredOnExpr = (BLangBinaryExpr) preSelectDesuagr.rewrite(onExpr,
                    new BSymbol[]{lhsDataMap.symbol, rhsDataMap.symbol}, streamAliasMap, rhsStream, outputEventType);

            refactoredOnExpr.lhsExpr = desugar.addConversionExprIfRequired(refactoredOnExpr.lhsExpr, lhsType);
            refactoredOnExpr.rhsExpr = desugar.addConversionExprIfRequired(refactoredOnExpr.rhsExpr, rhsType);
            onExpr = refactoredOnExpr;

            //onExpr.lhsExpr = desugar.addConversionExprIfRequired(onExpr.lhsExpr, on)
            addReturnStmt(onExpr.pos, funcBody, onExpr);
            createJoinProcessorStmt(joinStreamingInput, conditionFunc);
        } else if (onExpr == null && !isTableJoin) {
            createJoinProcessorStmt(joinStreamingInput, null);
        } else {
            createTableJoinProcessorStmt(joinStreamingInput);
        }

        BLangStreamingInput streamingInput = (BLangStreamingInput) joinStreamingInput.getStreamingInput();
        streamingInput.accept(this);
    }

    private void createTableJoinProcessorStmt(BLangJoinStreamingInput joinStreamingInput) {
        BLangFieldBasedAccess nextProcessMethodAccess = createNextProcessFuncPointer(joinStreamingInput.pos);
        BInvokableSymbol tableJoinProcessorInvokableSymbol = (BInvokableSymbol) symResolver.
                resolvePkgSymbol(joinStreamingInput.pos, env, Names.STREAMS_MODULE).
                scope.lookup(new Name(CREATE_TABLE_JOIN_PROCESS_METHOD_NAME)).symbol;
        BType tableJoinProcessorReturnType = tableJoinProcessorInvokableSymbol.type.getReturnType();
        BVarSymbol tableJoinProcessorVarSymbol =
                new BVarSymbol(0, new Name(getVariableName(TABLE_JOIN_PROCESS_FUNC_REFERENCE)),
                               tableJoinProcessorInvokableSymbol.pkgID, tableJoinProcessorReturnType, env.scope.owner);
        joinProcessorStack.push(tableJoinProcessorVarSymbol);
        BSymbol joinTypesSymbol = symResolver.resolvePkgSymbol(joinStreamingInput.pos, env, Names.STREAMS_MODULE).
                scope.lookup(new Name(JOIN_TYPE)).symbol;
        BLangLiteral joinType =
                ASTBuilderUtil.createLiteral(joinStreamingInput.pos, symTable.stringType,
                                             joinStreamingInput.getJoinType().toUpperCase());

        List<BLangExpression> args = new ArrayList<>();
        args.add(nextProcessMethodAccess);
        args.add(desugar.addConversionExprIfRequired(joinType, joinTypesSymbol.type));
        args.add(createOnConditionLambdaStatement(exprStack.pop()));
        // streams:createTableJoinProcessor( ... )
        BLangInvocation createTableJoinInvocation = ASTBuilderUtil.createInvocationExprForMethod(
                joinStreamingInput.pos, tableJoinProcessorInvokableSymbol, args, symResolver);
        createTableJoinInvocation.argExprs = args;
        // streams:TableJoinProcessor variable name
        BLangSimpleVariable joinInvokableTypeVariable = ASTBuilderUtil.
                createVariable(joinStreamingInput.pos, getVariableName(TABLE_JOIN_PROCESS_FUNC_REFERENCE),
                               tableJoinProcessorReturnType, createTableJoinInvocation,
                               tableJoinProcessorVarSymbol);
        // streams:TableJoinProcessor tableJoinProcessor = streams:createTableJoinProcessor(...);
        BLangUserDefinedType userDefinedType = (BLangUserDefinedType) TreeBuilder.createUserDefinedTypeNode();
        userDefinedType.typeName = ASTBuilderUtil.createIdentifier(joinStreamingInput.pos,
                                                                   TABLE_JOIN_PROCESS_OBJECT_NAME);
        userDefinedType.type = tableJoinProcessorReturnType;
        joinInvokableTypeVariable.setTypeNode(userDefinedType);
        BLangSimpleVariableDef tableJoinProcessorInvokableTypeDef =
                ASTBuilderUtil.createVariableDef(joinStreamingInput.pos, joinInvokableTypeVariable);
        stmts.add(tableJoinProcessorInvokableTypeDef);
    }
    /* Create below lambda function.
       function (streams:StreamEvent s) returns map<anydata>[] {
           map<anydata>[] result = [];
           table<Stock> stocks = queryStocksTable(<string> s.data["twitterStream.company"], 1);
           foreach var r in stocks {
               result[result.length()] = map<anydata>map<anydata>.create(r);
           }
           return result;
       }
   */
    private BLangExpression createOnConditionLambdaStatement(BLangExpression onConditionExpr) {
        // create lambda signature and body
        BLangSimpleVariable streamEventVarArg = createStreamEventArgVariable(
                getVariableName(TABLE_JOIN_LAMBDA_PARAM_REFERENCE), onConditionExpr.pos, env);
        BLangLambdaFunction conditionFunc =
                createLambdaWithVarArg(onConditionExpr.pos, new BLangSimpleVariable[]{streamEventVarArg},
                                       ASTBuilderUtil.createTypeNode(new BArrayType(symTable.mapAnydataType)));
        BLangBlockStmt lambdaBody = conditionFunc.function.body;

        BLangSimpleVarRef outputArrayRef = createResultArrayRefInForEach(conditionFunc.function.pos,
                symTable.mapAnydataType, conditionFunc.function.symbol, lambdaBody);

        // table<Stock> stocks = queryStocksTable(<string> s.data["twitterStream.company"], 1);
        onConditionExpr = (BLangExpression) preSelectDesuagr.rewrite(onConditionExpr, new BSymbol[]{
                createEventDataFieldAccessExpr(onConditionExpr.pos, streamEventVarArg.symbol).symbol},
                                                                     streamAliasMap, rhsStream, outputEventType);
        BTableType tableType = (BTableType) onConditionExpr.type;

        BLangSimpleVariable resultTableVariable =
                ASTBuilderUtil.createVariable(onConditionExpr.pos, VAR_RESULTS_TABLE, tableType, onConditionExpr, null);
        defineVariable(resultTableVariable, env.scope.owner.pkgID, env.scope.owner);
        BLangSimpleVariableDef resultTableDef =
                ASTBuilderUtil.createVariableDef(onConditionExpr.pos, resultTableVariable);
        lambdaBody.addStatement(resultTableDef);
        BLangForeach foreach = createForEachStmtForArrayConversion(conditionFunc.function.pos, conditionFunc
                .function.symbol, resultTableVariable.symbol, outputArrayRef);
        lambdaBody.addStatement(foreach);
        // return statement for result
        addReturnStmt(onConditionExpr.pos, lambdaBody, ASTBuilderUtil.createVariableRef
                (onConditionExpr.pos, outputArrayRef.symbol));
        return conditionFunc;
    }

    private void createJoinProcessorStmt(BLangJoinStreamingInput joinStreamingInput,
                                         BLangLambdaFunction conditionExpr) {

        BLangExpression nextProcessMethodAccess = createNextProcessFuncPointer(joinStreamingInput.pos);

        BInvokableSymbol joinProcessorInvokableSymbol = (BInvokableSymbol) symResolver.
                resolvePkgSymbol(joinStreamingInput.pos, env, Names.STREAMS_MODULE).
                scope.lookup(new Name(CREATE_STREAM_JOIN_PROCESS_METHOD_NAME)).symbol;

        BType joinProcessorReturnType = joinProcessorInvokableSymbol.type.getReturnType();

        BVarSymbol joinProcessorVarSymbol = new BVarSymbol(0,
                new Name(getVariableName(JOIN_PROCESS_FUNC_REFERENCE)), joinProcessorInvokableSymbol.pkgID,
                joinProcessorReturnType, env.scope.owner);

        BSymbol joinTypesSymbol = symResolver.resolvePkgSymbol(joinStreamingInput.pos, env, Names.STREAMS_MODULE).
                scope.lookup(new Name(JOIN_TYPE)).symbol;
        BLangLiteral joinType = ASTBuilderUtil.createLiteral(joinStreamingInput.pos, symTable.stringType,
                joinStreamingInput.getJoinType().toUpperCase());

        joinProcessorStack.push(joinProcessorVarSymbol);

        List<BLangExpression> args = new ArrayList<>();
        args.add(nextProcessMethodAccess);
        args.add(desugar.addConversionExprIfRequired(joinType, joinTypesSymbol.type));

        // streams:createJoinProcess( ... )
        BLangInvocation createJoinInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(joinStreamingInput.pos, joinProcessorInvokableSymbol, args, symResolver);
        createJoinInvocation.argExprs = args;

        if (conditionExpr != null) {
            BLangNamedArgsExpression conditionNamedArgExpression =
                    ASTBuilderUtil.createNamedArg(ON_CONDITION_NAMED_ARG_NAME, conditionExpr);
            createJoinInvocation.namedArgs.add(conditionNamedArgExpression);
            createJoinInvocation.argExprs.add(conditionNamedArgExpression);
        }

        // streams:JoinProcess variable name
        BLangSimpleVariable joinInvokableTypeVariable =
                ASTBuilderUtil.createVariable(joinStreamingInput.pos, getVariableName(JOIN_PROCESS_FUNC_REFERENCE),
                joinProcessorReturnType, createJoinInvocation, joinProcessorVarSymbol);

        // streams:Select - user defined data type node
        BLangUserDefinedType userDefinedType = (BLangUserDefinedType) TreeBuilder.createUserDefinedTypeNode();
        userDefinedType.typeName = ASTBuilderUtil.createIdentifier(joinStreamingInput.pos, JOIN_PROCESS_OBJECT_NAME);
        userDefinedType.type = joinProcessorReturnType;
        joinInvokableTypeVariable.setTypeNode(userDefinedType);

        // streams:Select select = streams:createSelect(...);
        BLangSimpleVariableDef joinProcessorInvokableTypeDef =
                ASTBuilderUtil.createVariableDef(joinStreamingInput.pos, joinInvokableTypeVariable);
        stmts.add(joinProcessorInvokableTypeDef);
    }

    //
    // This method create necessary Ballerina native constructs to consume events from stream based on the 'from'
    // statement of the streaming query.
    //
    // eg: Below query,
    //          from inputStream
    //
    // converts into below constructs.
    //
    //          inputStream.subscribe((Teacher t) => {
    //              streams:StreamEvent[] eventArr = streams:buildStreamEvent(t);
    //              filter.process(eventArr);
    //          });
    //
    //
    @Override
    public void visit(BLangStreamingInput streamingInput) {
        //Lambda function parameter
        BType lambdaParameterType;
        if (isTableReference(streamingInput.getStreamReference())) {
            lambdaParameterType = ((BTableType) ((BLangExpression) streamingInput.getStreamReference()).type)
                    .constraint;
        } else {
            lambdaParameterType = ((BStreamType) ((BLangExpression) streamingInput.getStreamReference()).type)
                    .constraint;
        }

        BLangWhere afterWhereNode = (BLangWhere) streamingInput.getAfterStreamingCondition();
        if (afterWhereNode != null) {
            afterWhereNode.accept(this);
        }

        BLangWindow windowClauseNode = (BLangWindow) streamingInput.getWindowClause();
        if (windowClauseNode != null) {
            windowClauseNode.accept(this);
        } else if (isJoin && !isTableReference(streamingInput.getStreamReference())) {
            windowClauseNode = createDefaultLengthWindow(streamingInput);
            streamingInput.setWindowClause(windowClauseNode);
            windowClauseNode.accept(this);
        }

        BLangWhere beforeWhereNode = (BLangWhere) streamingInput.getBeforeStreamingCondition();
        if (beforeWhereNode != null) {
            beforeWhereNode.accept(this);
        }

        if (!nextProcessVarSymbolStack.empty()) {
            BVarSymbol nextProcessInvokableTypeVarSymbol = nextProcessVarSymbolStack.pop();
            BVarSymbol lambdaParameterVarSymbol = new BVarSymbol(0,
                    new Name(getVariableName(INPUT_STREAM_PARAM_REFERENCE)), lambdaParameterType.tsymbol.pkgID,
                    lambdaParameterType, env.scope.owner);

            BLangSimpleVariable inputStreamLambdaFunctionVariable = ASTBuilderUtil.createVariable(streamingInput.pos,
                    getVariableName(INPUT_STREAM_PARAM_REFERENCE), lambdaParameterType, null, lambdaParameterVarSymbol);
            inputStreamLambdaFunctionVariable.typeNode = ASTBuilderUtil.createTypeNode(lambdaParameterType);

            Set<BVarSymbol> closureVarSymbols = new LinkedHashSet<>();
            closureVarSymbols.add(nextProcessInvokableTypeVarSymbol);
            closureVarSymbols.add(inputStreamLambdaFunctionVariable.symbol);

            TypeNode returnType = ASTBuilderUtil.createTypeNode(symTable.nilType);

            //Construct lambda function which consumes events
            BLangLambdaFunction streamSubscriberLambdaFunction = createLambdaFunction(streamingInput.pos,
                    new ArrayList<>(Lists.of(inputStreamLambdaFunctionVariable)), closureVarSymbols,
                    returnType);
            BLangBlockStmt lambdaBody = streamSubscriberLambdaFunction.function.body;

            //varRef to the input event
            BLangExpression inputEventRef =
                    ASTBuilderUtil.createVariableRef(streamingInput.pos, inputStreamLambdaFunctionVariable.symbol);

            BInvokableSymbol streamEventBuilderInvokableSymbol = (BInvokableSymbol) symResolver.
                    resolvePkgSymbol(streamingInput.pos, env, Names.STREAMS_MODULE).
                    scope.lookup(new Name(BUILD_STREAM_EVENT_METHOD_NAME)).symbol;

            BVarSymbol streamEventArrayTypeVarSymbol = new BVarSymbol(0,
                    new Name(getVariableName(STREAM_EVENT_ARRAY_PARAM_REFERENCE)),
                    streamEventBuilderInvokableSymbol.pkgID, streamEventBuilderInvokableSymbol.type.getReturnType(),
                    env.scope.owner);

            BLangSimpleVariable streamEventArrayTypeVariable = ASTBuilderUtil.
                    createVariable(streamingInput.pos, getVariableName(STREAM_EVENT_ARRAY_PARAM_REFERENCE),
                            streamEventArrayTypeVarSymbol.type.getReturnType(), null, streamEventArrayTypeVarSymbol);

            List<BLangExpression> args = new ArrayList<>();
            args.add(inputEventRef);

            String streamReferenceSymbolName = ((BLangSimpleVarRef) streamingInput.getStreamReference()).symbol
                    .toString();
            if (streamAliasMap.containsKey(streamReferenceSymbolName)) {
                args.add(ASTBuilderUtil.createLiteral(streamingInput.pos, symTable.stringType,
                        streamAliasMap.get(streamReferenceSymbolName)));
            } else {
                args.add(ASTBuilderUtil.createLiteral(streamingInput.pos, symTable.stringType, ((BLangSimpleVarRef)
                        streamingInput.getStreamReference()).symbol.toString()));
            }

            BLangInvocation streamEventBuilderMethodInvocation = ASTBuilderUtil.
                    createInvocationExprForMethod(streamingInput.pos, streamEventBuilderInvokableSymbol, args,
                            symResolver);
            streamEventBuilderMethodInvocation.argExprs = args;

            streamEventArrayTypeVariable.expr = streamEventBuilderMethodInvocation;

            BLangUserDefinedType userDefinedType = (BLangUserDefinedType) TreeBuilder.createUserDefinedTypeNode();
            userDefinedType.typeName = ASTBuilderUtil.createIdentifier(streamingInput.pos, STREAM_EVENT_OBJECT_NAME);
            userDefinedType.type = streamEventArrayTypeVarSymbol.type.getReturnType();
            streamEventArrayTypeVariable.setTypeNode(userDefinedType);
            BLangSimpleVariableDef streamEventArrayTypeVariableDef =
                    ASTBuilderUtil.createVariableDef(streamingInput.pos, streamEventArrayTypeVariable);

            lambdaBody.stmts.add(streamEventArrayTypeVariableDef);

            //Function invocation to call output process
            BInvokableSymbol nextProcessInvokableSymbol =
                    getNextProcessFunctionSymbol(nextProcessInvokableTypeVarSymbol);

            BLangSimpleVarRef streamEventArrayRef = ASTBuilderUtil.createVariableRef(streamingInput.pos,
                    streamEventArrayTypeVarSymbol);
            List<BLangExpression> nextProcessVariables = new ArrayList<>(1);
            nextProcessVariables.add(streamEventArrayRef);
            BLangInvocation nextProcessMethodInvocation = ASTBuilderUtil.
                    createInvocationExprForMethod(streamingInput.pos, nextProcessInvokableSymbol, nextProcessVariables,
                            symResolver);
            nextProcessMethodInvocation.argExprs = nextProcessVariables;


            nextProcessMethodInvocation.expr = ASTBuilderUtil.createVariableRef(streamingInput.pos,
                    nextProcessInvokableTypeVarSymbol);
            BLangExpressionStmt nextProcessExpressionStmt = (BLangExpressionStmt) TreeBuilder.
                    createExpressionStatementNode();
            nextProcessExpressionStmt.pos = streamingInput.pos;
            nextProcessExpressionStmt.expr = nextProcessMethodInvocation;
            lambdaBody.stmts.add(nextProcessExpressionStmt);

            //Create function call - stream1.subscribe(lambda_function)
            BLangExpressionStmt inputStreamSubscribeStatement = (BLangExpressionStmt) TreeBuilder.
                    createExpressionStatementNode();
            inputStreamSubscribeStatement.pos = streamingInput.pos;
            BInvokableSymbol subscribeMethodSymbol = (BInvokableSymbol) symTable.rootScope.
                    lookup(names.fromString(STREAM_SUBSCRIBE_METHOD_NAME)).symbol;
            List<BLangExpression> variables = new ArrayList<>(1);
            variables.add(streamSubscriberLambdaFunction);
            BLangInvocation invocationExpr = ASTBuilderUtil.
                    createInvocationExprForMethod(streamingInput.pos, subscribeMethodSymbol, variables, symResolver);

            invocationExpr.argExprs = variables;
            invocationExpr.expr = ASTBuilderUtil.createVariableRef(streamingInput.pos,
                    ((BLangSimpleVarRef) streamingInput.getStreamReference()).symbol);
            inputStreamSubscribeStatement.expr = invocationExpr;

            //Add stream subscriber function to stmts
            stmts.add(inputStreamSubscribeStatement);
        }
    }

    private BLangWindow createDefaultLengthWindow(BLangStreamingInput streamingInput) {
        List<BLangExpression> args = new ArrayList<>();
        args.add(ASTBuilderUtil.createLiteral(streamingInput.pos, symTable.intType, 1L));

        BLangIdentifier lengthIdentifier = ASTBuilderUtil.createIdentifier(streamingInput.pos,
                LENGTH_WINDOW_METHOD_NAME);
        BLangIdentifier pkgAlias = ASTBuilderUtil.createIdentifier(streamingInput.pos, Names
                .STREAMS_MODULE.value);
        BLangInvocation lengthInvocation = (BLangInvocation) TreeBuilder.createInvocationNode();
        lengthInvocation.pos = streamingInput.pos;
        lengthInvocation.name = lengthIdentifier;
        lengthInvocation.builtinMethodInvocation = false;
        lengthInvocation.argExprs = args;
        lengthInvocation.pkgAlias = pkgAlias;

        BLangWindow window = (BLangWindow) TreeBuilder.createWindowClauseNode();
        window.pos = streamingInput.pos;
        window.setFunctionInvocation(lengthInvocation);
        return window;
    }

    /*
        e.g: window lengthWindow(5) will be turned into,

        streams:LengthWindow lengthWindow = streams:lengthWindow(select.process, [5]);
     */
    @Override
    public void visit(BLangWindow window) {

        BLangInvocation invocation = (BLangInvocation) window.getFunctionInvocation();

        //converting the window parameters into an array of parameters
        BLangArrayLiteral windowParamArrExpr = (BLangArrayLiteral) TreeBuilder.createArrayLiteralNode();
        windowParamArrExpr.exprs = new ArrayList<>();
        windowParamArrExpr.type = symTable.anyType;
        windowParamArrExpr.exprs.addAll(invocation.argExprs);
        invocation.argExprs.clear();
        invocation.argExprs.add(windowParamArrExpr);

        convertFieldAccessArgsToStringLiteral(invocation);

        //checks for the symbol, if not exists, then set the pkgAlias to STREAMS_STDLIB_PACKAGE_NAME
        BInvokableSymbol windowInvokableSymbol = getInvokableSymbol(invocation, WINDOW_OBJECT_NAME);

        if (windowInvokableSymbol != null) {
            if (isReturnTypeMatching(invocation.pos, WINDOW_OBJECT_NAME, windowInvokableSymbol)) {
                //Create event filter definition
                BVarSymbol nextProcessInvokableTypeVarSymbol;
                if (rhsStream != null) {
                    nextProcessInvokableTypeVarSymbol = joinProcessorStack.peek();
                } else {
                    nextProcessInvokableTypeVarSymbol = nextProcessVarSymbolStack.pop();
                }
                BInvokableSymbol nextProcessInvokableSymbol =
                        getNextProcessFunctionSymbol(nextProcessInvokableTypeVarSymbol);

                BLangSimpleVarRef nextProcessSimpleVarRef = ASTBuilderUtil.createVariableRef(window.pos,
                        nextProcessInvokableTypeVarSymbol);
                BLangFieldBasedAccess nextProcessMethodAccess = createFieldBasedAccessForProcessFunc(window.pos,
                        nextProcessInvokableSymbol, nextProcessSimpleVarRef);


                BType windowInvokableType = windowInvokableSymbol.type.getReturnType();

                BVarSymbol windowInvokableTypeVarSymbol =
                        new BVarSymbol(0, new Name(getVariableName(WINDOW_FUNC_REFERENCE)),
                                       windowInvokableSymbol.pkgID, windowInvokableType, env.scope.owner);
                nextProcessVarSymbolStack.push(windowInvokableTypeVarSymbol);

                BLangNamedArgsExpression nextProcPointer =
                        ASTBuilderUtil.createNamedArg(NEXT_PROCESS_POINTER_ARG_NAME, nextProcessMethodAccess);

                typeChecker.checkExpr(invocation, env);

                //these should be added after type-checking
                invocation.argExprs.add(nextProcPointer);
                invocation.namedArgs.add(nextProcPointer);

                BLangSimpleVariableDef windowDef = createVariableDef(invocation, windowInvokableType,
                        windowInvokableTypeVarSymbol, window.pos, WINDOW_FUNC_REFERENCE, WINDOW_OBJECT_NAME);
                stmts.add(windowDef);

                if (!joinProcessorStack.empty()) {
                    if (isTableJoin) {
                        attachWindowToTableJoinProcessor(window, windowInvokableTypeVarSymbol, lhsStream, rhsStream);
                    } else {
                        if (isInJoin) {
                            attachWindowToStreamJoinProcessor(window, windowInvokableTypeVarSymbol, SET_RHS_METHOD_NAME,
                                    rhsStream);
                        } else {
                            attachWindowToStreamJoinProcessor(window, windowInvokableTypeVarSymbol, SET_LHS_METHOD_NAME,
                                    lhsStream);
                        }
                    }
                }
            } else {
                dlog.error(invocation.pos, DiagnosticCode.INCOMPATIBLE_TYPES, Names.STREAMS_MODULE.value + ":" +
                        WINDOW_OBJECT_NAME, windowInvokableSymbol.getType().retType.tsymbol);
            }
        } else {
            dlog.error(invocation.pos, DiagnosticCode.UNDEFINED_FUNCTION, invocation.name);
        }
    }

    private void attachWindowToTableJoinProcessor(BLangWindow window, BVarSymbol windowInvokableTypeVarSymbol,
                                                  BLangVariableReference stream, BLangVariableReference table) {
        BVarSymbol joinProcessVarSymbol = joinProcessorStack.peek();
        BInvokableSymbol methodInvokableSymbol =
                getInvokableSymbolOfObject(joinProcessVarSymbol, SET_JOIN_PROPERTIES_METHOD_NAME);
        List<BLangExpression> args = new ArrayList<>();
        // join processor (caller)
        args.add(ASTBuilderUtil.createVariableRef(window.pos, joinProcessVarSymbol));
        // table name
        String tableReferenceSymbolName = table.symbol.toString();
        args.add(ASTBuilderUtil.createLiteral(window.pos, symTable.stringType, streamAliasMap
                .getOrDefault(tableReferenceSymbolName, tableReferenceSymbolName)));
        // stream name
        String streamReferenceSymbolName = stream.symbol.toString();
        args.add(ASTBuilderUtil.createLiteral(window.pos, symTable.stringType, streamAliasMap
                .getOrDefault(streamReferenceSymbolName, streamReferenceSymbolName)));
        // window instance
        args.add(ASTBuilderUtil.createVariableRef(window.pos, windowInvokableTypeVarSymbol));
        BLangInvocation methodInvocation =
                ASTBuilderUtil.createInvocationExprForMethod(window.pos, methodInvokableSymbol, args, symResolver);
        methodInvocation.argExprs = args;
        BLangExpressionStmt methodInvocationStmt = (BLangExpressionStmt) TreeBuilder.createExpressionStatementNode();
        methodInvocationStmt.pos = window.pos;
        methodInvocationStmt.expr = methodInvocation;
        stmts.add(methodInvocationStmt);
    }


    private BInvokableSymbol getInvokableSymbol(BLangInvocation invocation, String modelType) {
        BInvokableSymbol invokableSymbol = (BInvokableSymbol) symResolver.
                resolvePkgSymbol(invocation.pos, env, names.fromString(invocation.pkgAlias.value)).
                scope.lookup(new Name(invocation.name.value)).symbol;
        if (invokableSymbol == null && invocation.pkgAlias.value.isEmpty()) {
            BSymbol windowSymbol = symResolver.
                    resolvePkgSymbol(invocation.pos, env, Names.STREAMS_MODULE).
                    scope.lookup(new Name(invocation.name.value)).symbol;

            if (windowSymbol != null && SymbolKind.FUNCTION.equals(windowSymbol.kind) &&
                    isReturnTypeMatching(invocation.pos, modelType, (BInvokableSymbol) windowSymbol)) {
                invokableSymbol = (BInvokableSymbol) windowSymbol;
            } else {
                dlog.error(invocation.pos, DiagnosticCode.INVALID_STREAMING_MODEL_TYPE, modelType, invocation.name);
            }

            invocation.pkgAlias.value = Names.STREAMS_MODULE.value;
        }
        return invokableSymbol;
    }

    private BLangSimpleVariableDef createVariableDef(BLangExpression expr, BType exprType,
                                                     BVarSymbol exprTypeSymbol,
                                                     DiagnosticPos pos, String exprVarName, String objName) {

        BLangSimpleVariable windowInvokableTypeVariable =
                ASTBuilderUtil.createVariable(pos, getVariableName(exprVarName), exprType, expr,
                        exprTypeSymbol);

        BLangUserDefinedType userDefinedType = (BLangUserDefinedType) TreeBuilder.createUserDefinedTypeNode();
        userDefinedType.typeName = ASTBuilderUtil.createIdentifier(pos, objName);
        userDefinedType.type = exprType;
        windowInvokableTypeVariable.setTypeNode(userDefinedType);
        return ASTBuilderUtil.createVariableDef(pos, windowInvokableTypeVariable);
    }

    private void convertFieldAccessArgsToStringLiteral(BLangInvocation invocation) {
        //converting BLangFieldBaseAccess to BLangLiteral of string type, in argExprs
        convertFieldAccessArgsToStringLiteral(invocation.argExprs);

        //converting BLangFieldBaseAccess to BLangLiteral of string type, in requiredArgs
        convertFieldAccessArgsToStringLiteral(invocation.requiredArgs);
    }

    private void convertFieldAccessArgsToStringLiteral(List<BLangExpression> exprs) {
        BLangLiteral streamEventParameter;
        for (int i = 0; i < exprs.size(); i++) {
            BLangExpression expr = exprs.get(i);
            if (expr.getKind() == NodeKind.FIELD_BASED_ACCESS_EXPR) {
                String variableName = ((BLangFieldBasedAccess) expr).expr.toString();
                if (streamAliasMap.containsKey(variableName)) {
                    variableName = streamAliasMap.get(variableName);
                    ((BLangSimpleVarRef) ((BLangFieldBasedAccess) expr).expr).variableName.value = variableName;
                }
                streamEventParameter = createStringLiteral(expr.pos, (expr).toString());
                exprs.set(i, streamEventParameter);
            } else if (expr.getKind() == NodeKind.ARRAY_LITERAL_EXPR) {
                BLangArrayLiteral arrayLiteral = (BLangArrayLiteral) expr;
                convertFieldAccessArgsToStringLiteral(arrayLiteral.exprs);
            } else {
                expr.typeChecked = false;
            }
        }
    }

    private BLangLiteral createStringLiteral(DiagnosticPos pos, String value) {
        BLangLiteral stringLit = new BLangLiteral();
        stringLit.pos = pos;
        stringLit.typeTag = TypeTags.STRING;
        stringLit.value = value;
        stringLit.type = symTable.stringType;
        return stringLit;
    }

    private void attachWindowToStreamJoinProcessor(BLangWindow window, BVarSymbol windowInvokableTypeVarSymbol,
                                                   String methodName, BLangVariableReference streamRef) {
        BVarSymbol joinProcessVarSymbol = joinProcessorStack.peek();
        BInvokableSymbol methodInvokableSymbol = getInvokableSymbolOfObject(joinProcessVarSymbol, methodName);
        List<BLangExpression> args = new ArrayList<>();
        args.add(ASTBuilderUtil.createVariableRef(window.pos, joinProcessVarSymbol));

        String streamReferenceSymbolName = streamRef.symbol.toString();
        args.add(ASTBuilderUtil.createLiteral(window.pos, symTable.stringType,
                streamAliasMap.getOrDefault(streamReferenceSymbolName, streamReferenceSymbolName)));
        args.add(ASTBuilderUtil.createVariableRef(window.pos, windowInvokableTypeVarSymbol));
        BLangInvocation methodInvocation = ASTBuilderUtil.createInvocationExprForMethod(window.pos,
                methodInvokableSymbol, args, symResolver);
        methodInvocation.argExprs = args;
        BLangExpressionStmt methodInvocationStmt = (BLangExpressionStmt) TreeBuilder.createExpressionStatementNode();
        methodInvocationStmt.pos = window.pos;
        methodInvocationStmt.expr = methodInvocation;
        stmts.add(methodInvocationStmt);
    }

    private BLangFieldBasedAccess createFieldBasedAccessForProcessFunc(DiagnosticPos pos,
                                                                       BInvokableSymbol nextProcessInvokableSymbol,
                                                                       BLangSimpleVarRef nextProcessSimpleVarRef) {
        BLangFieldBasedAccess nextProcessMethodAccess = (BLangFieldBasedAccess)
                TreeBuilder.createFieldBasedAccessNode();
        nextProcessMethodAccess.expr = nextProcessSimpleVarRef;
        nextProcessMethodAccess.symbol = nextProcessInvokableSymbol;
        nextProcessMethodAccess.type = nextProcessInvokableSymbol.type;
        nextProcessMethodAccess.pos = pos;
        nextProcessMethodAccess.field = ASTBuilderUtil.createIdentifier(pos, NEXT_PROCESS_METHOD_NAME);
        return nextProcessMethodAccess;
    }

    //
    // Below method creates the constructs to perform filtering based on the 'where' clause of the streaming query.
    //
    // eg: Below query,
    //          where inputStream.age > 25
    //
    // converts into below constructs.
    //
    //          streams:Filter filter = streams:createFilter(select.process, (map<anydata> o) => boolean {
    //              Teacher teacher = <Teacher> Teacher.create(o);
    //              return teacher.age > 25;
    //          });
    //
    //
    @Override
    public void visit(BLangWhere where) {
        BLangLambdaFunction lambda = createConditionLambda(where.pos, FILTER_LAMBDA_PARAM_REFERENCE);
        //always the condition lambda has one required param
        BSymbol varSymbol = lambda.function.requiredParams.get(0).symbol;
        BLangExpression conditionExpr = (BLangExpression) preSelectDesuagr.rewrite((BLangNode) where.getExpression(),
                new BSymbol[]{varSymbol}, streamAliasMap, rhsStream, outputEventType);
        visitFilter(where.pos, conditionExpr, lambda);
    }

    //
    // Below method creates the constructs to perform filtering based on the 'having' clause of the streaming query.
    //
    // eg: Below query,
    //          having age > 25
    //
    // converts into below constructs.
    //
    //          streams:Filter filter = streams:createFilter(outputProcess.process, (map<anydata> o) => boolean {
    //              Teacher teacher = <Teacher>Teacher.create(o);
    //              return teacher.age > 25;
    //          });
    //
    //
    @Override
    public void visit(BLangHaving having) {
        BLangLambdaFunction lambda = createConditionLambda(having.pos, HAVING_LAMBDA_PARAM_REFERENCE);
        //always the condition lambda has one required param
        BSymbol varSymbol = lambda.function.requiredParams.get(0).symbol;
        BLangExpression conditionExpr = (BLangExpression) postSelectDesugar.rewrite((BLangNode) having.getExpression(),
                varSymbol, outputEventType);
        visitFilter(having.pos, conditionExpr, lambda);
    }

    //------------------------------------- Methods required for filter / having -----------------------------------
    private void visitFilter(DiagnosticPos pos, BLangExpression expr, BLangLambdaFunction lambda) {
        BLangBlockStmt lambdaBody = lambda.function.body;
        // Return statement with having condition
        BLangReturn returnStmt = (BLangReturn) TreeBuilder.createReturnNode();
        returnStmt.pos = pos;
        returnStmt.expr = expr;
        lambdaBody.stmts.add(returnStmt);

        //Create having (filter) definition
        BVarSymbol nextProcessInvokableTypeVarSymbol = nextProcessVarSymbolStack.pop();
        BInvokableSymbol nextProcessInvokableSymbol = getNextProcessFunctionSymbol(nextProcessInvokableTypeVarSymbol);

        BLangSimpleVarRef nextProcessSimpleVarRef = ASTBuilderUtil.createVariableRef(pos,
                nextProcessInvokableTypeVarSymbol);
        BLangFieldBasedAccess nextProcessMethodAccess = createFieldBasedAccessForProcessFunc(pos,
                nextProcessInvokableSymbol, nextProcessSimpleVarRef);

        // Having will also use the same filter invokable
        BInvokableSymbol havingInvokableSymbol = (BInvokableSymbol) symResolver.
                resolvePkgSymbol(pos, env, Names.STREAMS_MODULE).
                scope.lookup(new Name(CREATE_FILTER_METHOD_NAME)).symbol;
        BType havingInvokableType = havingInvokableSymbol.type.getReturnType();
        BVarSymbol havingInvokableTypeVarSymbol = new BVarSymbol(0,
                new Name(getVariableName(FILTER_FUNC_REFERENCE)), havingInvokableSymbol.pkgID,
                havingInvokableType, env.scope.owner);
        nextProcessVarSymbolStack.push(havingInvokableTypeVarSymbol);

        List<BLangExpression> args = new ArrayList<>();
        args.add(nextProcessMethodAccess);
        args.add(lambda);

        BLangInvocation havingMethodInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(pos, havingInvokableSymbol, args, symResolver);
        havingMethodInvocation.argExprs = args;

        BLangSimpleVariableDef filterDef = createVariableDef(havingMethodInvocation, havingInvokableType,
                havingInvokableTypeVarSymbol, pos, FILTER_FUNC_REFERENCE, FILTER_OBJECT_NAME);
        stmts.add(filterDef);
    }

    private BLangLambdaFunction createConditionLambda(DiagnosticPos pos, String varName) {
        //Create lambda function Variable
        BLangSimpleVariable lambdaFunctionVariable =
                this.createMapTypeVariable(getVariableName(varName), pos, env);

        Set<BVarSymbol> closureVarSymbols = new LinkedHashSet<>();
        closureVarSymbols.add(lambdaFunctionVariable.symbol);

        BLangType returnType = ASTBuilderUtil.createTypeNode(symTable.booleanType);

        //Create new lambda function to process the output events
        return createLambdaFunction(pos, new ArrayList<>(Lists.of(lambdaFunctionVariable)), closureVarSymbols,
                                    returnType);
    }

    //----------------------------------------- Util Methods ---------------------------------------------------------

    private String getFunctionName(String name) {
        return name + lambdaFunctionCount++;
    }

    private String getVariableName(String name) {
        return name + lambdaFunctionCount;
    }

    private void defineFunction(BLangFunction funcNode, BLangPackage targetPkg) {
        final BPackageSymbol packageSymbol = targetPkg.symbol;
        final SymbolEnv packageEnv = this.symTable.pkgEnvMap.get(packageSymbol);
        symbolEnter.defineNode(funcNode, packageEnv);
        packageEnv.enclPkg.functions.add(funcNode);
        packageEnv.enclPkg.topLevelNodes.add(funcNode);
    }

    private BLangSimpleVariable createMapTypeVariable(String variableName, DiagnosticPos pos, SymbolEnv env) {
        BType varType = symTable.mapAnydataType;
        BVarSymbol varSymbol = new BVarSymbol(0, new Name(variableName),
                varType.tsymbol.pkgID, varType, env.scope.owner);

        BLangSimpleVariable mapTypeVariable = ASTBuilderUtil.createVariable(pos, variableName,
                varType, null, varSymbol);
        mapTypeVariable.typeNode = ASTBuilderUtil.createTypeNode(varType);
        return mapTypeVariable;
    }

    private BLangSimpleVariable createStreamEventArgVariable(String variableName, DiagnosticPos pos, SymbolEnv env) {
        BObjectTypeSymbol recordTypeSymbol = (BObjectTypeSymbol) symResolver.
                resolvePkgSymbol(pos, env, Names.STREAMS_MODULE).
                scope.lookup(new Name(STREAM_EVENT_OBJECT_NAME)).symbol;

        BType varType = recordTypeSymbol.type;
        BVarSymbol varSymbol = new BVarSymbol(0, new Name(variableName),
                varType.tsymbol.pkgID, varType, env.scope.owner);

        return ASTBuilderUtil.createVariable(pos, variableName, varType, null, varSymbol);
    }

    private BLangSimpleVariable createAggregatorTypeVariable(String variableName, DiagnosticPos pos, SymbolEnv env) {
        BInvokableSymbol invokableSymbol = (BInvokableSymbol) symResolver.
                resolvePkgSymbol(pos, env, Names.STREAMS_MODULE).
                scope.lookup(new Name(CREATE_SELECT_WITH_GROUP_BY_METHOD_NAME)).symbol;

        BType varType = ((BInvokableType) invokableSymbol.params.get(3).type).paramTypes.get(1);
        BVarSymbol varSymbol = new BVarSymbol(0, new Name(variableName),
                varType.tsymbol.pkgID, varType, env.scope.owner);
        return ASTBuilderUtil.createVariable(pos, variableName, varType, null, varSymbol);
    }

    private static BVarSymbol getOutputEventFieldSymbol(BType outputEventType, String fieldName) {
        List<BField> recordTypeFieldList = ((BRecordType) outputEventType).fields;
        for (BField field : recordTypeFieldList) {
            if (field.getName().value.equals(fieldName)) {
                return field.symbol;
            }
        }
        return null;
    }

    private BInvokableSymbol getInvokableSymbolOfObject(BSymbol nextProcessInvokableTypeVarSymbol, String funcName) {
        List<BAttachedFunction> attachedFunctionsList = ((BObjectTypeSymbol)
                (nextProcessInvokableTypeVarSymbol).type.tsymbol).attachedFuncs;
        for (BAttachedFunction attachedFunction : attachedFunctionsList) {
            if (attachedFunction.funcName.toString().equals(funcName)) {
                return attachedFunction.symbol;
            }
        }
        throw new IllegalStateException("Couldn't evaluate the " + funcName + " method of the next processor : " +
                (nextProcessInvokableTypeVarSymbol).type.toString());
    }

    private BInvokableSymbol getNextProcessFunctionSymbol(BSymbol nextProcessInvokableTypeVarSymbol) {
        return getInvokableSymbolOfObject(nextProcessInvokableTypeVarSymbol, NEXT_PROCESS_METHOD_NAME);
    }

    private List<BLangRecordLiteral.BLangRecordKeyValue> getFieldListInSelectClause
            (DiagnosticPos pos, List<? extends SelectExpressionNode> selectExprList,
             BVarSymbol streamEventSymbol, BVarSymbol aggregatorArraySymbol, BLangGroupBy groupBy) {
        LongAdder aggregatorIndex = new LongAdder();
        List<BLangRecordLiteral.BLangRecordKeyValue> recordKeyValueList = new ArrayList<>();

        for (SelectExpressionNode expressionNode : selectExprList) {
            BLangSelectExpression selectExpression = (BLangSelectExpression) expressionNode;
            BLangRecordLiteral.BLangRecordKeyValue recordKeyValue = (BLangRecordLiteral.BLangRecordKeyValue)
                    TreeBuilder.createRecordKeyValue();

            createOutputMapKey(pos, selectExpression, recordKeyValue);
            if (selectExpression.getExpression().getKind() == NodeKind.INVOCATION) {
                recordKeyValue.valueExpr = refactorSelectorInvocation(streamEventSymbol, aggregatorArraySymbol,
                        aggregatorIndex, selectExpression, groupBy);
            } else {
                BLangExpression expr = (BLangExpression) selectExpression.getExpression();
                BLangExpression refactoredExpr = (BLangExpression) preSelectDesuagr.rewrite(expr,
                        new BSymbol[]{createEventDataFieldAccessExpr(selectExpression.pos, streamEventSymbol).symbol},
                        streamAliasMap, rhsStream, outputEventType);
                recordKeyValue.valueExpr = desugar.addConversionExprIfRequired(refactoredExpr, symTable.anydataType);
            }
            recordKeyValueList.add(recordKeyValue);
        }
        return recordKeyValueList;
    }

    private void createOutputMapKey(DiagnosticPos pos, BLangSelectExpression selectExpression,
                                    BLangRecordLiteral.BLangRecordKeyValue recordKeyValue) {
        if (selectExpression.getIdentifier() != null) {
            BLangSimpleVarRef varRef = (BLangSimpleVarRef) TreeBuilder.createSimpleVariableReferenceNode();
            varRef.variableName = ASTBuilderUtil.createIdentifier(pos, selectExpression.getIdentifier());
            recordKeyValue.key = new BLangRecordLiteral.BLangRecordKey(varRef);
            BVarSymbol symbol = getOutputEventFieldSymbol(outputEventType, selectExpression.getIdentifier());
            if (symbol == null) {
                dlog.error(varRef.pos, DiagnosticCode.UNDEFINED_OUTPUT_STREAM_ATTRIBUTE, varRef);
            } else {
                recordKeyValue.key.fieldSymbol = symbol;
            }
        } else {
            if (selectExpression.getExpression().getKind() == NodeKind.FIELD_BASED_ACCESS_EXPR) {
                BLangSimpleVarRef varRef = (BLangSimpleVarRef) TreeBuilder.createSimpleVariableReferenceNode();
                varRef.variableName = ((BLangFieldBasedAccess) selectExpression.getExpression()).field;
                recordKeyValue.key = new BLangRecordLiteral.BLangRecordKey(varRef);
                BVarSymbol symbol = getOutputEventFieldSymbol(outputEventType,
                        ((BLangFieldBasedAccess) selectExpression.getExpression()).field.value);
                if (symbol == null) {
                    dlog.error(varRef.pos, DiagnosticCode.UNDEFINED_OUTPUT_STREAM_ATTRIBUTE, varRef);
                } else {
                    recordKeyValue.key.fieldSymbol = symbol;
                }
            } else {
                BLangExpression expr = (BLangExpression) selectExpression.getExpression();
                recordKeyValue.key = new BLangRecordLiteral.BLangRecordKey(expr);
                dlog.error(expr.pos, DiagnosticCode.UNDEFINED_SELECT_EXPR_ALIAS);
            }
        }
    }

    //this function will refactor the selector invocations appropriately by checking whether it is an aggregate or not
    private BLangExpression refactorSelectorInvocation(BVarSymbol streamEventSymbol, BVarSymbol aggregatorArraySymbol,
                                                       LongAdder aggregatorIndex, BLangSelectExpression selectExpr,
                                                       BLangGroupBy groupBy) {
        // Aggregator invocation in streaming query ( sum(..), count(..) .. etc)
        BLangInvocation invocation = (BLangInvocation) selectExpr.getExpression();

        // If the function is a library function
        if (invocation.expr != null) {
            invocation.expr = (BLangExpression) preSelectDesuagr.rewrite(invocation.expr,
                    new BSymbol[]{createEventDataFieldAccessExpr(invocation.expr.pos, streamEventSymbol).symbol},
                    streamAliasMap, rhsStream, outputEventType);
            invocation.expr = desugar.addConversionExprIfRequired(invocation.expr, invocation.type);
            return desugar.addConversionExprIfRequired(invocation, symTable.anydataType);
        }

        BInvokableSymbol symbol = getInvokableSymbol(invocation, AGGREGATOR_OBJECT_NAME);
        if (symbol != null) {
            if (isReturnTypeMatching(invocation.pos, AGGREGATOR_OBJECT_NAME, symbol)) {
                aggregatorIndex.increment();
                // aggregatorArr[0].process(e.data["inputStream.age"], e.eventType)
                return generateAggregatorInvocation(streamEventSymbol, aggregatorArraySymbol,
                        aggregatorIndex.longValue() - 1, invocation);
            } else {
                invocation = (BLangInvocation) preSelectDesuagr.rewrite(invocation,
                        new BSymbol[]{createEventDataFieldAccessExpr(invocation.pos, streamEventSymbol).symbol},
                                                                        streamAliasMap, rhsStream, outputEventType);
                return desugar.addConversionExprIfRequired(invocation, symTable.anydataType);
            }
        }

        return null;
    }

    private BLangInvocation generateAggregatorInvocation(BVarSymbol streamEventSymbol, BVarSymbol aggregatorArraySymbol,
                                                         long aggregatorIndex, BLangInvocation invocation) {
        // aggregatorArr[0]
        BLangIndexBasedAccess indexBasedAccess = createIndexBasedAggregatorExpr(aggregatorArraySymbol,
                aggregatorIndex, invocation.pos);

        // aggregatorArr[0].process(..)
        BLangInvocation aggregatorInvocation = ASTBuilderUtil.createInvocationExpr(invocation.pos,
                getNextProcessFunctionSymbol(indexBasedAccess.type.tsymbol), Collections.emptyList(), symResolver);
        aggregatorInvocation.expr = indexBasedAccess;

        // arguments of aggregatorArr[0].process(..). e.g. (t.age, e.eventType)
        List<BVarSymbol> params = ((BInvokableSymbol) aggregatorInvocation.symbol).params;
        aggregatorInvocation.requiredArgs = generateAggregatorInvocationArgs(streamEventSymbol, invocation,
                params);

        return aggregatorInvocation;
    }

    private List<BLangExpression> generateAggregatorInvocationArgs(BVarSymbol streamEventSymbol,
                                                                   BLangInvocation funcInvocation,
                                                                   List<BVarSymbol> params) {
        // generates the fields which will be aggregated e.g. t.age
        List<BLangExpression> args = generateAggregatorInputFieldsArgs(streamEventSymbol, funcInvocation,
                params);
        // generate the EventType for the aggregation o.eventType
        BLangFieldBasedAccess streamEventFieldAccess = generateStreamEventTypeForAggregatorArg(streamEventSymbol,
                funcInvocation.pos, params);
        // (t.age, o.eventType)
        args.add(streamEventFieldAccess);
        return args;
    }

    private BLangFieldBasedAccess generateStreamEventTypeForAggregatorArg(BVarSymbol streamEventSymbol,
                                                                          DiagnosticPos pos,
                                                                          List<BVarSymbol> params) {
        BLangFieldBasedAccess streamEventFieldAccess = createFieldBasedEventTypeExpr(streamEventSymbol, pos);
        // always the 2nd parameter is the EventType, so the 2nd parameter's type should match.
        streamEventFieldAccess.type = params.get(1).type;
        return streamEventFieldAccess;
    }

    private List<BLangExpression> generateAggregatorInputFieldsArgs(BVarSymbol streamEventSymbol,
                                                                    BLangInvocation funcInvocation,
                                                                    List<BVarSymbol> params) {
        List<BLangExpression> args = new ArrayList<>();
        int i = 0;
        for (BLangExpression expr : funcInvocation.argExprs) {
            if (expr.getKind() == NodeKind.FIELD_BASED_ACCESS_EXPR) {
                BLangExpression mapAccessExpr = createMapVariableIndexAccessExpr((BVarSymbol)
                        createEventDataFieldAccessExpr(expr.pos, streamEventSymbol).symbol, expr);
                BLangExpression castExpr = desugar.addConversionExprIfRequired(mapAccessExpr, params.get(i).type);
                args.add(castExpr);
            } else {
                args.add(expr);
            }
            i++;
        }
        // handles special cases like count(), which does not need arguments.
        if (args.isEmpty()) {
            args.add(ASTBuilderUtil.createLiteral(funcInvocation.pos, symTable.nilType, Names.NIL_VALUE));
        }
        return args;
    }

    private BLangFieldBasedAccess createFieldBasedEventTypeExpr(BVarSymbol streamEventSymbol, DiagnosticPos pos) {
        // o.eventType without the type
        BLangSimpleVarRef varStreamEventRef = ASTBuilderUtil.createVariableRef(pos, streamEventSymbol);
        return ASTBuilderUtil.createFieldAccessExpr(varStreamEventRef, ASTBuilderUtil.createIdentifier(pos,
                EVENT_TYPE_VARIABLE_NAME));
    }

    private BLangIndexBasedAccess createIndexBasedAggregatorExpr(BVarSymbol aggregatorArraySymbol, long aggregatorIndex,
                                                                 DiagnosticPos pos) {
        BLangSimpleVarRef fieldVarRef = ASTBuilderUtil.createVariableRef(pos, aggregatorArraySymbol);
        BLangLiteral indexExpr = ASTBuilderUtil.createLiteral(pos, symTable.intType, aggregatorIndex);
        BLangIndexBasedAccess indexAccessExpr = ASTBuilderUtil.createIndexAccessExpr(fieldVarRef, indexExpr);
        indexAccessExpr.type = ((BArrayType) aggregatorArraySymbol.type).eType;
        return indexAccessExpr;
    }

    private BLangIndexBasedAccess createMapVariableIndexAccessExpr(BVarSymbol mapVariableSymbol,
                                                                   BLangExpression expression) {
        BLangSimpleVarRef varRef = ASTBuilderUtil.createVariableRef(expression.pos, mapVariableSymbol);
        BLangIndexBasedAccess indexExpr = ASTBuilderUtil.createIndexAccessExpr(varRef,
                ASTBuilderUtil.createLiteral(expression.pos, symTable.stringType, expression.toString()));
        indexExpr.type = ((BMapType) mapVariableSymbol.type).constraint;
        indexExpr.pos = expression.pos;
        return indexExpr;
    }

    private BLangLambdaFunction createLambdaFunction(DiagnosticPos pos,
                                                     List<BLangSimpleVariable> lambdaFunctionVariable,
                                                     Set<BVarSymbol> closureVarSymbols, TypeNode returnType) {
        BLangLambdaFunction lambdaFunction = (BLangLambdaFunction) TreeBuilder.createLambdaFunctionNode();
        BLangFunction func = ASTBuilderUtil.createFunction(pos,
                getFunctionName(FUNC_CALLER));
        lambdaFunction.function = func;
        BLangBlockStmt lambdaBody = ASTBuilderUtil.createBlockStmt(pos);
        func.requiredParams.addAll(lambdaFunctionVariable);
        func.setReturnTypeNode(returnType);
        func.desugaredReturnType = true;
        defineFunction(func, env.enclPkg);
        lambdaFunctionVariable = func.requiredParams;

        func.body = lambdaBody;
        func.closureVarSymbols = closureVarSymbols;
        func.desugared = false;
        lambdaFunction.pos = pos;
        List<BType> paramTypes = new ArrayList<>();
        lambdaFunctionVariable.forEach(variable -> paramTypes.add(variable.symbol.type));
        lambdaFunction.type = new BInvokableType(paramTypes, func.symbol.type.getReturnType(),
                null);
        return lambdaFunction;
    }

    private void createStreamAliasMap(BLangStreamingInput streamingInput) {
        //Identify the alias if there anything as such
        if (streamingInput.getAlias() != null) {
            streamAliasMap.put(((BLangVariableReference) streamingInput.getStreamReference()).symbol.toString(),
                    streamingInput.getAlias());
        }
    }

    private BLangSimpleVariable createForeachVariables(DiagnosticPos pos, BSymbol symbol) {
        BType type = symTable.mapAnydataType;
        final BLangSimpleVariable variable = ASTBuilderUtil.createVariable(pos, VAR_FOREACH_VAL, type);
        variable.symbol = new BVarSymbol(0, names.fromIdNode(variable.name), symbol.pkgID, variable.type, symbol);
        return variable;
    }

    private BLangInvocation createLengthInvocation(DiagnosticPos pos, BVarSymbol collectionSymbol) {
        BLangInvocation lengthInvocation = (BLangInvocation) TreeBuilder.createInvocationNode();
        lengthInvocation.builtinMethodInvocation = true;
        lengthInvocation.builtInMethod = BLangBuiltInMethod.LENGTH;
        lengthInvocation.expr = ASTBuilderUtil.createVariableRef(pos, collectionSymbol);
        Name lengthMethodName = names.fromBuiltInMethod(BLangBuiltInMethod.LENGTH);
        lengthInvocation.symbol = symResolver.resolveBuiltinOperator(lengthMethodName, symTable.arrayType);
        lengthInvocation.type = symTable.intType;
        return lengthInvocation;
    }

    private void defineVariable(BLangSimpleVariable variable, PackageID pkgID, BSymbol owner) {
        variable.symbol = new BVarSymbol(0, names.fromIdNode(variable.name), pkgID, variable.type, owner);
    }
}
