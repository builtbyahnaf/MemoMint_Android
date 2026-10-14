# MemoMint 🌱
### Your personal memory, freshly recalled.

**MemoMint** is an offline-first personal memory engine for Android, designed to help you remember the little things that make up your everyday life.

Ever forgotten where you put something, which document contained that one important detail, or what you meant to do later? MemoMint aims to bring those scattered pieces of information together in one private, searchable place.

Instead of requiring you to organize everything perfectly, MemoMint is being designed to help you **capture information naturally and recall it later—even when you don't remember the exact words you originally used.**

> [!WARNING]
> **🚧 Work in Progress — Under Active Development**
>
> MemoMint is currently in its early development stage. The project is a work in progress, and many of the features, capabilities, and ideas described in this README are planned rather than implemented. Some features may change, be delayed, or never make it into the final application.
>
> This repository does not represent a feature-complete or production-ready release.

---

## ✨ The Idea

MemoMint aims to be more than another notes app.

It's envisioned as a personal memory system that connects the information you record, the files you keep, the things you own, and the reminders you set—making them easier to find when you need them.

Imagine being able to search for:

- *"Where did I put my spare earphones?"*
- *"Find that document where I compared guitar prices."*
- *"What was that tool I wanted to buy a few weeks ago?"*
- *"Remind me to check that thing I stored in the drawer."*

You shouldn't have to remember the exact filename, keyword, or category. You should be able to describe what you remember and let the system search for the relevant information.

**The goal is simple: spend less time trying to remember where information is, and more time using it.**

## 🎯 Planned Features

The following features describe the project's intended direction. They are **not a guarantee of current availability**.

- **🧠 Personal Memory Capture** — Record everyday information, thoughts, notes, and useful details.
- **🔎 Contextual Recall** — Search using natural descriptions, related concepts, and supported synonyms rather than relying solely on exact keyword matches.
- **📦 Belongings Tracker** — Record where physical items were last placed and when their locations were confirmed.
- **📄 Document Context** — Index supported documents from user-selected locations to help retrieve information by content, not just filename.
- **🕸️ Connected Memories** — Link related notes, documents, objects, and events into a navigable web of personal information.
- **⏰ Contextual Reminders** — Associate reminders with specific memories and things you need to revisit.
- **🎙️ Low-Friction Capture** — Explore voice input, Android share-sheet integration, and convenient capture shortcuts.
- **🔐 Local-First Privacy** — Keep core functionality independent of cloud services, with local storage and processing as the intended default.
- **🗂️ User-Controlled Data** — Inspect, edit, correct, export, and delete recorded information.

Feature scope and implementation details may evolve as development progresses.

## 🔒 Privacy by Design

Personal memories can contain sensitive details. MemoMint is being designed with privacy as a foundational principle rather than an optional feature.

The intended design principles include:

- No mandatory account or cloud service for core functionality.
- Local storage and processing by default.
- No required online AI service.
- No unnecessary transmission of personal information.
- Transparent file access and background processing.
- User control over stored memories and indexed content.
- Honest handling of uncertainty and outdated information.

These are **design goals, not claims about the current implementation**. The actual privacy guarantees will depend on the features implemented, tested, and documented in the application.

## 🛠️ Technical Direction

The initial implementation is planned around a native Android application with a modular, local-first architecture.

| Component | Intended approach |
|---|---|
| Platform | Android |
| Primary language | Java |
| Local database | SQLite |
| Text retrieval | SQLite full-text indexing and additional ranking methods |
| Core operation | Offline-first |
| AI/ML | Optional future exploration; not a prerequisite for the initial implementation |

The architecture may evolve as the project develops. Technical choices will prioritize reliability, maintainability, understandable behavior, and measurable retrieval quality over unnecessary complexity.

## 🧭 Development Status

MemoMint is still taking shape. Development will proceed incrementally, with an emphasis on getting the foundations right before expanding into more ambitious functionality.

Current priorities are expected to include:

- Establishing the application architecture.
- Designing a reliable personal-memory data model.
- Implementing local memory creation and management.
- Building and evaluating the retrieval engine.
- Establishing privacy, data integrity, and testing practices.

Not every screen, workflow, or feature described above exists yet. Please check the repository's issues, milestones, and release notes for the latest implementation status.

## 🤝 Community and Forks

MemoMint is intended to be a project that developers can explore, learn from, fork, and adapt into their own noncommercial variations.

The project uses a **source-available, noncommercial licensing model**. You may use, modify, and redistribute the code only within the permissions and conditions of the applicable license.

Commercial use requires separate prior permission from the relevant rights holder. See [`LICENSE`](LICENSE) for the governing terms.

## 🏷️ Name and Branding

**MemoMint** is the project name. The name, logo, and official branding are separate from the software's source-code license.

Permission to reuse the code under its license does not automatically grant permission to present a fork as an official MemoMint release or to use the MemoMint name and branding as another product's identity.

This notice communicates the project's branding policy; it does not, by itself, establish registered trademark rights.

## 🌱 Project Philosophy

MemoMint is built around a few guiding ideas:

- **Capture effortlessly.** Don't make users organize every detail perfectly.
- **Recall contextually.** Help users find information even when their recollection is incomplete.
- **Verify honestly.** Distinguish recorded facts from assumptions, and don't pretend outdated information is current.
- **Keep it local.** Make privacy and offline capability core design considerations.
- **Build deliberately.** Prefer tested, useful functionality over ambitious features that cannot yet be relied upon.

## 📌 A Note on Expectations

MemoMint is an evolving project, not a finished product. Its roadmap reflects a vision that will be implemented and evaluated over time.

If you're exploring the repository, treat proposed functionality as a direction for development—not as a promise that the application can already deliver it.

## 📜 License

MemoMint is intended to use the **PolyForm Noncommercial License 1.0.0**.

Please read the [`LICENSE`](LICENSE) file for the exact terms. Commercial use requires separate authorization under an appropriate agreement.

---

**MemoMint — Capture effortlessly. Recall contextually. Verify honestly. Keep it local.**