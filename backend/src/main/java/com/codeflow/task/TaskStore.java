package com.codeflow.task;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class TaskStore {

    private final CodingTaskRepository repository;

    public TaskStore(CodingTaskRepository repository) {
        this.repository = repository;
    }

    public CodingTask save(CodingTask task) {
        return repository.save(task);
    }

    public Optional<CodingTask> findById(String id) {
        return repository.findById(id);
    }

    public List<CodingTask> findAll() {
        return repository.findAll();
    }

    public boolean deleteById(String id) {
        if (!repository.existsById(id)) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }

    public int count() {
        return Math.toIntExact(repository.count());
    }
}
