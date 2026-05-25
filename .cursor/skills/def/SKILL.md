---
name: def
description: >-
  Routes all code changes to the default (ordinary) Zero visual edition at repo root.
  Use when the user invokes /def, default edition, or ordinary/обычная version work.
disable-model-invocation: true
---

# /def — обычная версия

все изменения делаются в обычном проекте

## Scope

| Делать | Не делать |
|--------|-----------|
| Файлы в **корне** репозитория (`src/`, `build.gradle`, …) | Папка `Funtime/` |

## Paths

- **Корень обычной сборки:** репозиторий без префикса `Funtime/`
- **Исходники:** `src/main/java/`
- **Сборка:** `gradlew build` из корня

## Правила

1. Не редактируй `Funtime/**`, если пользователь не вызвал `/all`.
2. Игнорируй `Funtime/` при поиске и рефакторинге для этой задачи.
3. Обычная версия пока без отдельного edition-маркера — не переноси Funtime-специфику сюда без запроса.

## Версии (справка)

См. [VERSIONS.md](../../VERSIONS.md) в корне репозитория.
