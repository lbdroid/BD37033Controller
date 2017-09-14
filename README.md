# BD37033 Sound Controller for Jerking UxyyyN[D?][2] car radios

Currently a work in progress, but tested AND WORKING.<br>
<br>
This will require library /system/lib/libjni_i2c.so to be renamed to /system/lib/libjni_i2c_moved.so in order to block sofia server from accessing it.<br>
This will also require the permissions to be changed on /dev/i2c-4 so that our process is allowed to access it.<br>
i.e., chmod 666 /dev/i2c-4<br>
Yeah, I know that 666 is not ideal.<br>
I'm going to look into what is the best way to automatically make the permission change, since it requires root access (adb shell, su)<br>
<br>
Right now implements a service launched by a launcher icon. The service provides buttons to vol+, vol-, enable/disable mixing, and swich between phone and AMFM radio mode (click the notification itself).<br>
<br>
DOES NOT respond to volume buttons on SWI. That functionality will be added when I take over control of the MCU.<br>
<br>
Not sure what it will do with a "D" radio. Probably work fine, but if they are rerouting through another audio chip, then who knows. Note that whether it is a "D" or not, it still have the BD37033, since it is built in to the SoM (on the bottom side).
