#include <string.h>
#include <stdint.h>
#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <gst/gst.h>
#include <gst/video/video.h>
#include <pthread.h>
#include <gst/gstregistry.h>
#include <gst/app/gstappsrc.h>
#include "common/log/JniLogger.h"
#include "common/log/JniFileLogger.h"
#include "media/CPipeline.h"
#include "CCustomData.h"
#include "UVCCamera/gstreamer/CGstStreamSource.h"
#include "common/configuration/CConfiguration.h"
#include "common/log/JniLogger.h"
#include "common/monitor/CMonitor.h"
#include <fstream>
#include <string>
#include <sstream>
#include <string>
#include <mutex>
#include <vector>
#include <atomic>

using namespace std;

CPipeline* cameraPipeline = nullptr;

/* Fakesink handoff handler for debugging */
static void fakesink_handoff_handler(GstElement* fakesink, GstBuffer* buffer, GstPad* pad, gpointer user_data) {
    static int frame_count = 0;
    frame_count++;
    
    // 매 30프레임마다 로그 출력
    if (frame_count % 30 == 0) {
        LOGI("Fakesink received buffer #%d, size: %zu bytes", 
             frame_count, gst_buffer_get_size(buffer));
    }
}

/* These global variables cache values which are not changing during execution */
static pthread_t gst_app_thread;
static pthread_key_t current_jni_env;
static JavaVM *java_vm;
static jfieldID custom_data_field_id;
static jmethodID set_message_method_id;
static jmethodID on_gstreamer_initialized_method_id;

// Recovery guard to avoid concurrent pipeline recoveries
static std::atomic<bool> g_pipeline_recovery_in_progress{false};

/* Mutex for check_initialization_complete function */
// static pthread_mutex_t init_check_mutex = PTHREAD_MUTEX_INITIALIZER;

/* Helper function to recursively print elements within a bin */
static void print_bin_elements(GstBin *bin, int indent_level) {
  gchar *indent_str = g_strdup_printf("%*s", indent_level * 2, ""); // Create indentation string

  // Use GST_ELEMENT_NAME for safety, might be unnamed
  LOGI ("%sBin: %s", indent_str, GST_ELEMENT_NAME(bin) ? GST_ELEMENT_NAME(bin) : "(unnamed bin)");

  GstIterator *it = gst_bin_iterate_elements(bin);
  GValue item = G_VALUE_INIT;
  while (gst_iterator_next(it, &item) == GST_ITERATOR_OK) {
    GstElement *element = GST_ELEMENT(g_value_get_object(&item));
    const gchar *element_name = gst_element_get_name(element);
    GstElementFactory *factory = gst_element_get_factory(element);
    const gchar *factory_name = factory ? gst_plugin_feature_get_name(GST_PLUGIN_FEATURE(factory)) : "unknown";

    LOGI ("%s  Element: %s (Factory: %s)",
              indent_str,
              element_name ? element_name : "(unnamed)",
              factory_name);

    // If the element is a Bin itself, recurse
    if (GST_IS_BIN(element)) {
      print_bin_elements(GST_BIN(element), indent_level + 1);
    }

    g_value_reset(&item); // Release the element reference
  }
  g_value_unset(&item);
  gst_iterator_free(it);
  g_free(indent_str); // Free indentation string
}

static const char* pad_presence_to_string(GstPadPresence presence) {
    switch (presence) {
        case GST_PAD_ALWAYS:    return "ALWAYS (Static)";
        case GST_PAD_SOMETIMES: return "SOMETIMES (Dynamic)";
        case GST_PAD_REQUEST:   return "REQUEST";
        default:                return "UNKNOWN";
    }
}

