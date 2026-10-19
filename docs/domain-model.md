# Domain Model

## Overview
The domain model represents the pedagogical structure of Data Structures and Algorithms.

## Core Entities
- **Topic:** A conceptual area of study (e.g., Arrays, Hash Maps, Dynamic Programming).
- **Curriculum:** A Directed Acyclic Graph (DAG) connecting `Topic`s based on prerequisites.
- **Problem:** A specific algorithmic challenge belonging to one or more `Topic`s.

## Dependencies
The Domain Model is the core of the application. It has zero dependencies on external frameworks, databases, or AI libraries. It is purely written in standard Java 25.
