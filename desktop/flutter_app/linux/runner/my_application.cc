#include "my_application.h"

#include <cstring>
#include <flutter_linux/flutter_linux.h>
#include <gio/gio.h>
#ifdef GDK_WINDOWING_X11
#include <gdk/gdkx.h>
#endif

#include <desktop_multi_window/desktop_multi_window_plugin.h>

#include "flutter/generated_plugin_registrant.h"

struct _MyApplication {
  GtkApplication parent_instance;
  char** dart_entrypoint_arguments;
  FlView* view;
  FlMethodChannel* platform_channel;
};

G_DEFINE_TYPE(MyApplication, my_application, GTK_TYPE_APPLICATION)

static gchar* read_gsettings_string(const gchar* schema, const gchar* key) {
  GSettingsSchemaSource* source = g_settings_schema_source_get_default();
  if (source == nullptr) {
    return nullptr;
  }
  GSettingsSchema* settings_schema =
      g_settings_schema_source_lookup(source, schema, TRUE);
  if (settings_schema == nullptr) {
    return nullptr;
  }
  g_settings_schema_unref(settings_schema);
  g_autoptr(GSettings) settings = g_settings_new(schema);
  gchar* value = g_settings_get_string(settings, key);
  if (value == nullptr || value[0] == '\0') {
    g_free(value);
    return nullptr;
  }
  return value;
}

static gchar* strip_uri_prefix(gchar* value) {
  if (value == nullptr) {
    return nullptr;
  }
  if (g_str_has_prefix(value, "file://")) {
    g_autofree gchar* unescaped = g_uri_unescape_string(value + 7, nullptr);
    g_free(value);
    if (unescaped == nullptr || unescaped[0] == '\0') {
      return nullptr;
    }
    return g_strdup(unescaped);
  }
  return value;
}

static gchar* get_wallpaper_path() {
  const gchar* env_wallpaper = g_getenv("ABK_DESKTOP_WALLPAPER");
  if (env_wallpaper != nullptr && env_wallpaper[0] != '\0') {
    return g_strdup(env_wallpaper);
  }

  const struct {
    const gchar* schema;
    const gchar* key;
  } candidates[] = {
      {"org.gnome.desktop.background", "picture-uri-dark"},
      {"org.gnome.desktop.background", "picture-uri"},
      {"org.cinnamon.desktop.background", "picture-uri"},
      {"org.mate.background", "picture-filename"},
      {"org.xfce.desktop", "last-image"},
  };

  for (guint index = 0; index < G_N_ELEMENTS(candidates); index++) {
    g_autofree gchar* value = read_gsettings_string(
        candidates[index].schema, candidates[index].key);
    if (value == nullptr) {
      continue;
    }
    value = strip_uri_prefix(value);
    if (value != nullptr && g_file_test(value, G_FILE_TEST_EXISTS)) {
      return g_strdup(value);
    }
  }

  g_autofree gchar* plasma_config = g_build_filename(
      g_get_home_dir(), ".config", "plasma-org.kde.plasma.desktop-appletsrc",
      nullptr);
  if (g_file_test(plasma_config, G_FILE_TEST_EXISTS)) {
    g_autofree gchar* contents = nullptr;
    gsize length = 0;
    if (g_file_get_contents(plasma_config, &contents, &length, nullptr)) {
      g_auto(GStrv) lines = g_strsplit(contents, "\n", -1);
      for (guint index = 0; lines[index] != nullptr; index++) {
        const gchar* prefix = "Image=file://";
        if (g_str_has_prefix(lines[index], prefix)) {
          g_autofree gchar* path = g_uri_unescape_string(
              lines[index] + strlen(prefix), nullptr);
          if (path != nullptr && g_file_test(path, G_FILE_TEST_EXISTS)) {
            return g_strdup(path);
          }
        }
      }
    }
  }

  return nullptr;
}

static FlMethodResponse* handle_platform_method_call(FlMethodCall* method_call) {
  const gchar* method = fl_method_call_get_name(method_call);
  if (strcmp(method, "getWallpaperPath") == 0) {
    g_autofree gchar* wallpaper_path = get_wallpaper_path();
    g_autoptr(FlValue) result = wallpaper_path == nullptr
        ? fl_value_new_null()
        : fl_value_new_string(wallpaper_path);
    return FL_METHOD_RESPONSE(fl_method_success_response_new(result));
  }

  return FL_METHOD_RESPONSE(fl_method_not_implemented_response_new());
}

static void platform_method_call_cb(FlMethodChannel* channel,
                                    FlMethodCall* method_call,
                                    gpointer user_data) {
  g_autoptr(FlMethodResponse) response =
      handle_platform_method_call(method_call);
  g_autoptr(GError) error = nullptr;
  if (!fl_method_call_respond(method_call, response, &error)) {
    g_warning("Failed to send platform response: %s", error->message);
  }
}

static void create_channels(MyApplication* self) {
  FlEngine* engine = fl_view_get_engine(self->view);
  FlBinaryMessenger* messenger = fl_engine_get_binary_messenger(engine);
  g_autoptr(FlStandardMethodCodec) codec = fl_standard_method_codec_new();

  self->platform_channel = fl_method_channel_new(
      messenger, "com.abk.desktop/platform", FL_METHOD_CODEC(codec));
  fl_method_channel_set_method_call_handler(
      self->platform_channel, platform_method_call_cb, self, nullptr);
}

