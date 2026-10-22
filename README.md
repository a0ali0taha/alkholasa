# الخلاصة

Native Android app that lists Arabic headlines from two Google News RSS feeds. Created from `android-app-starter-kit`. There is no backend: the app fetches the feeds itself.

## Feeds

- السعودية: `https://news.google.com/rss?hl=ar&gl=SA&ceid=SA:ar`
- مصر: `https://news.google.com/rss?hl=ar&gl=EG&ceid=EG:ar`

**الكل** shows both, newest first. **السعودية** and **مصر** filter that list. **تحديث** downloads again, and the list can be pulled to refresh. Tapping a headline opens an in-app tab. **الاهتمامات** can switch that to the external browser. If one edition fails, the other stays on screen and the status line says which one could not be fetched.

Do not add a local server or a CORS proxy. Those were only needed for the browser page, because Google News does not let a web page read these feeds. An Android HTTP client can read them directly.

## Code

- `app/src/main/java/com/taha/nabd/NewsFeed.kt` fetches both feeds in parallel and parses each `<item>` (title, link, source, pubDate). The publisher suffix after the last ` - ` is removed from the title.
- `app/src/main/java/com/taha/nabd/NewsAdapter.kt` renders one row: region, relative time, title, source.
- `app/src/main/java/com/taha/nabd/MainActivity.kt` owns the filters, refresh, and the list.
- Layouts are XML. The UI is RTL Arabic. Do not switch this app to Compose unless asked.

Package / namespace: `com.taha.nabd`. Debug builds install as `com.taha.nabd.debug` because of `applicationIdSuffix`. Launch the debug activity with:

```bash
adb shell am start -n com.taha.nabd.debug/com.taha.nabd.MainActivity
```

## Run on the emulator

From this directory. `adb` and `emulator` live in `~/Library/Android/sdk` and are not always on `PATH`.

```bash
export PATH="$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/platform-tools:$PATH"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.taha.nabd.debug/com.taha.nabd.MainActivity
```

The emulator used so far is `Medium_Phone_API_36`. Start it only if none is connected:

```bash
emulator -avd Medium_Phone_API_36 -no-audio -no-boot-anim
adb wait-for-device
```

JDK is Android Studio’s JBR (`JAVA_HOME` already points at it). `local.properties` has the SDK path and is gitignored.
