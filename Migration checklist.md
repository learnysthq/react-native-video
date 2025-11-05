# React Native Video Migration Checklist
## Migration from Old (3 years) to V7

---

## 📱 Android Changes

### 1. android/build.gradle
- [ ] **Migration Status: Needs Review**

**Description:**
Changed ExoPlayer dependency structure from a single bundled library to individual module dependencies.

**Changes Made:**
```diff
// OLD (Commented out):
- implementation('com.google.android.exoplayer:exoplayer:2.17.1') {
-     exclude group: 'com.android.support'
- }
- implementation('com.google.android.exoplayer:extension-okhttp:2.17.1') {
-     exclude group: 'com.squareup.okhttp3', module: 'okhttp'
- }

// NEW (Sridhar's changes):
+ implementation project(':exoplayer-library-core')
+ implementation project(':exoplayer-library-dash')
+ implementation project(':exoplayer-library-ui')
+ implementation project(':exoplayer-library-hls')
+ implementation project(':exoplayer-library-smoothstreaming')
+ implementation project(':exoplayer-extension-okhttp')
```

**Migration Actions Required:**
- Verify ExoPlayer local project modules exist in your project
- Check if V7 uses ExoPlayer 2.17.1 or newer version
- Ensure all ExoPlayer modules are properly linked
- Test DASH, HLS, and SmoothStreaming playback
- Verify OkHttp extension compatibility

---

### 2. android/src/main/java/com/brentvatne/exoplayer/DataSourceUtil.java
- [ ] **Migration Status: Needs Review**

**Description:**
Added custom proxy selector to bypass proxy for localhost connections and added ReactCookieJarContainer import.

**Changes Made:**
```diff
// NEW IMPORTS:
+ import com.facebook.react.modules.network.ReactCookieJarContainer;
+ import java.net.SocketAddress;
+ import java.net.URI;
+ import java.util.ArrayList;
+ import java.io.IOException;
+ import java.net.InetSocketAddress;
+ import java.net.Proxy;
+ import java.net.ProxySelector;
+ import java.util.List;

// MODIFIED METHOD:
private static HttpDataSource.Factory buildHttpDataSourceFactory(...) {
    // OLD:
    - OkHttpClient client = OkHttpClientProvider.getOkHttpClient();

    // NEW: Custom proxy selector for localhost bypass
    + ProxySelector proxySelector = new ProxySelector() {
    +     @Override
    +     public void connectFailed(URI uri, SocketAddress addr, IOException error) {
    +         System.err.println("React-Native-Video: Failed to connect to proxy!!!!");
    +     }
    +
    +     @Override
    +     public List<Proxy> select(URI uri) {
    +         if (uri.getHost() != null && (uri.getHost().equals("localhost") || uri.getHost().equals("127.0.0.1"))) {
    +             List<Proxy> proxyList = new ArrayList<Proxy>(1);
    +             proxyList.add(Proxy.NO_PROXY);
    +             return proxyList;
    +         } else {
    +             return ProxySelector.getDefault().select(uri);
    +         }
    +     }
    + };
    +
    + OkHttpClient client = new OkHttpClient.Builder()
    +     .proxySelector(proxySelector)
    +     .cookieJar(new ReactCookieJarContainer())
    +     .build();
}
```

**Migration Actions Required:**
- Check if V7 has built-in localhost proxy bypass
- Verify ReactCookieJarContainer exists in V7
- Test video playback from localhost during development
- Consider if this is still needed for V7 architecture
- Test with and without corporate proxies

---

### 3. android/src/main/java/com/brentvatne/exoplayer/ReactExoplayerView.java
- [ ] **Migration Status: Needs Review**

**Description:**
Major changes including DRM offline support, track selection modifications, player lifecycle management, and control UI improvements.

**Changes Made:**

#### A. New Imports:
```diff
+ import android.util.Base64;
+ import java.io.StringWriter;
+ import java.io.PrintWriter;
```

#### B. New Instance Variables:
```diff
+ private FrameworkMediaDrm mediaDrm; // For DRM handling
+ private boolean ignore1080pTrack = false; // Track selection control
+ private String drmOfflineKeySetIdStr = null; // Offline DRM key support
- private boolean controls;
+ private boolean controls = false; // Explicitly initialized to false
```

#### C. Lifecycle Changes:
```diff
@Override
public void onHostPause() {
    // OLD: Commented out
    // stopPlayback();

    // NEW: Active
+   stopPlayback(); // Ensures playback stops when host pauses
}
```

#### D. Player Initialization:
```diff
private void initializePlayer() {
+   /* Decoder resources will be leaked if player not released in some error cases.
+      So make sure release old player before creating new one. */
+   releasePlayer(); // Prevent resource leaks

    // ... rest of initialization
}
```

