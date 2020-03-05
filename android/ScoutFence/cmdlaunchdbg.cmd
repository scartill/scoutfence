adb -s emulator-5554 uninstall ru.glonassunion.aerospace.scoutfence
adb -s emulator-5554 install ./app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell pm grant ru.glonassunion.aerospace.scoutfence android.permission.ACCESS_FINE_LOCATION
adb -s emulator-5554 shell am start -n ru.glonassunion.aerospace.scoutfence/ru.glonassunion.aerospace.scoutfence.MapsActivity