static void log_gstreamer_elements_with_details() {
    LOGI("---- GStreamer Element Inspection Start ----");
    GstRegistry *registry = gst_registry_get();
    if (!registry) {
        LOGE("Failed to get GStreamer registry.");
        return;
    }

    GList *list = gst_registry_feature_filter(registry,
        [](GstPluginFeature *feature, gpointer /* user_data */) -> gboolean {
            return GST_IS_ELEMENT_FACTORY(feature);
        }, FALSE, NULL);

    if (!list) {
        LOGW("No GStreamer elements found in the registry.");
        LOGI("---- GStreamer Element Inspection End ----");
        return;
    }

    GList *l;
    for (l = list; l; l = l->next) {
        GstPluginFeature *feature = GST_PLUGIN_FEATURE(l->data);
        const gchar *element_name = gst_plugin_feature_get_name(feature);

        GstPlugin *plugin = gst_plugin_feature_get_plugin(feature);
        const gchar *plugin_name = plugin ? gst_plugin_get_name(plugin) : "unknown plugin";
        const gchar *klass = gst_element_factory_get_klass(GST_ELEMENT_FACTORY(feature));

        LOGV("Element: %-30s | Plugin: %-20s | Class: %s", element_name, plugin_name, klass);

        GstElementFactory* factory = GST_ELEMENT_FACTORY(feature);
        const GList* pad_templates = gst_element_factory_get_static_pad_templates(factory);
        const GList* p;
        for (p = pad_templates; p; p = p->next) {
            GstStaticPadTemplate *pad_template = (GstStaticPadTemplate*)p->data;
            const char* direction = (pad_template->direction == GST_PAD_SRC) ? "SRC" : "SINK";
            const char* presence_str = pad_presence_to_string(pad_template->presence);

            GstCaps *caps = gst_static_pad_template_get_caps(pad_template);
            char* caps_str = gst_caps_to_string(caps);

            LOGV("  -> Pad: %-5s | Presence: %-18s | Caps: %s", direction, presence_str, caps_str);

            g_free(caps_str);
            gst_caps_unref(caps);
        }

        if (plugin) {
            gst_object_unref(plugin);
        }
    }

    g_list_free(list);
    LOGI("---- GStreamer Element Inspection End ----");
}

/*
 * Private methods
 */

/* Register this thread with the VM */
static JNIEnv *
attach_current_thread (void)
{
  JNIEnv *env;
  JavaVMAttachArgs args;

  LOGD ("Attaching thread %p", g_thread_self ());
  LOGD ("java_vm: %p", java_vm);
  
  if (java_vm == NULL) {
    LOGE ("java_vm is NULL, cannot attach thread");
    return NULL;
  }
  
  args.version = JNI_VERSION_1_4;
  args.name = NULL;
  args.group = NULL;

  if (java_vm->AttachCurrentThread(&env, &args) < 0) {
    LOGE ("Failed to attach current thread");
    return NULL;
  }

  LOGD ("Successfully attached thread, env: %p", env);
  return env;
}

/* Unregister this thread from the VM */
static void
detach_current_thread (void *env)
{
  LOGD ("Detaching thread %p", g_thread_self ());
  java_vm->DetachCurrentThread();
}

/* Retrieve the JNI environment for this thread */
static JNIEnv *
get_jni_env (void)
{
  JNIEnv *env;

  if ((env = static_cast<JNIEnv*>(pthread_getspecific(current_jni_env))) == NULL) {
    env = attach_current_thread ();
    pthread_setspecific (current_jni_env, env);
  }
  return env;
}

/* Change the content of the UI's TextView */
static void
set_ui_message (const gchar* message, void* data)
{
  JNIEnv *env = get_jni_env ();
  LOGD ("Setting message to: %s", message);
  jstring jmessage = env->NewStringUTF(message);
  env->CallVoidMethod(CCustomData::getInstance()->getApp(), set_message_method_id, jmessage);
  
  if (env->ExceptionCheck()) {
    LOGE ("Failed to call Java method");
    env->ExceptionClear();
  }

  env->DeleteLocalRef(jmessage);
}

