osascript -e 'tell application "System Events" to keystroke "k" using {command down}'
reset; adb logcat -c; adb logcat PHOTO-BOX-FRONTEND:V PHOTO-BOX-BACKEND:V '*:S'
