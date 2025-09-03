package com.example.CodeAnalysis.CodeAnalysis.tracker;

import com.example.CodeAnalysis.CodeAnalysis.model.ClassInfo;
import com.example.CodeAnalysis.CodeAnalysis.model.ColumnUsage;
import com.example.CodeAnalysis.CodeAnalysis.model.ImpactResult;
import com.example.CodeAnalysis.CodeAnalysis.parser.JavaFileParser;
import com.example.CodeAnalysis.CodeAnalysis.parser.SpringBootAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ImpactTracker {

    @Autowired
    private JavaFileParser javaFileParser;

    @Autowired
    private SpringBootAnalyzer springBootAnalyzer;

    public ImpactResult analyzeColumnImpact(String projectPath, String columnName) {
        log.info("Starting impact analysis for column: {} in project: {}", columnName, projectPath);
        long startTime = System.currentTimeMillis();

        ImpactResult result = new ImpactResult(columnName);
        result.setProjectPath(projectPath);

        try {
            // Parse all Java files in the project
            List<ClassInfo> allClasses = javaFileParser.parseJavaFiles(projectPath);
            log.info("Found {} classes to analyze", allClasses.size());

            if (allClasses.isEmpty()) {
                log.warn("No Java classes found in path: {}", projectPath);
                return result;
            }

            // Categorize classes by type
            Map<String, List<ClassInfo>> classesByType = categorizeClasses(allClasses);
            logClassCounts(classesByType);

            // Analyze each class for column usage
            analyzeDirectImpacts(allClasses, columnName, result);

            // Find indirect impacts (services using impacted repos, controllers using impacted services)
            findIndirectImpacts(result, classesByType);

            long endTime = System.currentTimeMillis();
            result.setAnalysisTimeMs(endTime - startTime);

            log.info("Impact analysis completed in {}ms. Found {} repositories, {} entities, {} services, {} controllers",
                    result.getAnalysisTimeMs(),
                    result.getRepositories().size(),
                    result.getEntities().size(),
                    result.getServices().size(),
                    result.getControllers().size());

        } catch (Exception e) {
            log.error("Error during impact analysis", e);
            throw new RuntimeException("Impact analysis failed", e);
        }

        return result;
    }

    private void analyzeDirectImpacts(List<ClassInfo> allClasses, String columnName, ImpactResult result) {
        for (ClassInfo classInfo : allClasses) {
            try {
                List<ColumnUsage> usages = springBootAnalyzer.findColumnUsages(classInfo.getFilePath(), columnName);

                if (!usages.isEmpty()) {
                    // Set impact reason and usage count
                    classInfo.setImpactReason("Direct usage: " + usages.size() + " occurrence(s)");
                    classInfo.setUsageCount(usages.size());

                    // Categorize the impacted class
                    switch (classInfo.getClassType()) {
                        case "Repository":
                            result.addRepository(classInfo);
                            break;
                        case "Entity":
                            result.addEntity(classInfo);
                            break;
                        case "Service":
                            result.addService(classInfo);
                            break;
                        case "Controller":
                            result.addController(classInfo);
                            break;
                        default:
                            // For unknown types, still track the usages
                            log.debug("Unknown class type: {} for class: {}", classInfo.getClassType(), classInfo.getClassName());
                            break;
                    }

                    // Add all column usages to result
                    usages.forEach(result::addColumnUsage);
                }
            } catch (Exception e) {
                log.warn("Error analyzing class {}: {}", classInfo.getClassName(), e.getMessage());
            }
        }
    }

    private void findIndirectImpacts(ImpactResult result, Map<String, List<ClassInfo>> classesByType) {
        // Find services that use impacted repositories
        List<String> impactedRepositoryNames = result.getRepositories().stream()
                .map(ClassInfo::getClassName)
                .collect(Collectors.toList());

        if (!impactedRepositoryNames.isEmpty()) {
            for (ClassInfo service : classesByType.get("Service")) {
                if (!isAlreadyImpacted(service, result.getServices()) &&
                        usesAnyRepository(service, impactedRepositoryNames)) {
                    service.setImpactReason("Indirect: Uses impacted repository");
                    result.addService(service);
                }
            }
        }

        // Find controllers that use impacted services
        List<String> impactedServiceNames = result.getServices().stream()
                .map(ClassInfo::getClassName)
                .collect(Collectors.toList());

        if (!impactedServiceNames.isEmpty()) {
            for (ClassInfo controller : classesByType.get("Controller")) {
                if (!isAlreadyImpacted(controller, result.getControllers()) &&
                        usesAnyService(controller, impactedServiceNames)) {
                    controller.setImpactReason("Indirect: Uses impacted service");
                    result.addController(controller);
                }
            }
        }
    }

    private boolean isAlreadyImpacted(ClassInfo classInfo, List<ClassInfo> impactedClasses) {
        return impactedClasses.stream()
                .anyMatch(impacted -> impacted.getClassName().equals(classInfo.getClassName()));
    }

    private boolean usesAnyRepository(ClassInfo service, List<String> repositoryNames) {
        try {
            String fileContent = Files.readString(Paths.get(service.getFilePath()));

            return repositoryNames.stream().anyMatch(repoName ->
                    fileContent.contains(repoName) ||
                            service.getFields().stream().anyMatch(field ->
                                    field.toLowerCase().contains(repoName.toLowerCase().replace("repository", ""))));
        } catch (Exception e) {
            log.debug("Could not check repository usage for service: {}", service.getClassName());
            return false;
        }
    }

    private boolean usesAnyService(ClassInfo controller, List<String> serviceNames) {
        try {
            String fileContent = Files.readString(Paths.get(controller.getFilePath()));

            return serviceNames.stream().anyMatch(serviceName ->
                    fileContent.contains(serviceName) ||
                            controller.getFields().stream().anyMatch(field ->
                                    field.toLowerCase().contains(serviceName.toLowerCase().replace("service", ""))));
        } catch (Exception e) {
            log.debug("Could not check service usage for controller: {}", controller.getClassName());
            return false;
        }
    }

    private Map<String, List<ClassInfo>> categorizeClasses(List<ClassInfo> allClasses) {
        Map<String, List<ClassInfo>> categorized = new HashMap<>();

        categorized.put("Repository", allClasses.stream()
                .filter(c -> "Repository".equals(c.getClassType()))
                .collect(Collectors.toList()));

        categorized.put("Entity", allClasses.stream()
                .filter(c -> "Entity".equals(c.getClassType()))
                .collect(Collectors.toList()));

        categorized.put("Service", allClasses.stream()
                .filter(c -> "Service".equals(c.getClassType()))
                .collect(Collectors.toList()));

        categorized.put("Controller", allClasses.stream()
                .filter(c -> "Controller".equals(c.getClassType()))
                .collect(Collectors.toList()));

        return categorized;
    }

    private void logClassCounts(Map<String, List<ClassInfo>> classesByType) {
        log.info("Class breakdown - Repositories: {}, Entities: {}, Services: {}, Controllers: {}",
                classesByType.get("Repository").size(),
                classesByType.get("Entity").size(),
                classesByType.get("Service").size(),
                classesByType.get("Controller").size());
    }
}
