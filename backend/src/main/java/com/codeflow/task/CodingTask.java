package com.codeflow.task;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coding_tasks")
public class CodingTask {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, length = 5000)
    private String requirement;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TaskStatus status;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CodingTask() {
    }

    public CodingTask(String requirement) {
        this.id = UUID.randomUUID().toString();
        this.requirement = requirement;
        this.status = TaskStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public String getRequirement() {
        return requirement;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateStatus(TaskStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException(
                    "Task status must not be null"
            );
        }

        boolean allowed = switch (this.status) {
            case CREATED ->
                    newStatus == TaskStatus.PLANNING
                            || newStatus == TaskStatus.FAILED;
            case PLANNING ->
                    newStatus == TaskStatus.WAITING_FOR_APPROVAL
                            || newStatus == TaskStatus.FAILED;
            case WAITING_FOR_APPROVAL ->
                    newStatus == TaskStatus.APPROVED
                            || newStatus == TaskStatus.FAILED;
            case APPROVED ->
                    newStatus == TaskStatus.APPLYING_CHANGES
                            || newStatus == TaskStatus.FAILED;
            case APPLYING_CHANGES ->
                    newStatus == TaskStatus.TESTING
                            || newStatus == TaskStatus.FAILED;
            case TESTING ->
                    newStatus == TaskStatus.COMPLETED
                            || newStatus == TaskStatus.FAILED;
            case COMPLETED, FAILED -> false;
        };

        if (!allowed) {
            throw new IllegalStateException(
                    "Invalid task status transition: "
                            + this.status + " -> " + newStatus
            );
        }

        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    @Override
    public String toString() {
        return "CodingTask{" +
                "id='" + id + '\'' +
                ", requirement='" + requirement + '\'' +
                ", status=" + status +
                (errorMessage != null
                        ? ", errorMessage='" + errorMessage + '\''
                        : "") +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
