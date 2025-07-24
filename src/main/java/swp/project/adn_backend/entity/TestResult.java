package swp.project.adn_backend.entity;


public class TestResult {
    private String suiteName;
    private String testName;
    private String status;
    private double duration;

    // Constructors
    public TestResult() {}

    public TestResult(String suiteName, String testName, String status, double duration) {
        this.suiteName = suiteName;
        this.testName = testName;
        this.status = status;
        this.duration = duration;
    }

    public String getSuiteName() {
        return suiteName;
    }

    public void setSuiteName(String suiteName) {
        this.suiteName = suiteName;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }
// Getters and Setters
    // ...
}