/* Retrieve errors from the bus and show them on the UI */
static void
error_cb (GstBus * bus, GstMessage * msg, void* data)
{
  GError *err;
  gchar *debug_info;
  gchar *messageString;

  gst_message_parse_error (msg, &err, &debug_info);
  messageString =
      g_strdup_printf ("Error received from element %s: %s",
      GST_OBJECT_NAME (msg->src), err->message);
  
  LOGE("PIPELINE ERROR: %s", messageString);
  if (debug_info) {
    LOGE("Debug info: %s", debug_info);
  }

  // Decide if we need to perform recovery (VideoAppSource errors or not-negotiated cases)
  bool isVideoAppSource = (strncmp(GST_OBJECT_NAME(msg->src), "VideoAppSource", 14) == 0);
  bool isNotNegotiated = (debug_info && strstr(debug_info, "not-negotiated") != nullptr);

  // Always stop camera streaming immediately to prevent more buffers
  // CGstStreamSource::getInstance()->setStreamingAllowedState(FALSE);

  if ((isVideoAppSource || isNotNegotiated) && !g_pipeline_recovery_in_progress.exchange(true)) {
    LOGW("Starting pipeline recovery sequence (reason: %s%s)",
         isVideoAppSource ? "VideoAppSource " : "",
         isNotNegotiated ? "not-negotiated" : "");

    GstElement* pipeline = cameraPipeline ? cameraPipeline->getPipelineElement() : nullptr;
    if (pipeline) {
      // 1) Move pipeline to NULL
      gst_element_set_state(pipeline, GST_STATE_NULL);
      GstState currentState = GST_STATE_NULL, pendingState = GST_STATE_VOID_PENDING;
      gst_element_get_state(pipeline, &currentState, &pendingState, 5 * GST_SECOND);
      LOGI("Pipeline moved to NULL. current=%s pending=%s",
           gst_element_state_get_name(currentState),
           gst_element_state_get_name(pendingState));

      // 2) Move pipeline back to PLAYING
      gst_element_set_state(pipeline, GST_STATE_PLAYING);
      gst_element_get_state(pipeline, &currentState, &pendingState, 5 * GST_SECOND);
      LOGI("Pipeline moved to PLAYING. current=%s pending=%s",
           gst_element_state_get_name(currentState),
           gst_element_state_get_name(pendingState));

      // 3) Streaming re-enable is deferred to state_changed_cb when PLAYING is observed
      if (currentState == GST_STATE_PLAYING) {
        LOGI("Recovery: pipeline reached PLAYING; streaming will be enabled on state_changed_cb");
      } else {
        LOGW("Recovery: pipeline not PLAYING yet (current=%s); will wait for PLAYING",
             gst_element_state_get_name(currentState));
      }
    } else {
      LOGE("Pipeline element is null during recovery");
    }

    g_pipeline_recovery_in_progress.store(false);
  } else {
    if (g_pipeline_recovery_in_progress.load()) {
      LOGW("Recovery already in progress, skipping new recovery attempt");
    } else {
      LOGW("Skipping recovery (not a recoverable source)");
    }
  }

  g_clear_error (&err);
  g_free (debug_info);
  // set_ui_message (message_string, data);
  g_free (messageString);
}

/* Notify UI about pipeline state changes */
static void
state_changed_cb (GstBus* bus, GstMessage* message, void* data)
{
  GstState oldState, newState, pendingState;
  gst_message_parse_state_changed (message, &oldState, &newState, &pendingState);
  LOGI("%s, %s Changed State From %s To %s (pending: %s)", 
       GST_MESSAGE_TYPE_NAME(message), 
       GST_OBJECT_NAME(message->src), 
       gst_element_state_get_name(oldState), 
       gst_element_state_get_name(newState),
       gst_element_state_get_name(pendingState));
  
  // Check if this is the main pipeline reaching PLAYING state
  if (strncmp(GST_OBJECT_NAME(message->src), "usb-camera-pipeline", 19) == 0) {
    if (newState == GST_STATE_PLAYING) {
      LOGI("*** PIPELINE IS NOW PLAYING ***");
      // enable camera streaming when main pipeline is confirmed PLAYING
      CGstStreamSource::getInstance()->setStreamingAllowedState(TRUE);
      LOGI("Streaming allowed state set to TRUE by state_changed_cb");
    } else if (newState == GST_STATE_PAUSED && pendingState == GST_STATE_PLAYING) {
      LOGI("Pipeline paused, trying to reach playing...");
    }
  }
  
  if (strncmp(GST_OBJECT_NAME(message->src), "VideoAppSource", 14) == 0 && newState == GST_STATE_PLAYING) {
    LOGI("VideoAppSource is playing, set streaming allowed state to TRUE");
    CGstStreamSource::getInstance()->setStreamingAllowedState(TRUE);
  }
}

/* Check if all conditions are met to report GStreamer as initialized.
 * These conditions will change depending on the application */
