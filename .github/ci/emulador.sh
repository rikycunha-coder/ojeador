#!/usr/bin/env bash
# Dentro del móvil virtual: instala la app y su recorrido, lo ejecuta y recoge en
# ./salida-emulador las capturas, los PDF, los datos guardados y el registro del sistema.
set -u
OUT=salida-emulador
mkdir -p "$OUT"
APP=app/build/outputs/apk/debug/app-debug.apk
PRUEBA=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
# Capturas sin ruido del móvil virtual, que va lento: sin avisos de «… no responde» de sus
# propias apps (el sistema las reinicia sin preguntar) ni el aviso de pantalla completa.
adb shell settings put global hide_error_dialogs 1
adb shell settings put secure anr_show_background 0
adb shell settings put secure immersive_mode_confirmations confirmed
# Deja que termine de arrancar todo y cierra lo que haya quedado abierto.
sleep 45
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
adb shell input keyevent KEYCODE_HOME || true
adb shell getprop ro.build.version.release > "$OUT/android.txt"
adb shell wm size >> "$OUT/android.txt"
adb shell wm density >> "$OUT/android.txt"
# -g: concede los permisos (cámara, micrófono) sin preguntar.
adb install -r -g "$APP"
adb install -r -g "$PRUEBA"
adb logcat -c || true

# Saca las capturas que dejó la prueba en el móvil a la carpeta indicada ($1, dentro de $OUT).
recoger() {
  mkdir -p "$OUT/$1"
  adb exec-out run-as com.ojeador.app tar cf - -C files capturas > "$OUT/capturas.tar" 2>> "$OUT/tar-error.txt"
  tar xf "$OUT/capturas.tar" -C "$OUT/$1" && rm -f "$OUT/capturas.tar"
}

adb shell am instrument -w -r -e class com.ojeador.app.RecorridoTest#recorrido \
  com.ojeador.app.test/androidx.test.runner.AndroidJUnitRunner > "$OUT/instrument.txt" 2>&1
tail -n 80 "$OUT/instrument.txt"
recoger .

# Otra vez la cámara y el directo, ahora como en un móvil de 360 dp de ancho (densidad 3,0).
adb shell wm density 480
sleep 10
adb shell input keyevent KEYCODE_HOME || true
adb shell am instrument -w -r -e class com.ojeador.app.RecorridoTest#estrecha \
  com.ojeador.app.test/androidx.test.runner.AndroidJUnitRunner > "$OUT/instrument-estrecha.txt" 2>&1
tail -n 30 "$OUT/instrument-estrecha.txt"
recoger estrecha
adb shell wm density reset || true

adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
adb shell screencap -p /sdcard/final.png && adb pull /sdcard/final.png "$OUT/final.png" || true
ls -la "$OUT" "$OUT/capturas" || true

if grep -qE "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=" "$OUT/instrument.txt"; then
  echo "=== El recorrido tuvo fallos ==="
  grep -E "Pasos con fallos|^[a-z-]+: " "$OUT/instrument.txt" | head -40 || true
  exit 1
fi
if ! grep -q "OK (1 test)" "$OUT/instrument.txt"; then
  echo "=== El recorrido no terminó bien ==="
  exit 1
fi
if ! grep -q "OK (1 test)" "$OUT/instrument-estrecha.txt"; then
  echo "=== La prueba en pantalla estrecha no terminó bien ==="
  grep -E "Pasos con fallos|^[a-z-]+: |shortMsg=" "$OUT/instrument-estrecha.txt" | head -40 || true
  exit 1
fi