#### E. Control UI Changes:
```diff
// OLD: Generic View
- View playButton = playerControlView.findViewById(R.id.exo_play);
- View pauseButton = playerControlView.findViewById(R.id.exo_pause);

// NEW: Specific ImageButton
+ ImageButton playButton = playerControlView.findViewById(R.id.exo_play);
+ ImageButton pauseButton = playerControlView.findViewById(R.id.exo_pause);
```

**Migration Actions Required:**
- Verify V7 handles DRM offline scenarios properly
- Check if mediaDrm initialization is needed in V7
- Test player lifecycle and resource cleanup
- Verify 1080p track selection logic with V7
- Test control UI with ImageButton casting
- Check if stopPlayback() on pause is V7 default behavior
- Test DRM offline key handling if using DRM
- Verify Base64 encoding for DRM keys

---

### 4. android/src/main/java/com/brentvatne/exoplayer/ReactExoplayerViewManager.java
- [ ] **Migration Status: Needs Review**

**Description:**
Added support for offline DRM key set ID property.

**Changes Made:**
```diff
// NEW CONSTANT:
+ private static final String PROP_DRM_OFFLINE_KEY_SET_ID = "drmOfflineKeySetId";

// NEW METHOD:
+ @ReactProp(name = PROP_DRM_OFFLINE_KEY_SET_ID)
+ public void setDrmOfflineKeySetId(final ReactExoplayerView videoView, final String drmOfflineKeySetId) {
+     videoView.setDrmOfflineKeySetId(drmOfflineKeySetId);
+ }
```

**Migration Actions Required:**
- Check if V7 supports offline DRM natively
- Verify property naming conventions in V7
- Test DRM offline functionality if required
- Update React Native props to include drmOfflineKeySetId if needed

---

### 5. android/src/main/res/layout/exo_player_control_view.xml
- [ ] **Migration Status: Needs Review**

**Description:**
Modified control view layout with custom styling and visibility changes.

**Changes Made:**
```diff
// Root layout background change:
- android:background="#CC000000"
+ android:background="@color/transparent"

// Center controls container added:
+ <LinearLayout
+     android:layout_width="wrap_content"
+     android:layout_height="wrap_content"
+     android:layout_gravity="center"
+     android:background="@drawable/exo_controls_background"
+     android:gravity="center"
+     android:paddingLeft="4dp"
+     android:paddingTop="4dp"
+     android:paddingRight="4dp"
+     android:paddingBottom="4dp">
+     <!-- Play/Pause buttons here -->
+ </LinearLayout>

// Button styling changes:
+ android:background="@color/transparent"
+ android:contentDescription="@string/exo_controls_rewind_description"
+ android:paddingLeft="12dp"
+ android:paddingRight="12dp"
```

**Migration Actions Required:**
- Check if V7 has updated control view layouts
- Verify `exo_controls_background` drawable exists
- Test transparent background behavior
- Verify accessibility with new content descriptions
- Test control UI on different screen sizes
- Ensure custom styling doesn't conflict with V7 defaults

---

### 6. android/src/main/res/values/colors.xml
- [ ] **Migration Status: Needs Review**

**Description:**
Added transparent color resource.

**Changes Made:**
```diff
+ <color name="transparent">#00000000</color>
```

**Migration Actions Required:**
- Check if V7 defines this color
- Verify usage across all layouts
- Ensure transparency works correctly on all Android versions

---

### 7. android/src/main/res/values/strings.xml
- [ ] **Migration Status: Needs Review**

**Description:**
Added accessibility strings for control buttons.

**Changes Made:**
```diff
+ <string name="exo_controls_play_description">Play</string>
+ <string name="exo_controls_pause_description">Pause</string>
+ <string name="exo_controls_rewind_description">Rewind</string>
+ <string name="exo_controls_fastforward_description">Fast Forward</string>
```

**Migration Actions Required:**
- Check if V7 includes these strings by default
- Add translations for other languages if needed
- Test accessibility features with screen readers
- Verify string resource IDs don't conflict with V7

---

### 8. android/src/main/res/drawable/exo_controls_background.xml
- [ ] **Migration Status: Needs Review**

**Description:**
Added custom background drawable for control buttons.

**Changes Made:**
```xml
+ <?xml version="1.0" encoding="utf-8"?>
+ <shape xmlns:android="http://schemas.android.com/apk/res/android">
+     <solid android:color="#66000000"/>
+     <corners android:radius="4dp"/>
+ </shape>
```

**Migration Actions Required:**
- Verify this drawable doesn't conflict with V7 resources
- Test appearance on different Android versions
- Ensure rounded corners render correctly
- Check if V7 has its own control background styling

---

## 🍎 iOS Changes

### 9. ios/Video/RCTVideo.h
- [ ] **Migration Status: Needs Review**

