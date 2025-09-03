package com.example.CodeAnalysis.CodeAnalysis.controller;

import com.example.CodeAnalysis.CodeAnalysis.model.ImpactResult;
import com.example.CodeAnalysis.CodeAnalysis.service.CodeImpactAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.File;

@Slf4j
@Controller
@RequestMapping("/")
public class AnalysisController {

    @Autowired
    private CodeImpactAnalyzer codeImpactAnalyzer;

    @GetMapping
    public String home() {
        return "index";
    }

    @PostMapping("/analyze")
    @ResponseBody
    public ResponseEntity<?> analyzeCode(@RequestParam String projectPath,
                                         @RequestParam String columnName,
                                         @RequestParam(required = false) String outputFile) {
        try {
            // Generate default output file name if not provided
            if (outputFile == null || outputFile.trim().isEmpty()) {
                outputFile = codeImpactAnalyzer.generateDefaultOutputFileName(columnName);
            }

            // Ensure output file is in a temporary directory
            String tempDir = System.getProperty("java.io.tmpdir");
            if (!outputFile.contains(File.separator)) {
                outputFile = tempDir + File.separator + outputFile;
            }

            // Perform analysis
            ImpactResult result = codeImpactAnalyzer.analyzeAndGenerateReport(projectPath, columnName, outputFile);

            return ResponseEntity.ok().body(result);

        } catch (Exception e) {
            log.error("Analysis failed", e);
            return ResponseEntity.badRequest().body("Analysis failed: " + e.getMessage());
        }
    }

    @PostMapping("/analyze-only")
    @ResponseBody
    public ResponseEntity<?> analyzeOnly(@RequestParam String projectPath,
                                         @RequestParam String columnName) {
        try {
            ImpactResult result = codeImpactAnalyzer.analyzeOnly(projectPath, columnName);
            return ResponseEntity.ok().body(result);

        } catch (Exception e) {
            log.error("Analysis failed", e);
            return ResponseEntity.badRequest().body("Analysis failed: " + e.getMessage());
        }
    }

    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadReport(@PathVariable String fileName) {
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            File file = new File(tempDir + File.separator + fileName);

            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (Exception e) {
            log.error("Download failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
