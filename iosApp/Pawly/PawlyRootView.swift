import SwiftUI

struct PawlyRootView: View {
    @AppStorage("pawly.ios.dogName") private var dogName = "Chispita"
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView(dogName: $dogName, selectedTab: $selectedTab)
                .tabItem { Label("Inicio", systemImage: "house.fill") }
                .tag(0)
            ExploreView()
                .tabItem { Label("Explorar", systemImage: "map.fill") }
                .tag(1)
            HealthView(dogName: dogName)
                .tabItem { Label("Salud", systemImage: "heart.text.square") }
                .tag(2)
            DiaryView(dogName: dogName)
                .tabItem { Label("Diario", systemImage: "book.closed.fill") }
                .tag(3)
            CommunityView()
                .tabItem { Label("Comunidad", systemImage: "person.2.fill") }
                .tag(4)
        }
        .tint(PawlyStyle.forest)
        .preferredColorScheme(.light)
    }
}

struct HomeView: View {
    @Binding var dogName: String
    @Binding var selectedTab: Int
    @AppStorage("pawly.ios.walkDone") private var walkDone = false
    @AppStorage("pawly.ios.medicineDone") private var medicineDone = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("HOLA, ¡QUÉ ALEGRÍA VERTE!")
                            .font(.caption.weight(.bold))
                            .tracking(1.2)
                            .foregroundStyle(PawlyStyle.muted)
                        Text("Un día genial con \(dogName) 🐾")
                            .font(.largeTitle.bold())
                            .foregroundStyle(PawlyStyle.forest)
                        Text("Todo lo importante para cuidar y disfrutar de vuestra vida juntos.")
                            .foregroundStyle(PawlyStyle.muted)
                    }
                    .padding(.top, 12)

                    HStack(spacing: 14) {
                        Image(systemName: "pawprint.fill")
                            .font(.system(size: 30))
                            .foregroundStyle(PawlyStyle.forest)
                            .frame(width: 66, height: 66)
                            .background(PawlyStyle.lime, in: RoundedRectangle(cornerRadius: 20))
                        VStack(alignment: .leading, spacing: 4) {
                            Text(dogName).font(.title2.bold())
                            Text("Tu compañero de aventuras").font(.subheadline).foregroundStyle(PawlyStyle.muted)
                        }
                        Spacer()
                        Image(systemName: "pencil")
                            .foregroundStyle(PawlyStyle.forest)
                    }
                    .padding()
                    .background(.white, in: RoundedRectangle(cornerRadius: 22))
                    .overlay(alignment: .bottom) {
                        TextField("Nombre del perro", text: $dogName)
                            .textFieldStyle(.roundedBorder)
                            .padding(.horizontal)
                            .offset(y: 48)
                    }
                    .padding(.bottom, 42)

                    Text("Rutina de hoy").font(.title2.bold()).foregroundStyle(PawlyStyle.forest)
                    TaskToggle(title: "Paseo", subtitle: "Un rato para explorar y olfatear", icon: "figure.walk", isDone: $walkDone)
                    TaskToggle(title: "Medicación", subtitle: "Comprueba si hoy toca tratamiento", icon: "cross.case.fill", isDone: $medicineDone)

                    Button {
                        selectedTab = 2
                    } label: {
                        Label("Ver ficha de salud", systemImage: "heart.text.square")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(PawlyStyle.lime, in: RoundedRectangle(cornerRadius: 16))
                            .foregroundStyle(PawlyStyle.forest)
                    }
                }
                .padding(20)
            }
            .background(PawlyStyle.cream.ignoresSafeArea())
            .navigationBarHidden(true)
        }
    }
}

private struct TaskToggle: View {
    let title: String
    let subtitle: String
    let icon: String
    @Binding var isDone: Bool

    var body: some View {
        Button { isDone.toggle() } label: {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundStyle(PawlyStyle.forest)
                    .frame(width: 46, height: 46)
                    .background(PawlyStyle.cream, in: RoundedRectangle(cornerRadius: 14))
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(.headline).foregroundStyle(.primary)
                    Text(subtitle).font(.caption).foregroundStyle(PawlyStyle.muted)
                }
                Spacer()
                Image(systemName: isDone ? "checkmark.circle.fill" : "circle")
                    .font(.title2)
                    .foregroundStyle(isDone ? PawlyStyle.forest : PawlyStyle.muted)
            }
            .padding()
            .background(.white, in: RoundedRectangle(cornerRadius: 18))
        }
        .buttonStyle(.plain)
    }
}

