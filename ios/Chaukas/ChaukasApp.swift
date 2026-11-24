// App entry point: configures Firebase and App Check, and handles chaukas://check?text= links
// from Shortcuts.

import FirebaseAppCheck
import FirebaseCore
import SwiftUI

@main
struct ChaukasApp: App {
    @State private var model = AppModel()

    init() {
        // App Check is required for Firebase AI Logic. Debug builds use the
        // debug provider (register the token printed in the Xcode console);
        // release builds use App Attest / DeviceCheck.
        #if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(DeviceCheckProviderFactory())
        #endif
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .preferredColorScheme(.light)
                .onOpenURL { url in model.handle(url: url) }
        }
    }
}
