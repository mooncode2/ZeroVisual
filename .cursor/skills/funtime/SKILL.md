---
name: funtime
description: >-
  Routes all code changes to the Funtime legit visual edition under Funtime/.
  Use when the user invokes /funtime, asks for Funtime, легит, or legit edition work.
disable-model-invocation: true
---

# /funtime — легитная версия Funtime

все изменения ты делаешь в версии Funtime - легитной

## Scope

| Делать | Не делать |
|--------|-----------|
| Файлы в `Funtime/` | Корень репозитория (обычная версия) |

## Paths

- **Корень легит-сборки:** `Funtime/`
- **Исходники:** `Funtime/src/main/java/`
- **Ресурсы:** `Funtime/src/main/resources/`
- **Сборка:** `cd Funtime && gradlew build`

## Правила

1. Рабочая директория для правок — только `Funtime/`.
2. Название мода в `fabric.mod.json` остаётся **Zero**; версия визуала — **Funtime** (`ClientEdition`, `zero/edition.properties`).
3. Не дублируй правки в корень, если пользователь не вызвал `/all`.
4. После изменений при необходимости собери проект из `Funtime/`.

## Версии (справка)

См. [VERSIONS.md](../../VERSIONS.md) в корне репозитория.
