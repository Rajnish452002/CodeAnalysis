package com.example.CodeAnalysis.CodeAnalysis;

import com.example.CodeAnalysis.CodeAnalysis.service.CodeImpactAnalyzer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CodeAnalysisApplication {

    @Autowired
    private CodeImpactAnalyzer codeImpactAnalyzer;

	public static void main(String[] args) {
		SpringApplication.run(CodeAnalysisApplication.class, args);
	}


    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            if (args.length >= 2) {
                // Command line mode
                String projectPath = args[0];
                String columnName = args[1];
                String outputFile = args.length > 2 ? args[2] : "impact-analysis.xlsx";

                System.out.println("Running Code Impact Analysis...");
                System.out.println("Project Path: " + projectPath);
                System.out.println("Column Name: " + columnName);
                System.out.println("Output File: " + outputFile);

                codeImpactAnalyzer.analyzeAndGenerateReport(projectPath, columnName, outputFile);
            } else {
                // Web mode
                System.out.println("========================================");
                System.out.println("Code Impact Analyzer for Spring Boot");
                System.out.println("========================================");
                System.out.println("Web interface available at: http://localhost:8080");
                System.out.println("");
                System.out.println("Command line usage:");
                System.out.println("java -jar app.jar <project-path> <column-name> [output-file]");
                System.out.println("");
                System.out.println("Example:");
                System.out.println("java -jar app.jar /path/to/project user_email report.xlsx");
            }
        };
    }
}