static void
check_initialization_complete ()
{
  LOGD ("%s start", __func__);
  
  /* Lock mutex to prevent concurrent execution */
  // pthread_mutex_lock(&init_check_mutex);
  
  JNIEnv *env = get_jni_env ();
  if (env == NULL) {
    LOGE ("Failed to get JNI environment");
    return;
  }
  
  CCustomData* customData = CCustomData::getInstance();
  if (customData == NULL) {
    LOGE ("CustomData instance is NULL");
    return;
  }
  
  /* Set native window whenever it's available (not just during initialization) */
  if (customData->getNativeWindow() != nullptr && customData->getMainLoop() != nullptr) {
    LOGD("Setting native windows for video sinks. native_window:%p main_loop:%p",
        customData->getNativeWindow(), customData->getMainLoop());

    /* Set native window for main pipeline video sink */
    gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY (cameraPipeline->getVideoSink()),
        (guintptr) customData->getNativeWindow());
    
    /* Main pipeline now handles display directly */
    LOGI("Native window set for main pipeline video sink");
  }

  if (customData->getInitialized() == FALSE && customData->getNativeWindow() != nullptr && customData->getMainLoop() != nullptr) {
    LOGD("Initialization complete, notifying application.");
    
    /* Notify application initialization is complete */
    
    /* Check if method ID is valid */
    if (on_gstreamer_initialized_method_id == NULL) {
      LOGE ("on_gstreamer_initialized_method_id is NULL");
      return;
    }
    
    /* Check if app object is valid */
    jobject app = customData->getApp();
    if (app == NULL) {
      LOGE ("App object is NULL");
      return;
    }
    
    LOGI("About to call Java method. env: %p, app: %p, method_id: %p", 
             env, app, on_gstreamer_initialized_method_id);
    
    /* Call Java method with exception handling */
    env->CallVoidMethod(app, on_gstreamer_initialized_method_id);
    if (env->ExceptionCheck()) {
      LOGE ("Failed to call Java method onGStreamerInitialized");
      env->ExceptionClear();
      return;
    }
    
    LOGI("Java method call successful");
    
    customData->getInstance()->setInitialized(TRUE);
  }
  
  /* Unlock mutex */
  // pthread_mutex_unlock(&init_check_mutex);
  
  LOGD ("%s end", __func__);
}

/* Main method for the native code. This is executed on its own thread. */
static void *
app_function (void *userdata)
{
  log_gstreamer_elements_with_details();
  LOGI("%s start", __func__);
  CCustomData* customData = static_cast<CCustomData*>(userdata);

  JavaVMAttachArgs args;
  GstBus *bus;
  GSource *bus_source;
  GError *error = NULL;

  LOGD ("Creating pipeline in CustomData at %p", customData);

  /* Create our own GLib Main Context and make it the default one */
  customData->setContext(g_main_context_new());
  g_main_context_push_thread_default (customData->getContext());


  if(FALSE == CMonitor::getInstance()->startMonitor()) {
    LOGE("Failed to start monitor");
    return NULL;
  }

  LOGI("Monitor started");

  /* Build pipeline */
  cameraPipeline = new CPipeline();
  if (FALSE == cameraPipeline->generatePipeline()) {

    LOGE("Failed to generate pipeline");

    gchar *message = g_strdup_printf ("Unable to create an element");
    set_ui_message (message, customData);
    g_free (message);
    g_main_context_pop_thread_default (customData->getContext());
    g_main_context_unref (customData->getContext());
    return NULL;
    
  }

  /* Main pipeline now handles display directly */
  LOGI("Main pipeline handles display directly - no separate display pipeline needed");

  /* Print detailed pipeline elements recursively */
  if (cameraPipeline->getPipelineElement()) {
    LOGI("Pipeline details (recursive):");
    print_bin_elements(GST_BIN(cameraPipeline->getPipelineElement()), 0); // Call the recursive print function
  }

  /* Main pipeline handles display directly */

  // gst_element_set_state (cameraPipeline->getPipelineElement(), GST_STATE_READY);
  /* Set the main decoding pipeline to Playing */
  gst_element_set_state (cameraPipeline->getPipelineElement(), GST_STATE_PLAYING);

  // appsink를 사용하므로 별도의 시그널 연결 불필요
  if (nullptr == cameraPipeline->getVideoSink()) {
    LOGE ("Could not retrieve video sink");
    return NULL;
  }

  /* Instruct the bus to emit signals for each received message, and connect to the interesting signals */
  bus = gst_element_get_bus (cameraPipeline->getPipelineElement());
  bus_source = gst_bus_create_watch (bus);
  g_source_set_callback (bus_source, (GSourceFunc) gst_bus_async_signal_func,
      NULL, NULL);
  g_source_attach (bus_source, customData->getContext());
  g_source_unref (bus_source);
  g_signal_connect (G_OBJECT (bus), "message::error", (GCallback) error_cb,
      customData);
  g_signal_connect (G_OBJECT (bus), "message::warning", (GCallback) error_cb,
      customData);
  g_signal_connect (G_OBJECT (bus), "message::state-changed",
      (GCallback) state_changed_cb, customData);
  gst_object_unref (bus);


  if(FALSE == cameraPipeline->connectCameraPipelineStreamProbingPad()) {
    LOGE("Failed to connect camera pipeline stream probing pad");
    return NULL;
  }

  /* Create a GLib Main Loop and set it to run */
  LOGD ("Entering main loop... (CustomData:%p)", customData);
  customData->setMainLoop(g_main_loop_new (customData->getContext(), FALSE));
  check_initialization_complete();
  g_main_loop_run (customData->getMainLoop());
  LOGD ("Exited main loop");
  g_main_loop_unref (customData->getMainLoop());
  customData->setMainLoop(nullptr);

  /* Free resources */
  g_main_context_pop_thread_default (customData->getContext());
  g_main_context_unref (customData->getContext());
  gst_element_set_state (cameraPipeline->getPipelineElement(), GST_STATE_NULL);
  
  /* Main pipeline handles display directly - no separate display pipeline to stop */
  
  if (FALSE == cameraPipeline->disconnectCameraPipelineStreamProbingPad()) {
    LOGE("Failed to disconnect camera pipeline stream probing pad");
  }
 
  gst_object_unref (cameraPipeline->getVideoSink());
  cameraPipeline->terminatePipeline();
  
  if(FALSE == CMonitor::getInstance()->stopMonitor()) { 
    LOGE("Failed to stop monitor");
  }

  LOGI("%s end", __func__);
  return NULL;
}

