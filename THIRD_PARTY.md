# Сторонний код

## photocraft-psd (PhotoCraft)

Ядро разбора PSD/PSB в `rust/psd` — вендоренная копия крейта `photocraft-psd`
из проекта **PhotoCraft** (https://github.com/storytold/photocraft), версия 0.17.0.

- Лицензия: **MIT OR Apache-2.0** (копии — `rust/LICENSE-MIT`, `rust/LICENSE-APACHE`).
- Что взято: весь крейт `crates/psd` без изменений.
- Что изменено: только `Cargo.toml` (убрано наследование workspace, `flate2`
  переключён на чистый Rust-бэкенд ради Android), добавлен JNI-мост `rust/psd-jni`.

Крейт написан clean-room по публичной спецификации формата PSD от Adobe;
код Adobe внутри не используется. Названия продуктов Adobe принадлежат Adobe.

## Прочее

- Иконки интерфейса — векторная отрисовка в коде (`Glyph.kt`), Material Icons
  для мелких действий (Apache-2.0).
- Шрифты — системные Android.
