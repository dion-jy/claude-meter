import SwiftUI
import WebKit

struct LoginView: View {
    let onSessionCaptured: (String) -> Void
    let onClose: () -> Void

    @State private var isLoading = true
    @State private var showEmailHint = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.darkBackground.ignoresSafeArea()

                LoginWebView(
                    isLoading: $isLoading,
                    showEmailHint: $showEmailHint,
                    onSessionCaptured: onSessionCaptured
                )

                if showEmailHint {
                    VStack {
                        Spacer()
                        Text("Enter your email address to sign in.")
                            .font(.system(size: 13))
                            .foregroundColor(.textPrimary)
                            .padding(16)
                            .frame(maxWidth: .infinity)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color.claudePurpleDark)
                            )
                            .padding(12)
                    }
                }

                if isLoading {
                    ProgressView()
                        .tint(.claudePurple)
                        .scaleEffect(1.5)
                }
            }
            .navigationTitle("Sign in to Claude")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .foregroundColor(.textSecondary)
                    }
                }
            }
            .toolbarBackground(Color.darkBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
        }
    }
}

struct LoginWebView: UIViewRepresentable {
    @Binding var isLoading: Bool
    @Binding var showEmailHint: Bool
    let onSessionCaptured: (String) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.isOpaque = false
        webView.backgroundColor = UIColor(Color.darkBackground)

        // Clear cookies
        WKWebsiteDataStore.default().httpCookieStore.getAllCookies { cookies in
            cookies.forEach { cookie in
                WKWebsiteDataStore.default().httpCookieStore.delete(cookie)
            }
        }

        if let url = URL(string: "https://claude.ai/login") {
            webView.load(URLRequest(url: url))
        }

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    class Coordinator: NSObject, WKNavigationDelegate {
        var parent: LoginWebView
        var sessionCaptured = false
        var pollingTimer: Timer?

        init(_ parent: LoginWebView) {
            self.parent = parent
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            DispatchQueue.main.async {
                self.parent.isLoading = false
            }

            // Hide Google login button
            let hideGoogleJS = """
            (function() {
                var style = document.createElement('style');
                style.textContent = `
                    button[data-testid="google-auth-button"],
                    a[href*="accounts.google.com"],
                    button:has(img[alt*="Google"]) {
                        display: none !important;
                    }
                `;
                document.head.appendChild(style);
                var allElements = document.querySelectorAll('*');
                allElements.forEach(function(el) {
                    var text = (el.textContent || '').trim().toLowerCase();
                    if (text === 'or') el.style.display = 'none';
                });
            })();
            """
            webView.evaluateJavaScript(hideGoogleJS, completionHandler: nil)

            // Check for email hint
            if let url = webView.url?.absoluteString, url.contains("claude.ai/login") {
                DispatchQueue.main.async { self.parent.showEmailHint = true }
            } else {
                DispatchQueue.main.async { self.parent.showEmailHint = false }
            }

            // Check cookies
            checkForSessionCookie(webView: webView)

            // Start polling for session cookie
            startPolling(webView: webView)
        }

        func startPolling(webView: WKWebView) {
            pollingTimer?.invalidate()
            pollingTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] _ in
                self?.checkForSessionCookie(webView: webView)
            }
        }

        func checkForSessionCookie(webView: WKWebView) {
            guard !sessionCaptured else { return }

            webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { [weak self] cookies in
                guard let self = self, !self.sessionCaptured else { return }

                if let sessionCookie = cookies.first(where: {
                    $0.name == "sessionKey" && $0.domain.contains("claude.ai")
                }) {
                    let value = sessionCookie.value
                    if !value.isEmpty {
                        self.sessionCaptured = true
                        self.pollingTimer?.invalidate()
                        DispatchQueue.main.async {
                            self.parent.onSessionCaptured(value)
                        }
                    }
                }
            }
        }

        deinit {
            pollingTimer?.invalidate()
        }
    }
}
