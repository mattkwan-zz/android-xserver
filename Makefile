# Author: Daniel Giritzer (giri@nwrk.biz)
## Workdir
WORKDIR=$(shell pwd)
PROJNAME=au.com.darkside.xdemo
LIBNAME=au.com.darkside.xserver

## Java/Android Compiler Settings
JAVA_HOME=/usr/lib/jvm/java-1.11.0-openjdk-amd64/
ANDROID_SDK_ROOT=/usr/lib/android-sdk
ANDROID_BUILD_TOOLS_VERSION=debian
ANDROID_PLATORM_VERSION=23
ANDROID_PLATORM=android-$(ANDROID_PLATORM_VERSION)
ANDROID_CP=$(ANDROID_SDK_ROOT)/platforms/$(ANDROID_PLATORM)/android.jar

## Keystore Settings
ANDROID_KEYSTORE_PATH=debug.keystore
ANDROID_KEYSTORE_NAME=androiddebugkey
ANDROID_KEYSTORE_PW=android

## Tools
ADB=$(ANDROID_SDK_ROOT)/platform-tools/adb
AAPT=$(ANDROID_SDK_ROOT)/build-tools/$(ANDROID_BUILD_TOOLS_VERSION)/aapt
DX=$(ANDROID_SDK_ROOT)/build-tools/$(ANDROID_BUILD_TOOLS_VERSION)/dx
ZIPALIGN=$(ANDROID_SDK_ROOT)/build-tools/$(ANDROID_BUILD_TOOLS_VERSION)/zipalign
JAVAC=$(JAVA_HOME)/bin/javac
JARSIGNER=$(JAVA_HOME)/bin/jarsigner

# Android Sources and resources
ANDROID_SRC=$(WORKDIR)/demo/src/main
ANDROID_LIB=$(WORKDIR)/library/src/main
ANDROID_SOURCES=$(shell find $(WORKDIR) -name *.java)

# out
GENDIR_ANDROID=$(WORKDIR)/app/release/gen
CLASSDIR_ANDROID=$(WORKDIR)/app/release/class
OUT_ANDROID=$(WORKDIR)/app/release/

all: clean android

android:
	mkdir -p $(GENDIR_ANDROID)
	mkdir -p $(CLASSDIR_ANDROID)
	$(AAPT) package -f -m -J $(GENDIR_ANDROID) --auto-add-overlay -M $(ANDROID_SRC)/AndroidManifest.xml -S $(ANDROID_LIB)/res -S $(ANDROID_SRC)/res -I $(ANDROID_CP)  --extra-packages $(LIBNAME)
	$(JAVAC) -classpath $(ANDROID_CP) -sourcepath 'src:$(GENDIR_ANDROID)' -d '$(CLASSDIR_ANDROID)' -target 1.7 -source 1.7 $(ANDROID_SOURCES)
	$(DX) --dex --output=$(GENDIR_ANDROID)/classes.dex $(CLASSDIR_ANDROID)
	$(AAPT) package -f -M $(ANDROID_LIB)/AndroidManifest.xml -M $(ANDROID_SRC)/AndroidManifest.xml -S $(ANDROID_LIB)/res -S $(ANDROID_SRC)/res -A $(ANDROID_SRC)/assets -I $(ANDROID_CP) -F $(GENDIR_ANDROID)/$(PROJNAME).apk.unaligned
	cd $(GENDIR_ANDROID) && $(AAPT) add $(GENDIR_ANDROID)/$(PROJNAME).apk.unaligned classes.dex
	$(JARSIGNER) -keystore $(ANDROID_KEYSTORE_PATH) -storepass '$(ANDROID_KEYSTORE_PW)' $(GENDIR_ANDROID)/$(PROJNAME).apk.unaligned  $(ANDROID_KEYSTORE_NAME)
	$(ZIPALIGN) -f 4 $(GENDIR_ANDROID)/$(PROJNAME).apk.unaligned  $(OUT_ANDROID)/$(PROJNAME).apk

generate_keystore:
	keytool -genkey -v -keystore $(ANDROID_KEYSTORE_PATH)  -storepass $(ANDROID_KEYSTORE_PW) -alias $(ANDROID_KEYSTORE_NAME) -keypass $(ANDROID_KEYSTORE_PW) -keyalg RSA -keysize 2048 -validity 10000	

install:
	adb install $(OUT_ANDROID)/$(PROJNAME).apk

uninstall:
	adb uninstall $(PROJNAME)

run:
	adb shell monkey -p $(PROJNAME) -c android.intent.category.LAUNCHER 1

clean:
	rm -rf $(GENDIR_ANDROID)
	rm -rf $(CLASSDIR_ANDROID)
	rm -rf $(OUT_ANDROID)