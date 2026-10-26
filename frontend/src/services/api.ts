export type TaskStatus =
  | 'CREATED'
  | 'PLANNING'
  | 'WAITING_FOR_APPROVAL'
  | 'APPROVED'
  | 'APPLYING_CHANGES'
  | 'TESTING'
  | 'COMPLETED'
  | 'FAILED';

export type ExecutionResult = {
  taskId?: string;
  status?: TaskStatus;
  project?: string;
  sourceFile?: string;
  testFile?: string;
  testOutput?: string;
  message?: string;
};

export type CodingTask = {
  id: string;
  requirement: string;
  status: TaskStatus;
  plan?: string | null;
  errorMessage?: string | null;
  createdAt?: string;
  updatedAt?: string;
  executionResult?: ExecutionResult | null;
};

export type HealthStatus = {
  status: string;
  application: string;
};

export const API_BASE_URL =
  ('http://localhost:8080').replace(/\/+$/, '');

const parseJson = async <T>(response: Response): Promise<T | null> => {
  const text = await response.text();

  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text) as T;
  } catch {
    return text as T;
  }
};

const request = async <T>(path: string, init?: RequestInit): Promise<T> => {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  });

  const payload = await parseJson<T>(response);

  if (!response.ok) {
    const details =
      payload && typeof payload === 'object' && 'error' in payload
        ? String((payload as { error?: string }).error)
        : payload && typeof payload === 'object' && 'message' in payload
          ? String((payload as { message?: string }).message)
          : payload && typeof payload === 'object' && 'detail' in payload
            ? String((payload as { detail?: string }).detail)
            : payload && typeof payload === 'object' && 'title' in payload
              ? String((payload as { title?: string }).title)
              : typeof payload === 'string' && payload.trim()
                ? payload
                : `Request failed (${response.status})`;

    throw new Error(details);
  }

  return payload as T;
};

export const createTask = async (requirement: string): Promise<CodingTask> =>
  request<CodingTask>('/api/tasks', {
    method: 'POST',
    body: JSON.stringify({ requirement }),
  });

export const getTasks = async (): Promise<CodingTask[]> =>
  request<CodingTask[]>('/api/tasks');

export const getTask = async (taskId: string): Promise<CodingTask> =>
  request<CodingTask>(`/api/tasks/${encodeURIComponent(taskId)}`);

export const generatePlan = async (
  taskId: string
): Promise<{ taskId: string; plan: string }> =>
  request<{ taskId: string; plan: string }>(`/api/tasks/${encodeURIComponent(taskId)}/plan`, {
    method: 'POST',
  });

export const approveTask = async (taskId: string): Promise<CodingTask> =>
  request<CodingTask>(`/api/tasks/${encodeURIComponent(taskId)}/approve`, {
    method: 'POST',
  });

export const executeTask = async (
  taskId: string
): Promise<ExecutionResult> =>
  request<ExecutionResult>(`/api/tasks/${encodeURIComponent(taskId)}/execute`, {
    method: 'POST',
  });

export const getHealth = async (): Promise<HealthStatus> =>
  request<HealthStatus>('/api/health');




