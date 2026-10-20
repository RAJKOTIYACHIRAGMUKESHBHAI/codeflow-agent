package com.codeflow.ai;

import com.codeflow.task.CodingTask;
import com.codeflow.task.TaskService;
import com.codeflow.task.TaskStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;

@Service
public class CodeExecutionService {

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
            throw new IllegalStateException(
                    "Task must be approved before execution");
        }
String pomContent = """
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>
            <groupId>com.example</groupId>
            <artifactId>generated-task</artifactId>
            <version>1.0-SNAPSHOT</version>
            <properties>
                <maven.compiler.release>17</maven.compiler.release>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                <junit.version>5.11.4</junit.version>
            </properties>
            <dependencies>
                <dependency>
                    <groupId>org.junit.jupiter</groupId>
                    <artifactId>junit-jupiter</artifactId>
                    <version>${junit.version}</version>
                    <scope>test</scope>
                </dependency>
            </dependencies>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>3.13.0</version>
                    </plugin>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-plugin</artifactId>
                        <version>3.5.2</version>
                    </plugin>
                </plugins>
            </build>
        </project>
        """;

workspaceFileService.writeFile(
        "tasks/" + taskId + "/pom.xml", pomContent);

        taskService.updateTaskStatus(
                taskId, TaskStatus.APPLYING_CHANGES);

        try {
            String generatedCode =
                    codeGenerationService.generateCode(task);

            String generatedTests =
                    codeGenerationService.generateTests(task, generatedCode);

            Path project = Path.of(
                    "generated-workspace", "tasks", taskId)
                    .toAbsolutePath().normalize();

            // Save source and test files separately in the task project.
            String sourcePath = "src/main/java/com/example/calculator/Calculator.java";
            String testPath = "src/test/java/com/example/calculator/CalculatorTest.java";

            workspaceFileService.writeFile(
                    "tasks/" + taskId + "/" + sourcePath, generatedCode);

            workspaceFileService.writeFile(
                    "tasks/" + taskId + "/" + testPath, generatedTests);

            taskService.updateTaskStatus(taskId, TaskStatus.TESTING);

            String testOutput = mavenTestRunner.runTests(project);

            taskService.updateTaskStatus(taskId, TaskStatus.COMPLETED);

            return Map.of(
                    "taskId", taskId,
                    "status", taskService.getTask(taskId).getStatus().name(),
                    "project", project.toString(),
                    "sourceFile", project.resolve(sourcePath).toString(),
                    "testFile", project.resolve(testPath).toString(),
                    "testOutput", testOutput,
                    "message", "Generated project tests passed"
            );

        } catch (Exception ex) {
            taskService.updateErrorMessage(taskId, ex.getMessage());
            if (task.getStatus() != TaskStatus.FAILED
                    && task.getStatus() != TaskStatus.COMPLETED) {
                taskService.updateTaskStatus(taskId, TaskStatus.FAILED);
            }
            throw ex;
        }
    }
}