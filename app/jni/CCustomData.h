#pragma once

#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <gst/gst.h>

class CCustomData {
public:
  CCustomData();
  ~CCustomData();

  static CCustomData* getInstance() {
		if(NULL == pInstance_) pInstance_ = new CCustomData();
		return pInstance_;
    }

  void destroyInstance() {
		if(pInstance_) {
			delete pInstance_;
			pInstance_ = NULL;
		}
	}

  void setApp(jobject app) {
    app_ = app;
  }

  void setNativeWindow(ANativeWindow *native_window) {
    native_window_ = native_window;
  }

  void setContext(GMainContext *context) {
    context_ = context;
  }

  void setMainLoop(GMainLoop *main_loop) {
    main_loop_ = main_loop;
  }

  void setInitialized(gboolean initialized) {
    initialized_ = initialized;
  }

  jobject getApp() {
    return app_;
  }

  ANativeWindow *getNativeWindow() {
    return native_window_;
  }

  GMainContext *getContext() {
    return context_;
  }

  GMainLoop *getMainLoop() {
    return main_loop_;
  }

  gboolean getInitialized() {
    return initialized_;
  }

protected:
  static CCustomData* pInstance_;
  jobject app_ = nullptr;
  GMainContext *context_ = nullptr;
  GMainLoop *main_loop_ = nullptr;
  gboolean initialized_ = FALSE;
  ANativeWindow *native_window_ = nullptr;
};