import SwiftUI

struct PawlyPetHeader: View {
    @EnvironmentObject private var pets: PawlyPetStore
    @State private var adding = false
    @State private var editing: PawlyPet?
    @State private var deleting: PawlyPet?
    @State private var settings = false
    @State private var notifications = false

    var body: some View {
        HStack {
            Menu {
                ForEach(pets.pets) { pet in
                    Button { pets.select(pet) } label: {
                        if pet == pets.selectedPet { Label(pet.name, systemImage: "checkmark") }
                        else { Text(verbatim: pet.name) }
                    }
                }
                Divider()
                Button { adding = true } label: { Label("Añadir mascota", systemImage: "plus") }
                if let pet = pets.selectedPet, pet.ownerUID == nil {
                    Button { editing = pet } label: { Label("Editar perfil", systemImage: "pencil") }
                    Button(role: .destructive) { deleting = pet } label: { Label("Eliminar perfil", systemImage: "trash") }
                }
            } label: {
                HStack(spacing: 8) {
                    AsyncImage(url: pets.selectedPet.flatMap { URL(string: $0.photoURL) }) { phase in
                        if let image = phase.image { image.resizable().scaledToFill() }
                        else { Image(systemName: "pawprint.fill").foregroundStyle(PawlyColors.primary) }
                    }
                    .frame(width: 40, height: 40).background(PawlyColors.mint).clipShape(Circle())
                    if let name = pets.selectedPet?.name { Text(verbatim: name) } else { Text("Añadir mascota") }
                    Image(systemName: "chevron.down").font(.caption)
                }.font(.headline).foregroundStyle(PawlyColors.ink)
            }
            .accessibilityIdentifier("pet.selection")
            Spacer()
            Button { notifications = true } label: { Image(systemName: "bell").frame(width: 44, height: 44) }
                .accessibilityLabel("Notificaciones")
            Button { settings = true } label: { Image(systemName: "gearshape").frame(width: 44, height: 44) }
                .accessibilityLabel("Ajustes").accessibilityIdentifier("pet.settings")
        }
        .sheet(isPresented: $adding) { PetFormSheet() }
        .sheet(item: $editing) { PetFormSheet(pet: $0) }
        .sheet(isPresented: $notifications) { PawlyNotificationsView() }
        .sheet(isPresented: $settings) {
            NavigationStack {
                PawlySettingsView().toolbar {
                    ToolbarItem(placement: .confirmationAction) { Button("Cerrar") { settings = false }.accessibilityIdentifier("settings.close") }
                }
            }
        }
        .confirmationDialog("¿Eliminar el perfil?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
                            titleVisibility: .visible, presenting: deleting) { pet in
            Button("Eliminar perfil", role: .destructive) {
                Task {
                    do { try await pets.deletePet(pet) }
                    catch { pets.errorMessage = PawlyServiceMessage.key(for: error) }
                }
            }
            Button("Cancelar", role: .cancel) {}
        } message: { pet in Text("Se quitará el perfil de \(pet.name) de tu cuenta. Se conservan los registros históricos.") }
    }
}
