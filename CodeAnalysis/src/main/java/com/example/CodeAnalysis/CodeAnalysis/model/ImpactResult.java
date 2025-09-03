package com.example.CodeAnalysis.CodeAnalysis.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ImpactResult {
    private String columnName;
    private String projectPath;
    private List<ClassInfo> repositories = new ArrayList<>();
    private List<ClassInfo> entities = new ArrayList<>();
    private List<ClassInfo> services = new ArrayList<>();
    private List<ClassInfo> controllers = new ArrayList<>();
    private List<ColumnUsage> columnUsages = new ArrayList<>();
    private long analysisTimeMs;
    private LocalDateTime analysisDate;

    public ImpactResult(String columnName) {
        this.columnName = columnName;
        this.analysisDate = LocalDateTime.now();
    }

    // Helper methods
    public void addRepository(ClassInfo classInfo) {
        this.repositories.add(classInfo);
    }

    public void addEntity(ClassInfo classInfo) {
        this.entities.add(classInfo);
    }

    public void addService(ClassInfo classInfo) {
        this.services.add(classInfo);
    }

    public void addController(ClassInfo classInfo) {
        this.controllers.add(classInfo);
    }

    public void addColumnUsage(ColumnUsage usage) {
        this.columnUsages.add(usage);
    }

    public int getTotalImpactedClasses() {
        return repositories.size() + entities.size() + services.size() + controllers.size();
    }

    public int getTotalUsages() {
        return columnUsages.size();
    }
}