/*
 * Java Bindings
 */

/* Instruct the native code to create its internal data structure, pipeline and thread */
static void
gst_native_init (JNIEnv * env, jobject thiz)
{
  CCustomData::getInstance()->setApp(env->NewGlobalRef(thiz));
  LOGI("Created GlobalRef for app object at %p", CCustomData::getInstance()->getApp());
  pthread_create (&gst_app_thread, NULL, &app_function, CCustomData::getInstance());
  // Crash is occurred when this function is called.
  
  LOGI("gst_native_init end");
}

/* Quit the main loop, remove the native thread and free resources */
static void
gst_native_finalize (JNIEnv * env, jobject thiz)
{
  LOGD ("Quitting main loop...");
  g_main_loop_quit (CCustomData::getInstance()->getMainLoop());
  LOGD ("Waiting for thread to finish...");
  pthread_join (gst_app_thread, NULL);
  LOGD ("Deleting GlobalRef for app object at %p", CCustomData::getInstance()->getApp());

  env->DeleteGlobalRef(CCustomData::getInstance()->getApp());
  LOGD ("Done finalizing");
}

/* Set pipeline to PLAYING state */
static void
gst_native_play (JNIEnv * env, jobject thiz)
{
  LOGD ("Setting state to PLAYING");
  gst_element_set_state (cameraPipeline->getPipelineElement(), GST_STATE_PLAYING);
}

/* Set pipeline to PAUSED state */
static void
gst_native_pause (JNIEnv * env, jobject thiz)
{
  LOGD ("Setting state to PAUSED");
  gst_element_set_state (cameraPipeline->getPipelineElement(), GST_STATE_PAUSED);
}

static std::string g_native_log_type;
static std::string g_native_log_file_path;
static std::mutex g_native_log_mutex;

