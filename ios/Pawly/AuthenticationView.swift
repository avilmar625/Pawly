import SwiftUI

struct AuthenticationView: View {
    @EnvironmentObject private var session: PawlySession
    @State private var isRegistering = false
    @State private var name = ""
    @State private var username = ""
    @State private var email = ""
    @State private var password = ""
    @State private var message: String?
    @State private var isError = false
    @State private var isWorking = false
    @State private var isGoogleWorking = false

    var body: some View {
        ZStack {
            PawlyColors.canvas.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    HStack { Spacer(); PawlyLogo(); Spacer() }
                        .padding(.top, 42)
                    VStack(alignment: .leading, spacing: 7) {
                        Text(isRegistering ? "Crea tu cuenta" : "Qué alegría verte")
                            .font(.system(size: 31, weight: .bold, design: .rounded))
                            .foregroundStyle(PawlyColors.ink)
                        Text(isRegistering ? "Empieza a cuidar, recordar y compartir cada aventura." : "Entra en tu espacio y sigue las aventuras de tu compañero.")
                            .font(.subheadline).foregroundStyle(PawlyColors.muted)
                    }
                    VStack(spacing: 14) {
                        if isRegistering {
                            field("Tu nombre", text: $name, icon: "person")
                                .textInputAutocapitalization(.words)
                            field("Nombre de usuario", text: $username, icon: "at")
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                        }
                        field("Correo electrónico", text: $email, icon: "envelope")
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        HStack {
                            Image(systemName: "lock").foregroundStyle(PawlyColors.muted)
                            SecureField("Contraseña", text: $password)
                                .textContentType(isRegistering ? .newPassword : .password)
                        }
                        .padding(16)
                        .background(.white, in: RoundedRectangle(cornerRadius: 15))
                    }
                    if let message {
                        Text(message).font(.footnote).foregroundStyle(isError ? .red : PawlyColors.primary)
                    }
                    Button {
                        Task { await submit() }
                    } label: {
                        HStack {
                            if isWorking { ProgressView().tint(.white) }
                            Text(isRegistering ? "Crear cuenta" : "Iniciar sesión")
                                .fontWeight(.bold)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 17)
                        .background(PawlyColors.primary, in: RoundedRectangle(cornerRadius: 16))
                        .foregroundStyle(.white)
                    }
                    .disabled(isWorking || email.isEmpty || password.count < 6 ||
                              (isRegistering && (name.trimmingCharacters(in: .whitespaces).isEmpty || username.isEmpty)))
                    .opacity(isWorking ? 0.7 : 1)
                    if !isRegistering {
                        Button {
                            Task {
                                isGoogleWorking = true
                                do { try await PawlyGoogleSignIn.signIn(); message = nil }
                                catch { message = error.localizedDescription; isError = true }
                                isGoogleWorking = false
                            }
                        } label: {
                            HStack { if isGoogleWorking { ProgressView() }; Image(systemName: "g.circle.fill"); Text("Continuar con Google") }
                                .fontWeight(.semibold).frame(maxWidth: .infinity).padding(.vertical, 15)
                                .background(.white, in: RoundedRectangle(cornerRadius: 15))
                                .overlay(RoundedRectangle(cornerRadius: 15).stroke(PawlyColors.muted.opacity(0.25)))
                                .foregroundStyle(PawlyColors.ink)
                        }.disabled(isWorking || isGoogleWorking)
                    }
                    if !isRegistering {
                        Button("He olvidado mi contraseña") {
                            Task { await resetPassword() }
                        }
                        .font(.footnote.weight(.semibold)).foregroundStyle(PawlyColors.primary)
                        .frame(maxWidth: .infinity)
                    }
                    HStack {
                        Text(isRegistering ? "¿Ya tienes cuenta?" : "¿Todavía no tienes cuenta?")
                            .foregroundStyle(PawlyColors.muted)
                        Button(isRegistering ? "Inicia sesión" : "Regístrate") {
                            isRegistering.toggle()
                            message = nil
                        }
                        .fontWeight(.bold).foregroundStyle(PawlyColors.primary)
                    }
                    .font(.subheadline).frame(maxWidth: .infinity)
                    Text("Al continuar aceptas las condiciones y la política de privacidad de Pawly.")
                        .font(.caption2).foregroundStyle(PawlyColors.muted)
                        .multilineTextAlignment(.center)
                }
                .padding(24)
            }
        }
    }

    private func field(_ title: String, text: Binding<String>, icon: String) -> some View {
        HStack {
            Image(systemName: icon).foregroundStyle(PawlyColors.muted)
            TextField(title, text: text)
        }
        .padding(16)
        .background(.white, in: RoundedRectangle(cornerRadius: 15))
    }

    @MainActor private func submit() async {
        isWorking = true
        defer { isWorking = false }
        do {
            if isRegistering {
                try await session.register(email: email, password: password, displayName: name, username: username)
                message = "Cuenta creada. Revisa tu correo para verificarla."
                isError = false
            } else {
                try await session.signIn(email: email, password: password)
            }
        } catch {
            message = error.localizedDescription
            isError = true
        }
    }

    @MainActor private func resetPassword() async {
        guard !email.isEmpty else {
            message = "Escribe primero tu correo electrónico."
            isError = true
            return
        }
        do {
            try await session.resetPassword(email: email)
            message = "Te hemos enviado un enlace para restablecer la contraseña."
            isError = false
        } catch {
            message = error.localizedDescription
            isError = true
        }
    }
}
