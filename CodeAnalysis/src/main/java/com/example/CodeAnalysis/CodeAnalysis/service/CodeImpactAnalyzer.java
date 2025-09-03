package com.example.CodeAnalysis.CodeAnalysis.service;

import com.example.CodeAnalysis.CodeAnalysis.model.ImpactResult;
import com.example.CodeAnalysis.CodeAnalysis.report.ExcelReportGenerator;
import com.example.CodeAnalysis.CodeAnalysis.tracker.ImpactTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class CodeImpactAnalyzer {

    @Autowired
    private ImpactTracker impactTracker;

    @Autowired
    private ExcelReportGenerator reportGenerator;

    public ImpactResult analyzeAndGenerateReport(String projectPath, String columnName, String outputFile) {
        try {
            log.info("Starting code impact analysis...");
            log.info("Project Path: {}", projectPath);
            log.info("Column Name: {}", columnName);
            log.info("Output File: {}", outputFile);

            // Validate inputs
            validateInputs(projectPath, columnName, outputFile);

            // Perform impact analysis
            ImpactResult result = impactTracker.analyzeColumnImpact(projectPath, columnName);

            // Generate Excel report
            reportGenerator.generateReport(result, outputFile);

            // Display summary
            displaySummary(result, outputFile);

            return result;

        } catch (Exception e) {
            log.error("Error during code impact analysis", e);
            throw new RuntimeException("Code impact analysis failed", e);
        }
    }

    public ImpactResult analyzeOnly(String projectPath, String columnName) {
        log.info("Performing analysis only (no report generation)");
        validateProjectPath(projectPath);
        validateColumnName(columnName);

        return impactTracker.analyzeColumnImpact(projectPath, columnName);
    }

    private void validateInputs(String projectPath, String columnName, String outputFile) {
        validateProjectPath(projectPath);
        validateColumnName(columnName);
        validateOutputFile(outputFile);
    }

    private void validateProjectPath(String projectPath) {
        if (projectPath == null || projectPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Project path cannot be empty");
        }

        File projectDir = new File(projectPath);
        if (!projectDir.exists()) {
            throw new IllegalArgumentException("Project path does not exist: " + projectPath);
        }

        if (!projectDir.isDirectory()) {
            throw new IllegalArgumentException("Project path is not a directory: " + projectPath);
        }
    }

    private void validateColumnName(String columnName) {
        if (columnName == null || columnName.trim().isEmpty()) {
            throw new IllegalArgumentException("Column name cannot be empty");
        }
    }

    private void validateOutputFile(String outputFile) {
        if (outputFile == null || outputFile.trim().isEmpty()) {
            throw new IllegalArgumentException("Output file path cannot be empty");
        }

        if (!outputFile.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("Output file must have .xlsx extension");
        }

        // Check if parent directory exists
        File file = new File(outputFile);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            throw new IllegalArgumentException("Output directory does not exist: " + parentDir.getPath());
        }
    }

    private void displaySummary(ImpactResult result, String outputFile) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🎯 CODE IMPACT ANALYSIS COMPLETE");
        System.out.println("=".repeat(60));
        System.out.println("📊 Analysis Summary:");
        System.out.println("   Column analyzed: " + result.getColumnName());
        System.out.println("   Analysis time: " + result.getAnalysisTimeMs() + " ms");
        System.out.println("   Project path: " + result.getProjectPath());
        System.out.println();

        System.out.println("🏛️ Impact Results:");
        System.out.println("   Repositories: " + result.getRepositories().size());
        System.out.println("   Entities: " + result.getEntities().size());
        System.out.println("   Services: " + result.getServices().size());
        System.out.println("   Controllers: " + result.getControllers().size());
        System.out.println("   Total usages: " + result.getColumnUsages().size());
        System.out.println();

        if (!result.getRepositories().isEmpty()) {
            System.out.println("📂 Impacted Repositories:");
            result.getRepositories().forEach(repo ->
                    System.out.println("   • " + repo.getClassName() + " (" + repo.getUsageCount() + " usages)"));
            System.out.println();
        }

        if (!result.getServices().isEmpty()) {
            System.out.println("⚙️ Impacted Services:");
            result.getServices().forEach(service ->
                    System.out.println("   • " + service.getClassName() + " - " + service.getImpactReason()));
            System.out.println();
        }

        if (!result.getControllers().isEmpty()) {
            System.out.println("🌐 Impacted Controllers (APIs):");
            result.getControllers().forEach(controller ->
                    System.out.println("   • " + controller.getClassName() + " - " + controller.getImpactReason()));
            System.out.println();
        }

        System.out.println("📄 Excel Report Generated: " + outputFile);
        System.out.println("=".repeat(60));
    }

    public String generateDefaultOutputFileName(String columnName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String safeColumnName = columnName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return "impact-analysis-" + safeColumnName + "-" + timestamp + ".xlsx";
    }
}
