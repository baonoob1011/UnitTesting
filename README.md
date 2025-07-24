# 🧬 ADN Service Management - Unit Test Report

## 📌 Project Overview
This project focuses on unit testing for core modules of the **DNA Paternity Test Service Management System**, covering:

- 📅 Appointment booking
- 🏠 Home & 🏥 Center sample collection
- 📈 Result tracking
- 💳 Payment & billing

## 🔧 Technologies Used

| Tech        | Version  |
|-------------|----------|
| Java        | 17       |
| Spring Boot | Latest   |
| JUnit       | 5        |
| Mockito     | ✓        |
| Maven       | ≥ 3.8    |
| JaCoCo      | ✓        |

To run unit tests and generate coverage report:
.\mvnw clean test jacoco:report

to export json file : run this class [TestResultConverter.java](main/java/swp/project/adn_backend/TestResultConverter.java)
