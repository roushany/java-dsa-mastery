# Learner Model

## Overview
The Learner Model represents the state of a user's knowledge at a specific point in time.

## Core Entities
- **LearnerProfile:** The root entity for a user. Contains historical session data and current mastery maps.
- **TopicMastery:** Represents a learner's current standing in a specific `Topic`.

## Data Flow
The Learner Model is **read-only** for the `Learning Session` and `Question Engine`. It is **write-only** for the `Mastery Engine`.

No AI component is permitted to modify the Learner Model directly. It is entirely deterministic.