**Description:**
Modified header file imports and structure.

**Changes Made:**
```diff
// Import changes and property additions (specific changes truncated in patch)
```

**Migration Actions Required:**
- Review full header changes in your codebase
- Check protocol conformance with V7
- Verify all property declarations match V7 API
- Test compilation with new header structure

---

### 10. ios/Video/RCTVideo.m
- [ ] **Migration Status: Needs Review**

**Description:**
Major implementation changes including player lifecycle, DRM handling, and control modifications.

**Changes Made:**
```diff
// Multiple implementation changes (patch truncated)
// Key areas modified:
// - Player initialization
// - DRM handling
// - Lifecycle management
// - Control UI updates
```

**Migration Actions Required:**
- Compare full implementation with V7 codebase
- Test player initialization flow
- Verify DRM functionality if used
- Test all lifecycle events (pause, resume, background)
- Check memory management and resource cleanup

---

### 11. ios/Video/RCTVideoPlayerViewController.h
- [ ] **Migration Status: Needs Review**

**Description:**
Converted from Swift to Objective-C header.

**Changes Made:**
```diff
// Converted to Objective-C header format
// Added proper interface declarations
```

**Migration Actions Required:**
- Verify V7 uses Objective-C or Swift for this component
- Check if bridging header is needed
- Test full screen video controller functionality
- Verify delegate protocols are properly implemented

---

### 12. ios/Video/RCTVideoPlayerViewController.m
- [ ] **Migration Status: Needs Review**

**Description:**
Converted from Swift to Objective-C implementation.

**Changes Made:**
```diff
// Full Swift to Objective-C conversion
// Implementation details for video player view controller
```

**Migration Actions Required:**
- Test full screen presentation
- Verify rotation handling
- Check status bar behavior
- Test dismiss gestures and animations
- Ensure proper delegate callbacks

---

### 13. ios/Video/RCTVideoPlayerViewControllerDelegate.swift
- [ ] **Migration Status: DELETED**

**Description:**
Swift delegate protocol file removed.

**Changes Made:**
```diff
- import Foundation
- import AVKit
-
- protocol RCTVideoPlayerViewControllerDelegate : NSObject {
-     func videoPlayerViewControllerWillDismiss(playerViewController:AVPlayerViewController)
-     func videoPlayerViewControllerDidDismiss(playerViewController:AVPlayerViewController)
- }
```

**Migration Actions Required:**
- Verify V7 doesn't need this Swift protocol
- Check if delegate functionality moved to Objective-C
- Test view controller dismissal callbacks
- Ensure no references to this protocol exist

---

### 14. ios/Video/UIView+FindUIViewController.swift
- [ ] **Migration Status: DELETED**

**Description:**
Swift extension for finding view controller removed.

**Changes Made:**
```diff
- extension UIView {
-     func firstAvailableUIViewController() -> UIViewController? {
-         return traverseResponderChainForUIViewController()
-     }
-
-     func traverseResponderChainForUIViewController() -> UIViewController? {
-         if let nextUIViewController = next as? UIViewController {
-             return nextUIViewController
-         } else if let nextUIView = next as? UIView {
-             return nextUIView.traverseResponderChainForUIViewController()
-         } else {
-             return nil
-         }
-     }
- }
```

**Migration Actions Required:**
- Verify V7 doesn't rely on Swift extensions
- Check replacement Objective-C implementation
- Test view controller discovery functionality
- Ensure responder chain traversal works correctly

---

### 15. ios/Video/UIView+FindUIViewController.h
- [ ] **Migration Status: ADDED**

**Description:**
New Objective-C header for UIView category.

**Changes Made:**
```objc
+ #import <UIKit/UIKit.h>
+
+ @interface UIView (FindUIViewController)
+ - (UIViewController *) firstAvailableUIViewController;
+ - (id) traverseResponderChainForUIViewController;
+ @end
```

**Migration Actions Required:**
- Verify V7 doesn't provide this functionality
- Ensure header is included where needed
- Test method availability from Swift if mixed codebase
- Check for naming conflicts with V7

---

### 16. ios/Video/UIView+FindUIViewController.m
- [ ] **Migration Status: ADDED**

**Description:**
New Objective-C implementation for UIView category.

**Changes Made:**
```objc
+ @implementation UIView (FindUIViewController)
+ - (UIViewController *) firstAvailableUIViewController {
+     return (UIViewController *)[self traverseResponderChainForUIViewController];
+ }
+
+ - (id) traverseResponderChainForUIViewController {
+     id nextResponder = [self nextResponder];
+     if ([nextResponder isKindOfClass:[UIViewController class]]) {
+         return nextResponder;
+     } else if ([nextResponder isKindOfClass:[UIView class]]) {
+         return [nextResponder traverseResponderChainForUIViewController];
+     } else {
+         return nil;
+     }
+ }
+ @end
```

