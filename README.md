Android X11 X-Server
=============

This project implements an X11 server for use with Android devices, written in Java. The X11 server runs within an Android View subclass, allowing it to be embedded in other applications.

This project also includes a simple demo application. 

Quick Start
----------------

To display programs within the X-Server app you need to set the DISPLAY environment variable on your host device. Its also highly recommended to use a window manager (i.e. lwm).

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