extern "C" JNIEXPORT void JNICALL
Java_com_skt_photobox_DemoApplication_nativeInitConfiguration(JNIEnv* env, jobject thiz, jstring jConfigPath, jstring jFrontendLogPath, jstring jPhotoBoxLogPath, jstring jGStreamerLogPath) {
    const char *config_path = env->GetStringUTFChars(jConfigPath, NULL);
    const char *frontend_log_path = env->GetStringUTFChars(jFrontendLogPath, NULL);
    const char *photobox_log_path = env->GetStringUTFChars(jPhotoBoxLogPath, NULL);
    const char *gstreamer_log_path = env->GetStringUTFChars(jGStreamerLogPath, NULL);

    CConfiguration::getInstance()->loadConfiguration(config_path);

    // logging
    if (strncmp(CConfiguration::getInstance()->getLogType(), "file", 4) == 0 || strncmp(CConfiguration::getInstance()->getLogType(), "both", 4) == 0) {
      jni_frontend_file_logger_init(frontend_log_path,
                            CConfiguration::getInstance()->getFrontendLogLevel(),
                            CConfiguration::getInstance()->getMaxLogFileSize(),
                            CConfiguration::getInstance()->getMaxLogBackupIndex());
    
      jni_photo_box_file_logger_init(photobox_log_path,
                            CConfiguration::getInstance()->getPhotoBoxLogLevel(),
                            CConfiguration::getInstance()->getMaxLogFileSize(),
                            CConfiguration::getInstance()->getMaxLogBackupIndex());
    
      jni_gstreamer_file_logger_init(gstreamer_log_path,
                            CConfiguration::getInstance()->getGstreamerLogLevel(),
                            CConfiguration::getInstance()->getMaxLogFileSize(),
                            CConfiguration::getInstance()->getMaxLogBackupIndex());
    }
  

    CConfiguration::getInstance()->displayParsedConfig();
    
    env->ReleaseStringUTFChars(jConfigPath, config_path);
    env->ReleaseStringUTFChars(jFrontendLogPath, frontend_log_path);
    env->ReleaseStringUTFChars(jPhotoBoxLogPath, photobox_log_path);
    env->ReleaseStringUTFChars(jGStreamerLogPath, gstreamer_log_path);
}

