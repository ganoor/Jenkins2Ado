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
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JenkinsJobExporter {
    private static final Properties properties = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream("src/main/resources/config.properties")) {
            properties.load(fis);
        } catch (IOException e) {
            System.err.println("Error loading config.properties: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static final String JENKINS_URL = getProperty("JENKINS_URL");
    public static final String USERNAME = getProperty("USERNAME");
    public static final String TOKEN = getProperty("TOKEN");
    public static final String OUTPUT_DIRECTORY = getProperty("OUTPUT_DIRECTORY");
    public static final String REPORT_FILE = getProperty("REPORT_FILE");
    public static final String GITHUB_TOKEN = getProperty("GITHUB_TOKEN");
    public static final String GITHUB_OWNER = getProperty("GITHUB_OWNER");
    public static final boolean FETCH_JENKINS_API = Boolean.parseBoolean(getProperty("FETCH_JENKINS_API"));
    public static final String TECHSTACK_CSV = getProperty("TECHSTACK_CSV");

    public static void main(String[] args) throws IOException {

        // Usage
//        listRepoContents("newtglobalgit", "DMAP_Jenkins_Pipelines", "1_DB_DMAP_Binary_New", "scripts_backup");

//        identifyMissingTechnologies();


        if (isFileOpened(REPORT_FILE)) {
            System.out.println("The Excel file is currently opened by another process. Program will terminate.");
            System.exit(1);
        } else {
            System.out.println("The Excel file is not opened. Proceeding with the program.");
            // Proceed with your program logic here
        }
        new File(OUTPUT_DIRECTORY).mkdirs();
        List<PipelineReport> reports = new ArrayList<>();

        if (FETCH_JENKINS_API) {
            exportAllJobs("", reports);
        } else {
            processLocalXmlFiles(reports);
        }
        generateExcelReport(reports);
        //Read JenkinsFile to Summarize the Script Modified Content in XML
        readAndPrintUnknownTechStackPipelines();

    }

//    public static void main(String[] args) {
//        if (isFileOpened(REPORT_FILE)) {
//            System.out.println("The Excel file is currently opened by another process. Program will terminate.");
//            System.exit(1);
//        } else {
//            System.out.println("The Excel file is not opened. Proceeding with the program.");
//        }
//        new File(OUTPUT_DIRECTORY).mkdirs();
//        List<PipelineReport> reports = new ArrayList<>();
//
//        // Process local XML files
//        processLocalXmlFiles(reports);
//
//        // Generate the Excel report
//        generateExcelReport(reports);
//    }

    private static void processLocalXmlFiles(List<PipelineReport> reports) {
        File folder = new File(OUTPUT_DIRECTORY);
        File[] listOfFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));
        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                try {
                    String localFilePath = file.getPath();
                    String configXml = new String(Files.readAllBytes(Paths.get(localFilePath)));
                    System.out.println("Reading file: " + localFilePath); // Debug statement
                    String jobName = file.getName().replace(".xml", "");
                    processJobConfig(jobName, configXml, reports);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private static void processJobConfig(String jobName, String configXml, List<PipelineReport> reports) {
        try {
            List<String> plugins = analyzePlugins(configXml);
            String os = analyzeOS(configXml);
            List<String> stages = extractStages(configXml);

            String techStack = "Unknown";
            Map<String, Integer> commandsWithLines = extractCommandsWithLineNumbers(configXml);
            if (!commandsWithLines.isEmpty()) {
                techStack = detectTechStack(new ArrayList<>(commandsWithLines.keySet()));
            }

            if (commandsWithLines.isEmpty() && configXml.contains("<scm>")) {
                String repoName = extractRepoName(configXml);
                String branchName = extractBranchName(configXml);
                String scriptPath = extractScriptPath(configXml);

                System.out.println("Repository Name: " + repoName);
                System.out.println("Branch Name: " + branchName);
                System.out.println("Script Path: " + scriptPath);

                try {
                    if (!repoName.equals("Unknown")) {
                        String pipelineScript = fetchFileFromGitHub(GITHUB_OWNER, repoName, scriptPath, branchName);
                        Map<String, Integer> commandsFromScript = extractCommandsWithLineNumbers(pipelineScript);
                        techStack = detectTechStack(new ArrayList<>(commandsFromScript.keySet()));

                        configXml = addScriptToXml(configXml, pipelineScript);
                        FileWriter writer = new FileWriter(new File(OUTPUT_DIRECTORY + jobName + ".xml"));
                        writer.write(configXml);
                        writer.close();
                        System.out.println("Updated XML with fetched script for: " + jobName);
                    }
                } catch (IOException e) {
                    System.err.println("Failed to fetch file from GitHub: " + e.getMessage());
                }
            }

            // Skip build info fetching from Jenkins API
            String lastBuild = "N/A";
            String lastStableBuild = "N/A";
            String lastSuccessfulBuild = "N/A";
            String lastFailedBuild = "N/A";
            String lastUnstableBuild = "N/A";
            String lastUnsuccessfulBuild = "N/A";
            String lastCompletedBuild = "N/A";

            // Prepare a string to hold the highlighted content
            StringBuilder highlightedContent = new StringBuilder();
            for (Map.Entry<String, Integer> entry : commandsWithLines.entrySet()) {
                highlightedContent.append("Line ").append(entry.getValue()).append(": ").append(entry.getKey()).append("\n");
            }

            // Add a new PipelineReport object with all parameters
            reports.add(new PipelineReport(jobName, plugins, os, stages, techStack, lastBuild, lastStableBuild, lastSuccessfulBuild,
                    lastFailedBuild, lastUnstableBuild, lastUnsuccessfulBuild, lastCompletedBuild, highlightedContent.toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }
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


    private static String getBuildInfo(String jobPath, String buildType) {
        try {
            String apiUrl = JENKINS_URL + jobPath + "/" + buildType + "/api/json";
            JSONObject response = getJsonResponse(apiUrl);
            if (response != null) {
                switch (buildType) {
                    case "lastBuild":
                        long duration = response.optLong("duration");
                        return duration > 0 ? (duration / 60000) + " minutes" : "N/A";
                    case "lastSuccessfulBuild":
                    case "lastFailedBuild":
                    case "lastUnstableBuild":
                    case "lastUnsuccessfulBuild":
                    case "lastCompletedBuild":
                    case "lastStableBuild":
                        long timestamp = response.optLong("timestamp");
                        return timestamp > 0 ? new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new java.util.Date(timestamp)) : "N/A";
                    default:
                        return "N/A";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "N/A";
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

    private static List<String> analyzePlugins(String configXml) {
        List<String> plugins = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(configXml.getBytes()));

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

    private static List<String> extractStages(String configXml) {
        List<String> stages = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(configXml.getBytes()));

            NodeList scriptNodes = doc.getElementsByTagName("script");
            for (int i = 0; i < scriptNodes.getLength(); i++) {
                Node scriptNode = scriptNodes.item(i);
                if (scriptNode instanceof Element) {
                    Element scriptElement = (Element) scriptNode;
                    String scriptContent = scriptElement.getTextContent();

                    // First pattern to extract stage names
                    Pattern pattern1 = Pattern.compile("stage \\(\"([^\"]+)\"\\)");
                    Matcher matcher1 = pattern1.matcher(scriptContent);
                    while (matcher1.find()) {
                        stages.add(matcher1.group(1));
                    }

                    // Second pattern to extract stage names
                    Pattern pattern2 = Pattern.compile("stage\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
                    Matcher matcher2 = pattern2.matcher(scriptContent);
                    while (matcher2.find()) {
                        String stageName = matcher2.group(1);
                        if (!stages.contains(stageName)) { // Ensure no duplicates
                            stages.add(stageName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stages;
    }

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

    private static String extractRepoName(String configXml) {
        String repoName = "Unknown";
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(configXml.getBytes()));

            NodeList nodes = doc.getElementsByTagName("scm");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node instanceof Element) {
                    Element element = (Element) node;
                    NodeList childNodes = element.getElementsByTagName("url");
                    for (int j = 0; j < childNodes.getLength(); j++) {
                        Node childNode = childNodes.item(j);
                        if (childNode != null) {
                            String url = childNode.getTextContent();
                            System.out.println("Extracted URL: " + url);
                            if (url.contains("github.com")) {
                                repoName = url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf(".git"));
                                System.out.println("Extracted Repository Name: " + repoName);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return repoName;
    }

    private static String extractBranchName(String configXml) {
        String branchName = "master"; // Default to master if no branch specified
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(configXml.getBytes()));

            NodeList nodes = doc.getElementsByTagName("hudson.plugins.git.BranchSpec");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node instanceof Element) {
                    Element element = (Element) node;
                    NodeList childNodes = element.getElementsByTagName("name");
                    for (int j = 0; j < childNodes.getLength(); j++) {
                        Node childNode = childNodes.item(j);
                        if (childNode != null) {
                            String name = childNode.getTextContent();
                            branchName = name.replace("*/", ""); // Remove the wildcard and extract branch name
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return branchName;
    }

    private static String extractScriptPath(String configXml) {
        String scriptPath = "Jenkinsfile"; // Default to Jenkinsfile if no script path specified
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(configXml.getBytes()));

            NodeList nodes = doc.getElementsByTagName("scriptPath");
            if (nodes.getLength() > 0) {
                scriptPath = nodes.item(0).getTextContent();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return scriptPath;
    }

    public static String fetchFileFromGitHub(String owner, String repo, String filePath, String branch) throws IOException {
        // Construct the correct API URL
        String apiUrl = String.format("https://api.github.com/repos/%s/%s/contents/%s?ref=%s", owner, repo, filePath, branch);
        System.out.println("Fetching file from GitHub URL: " + apiUrl);

        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");
        String encodedAuth = "token " + GITHUB_TOKEN;
        conn.setRequestProperty("Authorization", encodedAuth);

        int responseCode = conn.getResponseCode();
        System.out.println("GitHub API response code: " + responseCode);
        if (responseCode != 200) {
            // Log the full response message for better diagnostics
            Scanner errorScanner = new Scanner(conn.getErrorStream());
            StringBuilder errorResponse = new StringBuilder();
            while (errorScanner.hasNextLine()) {
                errorResponse.append(errorScanner.nextLine());
            }
            errorScanner.close();
            System.out.println("Error response: " + errorResponse.toString());

            throw new IOException("Failed to fetch file. HTTP error code: " + responseCode + ". URL: " + apiUrl);
        }

        Scanner scanner = new Scanner(conn.getInputStream());
        StringBuilder response = new StringBuilder();
        while (scanner.hasNextLine()) {
            response.append(scanner.nextLine());
        }
        scanner.close();

        JSONObject jsonResponse = new JSONObject(response.toString());
        String downloadUrl = jsonResponse.getString("download_url");
        System.out.println(downloadUrl);
        // Use the download URL to fetch the file content
        conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
        conn.setRequestMethod("GET");
        responseCode = conn.getResponseCode();
        System.out.println("Download URL response code: " + responseCode);
        if (responseCode != 200) {
            throw new IOException("Failed to download file content. HTTP error code: " + responseCode + ". URL: " + downloadUrl);
        }

        scanner = new Scanner(conn.getInputStream());
        response = new StringBuilder();
        while (scanner.hasNextLine()) {
            response.append(scanner.nextLine());
        }
        scanner.close();

        return response.toString();
    }


    private static void reprocessJobConfig(String jobName, String configXml, List<PipelineReport> reports) {
        try {
            List<String> plugins = analyzePlugins(configXml);
            String os = analyzeOS(configXml);
            List<String> stages = extractStages(configXml);

            String techStack = "Unknown";
            Map<String, Integer> commandsWithLines = extractCommandsWithLineNumbers(configXml);

            // Check for commands in <script> tag
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
                techStack = detectTechStack(new ArrayList<>(commandsFromScript.keySet()));
            }

            // Prepare a string to hold the highlighted content
            StringBuilder highlightedContent = new StringBuilder();
            for (Map.Entry<String, Integer> entry : commandsWithLines.entrySet()) {
                highlightedContent.append("Line ").append(entry.getValue()).append(": ").append(entry.getKey()).append("\n");
            }

            // Find and update the corresponding report in the list
            for (PipelineReport report : reports) {
                if (report.getPipeline().equals(jobName)) {
                    reports.remove(report);
                    reports.add(new PipelineReport(jobName, plugins, os, stages, techStack, report.getLastBuild(),
                            report.getLastStableBuild(), report.getLastSuccessfulBuild(), report.getLastFailedBuild(),
                            report.getLastUnstableBuild(), report.getLastUnsuccessfulBuild(), report.getLastCompletedBuild(),
                            highlightedContent.toString()));
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static String addPipelineFromGitTag(String configXml, String pipelineContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(configXml.getBytes()));

            // Create <pipelineFromGit> element and add pipeline content
            Element pipelineElement = doc.createElement("pipelineFromGit");
            pipelineElement.setTextContent(pipelineContent);

            // Append the <pipelineFromGit> element to the root element
            doc.getDocumentElement().appendChild(pipelineElement);

            // Convert the updated document back to a string
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);

            return writer.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return configXml;
    }


    public static void listRepoContents(String owner, String repo, String path, String branch) throws IOException {
        // Construct the correct API URL for the directory listing
        String apiUrl = String.format("https://api.github.com/repos/%s/%s/contents/%s?ref=%s", owner, repo, path, branch);
        System.out.println("Listing contents from GitHub URL: " + apiUrl);

        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");
        String encodedAuth = "token " + GITHUB_TOKEN;
        conn.setRequestProperty("Authorization", encodedAuth);

        int responseCode = conn.getResponseCode();
        System.out.println("GitHub API response code: " + responseCode);
        if (responseCode != 200) {
            // Log the full response message for better diagnostics
            Scanner errorScanner = new Scanner(conn.getErrorStream());
            StringBuilder errorResponse = new StringBuilder();
            while (errorScanner.hasNextLine()) {
                errorResponse.append(errorScanner.nextLine());
            }
            errorScanner.close();
            System.out.println("Error response: " + errorResponse.toString());

            throw new IOException("Failed to list contents. HTTP error code: " + responseCode + ". URL: " + apiUrl);
        }

        Scanner scanner = new Scanner(conn.getInputStream());
        StringBuilder response = new StringBuilder();
        while (scanner.hasNextLine()) {
            response.append(scanner.nextLine());
        }
        scanner.close();

        System.out.println("Repository contents: " + response.toString());
    }


    private static List<String> extractCommands(String scriptContent) {
        List<String> commands = new ArrayList<>();
        try {
            // Use regex to extract commands from the script content
            Pattern pattern = Pattern.compile("(mvn|gradle|python|javac|java|npm|ng|vue|django|flask|rails|spring-boot|dotnet|flutter|react-native|ionic)\\s+[^\\n]+");
            Matcher matcher = pattern.matcher(scriptContent);
            while (matcher.find()) {
                commands.add(matcher.group());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return commands;
    }


//    private static List<String> extractCommandsFromScript(String scriptContent) {
//        List<String> commands = new ArrayList<>();
//        String[] lines = scriptContent.split("\\n");
//        Pattern pattern = Pattern.compile("\\b(podman|docker|sonar-scanner|mvn|gradle|python|pytest|javac|java|npm|ng|vue|django|flask|rails|spring-boot|dotnet|flutter|react-native|ionic|trivy|sh|jenkins|groovy|nexus|emailext|nodejs|restassured|az|terraform|git)\\b");
//        for (String line : lines) {
//            Matcher matcher = pattern.matcher(line);
//            while (matcher.find()) {
//                commands.add(matcher.group());
//            }
//        }
//        return commands;
//    }

    private static List<String> extractCommandsFromScript(String scriptContent) throws IOException {
        Map<String, String> techStackMap = loadTechStackMap(TECHSTACK_CSV);
        List<String> commands = new ArrayList<>();
        String[] lines = scriptContent.split("\\n");
        StringBuilder patternBuilder = new StringBuilder("\\b(");
        for (String keyword : techStackMap.keySet()) {
            patternBuilder.append(keyword).append("|");
        }
        patternBuilder.setLength(patternBuilder.length() - 1); // Remove last '|'
        patternBuilder.append(")\\b");
        Pattern pattern = Pattern.compile(patternBuilder.toString());
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            while (matcher.find()) {
                commands.add(matcher.group());
            }
        }
        return commands;
    }


//    private static String detectTechStack(List<String> commands) {
//        Map<String, String> techStackMap = new LinkedHashMap<>();
//        techStackMap.put("podman", "Podman");
//        techStackMap.put("docker", "Docker");
//        techStackMap.put("sonar-scanner", "SonarQube");
//        techStackMap.put("sh", "Shell");
//        techStackMap.put("mvn", "Maven");
//        techStackMap.put("gradle", "Gradle");
//        techStackMap.put("python", "Python");
//        techStackMap.put("javac", "Java");
//        techStackMap.put("java", "Java");
//        techStackMap.put("npm", "Node.js/ReactJS");
//        techStackMap.put("ng", "AngularJS");
//        techStackMap.put("vue", "Vue.js");
//        techStackMap.put("django", "Django");
//        techStackMap.put("flask", "Flask");
//        techStackMap.put("rails", "Ruby on Rails");
//        techStackMap.put("spring-boot", "Spring Boot");
//        techStackMap.put("dotnet", "ASP.NET");
//        techStackMap.put("flutter", "Flutter");
//        techStackMap.put("react-native", "React Native");
//        techStackMap.put("ionic", "Ionic");
//        techStackMap.put("trivy", "Trivy");
//        techStackMap.put("bandit", "Bandit");
//        techStackMap.put("pytest", "Pytest");
//        techStackMap.put("git", "Git");
//        techStackMap.put("jenkins", "Jenkins");
//        techStackMap.put("groovy", "Groovy");
//        techStackMap.put("nexus", "Nexus");
//        techStackMap.put("emailext", "Email Extension Plugin");
//        techStackMap.put("nodejs", "Node.js");
//        techStackMap.put("restassured", "RestAssured");
//        techStackMap.put("az", "Azure CLI");
//        techStackMap.put("terraform", "Terraform");
//
//        Set<String> detectedTechStacks = new LinkedHashSet<>();
//
//        for (String command : commands) {
//            for (String keyword : techStackMap.keySet()) {
//                if (command.contains(keyword)) {
//                    detectedTechStacks.add(techStackMap.get(keyword));
//                }
//            }
//        }
//
//        return String.join(", ", detectedTechStacks);
//    }


    private static String detectTechStack(List<String> commands) throws IOException {
        Map<String, String> techStackMap = loadTechStackMap(TECHSTACK_CSV);
        Set<String> detectedTechStacks = new LinkedHashSet<>();
        for (String command : commands) {
            for (String keyword : techStackMap.keySet()) {
                if (command.contains(keyword)) {
                    detectedTechStacks.add(techStackMap.get(keyword));
                }
            }
        }
        return String.join(", ", detectedTechStacks);
    }


    static class PipelineReport {
        private final String pipeline;
        private final List<String> plugins;
        private final String os;
        private final List<String> stages;
        private final String techStack;
        private final String lastBuild;
        private final String lastStableBuild;
        private final String lastSuccessfulBuild;
        private final String lastFailedBuild;
        private final String lastUnstableBuild;
        private final String lastUnsuccessfulBuild;
        private final String lastCompletedBuild;
        private final String highlightedContent;

        public PipelineReport(String pipeline, List<String> plugins, String os, List<String> stages, String techStack, String lastBuild, String lastStableBuild,
                              String lastSuccessfulBuild, String lastFailedBuild, String lastUnstableBuild,
                              String lastUnsuccessfulBuild, String lastCompletedBuild, String highlightedContent) {
            this.pipeline = pipeline;
            this.plugins = plugins;
            this.os = os;
            this.stages = stages;
            this.techStack = techStack;
            this.lastBuild = lastBuild;
            this.lastStableBuild = lastStableBuild;
            this.lastSuccessfulBuild = lastSuccessfulBuild;
            this.lastFailedBuild = lastFailedBuild;
            this.lastUnstableBuild = lastUnstableBuild;
            this.lastUnsuccessfulBuild = lastUnsuccessfulBuild;
            this.lastCompletedBuild = lastCompletedBuild;
            this.highlightedContent = highlightedContent;
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

        public List<String> getStages() {
            return stages;
        }

        public String getTechStack() {
            return techStack;
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

        public String getHighlightedContent() {
            return highlightedContent;
        }
    }


    private static void generateExcelReport(List<PipelineReport> reports) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Pipeline Extract Report");

        // Create a header font
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 8);
        headerFont.setFontName("Arial");
        headerFont.setColor(IndexedColors.WHITE.getIndex());

        // Create a cell style with the header font, background color, and border
        CellStyle headerCellStyle = workbook.createCellStyle();
        headerCellStyle.setFont(headerFont);
        headerCellStyle.setFillForegroundColor(IndexedColors.BLACK.getIndex());
        headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerCellStyle.setAlignment(HorizontalAlignment.CENTER);
        headerCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerCellStyle.setWrapText(true); // Enable word wrap
        headerCellStyle.setBorderBottom(BorderStyle.THIN);
        headerCellStyle.setBorderTop(BorderStyle.THIN);
        headerCellStyle.setBorderRight(BorderStyle.THIN);
        headerCellStyle.setBorderLeft(BorderStyle.THIN);

        // Create a normal font
        Font normalFont = workbook.createFont();
        normalFont.setFontHeightInPoints((short) 8);
        normalFont.setFontName("Arial");
        normalFont.setColor(IndexedColors.BLACK.getIndex());

        // Create a cell style for data rows with word wrap, center alignment, and border
        CellStyle rowCellStyle = workbook.createCellStyle();
        rowCellStyle.setFont(normalFont);
        rowCellStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        rowCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        rowCellStyle.setAlignment(HorizontalAlignment.CENTER);
        rowCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        rowCellStyle.setWrapText(true); // Enable word wrap
        rowCellStyle.setBorderBottom(BorderStyle.THIN);
        rowCellStyle.setBorderTop(BorderStyle.THIN);
        rowCellStyle.setBorderRight(BorderStyle.THIN);
        rowCellStyle.setBorderLeft(BorderStyle.THIN);

        // Create a cell style for highlighting yellow rows
        CellStyle yellowCellStyle = workbook.createCellStyle();
        yellowCellStyle.cloneStyleFrom(rowCellStyle);
        yellowCellStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        yellowCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Create a cell style for highlighting red font
        CellStyle redFontCellStyle = workbook.createCellStyle();
        redFontCellStyle.cloneStyleFrom(rowCellStyle);
        Font redFont = workbook.createFont();
        redFont.setColor(IndexedColors.RED.getIndex());
        redFontCellStyle.setFont(redFont);

        // Set initial column widths to 30
        for (int i = 0; i < 14; i++) { // Updated to 14 columns to accommodate new Highlighted Content column
            sheet.setColumnWidth(i, 30 * 256); // Width is measured in units of 1/256th of a character width
        }

        // Create the header row
        int rowNum = 0;
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Pipeline", "Tech Stack", "Plugins Used", "Plugin Count", "Operating System/Node", "Stages", "Last Build", "Last Stable Build", "Last Successful Build", "Last Failed Build", "Last Unstable Build", "Last Unsuccessful Build", "Last Completed Build", "Highlighted Content"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerCellStyle);
        }

        // Copy the reports list to avoid ConcurrentModificationException
        List<PipelineReport> reportsCopy = new ArrayList<>(reports);

        // Write data rows
        for (PipelineReport report : reportsCopy) {
            Row row = sheet.createRow(rowNum++);

            CellStyle currentRowCellStyle = rowCellStyle;
            if ("Unknown".equals(report.getTechStack())) {
                try {
                    String configXml = new String(Files.readAllBytes(Paths.get(report.getPipeline())));
                    boolean scriptUpdated = checkAndCloneRepo(report.getPipeline(), configXml, report.getPipeline(), reports);
                    // Update the reportsCopy after processing
                    reportsCopy = new ArrayList<>(reports);
                    if (scriptUpdated) {
                        currentRowCellStyle = yellowCellStyle;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    currentRowCellStyle = redFontCellStyle;
                }
            }

            Cell cell0 = row.createCell(0);
            cell0.setCellValue(truncateContent(report.getPipeline()));
            cell0.setCellStyle(currentRowCellStyle);

            Cell cell1 = row.createCell(1);
            cell1.setCellValue(truncateContent(report.getTechStack()));
            cell1.setCellStyle(currentRowCellStyle);

            Cell cell2 = row.createCell(2);
            cell2.setCellValue(truncateContent(String.join(", ", report.getPlugins())));
            cell2.setCellStyle(currentRowCellStyle);

            Cell cell3 = row.createCell(3);
            cell3.setCellValue(report.getPlugins().size());
            cell3.setCellStyle(currentRowCellStyle);

            Cell cell4 = row.createCell(4);
            cell4.setCellValue(truncateContent(report.getOs()));
            cell4.setCellStyle(currentRowCellStyle);

            Cell cell5 = row.createCell(5);
            cell5.setCellValue(truncateContent(String.join(", ", report.getStages())));
            cell5.setCellStyle(currentRowCellStyle);

            Cell cell6 = row.createCell(6);
            cell6.setCellValue(truncateContent(report.getLastBuild()));
            cell6.setCellStyle(currentRowCellStyle);

            Cell cell7 = row.createCell(7);
            cell7.setCellValue(truncateContent(report.getLastStableBuild()));
            cell7.setCellStyle(currentRowCellStyle);

            Cell cell8 = row.createCell(8);
            cell8.setCellValue(truncateContent(report.getLastSuccessfulBuild()));
            cell8.setCellStyle(currentRowCellStyle);

            Cell cell9 = row.createCell(9);
            cell9.setCellValue(truncateContent(report.getLastFailedBuild()));
            cell9.setCellStyle(currentRowCellStyle);

            Cell cell10 = row.createCell(10);
            cell10.setCellValue(truncateContent(report.getLastUnstableBuild()));
            cell10.setCellStyle(currentRowCellStyle);

            Cell cell11 = row.createCell(11);
            cell11.setCellValue(truncateContent(report.getLastUnsuccessfulBuild()));
            cell11.setCellStyle(currentRowCellStyle);

            Cell cell12 = row.createCell(12);
            cell12.setCellValue(truncateContent(report.getLastCompletedBuild()));
            cell12.setCellStyle(currentRowCellStyle);

            // New cell for Highlighted Content
            Cell cell13 = row.createCell(13);
            cell13.setCellValue(truncateContent(report.getHighlightedContent()));
            cell13.setCellStyle(currentRowCellStyle);

            // Adjust row height based on content
            row.setHeight((short) -1);
        }

        // Adjust column widths to fit content, not exceeding 30
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) > 30 * 256) {
                sheet.setColumnWidth(i, 30 * 256);
            }
        }

        // Turn off gridlines
        sheet.setDisplayGridlines(false);

        // Write the output to a file
        try (FileOutputStream outputStream = new FileOutputStream(REPORT_FILE)) {
            workbook.write(outputStream);
            workbook.close();
            System.out.println("Generated detailed pipeline report: " + REPORT_FILE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static boolean isFileOpened(String filePath) {
        File file = new File(filePath);
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
             FileChannel channel = raf.getChannel()) {

            FileLock lock = channel.tryLock();
            if (lock == null) {
                return true; // File is locked by another process
            }
            lock.release();
            return false; // File is not locked

        } catch (IOException e) {
            return true; // If there's an IO exception, assume the file is in use
        }
    }


    private static void exportJobConfig(String jobName, String folderPath, List<PipelineReport> reports) {
        try {
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

                List<String> plugins = analyzePlugins(configXml);
                String os = analyzeOS(configXml);
                List<String> stages = extractStages(configXml);

                String techStack = "Unknown";
                Map<String, Integer> commandsWithLines = extractCommandsWithLineNumbers(configXml);
                if (!commandsWithLines.isEmpty()) {
                    techStack = detectTechStack(new ArrayList<>(commandsWithLines.keySet()));
                }

                if (commandsWithLines.isEmpty() && configXml.contains("<scm>")) {
                    String repoName = extractRepoName(configXml);
                    String branchName = extractBranchName(configXml);
                    String scriptPath = extractScriptPath(configXml);

                    System.out.println("Repository Name: " + repoName);
                    System.out.println("Branch Name: " + branchName);
                    System.out.println("Script Path: " + scriptPath);

                    try {
                        if (!repoName.equals("Unknown")) {
                            String pipelineScript = fetchFileFromGitHub(GITHUB_OWNER, repoName, scriptPath, branchName);
                            Map<String, Integer> commandsFromScript = extractCommandsWithLineNumbers(pipelineScript);
                            techStack = detectTechStack(new ArrayList<>(commandsFromScript.keySet()));

                            configXml = addScriptToXml(configXml, pipelineScript);
                            writer = new FileWriter(new File(outputFilePath));
                            writer.write(configXml);
                            writer.close();
                            System.out.println("Updated XML with fetched script for: " + jobName);
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to fetch file from GitHub: " + e.getMessage());
                    }
                }

                String lastBuild = getBuildInfo(folderPath + "/job/" + jobName, "lastBuild");
                String lastStableBuild = getBuildInfo(folderPath + "/job/" + jobName, "lastStableBuild");
                String lastSuccessfulBuild = getBuildInfo(folderPath + "/job/" + jobName, "lastSuccessfulBuild");
                String lastFailedBuild = getBuildInfo(folderPath + "/job/" + jobName, "lastFailedBuild");
                String lastUnstableBuild = getBuildInfo(folderPath + "/job/" + jobName, "lastUnstableBuild");
                String lastUnsuccessfulBuild = getBuildInfo(folderPath + "/job/" + jobName, "lastUnsuccessfulBuild");
                String lastCompletedBuild = getBuildInfo(folderPath + "/job/" + jobName, "lastCompletedBuild");

                // Prepare a string to hold the highlighted content
                StringBuilder highlightedContent = new StringBuilder();
                for (Map.Entry<String, Integer> entry : commandsWithLines.entrySet()) {
                    highlightedContent.append("Line ").append(entry.getValue()).append(": ").append(entry.getKey()).append("\n");
                }

                reports.add(new PipelineReport(outputFilePath, plugins, os, stages, techStack, lastBuild, lastStableBuild, lastSuccessfulBuild,
                        lastFailedBuild, lastUnstableBuild, lastUnsuccessfulBuild, lastCompletedBuild, highlightedContent.toString()));
            } else {
                System.out.println("Failed to fetch config for: " + jobName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static String addScriptToXml(String configXml, String scriptContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(configXml.getBytes()));

            // Create <script> element and add script content
            Element scriptElement = doc.createElement("script");
            scriptElement.setTextContent(scriptContent);

            // Append the <script> element to the root element
            doc.getDocumentElement().appendChild(scriptElement);

            // Convert the updated document back to a string
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);

            return writer.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return configXml;
    }


    private static boolean checkAndCloneRepo(String jobName, String configXml, String outputFilePath, List<PipelineReport> reports) {
        String repoName = extractRepoName(configXml);
        String branchName = extractBranchName(configXml);
        String scriptPath = extractScriptPath(configXml);

        System.out.println("Repository Name: " + repoName);
        System.out.println("Branch Name: " + branchName);
        System.out.println("Script Path: " + scriptPath);

        try {
            if (!repoName.equals("Unknown")) {
                // Clone the repo to local
                String pipelineScript = fetchFileFromGitHub(GITHUB_OWNER, repoName, scriptPath, branchName);

                // Add comments and pipeline scripts
                pipelineScript = "// Manually pulled from git based on configurations\n" + pipelineScript;

                // Update the config XML with the fetched script using <script> tag
                configXml = addScriptTag(configXml, pipelineScript);

                // Write the updated config XML back to the file
                FileWriter writer = new FileWriter(new File(outputFilePath));
                writer.write(configXml);
                writer.close();
                System.out.println("Updated XML with fetched script for: " + jobName);

                // Reprocess the job config to update the tech stack and other details
                System.out.println(jobName + " | " + configXml + " | " + reports);
                reprocessJobConfig(jobName, configXml, reports);

                return true; // Indicate that the script was successfully fetched and added
            }
        } catch (IOException e) {
            System.err.println("Failed to fetch file from GitHub: " + e.getMessage());
        }

        return false; // Indicate that the script was not fetched or added
    }


    private static String addScriptTag(String configXml, String pipelineContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(configXml.getBytes()));

            // Create <script> element and add pipeline content
            Element scriptElement = doc.createElement("script");
            scriptElement.setTextContent(pipelineContent);

            // Append the <script> element to the root element
            doc.getDocumentElement().appendChild(scriptElement);

            // Convert the updated document back to a string
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);

            return writer.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return configXml;
    }


    private static void readAndPrintUnknownTechStackPipelines() {
        try (FileInputStream file = new FileInputStream(REPORT_FILE);
             XSSFWorkbook workbook = new XSSFWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            CreationHelper factory = workbook.getCreationHelper();

            // Create a bold Arial 8 font
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldFont.setFontHeightInPoints((short) 8);
            boldFont.setFontName("Arial");

            // Create a cell style with the bold font, borders, and yellow background
            CellStyle boldYellowBorderStyle = workbook.createCellStyle();
            boldYellowBorderStyle.setFont(boldFont);
            boldYellowBorderStyle.setBorderBottom(BorderStyle.THIN);
            boldYellowBorderStyle.setBorderTop(BorderStyle.THIN);
            boldYellowBorderStyle.setBorderLeft(BorderStyle.THIN);
            boldYellowBorderStyle.setBorderRight(BorderStyle.THIN);
            boldYellowBorderStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            boldYellowBorderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Skip the header row
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Cell techStackCell = row.getCell(1); // Tech Stack column

                if ("Unknown".equals(techStackCell.getStringCellValue()) && isYellow(row)) {
                    Cell pipelineCell = row.getCell(0); // Pipeline column
                    String pipeline = pipelineCell.getStringCellValue();

                    String techStack = getTechStackFromXml(pipeline);
                    String osNode = getOSFromXml(pipeline);
                    List<String> stages = getStagesFromXml(pipeline);
                    String highlightedContent = getHighlightedContentFromXml(pipeline);

                    try {
                        // Update Tech Stack column
                        techStackCell.setCellValue(techStack);
                        techStackCell.setCellStyle(boldYellowBorderStyle);

                        // Update Operating System/Node column
                        Cell osNodeCell = row.getCell(4); // Operating System/Node column
                        osNodeCell.setCellValue(osNode);
                        osNodeCell.setCellStyle(boldYellowBorderStyle);

                        // Update Stages column
                        Cell stagesCell = row.getCell(5); // Stages column
                        stagesCell.setCellValue(String.join(", ", stages));
                        stagesCell.setCellStyle(boldYellowBorderStyle);

                        // Update Highlighted Content column
                        Cell highlightedContentCell = row.getCell(13); // Highlighted Content column
                        String truncatedHighlightedContent = truncateContent(highlightedContent);
                        highlightedContentCell.setCellValue(truncatedHighlightedContent);
                        highlightedContentCell.setCellStyle(boldYellowBorderStyle);

                        // Add comment to Pipeline column
                        Drawing<?> drawing = sheet.createDrawingPatriarch();
                        Comment comment = drawing.createCellComment(factory.createClientAnchor());
                        comment.setString(factory.createRichTextString("Re-processed by locating the scripts from git repository"));
                        pipelineCell.setCellComment(comment);

                        System.out.println("Pipeline: " + pipeline);
                        System.out.println("Detected Tech Stack: " + techStack);
                        System.out.println("Operating System/Node: " + osNode);
                        System.out.println("Highlighted Content: " + truncatedHighlightedContent);

                        // Apply bold style with yellow background and borders to the entire row
                        for (int i = 0; i < row.getLastCellNum(); i++) {
                            Cell cell = row.getCell(i);
                            if (cell != null) {
                                cell.setCellStyle(boldYellowBorderStyle);
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        System.err.println("Content too long for cell: " + highlightedContent.length() + " characters. Truncated to 30,000 characters.");
                    }
                }
            }

            // Write the output to a file
            try (FileOutputStream outputStream = new FileOutputStream(REPORT_FILE)) {
                workbook.write(outputStream);
            }
            workbook.close();
            System.out.println("Excel file updated with TechStack, Operating System/Node, Stages, and Highlighted Content columns.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static String truncateContent(String content) {
        if (content.length() > 30000) {
            return content.substring(0, 30000) + "[TRUNCATED]";
        }
        return content;
    }


    private static boolean isYellow(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null) {
                CellStyle cellStyle = cell.getCellStyle();
                if (cellStyle.getFillForegroundColor() == IndexedColors.YELLOW.getIndex() ||
                        cellStyle.getFillBackgroundColor() == IndexedColors.YELLOW.getIndex()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getTechStackFromXml(String pipeline) {
        try {
            String configXml = new String(Files.readAllBytes(Paths.get(pipeline)));
            List<String> commands = extractCommandsFromScript(configXml);
            return detectTechStack(commands);
        } catch (IOException e) {
            e.printStackTrace();
            return "Unknown";
        }
    }

    private static String getOSFromXml(String pipeline) {
        try {
            String configXml = new String(Files.readAllBytes(Paths.get(pipeline)));
            return analyzeOS(configXml);
        } catch (IOException e) {
            e.printStackTrace();
            return "Unknown";
        }
    }

    private static List<String> getStagesFromXml(String pipeline) {
        try {
            String configXml = new String(Files.readAllBytes(Paths.get(pipeline)));
            return extractStages(configXml);
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private static String getHighlightedContentFromXml(String pipeline) {
        try {
            String configXml = new String(Files.readAllBytes(Paths.get(pipeline)));
            Map<String, Integer> commandsWithLines = extractCommandsWithLineNumbers(configXml);

            StringBuilder highlightedContent = new StringBuilder();
            for (Map.Entry<String, Integer> entry : commandsWithLines.entrySet()) {
                highlightedContent.append("Line ").append(entry.getValue()).append(": ").append(entry.getKey()).append("\n");
            }

            return truncateContent(highlightedContent.toString());
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to extract highlighted content.";
        }
    }


    private static Map<String, Integer> extractCommandsWithLineNumbers(String scriptContent) {
        Map<String, Integer> commandsWithLines = new HashMap<>();
        try {
            String[] lines = scriptContent.split("\\n");
            Pattern pattern = Pattern.compile("\\b(mvn|gradle|python|javac|java|npm|ng|vue|django|flask|rails|spring-boot|dotnet|flutter|react-native|ionic|docker|sh)\\b\\s+[^\\n]+");
            for (int i = 0; i < lines.length; i++) {
                Matcher matcher = pattern.matcher(lines[i]);
                while (matcher.find()) {
                    commandsWithLines.put(matcher.group(), i + 1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return commandsWithLines;
    }


    private static void identifyMissingTechnologies() {
        try (FileInputStream file = new FileInputStream(REPORT_FILE);
             XSSFWorkbook workbook = new XSSFWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            // Skip the header row
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            Set<String> missingTechnologies = new HashSet<>();

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Cell techStackCell = row.getCell(1); // Tech Stack column
                if ("Unknown".equals(techStackCell.getStringCellValue())) {
                    Cell pipelineCell = row.getCell(0); // Pipeline column
                    String pipeline = pipelineCell.getStringCellValue();
                    Set<String> detectedTech = detectMissingTechnologiesFromXml(pipeline);
                    missingTechnologies.addAll(detectedTech);
                }
            }

            System.out.println("Missing Technologies: " + missingTechnologies);
            enhanceDetectTechStack(missingTechnologies);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Set<String> detectMissingTechnologiesFromXml(String pipeline) {
        Set<String> detectedTechnologies = new HashSet<>();
        try {
            String configXml = new String(Files.readAllBytes(Paths.get(pipeline)));
            List<String> commands = extractCommandsFromScript(configXml);

            // Identify technologies not currently in detectTechStack
            Map<String, String> techStackMap = getCurrentTechStackMap();
            for (String command : commands) {
                boolean found = false;
                for (String keyword : techStackMap.keySet()) {
                    if (command.contains(keyword)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    detectedTechnologies.add(command.split(" ")[0]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return detectedTechnologies;
    }

    private static Map<String, String> getCurrentTechStackMap() {
        Map<String, String> techStackMap = new HashMap<>();
        techStackMap.put("mvn", "Maven");
        techStackMap.put("gradle", "Gradle");
        techStackMap.put("python", "Python");
        techStackMap.put("javac", "Java");
        techStackMap.put("java", "Java");
        techStackMap.put("npm", "Node.js/ReactJS");
        techStackMap.put("ng", "AngularJS");
        techStackMap.put("vue", "Vue.js");
        techStackMap.put("django", "Django");
        techStackMap.put("flask", "Flask");
        techStackMap.put("rails", "Ruby on Rails");
        techStackMap.put("spring-boot", "Spring Boot");
        techStackMap.put("dotnet", "ASP.NET");
        techStackMap.put("flutter", "Flutter");
        techStackMap.put("react-native", "React Native");
        techStackMap.put("ionic", "Ionic");
        return techStackMap;
    }

    private static void enhanceDetectTechStack(Set<String> missingTechnologies) {
        // Add missing technologies to the existing map
        Map<String, String> techStackMap = getCurrentTechStackMap();
        for (String tech : missingTechnologies) {
            // Assuming default naming conventions
            techStackMap.put(tech, tech.substring(0, 1).toUpperCase() + tech.substring(1));
        }

        // Print the enhanced map
        System.out.println("Enhanced Tech Stack Map: " + techStackMap);
    }

    private static Map<String, String> loadTechStackMap(String csvFilePath) throws IOException {
        Map<String, String> techStackMap = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length == 2) {
                    techStackMap.put(values[0], values[1]);
                }
            }
        }
        return techStackMap;
    }
}
