
# CSC360 Course Project

## Group 2

> A JavaFX desktop editor for creating, connecting, and managing graph nodes.

This repository contains the Group 2 course project for CSC360. It brings together
the project's requirements, interaction design, and software architecture in one
place as the editor is developed.

---

## Course Information

| Field | Details |
| --- | --- |
| Course code | CSC360 |
| Course name | Computer Graphics and Digital Image Processing |
| Session | Monsoon 2026 |
| Group | 2 |

## Group Members

| No. | Name | Enrolment Number |
| --- | --- | --- |
| 1 | Varun | AU2520215 |
| 2 | Satvik | AU2520039 |
| 3 | Garv | AU2520247 |
| 4 | Shambhavee | AU2500016 |

## About The Project

The project is a JavaFX desktop editor for building small directed graphs visually.
Users can place fixed-radius nodes on a drawing surface and create directed arrows
between them through mouse interactions. The design focuses on predictable
geometry, clear selection feedback, and reversible graph edits.

### Core Capabilities

- Create solid, fixed-radius nodes with a secondary mouse click.
- Prevent overlapping nodes through proximity checking.
- Select an existing node for one-shot automatic connection when a new node is created.
- Create directed arrows by dragging from one node to another.
- Delete nodes or arrows with a primary click.
- Remove all connected arrows automatically when a node is deleted.
- Undo and redo graph changes using linked-list history stacks.
- Keep rendering geometry and hit testing consistent so visible objects behave as expected.

## Project Documentation

The documentation is divided into two complementary views of the project:

| Document | Description |
| --- | --- |
| [System Design](docs/design.md) | Defines the requirements, user interactions, mouse and keyboard behaviour, geometry rules, history semantics, and acceptance criteria. |
| [System Architecture](docs/architecture.md) | Explains the proposed application structure, component responsibilities, data model, rendering approach, and implementation boundaries. |

## Repository Structure

```text
CSC360-Group2-Project/
├── README.md                 Project overview and team information
└── docs/
    ├── architecture.md      System structure and component design
    └── design.md            Requirements and interaction specification
```
