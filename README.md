Jenkins to Azure DevOps Migration
This project aims to facilitate the migration of Jenkins pipelines to Azure DevOps (ADO) by analyzing Jenkins configurations, identifying technology stacks, and providing detailed reports. The tool, developed in Java, automates several aspects of the migration process, ensuring minimal manual intervention and accurate, consistent results.

Table of Contents
Project Overview

Features

Getting Started

Prerequisites

Installation

Configuration

Usage

Reports

Contributing

License

Project Overview
This project facilitates the migration of Jenkins pipelines to Azure DevOps (ADO) by automating the analysis of Jenkins configurations, identification of technology stacks, and generation of detailed reports.

Features
Analyze Jenkins XML: Parses Jenkins XML configuration files to check for pipeline scripts.

Locate and Download Scripts: Retrieves missing scripts from Git repositories using <scm> tags.

Update Jenkins XML: Updates Jenkins XML configurations with new scripts.

Tech Stack Analysis: Identifies technologies used in the Jenkins pipelines.

Validation: Validates identified technologies against a predefined list.

Report Generation: Generates detailed reports summarizing the analysis and validation results.

Azure DevOps Migration: Supports migration of both cloud and on-premise Jenkins projects to ADO.

Encryption and Security: Implements GPG encryption and Crypto as a Service (CaaS) for enhanced data security.

Automation: Automates data migration and functional testing.

Getting Started
Prerequisites
Java 8 or higher

Git

Maven

Installation
Clone the repository:

sh
git clone https://github.com/ganoor/Jenkins2Ado.git
cd Jenkins2Ado
Build the project using Maven:

sh
mvn clean install
Configuration
Configure the config.properties file with the following placeholders:

properties
JENKINS_URL=http://example.com:8080/
USERNAME=your_username
TOKEN=your_token
OUTPUT_DIRECTORY=your_output_directory/
REPORT_FILE=your_report_file.xlsx
GITHUB_TOKEN=your_github_token
GITHUB_OWNER=your_github_owner
FETCH_JENKINS_API=true
TECHSTACK_CSV=src/main/resources/tech_stack.csv
Usage
Run the application:

sh
java -jar target/jenkins2ado.jar
Follow the prompts to analyze Jenkins pipelines and generate reports.

Reports
The program generates detailed reports that include the following information:

Pipeline name

Tech stack

Plugins used

Plugin count

Operating system/node

Stages

Build status (last build, last stable build, last successful build, etc.)

Contributing
Contributions are welcome! Please fork the repository and create a pull request with your changes. Ensure that your code adheres to the coding standards and includes appropriate tests.

License
This project is licensed under the MIT License - see the LICENSE file for details.
