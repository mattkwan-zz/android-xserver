<h2 align="center"><b>Android X11 X-Server</b></h2>
<h4 align="center">An X11 X-Server written in Java</h4>
<p align="center"><a href="https://f-droid.org/packages/au.com.darkside.xdemo/"><img src="https://f-droid.org/wiki/images/0/06/F-Droid-button_get-it-on.png"></a></p>

This project implements an X11 server for use with Android devices, written in Java. The X11 server runs within an Android View subclass, allowing it to be embedded in other applications.

This project also includes a simple demo application.


Quick Start
-----------

To display programs within the X-Server demo app you need to set the DISPLAY environment variable on your host device. Its also highly recommended to use a window manager (i.e. lwm). A simple window manager (FLWM) is already embedded into the application. For an extended softkeyboard [Hacker's Keyboard](https://f-droid.org/en/packages/org.pocketworkstation.pckeyboard/) can be used.

### Build

#### Gardle

This project uses gradle build system, so if you don't want to mess with Android Studio you can just
 open console at project's root folder and type `./gradlew build`. Generated APK can be found under
 `demo/build/outputs/`.

#### Manual (Makefile based)

If, for whatever reason, you do not want to use gardle, there is also a Makefile that performs all the necessary build steps.

### Example

Starts xfe file browser, 192.178.1.2 should be replaced with te IP of your device:

```
$ export DISPLAY=192.178.1.2:0
$ lwm &
$ xfe
```

In addition to the touch screen, the volume rocker acts as mouse buttons.


About
-----

Forked from: https://github.com/mattkwan-zz/android-xserver

Daniel Giritzer, 2020 (https://page.nwrk.biz/giri)