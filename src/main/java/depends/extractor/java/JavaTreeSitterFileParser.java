package depends.extractor.java;

import depends.entity.FunctionEntity;
import depends.entity.GenericName;
import depends.entity.Expression;
import depends.entity.TypeEntity;
import depends.entity.VarEntity;
import depends.entity.Entity;
import depends.entity.DecoratedEntity;
import depends.entity.repo.EntityRepo;
import depends.importtypes.ExactMatchImport;
import depends.relations.IBindingResolver;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterJava;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tree-sitter based Java parser.
 * Iteration-2 scope: package/import/type + extends/implements.
 */
public class JavaTreeSitterFileParser extends depends.extractor.FileParser {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([\\w\\.]+)");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+(?:static\\s+)?([\\w\\.\\*]+)");
    private static final Pattern TYPE_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_$.]*");

    private final IBindingResolver bindingResolver;
    private final Deque<String> superTypeStack = new ArrayDeque<>();
    private byte[] sourceBytes;

    public JavaTreeSitterFileParser(EntityRepo entityRepo, IBindingResolver bindingResolver) {
        this.entityRepo = entityRepo;
        this.bindingResolver = bindingResolver;
    }

    @Override
    protected void parseFile(String fileFullPath) throws IOException {
        sourceBytes = Files.readAllBytes(Paths.get(fileFullPath));
        String source = new String(sourceBytes, StandardCharsets.UTF_8);
        TSParser parser = new TSParser();
        if (!parser.setLanguage(new TreeSitterJava())) {
            throw new IOException("Failed to initialize Tree-sitter Java language");
        }
        TSTree tree = parser.parseString(null, source);
        JavaHandlerContext context = new JavaHandlerContext(entityRepo, bindingResolver);
        context.startFile(fileFullPath);
        walk(tree.getRootNode(), source, context);
    }

    private void walk(TSNode node, String source, JavaHandlerContext context) {
        String nodeType = node.getType();
        if ("package_declaration".equals(nodeType)) {
            processPackage(node, source, context);
            return;
        }
        if ("import_declaration".equals(nodeType)) {
            processImport(node, source, context);
            return;
        }
        if ("class_declaration".equals(nodeType)
                || "interface_declaration".equals(nodeType)
                || "enum_declaration".equals(nodeType)
                || "annotation_type_declaration".equals(nodeType)) {
            processType(node, source, context);
            return;
        }
        if ("method_declaration".equals(nodeType)) {
            processMethod(node, source, context);
            return;
        }
        if ("constructor_declaration".equals(nodeType)) {
            processConstructor(node, source, context);
            return;
        }
        if ("field_declaration".equals(nodeType)) {
            processField(node, source, context);
            return;
        }
        if ("constant_declaration".equals(nodeType)) {
            processField(node, source, context);
            return;
        }
        if ("enum_constant".equals(nodeType)) {
            processEnumConstant(node, source, context);
            return;
        }
        if ("resource".equals(nodeType)) {
            processResource(node, source, context);
            return;
        }
        if ("enhanced_for_statement".equals(nodeType)) {
            processEnhancedForStatement(node, source, context);
            return;
        }
        if ("local_variable_declaration".equals(nodeType)) {
            processLocalVariable(node, source, context);
            return;
        }
        if ("instanceof_expression".equals(nodeType)) {
            processInstanceofExpression(node, source, context);
            return;
        }
        if ("method_invocation".equals(nodeType)) {
            processMethodInvocation(node, source, context);
            return;
        }
        if ("object_creation_expression".equals(nodeType)) {
            processObjectCreation(node, source, context);
            return;
        }
        if ("array_creation_expression".equals(nodeType)) {
            processArrayCreation(node, source, context);
            return;
        }
        if ("explicit_constructor_invocation".equals(nodeType)
                || "super_constructor_invocation".equals(nodeType)
                || "this_constructor_invocation".equals(nodeType)) {
            processExplicitConstructorInvocation(node, source, context);
            return;
        }
        if ("cast_expression".equals(nodeType)) {
            processCast(node, source, context);
            return;
        }
        if ("assignment_expression".equals(nodeType) || "update_expression".equals(nodeType)) {
            processSetLikeExpression(node, source, context);
            return;
        }
        if ("field_access".equals(nodeType)) {
            processFieldAccess(node, source, context);
            return;
        }
        if ("class_literal".equals(nodeType)) {
            processClassLiteral(node, source, context);
            return;
        }
        if ("lambda_expression".equals(nodeType)) {
            processLambdaExpression(node, source, context);
            return;
        }
        if ("method_reference".equals(nodeType)) {
            processMethodReference(node, source, context);
            return;
        }
        if ("this".equals(nodeType)) {
            processThisExpression(node, context);
            return;
        }
        if ("identifier".equals(nodeType)) {
            processIdentifier(node, source, context);
            return;
        }
        walkChildren(node, source, context);
    }

    private void walkChildren(TSNode node, String source, JavaHandlerContext context) {
        int childCount = node.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getNamedChild(i);
            if (child == null || child.isNull()) {
                continue;
            }
            walk(child, source, context);
        }
    }

    private void processPackage(TSNode node, String source, JavaHandlerContext context) {
        String text = sourceSlice(node, source);
        Matcher matcher = PACKAGE_PATTERN.matcher(text);
        if (matcher.find()) {
            context.foundNewPackage(matcher.group(1));
        }
    }

    private void processImport(TSNode node, String source, JavaHandlerContext context) {
        String text = sourceSlice(node, source);
        Matcher matcher = IMPORT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return;
        }
        String importName = matcher.group(1);
        if (importName.endsWith(".*")) {
            importName = importName.substring(0, importName.length() - 2);
        }
        context.foundNewImport(new ExactMatchImport(importName));
    }

    private void processType(TSNode node, String source, JavaHandlerContext context) {
        TSNode nameNode = node.getChildByFieldName("name");
        if (nameNode == null || nameNode.isNull()) {
            return;
        }
        String typeName = sourceSlice(nameNode, source).trim();
        int line = node.getStartPoint().getRow() + 1;
        TypeEntity typeEntity = context.foundNewType(GenericName.build(typeName), line);
        applyAnnotations(node, source, typeEntity);
        TSNode typeParametersNode = findChildByType(node, "type_parameters");
        processTypeParameters(typeParametersNode, source, context, true);

        TSNode superClassNode = node.getChildByFieldName("superclass");
        String superTypeName = "";
        if (superClassNode != null && !superClassNode.isNull()) {
            List<String> superTypes = extractDeclaredTypeNames(superClassNode, source);
            if (!superTypes.isEmpty()) {
                superTypeName = superTypes.get(0);
                context.foundExtends(GenericName.build(superTypeName));
            }
        }

        TSNode interfacesNode = node.getChildByFieldName("interfaces");
        if (interfacesNode != null && !interfacesNode.isNull()) {
            List<String> interfaceTypes = extractDeclaredTypeNames(interfacesNode, source);
            if ("interface_declaration".equals(node.getType())) {
                for (String intf : interfaceTypes) {
                    context.foundExtends(GenericName.build(intf));
                }
            } else {
                for (String intf : interfaceTypes) {
                    context.foundImplements(GenericName.build(intf));
                }
            }
        }

        TSNode superInterfacesNode = node.getChildByFieldName("super_interfaces");
        if (superInterfacesNode != null && !superInterfacesNode.isNull()) {
            List<String> interfaceTypes = extractDeclaredTypeNames(superInterfacesNode, source);
            for (String intf : interfaceTypes) {
                context.foundExtends(GenericName.build(intf));
            }
        }

        superTypeStack.push(superTypeName);
        walkChildren(node, source, context);
        superTypeStack.pop();
        context.exitLastedEntity();
    }

    private void processMethod(TSNode node, String source, JavaHandlerContext context) {
        TSNode declarator = findChildByType(node, "method_declarator");
        if (declarator == null) {
            declarator = node;
        }
        TSNode nameNode = declarator.getChildByFieldName("name");
        if (nameNode == null || nameNode.isNull()) {
            nameNode = node.getChildByFieldName("name");
        }
        if (nameNode == null || nameNode.isNull()) {
            nameNode = findFirstDescendantByType(declarator, "identifier");
        }
        if (nameNode == null || nameNode.isNull()) {
            return;
        }
        String methodName = sourceSlice(nameNode, source).trim();
        String rawReturnType = extractMethodReturnType(node, source);
        String returnType = parseJavaTypeName(rawReturnType).baseName;
        List<String> throwedTypes = extractThrows(node, source);
        int line = node.getStartPoint().getRow() + 1;
        FunctionEntity method = context.foundMethodDeclarator(methodName, returnType, throwedTypes, line);
        applyAnnotations(node, source, method);
        processTypeParameters(findChildByType(node, "type_parameters"), source, context, false);
        addArrayReturnTypeUseExpression(context, node, rawReturnType);
        addFormalParameters(declarator, source, context, false);
        walkChildren(node, source, context);
        context.exitLastedEntity();
    }

    private void processConstructor(TSNode node, String source, JavaHandlerContext context) {
        TSNode declarator = findChildByType(node, "constructor_declarator");
        if (declarator == null) {
            declarator = node;
        }
        TSNode nameNode = declarator.getChildByFieldName("name");
        if (nameNode == null || nameNode.isNull()) {
            nameNode = node.getChildByFieldName("name");
        }
        if (nameNode == null || nameNode.isNull()) {
            nameNode = findFirstDescendantByType(declarator, "identifier");
        }
        if (nameNode == null || nameNode.isNull()) {
            return;
        }
        String constructorName = sourceSlice(nameNode, source).trim();
        List<String> throwedTypes = extractThrows(node, source);
        int line = node.getStartPoint().getRow() + 1;
        FunctionEntity method = context.foundMethodDeclarator(constructorName, constructorName, throwedTypes, line);
        applyAnnotations(node, source, method);
        addFormalParameters(declarator, source, context, false);
        if (context.currentType() != null) {
            method.addReturnType(context.currentType());
        }
        addExpression(context, nameNode, null, constructorName,
                false, false, false, false, false, false);
        addExplicitSuperConstructorCallExpression(node, source, context);
        walkChildren(node, source, context);
        context.exitLastedEntity();
    }

    private void addExplicitSuperConstructorCallExpression(TSNode node, String source, JavaHandlerContext context) {
        if (superTypeStack.isEmpty() || superTypeStack.peek().isEmpty()) {
            return;
        }
        if (!Pattern.compile("(?m)^\\s*super\\s*\\(").matcher(sourceSlice(node, source)).find()) {
            return;
        }
        String typeName = explicitSuperConstructorTypeName();
        if (typeName.equals(superTypeStack.peek())) {
            return;
        }
        Expression expression = addExpression(context, node, null, null,
                true, false, false, false, false, false);
        expression.setRawType(typeName);
        expression.disableDriveTypeFromChild();
    }

    private String explicitSuperConstructorTypeName() {
        String typeName = superTypeStack.peek();
        if (typeName == null || typeName.contains(".")) {
            return typeName == null ? "" : typeName;
        }
        Iterator<String> iterator = superTypeStack.iterator();
        if (iterator.hasNext()) {
            iterator.next();
        }
        while (iterator.hasNext()) {
            String enclosingSuperType = iterator.next();
            if (enclosingSuperType != null && !enclosingSuperType.isEmpty()) {
                return enclosingSuperType + "." + typeName;
            }
        }
        return typeName;
    }

    private void processField(TSNode node, String source, JavaHandlerContext context) {
        JavaTypeName fieldType = parseJavaTypeName(extractFieldType(node, source));
        List<String> varNames = extractVariableNames(node, source);
        if (fieldType.baseName.isEmpty() || varNames.isEmpty()) {
            walkChildren(node, source, context);
            return;
        }
        List<VarEntity> vars = context.foundVarDefinitions(varNames, fieldType.baseName, fieldType.typeArguments,
                node.getStartPoint().getRow() + 1);
        applyAnnotations(node, source, vars);
        walkChildren(node, source, context);
    }

    private void processLocalVariable(TSNode node, String source, JavaHandlerContext context) {
        JavaTypeName varType = parseJavaTypeName(extractFieldType(node, source));
        List<String> varNames = extractVariableNames(node, source);
        if (varType.baseName.isEmpty() || varNames.isEmpty()) {
            walkChildren(node, source, context);
            return;
        }
        List<VarEntity> vars = context.foundVarDefinitions(varNames, varType.baseName, varType.typeArguments,
                node.getStartPoint().getRow() + 1);
        applyAnnotations(node, source, vars);
        if (!hasOnlySameTypeObjectCreationInitializers(node, varType.baseName, source)) {
            addDeclaredTypeUseExpressions(context, extractFieldTypeNode(node), source);
        }
        walkChildren(node, source, context);
    }

    private boolean hasOnlySameTypeObjectCreationInitializers(TSNode node, String declaredTypeName, String source) {
        List<TSNode> declarators = findChildrenByType(node, "variable_declarator");
        if (declarators.isEmpty() || declaredTypeName == null || declaredTypeName.isEmpty()) {
            return false;
        }
        boolean foundInitializer = false;
        for (TSNode declarator : declarators) {
            TSNode valueNode = declarator.getChildByFieldName("value");
            if (valueNode == null || valueNode.isNull()) {
                return false;
            }
            foundInitializer = true;
            if (!"object_creation_expression".equals(valueNode.getType())) {
                return false;
            }
            TSNode typeNode = valueNode.getChildByFieldName("type");
            if (typeNode == null || typeNode.isNull()) {
                typeNode = findFirstDescendantByType(valueNode, "type_identifier");
            }
            String createdTypeName = parseJavaTypeName(typeNode == null ? "" : sourceSlice(typeNode, source).trim()).baseName;
            if (!declaredTypeName.equals(createdTypeName)) {
                return false;
            }
        }
        return foundInitializer;
    }

    private void processEnumConstant(TSNode node, String source, JavaHandlerContext context) {
        TSNode nameNode = node.getChildByFieldName("name");
        if (nameNode == null || nameNode.isNull()) {
            nameNode = findFirstDescendantByType(node, "identifier");
        }
        if (nameNode == null || nameNode.isNull()) {
            walkChildren(node, source, context);
            return;
        }
        String enumConstName = sourceSlice(nameNode, source).trim();
        if (enumConstName.isEmpty()) {
            walkChildren(node, source, context);
            return;
        }
        VarEntity enumConst = context.foundEnumConstDefinition(enumConstName, node.getStartPoint().getRow() + 1);
        applyAnnotations(node, source, enumConst);
        walkChildren(node, source, context);
    }

    private void processResource(TSNode node, String source, JavaHandlerContext context) {
        JavaTypeName resourceType = parseJavaTypeName(extractFieldType(node, source));
        List<String> resourceNames = extractVariableNames(node, source);
        TSNode nameNode = node.getChildByFieldName("name");
        if (resourceNames.isEmpty() && nameNode != null && !nameNode.isNull()) {
            resourceNames.add(sourceSlice(nameNode, source).trim());
        }
        if (!resourceType.baseName.isEmpty() && !resourceNames.isEmpty()) {
            context.foundVarDefinitions(resourceNames, resourceType.baseName, resourceType.typeArguments,
                    node.getStartPoint().getRow() + 1);
        }
        addDeclaredTypeUseExpressions(context, extractFieldTypeNode(node), source);
        walkChildren(node, source, context);
    }

    private void processEnhancedForStatement(TSNode node, String source, JavaHandlerContext context) {
        TSNode typeNode = node.getChildByFieldName("type");
        TSNode nameNode = node.getChildByFieldName("name");
        JavaTypeName varType = parseJavaTypeName(typeNode == null || typeNode.isNull() ? "" : sourceSlice(typeNode, source).trim());
        String varName = nameNode == null || nameNode.isNull() ? "" : sourceSlice(nameNode, source).trim();
        if (varType.baseName.isEmpty() || varName.isEmpty()) {
            Matcher matcher = Pattern.compile("for\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$.<>]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:")
                    .matcher(sourceSlice(node, source));
            if (matcher.find()) {
                varType = parseJavaTypeName(matcher.group(1));
                varName = matcher.group(2);
            }
        }
        if (!varType.baseName.isEmpty() && !varName.isEmpty()) {
            context.foundVarDefinition(varName, GenericName.build(varType.baseName), varType.typeArguments,
                    node.getStartPoint().getRow() + 1);
        }
        addDeclaredTypeUseExpressions(context, typeNode, source);
        walkChildren(node, source, context);
    }

    private void processInstanceofExpression(TSNode node, String source, JavaHandlerContext context) {
        TSNode rightNode = node.getChildByFieldName("right");
        if (rightNode != null && !rightNode.isNull()) {
            String rawRight = sourceSlice(rightNode, source).trim();
            JavaTypeName instanceofType = parseJavaTypeName(extractInstanceofTypeName(rawRight));
            if (!instanceofType.baseName.isEmpty()) {
                Expression expression = addExpression(context, rightNode, null, null,
                        false, false, false, true, false, false);
                expression.setRawType(instanceofType.baseName);
                expression.disableDriveTypeFromChild();
                addTypeUseExpression(context, rightNode, instanceofType.baseName);
            }
            Matcher matcher = Pattern.compile("([A-Za-z_][A-Za-z0-9_$.]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$")
                    .matcher(rawRight);
            if (matcher.find()) {
                JavaTypeName typeName = parseJavaTypeName(matcher.group(1));
                String varName = matcher.group(2);
                context.foundVarDefinition(varName, GenericName.build(typeName.baseName), typeName.typeArguments,
                        node.getStartPoint().getRow() + 1);
            }
        }
        walkChildren(node, source, context);
    }

    private String extractInstanceofTypeName(String rawRight) {
        Matcher patternMatcher = Pattern.compile("([A-Za-z_][A-Za-z0-9_$.]*(?:\\s*<[^>]+>)?(?:\\s*\\[\\])*)\\s+[A-Za-z_][A-Za-z0-9_]*\\s*$")
                .matcher(rawRight);
        if (patternMatcher.find()) {
            return patternMatcher.group(1).trim();
        }
        return rawRight;
    }

    private void processMethodInvocation(TSNode node, String source, JavaHandlerContext context) {
        TSNode nameNode = node.getChildByFieldName("name");
        if (nameNode == null || nameNode.isNull()) {
            nameNode = findFirstDescendantByType(node, "identifier");
        }
        if (nameNode == null || nameNode.isNull()) {
            return;
        }
        String callName = sourceSlice(nameNode, source).trim();
        TSNode objectNode = node.getChildByFieldName("object");
        boolean isDotCall = objectNode != null && !objectNode.isNull();
        Expression callExpression = addExpression(context, node, null, callName,
                true, isDotCall, false, false, false, false);
        if (shouldUseResolvedExpressionType(node, source)) {
            callExpression.enableTypeDependencyUse();
        }
        if (shouldEmitAssertionArgumentTypeCallForArgument(node, source)) {
            callExpression.enableTypeDependencyCall();
        }
        if (isDotCall) {
            addObjectExpression(objectNode, source, context, callExpression);
        }
        processInvocationArguments(node, source, context);
    }

    private void processObjectCreation(TSNode node, String source, JavaHandlerContext context) {
        TSNode typeNode = node.getChildByFieldName("type");
        if (typeNode == null || typeNode.isNull()) {
            typeNode = findFirstDescendantByType(node, "type_identifier");
        }
        String typeName = parseJavaTypeName(typeNode == null ? "" : sourceSlice(typeNode, source).trim()).baseName;
        if (typeName.isEmpty()) {
            return;
        }
        Expression expression = addExpression(context, node, null, null,
                true, false, true, false, false, false);
        expression.setRawType(typeName);
        expression.disableDriveTypeFromChild();
        if (isInsideAssertThrowsLambda(node, source)) {
            expression.enableTypeDependencyUse();
        }
        if (shouldEmitCreateTypeUse(node)) {
            addTypeUseExpression(context, typeNode, typeName);
        }
        walkChildren(node, source, context);
    }

    private void processArrayCreation(TSNode node, String source, JavaHandlerContext context) {
        TSNode typeNode = node.getChildByFieldName("type");
        if (typeNode == null || typeNode.isNull()) {
            typeNode = findFirstDescendantByType(node, "type_identifier");
        }
        String typeName = parseJavaTypeName(typeNode == null ? "" : sourceSlice(typeNode, source).trim()).baseName;
        if (typeName.isEmpty()) {
            walkChildren(node, source, context);
            return;
        }
        Expression expression = addExpression(context, node, null, null,
                true, false, true, false, false, false);
        expression.setRawType(typeName);
        expression.disableDriveTypeFromChild();
        walkChildren(node, source, context);
    }

    private void processExplicitConstructorInvocation(TSNode node, String source, JavaHandlerContext context) {
        String raw = sourceSlice(node, source).trim();
        String typeName = "";
        if (raw.startsWith("super")) {
            typeName = superTypeStack.isEmpty() ? "" : superTypeStack.peek();
        } else if (raw.startsWith("this") && context.currentType() != null) {
            typeName = context.currentType().getRawName().uniqName();
        }
        if (!typeName.isEmpty()) {
            Expression expression = addExpression(context, node, null, null,
                    true, false, false, false, false, false);
            expression.setRawType(typeName);
            expression.disableDriveTypeFromChild();
        }
        processInvocationArguments(node, source, context);
    }

    private void processCast(TSNode node, String source, JavaHandlerContext context) {
        TSNode typeNode = node.getChildByFieldName("type");
        if (typeNode == null || typeNode.isNull()) {
            typeNode = findFirstDescendantByType(node, "type_identifier");
        }
        String typeName = parseJavaTypeName(typeNode == null ? "" : sourceSlice(typeNode, source).trim()).baseName;
        if (typeName.isEmpty()) {
            return;
        }
        Expression expression = addExpression(context, node, null, null,
                false, false, false, true, false, false);
        expression.setRawType(typeName);
        expression.disableDriveTypeFromChild();
        addTypeUseExpression(context, typeNode, typeName);
        TSNode valueNode = node.getChildByFieldName("value");
        if (valueNode != null && !valueNode.isNull()) {
            walk(valueNode, source, context);
        }
    }

    private void processSetLikeExpression(TSNode node, String source, JavaHandlerContext context) {
        TSNode leftNode = node.getChildByFieldName("left");
        if (leftNode == null || leftNode.isNull()) {
            leftNode = firstNamedChild(node);
        }
        if (leftNode == null || leftNode.isNull()) {
            return;
        }
        processFieldAccessTargets(leftNode, source, context);
        String leftIdentifier = extractSimpleIdentifier(leftNode, source);
        if (leftIdentifier.isEmpty()) {
            return;
        }
        addExpression(context, node, null, leftIdentifier,
                false, false, false, false, true, false);
        TSNode rightNode = node.getChildByFieldName("right");
        if (rightNode != null && !rightNode.isNull()) {
            walk(rightNode, source, context);
        }
    }

    private void processFieldAccessTargets(TSNode node, String source, JavaHandlerContext context) {
        if (node == null || node.isNull()) {
            return;
        }
        if ("field_access".equals(node.getType())) {
            addFieldAccessExpression(node, source, context, null);
        }
        int childCount = node.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            processFieldAccessTargets(node.getNamedChild(i), source, context);
        }
    }

    private void processFieldAccess(TSNode node, String source, JavaHandlerContext context) {
        addFieldAccessExpression(node, source, context, null);
    }

    private void processClassLiteral(TSNode node, String source, JavaHandlerContext context) {
        addClassLiteralObjectExpression(node, source, context, null);
    }

    private Expression addFieldAccessExpression(TSNode node,
                                                String source,
                                                JavaHandlerContext context,
                                                Expression parent) {
        TSNode fieldNode = node.getChildByFieldName("field");
        if (fieldNode == null || fieldNode.isNull()) {
            fieldNode = findFirstDescendantByType(node, "identifier");
        }
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        String fieldName = sourceSlice(fieldNode, source).trim();
        if (fieldName.isEmpty()) {
            return null;
        }
        Expression fieldExpression = addExpression(context, node, parent, fieldName,
                false, true, false, false, false, false);
        if (shouldUseResolvedExpressionType(node, source)) {
            fieldExpression.enableTypeDependencyUse();
        }
        TSNode objectNode = node.getChildByFieldName("object");
        if (objectNode != null && !objectNode.isNull()) {
            addObjectExpression(objectNode, source, context, fieldExpression);
            JavaTypeName objectTypeName = declaredTypeForObjectExpression(objectNode, source, context);
            if (!objectTypeName.baseName.isEmpty()) {
                Expression typedFieldExpression = addExpression(context, node, null, fieldName,
                        false, true, false, false, false, false);
                Expression typedOwnerExpression = addExpression(context, objectNode, typedFieldExpression, null,
                        false, false, false, false, false, false);
                typedOwnerExpression.setRawType(GenericName.build(objectTypeName.baseName, objectTypeName.typeArguments));
                typedOwnerExpression.disableDriveTypeFromChild();
                typedOwnerExpression.setStatement(true);
            }
        }
        return fieldExpression;
    }

    private void processThisExpression(TSNode node, JavaHandlerContext context) {
        if (context.currentType() == null) {
            return;
        }
        Expression expression = addExpression(context, node, null, null,
                false, false, false, false, false, false);
        expression.setRawType(context.currentType().getRawName());
        expression.disableDriveTypeFromChild();
        expression.enableTypeDependencyUse();
    }

    private void processLambdaExpression(TSNode node, String source, JavaHandlerContext context) {
        addLambdaParameters(node, source, context);
        walkChildren(node, source, context);
    }

    private void processMethodReference(TSNode node, String source, JavaHandlerContext context) {
        String raw = sourceSlice(node, source).trim();
        if (raw.isEmpty() || !raw.contains("::")) {
            return;
        }
        String[] parts = raw.split("::", 2);
        if (parts.length != 2) {
            return;
        }
        String left = parts[0].trim();
        String right = parts[1].trim();
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }
        if ("new".equals(right)) {
            Expression createExpr = addExpression(context, node, null, null,
                    true, false, true, false, false, false);
            createExpr.setRawType(left);
            createExpr.disableDriveTypeFromChild();
            return;
        }

        Expression refCall = addExpression(context, node, null, right,
                true, true, false, false, false, false);
        addExpression(context, node, refCall, left,
                false, false, false, false, false, false);
    }

    private void processIdentifier(TSNode node, String source, JavaHandlerContext context) {
        if (isDeclarationIdentifier(node) || isTypeSyntaxIdentifier(node)) {
            return;
        }
        String identifier = sourceSlice(node, source).trim();
        if (identifier.isEmpty()) {
            return;
        }
        Expression expression = addExpression(context, node, null, identifier,
                false, false, false, false, false, false);
        if (shouldUseResolvedExpressionType(node, source)) {
            expression.enableTypeDependencyUse();
        }
        if (shouldEmitAssertionArgumentTypeCallForArgument(node, source)) {
            expression.enableTypeDependencyCall();
        }
    }

    private boolean isDeclarationIdentifier(TSNode node) {
        TSNode parent = node.getParent();
        if (parent == null || parent.isNull()) {
            return false;
        }
        String parentType = parent.getType();
        String fieldName = fieldNameForChild(parent, node);
        if ("name".equals(fieldName)) {
            return true;
        }
        return "package_declaration".equals(parentType)
                || "import_declaration".equals(parentType)
                || "scoped_identifier".equals(parentType)
                || "method_invocation".equals(parentType)
                || "field_access".equals(parentType)
                || "method_reference".equals(parentType);
    }

    private boolean isTypeSyntaxIdentifier(TSNode node) {
        TSNode parent = node.getParent();
        while (parent != null && !parent.isNull()) {
            String parentType = parent.getType();
            if ("type_identifier".equals(parentType)
                    || "scoped_type_identifier".equals(parentType)
                    || "generic_type".equals(parentType)
                    || "array_type".equals(parentType)
                    || "type_arguments".equals(parentType)
                    || "integral_type".equals(parentType)
                    || "floating_point_type".equals(parentType)
                    || "void_type".equals(parentType)) {
                return true;
            }
            if (!isTransparentTypeSyntaxParent(parentType)) {
                return false;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private boolean isTransparentTypeSyntaxParent(String nodeType) {
        return "identifier".equals(nodeType)
                || "type_identifier".equals(nodeType)
                || "scoped_type_identifier".equals(nodeType)
                || "generic_type".equals(nodeType)
                || "array_type".equals(nodeType)
                || "type_arguments".equals(nodeType)
                || "wildcard".equals(nodeType)
                || "annotated_type".equals(nodeType)
                || "dimensions".equals(nodeType);
    }

    private String fieldNameForChild(TSNode parent, TSNode child) {
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode candidate = parent.getChild(i);
            if (candidate != null && TSNode.eq(candidate, child)) {
                return parent.getFieldNameForChild(i);
            }
        }
        return null;
    }

    private void processTypeParameters(TSNode typeParametersNode,
                                       String source,
                                       JavaHandlerContext context,
                                       boolean addDeclaredGenericToCurrentType) {
        if (typeParametersNode == null || typeParametersNode.isNull()) {
            return;
        }
        List<TSNode> params = findChildrenByType(typeParametersNode, "type_parameter");
        for (TSNode param : params) {
            TSNode nameNode = param.getChildByFieldName("name");
            String genericName = "";
            if (nameNode != null && !nameNode.isNull()) {
                genericName = sourceSlice(nameNode, source).trim();
            }
            if (addDeclaredGenericToCurrentType && context.currentType() != null && !genericName.isEmpty()) {
                context.currentType().addTypeParameter(GenericName.build(genericName));
            }

            String rawParam = sourceSlice(param, source).trim();
            List<String> boundNames = extractTypeNames(rawParam);
            for (String bound : boundNames) {
                if (bound.equals(genericName)) {
                    continue;
                }
                context.foundTypeParametes(GenericName.build(bound));
            }
        }
    }

    private String sourceSlice(TSNode node, String source) {
        int start = Math.max(0, node.getStartByte());
        int end = Math.min(sourceBytes.length, node.getEndByte());
        if (start >= end) {
            return "";
        }
        return new String(sourceBytes, start, end - start, StandardCharsets.UTF_8);
    }

    private List<String> extractTypeNames(String text) {
        String normalized = text
                .replace("extends", " ")
                .replace("implements", " ")
                .replace("<", " ")
                .replace(">", " ")
                .replace("&", " ");
        Matcher matcher = TYPE_NAME_PATTERN.matcher(normalized);
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            String candidate = matcher.group();
            if ("extends".equals(candidate) || "implements".equals(candidate)) {
                continue;
            }
            names.add(candidate);
        }
        return names;
    }

    private List<String> extractDeclaredTypeNames(TSNode node, String source) {
        List<String> names = new ArrayList<>();
        collectDeclaredTypeNames(node, source, names);
        return names;
    }

    private void collectDeclaredTypeNames(TSNode node, String source, List<String> names) {
        if (node == null || node.isNull()) {
            return;
        }
        String nodeType = node.getType();
        if (isTypeNameNode(nodeType)) {
            String typeName = parseJavaTypeName(sourceSlice(node, source).trim()).baseName;
            if (!typeName.isEmpty()) {
                names.add(typeName);
            }
            return;
        }
        int childCount = node.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            collectDeclaredTypeNames(node.getNamedChild(i), source, names);
        }
    }

    private JavaTypeName parseJavaTypeName(String rawType) {
        if (rawType == null) {
            return new JavaTypeName("", new ArrayList<>());
        }
        String text = rawType.trim();
        if (text.isEmpty()) {
            return new JavaTypeName("", new ArrayList<>());
        }
        text = text.replace("...", "");
        while (text.endsWith("[]")) {
            text = text.substring(0, text.length() - 2).trim();
        }
        while (text.startsWith("@")) {
            int space = text.indexOf(' ');
            if (space < 0) {
                return new JavaTypeName("", new ArrayList<>());
            }
            text = text.substring(space + 1).trim();
        }
        if (text.startsWith("? extends ")) {
            text = text.substring("? extends ".length()).trim();
        } else if (text.startsWith("? super ")) {
            text = text.substring("? super ".length()).trim();
        } else if ("?".equals(text)) {
            return new JavaTypeName("Object", new ArrayList<>());
        }

        int genericStart = findTopLevelChar(text, '<');
        if (genericStart < 0) {
            return new JavaTypeName(text, new ArrayList<>());
        }
        String baseName = text.substring(0, genericStart).trim();
        int genericEnd = findMatchingGenericEnd(text, genericStart);
        if (genericEnd < 0) {
            return new JavaTypeName(baseName, new ArrayList<>());
        }
        String argumentsText = text.substring(genericStart + 1, genericEnd);
        List<GenericName> arguments = new ArrayList<>();
        for (String argument : splitTopLevel(argumentsText, ',')) {
            JavaTypeName parsedArgument = parseJavaTypeName(argument);
            if (!parsedArgument.baseName.isEmpty()) {
                arguments.add(GenericName.build(parsedArgument.baseName, parsedArgument.typeArguments));
            }
        }
        return new JavaTypeName(baseName, arguments);
    }

    private int findTopLevelChar(String text, char target) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '<') {
                if (target == '<' && depth == 0) {
                    return i;
                }
                depth++;
            } else if (ch == '>') {
                depth = Math.max(0, depth - 1);
            } else if (ch == target && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private int findMatchingGenericEnd(String text, int genericStart) {
        int depth = 0;
        for (int i = genericStart; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '<') {
                depth++;
            } else if (ch == '>') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private List<String> splitTopLevel(String text, char separator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '<') {
                depth++;
            } else if (ch == '>') {
                depth = Math.max(0, depth - 1);
            } else if (ch == separator && depth == 0) {
                parts.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(text.substring(start).trim());
        return parts;
    }

    private static class JavaTypeName {
        private final String baseName;
        private final List<GenericName> typeArguments;

        private JavaTypeName(String baseName, List<GenericName> typeArguments) {
            this.baseName = baseName;
            this.typeArguments = typeArguments;
        }
    }

    private TSNode findChildByType(TSNode node, String childType) {
        int childCount = node.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getNamedChild(i);
            if (child != null && !child.isNull() && childType.equals(child.getType())) {
                return child;
            }
        }
        return null;
    }

    private List<TSNode> findChildrenByType(TSNode node, String childType) {
        List<TSNode> result = new ArrayList<>();
        int childCount = node.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getNamedChild(i);
            if (child != null && !child.isNull() && childType.equals(child.getType())) {
                result.add(child);
            }
        }
        return result;
    }

    private String extractMethodReturnType(TSNode methodNode, String source) {
        int childCount = methodNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = methodNode.getNamedChild(i);
            if (child == null || child.isNull()) {
                continue;
            }
            String type = child.getType();
            if ("modifiers".equals(type) || "type_parameters".equals(type) || "method_declarator".equals(type)
                    || "throws".equals(type) || "block".equals(type)) {
                continue;
            }
            return sourceSlice(child, source).trim();
        }
        return "";
    }

    private List<String> extractThrows(TSNode node, String source) {
        TSNode throwsNode = findChildByType(node, "throws");
        if (throwsNode == null) {
            return new ArrayList<>();
        }
        return extractTypeNames(sourceSlice(throwsNode, source));
    }

    private void addFormalParameters(TSNode declarator, String source, JavaHandlerContext context, boolean emitTypeUse) {
        TSNode parametersNode = declarator.getChildByFieldName("parameters");
        if (parametersNode == null || parametersNode.isNull()) {
            parametersNode = findChildByType(declarator, "formal_parameters");
        }
        if (parametersNode == null || parametersNode.isNull()) {
            parametersNode = findFirstDescendantByType(declarator, "formal_parameters");
        }
        if (parametersNode == null || parametersNode.isNull()) {
            return;
        }

        List<TSNode> parameterNodes = new ArrayList<>();
        parameterNodes.addAll(findChildrenByType(parametersNode, "formal_parameter"));
        parameterNodes.addAll(findChildrenByType(parametersNode, "spread_parameter"));
        for (TSNode paramNode : parameterNodes) {
            TSNode nameNode = extractParameterNameNode(paramNode);
            TSNode typeNode = extractParameterTypeNode(paramNode);
            if (nameNode == null || nameNode.isNull()) {
                continue;
            }
            String paramName = sourceSlice(nameNode, source).trim();
            VarEntity param = context.addMethodParameter(paramName);
            if (param == null) {
                continue;
            }
            param.setLine(paramNode.getStartPoint().getRow() + 1);
            if (typeNode != null && !typeNode.isNull()) {
                JavaTypeName paramType = parseJavaTypeName(sourceSlice(typeNode, source).trim());
                param.setRawType(GenericName.build(paramType.baseName, paramType.typeArguments));
                param.addTypeParameter(paramType.typeArguments);
                if (emitTypeUse) {
                    addTypeUseExpression(context, typeNode, paramType.baseName);
                }
            }
        }
    }

    private TSNode extractParameterNameNode(TSNode paramNode) {
        TSNode nameNode = paramNode.getChildByFieldName("name");
        if (nameNode != null && !nameNode.isNull()) {
            return nameNode;
        }
        TSNode declarator = findChildByType(paramNode, "variable_declarator");
        if (declarator == null || declarator.isNull()) {
            return null;
        }
        nameNode = declarator.getChildByFieldName("name");
        if (nameNode != null && !nameNode.isNull()) {
            return nameNode;
        }
        return null;
    }

    private TSNode extractParameterTypeNode(TSNode paramNode) {
        TSNode typeNode = paramNode.getChildByFieldName("type");
        if (typeNode != null && !typeNode.isNull()) {
            return widenDeclaredTypeNode(typeNode);
        }
        int childCount = paramNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = paramNode.getNamedChild(i);
            if (child != null && !child.isNull() && isTypeNameNode(child.getType())) {
                return widenDeclaredTypeNode(child);
            }
        }
        return null;
    }

    private boolean isTypeNameNode(String nodeType) {
        return "type_identifier".equals(nodeType)
                || "scoped_type_identifier".equals(nodeType)
                || "generic_type".equals(nodeType)
                || "array_type".equals(nodeType)
                || "integral_type".equals(nodeType)
                || "floating_point_type".equals(nodeType)
                || "boolean_type".equals(nodeType)
                || "void_type".equals(nodeType);
    }

    private String extractFieldType(TSNode fieldNode, String source) {
        TSNode typeNode = extractFieldTypeNode(fieldNode);
        if (typeNode != null && !typeNode.isNull()) {
            return sourceSlice(typeNode, source).trim();
        }
        return "";
    }

    private TSNode extractFieldTypeNode(TSNode fieldNode) {
        TSNode typeNode = fieldNode.getChildByFieldName("type");
        if (typeNode != null && !typeNode.isNull()) {
            return widenDeclaredTypeNode(typeNode);
        }
        int childCount = fieldNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = fieldNode.getNamedChild(i);
            if (child == null || child.isNull()) {
                continue;
            }
            if (!"modifiers".equals(child.getType()) && !"variable_declarator".equals(child.getType())) {
                return widenDeclaredTypeNode(child);
            }
        }
        return null;
    }

    private TSNode widenDeclaredTypeNode(TSNode typeNode) {
        TSNode current = typeNode;
        TSNode parent = current.getParent();
        while (parent != null && !parent.isNull() && isTypeNameNode(parent.getType())) {
            current = parent;
            parent = current.getParent();
        }
        return current;
    }

    private List<String> extractVariableNames(TSNode fieldNode, String source) {
        List<String> names = new ArrayList<>();
        List<TSNode> declarators = findChildrenByType(fieldNode, "variable_declarator");
        for (TSNode declarator : declarators) {
            TSNode nameNode = declarator.getChildByFieldName("name");
            if (nameNode != null && !nameNode.isNull()) {
                names.add(sourceSlice(nameNode, source).trim());
            }
        }
        return names;
    }

    private TSNode findFirstDescendantByType(TSNode node, String targetType) {
        int childCount = node.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getNamedChild(i);
            if (child == null || child.isNull()) {
                continue;
            }
            if (targetType.equals(child.getType())) {
                return child;
            }
            TSNode found = findFirstDescendantByType(child, targetType);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private Expression addExpression(JavaHandlerContext context,
                                     TSNode keyNode,
                                     Expression parent,
                                     String identifier,
                                     boolean call,
                                     boolean dot,
                                     boolean create,
                                     boolean cast,
                                     boolean set,
                                     boolean logic) {
        if (context.lastContainer() == null) {
            return new Expression(entityRepo.generateId());
        }
        Expression expression = new Expression(entityRepo.generateId());
        expression.setLine(keyNode.getStartPoint().getRow() + 1);
        expression.setParent(parent);
        expression.setCall(call);
        expression.setDot(dot);
        expression.setCreate(create);
        expression.setCast(cast);
        expression.setSet(set);
        expression.setLogic(logic);
        if (identifier != null && !identifier.isEmpty()) {
            expression.setIdentifier(identifier);
        }
        // TSNode is stable enough as expression key during one parse walk.
        context.lastContainer().addExpression(keyNode, expression);
        return expression;
    }

    private void addTypeUseExpression(JavaHandlerContext context, TSNode keyNode, String typeName) {
        if (keyNode == null || keyNode.isNull() || typeName == null || typeName.isEmpty()) {
            return;
        }
        Expression expression = addExpression(context, keyNode, null, null,
                false, false, false, false, false, false);
        expression.setRawType(typeName);
        expression.disableDriveTypeFromChild();
    }

    private void addDeclaredTypeUseExpressions(JavaHandlerContext context, TSNode typeNode, String source) {
        if (typeNode == null || typeNode.isNull()) {
            return;
        }
        for (String typeName : extractTypeNames(sourceSlice(typeNode, source))) {
            addTypeUseExpression(context, typeNode, typeName);
        }
    }

    private void addArrayReturnTypeUseExpression(JavaHandlerContext context, TSNode node, String returnType) {
        if (returnType == null || !returnType.contains("[]")) {
            return;
        }
        JavaTypeName typeName = parseJavaTypeName(returnType);
        if (typeName.baseName.isEmpty() || isPrimitiveTypeName(typeName.baseName)) {
            return;
        }
        addTypeUseExpression(context, node, typeName.baseName);
    }

    private JavaTypeName declaredTypeForObjectExpression(TSNode objectNode, String source, JavaHandlerContext context) {
        String objectIdentifier = extractSimpleIdentifier(objectNode, source);
        if (objectIdentifier.isEmpty() || context.lastContainer() == null) {
            return new JavaTypeName("", new ArrayList<>());
        }
        Entity objectEntity = context.lastContainer().lookupVarInVisibleScope(GenericName.build(objectIdentifier));
        if (!(objectEntity instanceof VarEntity)) {
            return new JavaTypeName("", new ArrayList<>());
        }
        GenericName rawType = ((VarEntity) objectEntity).getRawType();
        if (rawType == null || rawType.getName().isEmpty() || isPrimitiveTypeName(rawType.getName())) {
            return new JavaTypeName("", new ArrayList<>());
        }
        return new JavaTypeName(rawType.getName(), rawType.getArguments());
    }

    private boolean isPrimitiveTypeName(String typeName) {
        return "byte".equals(typeName)
                || "short".equals(typeName)
                || "int".equals(typeName)
                || "long".equals(typeName)
                || "float".equals(typeName)
                || "double".equals(typeName)
                || "boolean".equals(typeName)
                || "char".equals(typeName)
                || "void".equals(typeName);
    }

    private boolean shouldEmitCreateTypeUse(TSNode node) {
        TSNode parent = node.getParent();
        if (parent == null || parent.isNull()) {
            return false;
        }
        if (!"assignment_expression".equals(parent.getType())) {
            return false;
        }
        String fieldName = fieldNameForChild(parent, node);
        return fieldName == null || "right".equals(fieldName);
    }

    private boolean shouldUseResolvedExpressionType(TSNode node, String source) {
        return isAssignmentRightSide(node) || isInsideAssertThrowsLambda(node, source);
    }

    private boolean isAssignmentRightSide(TSNode node) {
        TSNode parent = node.getParent();
        if (parent == null || parent.isNull() || !"assignment_expression".equals(parent.getType())) {
            return false;
        }
        return "right".equals(fieldNameForChild(parent, node));
    }

    private boolean isInsideAssertThrowsLambda(TSNode node, String source) {
        TSNode current = node;
        while (current != null && !current.isNull()) {
            if ("lambda_expression".equals(current.getType())) {
                return isAssertThrowsArgument(current, source);
            }
            current = current.getParent();
        }
        return false;
    }

    private boolean isAssertThrowsArgument(TSNode lambdaNode, String source) {
        TSNode parent = lambdaNode.getParent();
        while (parent != null && !parent.isNull() && !"argument_list".equals(parent.getType())) {
            parent = parent.getParent();
        }
        if (parent == null || parent.isNull()) {
            return false;
        }
        TSNode invocationNode = parent.getParent();
        if (invocationNode == null || invocationNode.isNull() || !"method_invocation".equals(invocationNode.getType())) {
            return false;
        }
        TSNode nameNode = invocationNode.getChildByFieldName("name");
        if (nameNode == null || nameNode.isNull()) {
            return false;
        }
        return "assertThrows".equals(sourceSlice(nameNode, source).trim());
    }

    private String extractSimpleIdentifier(TSNode node, String source) {
        if ("identifier".equals(node.getType())) {
            return sourceSlice(node, source).trim();
        }
        TSNode idNode = findFirstDescendantByType(node, "identifier");
        if (idNode != null && !idNode.isNull()) {
            return sourceSlice(idNode, source).trim();
        }
        String raw = sourceSlice(node, source).trim();
        Matcher matcher = TYPE_NAME_PATTERN.matcher(raw);
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    private void addObjectExpression(TSNode objectNode, String source, JavaHandlerContext context, Expression parent) {
        if (objectNode == null || objectNode.isNull()) {
            return;
        }
        String objectType = objectNode.getType();
        if ("parenthesized_expression".equals(objectType)) {
            TSNode child = firstNamedChild(objectNode);
            if (child != null) {
                addObjectExpression(child, source, context, parent);
            }
            return;
        }
        if ("cast_expression".equals(objectType)) {
            TSNode typeNode = objectNode.getChildByFieldName("type");
            if (typeNode == null || typeNode.isNull()) {
                typeNode = findFirstDescendantByType(objectNode, "type_identifier");
            }
            String typeName = parseJavaTypeName(typeNode == null ? "" : sourceSlice(typeNode, source).trim()).baseName;
            if (typeName.isEmpty()) {
                return;
            }
            Expression castExpression = addExpression(context, objectNode, parent, null,
                    false, false, false, true, false, false);
            castExpression.setRawType(typeName);
            castExpression.disableDriveTypeFromChild();
            addTypeUseExpression(context, typeNode, typeName);
            TSNode valueNode = objectNode.getChildByFieldName("value");
            if (valueNode != null && !valueNode.isNull()) {
                walk(valueNode, source, context);
            }
            return;
        }
        if ("array_access".equals(objectType)) {
            Expression arrayElementExpression = addExpression(context, objectNode, parent, null,
                    false, false, false, false, false, false);
            TSNode arrayNode = objectNode.getChildByFieldName("array");
            if (arrayNode != null && !arrayNode.isNull()) {
                addObjectExpression(arrayNode, source, context, arrayElementExpression);
            }
            TSNode indexNode = objectNode.getChildByFieldName("index");
            if (indexNode != null && !indexNode.isNull()) {
                walk(indexNode, source, context);
            }
            return;
        }
        if ("field_access".equals(objectType)) {
            addFieldAccessExpression(objectNode, source, context, parent);
            return;
        }
        if ("object_creation_expression".equals(objectType)) {
            TSNode typeNode = objectNode.getChildByFieldName("type");
            if (typeNode == null || typeNode.isNull()) {
                typeNode = findFirstDescendantByType(objectNode, "type_identifier");
            }
            String typeName = parseJavaTypeName(typeNode == null ? "" : sourceSlice(typeNode, source).trim()).baseName;
            if (typeName.isEmpty()) {
                return;
            }
            Expression createExpression = addExpression(context, objectNode, parent, null,
                    true, false, true, false, false, false);
            createExpression.setRawType(typeName);
            createExpression.disableDriveTypeFromChild();
            if (isInsideAssertThrowsLambda(objectNode, source)) {
                createExpression.enableTypeDependencyUse();
            }
            if (shouldEmitCreateTypeUse(objectNode)) {
                addTypeUseExpression(context, typeNode, typeName);
            }
            processInvocationArguments(objectNode, source, context);
            return;
        }
        if ("method_invocation".equals(objectType)) {
            TSNode nameNode = objectNode.getChildByFieldName("name");
            if (nameNode == null || nameNode.isNull()) {
                nameNode = findFirstDescendantByType(objectNode, "identifier");
            }
            if (nameNode == null || nameNode.isNull()) {
                return;
            }
            String nestedName = sourceSlice(nameNode, source).trim();
            TSNode nestedObject = objectNode.getChildByFieldName("object");
            boolean nestedDot = nestedObject != null && !nestedObject.isNull();
            Expression nestedCall = addExpression(context, objectNode, parent, nestedName,
                    true, nestedDot, false, false, false, false);
            if (nestedDot) {
                addObjectExpression(nestedObject, source, context, nestedCall);
            }
            processInvocationArguments(objectNode, source, context);
            return;
        }
        if ("class_literal".equals(objectType) || sourceSlice(objectNode, source).trim().endsWith(".class")) {
            addClassLiteralObjectExpression(objectNode, source, context, parent);
            return;
        }

        String objectIdentifier = extractSimpleIdentifier(objectNode, source);
        if (objectIdentifier.isEmpty()) {
            return;
        }
        addExpression(context, objectNode, parent, objectIdentifier,
                false, false, false, false, false, false);
    }

    private void addClassLiteralObjectExpression(TSNode node,
                                                 String source,
                                                 JavaHandlerContext context,
                                                 Expression parent) {
        String raw = sourceSlice(node, source).trim();
        if (!raw.endsWith(".class")) {
            return;
        }
        String typeName = parseJavaTypeName(raw.substring(0, raw.length() - ".class".length()).trim()).baseName;
        if (typeName.isEmpty()) {
            return;
        }
        Expression classLiteral = addExpression(context, node, parent, null,
                false, false, false, false, false, false);
        classLiteral.setRawType(typeName);
        classLiteral.disableDriveTypeFromChild();
    }

    private TSNode firstNamedChild(TSNode node) {
        int childCount = node.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getNamedChild(i);
            if (child != null && !child.isNull()) {
                return child;
            }
        }
        return null;
    }

    private void processInvocationArguments(TSNode invocationNode, String source, JavaHandlerContext context) {
        TSNode argumentsNode = invocationNode.getChildByFieldName("arguments");
        if (argumentsNode == null || argumentsNode.isNull()) {
            argumentsNode = findChildByType(invocationNode, "argument_list");
        }
        if (argumentsNode == null || argumentsNode.isNull()) {
            return;
        }
        int childCount = argumentsNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = argumentsNode.getNamedChild(i);
            if (child == null || child.isNull()) {
                continue;
            }
            walk(child, source, context);
            addArrayAccessTypeUseExpression(child, source, context);
        }
    }

    private void addArrayAccessTypeUseExpression(TSNode node, String source, JavaHandlerContext context) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!"array_access".equals(node.getType())) {
            return;
        }
        JavaTypeName typeName = declaredTypeForObjectExpression(node, source, context);
        if (typeName.baseName.isEmpty()) {
            return;
        }
        addTypeUseExpression(context, node, typeName.baseName);
    }

    private boolean shouldEmitAssertionArgumentTypeCall(TSNode invocationNode, int argumentIndex, String source) {
        if (!"method_invocation".equals(invocationNode.getType())) {
            return false;
        }
        TSNode objectNode = invocationNode.getChildByFieldName("object");
        if (objectNode != null && !objectNode.isNull()) {
            return false;
        }
        TSNode nameNode = invocationNode.getChildByFieldName("name");
        if (nameNode == null || nameNode.isNull()) {
            return false;
        }
        String name = sourceSlice(nameNode, source).trim();
        return "assertNotNull".equals(name) || ("assertEquals".equals(name) && argumentIndex == 0);
    }

    private boolean shouldEmitAssertionArgumentTypeCallForArgument(TSNode argumentNode, String source) {
        TSNode argumentsNode = argumentNode.getParent();
        while (argumentsNode != null && !argumentsNode.isNull() && !"argument_list".equals(argumentsNode.getType())) {
            argumentsNode = argumentsNode.getParent();
        }
        if (argumentsNode == null || argumentsNode.isNull()) {
            return false;
        }
        TSNode invocationNode = argumentsNode.getParent();
        if (invocationNode == null || invocationNode.isNull()) {
            return false;
        }
        int argumentIndex = argumentIndex(argumentsNode, argumentNode);
        if (argumentIndex < 0) {
            return false;
        }
        return shouldEmitAssertionArgumentTypeCall(invocationNode, argumentIndex, source);
    }

    private int argumentIndex(TSNode argumentsNode, TSNode argumentNode) {
        int argumentIndex = 0;
        int childCount = argumentsNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = argumentsNode.getNamedChild(i);
            if (sameNode(child, argumentNode)) {
                return argumentIndex;
            }
            argumentIndex++;
        }
        return -1;
    }

    private boolean sameNode(TSNode left, TSNode right) {
        return left != null && right != null
                && !left.isNull() && !right.isNull()
                && left.getStartByte() == right.getStartByte()
                && left.getEndByte() == right.getEndByte()
                && left.getType().equals(right.getType());
    }

    private void addLambdaParameters(TSNode lambdaNode, String source, JavaHandlerContext context) {
        TSNode parametersNode = lambdaNode.getChildByFieldName("parameters");
        if (parametersNode == null || parametersNode.isNull()) {
            parametersNode = findChildByType(lambdaNode, "formal_parameters");
        }
        if (parametersNode == null || parametersNode.isNull()) {
            parametersNode = findChildByType(lambdaNode, "inferred_parameters");
        }
        if (parametersNode == null || parametersNode.isNull()) {
            return;
        }

        List<TSNode> explicitParameters = findChildrenByType(parametersNode, "formal_parameter");
        if (!explicitParameters.isEmpty()) {
            for (TSNode paramNode : explicitParameters) {
                TSNode nameNode = paramNode.getChildByFieldName("name");
                TSNode typeNode = paramNode.getChildByFieldName("type");
                if (nameNode == null || nameNode.isNull()) {
                    continue;
                }
                String paramName = sourceSlice(nameNode, source).trim();
                if (paramName.isEmpty()) {
                    continue;
                }
                JavaTypeName paramType = parseJavaTypeName((typeNode == null || typeNode.isNull())
                        ? ""
                        : sourceSlice(typeNode, source).trim());
                if (paramType.baseName.isEmpty()) {
                    context.foundVarDefinition(paramName, GenericName.build("Object"), new ArrayList<>(),
                            paramNode.getStartPoint().getRow() + 1);
                } else {
                    context.foundVarDefinition(paramName, GenericName.build(paramType.baseName), paramType.typeArguments,
                            paramNode.getStartPoint().getRow() + 1);
                }
            }
            return;
        }

        if ("identifier".equals(parametersNode.getType())) {
            addInferredLambdaParameter(parametersNode, source, context);
            return;
        }

        List<TSNode> inferredParameters = findChildrenByType(parametersNode, "identifier");
        for (TSNode inferred : inferredParameters) {
            addInferredLambdaParameter(inferred, source, context);
        }
    }

    private void addInferredLambdaParameter(TSNode paramNode, String source, JavaHandlerContext context) {
        String paramName = sourceSlice(paramNode, source).trim();
        if (paramName.isEmpty()) {
            return;
        }
        context.foundVarDefinition(paramName, GenericName.build("Object"), new ArrayList<>(),
                paramNode.getStartPoint().getRow() + 1);
    }

    private void applyAnnotations(TSNode node, String source, Entity entity) {
        if (!(entity instanceof DecoratedEntity)) {
            return;
        }
        List<GenericName> annotations = collectAnnotations(node, source);
        for (GenericName annotation : annotations) {
            ((DecoratedEntity) entity).addAnnotation(annotation);
        }
    }

    private void applyAnnotations(TSNode node, String source, List<VarEntity> vars) {
        if (vars == null || vars.isEmpty()) {
            return;
        }
        List<GenericName> annotations = collectAnnotations(node, source);
        if (annotations.isEmpty()) {
            return;
        }
        for (VarEntity var : vars) {
            for (GenericName annotation : annotations) {
                var.addAnnotation(annotation);
            }
        }
    }

    private List<GenericName> collectAnnotations(TSNode node, String source) {
        List<GenericName> annotations = new ArrayList<>();
        TSNode modifiers = findChildByType(node, "modifiers");
        if (modifiers == null || modifiers.isNull()) {
            return annotations;
        }
        collectAnnotationFromDescendants(modifiers, source, annotations);
        return annotations;
    }

    private void collectAnnotationFromDescendants(TSNode node, String source, List<GenericName> annotations) {
        if (node == null || node.isNull()) {
            return;
        }
        String nodeType = node.getType();
        if ("marker_annotation".equals(nodeType) || "annotation".equals(nodeType)) {
            GenericName annotation = extractAnnotationName(node, source);
            if (annotation != null) {
                annotations.add(annotation);
            }
            return;
        }
        int childCount = node.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            collectAnnotationFromDescendants(node.getNamedChild(i), source, annotations);
        }
    }

    private GenericName extractAnnotationName(TSNode annotationNode, String source) {
        TSNode nameNode = annotationNode.getChildByFieldName("name");
        String raw;
        if (nameNode != null && !nameNode.isNull()) {
            raw = sourceSlice(nameNode, source).trim();
        } else {
            raw = sourceSlice(annotationNode, source).trim();
            if (raw.startsWith("@")) {
                raw = raw.substring(1);
            }
            int leftParen = raw.indexOf('(');
            if (leftParen >= 0) {
                raw = raw.substring(0, leftParen).trim();
            }
        }
        if (raw.isEmpty()) {
            return null;
        }
        return GenericName.build(raw);
    }
}
