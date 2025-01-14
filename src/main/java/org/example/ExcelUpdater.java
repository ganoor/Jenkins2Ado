package org.example;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExcelUpdater {
    private static final String INPUT_FILE = "C:\\Users\\ganes\\ado\\0ADO PROJECTS\\Jenkins2Ado\\jenkins-pipelines-report.xlsx";
    private static final String OUTPUT_FILE = "C:\\Users\\ganes\\ado\\0ADO PROJECTS\\Jenkins2Ado\\jenkins-pipelines-report_new.xlsx";

    public static void main(String[] args) {
        try (FileInputStream fis = new FileInputStream(INPUT_FILE);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            XSSFSheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                Cell techStackCell = row.getCell(1);
                Cell osCell = row.getCell(4);
                Cell pipelineCell = row.getCell(0);

                if (isCellHighlighted(row)) {
                    String xmlFilePath = pipelineCell.getStringCellValue();
                    String configXml = new String(Files.readAllBytes(Paths.get(xmlFilePath)));

                    // Update techStack and Operating System/Node based on <script> tags
                    String techStack = analyzeTechStack(configXml);
                    String os = analyzeOS(configXml);

                    // Set the updated values in the cells
                    techStackCell.setCellValue(techStack);
                    osCell.setCellValue(os);
                }
            }

            // Write the updated workbook to a file
            try (FileOutputStream fos = new FileOutputStream(OUTPUT_FILE)) {
                workbook.write(fos);
                workbook.close();
                System.out.println("Updated Excel file saved to: " + OUTPUT_FILE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isCellHighlighted(Row row) {
        for (Cell cell : row) {
            CellStyle style = cell.getCellStyle();
            if (style.getFillForegroundColor() == IndexedColors.YELLOW.getIndex()) {
                return true;
            }
        }
        return false;
    }

    private static String analyzeTechStack(String configXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(configXml.getBytes()));

            NodeList scriptNodes = doc.getElementsByTagName("script");
            StringBuilder scriptContentBuilder = new StringBuilder();
            for (int i = 0; i < scriptNodes.getLength(); i++) {
                String scriptContent = scriptNodes.item(i).getTextContent();
                scriptContentBuilder.append(scriptContent).append("\n");
            }

            // Extract commands from the aggregated script content
            String aggregatedScriptContent = scriptContentBuilder.toString();
            Map<String, Integer> commandsFromScript = extractCommandsWithLineNumbers(aggregatedScriptContent);
            if (!commandsFromScript.isEmpty()) {
                return detectTechStack(new ArrayList<>(commandsFromScript.keySet()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Unknown";
    }

    // Assuming this method is already defined elsewhere
    private static String analyzeOS(String configXml) {
        String os = "Unknown";
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(configXml.getBytes()));

            // Look for the agent node label
            NodeList nodes = doc.getElementsByTagName("script");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node instanceof Element) {
                    Element element = (Element) node;
                    String scriptContent = element.getTextContent();
                    if (scriptContent.contains("agent") && scriptContent.contains("node")) {
                        int start = scriptContent.indexOf("label '") + 7;
                        int end = scriptContent.indexOf("'", start);
                        os = scriptContent.substring(start, end);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return os;
    }

    private static Map<String, Integer> extractCommandsWithLineNumbers(String scriptContent) {
        // Your implementation here
        return new HashMap<>();
    }

    private static String detectTechStack(List<String> commands) {
        // Your implementation here
        return "Unknown";
    }
}