**Migration Actions Required:**
- Test responder chain traversal
- Verify compatibility with iOS versions supported by V7
- Check for memory leaks in traversal
- Test edge cases (detached views, window transitions)

---

### 17. ios/VideoCaching/RCTVideoCachingHandler.swift
- [ ] **Migration Status: DELETED**

**Description:**
Entire Swift caching handler removed (87 lines).

**Changes Made:**
```diff
- class RCTVideoCachingHandler: NSObject, DVAssetLoaderDelegatesDelegate {
-     // Full caching implementation removed
-     // Including:
-     // - Video cache integration
-     // - Promise-based asset loading
-     // - DVAssetLoaderDelegate handling
-     // - Cache storage logic
- }
```

**Migration Actions Required:**
- **CRITICAL**: Check if V7 has built-in caching
- Verify if caching is still needed for your use case
- Test video loading performance without caching
- Check if DVAssetLoaderDelegate is still required
- Review V7 caching documentation
- Test with text tracks (old implementation had issues with text tracks + caching)
- Consider alternative caching solutions if needed

---

## 📊 Migration Summary

### Android Files Changed: 8
- Build configuration: 1
- Java source files: 4
- Layout XML: 1
- Resource files: 2

### iOS Files Changed: 9
- Deleted Swift files: 3
- Added Objective-C files: 2
- Modified implementation files: 4

### Total Files Changed: 17

---

## 🎯 Critical Migration Actions

### High Priority
1. **ExoPlayer Dependencies** - Verify local project modules setup
2. **DRM Offline Support** - Test if V7 handles this natively
3. **Caching Functionality** - Determine V7 caching capabilities
4. **Swift to Objective-C** - Ensure language transition is complete
5. **Player Resource Management** - Test leak prevention measures

### Medium Priority
1. **Proxy Bypass for Localhost** - Check V7 default behavior
2. **Control UI Styling** - Verify custom styles work with V7
3. **Track Selection** - Test 1080p track handling
4. **Accessibility Strings** - Ensure no conflicts with V7

### Low Priority
1. **Color Resources** - Verify transparent color usage
2. **Control Background Drawable** - Check styling compatibility

---

## ✅ Testing Checklist

### Android Testing
- [ ] Video playback from network URLs
- [ ] Video playback from localhost (development)
- [ ] DRM content playback
- [ ] Offline DRM scenarios
- [ ] DASH streaming
- [ ] HLS streaming
- [ ] SmoothStreaming
- [ ] Control UI appearance and functionality
- [ ] Play/Pause button interactions
- [ ] Fast forward/Rewind functionality
- [ ] Player lifecycle (pause/resume/background)
- [ ] Memory leaks during repeated play/stop
- [ ] Proxy handling in corporate networks
- [ ] Track selection (including 1080p)

### iOS Testing
- [ ] Video playback from network URLs
- [ ] Full screen video presentation
- [ ] View controller transitions
- [ ] Rotation handling
- [ ] Status bar behavior
- [ ] Player dismissal callbacks
- [ ] Responder chain view controller discovery
- [ ] Memory management
- [ ] Background/foreground transitions
- [ ] Text track display (without caching)
- [ ] AVPlayerViewController functionality

### Cross-Platform Testing
- [ ] DRM content on both platforms
- [ ] Control consistency between platforms
- [ ] Performance comparison
- [ ] Resource usage monitoring
- [ ] Crash reporting verification

---

## 📝 Notes

1. **Sridhar's Changes**: All modifications are marked with "Sridhar" comments in the code. These represent customizations from the old codebase.

2. **Language Migration**: iOS code is being migrated from Swift to Objective-C for some components. Verify this aligns with V7's architecture.

3. **Caching Removal**: The entire caching handler was removed on iOS. This is a significant change that may impact performance.

4. **DRM Focus**: Substantial DRM-related changes suggest this feature is critical for your application.

5. **Resource Management**: Strong emphasis on preventing resource leaks indicates this was a previous issue.

---

## 🔗 References for V7 Migration

- Check react-native-video V7 changelog
- Review V7 API documentation
- Compare V7 ExoPlayer version requirements
- Check V7 DRM implementation guide
- Review V7 caching capabilities
- Verify V7 iOS implementation language (Swift vs Objective-C)

---

**Migration Status Key:**
- ✅ **Completed**: Change fully reviewed and tested
- ⚠️ **Needs Review**: Change identified but requires verification against V7
- ❌ **Blocked**: Cannot proceed until dependencies resolved
- 🔄 **In Progress**: Currently being migrated

**Last Updated**: [Add Date]
**Reviewed By**: [Add Name]
