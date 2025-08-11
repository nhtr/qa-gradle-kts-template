# QA-friendly Gradle (Kotlin DSL) template

Features:

- JUnit 5 (parameterized tests) reading data from JSON resources
- Allure JUnit5 integration (results -> `build/allure-results`, report -> `build/reports/allure-report`)
- JaCoCo coverage with thresholds (90% instruction, 80% branch)
- Custom CSV report for QA at `build/qa-report.csv`

## Commands

```bash
./gradlew test              # run tests, produce allure-results + qa-report.csv
./gradlew jacocoTestReport  # generate HTML coverage under build/reports/jacoco/test/html
./gradlew check             # runs coverage verification + report
./gradlew allureReport      # generate Allure HTML under build/reports/allure-report
# ./gradlew allureServe     # starts a temp web-server for Allure (optional)
```

## Notes

- JDK 17+ required.
- If Allure CLI is installed, you can also run: `allure serve build/allure-results`.
- Adjust thresholds in `build.gradle.kts` (task `jacocoTestCoverageVerification`) if needed.
- CSV is saved to `build/qa-report.csv` and includes ID, input, expected, actual, status, and error message (if any).
