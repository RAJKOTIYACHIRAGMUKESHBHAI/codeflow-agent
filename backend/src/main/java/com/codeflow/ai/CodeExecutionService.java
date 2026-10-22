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
            Pattern.compile("(?m)\\bpublic\\s+(?:(?:abstract|final|sealed|non-sealed|strictfp)\\s+)*(?:class|record|enum|interface)\\s+([A-Za-z_$][\\w$]*)");

    private static final String POM_CONTENT = """
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.codeflow.generated</groupId>
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

        try {
            // Start from a clean, task-specific directory on every execution attempt.
            workspaceFileService.resetTaskWorkspace(taskId);
            Path project = workspaceFileService.getTaskDirectory(taskId);

            taskService.updateTaskStatus(taskId, TaskStatus.APPLYING_CHANGES);
            workspaceFileService.writeFile("tasks/" + taskId + "/pom.xml", POM_CONTENT);

            String generatedCode = codeGenerationService.generateCode(task);
            String generatedTests =
                    codeGenerationService.generateTests(task, generatedCode);

            String sourcePath = sourcePathFor(generatedCode);
            String testPath = testPathFor(generatedTests);

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

        Matcher typeMatcher = PUBLIC_TYPE_PATTERN.matcher(source);
        if (!typeMatcher.find()) {
            throw new IllegalStateException(
                    description + " must contain a public Java type declaration");
        }

        String className = typeMatcher.group(1);
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(source);
        String packagePath = packageMatcher.find()
                ? packageMatcher.group(1).replace('.', '/') + "/"
                : "";

        return "src/" + sourceSet + "/java/" + packagePath + className + ".java";
    }
}