private struct ExploreIdea: Identifiable {
    let title: String
    let subtitle: String
    let symbol: String
    var id: String { title }
}

struct ExploreView: View {
    private let ideas = [
        ExploreIdea(title: "Rutas y senderos", subtitle: "Descubre nuevos caminos para pasear.", symbol: "figure.hiking"),
        ExploreIdea(title: "Playas para perros", subtitle: "Consulta opciones y normas locales.", symbol: "water.waves"),
        ExploreIdea(title: "Parques y zonas verdes", subtitle: "Encuentra espacios para jugar.", symbol: "tree.fill"),
        ExploreIdea(title: "Terrazas y cafeterías", subtitle: "Planes para compartir contigo.", symbol: "cup.and.saucer.fill")
    ]

    var body: some View {
        NavigationStack {
            List(ideas) { item in
                Label {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.title).font(.headline)
                        Text(item.subtitle).font(.caption).foregroundStyle(PawlyStyle.muted)
                    }
                } icon: {
                    Image(systemName: item.symbol).foregroundStyle(PawlyStyle.forest)
                }
                .padding(.vertical, 6)
            }
            .scrollContentBackground(.hidden)
            .background(PawlyStyle.cream)
            .navigationTitle("Explorar")
        }
    }
}

struct HealthView: View {
    let dogName: String
    @AppStorage("pawly.ios.vetNotes") private var vetNotes = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Ficha de \(dogName)") {
                    Label("Vacunas y revisiones", systemImage: "cross.vial")
                    Label("Tratamientos", systemImage: "pills")
                    Label("Peso y evolución", systemImage: "scalemass")
                }
                Section("Notas de salud") {
                    TextEditor(text: $vetNotes)
                        .frame(minHeight: 150)
                }
            }
            .navigationTitle("Salud")
        }
    }
}

struct DiaryView: View {
    let dogName: String
    @AppStorage("pawly.ios.diaryText") private var diaryText = ""
    @State private var newEntry = ""

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("Los recuerdos de \(dogName)")
                    .font(.title2.bold())
                    .foregroundStyle(PawlyStyle.forest)
                TextField("¿Qué habéis hecho hoy?", text: $newEntry, axis: .vertical)
                    .textFieldStyle(.roundedBorder)
                    .lineLimit(3...5)
                Button("Guardar recuerdo") {
                    let clean = newEntry.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !clean.isEmpty else { return }
                    let stamp = Date.now.formatted(date: .abbreviated, time: .shortened)
                    diaryText = "\(stamp) · \(clean)\n" + diaryText
                    newEntry = ""
                }
                .buttonStyle(.borderedProminent)
                .tint(PawlyStyle.forest)
                Divider()
                ScrollView {
                    Text(diaryText.isEmpty ? "Todavía no hay recuerdos. ¡El primero está esperando!" : diaryText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                }
                Spacer()
            }
            .padding()
            .background(PawlyStyle.cream)
            .navigationTitle("Diario")
        }
    }
}

struct CommunityView: View {
    @State private var email = ""
    @State private var password = ""
    @State private var status = "La comunidad se conectará a tu cuenta Firebase cuando añadamos la configuración iOS."

    var body: some View {
        NavigationStack {
            Form {
                Section("Tu cuenta Pawly") {
                    TextField("Correo electrónico", text: $email)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .autocorrectionDisabled()
                    SecureField("Contraseña", text: $password)
                    Text(status).font(.footnote).foregroundStyle(PawlyStyle.muted)
                }
                Section {
                    Text("En esta primera base iOS, la navegación y las funciones locales están preparadas. El acceso, publicaciones y mensajes se conectarán a Firebase en la siguiente fase.")
                        .font(.footnote)
                }
            }
            .navigationTitle("Comunidad")
        }
    }
}
