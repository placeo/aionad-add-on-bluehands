# adb logcat -c
# adb logcat  '*:D' -v color | grep YKK_TEST

adb exec-out "run-as com.skt.photobox tar c -C files log" > photo-box-logs.tar
tar xvf photo-box-logs.tar