# adb logcat -c
# adb logcat  '*:D' -v color | grep YKK_TEST

adb exec-out "run-as com.skt.aionad.addon tar c -C files logs" > aionad-add-on-bluehands.tar
tar xvf aionad-add-on-bluehands.tar
