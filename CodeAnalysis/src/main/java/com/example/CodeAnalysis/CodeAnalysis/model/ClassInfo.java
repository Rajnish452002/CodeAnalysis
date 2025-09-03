package com.example.CodeAnalysis.CodeAnalysis.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassInfo {
    private String className;
    private String packageName;
    private String filePath;
    private String classType; // Repository, Entity, Service, Controller
    private List<String> methods = new ArrayList<>();
    private List<String> fields = new ArrayList<>();
    private List<String> annotations = new ArrayList<>();
    private String impactReason; // Why this class is impacted
    private List<String> apiEndpoints = new ArrayList<>(); // For controllers
    private int usageCount = 0; // Number of times column is used in this class

    public ClassInfo(String className, String packageName, String filePath, String classType) {
        this.className = className;
        this.packageName = packageName;
        this.filePath = filePath;
        this.classType = classType;
    }

    public void addMethod(String method) {
        if (methods == null) methods = new ArrayList<>();
        this.methods.add(method);
    }

    public void addField(String field) {
        if (fields == null) fields = new ArrayList<>();
        this.fields.add(field);
    }

    public void addAnnotation(String annotation) {
        if (annotations == null) annotations = new ArrayList<>();
        this.annotations.add(annotation);
    }

    public void addApiEndpoint(String endpoint) {
        if (apiEndpoints == null) apiEndpoints = new ArrayList<>();
        this.apiEndpoints.add(endpoint);
    }

    public void incrementUsageCount() {
        this.usageCount++;
    }

    public String getFullClassName() {
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    public String getShortFilePath() {
        return filePath.length() > 50 ? "..." + filePath.substring(filePath.length() - 47) : filePath;
    }
}