extern "C" JNIEXPORT void JNICALL
Java_com_skt_photobox_utils_JniLoggingTree_nativeLog(
        JNIEnv *env,
        jobject /* this */,
        jint priority,
        jstring jTag,
        jstring jMessage) {

    const char *tag = env->GetStringUTFChars(jTag, nullptr);
    const char *message = env->GetStringUTFChars(jMessage, nullptr);

    switch (priority) {
        case 2:  // Log.VERBOSE
            FRONTEND_LOGV("[%s] %s", tag, message);
            break;
        case 3:  // Log.DEBUG
            FRONTEND_LOGD("[%s] %s", tag, message);
            break;
        case 4:  // Log.INFO
            FRONTEND_LOGI("[%s] %s", tag, message);
            break;
        case 5:  // Log.WARN
            FRONTEND_LOGW("[%s] %s", tag, message);
            break;
        case 6:  // Log.ERROR
        case 7:  // Log.ASSERT
            FRONTEND_LOGE("[%s] %s", tag, message);
            break;
        default:
            FRONTEND_LOGI("[%s] %s", tag, message);
            break;
    }

    env->ReleaseStringUTFChars(jTag, tag);
    env->ReleaseStringUTFChars(jMessage, message);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_skt_photobox_DemoApplication_getResolutionWidth(JNIEnv *env, jobject thiz) {
    return CConfiguration::getInstance()->getResolutionWidth();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_skt_photobox_DemoApplication_getResolutionHeight(JNIEnv *env, jobject thiz) {
    return CConfiguration::getInstance()->getResolutionHeight();
}

extern "C" JNIEXPORT void JNICALL
Java_com_skt_photobox_DemoApplication_nativeReleaseConfiguration(JNIEnv* env, jobject thiz) {
    CConfiguration::getInstance()->releaseConfiguration();

    if (strncmp(CConfiguration::getInstance()->getLogType(), "file", 4) == 0 || strncmp(CConfiguration::getInstance()->getLogType(), "both", 4) == 0) {
        jni_frontend_file_logger_release();
        jni_photo_box_file_logger_release();
        jni_gstreamer_file_logger_release();
    }
}



/* Static class initializer: retrieve method and field IDs */
static jboolean
gst_native_class_init (JNIEnv * env, jclass klass)
{
  LOGI("gst_native_class_init start");

  custom_data_field_id =
      env->GetFieldID(klass, "native_custom_data", "J");
  set_message_method_id =
      env->GetMethodID(klass, "setMessage", "(Ljava/lang/String;)V");
  on_gstreamer_initialized_method_id =
      env->GetMethodID(klass, "onGStreamerInitialized", "()V");

  LOGI("custom_data_field_id: %p", custom_data_field_id);
  LOGI("set_message_method_id: %p", set_message_method_id);
  LOGI("on_gstreamer_initialized_method_id: %p", on_gstreamer_initialized_method_id);

  if (!custom_data_field_id || !set_message_method_id
      || !on_gstreamer_initialized_method_id) {
    /* We emit this message through the Android log instead of the GStreamer log because the later
     * has not been initialized yet.
     */
    __android_log_print (ANDROID_LOG_ERROR, "PhotoBox",
        "The calling class does not implement all necessary interface methods");
    LOGE("Failed to get method IDs");
    return JNI_FALSE;
  }
  
  LOGI("gst_native_class_init end - success");
  return JNI_TRUE;
}

static void
gst_native_surface_init (JNIEnv * env, jobject thiz, jobject surface)
{
  ANativeWindow* new_native_window = ANativeWindow_fromSurface (env, surface);
  LOGD ("Received surface %p (native window %p)", surface,
      new_native_window);

  ANativeWindow* native_window = CCustomData::getInstance()->getNativeWindow();

  if (native_window != nullptr) {
    ANativeWindow_release(native_window);
    if (native_window == new_native_window) {
      LOGD ("New native window is the same as the previous one %p",
          native_window);
      if (cameraPipeline->getVideoSink() != nullptr) {
        gst_video_overlay_expose (GST_VIDEO_OVERLAY (cameraPipeline->getVideoSink()));
        gst_video_overlay_expose (GST_VIDEO_OVERLAY (cameraPipeline->getVideoSink()));
      }
      // Main pipeline handles display directly
      return;
    } else {
      LOGD ("Released previous native window %p", CCustomData::getInstance()->getNativeWindow());
      CCustomData::getInstance()->setInitialized(FALSE);
    }
  }
  CCustomData::getInstance()->setNativeWindow(new_native_window);

  check_initialization_complete();
}

static void
gst_native_surface_finalize (JNIEnv * env, jobject thiz)
{
  LOGI("Releasing Native Window %p", CCustomData::getInstance()->getNativeWindow());

  if (cameraPipeline->getVideoSink() != nullptr) {
    gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY (cameraPipeline->getVideoSink()),
        (guintptr) NULL);
    gst_element_set_state (cameraPipeline->getPipelineElement(), GST_STATE_READY);
  }
  
  // Main pipeline handles display directly - no separate display pipeline

  // 하드웨어 디코더 정리
  cameraPipeline->releaseHardwareDecoder();

  ANativeWindow_release(CCustomData::getInstance()->getNativeWindow());
  CCustomData::getInstance()->setNativeWindow(nullptr);
  CCustomData::getInstance()->setInitialized(FALSE);
}



/* List of implemented native methods */
static JNINativeMethod native_methods[] = {
  {"nativeClassInit", "()Z", (void *) gst_native_class_init},
  {"nativeInit", "()V", (void *) gst_native_init},
  {"nativeFinalize", "()V", (void *) gst_native_finalize},
  {"nativePlay", "()V", (void *) gst_native_play},
  {"nativePause", "()V", (void *) gst_native_pause},
  {"nativeSurfaceInit", "(Ljava/lang/Object;)V",
      (void *) gst_native_surface_init},
  {"nativeSurfaceFinalize", "()V", (void *) gst_native_surface_finalize},
};

/* Library initializer */
JNIEXPORT jint
JNI_OnLoad (JavaVM * vm, void *reserved)
{
  JNIEnv *env = NULL;

  LOGI("JNI_OnLoad start");

  if (vm->GetEnv((void **)&env, JNI_VERSION_1_4) != JNI_OK) {
    LOGE("Failed to get JNI environment");
    return -1;
  }

  LOGI("Setting java_vm to %p", vm);
  java_vm = vm;

  jclass klass = env->FindClass("com/skt/photobox/MainActivity");
  if (klass == NULL) {
    LOGE("Failed to find MainActivity class");
    return -1;
  }
  
  env->RegisterNatives(klass, native_methods, G_N_ELEMENTS(native_methods));

  pthread_key_create (&current_jni_env, detach_current_thread);

  //LOGI("JNI_OnLoad completed successfully");
  return JNI_VERSION_1_4;
}
