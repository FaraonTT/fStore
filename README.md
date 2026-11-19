# fStore

Магазин приложений в стиле Material 3 Expressive. Бэкенд — этот же GitHub-репозиторий:

- `data/*.json` — пользователи, приложения, отзывы, бейджи
- `apps/<user_name>/` — APK и иконки
- `data/avatars`, `data/badges` — картинки

## Настройка (один раз)

1. Создай fine-grained токен (GitHub → Settings → Developer settings → Personal access tokens) только для репозитория `fStore` с правом **Contents: Read and write**.
2. Репозиторий → Settings → Secrets and variables → Actions → **New repository secret** → имя `FSTORE_TOKEN`, значение — токен.
3. Actions → Build fStore APK → **Run workflow**. APK `fStore-1.1b.apk` появится в Releases.
4. Установи APK и первым зарегистрируй пользователя `void_fworld` — он получает админ-панель.
