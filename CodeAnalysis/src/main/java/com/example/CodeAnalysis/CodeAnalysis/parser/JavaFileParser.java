package com.example.CodeAnalysis.CodeAnalysis.parser;

import com.example.CodeAnalysis.CodeAnalysis.model.ClassInfo;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Component
public class JavaFileParser {
    private final JavaParser javaParser;

    public JavaFileParser() {
        this.javaParser = new JavaParser();
    }

    public List<ClassInfo> parseJavaFiles(String directoryPath) {
        List<ClassInfo> classInfoList = new ArrayList<>();
        Path startPath = Paths.get(directoryPath);

        if (!Files.exists(startPath) || !Files.isDirectory(startPath)) {
            log.error("Invalid directory path: {}", directoryPath);
            return classInfoList;
        }

        try (Stream<Path> paths = Files.walk(startPath)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            ClassInfo classInfo = parseJavaFile(path.toFile());
                            if (classInfo != null) {
                                classInfoList.add(classInfo);
                            }
                        } catch (Exception e) {
                            log.error("Error parsing file: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Error walking directory: {}", directoryPath, e);
        }

        log.info("Parsed {} Java files", classInfoList.size());
        return classInfoList;
    }

    public ClassInfo parseJavaFile(File file) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            Optional<CompilationUnit> compilationUnit = javaParser.parse(fileInputStream).getResult();

            if (!compilationUnit.isPresent()) {
                log.warn("Could not parse file: {}", file.getPath());
                return null;
            }

            CompilationUnit cu = compilationUnit.get();

            // Get package name
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            // Find the main class
            Optional<ClassOrInterfaceDeclaration> mainClass = cu.findFirst(ClassOrInterfaceDeclaration.class);

            if (!mainClass.isPresent()) {
                return null;
            }

            ClassOrInterfaceDeclaration classDecl = mainClass.get();
            String className = classDecl.getNameAsString();
            String classType = determineClassType(classDecl, packageName, file.getName());

            ClassInfo classInfo = new ClassInfo(className, packageName, file.getPath(), classType);

            // Extract annotations
            for (AnnotationExpr annotation : classDecl.getAnnotations()) {
                classInfo.addAnnotation(annotation.getNameAsString());
            }

            // Extract fields
            classDecl.getFields().forEach(field -> {
                field.getVariables().forEach(variable -> {
                    classInfo.addField(variable.getNameAsString());
                });
            });

            // Extract methods and API endpoints
            classDecl.getMethods().forEach(method -> {
                String methodName = method.getNameAsString();
                classInfo.addMethod(methodName);

                // Extract API endpoints for controllers
                if ("Controller".equals(classType)) {
                    extractApiEndpoints(method, classInfo);
                }
            });

            return classInfo;
        }
    }

    private void extractApiEndpoints(MethodDeclaration method, ClassInfo classInfo) {
        method.getAnnotations().forEach(annotation -> {
            String annotationName = annotation.getNameAsString();
            String endpoint = "";

            switch (annotationName) {
                case "GetMapping":
                    endpoint = extractEndpointPath(annotation, "GET", method.getNameAsString());
                    break;
                case "PostMapping":
                    endpoint = extractEndpointPath(annotation, "POST", method.getNameAsString());
                    break;
                case "PutMapping":
                    endpoint = extractEndpointPath(annotation, "PUT", method.getNameAsString());
                    break;
                case "DeleteMapping":
                    endpoint = extractEndpointPath(annotation, "DELETE", method.getNameAsString());
                    break;
                case "PatchMapping":
                    endpoint = extractEndpointPath(annotation, "PATCH", method.getNameAsString());
                    break;
                case "RequestMapping":
                    endpoint = extractRequestMappingPath(annotation, method.getNameAsString());
                    break;
            }

            if (!endpoint.isEmpty()) {
                classInfo.addApiEndpoint(endpoint);
            }
        });
    }

    private String extractEndpointPath(AnnotationExpr annotation, String httpMethod, String methodName) {
        String path = "";

        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                if ((pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path"))
                        && pair.getValue() instanceof StringLiteralExpr) {
                    path = ((StringLiteralExpr) pair.getValue()).getValue();
                    break;
                }
            }
        }

        if (path.isEmpty()) {
            path = "/" + methodName.toLowerCase();
        }

        return httpMethod + " " + path;
    }

    private String extractRequestMappingPath(AnnotationExpr annotation, String methodName) {
        String path = "";
        String method = "ALL";

        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                if ((pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path"))
                        && pair.getValue() instanceof StringLiteralExpr) {
                    path = ((StringLiteralExpr) pair.getValue()).getValue();
                } else if (pair.getNameAsString().equals("method")) {
                    method = pair.getValue().toString().replace("RequestMethod.", "");
                }
            }
        }

        if (path.isEmpty()) {
            path = "/" + methodName.toLowerCase();
        }

        return method + " " + path;
    }

    private String determineClassType(ClassOrInterfaceDeclaration classDecl, String packageName, String fileName) {
        // Check annotations first
        for (AnnotationExpr annotation : classDecl.getAnnotations()) {
            String annotationName = annotation.getNameAsString();
            switch (annotationName) {
                case "Repository":
                    return "Repository";
                case "Entity":
                case "Table":
                    return "Entity";
                case "Service":
                    return "Service";
                case "Controller":
                case "RestController":
                    return "Controller";
                case "Component":
                    return "Component";
                case "Configuration":
                    return "Configuration";
            }
        }

        // Check package patterns
        String lowerPackage = packageName.toLowerCase();
        if (lowerPackage.contains("repository")) return "Repository";
        if (lowerPackage.contains("entity") || lowerPackage.contains("model")) return "Entity";
        if (lowerPackage.contains("service")) return "Service";
        if (lowerPackage.contains("controller") || lowerPackage.contains("web")) return "Controller";
        if (lowerPackage.contains("config")) return "Configuration";

        // Check filename patterns
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.contains("repository")) return "Repository";
        if (lowerFileName.contains("entity") || lowerFileName.contains("model")) return "Entity";
        if (lowerFileName.contains("service")) return "Service";
        if (lowerFileName.contains("controller")) return "Controller";

        // Check interface extensions for repositories
        if (classDecl.isInterface()) {
            for (var extendedType : classDecl.getExtendedTypes()) {
                String extendedTypeName = extendedType.getNameAsString();
                if (extendedTypeName.contains("Repository") ||
                        extendedTypeName.equals("JpaRepository") ||
                        extendedTypeName.equals("CrudRepository")) {
                    return "Repository";
                }
            }
        }

        return "Unknown";
    }
}
