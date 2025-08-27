#pragma once

#include <gst/app/gstappsrc.h>
#include <string>

using namespace std;

class CGstStreamSource {
public:
    CGstStreamSource() {

    }

    ~CGstStreamSource() {
      
    }

    static CGstStreamSource* getInstance() {
      if(NULL == pInstance_) pInstance_ = new CGstStreamSource();
      return pInstance_;
    }

    void destroyInstance() {
		if(pInstance_) {
			delete pInstance_;
			pInstance_ = NULL;
		}
	}
    
    gboolean generateAppSrc(const char* appSrcName);
    gboolean terminateAppSrc();
    GstElement* getAppSrc();
    void setStreamingAllowedState(gboolean state);
    gboolean getStreamingAllowedState();
    gboolean initializeStreamSource(string applicationType, string mediaType);
    
protected:
    static CGstStreamSource* pInstance_;
    GstAppSrc *appSrc_ = nullptr;
    GstCaps *appSrcCaps_ = nullptr;
    gint isStreamingAllowed_ = FALSE;
};