#include "CGstStreamSource.h"
#include "../common/log/JniLogger.h"
#include "../common/configuration/CConfiguration.h"

CGstStreamSource* CGstStreamSource::pInstance_ = nullptr;

gboolean CGstStreamSource::generateAppSrc(const char* appSrcName) {
  appSrc_ = (GstAppSrc*)gst_element_factory_make("appsrc", appSrcName);
  if(NULL == appSrc_) {
      LOGE("Video app src element is not generated");
      return FALSE;
  }
  return TRUE;
}

gboolean CGstStreamSource::terminateAppSrc() {
  gst_element_set_state((GstElement*)appSrc_, GST_STATE_NULL);
  gst_object_unref(GST_OBJECT((GstElement*)appSrc_));
  setStreamingAllowedState(FALSE);
  return TRUE;
}

GstElement* CGstStreamSource::getAppSrc() {
  return (GstElement*)appSrc_;
}

void CGstStreamSource::setStreamingAllowedState(gboolean state) {
  if(FALSE == state) g_atomic_int_set(&isStreamingAllowed_, 0);
  else g_atomic_int_set(&isStreamingAllowed_, 1);
}

gboolean CGstStreamSource::getStreamingAllowedState() {
  if(0 != g_atomic_int_get(&isStreamingAllowed_)) return TRUE;
  else return FALSE;
}

gboolean CGstStreamSource::initializeStreamSource(string applicationType, string mediaType) {
  LOGD("%s Start", __FUNCTION__);
  setStreamingAllowedState(FALSE);
  if(FALSE == generateAppSrc("VideoAppSource")) {
      LOGE("Video source generation error");
      if(FALSE == terminateAppSrc()) {
          LOGE("Failed to terminate subtitle appsrc");
      }
      return FALSE;
  }

  if(0 == applicationType.compare("UsbCamera")) {
      if(CConfiguration::getInstance()->getJpegDecoderType() == "hw") {
        appSrcCaps_ = gst_caps_new_simple("video/x-raw", 
                "format", G_TYPE_STRING, "NV12",   
                "width", G_TYPE_INT, CConfiguration::getInstance()->getResolutionWidth(), 
                "height", G_TYPE_INT, CConfiguration::getInstance()->getResolutionHeight(), 
                "framerate", GST_TYPE_FRACTION, CConfiguration::getInstance()->getFps(), 1, 
                NULL);
      } else {
        appSrcCaps_ = gst_caps_new_simple("image/jpeg", 
                "width", G_TYPE_INT, CConfiguration::getInstance()->getResolutionWidth(), 
                "height", G_TYPE_INT, CConfiguration::getInstance()->getResolutionHeight(), 
                "framerate", GST_TYPE_FRACTION, CConfiguration::getInstance()->getFps(), 1, 
                NULL);
      }

      g_object_set(G_OBJECT(appSrc_),
                   "is-live", TRUE,
                   "format", GST_FORMAT_TIME,
                   "do-timestamp", TRUE,
                   "caps", appSrcCaps_,
                   NULL);
                   
      gst_caps_unref(appSrcCaps_);
  }
  else {
      LOGE("Application type is not supported, application type : %s", applicationType.c_str());
      if(FALSE == terminateAppSrc()) {
          LOGE("Failed to terminate video appsrc");
      }
      return FALSE;
  }
  LOGD("%s End", __FUNCTION__);
  setStreamingAllowedState(TRUE);
  return TRUE;
}