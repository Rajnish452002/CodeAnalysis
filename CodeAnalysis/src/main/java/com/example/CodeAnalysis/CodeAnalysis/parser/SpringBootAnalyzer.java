package com.example.CodeAnalysis.CodeAnalysis.parser;

import com.example.CodeAnalysis.CodeAnalysis.model.ColumnUsage;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SpringBootAnalyzer {
    private final JavaParser javaParser;
    private final Pattern camelCasePattern = Pattern.compile("([a-z])([A-Z])");

    public SpringBootAnalyzer() {
        this.javaParser = new JavaParser();
    }

    public List<ColumnUsage> findColumnUsages(String filePath, String columnName) {
        List<ColumnUsage> usages = new ArrayList<>();

        try (FileInputStream fileInputStream = new FileInputStream(new File(filePath))) {
            Optional<CompilationUnit> compilationUnit = javaParser.parse(fileInputStream).getResult();

            if (!compilationUnit.isPresent()) {
                return usages;
            }

            CompilationUnit cu = compilationUnit.get();
            String className = extractClassName(cu);

            // Find column usages in different contexts
            findColumnInFields(cu, className, columnName, usages, filePath);
            findColumnInQueries(cu, className, columnName, usages, filePath);
            findColumnInMethods(cu, className, columnName, usages, filePath);
            findColumnInStrings(cu, className, columnName, usages, filePath);

        } catch (Exception e) {
            log.debug("Error analyzing file: {} - {}", filePath, e.getMessage());
        }

        return usages;
    }

    private void findColumnInFields(CompilationUnit cu, String className, String columnName,
                                    List<ColumnUsage> usages, String filePath) {
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            int lineNumber = field.getRange().map(r -> r.begin.line).orElse(0);

            // Check @Column annotations
            field.getAnnotations().forEach(annotation -> {
                if (annotation.getNameAsString().equals("Column")) {
                    checkColumnAnnotation(annotation, className, columnName, usages, lineNumber, filePath);
                }
            });

            // Check field names that match column name
            field.getVariables().forEach(variable -> {
                String fieldName = variable.getNameAsString();
                if (isColumnMatch(fieldName, columnName)) {
                    usages.add(new ColumnUsage(className, fieldName, "FIELD",
                            "Field declaration: " + fieldName, lineNumber, filePath));
                }
            });
        });
    }

    private void findColumnInQueries(CompilationUnit cu, String className, String columnName,
                                     List<ColumnUsage> usages, String filePath) {
        cu.findAll(AnnotationExpr.class).forEach(annotation -> {
            String annotationName = annotation.getNameAsString();
            if (annotationName.equals("Query") || annotationName.equals("NamedQuery") ||
                    annotationName.equals("Modifying")) {

                String queryString = extractQueryString(annotation);
                if (queryString != null && containsColumn(queryString, columnName)) {
                    int lineNumber = annotation.getRange().map(r -> r.begin.line).orElse(0);
                    usages.add(new ColumnUsage(className, "query", "QUERY",
                            "SQL Query contains column", lineNumber, filePath));
                }
            }
        });
    }

    private void findColumnInMethods(CompilationUnit cu, String className, String columnName,
                                     List<ColumnUsage> usages, String filePath) {
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            String methodName = method.getNameAsString();
            String methodContent = method.toString();

            if (containsColumn(methodContent, columnName)) {
                int lineNumber = method.getRange().map(r -> r.begin.line).orElse(0);
                usages.add(new ColumnUsage(className, methodName, "METHOD",
                        "Method contains column reference", lineNumber, filePath));
            }

            // Check method parameters
            method.getParameters().forEach(param -> {
                String paramName = param.getNameAsString();
                if (isColumnMatch(paramName, columnName)) {
                    int lineNumber = method.getRange().map(r -> r.begin.line).orElse(0);
                    usages.add(new ColumnUsage(className, methodName, "PARAMETER",
                            "Method parameter: " + paramName, lineNumber, filePath));
                }
            });

            // Check method names (like findByUserEmail)
            if (containsColumnInMethodName(methodName, columnName)) {
                int lineNumber = method.getRange().map(r -> r.begin.line).orElse(0);
                usages.add(new ColumnUsage(className, methodName, "METHOD_NAME",
                        "Method name references column", lineNumber, filePath));
            }
        });
    }

    private void findColumnInStrings(CompilationUnit cu, String className, String columnName,
                                     List<ColumnUsage> usages, String filePath) {
        try {
            String content = Files.readString(Paths.get(filePath));
            String[] lines = content.split("\n");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.contains("\"") && containsColumn(line, columnName)) {
                    // Avoid duplicating query annotations already found
                    if (!line.contains("@Query") && !line.contains("@NamedQuery")) {
                        usages.add(new ColumnUsage(className, "string-literal", "STRING",
                                "String contains column reference", i + 1, filePath));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not read file content for string analysis: {}", filePath);
        }
    }

    private boolean containsColumnInMethodName(String methodName, String columnName) {
        String camelCase = toCamelCase(columnName);
        String pascalCase = toPascalCase(columnName);

        return methodName.contains(camelCase) || methodName.contains(pascalCase);
    }

    private void checkColumnAnnotation(AnnotationExpr annotation, String className, String columnName,
                                       List<ColumnUsage> usages, int lineNumber, String filePath) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                if (pair.getNameAsString().equals("name") && pair.getValue() instanceof StringLiteralExpr) {
                    String annotationColumnName = ((StringLiteralExpr) pair.getValue()).getValue();
                    if (isColumnMatch(annotationColumnName, columnName)) {
                        usages.add(new ColumnUsage(className, "annotation", "COLUMN_ANNOTATION",
                                "@Column(name=\"" + annotationColumnName + "\")", lineNumber, filePath));
                    }
                }
            }
        }
    }

    private boolean containsColumn(String text, String columnName) {
        if (text == null || columnName == null) return false;

        String lowerText = text.toLowerCase();
        String lowerColumn = columnName.toLowerCase();

        // Check for exact match
        if (lowerText.contains(lowerColumn)) return true;

        // Check for camelCase version
        String camelCase = toCamelCase(columnName);
        if (lowerText.contains(camelCase.toLowerCase())) return true;

        // Check for PascalCase version
        String pascalCase = toPascalCase(columnName);
        if (lowerText.contains(pascalCase.toLowerCase())) return true;

        return false;
    }

    private boolean isColumnMatch(String fieldName, String columnName) {
        if (fieldName == null || columnName == null) return false;

        // Direct match
        if (fieldName.equalsIgnoreCase(columnName)) return true;

        // CamelCase match
        String camelCase = toCamelCase(columnName);
        if (fieldName.equalsIgnoreCase(camelCase)) return true;

        // PascalCase match
        String pascalCase = toPascalCase(columnName);
        if (fieldName.equalsIgnoreCase(pascalCase)) return true;

        // Snake case match
        String snakeCase = toSnakeCase(fieldName);
        if (snakeCase.equalsIgnoreCase(columnName)) return true;

        return false;
    }

    private String toCamelCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) return "";

        StringBuilder result = new StringBuilder();
        boolean nextIsUpper = false;

        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                nextIsUpper = true;
            } else if (nextIsUpper) {
                result.append(Character.toUpperCase(c));
                nextIsUpper = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }

    private String toPascalCase(String snakeCase) {
        String camelCase = toCamelCase(snakeCase);
        if (camelCase.isEmpty()) return "";
        return Character.toUpperCase(camelCase.charAt(0)) + camelCase.substring(1);
    }

    private String toSnakeCase(String camelCase) {
        if (camelCase == null) return "";
        return camelCasePattern.matcher(camelCase).replaceAll("$1_$2").toLowerCase();
    }

    private String extractQueryString(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                if ((pair.getNameAsString().equals("value") || pair.getNameAsString().equals("nativeQuery"))
                        && pair.getValue() instanceof StringLiteralExpr) {
                    return ((StringLiteralExpr) pair.getValue()).getValue();
                }
            }
        }
        return null;
    }

    private String extractClassName(CompilationUnit cu) {
        return cu.findFirst(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                .map(c -> c.getNameAsString())
                .orElse("Unknown");
    }
}
