package org.example;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class JenkinsJobExporter {
    private static final String JENKINS_URL = "http://192.168.1.198:8080/";
    private static final String USERNAME = "ganoor";
    private static final String TOKEN = "11628c7a967092a9fceca0e1ab9fb5f8e8";
    private static final String OUTPUT_DIRECTORY = "jenkins-configs/";
    private static final String REPORT_FILE = "jenkins-plugin-report.xlsx";

    public static void main(String[] args) {
        new File(OUTPUT_DIRECTORY).mkdirs();
        List<PipelineReport> reports = new ArrayList<>();
        exportAllJobs("", reports);
        generateExcelReport(reports);
    }

    private static void exportAllJobs(String folderPath, List<PipelineReport> reports) {
        try {
            String apiUrl = JENKINS_URL + folderPath + "api/json?tree=jobs[name,url]";
            System.out.println("Fetching jobs from: " + apiUrl);
            JSONObject response = getJsonResponse(apiUrl);
            if (response == null) {
                System.out.println("No response from API for: " + apiUrl);
                return;
            }

            if (response.has("jobs")) {
                JSONArray jobs = response.getJSONArray("jobs");
                for (int i = 0; i < jobs.length(); i++) {
                    JSONObject job = jobs.getJSONObject(i);
                    String jobName = job.getString("name");
                    String jobUrl = job.getString("url");

                    System.out.println("Processing job: " + jobName + ", URL: " + jobUrl);

                    // Check if the job is a folder
                    if (jobUrl.endsWith("/")) {
                        exportAllJobs(folderPath + "job/" + jobName + "/", reports);
                    } else {
                        exportJobConfig(jobName, folderPath, reports);
                    }
                }
            } else {
                System.out.println("No nested jobs found, treating as a leaf job.");
                String jobName = folderPath.substring(folderPath.lastIndexOf("job/") + 4, folderPath.length() - 1);
                String parentPath = folderPath.substring(0, folderPath.lastIndexOf("job/") - 1);
                exportJobConfig(jobName, parentPath, reports);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void exportJobConfig(String jobName, String folderPath, List<PipelineReport> reports) {
        try {
            // Correct URL construction for fetching job config
            String configUrl = JENKINS_URL + folderPath + (folderPath.endsWith("/") ? "" : "/") + "job/" + jobName + "/config.xml";
            System.out.println("Fetching config from: " + configUrl);
            String configXml = getXmlResponse(configUrl);
            if (configXml != null) {
                String outputFilePath = OUTPUT_DIRECTORY + folderPath.replace("/", "-") + "-" + jobName + ".xml";
                System.out.println("Saving config to: " + outputFilePath);
                FileWriter writer = new FileWriter(new File(outputFilePath));
                writer.write(configXml);
                writer.close();
                System.out.println("Exported config for: " + jobName);

                // Analyze the plugins and OS used in this job's config
                List<String> plugins = analyzePlugins(configXml);
                String os = analyzeOS(configXml);

                // Fetch build information
                String lastBuild = getBuildInfo(folderPath + "/job/" + jobName, "lastBuild");
                String lastStableBuild = getBuildInfo(folderPath + "/job/" + jobName, "lastStableBuild");
                String lastSuccessfulBuild = getBuildInfo(folderPath + "/job/" + jobName, "lastSuccessfulBuild");
                String lastFailedBuild = getBuildInfo(folderPath + "/job/" + jobName, "lastFailedBuild");
                String lastUnstableBuild = getBuildInfo(folderPath + "/job/" + jobName, "lastUnstableBuild");
                String lastUnsuccessfulBuild = getBuildInfo(folderPath + "/job/" + jobName, "lastUnsuccessfulBuild");
                String lastCompletedBuild = getBuildInfo(folderPath + "/job/" + jobName, "lastCompletedBuild");

                reports.add(new PipelineReport(outputFilePath, plugins, os, lastBuild, lastStableBuild, lastSuccessfulBuild,
                        lastFailedBuild, lastUnstableBuild, lastUnsuccessfulBuild, lastCompletedBuild));
            } else {
                System.out.println("Failed to fetch config for: " + jobName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getBuildInfo(String jobPath, String buildType) {
        try {
            String apiUrl = JENKINS_URL + jobPath + "/" + buildType + "/api/json";
            JSONObject response = getJsonResponse(apiUrl);
            if (response != null) {
                switch (buildType) {
                    case "lastSuccessfulBuild":
                    case "lastFailedBuild":
                    case "lastUnstableBuild":
                    case "lastUnsuccessfulBuild":
                    case "lastCompletedBuild":
                    case "lastStableBuild":
                        long timestamp = response.optLong("timestamp");
                        return new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new java.util.Date(timestamp));
                    case "lastBuild":
                        long duration = response.optLong("duration");
                        return duration > 0 ? (duration / 60000) + " minutes" : "N/A";
                    default:
                        return "N/A";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "N/A";
    }




    private static List<String> analyzePlugins(String configXml) {
        List<String> plugins = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new java.io.ByteArrayInputStream(configXml.getBytes()));

            // Look for all elements with a "plugin" attribute
            NodeList nodes = doc.getElementsByTagName("*");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node instanceof Element) {
                    Element element = (Element) node;
                    String plugin = element.getAttribute("plugin");
                    if (plugin != null && !plugin.isEmpty() && !plugins.contains(plugin)) {
                        plugins.add(plugin);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return plugins;
    }

    private static String analyzeOS(String configXml) {
        String os = "Unknown";
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new java.io.ByteArrayInputStream(configXml.getBytes()));

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




    private static void generateExcelReport(List<PipelineReport> reports) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Plugin Report");

        int rowNum = 0;
        Row headerRow = sheet.createRow(rowNum++);
        Cell headerCell1 = headerRow.createCell(0);
        headerCell1.setCellValue("Pipeline");
        Cell headerCell2 = headerRow.createCell(1);
        headerCell2.setCellValue("Plugins Used");
        Cell headerCell3 = headerRow.createCell(2);
        headerCell3.setCellValue("Plugin Count");
        Cell headerCell4 = headerRow.createCell(3);
        headerCell4.setCellValue("Operating System");
        Cell headerCell5 = headerRow.createCell(4);
        headerCell5.setCellValue("Last Build");
        Cell headerCell6 = headerRow.createCell(5);
        headerCell6.setCellValue("Last Stable Build");
        Cell headerCell7 = headerRow.createCell(6);
        headerCell7.setCellValue("Last Successful Build");
        Cell headerCell8 = headerRow.createCell(7);
        headerCell8.setCellValue("Last Failed Build");
        Cell headerCell9 = headerRow.createCell(8);
        headerCell9.setCellValue("Last Unstable Build");
        Cell headerCell10 = headerRow.createCell(9);
        headerCell10.setCellValue("Last Unsuccessful Build");
        Cell headerCell11 = headerRow.createCell(10);
        headerCell11.setCellValue("Last Completed Build");

        for (PipelineReport report : reports) {
            Row row = sheet.createRow(rowNum++);
            Cell cell1 = row.createCell(0);
            cell1.setCellValue(report.getPipeline());
            Cell cell2 = row.createCell(1);
            cell2.setCellValue(String.join(", ", report.getPlugins()));
            Cell cell3 = row.createCell(2);
            cell3.setCellValue(report.getPlugins().size());
            Cell cell4 = row.createCell(3);
            cell4.setCellValue(report.getOs());
            Cell cell5 = row.createCell(4);
            cell5.setCellValue(report.getLastBuild());
            Cell cell6 = row.createCell(5);
            cell6.setCellValue(report.getLastStableBuild());
            Cell cell7 = row.createCell(6);
            cell7.setCellValue(report.getLastSuccessfulBuild());
            Cell cell8 = row.createCell(7);
            cell8.setCellValue(report.getLastFailedBuild());
            Cell cell9 = row.createCell(8);
            cell9.setCellValue(report.getLastUnstableBuild());
            Cell cell10 = row.createCell(9);
            cell10.setCellValue(report.getLastUnsuccessfulBuild());
            Cell cell11 = row.createCell(10);
            cell11.setCellValue(report.getLastCompletedBuild());
        }

        try (FileOutputStream outputStream = new FileOutputStream(REPORT_FILE)) {
            workbook.write(outputStream);
            workbook.close();
            System.out.println("Generated detailed plugin usage report: " + REPORT_FILE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private static JSONObject getJsonResponse(String apiUrl) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");
        String auth = USERNAME + ":" + TOKEN;
        String encodedAuth = "Basic " + new String(java.util.Base64.getEncoder().encode(auth.getBytes()));
        conn.setRequestProperty("Authorization", encodedAuth);
        if (conn.getResponseCode() != 200) {
            System.out.println("Failed to get JSON response. HTTP error code: " + conn.getResponseCode());
            return null;
        }
        Scanner scanner = new Scanner(conn.getInputStream());
        StringBuilder response = new StringBuilder();
        while (scanner.hasNextLine()) {
            response.append(scanner.nextLine());
        }
        scanner.close();
        return new JSONObject(response.toString());
    }

    private static String getXmlResponse(String configUrl) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(configUrl).openConnection();
        conn.setRequestMethod("GET");
        String auth = USERNAME + ":" + TOKEN;
        String encodedAuth = "Basic " + new String(java.util.Base64.getEncoder().encode(auth.getBytes()));
        conn.setRequestProperty("Authorization", encodedAuth);
        if (conn.getResponseCode() != 200) {
            System.out.println("Failed to get XML response. HTTP error code: " + conn.getResponseCode());
            return null;
        }
        Scanner scanner = new Scanner(conn.getInputStream());
        StringBuilder response = new StringBuilder();
        while (scanner.hasNextLine()) {
            response.append(scanner.nextLine());
        }
        scanner.close();
        return response.toString();
    }
}

class PipelineReport {
    private final String pipeline;
    private final List<String> plugins;
    private final String os;
    private final String lastBuild;
    private final String lastStableBuild;
    private final String lastSuccessfulBuild;
    private final String lastFailedBuild;
    private final String lastUnstableBuild;
    private final String lastUnsuccessfulBuild;
    private final String lastCompletedBuild;

    public PipelineReport(String pipeline, List<String> plugins, String os, String lastBuild, String lastStableBuild,
                          String lastSuccessfulBuild, String lastFailedBuild, String lastUnstableBuild,
                          String lastUnsuccessfulBuild, String lastCompletedBuild) {
        this.pipeline = pipeline;
        this.plugins = plugins;
        this.os = os;
        this.lastBuild = lastBuild;
        this.lastStableBuild = lastStableBuild;
        this.lastSuccessfulBuild = lastSuccessfulBuild;
        this.lastFailedBuild = lastFailedBuild;
        this.lastUnstableBuild = lastUnstableBuild;
        this.lastUnsuccessfulBuild = lastUnsuccessfulBuild;
        this.lastCompletedBuild = lastCompletedBuild;
    }

    public String getPipeline() {
        return pipeline;
    }

    public List<String> getPlugins() {
        return plugins;
    }

    public String getOs() {
        return os;
    }

    public String getLastBuild() {
        return lastBuild;
    }

    public String getLastStableBuild() {
        return lastStableBuild;
    }

    public String getLastSuccessfulBuild() {
        return lastSuccessfulBuild;
    }

    public String getLastFailedBuild() {
        return lastFailedBuild;
    }

    public String getLastUnstableBuild() {
        return lastUnstableBuild;
    }

    public String getLastUnsuccessfulBuild() {
        return lastUnsuccessfulBuild;
    }

    public String getLastCompletedBuild() {
        return lastCompletedBuild;
    }
}



