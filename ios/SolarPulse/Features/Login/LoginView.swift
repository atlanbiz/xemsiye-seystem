import SwiftUI
import SolarPulseKit

struct LoginView: View {
    @Environment(AuthStore.self) private var auth
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n

    @State var isSignUp = false
    @State var email = ""
    @State var password = ""
    @State var error: String?
    @State var info: String?
    @State var busy = false
    @State var appeared = false
    @State var failures = 0
    @FocusState private var focus: Field?

    private enum Field { case email, password }

    var body: some View {
        ZStack {
            Brand.heroGradient.ignoresSafeArea()
            // soft glow
            Circle()
                .fill(Brand.amber.opacity(0.35))
                .frame(width: 320, height: 320)
                .blur(radius: 90)
                .offset(x: 140, y: -300)
                .accessibilityHidden(true)

            ScrollView {
                VStack(spacing: 28) {
                    header
                    hero
                    form
                }
                .padding(.horizontal, 22)
                .padding(.vertical, 24)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .onAppear {
            if !auth.isSupabase, email.isEmpty {
                email = "admin@solarpulse.app"
                password = "demo1234"
            }
            withAnimation(.spring(duration: 0.8)) { appeared = true }
        }
        .sensoryFeedback(.error, trigger: failures)
    }

    private var header: some View {
        HStack {
            BrandMark(size: 36)
                .environment(\.colorScheme, .dark)
            Spacer()
            LanguageMenu()
        }
    }

    private var hero: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text(l10n.t("auth.heroTitle"))
                .font(.system(.largeTitle, design: .rounded).weight(.bold))
                .foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)
                .opacity(appeared ? 1 : 0)
                .offset(y: appeared ? 0 : 14)
            VStack(alignment: .leading, spacing: 12) {
                feature("bolt.horizontal.circle.fill", l10n.t("auth.feature.live"), delay: 0.1)
                feature("chart.line.uptrend.xyaxis.circle.fill", l10n.t("auth.feature.roi"), delay: 0.2)
                feature("bell.badge.circle.fill", l10n.t("auth.feature.alerts"), delay: 0.3)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func feature(_ icon: String, _ text: String, delay: Double) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title2)
                .symbolRenderingMode(.hierarchical)
                .foregroundStyle(.white)
                .accessibilityHidden(true)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.92))
                .fixedSize(horizontal: false, vertical: true)
        }
        .opacity(appeared ? 1 : 0)
        .offset(y: appeared ? 0 : 10)
        .animation(.spring(duration: 0.7).delay(delay), value: appeared)
    }

    private var form: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Text(l10n.t(isSignUp ? "auth.signUp" : "auth.welcome")).font(.title2.weight(.semibold))
                Text(l10n.t("auth.subtitle")).font(.subheadline).foregroundStyle(.secondary)
            }

            VStack(spacing: 12) {
                field(icon: "envelope.fill") {
                    TextField(l10n.t("auth.email"), text: $email)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .focused($focus, equals: .email)
                        .submitLabel(.next)
                        .onSubmit { focus = .password }
                }
                field(icon: "lock.fill") {
                    SecureField(l10n.t("auth.password"), text: $password)
                        .textContentType(isSignUp ? .newPassword : .password)
                        .focused($focus, equals: .password)
                        .submitLabel(.go)
                        .onSubmit { Task { await submit() } }
                }
            }
            .environment(\.layoutDirection, .leftToRight)

            if let error {
                Label(error, systemImage: "exclamationmark.circle.fill")
                    .font(.footnote)
                    .foregroundStyle(Brand.danger)
                    .transition(.opacity)
            }
            if let info {
                Label(info, systemImage: "envelope.badge.fill")
                    .font(.footnote)
                    .foregroundStyle(Brand.green)
            }

            Button {
                Task { await submit() }
            } label: {
                HStack {
                    if busy { ProgressView().tint(.white) }
                    Text(l10n.t(isSignUp ? "auth.signUp" : "auth.signIn")).fontWeight(.semibold)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .foregroundStyle(.white)
                .background(Brand.gradient, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(busy)
            .accessibilityHint(Text(l10n.t("auth.subtitle")))

            if auth.isSupabase {
                Button(l10n.t(isSignUp ? "auth.haveAccount" : "auth.noAccount")) {
                    withAnimation { isSignUp.toggle(); error = nil; info = nil }
                }
                .font(.footnote)
                .frame(maxWidth: .infinity)
                Label(l10n.t("auth.supabase"), systemImage: "checkmark.shield.fill")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
            } else {
                Label(l10n.t("auth.demo"), systemImage: "sparkles")
                    .font(.caption)
                    .foregroundStyle(Brand.b600)
                    .padding(10)
                    .frame(maxWidth: .infinity)
                    .background(Brand.b50, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
        }
        .padding(22)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .shadow(color: .black.opacity(0.25), radius: 30, y: 14)
        .opacity(appeared ? 1 : 0)
        .offset(y: appeared ? 0 : 24)
    }

    private func field<F: View>(icon: String, @ViewBuilder content: () -> F) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon).foregroundStyle(.secondary).frame(width: 20)
            content()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 13)
        .background(Theme.subtleFill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func submit() async {
        guard !busy else { return }
        error = nil
        info = nil
        busy = true
        let outcome: AuthStore.Outcome
        if isSignUp {
            outcome = await auth.signUp(email: email, password: password)
        } else {
            outcome = await auth.signIn(email: email, password: password)
        }
        busy = false
        switch outcome {
        case .signedIn:
            break
        case .confirmEmail:
            info = l10n.t("auth.checkEmail")
        case .failed(let message):
            withAnimation { error = message == "invalid" ? l10n.t("auth.error") : message }
            failures += 1
        }
    }
}

/// Language switcher (also used in Settings).
struct LanguageMenu: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        Menu {
            ForEach(AppLanguage.allCases) { lang in
                Button {
                    Task { await store.updateSettings { $0.language = lang } }
                } label: {
                    if lang == store.settings.language {
                        Label(lang.nativeName, systemImage: "checkmark")
                    } else {
                        Text(lang.nativeName)
                    }
                }
            }
        } label: {
            Label(store.settings.language.nativeName, systemImage: "globe")
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(.ultraThinMaterial, in: Capsule())
                .foregroundStyle(.white)
        }
    }
}