// Called when first Flutter frame received.
static void first_frame_cb(MyApplication* self, FlView* view) {
  gtk_widget_show(gtk_widget_get_toplevel(GTK_WIDGET(view)));
}

// Implements GApplication::activate.
static void my_application_activate(GApplication* application) {
  MyApplication* self = MY_APPLICATION(application);
  GtkWindow* window =
      GTK_WINDOW(gtk_application_window_new(GTK_APPLICATION(application)));

  // Use a header bar when running in GNOME as this is the common style used
  // by applications and is the setup most users will be using (e.g. Ubuntu
  // desktop).
  // If running on X and not using GNOME then just use a traditional title bar
  // in case the window manager does more exotic layout, e.g. tiling.
  // If running on Wayland assume the header bar will work (may need changing
  // if future cases occur).
  gboolean use_header_bar = TRUE;
#ifdef GDK_WINDOWING_X11
  GdkScreen* screen = gtk_window_get_screen(window);
  if (GDK_IS_X11_SCREEN(screen)) {
    const gchar* wm_name = gdk_x11_screen_get_window_manager_name(screen);
    if (g_strcmp0(wm_name, "GNOME Shell") != 0) {
      use_header_bar = FALSE;
    }
  }
#endif
  if (use_header_bar) {
    GtkHeaderBar* header_bar = GTK_HEADER_BAR(gtk_header_bar_new());
    gtk_widget_show(GTK_WIDGET(header_bar));
    gtk_header_bar_set_title(header_bar, "ABK Desktop");
    gtk_header_bar_set_show_close_button(header_bar, TRUE);
    gtk_window_set_titlebar(window, GTK_WIDGET(header_bar));
  } else {
    gtk_window_set_title(window, "ABK Desktop");
  }

  gtk_window_set_default_size(window, 1280, 720);

  g_autoptr(FlDartProject) project = fl_dart_project_new();
  fl_dart_project_set_dart_entrypoint_arguments(
      project, self->dart_entrypoint_arguments);

  self->view = fl_view_new(project);
  GdkRGBA background_color;
  // Background defaults to black, override it here if necessary, e.g. #00000000
  // for transparent.
  gdk_rgba_parse(&background_color, "#000000");
  fl_view_set_background_color(self->view, &background_color);
  gtk_widget_show(GTK_WIDGET(self->view));
  gtk_container_add(GTK_CONTAINER(window), GTK_WIDGET(self->view));

  // Show the window when Flutter renders.
  // Requires the view to be realized so we can start rendering.
  g_signal_connect_swapped(self->view, "first-frame", G_CALLBACK(first_frame_cb),
                           self);
  gtk_widget_realize(GTK_WIDGET(self->view));

  fl_register_plugins(FL_PLUGIN_REGISTRY(self->view));
  desktop_multi_window_plugin_set_window_created_callback(
      [](FlPluginRegistry* registry) { fl_register_plugins(registry); });
  create_channels(self);

  gtk_widget_grab_focus(GTK_WIDGET(self->view));
}

// Implements GApplication::local_command_line.
static gboolean my_application_local_command_line(GApplication* application,
                                                  gchar*** arguments,
                                                  int* exit_status) {
  MyApplication* self = MY_APPLICATION(application);
  // Strip out the first argument as it is the binary name.
  self->dart_entrypoint_arguments = g_strdupv(*arguments + 1);

  g_autoptr(GError) error = nullptr;
  if (!g_application_register(application, nullptr, &error)) {
    g_warning("Failed to register: %s", error->message);
    *exit_status = 1;
    return TRUE;
  }

  g_application_activate(application);
  *exit_status = 0;

  return TRUE;
}

// Implements GApplication::startup.
static void my_application_startup(GApplication* application) {
  // MyApplication* self = MY_APPLICATION(object);

  // Perform any actions required at application startup.

  G_APPLICATION_CLASS(my_application_parent_class)->startup(application);
}

// Implements GApplication::shutdown.
static void my_application_shutdown(GApplication* application) {
  // MyApplication* self = MY_APPLICATION(object);

  // Perform any actions required at application shutdown.

  G_APPLICATION_CLASS(my_application_parent_class)->shutdown(application);
}

// Implements GObject::dispose.
static void my_application_dispose(GObject* object) {
  MyApplication* self = MY_APPLICATION(object);
  g_clear_pointer(&self->dart_entrypoint_arguments, g_strfreev);
  g_clear_object(&self->platform_channel);
  G_OBJECT_CLASS(my_application_parent_class)->dispose(object);
}

static void my_application_class_init(MyApplicationClass* klass) {
  G_APPLICATION_CLASS(klass)->activate = my_application_activate;
  G_APPLICATION_CLASS(klass)->local_command_line =
      my_application_local_command_line;
  G_APPLICATION_CLASS(klass)->startup = my_application_startup;
  G_APPLICATION_CLASS(klass)->shutdown = my_application_shutdown;
  G_OBJECT_CLASS(klass)->dispose = my_application_dispose;
}

static void my_application_init(MyApplication* self) {
  self->view = nullptr;
  self->platform_channel = nullptr;
}

MyApplication* my_application_new() {
  // Set the program name to the application ID, which helps various systems
  // like GTK and desktop environments map this running application to its
  // corresponding .desktop file. This ensures better integration by allowing
  // the application to be recognized beyond its binary name.
  g_set_prgname(APPLICATION_ID);

  return MY_APPLICATION(g_object_new(my_application_get_type(),
                                     "application-id", APPLICATION_ID, "flags",
                                     G_APPLICATION_NON_UNIQUE, nullptr));
}
