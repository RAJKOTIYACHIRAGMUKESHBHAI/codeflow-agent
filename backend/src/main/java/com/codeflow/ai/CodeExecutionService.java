package com.codeflow.ai;

import com.codeflow.task.CodingTask;
import com.codeflow.task.TaskService;
import com.codeflow.task.TaskStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CodeExecutionService {

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;");
    private static final Pattern PUBLIC_TYPE_PATTERN =
            Pattern.compile("(?m)\\b(?:(?:public|protected|private|abstract|final|sealed|non-sealed|strictfp)\\s+)*(?:class|record|enum|interface)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern TEST_TYPE_PATTERN =
            Pattern.compile("(?m)\\b(?:(?:public|protected|private|abstract|final|strictfp)\\s+)*(?:class|record|enum|interface)\\s+([A-Za-z_$][\\w$]*)");

    // Updated POM_CONTENT with Spring Boot, Thymeleaf, JPA & H2 Database Dependencies
    private static final String POM_CONTENT = """
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.0</version>
                    <relativePath/>
                </parent>
                <groupId>com.codeflow.generated</groupId>
                <artifactId>generated-task</artifactId>
                <version>1.0-SNAPSHOT</version>
                <properties>
                    <java.version>17</java.version>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-thymeleaf</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-data-jpa</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>com.h2database</groupId>
                        <artifactId>h2</artifactId>
                        <scope>runtime</scope>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-test</artifactId>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-maven-plugin</artifactId>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """;

    private final TaskService taskService;
    private final AiCodeGenerationService codeGenerationService;
    private final WorkspaceFileService workspaceFileService;
    private final MavenTestRunner mavenTestRunner;

    public CodeExecutionService(
            TaskService taskService,
            AiCodeGenerationService codeGenerationService,
            WorkspaceFileService workspaceFileService,
            MavenTestRunner mavenTestRunner) {
        this.taskService = taskService;
        this.codeGenerationService = codeGenerationService;
        this.workspaceFileService = workspaceFileService;
        this.mavenTestRunner = mavenTestRunner;
    }

    public Map<String, String> executeApprovedTask(String taskId)
            throws Exception {

        CodingTask task = taskService.getTask(taskId);
        if (task.getStatus() != TaskStatus.APPROVED) {
            throw new IllegalStateException("Task must be approved before execution");
        }

        try {
            workspaceFileService.resetTaskWorkspace(taskId);
            Path project = workspaceFileService.getTaskDirectory(taskId);

            taskService.updateTaskStatus(taskId, TaskStatus.APPLYING_CHANGES);
            workspaceFileService.writeFile("tasks/" + taskId + "/pom.xml", POM_CONTENT);

            if (task.getPlan() != null && !task.getPlan().isBlank()) {
                workspaceFileService.extractAndWriteAllPlanFiles(taskId, task.getPlan());
            }

            AiCodeGenerationService.TaskType taskType = codeGenerationService.determineTaskType(task);

            String sourcePath = "";
            String testPath = "";
            String testOutput = "";

            if (taskType == AiCodeGenerationService.TaskType.PURE_UI) {
                String generatedCode = codeGenerationService.generateCode(task);
                sourcePath = uiSourcePathFor(generatedCode);
                workspaceFileService.writeFile("tasks/" + taskId + "/" + sourcePath, generatedCode);

                taskService.updateTaskStatus(taskId, TaskStatus.TESTING);
                testOutput = "UI Changes Applied Successfully. Maven build skipped.";

            } else if (taskType == AiCodeGenerationService.TaskType.FULL_STACK) {
                String generatedCode = codeGenerationService.generateCode(task);
                String generatedTests = codeGenerationService.generateTests(task, generatedCode);
                String generatedHtml = codeGenerationService.generateHtmlTemplate(task);

                sourcePath = sourcePathFor(generatedCode);
                testPath = testPathFor(generatedTests);
                String htmlPath = "src/main/resources/templates/profile.html";

                workspaceFileService.writeFile("tasks/" + taskId + "/" + sourcePath, generatedCode);
                workspaceFileService.writeFile("tasks/" + taskId + "/" + testPath, generatedTests);
                workspaceFileService.writeFile("tasks/" + taskId + "/" + htmlPath, generatedHtml);

                taskService.updateTaskStatus(taskId, TaskStatus.TESTING);
                testOutput = mavenTestRunner.runTests(project);

            } else {
                String generatedCode = codeGenerationService.generateCode(task);
                String generatedTests = codeGenerationService.generateTests(task, generatedCode);

                sourcePath = sourcePathFor(generatedCode);
                testPath = testPathFor(generatedTests);

                workspaceFileService.writeFile("tasks/" + taskId + "/" + sourcePath, generatedCode);
                workspaceFileService.writeFile("tasks/" + taskId + "/" + testPath, generatedTests);

                taskService.updateTaskStatus(taskId, TaskStatus.TESTING);
                testOutput = mavenTestRunner.runTests(project);
            }

            taskService.updateTaskStatus(taskId, TaskStatus.COMPLETED);

            return Map.of(
                    "taskId", taskId,
                    "status", taskService.getTask(taskId).getStatus().name(),
                    "project", project.toString(),
                    "sourceFile", project.resolve(sourcePath).toString(),
                    "testFile", taskType == AiCodeGenerationService.TaskType.PURE_UI ? "N/A" : project.resolve(testPath).toString(),
                    "testOutput", testOutput,
                    "message", taskType == AiCodeGenerationService.TaskType.PURE_UI ? "UI task executed successfully" : "Generated project tests passed"
            );
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName() : ex.getMessage();
            try {
                taskService.updateErrorMessage(taskId, message);
                CodingTask current = taskService.getTask(taskId);
                if (current.getStatus() != TaskStatus.FAILED
                        && current.getStatus() != TaskStatus.COMPLETED) {
                    taskService.updateTaskStatus(taskId, TaskStatus.FAILED);
                }
            } catch (Exception statusError) {
                ex.addSuppressed(statusError);
            }
            throw ex;
        }
    }

    private String uiSourcePathFor(String source) {
        if (source.contains("<html") || source.contains("<!DOCTYPE html") || source.contains("<footer") || source.contains("<div")) {
            return "src/main/resources/templates/dashboard.html";
        } else if (source.contains("{") && source.contains("}")) {
            return "src/main/resources/static/css/style.css";
        }
        return "src/main/resources/templates/index.html";
    }

    private String sourcePathFor(String source) {
        return javaSourcePath(source, "Generated implementation", "main");
    }

    private String testPathFor(String source) {
        return javaSourcePath(source, "Generated test class", "test");
    }

    private String javaSourcePath(String source, String description, String sourceSet) {
        if (source == null || source.isBlank()) {
            throw new IllegalStateException(description + " is empty");
        }

        Pattern typePattern = "test".equals(sourceSet)
                ? TEST_TYPE_PATTERN
                : PUBLIC_TYPE_PATTERN;

        Matcher typeMatcher = typePattern.matcher(source);
        if (!typeMatcher.find()) {
            throw new IllegalStateException(description + " must contain a valid Java type declaration");
        }

        String className = typeMatcher.group(1);
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(source);
        String packagePath = packageMatcher.find()
                ? packageMatcher.group(1).replace('.', '/') + "/"
                : "";

        return "src/" + sourceSet + "/java/" + packagePath + className + ".java";
    }
}