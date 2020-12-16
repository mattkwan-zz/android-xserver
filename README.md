Android X11 X-Server
=============

This project implements an X11 server for use with Android devices, written in Java. The X11 server runs within an Android View subclass, allowing it to be embedded in other applications.

This project also includes a simple demo application. 

Quick Start
----------------

To display programs within the X-Server app you need to set the DISPLAY environment variable on your host device. Its also highly recommended to use a window manager (i.e. lwm).

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

The volume rocker acts as mouse buttons.

About
--------

Forked from: https://github.com/mattkwan-zz/android-xserver

Daniel Giritzer, 2020 (https://page.nwrk.biz/giri)