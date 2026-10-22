import { useEffect, useMemo, useState, type FormEvent } from 'react';
import './App.css';
import {
  approveTask,
  createTask,
  executeTask,
  generatePlan,
  getHealth,
  getTask,
  getTasks,
  type CodingTask,
  type TaskStatus,
} from './services/api';

type View = 'overview' | 'new-task' | 'task-details';
type FeedMessage = {
  type: 'success' | 'error';
  text: string;
};
type TaskMap = Record<string, CodingTask>;

type StatCardProps = {
  label: string;
  value: number;
  accent: string;
  detail: string;
};

const navItems = [
  { id: 'overview', label: 'Overview', icon: '◫' },
  { id: 'new-task', label: 'New Task', icon: '＋' },
] as const;

const terminalStates = new Set<TaskStatus>(['COMPLETED', 'FAILED']);
const pollingStates = new Set<TaskStatus>(['PLANNING', 'APPLYING_CHANGES', 'TESTING']);

function App() {
  const [activeView, setActiveView] = useState<View>('overview');
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [tasksById, setTasksById] = useState<TaskMap>({});
  const [requirement, setRequirement] = useState('');
  const [formError, setFormError] = useState('');
  const [uiMessage, setUiMessage] = useState<FeedMessage | null>(null);
  const [isCreatingTask, setIsCreatingTask] = useState(false);
  const [pendingAction, setPendingAction] = useState<string | null>(null);
  const [apiHealth, setApiHealth] = useState('Checking…');

  const selectedTask = selectedTaskId ? tasksById[selectedTaskId] : undefined;
  const recentTasks = useMemo(
      () =>
          Object.values(tasksById)
              .sort((left, right) => (right.updatedAt ?? right.createdAt ?? '').localeCompare(left.updatedAt ?? left.createdAt ?? ''))
              .slice(0, 4),
      [tasksById]
  );

  const stats = useMemo(() => {
    const tasks = Object.values(tasksById);
    const total = tasks.length;
    const active = tasks.filter((task) => !terminalStates.has(task.status)).length;
    const completed = tasks.filter((task) => task.status === 'COMPLETED').length;
    const failed = tasks.filter((task) => task.status === 'FAILED').length;

    return { total, active, completed, failed };
  }, [tasksById]);

  const refreshTask = async (taskId: string): Promise<CodingTask | null> => {
    try {
      const task = await getTask(taskId);
      setTasksById((current) => ({
        ...current,
        [taskId]: {
          ...current[taskId],
          ...task,
          executionResult: task.executionResult ?? current[taskId]?.executionResult ?? null,
        },
      }));
      return task;
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to refresh task.';
      setUiMessage((current) => current?.type === 'success' ? current : { type: 'error', text: message });
      return null;
    }
  };

  const checkHealth = async () => {
    try {
      const health = await getHealth();
      const value = health?.status === 'UP' ? 'UP' : health?.status ?? 'Unknown';
      setApiHealth(value);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Health check failed';
      setApiHealth(message);
    }
  };

  useEffect(() => {
    void checkHealth();
    void getTasks()
      .then((tasks) => {
        setTasksById(Object.fromEntries(tasks.map((task) => [task.id, task] as const)));
      })
      .catch((error) => {
        const message = error instanceof Error ? error.message : 'Unable to load tasks.';
        setUiMessage({ type: 'error', text: message });
      });
  }, []);

  useEffect(() => {
    if (!selectedTaskId || !selectedTask || activeView !== 'task-details') {
      return;
    }

    if (terminalStates.has(selectedTask.status) || !pollingStates.has(selectedTask.status)) {
      return;
    }

    const timer = window.setInterval(() => {
      void refreshTask(selectedTaskId);
    }, 2500);

    return () => window.clearInterval(timer);
  }, [activeView, selectedTaskId, selectedTask?.status]);

  useEffect(() => {
    if (!selectedTaskId || activeView !== 'task-details') {
      return;
    }

    void refreshTask(selectedTaskId);
  }, [selectedTaskId, activeView]);

  const validateRequirement = (value: string) => {
    const trimmed = value.trim();

    if (!trimmed) {
      return 'Requirement must not be empty.';
    }

    if (trimmed.length > 5000) {
      return 'Requirement must not exceed 5000 characters.';
    }

    return '';
  };

  const handleCreateTask = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const trimmedRequirement = requirement.trim();
    const nextError = validateRequirement(trimmedRequirement);

    if (nextError) {
      setFormError(nextError);
      setUiMessage({ type: 'error', text: nextError });
      return;
    }

    setFormError('');
    setUiMessage(null);
    setIsCreatingTask(true);

    try {
      const task = await createTask(trimmedRequirement);
      setTasksById((current) => ({ ...current, [task.id]: task }));
      setSelectedTaskId(task.id);
      setActiveView('task-details');
      setRequirement('');
      setUiMessage({ type: 'success', text: `Task ${task.id} created successfully.` });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to create task.';
      setFormError(message);
      setUiMessage({ type: 'error', text: message });
    } finally {
      setIsCreatingTask(false);
    }
  };

  const handleGeneratePlan = async () => {
    if (!selectedTaskId || selectedTask?.status !== 'CREATED') {
      return;
    }

    setPendingAction('generate-plan');
    setUiMessage(null);

    try {
      const result = await generatePlan(selectedTaskId);
      setTasksById((current) => ({
        ...current,
        [selectedTaskId]: {
          ...current[selectedTaskId],
          ...current[selectedTaskId],
          plan: result.plan,
          status: 'WAITING_FOR_APPROVAL',
        },
      }));
      await refreshTask(selectedTaskId);
      setUiMessage({ type: 'success', text: 'Plan generated and ready for review.' });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to generate a plan.';
      setUiMessage({ type: 'error', text: message });
    } finally {
      setPendingAction(null);
    }
  };

  const handleApproveTask = async () => {
    if (!selectedTaskId || selectedTask?.status !== 'WAITING_FOR_APPROVAL') {
      return;
    }

    setPendingAction('approve-task');
    setUiMessage(null);

    try {
      await approveTask(selectedTaskId);
      await refreshTask(selectedTaskId);
      setUiMessage({ type: 'success', text: 'Plan approved. Execution can begin.' });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Approval failed.';
      setUiMessage({ type: 'error', text: message });
    } finally {
      setPendingAction(null);
    }
  };

  const handleExecuteTask = async () => {
    if (!selectedTaskId || selectedTask?.status !== 'APPROVED') {
      return;
    }

    setPendingAction('execute-task');
    setUiMessage(null);

    try {
      const result = await executeTask(selectedTaskId);
      setTasksById((current) => ({
        ...current,
        [selectedTaskId]: {
          ...current[selectedTaskId],
          ...current[selectedTaskId],
          executionResult: result,
          status: result.status ?? current[selectedTaskId]?.status ?? 'APPROVED',
        },
      }));
      await refreshTask(selectedTaskId);
      setUiMessage({
        type: 'success',
        text: result.message ?? 'Task executed successfully.',
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Execution failed.';
      setUiMessage({ type: 'error', text: message });
    } finally {
      setPendingAction(null);
    }
  };

  const openTaskDetails = async (taskId: string) => {
    setSelectedTaskId(taskId);
    setActiveView('task-details');
    await refreshTask(taskId);
  };

  return (
      <div className="app-shell">
        <aside className="sidebar">
          <div className="brand-block">
            <div className="brand-mark">C</div>
            <div>
              <p className="eyebrow">AI coding agent</p>
              <h1>CodeFlow</h1>
            </div>
          </div>

          <nav className="sidebar-nav" aria-label="Sidebar navigation">
            {navItems.map((item) => (
                <button
                    key={item.id}
                    type="button"
                    className={item.id === activeView ? 'nav-item is-active' : 'nav-item'}
                    onClick={() => {
                      if (item.id === 'overview') {
                        setActiveView('overview');
                        return;
                      }
                      setActiveView('new-task');
                    }}
                >
                  <span aria-hidden="true">{item.icon}</span>
                  {item.label}
                </button>
            ))}

            {selectedTaskId ? (
                <button
                    type="button"
                    className={activeView === 'task-details' ? 'nav-item is-active' : 'nav-item'}
                    onClick={() => setActiveView('task-details')}
                >
                  <span aria-hidden="true">◌</span>
                  Task details
                </button>
            ) : null}
          </nav>

          <div className="sidebar-card">
            <p className="mini-label">System</p>
            <strong>Agent status</strong>
            <span className="online-indicator">{apiHealth}</span>
          </div>
        </aside>

        <main className="content-panel">
          <header className="topbar">
            <div>
              <p className="eyebrow">Workspace</p>
              <h2>
                {activeView === 'overview'
                    ? 'Overview Dashboard'
                    : activeView === 'new-task'
                        ? 'New Task'
                        : selectedTaskId
                            ? `Task ${selectedTaskId}`
                            : 'Task Details'}
              </h2>
            </div>
            <div className="header-actions">
              <button type="button" className="ghost-button" onClick={() => void checkHealth()}>
                API health
              </button>
              <button type="button" className="primary-button" onClick={() => setActiveView('new-task')}>
                + Create task
              </button>
            </div>
          </header>

          {activeView === 'overview' ? (
              <>
                <section className="stats-grid" aria-label="Overview statistics">
                  <StatCard label="Total tasks" value={stats.total} accent="purple" detail="Created in session" />
                  <StatCard label="Active" value={stats.active} accent="amber" detail="Planning or execution" onClick={() => { const task = Object.values(tasksById).find((item) => !terminalStates.has(item.status)); if (task) void openTaskDetails(task.id); else setUiMessage({ type: "error", text: "No active tasks found." }); }} />
                  <StatCard label="Completed" value={stats.completed} accent="green" detail="Successful outcomes" />
                  <StatCard label="Failed" value={stats.failed} accent="red" detail="Needs attention" />
                </section>

                <section className="panel">
                  <div className="panel-header">
                    <div>
                      <p className="mini-label">Recent work</p>
                      <h3>Recent tasks</h3>
                    </div>
                    <button type="button" className="ghost-button small" onClick={() => setActiveView('new-task')}>
                      Add task
                    </button>
                  </div>

                  {recentTasks.length === 0 ? (
                      <div className="empty-state">
                        <p>No tasks have been created yet.</p>
                        <button type="button" className="primary-button" onClick={() => setActiveView('new-task')}>
                          Create your first task
                        </button>
                      </div>
                  ) : (
                      <div className="task-table">
                        <div className="task-table-header">
                          <span>Requirement</span>
                          <span>Status</span>
                          <span>Updated</span>
                        </div>
                        {recentTasks.map((task) => (
                            <button type="button" className="task-row task-row-button" key={task.id} onClick={() => void openTaskDetails(task.id)}>
                              <span className="requirement-preview">{task.requirement}</span>
                              <StatusBadge status={task.status} />
                              <span className="timestamp">
                        {task.updatedAt ? new Date(task.updatedAt).toLocaleDateString() : '—'}
                      </span>
                            </button>
                        ))}
                      </div>
                  )}
                </section>
              </>
          ) : activeView === 'new-task' ? (
              <section className="panel form-panel">
                <div className="panel-header">
                  <div>
                    <p className="mini-label">Submission</p>
                    <h3>Create a new task</h3>
                  </div>
                </div>

                <form onSubmit={handleCreateTask} className="task-form">
                  <label htmlFor="requirement" className="field-label">
                    Requirement
                  </label>
                  <textarea
                      id="requirement"
                      name="requirement"
                      value={requirement}
                      onChange={(event) => {
                        setRequirement(event.target.value);
                        if (formError) {
                          setFormError('');
                        }
                      }}
                      placeholder="Describe the task to be planned and executed by the agent..."
                      rows={10}
                      maxLength={5000}
                      disabled={isCreatingTask}
                  />

                  <div className="form-meta">
                    <span>{requirement.trim().length} characters</span>
                    <span>Max 5000</span>
                  </div>

                  {formError ? <div className="status-banner error">{formError}</div> : null}
                  {uiMessage && uiMessage.type === 'success' ? (
                      <div className="status-banner success">{uiMessage.text}</div>
                  ) : null}

                  <div className="form-actions">
                    <button type="button" className="ghost-button" onClick={() => setActiveView('overview')} disabled={isCreatingTask}>
                      Cancel
                    </button>
                    <button type="submit" className="primary-button" disabled={isCreatingTask}>
                      {isCreatingTask ? 'Creating…' : 'Create task'}
                    </button>
                  </div>
                </form>
              </section>
          ) : (
              <section className="panel task-detail-panel">
                {selectedTask ? (
                    <>
                      <div className="panel-header task-detail-header">
                        <div>
                          <p className="mini-label">Execution workflow</p>
                          <h3>{selectedTask.requirement}</h3>
                        </div>
                        <StatusBadge status={selectedTask.status} />
                      </div>

                      {uiMessage ? (
                          <div className={uiMessage.type === 'success' ? 'status-banner success' : 'status-banner error'}>
                            {uiMessage.text}
                          </div>
                      ) : null}

                      <div className="detail-grid">
                        <article className="detail-card">
                          <h4>Task details</h4>
                          <dl>
                            <div>
                              <dt>Task ID</dt>
                              <dd>{selectedTask.id}</dd>
                            </div>
                            <div>
                              <dt>Status</dt>
                              <dd>{selectedTask.status}</dd>
                            </div>
                            <div>
                              <dt>Requirement</dt>
                              <dd>{selectedTask.requirement}</dd>
                            </div>
                          </dl>
                        </article>

                        <article className="detail-card">
                          <h4>Workflow</h4>
                          <div className="action-stack">
                            <button
                                type="button"
                                className="primary-button"
                                onClick={() => void handleGeneratePlan()}
                                disabled={selectedTask.status !== 'CREATED' || pendingAction !== null}
                            >
                              {pendingAction === 'generate-plan' ? 'Generating…' : 'Generate Plan'}
                            </button>
                            <button
                                type="button"
                                className="primary-button"
                                onClick={() => void handleApproveTask()}
                                disabled={selectedTask.status !== 'WAITING_FOR_APPROVAL' || pendingAction !== null}
                            >
                              {pendingAction === 'approve-task' ? 'Approving…' : 'Approve Plan'}
                            </button>
                            <button
                                type="button"
                                className="primary-button"
                                onClick={() => void handleExecuteTask()}
                                disabled={selectedTask.status !== 'APPROVED' || pendingAction !== null}
                            >
                              {pendingAction === 'execute-task' ? 'Executing…' : 'Execute Task'}
                            </button>
                            <button
                                type="button"
                                className="ghost-button"
                                onClick={() => void refreshTask(selectedTask.id)}
                                disabled={pendingAction !== null}
                            >
                              Refresh status
                            </button>
                          </div>
                        </article>
                      </div>

                      <div className="detail-grid detail-grid-bottom">
                        <article className="detail-card">
                          <h4>AI plan</h4>
                          <div className="text-block">{selectedTask.plan || 'No plan generated yet.'}</div>
                        </article>

                        <article className="detail-card">
                          <h4>Errors</h4>
                          <div className="text-block error-text">
                            {selectedTask.errorMessage || 'No errors reported.'}
                          </div>
                        </article>
                      </div>

                      <article className="detail-card execution-card">
                        <h4>Execution result</h4>
                        {selectedTask.executionResult ? (
                            <dl>
                              <div>
                                <dt>Message</dt>
                                <dd>{selectedTask.executionResult.message ?? 'No message'}</dd>
                              </div>
                              <div>
                                <dt>Project</dt>
                                <dd>{selectedTask.executionResult.project ?? '—'}</dd>
                              </div>
                              <div>
                                <dt>Source file</dt>
                                <dd>{selectedTask.executionResult.sourceFile ?? '—'}</dd>
                              </div>
                              <div>
                                <dt>Test file</dt>
                                <dd>{selectedTask.executionResult.testFile ?? '—'}</dd>
                              </div>
                              <div>
                                <dt>Test output</dt>
                                <dd className="mono-output">{selectedTask.executionResult.testOutput ?? 'No test output yet.'}</dd>
                              </div>
                            </dl>
                        ) : (
                            <div className="text-block">No execution result available yet.</div>
                        )}
                      </article>
                    </>
                ) : (
                    <div className="empty-state">
                      <p>Select a task to view details.</p>
                    </div>
                )}
              </section>
          )}
        </main>
      </div>
  );
}

function StatusBadge({ status }: { status: TaskStatus }) {
  const label = status.replace(/_/g, ' ');
  const className = `status-badge ${status.toLowerCase()}`;

  return <span className={className}>{label}</span>;
}

function StatCard({ label, value, accent, detail, onClick }: StatCardProps & { onClick?: () => void }) {
  return (
      <article
        className={`stat-card ${accent}`}
        onClick={onClick}
        role={onClick ? "button" : undefined}
        tabIndex={onClick ? 0 : undefined}
        onKeyDown={onClick ? (event) => {
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            onClick();
          }
        } : undefined}
        style={onClick ? { cursor: "pointer" } : undefined}
      >
        <p className="mini-label">{label}</p>
        <strong>{value}</strong>
        <span>{detail}</span>
      </article>
  );
}

export default App;