package org.example;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileWriter;

public class JenkinsToAzurePipelineConverter {
    private static final String INPUT_DIRECTORY = "jenkins-configs/";
    private static final String OUTPUT_DIRECTORY = "azure-pipelines/";

    public static void main(String[] args) {
        new File(OUTPUT_DIRECTORY).mkdirs();
        File[] xmlFiles = new File(INPUT_DIRECTORY).listFiles((dir, name) -> name.endsWith(".xml"));
        if (xmlFiles != null) {
            for (File xmlFile : xmlFiles) {
                convertXmlToYaml(xmlFile);
            }
        }
    }

    private static void convertXmlToYaml(File xmlFile) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            String jobName = getElementTextContent(doc, "name");

            // Extract the script content from <script> section
            NodeList scriptNodeList = doc.getElementsByTagName("script");
            if (scriptNodeList.getLength() == 0) {
                System.out.println("No script section found in " + xmlFile.getName());
                return;
            }
            String scriptContent = scriptNodeList.item(0).getTextContent();

            // Initialize YAML content
            StringBuilder yamlContent = new StringBuilder();
            yamlContent.append("trigger:\n  branches:\n    include:\n      - '*'\n\n");
            yamlContent.append("jobs:\n");
            yamlContent.append("- job: ").append(jobName).append("\n");
            yamlContent.append("  pool:\n");
            yamlContent.append("    vmImage: 'ubuntu-latest'\n");
            yamlContent.append("  steps:\n");

            // Extract NOTIFYUSERS and include in email notification section
            String notifyUsers = extractNotifyUsers(scriptContent);

            // Parse and convert the script content to YAML format
            parseScriptToYaml(scriptContent, yamlContent, notifyUsers);

            // Write YAML file
            String yamlFileName = OUTPUT_DIRECTORY + xmlFile.getName().replace(".xml", ".yaml");
            try (FileWriter writer = new FileWriter(new File(yamlFileName))) {
                writer.write(yamlContent.toString());
                System.out.println("Converted " + xmlFile.getName() + " to " + yamlFileName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void parseScriptToYaml(String scriptContent, StringBuilder yamlContent, String notifyUsers) {
        String[] lines = scriptContent.split("\n");
        boolean inStages = false;
        boolean inSteps = false;

        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("pipeline {") || line.startsWith("agent {") || line.startsWith("environment {")
                    || line.startsWith("parameters {") || line.startsWith("options {")) {
                continue;
            }

            if (line.startsWith("stages {")) {
                inStages = true;
                yamlContent.append("  stages:\n");
                continue;
            }

            if (line.startsWith("stage (")) {
                String stageName = line.split("\"")[1];
                yamlContent.append("    - stage: ").append(stageName).append("\n");
                yamlContent.append("      jobs:\n");
                yamlContent.append("      - job: ").append(stageName).append("\n");
                yamlContent.append("        steps:\n");
                inSteps = true;
                continue;
            }

            if (line.startsWith("steps {")) {
                inSteps = true;
                continue;
            }

            if (line.startsWith("}")) {
                if (inSteps) {
                    inSteps = false;
                    continue;
                }
                if (inStages) {
                    inStages = false;
                    continue;
                }
            }

            if (inSteps) {
                yamlContent.append("        - script: |\n");
                yamlContent.append("            ").append(line).append("\n");
            }
        }

        // Add email notifications
        if (!notifyUsers.isEmpty()) {
            yamlContent.append("notifications:\n");
            yamlContent.append("  - email: |\n");
            yamlContent.append("      subject: 'Build Status'\n");
            yamlContent.append("      to:\n");
            String[] users = notifyUsers.split(",");
            for (String user : users) {
                yamlContent.append("        - ").append(user.trim()).append("\n");
            }
            yamlContent.append("      body: 'The build status is $(Build.Status)'\n");
        }
    }

    private static String extractNotifyUsers(String scriptContent) {
        String notifyUsers = "";
        String[] lines = scriptContent.split("\n");
        for (String line : lines) {
            if (line.contains("NOTIFYUSERS")) {
                int start = line.indexOf("'") + 1;
                int end = line.lastIndexOf("'");
                if (start < end) {
                    notifyUsers = line.substring(start, end);
                }
                break;
            }
        }
        return notifyUsers;
    }

    private static String getElementTextContent(Document doc, String tagName) {
        NodeList nodeList = doc.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0 && nodeList.item(0) != null) {
            return nodeList.item(0).getTextContent();
        }
        return "";
    }
}
