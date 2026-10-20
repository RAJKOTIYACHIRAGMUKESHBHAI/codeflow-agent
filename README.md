# ⚡ CodeFlow Agent

**An AI-powered coding agent that plans, generates, tests, and iteratively improves code.**

CodeFlow Agent aims to streamline software development by turning coding requirements into a structured workflow—from planning and code generation to execution and testing.

Built with **Spring Boot, React, and NVIDIA Nemotron via Nebius Token Factory**, the project explores how AI agents can assist developers with coding tasks.

## ✨ Key Features

- 🧠 **AI-Powered Planning** — Break coding requirements into actionable plans.
- 💻 **Code Generation** — Generate code based on task requirements.
- 🧪 **Automated Testing** — Run project tests and inspect execution results.
- 🔄 **Iterative Improvement** — Use execution and testing feedback to improve generated code.
- 📋 **Task Management** — Create tasks and track their workflow and status.
- 🖥️ **Web Dashboard** — Interact with the agent through a React-based interface.

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot |
| Frontend | React, TypeScript, Vite |
| AI Integration | NVIDIA Nemotron via Nebius Token Factory |
| Build Tools | Maven, npm |
| Database | MySQL |

## 📁 Project Structure

```text
codeflow-agent/
├── backend/       # Spring Boot API and agent workflow
├── frontend/      # React + TypeScript user interface
└── README.md      # Project documentation
```

## 🚀 Getting Started

### Prerequisites

- Java 17
- Node.js and npm
- MySQL 8
- Nebius Token Factory API credentials

### 1. Clone the repository

```bash
git clone <YOUR_GITHUB_REPOSITORY_URL>
cd codeflow-agent
```

### 2. Configure the backend

Configure your database connection and AI provider credentials using environment variables or a local configuration file.

**Never commit API keys, database passwords, or other secrets to Git.**

### 3. Start the backend

Open a terminal:

```bash
cd backend
./mvnw spring-boot:run
```

On Windows PowerShell, use:

```powershell
.\mvnw.cmd spring-boot:run
```

### 4. Start the frontend

Open another terminal:

```bash
cd frontend
npm install
npm run dev
```

Open the local URL displayed by Vite in your terminal.

## 🗺️ Development Roadmap

- [x] Backend API foundation
- [x] AI planning and code generation components
- [x] Task management workflow
- [x] React frontend foundation
- [ ] Improve end-to-end agent workflow
- [ ] Strengthen generated-code validation and error recovery
- [ ] Expand project export and download capabilities

## 🎯 Vision

Build a developer-focused AI agent that helps transform software requirements into tested, understandable, and maintainable code.

## 👨‍💻 Project Status

**Actively evolving.** Features and workflows may change as development progresses.

---

*Built to explore the future of AI-assisted software engineering.*
