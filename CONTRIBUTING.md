# Contributing · Как помочь проекту

Thanks for your interest in YouLauncher! · Спасибо за интерес к YouLauncher!

## Rules · Правила

- **Design · Дизайн.** Material 3 Expressive components only. Colors, shapes, type and motion come from the theme; no new color constants (except the seed list in `Theme.kt`). Animations use springs from `MaterialTheme.motionScheme`, no `tween`. Every interactive element has pressed, hovered and focused states.
  Только компоненты Material 3 Expressive. Цвета, формы, шрифты и анимации берутся из темы; новые константы цвета запрещены (кроме seed-цветов в `Theme.kt`). Анимации только на пружинах `MaterialTheme.motionScheme`, без `tween`.
- **Icons · Иконки.** Material Symbols Rounded only: add the name to `tools/symbols.txt` and run `gradlew.bat :tools:generateSymbols`.
- **Strings · Строки.** Resources only, in both English (`values`) and Russian (`values-ru`). `ResourcesTest` checks that the keys match.
- **Legal · Юридическое.** No Minecraft or Mojang logos, art, sounds or textures; game files only from Mojang servers with hash checks; no Google Pixel wallpapers.
- **Accounts · Аккаунты.** Offline, Ely.by and Microsoft (device code flow, the password never reaches the launcher).
- **Security · Безопасность.** Tokens live only in Windows Credential Manager and are masked in logs. No keys or passwords in the repository.
- **Layers · Слои.** `core` has no UI or network, `data` implements interfaces from `core`, `ui` gets state through ViewModels and StateFlow.
- **Tests · Тесты.** Everything that is not UI is covered by unit tests. `gradlew.bat test` and `gradlew.bat exportApp` must pass.

## Workflow · Процесс

1. Branch off `main`. · Создайте ветку от `main`.
2. Keep commits small and clear. · Делайте небольшие понятные коммиты.
3. For UI changes attach screenshots: `gradlew.bat :tools:renderScreens -PshotOnly=<name>` writes them to `docs/screens`.
4. Open a pull request; GitHub Actions builds the project and runs the tests.

## Reporting bugs · Сообщить об ошибке

In the launcher: Settings → About → Save report. The archive contains no tokens, passwords or Windows user name, so it is safe to attach to an issue. Launcher crash reports are in the `logs` folder of the YouLauncher data.

В лаунчере: Настройки → «О программе» → «Сохранить отчёт». В архиве нет токенов, паролей и имени пользователя Windows, его можно приложить к issue.
