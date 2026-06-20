// package app.hayase;

// import android.view.WindowManager;

// import com.getcapacitor.Plugin;
// import com.getcapacitor.PluginCall;
// import com.getcapacitor.PluginMethod;
// import com.getcapacitor.annotation.CapacitorPlugin;

// @CapacitorPlugin(name = "WakeLock")
// public class WakeLockPlugin extends Plugin {
//   @PluginMethod
//   public void acquire(PluginCall call) {
//     getActivity().runOnUiThread(() -> {
//       getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//       call.resolve();
//     });
//   }

//   @PluginMethod
//   public void release(PluginCall call) {
//     getActivity().runOnUiThread(() -> {
//       getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//       call.resolve();
//     });
//   }
// }
