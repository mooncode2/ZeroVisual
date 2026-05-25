---
name: all
description: >-
  Applies code changes to both visual editions: repo root (default) and Funtime/ (legit).
  Use when the user invokes /all or asks for changes in both versions.
disable-model-invocation: true
---

# /all — обе версии

Изменения будут касаться обоих версий

## Scope

Правки в **двух** деревьях:

| Версия | Путь |
|--------|------|
| Обычная | корень: `src/`, `build.gradle`, … |
| Funtime (легит) | `Funtime/src/`, `Funtime/build.gradle`, … |

## Workflow

1. Сначала внеси изменение в **корень** (обычная версия).
2. Затем **повтори эквивалент** в `Funtime/` (те же файлы по относительному пути).
3. Учитывай отличия Funtime: `ClientEdition`, `zero/edition.properties`, watermark «Funtime» — не ломай их при переносе.
4. Если файл есть только в одной версии — создай зеркало во второй, если это уместно для задачи.
5. По возможности проверь сборку: `gradlew build` (корень) и `cd Funtime && gradlew build`.

## Когда не дублировать

- Легит-only логика — только `Funtime/`.
- Обычная-only эксперименты — только корень.
- В сомнении — уточни у пользователя.

## Версии (справка)

См. [VERSIONS.md](../../VERSIONS.md) в корне репозитория.
