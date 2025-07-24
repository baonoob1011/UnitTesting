# 🧬 ADN Service Management - Unit Test Report

## 📌 Project Overview

This project focuses on unit testing for core modules of the **DNA Paternity Test Service Management System**, including:

- 📅 Appointment booking  
- 🏠 Home & 🏥 Center sample collection  
- 📈 Result tracking  
- 💳 Payment & billing  

The goal is to ensure that all major business logic and service layers function correctly through comprehensive unit testing.

---

## 🔧 Technologies Used

| Technology     | Version   |
|----------------|-----------|
| Java           | 21        |
| Spring Boot    | Latest    |
| JUnit          | 5         |
| Mockito        | ✓         |
| Maven          | ≥ 3.8     |
| JaCoCo         | ✓         |
| Surefire Plugin| ✓         |

---

## ⚙️ Setup Instructions

### 1. Clone the Project

bash
git clone https://github.com/baonoob1011/UnitTesting.git
cd UnitTesting

2. Build the Project
Ensure Java 21 and Maven are installed:

bash
mvn clean install

✅ Running Unit Tests
To execute all unit tests:

bash
mvn test

📊 Test Reports
🔸 JaCoCo (Code Coverage Report)
Generates a visual report showing which parts of your codebase are covered by unit tests.

bash
mvn clean test jacoco:report

📁 Output:
target/site/jacoco/index.html

🔸 Maven Surefire Plugin (Test Execution Report)
Generates detailed results of test executions (pass/fail/skipped).

📁 Output:
target/site/surefire-report.html

These reports help evaluate test quality and ensure your codebase is well-tested.

🔄 Exporting Test Results to JSON
This project includes a utility class to convert test results into JSON format for integration with dashboards or external systems.

📄 Run this class:
TestResultConverter.java

🚀 Main Workflow (DNA Test Registration)
text
Step 1:  User logs in or registers an account
Step 2:  Select desired DNA test service (e.g., civil DNA test)
Step 3:  Choose sample collection method:
            • Option 1: At the clinic
                - Select branch
                - Choose time slot
            • Option 2: At home
                - Enter home address
                - Choose time slot
Step 4:  Select test package
Step 5:  Enter patient information
Step 6:  Choose payment method (VNPay)
Step 7:  Confirm and complete booking
Step 8:  (If home collection) Wait for staff confirmation & kit delivery
