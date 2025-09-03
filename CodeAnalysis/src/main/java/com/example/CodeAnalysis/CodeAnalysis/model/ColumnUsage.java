package com.example.CodeAnalysis.CodeAnalysis.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ColumnUsage {
    private String className;
    private String methodName;
    private String usageType; // FIELD, QUERY, PARAMETER, METHOD, STRING, COLUMN_ANNOTATION
    private String context; // Where exactly it's used
    private int lineNumber;
    private String filePath;

    public ColumnUsage(String className, String methodName, String usageType, String context, int lineNumber) {
        this.className = className;
        this.methodName = methodName;
        this.usageType = usageType;
        this.context = context;
        this.lineNumber = lineNumber;
    }

    public String getUsageDescription() {
        return String.format("%s:%d - %s in %s",
                className, lineNumber, usageType, methodName);
    }
}
