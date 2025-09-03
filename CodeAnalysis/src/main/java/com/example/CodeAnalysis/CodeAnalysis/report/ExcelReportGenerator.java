package com.example.CodeAnalysis.CodeAnalysis.report;

import com.example.CodeAnalysis.CodeAnalysis.model.ClassInfo;
import com.example.CodeAnalysis.CodeAnalysis.model.ColumnUsage;
import com.example.CodeAnalysis.CodeAnalysis.model.ImpactResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class ExcelReportGenerator {

    public void generateReport(ImpactResult impactResult, String outputPath) {
        log.info("Generating Excel report: {}", outputPath);

        try (Workbook workbook = new XSSFWorkbook()) {
            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);

            // Create worksheets
            createSummarySheet(workbook, impactResult, headerStyle, dataStyle, titleStyle);
            createRepositoriesSheet(workbook, impactResult.getRepositories(), headerStyle, dataStyle);
            createEntitiesSheet(workbook, impactResult.getEntities(), headerStyle, dataStyle);
            createServicesSheet(workbook, impactResult.getServices(), headerStyle, dataStyle);
            createControllersSheet(workbook, impactResult.getControllers(), headerStyle, dataStyle);
            createUsageDetailsSheet(workbook, impactResult.getColumnUsages(), headerStyle, dataStyle);
            createApiEndpointsSheet(workbook, impactResult.getControllers(), headerStyle, dataStyle);

            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
                workbook.write(fileOut);
            }

            log.info("Excel report generated successfully: {}", outputPath);

        } catch (IOException e) {
            log.error("Error generating Excel report", e);
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }

    private void createSummarySheet(Workbook workbook, ImpactResult result,
                                    CellStyle headerStyle, CellStyle dataStyle, CellStyle titleStyle) {
        Sheet sheet = workbook.createSheet("📊 Summary");

        int rowNum = 0;

        // Title
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("🔍 Code Impact Analysis Report");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));

        rowNum++; // Empty row

        // Analysis details
        createInfoSection(sheet, rowNum, "Analysis Details", headerStyle, dataStyle);
        rowNum += 2;

        addInfoRow(sheet, rowNum++, "📅 Analysis Date:",
                result.getAnalysisDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), dataStyle);
        addInfoRow(sheet, rowNum++, "📂 Project Path:", result.getProjectPath(), dataStyle);
        addInfoRow(sheet, rowNum++, "🗂️ Column Analyzed:", result.getColumnName(), dataStyle);
        addInfoRow(sheet, rowNum++, "⏱️ Analysis Time:", result.getAnalysisTimeMs() + " ms", dataStyle);

        rowNum++; // Empty row

        // Impact summary
        createInfoSection(sheet, rowNum, "Impact Summary", headerStyle, dataStyle);
        rowNum += 2;

        String[] categories = {"🏛️ Repositories", "📋 Entities", "⚙️ Services", "🌐 Controllers", "📊 Total Usages", "📁 Total Classes"};
        int[] counts = {
                result.getRepositories().size(),
                result.getEntities().size(),
                result.getServices().size(),
                result.getControllers().size(),
                result.getColumnUsages().size(),
                result.getTotalImpactedClasses()
        };

        for (int i = 0; i < categories.length; i++) {
            addInfoRow(sheet, rowNum++, categories[i], String.valueOf(counts[i]), dataStyle);
        }

        // Auto-size columns
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createRepositoriesSheet(Workbook workbook, List<ClassInfo> repositories,
                                         CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("🏛️ Repositories (" + repositories.size() + ")");
        createClassInfoSheet(sheet, repositories, headerStyle, dataStyle);
    }

    private void createEntitiesSheet(Workbook workbook, List<ClassInfo> entities,
                                     CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("📋 Entities (" + entities.size() + ")");
        createClassInfoSheet(sheet, entities, headerStyle, dataStyle);
    }

    private void createServicesSheet(Workbook workbook, List<ClassInfo> services,
                                     CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("⚙️ Services (" + services.size() + ")");
        createClassInfoSheet(sheet, services, headerStyle, dataStyle);
    }

    private void createControllersSheet(Workbook workbook, List<ClassInfo> controllers,
                                        CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("🌐 Controllers (" + controllers.size() + ")");
        createClassInfoSheet(sheet, controllers, headerStyle, dataStyle);
    }

    private void createClassInfoSheet(Sheet sheet, List<ClassInfo> classInfoList,
                                      CellStyle headerStyle, CellStyle dataStyle) {
        int rowNum = 0;

        // Create headers
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Class Name", "Package", "File Path", "Class Type", "Impact Reason",
                "Usage Count", "Methods", "Fields", "Annotations"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Add data rows
        for (ClassInfo classInfo : classInfoList) {
            Row dataRow = sheet.createRow(rowNum++);

            dataRow.createCell(0).setCellValue(classInfo.getClassName());
            dataRow.createCell(1).setCellValue(classInfo.getPackageName());
            dataRow.createCell(2).setCellValue(classInfo.getShortFilePath());
            dataRow.createCell(3).setCellValue(classInfo.getClassType());
            dataRow.createCell(4).setCellValue(classInfo.getImpactReason() != null ? classInfo.getImpactReason() : "");
            dataRow.createCell(5).setCellValue(classInfo.getUsageCount());
            dataRow.createCell(6).setCellValue(joinList(classInfo.getMethods(), 3));
            dataRow.createCell(7).setCellValue(joinList(classInfo.getFields(), 3));
            dataRow.createCell(8).setCellValue(joinList(classInfo.getAnnotations(), 5));

            // Apply data style
            for (int i = 0; i < headers.length; i++) {
                dataRow.getCell(i).setCellStyle(dataStyle);
            }
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createUsageDetailsSheet(Workbook workbook, List<ColumnUsage> usages,
                                         CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("🔍 Usage Details (" + usages.size() + ")");

        int rowNum = 0;

        // Create headers
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Class Name", "Method/Field", "Usage Type", "Context", "Line #", "File Path"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Add data rows
        for (ColumnUsage usage : usages) {
            Row dataRow = sheet.createRow(rowNum++);

            dataRow.createCell(0).setCellValue(usage.getClassName());
            dataRow.createCell(1).setCellValue(usage.getMethodName());
            dataRow.createCell(2).setCellValue(usage.getUsageType());
            dataRow.createCell(3).setCellValue(truncateString(usage.getContext(), 100));
            dataRow.createCell(4).setCellValue(usage.getLineNumber());
            dataRow.createCell(5).setCellValue(usage.getFilePath());

            // Apply data style
            for (int i = 0; i < headers.length; i++) {
                dataRow.getCell(i).setCellStyle(dataStyle);
            }
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createApiEndpointsSheet(Workbook workbook, List<ClassInfo> controllers,
                                         CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("🔗 API Endpoints");

        int rowNum = 0;

        // Create headers
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Controller", "Package", "API Endpoint", "Impact Reason"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Add data rows
        for (ClassInfo controller : controllers) {
            if (controller.getApiEndpoints().isEmpty()) {
                Row dataRow = sheet.createRow(rowNum++);
                dataRow.createCell(0).setCellValue(controller.getClassName());
                dataRow.createCell(1).setCellValue(controller.getPackageName());
                dataRow.createCell(2).setCellValue("No explicit endpoints found");
                dataRow.createCell(3).setCellValue(controller.getImpactReason());

                for (int i = 0; i < headers.length; i++) {
                    dataRow.getCell(i).setCellStyle(dataStyle);
                }
            } else {
                for (String endpoint : controller.getApiEndpoints()) {
                    Row dataRow = sheet.createRow(rowNum++);
                    dataRow.createCell(0).setCellValue(controller.getClassName());
                    dataRow.createCell(1).setCellValue(controller.getPackageName());
                    dataRow.createCell(2).setCellValue(endpoint);
                    dataRow.createCell(3).setCellValue(controller.getImpactReason());

                    for (int i = 0; i < headers.length; i++) {
                        dataRow.getCell(i).setCellStyle(dataStyle);
                    }
                }
            }
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createInfoSection(Sheet sheet, int rowNum, String title, CellStyle headerStyle, CellStyle dataStyle) {
        Row sectionRow = sheet.createRow(rowNum);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue(title);
        sectionCell.setCellStyle(headerStyle);
    }

    private void addInfoRow(Sheet sheet, int rowNum, String label, String value, CellStyle dataStyle) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        Cell valueCell = row.createCell(1);

        labelCell.setCellValue(label);
        valueCell.setCellValue(value);

        labelCell.setCellStyle(dataStyle);
        valueCell.setCellStyle(dataStyle);
    }

    private String joinList(List<String> list, int maxItems) {
        if (list == null || list.isEmpty()) return "";

        List<String> limitedList = list.size() > maxItems ?
                list.subList(0, maxItems) : list;

        String result = String.join(", ", limitedList);
        if (list.size() > maxItems) {
            result += "... (+" + (list.size() - maxItems) + " more)";
        }

        return result;
    }

    private String truncateString(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
}
