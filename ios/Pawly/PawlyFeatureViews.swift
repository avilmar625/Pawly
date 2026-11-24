import SwiftUI
import FirebaseFirestore
import PhotosUI
import UIKit
import AVKit
import FirebaseAuth
import Combine

struct PawlyTabView: View {
    @StateObject private var pets = PawlyPetStore()
    @StateObject private var pushRouter = PawlyPushRouter.shared
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            PawlyMyPetView(selectedTab: $selectedTab).tabItem { Label("Mi perro", systemImage: "pawprint.fill") }.tag(0)
            CommunityView().tabItem { Label("Comunidad", systemImage: "person.3.fill") }.tag(3)
            PawlyExploreView().tabItem { Label("Explorar", systemImage: "safari.fill") }.tag(1)
            PawlyMoreHubView().tabItem { Label("Más", systemImage: "ellipsis") }.tag(4)
        }
        .tint(PawlyColors.primary)
        .environmentObject(pets)
        .environmentObject(pushRouter)
        .toolbarBackground(PawlyColors.surface, for:.tabBar)
        .toolbarBackground(.visible, for:.tabBar)
        .onReceive(pushRouter.$destination.compactMap { $0 }) { _ in
            selectedTab = 3
        }
        .task { await pets.loadPets(); pets.startListening() }
        .onDisappear { pets.stopListening() }
    }
}

private struct PawlyMyPetView: View {
    @Binding var selectedTab: Int
    @EnvironmentObject private var pets: PawlyPetStore
    @State private var section = 0

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 9) {
                if section != 0, pets.pets.count > 1 {
                    HStack { Text("Mascota").font(.caption); Spacer(); PetPicker() }
                }
                HStack(spacing: 4) {
                    ForEach(Array(["Hoy", "Salud", "Diario", "Agenda"].enumerated()), id: \.offset) { index, label in
                        Button { section = index } label: {
                            Text(LocalizedStringKey(label))
                                .font(.subheadline.weight(section == index ? .semibold : .regular))
                                .lineLimit(1).minimumScaleFactor(0.8)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .foregroundStyle(section == index ? PawlyColors.primary : PawlyColors.ink)
                                .background(section == index ? PawlyColors.mint : Color.clear, in: RoundedRectangle(cornerRadius: 12))
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(section == index ? .isSelected : [])
                    }
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(PawlyColors.surface)
            Divider()
            switch section {
            case 1: CareView()
            case 2: DiaryView()
            case 3:
                NavigationStack {
                    PawlyRemindersView()
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                NavigationLink { PawlyCalendarView() } label: {
                                    Label("Calendario", systemImage: "calendar")
                                }
                            }
                        }
                }
            default: HomeView(selectedTab: $selectedTab, openHealth: { section = 1 })
            }
        }
        .background(PawlyColors.canvas)
    }
}

struct HomeView: View {
    @EnvironmentObject private var pets: PawlyPetStore
    @Binding private var selectedTab: Int
    @State private var showAdd = false
    @State private var petToDelete: PawlyPet?
    @State private var petToEdit: PawlyPet?

    var openHealth: (() -> Void)?

    init(selectedTab: Binding<Int>, openHealth: (() -> Void)? = nil) {
        self._selectedTab = selectedTab
        self.openHealth = openHealth
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 10) {
                        if let pet = pets.selectedPet {
                            Button {
                                if pet.ownerUID == nil { petToEdit = pet }
                            } label: {
                                HStack(spacing: 11) {
                                    if let url = URL(string: pet.photoURL), !pet.photoURL.isEmpty {
                                        AsyncImage(url: url) { image in
                                            image.resizable().scaledToFill()
                                        } placeholder: {
                                            Image(systemName: pet.species == "Gato" ? "cat.fill" : "pawprint.fill")
                                                .foregroundStyle(PawlyColors.primary)
                                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                                .background(PawlyColors.mint)
                                        }
                                        .frame(width: 50, height: 50)
                                        .clipShape(Circle())
                                    } else {
                                        Image(systemName: pet.species == "Gato" ? "cat.fill" : "pawprint.fill")
                                            .font(.title3)
                                            .foregroundStyle(PawlyColors.primary)
                                            .frame(width: 50, height: 50)
                                            .background(PawlyColors.mint, in: Circle())
                                    }
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(pet.name)
                                            .font(.headline)
                                            .foregroundStyle(PawlyColors.ink)
                                        Text(pet.ownerUID == nil ? "Editar perfil" : "Mascota compartida")
                                            .font(.caption)
                                            .foregroundStyle(PawlyColors.muted)
                                    }
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .disabled(pet.ownerUID != nil)
                            .contextMenu {
                                if pet.ownerUID == nil {
                                    Button { petToEdit = pet } label: {
                                        Label("Editar perfil", systemImage: "pencil")
                                    }
                                    Button(role: .destructive) {
                                        petToDelete = pet
                                    } label: {
                                        Label("Eliminar perfil", systemImage: "trash")
                                    }
                                }
                            }
                        } else {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Tu mascota")
                                    .font(.headline)
                                    .foregroundStyle(PawlyColors.ink)
                                Text("Añade un perfil para empezar")
                                    .font(.caption)
                                    .foregroundStyle(PawlyColors.muted)
                            }
                        }

                        Spacer(minLength: 4)

                        Button { selectedTab = 3 } label: {
                            Image(systemName: "bell")
                                .font(.headline)
                                .foregroundStyle(PawlyColors.primary)
                                .frame(width: 42, height: 42)
                                .background(PawlyColors.surface, in: Circle())
                                .overlay(Circle().stroke(PawlyColors.muted.opacity(0.18)))
                        }
                        .accessibilityLabel("Abrir comunidad y avisos")

                        Button { selectedTab = 4 } label: {
                            Image(systemName: "slider.horizontal.3")
                                .font(.headline)
                                .foregroundStyle(PawlyColors.primary)
                                .frame(width: 42, height: 42)
                                .background(PawlyColors.surface, in: Circle())
                                .overlay(Circle().stroke(PawlyColors.muted.opacity(0.18)))
                        }
                        .accessibilityLabel("Abrir ajustes")
                    }

                    if pets.pets.count > 1 {
                        HStack {
                            Text("Cambiar de mascota")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(PawlyColors.muted)
                            Spacer()
                            PetPicker()
                        }
                    }

                    if let pet = pets.selectedPet {
                        PawlyHomeCareSection(pet: pet, openHealth: openHealth)
                    } else {
                        PawlyEmptyState(
                            icon: "pawprint",
                            title: "Tu historia empieza aquí",
                            detail: "Añade el perfil de tu mascota para organizar sus cuidados y recuerdos."
                        )
                        .frame(minHeight: 230)
                    }

                    HStack {
                        Text("Tus mascotas")
                            .font(.headline)
                            .foregroundStyle(PawlyColors.ink)
                        Spacer()
                        Button { showAdd = true } label: {
                            Label("Añadir", systemImage: "plus")
                                .font(.subheadline.weight(.semibold))
                        }
                        .tint(PawlyColors.primary)
                    }

                    if pets.pets.count > 1 {
                        ForEach(pets.pets) { pet in
                            Button { pets.select(pet) } label: {
                                HStack(spacing: 11) {
                                    Image(systemName: pet.species == "Gato" ? "cat.fill" : "pawprint.fill")
                                        .foregroundStyle(PawlyColors.primary)
                                        .frame(width: 40, height: 40)
                                        .background(PawlyColors.mint, in: Circle())
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(pet.name)
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundStyle(PawlyColors.ink)
                                        Text(pet.ownerUID == nil ? [pet.breed, pet.age].filter { !$0.isEmpty }.joined(separator: " · ") : "Perfil compartido")
                                            .font(.caption)
                                            .foregroundStyle(PawlyColors.muted)
                                    }
                                    Spacer()
                                    if pets.selectedPetID == pet.id {
                                        Image(systemName: "checkmark.circle.fill")
                                            .foregroundStyle(PawlyColors.primary)
                                    }
                                }
                                .padding(11)
                                .background(PawlyColors.surface, in: RoundedRectangle(cornerRadius: 15))
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    if let error = pets.errorMessage {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }
                }
                .padding(18)
                .frame(maxWidth: 760)
                .frame(maxWidth: .infinity)
            }
            .background(PawlyColors.canvas)
            .navigationBarHidden(true)
            .sheet(isPresented: $showAdd) { PetFormSheet() }
            .sheet(item: $petToEdit) { pet in PetFormSheet(pet: pet) }
            .confirmationDialog(
                "¿Eliminar el perfil de \(petToDelete?.name ?? "esta mascota")?",
                isPresented: Binding(
                    get: { petToDelete != nil },
                    set: { if !$0 { petToDelete = nil } }
                ),
                titleVisibility: .visible
            ) {
                Button("Eliminar perfil", role: .destructive) {
                    if let pet = petToDelete {
                        Task { try? await pets.deletePet(pet) }
                    }
                    petToDelete = nil
                }
            } message: {
                Text("Esto elimina el perfil de la lista de tu cuenta. Los registros históricos no se borran automáticamente.")
            }
            .refreshable { await pets.loadPets() }
        }
    }
}
struct PetPicker:View {
    @EnvironmentObject private var pets:PawlyPetStore
    var body:some View {
        Menu {
            ForEach(pets.pets) { pet in
                Button { pets.select(pet) } label:{
                    if pet.id==pets.selectedPetID { Label(pet.name,systemImage:pet.ownerUID == nil ? "checkmark":"person.2.fill") } else { Text(pet.ownerUID == nil ? pet.name : "\(pet.name) · Compartida") }
                }
            }
        } label:{
            HStack(spacing:6){Text(pets.selectedPet?.name ?? "Elige mascota");Image(systemName:"chevron.down").font(.caption)}
                .font(.subheadline.weight(.semibold)).foregroundStyle(PawlyColors.primary)
                .padding(.horizontal,12).padding(.vertical,8).background(PawlyColors.mint,in:Capsule())
        }
    }
}

struct PetFormSheet:View {
    @EnvironmentObject private var pets:PawlyPetStore
    @Environment(\.dismiss) private var dismiss
    let pet:PawlyPet?
    @State private var name=""
    @State private var breed=""
    @State private var age=""
    @State private var species="Perro"
    @State private var description=""
    @State private var photoURL=""
    @State private var photoItem:PhotosPickerItem?
    @State private var photoData:Data?
    @State private var error:String?
    @State private var saving=false
    init(pet:PawlyPet?=nil){
        self.pet=pet
        _name=State(initialValue:pet?.name ?? "")
        _breed=State(initialValue:pet?.breed ?? "")
        _age=State(initialValue:pet?.age ?? "")
        _species=State(initialValue:pet?.species ?? "Perro")
        _description=State(initialValue:pet?.description ?? "")
        _photoURL=State(initialValue:pet?.photoURL ?? "")
    }
    var body:some View {
        NavigationStack {
            Form {
                Section("Tu compañero") {
                    TextField("Nombre",text:$name)
                    TextField("Raza",text:$breed)
                    TextField("Edad",text:$age)
                    Picker("Especie",selection:$species){ForEach(["Perro","Gato","Conejo","Ave","Roedor","Reptil","Otro"],id:\.self){Text($0)}}
                    TextField("Descripción",text:$description,axis:.vertical).lineLimit(2...4)
                }
                Section("Fotografía") {
                    PhotosPicker(selection:$photoItem,matching:.images){Label("Elegir de la galería",systemImage:"photo")}
                    PawlyCameraButton { data in photoItem=nil;photoData=data }
                    if let data=photoData,let image=UIImage(data:data){Image(uiImage:image).resizable().scaledToFill().frame(height:150).clipped().clipShape(RoundedRectangle(cornerRadius:14))}
                    else if let url=URL(string:photoURL),!photoURL.isEmpty{
                        AsyncImage(url: url) { image in
                            image.resizable().scaledToFill()
                        } placeholder: {
                            ProgressView()
                        }
                        .frame(height:150).clipped().clipShape(RoundedRectangle(cornerRadius:14))
                    }
                }
                if let error {Text(error).foregroundStyle(.red)}
            }
            .navigationTitle(pet == nil ? "Añadir mascota":"Editar mascota")
            .toolbar {
                ToolbarItem(placement:.cancellationAction){Button("Cancelar"){dismiss()}}
                ToolbarItem(placement:.confirmationAction){
                    Button(saving ? "Guardando…" : "Guardar"){
                        Task {
                            saving=true
                            do {
                                var finalURL=photoURL
                                if let data=photoData,let uid=Auth.auth().currentUser?.uid {
                                    finalURL=try await PawlyCloudinaryUploader().uploadImage(data,folder:"dog-photos",ownerUID:uid)
                                }
                                if let pet {
                                    try await pets.updatePet(pet,name:name,breed:breed,age:age,species:species,description:description,photoURL:finalURL)
                                } else {
                                    try await pets.savePet(name:name,breed:breed,age:age,species:species,description:description,photoURL:finalURL)
                                }
                                dismiss()
                            } catch {self.error=error.localizedDescription}
                            saving=false
                        }
                    }.disabled(saving || name.trimmingCharacters(in:.whitespaces).isEmpty)
                }
            }
            .onChange(of:photoItem){item in
                guard let item else{return}
                Task {do{photoData=try await item.loadTransferable(type:Data.self)}catch{self.error=error.localizedDescription}}
            }
        }
    }
}

struct CareView:View {
    private let startWithNewEntry: Bool
    private let initialEntryType: String?

    init(startWithNewEntry: Bool = false, initialEntryType: String? = nil) {
        self.startWithNewEntry = startWithNewEntry
        self.initialEntryType = initialEntryType
    }

    @EnvironmentObject private var pets:PawlyPetStore
    @State private var passport:[PawlyPassportEntry]=[]
    @State private var tasks:[PawlyCareTask]=[]
    @State private var historyCategory="Todos"
    @State private var showEntry=false
    @State private var showTask=false
    @State private var showShare=false
    @State private var showDiary=false
    @State private var exportURL:URL?
    @State private var error:String?

    private var preferenceScope:String {
        "\(Auth.auth().currentUser?.uid ?? "guest").\(pets.selectedPetID ?? "no-pet")"
    }
    private func sectionKey(_ name:String)->String { "pawly.health.\(preferenceScope).\(name)" }
    private var historyCategories:[String] { ["Todos"] + Set(passport.map(\.type)).sorted() }
    private var filteredPassport:[PawlyPassportEntry] {
        passport.filter { historyCategory == "Todos" || $0.type == historyCategory }
            .sorted {
                if $0.date == $1.date { return $0.createdAtOrder > $1.createdAtOrder }
                return $0.date > $1.date
            }
    }
    private func passportDueDate(_ entry:PawlyPassportEntry)->Date? {
        guard !entry.nextDoseDate.isEmpty else { return nil }
        let formatter=DateFormatter()
        formatter.locale=Locale(identifier:"en_US_POSIX")
        formatter.timeZone=Calendar.current.timeZone
        formatter.dateFormat="yyyy-MM-dd"
        return formatter.date(from:entry.nextDoseDate)
    }
    private func computedPassportStatus(_ entry:PawlyPassportEntry)->String {
        if entry.status == "VENCIDO" { return "VENCIDO" }
        if let due=passportDueDate(entry) {
            return Calendar.current.startOfDay(for:due) < Calendar.current.startOfDay(for:Date())
                ? "VENCIDO" : "PENDIENTE"
        }
        return entry.status == "PENDIENTE" ? "PENDIENTE" : "REALIZADO"
    }
    private var upcomingEntries:[PawlyPassportEntry] {
        passport.filter { computedPassportStatus($0) != "REALIZADO" }
            .sorted {
                let a=passportDueDate($0) ?? Date.distantFuture
                let b=passportDueDate($1) ?? Date.distantFuture
                if a == b { return $0.createdAtOrder > $1.createdAtOrder }
                return a < b
            }
    }
    private var emergencyEntries:[PawlyPassportEntry] {
        let types:Set<String>=["Microchip","Alergia","Seguro","Veterinario habitual","Contacto de emergencia"]
        return passport.filter { types.contains($0.type) }
    }
    private var emergencyShareText:String {
        (["Ficha de emergencia de \(pets.selectedPet?.name ?? "mi mascota")"] +
         emergencyEntries.map { "\($0.type): \($0.detail.isEmpty ? $0.title : $0.detail)" })
            .joined(separator:"\n")
    }

    var body:some View {
        NavigationStack {
            Group {
                if pets.pets.isEmpty {
                    PawlyEmptyState(icon:"cross.case",title:"Añade a tu mascota",detail:"Su pasaporte sanitario y sus rutinas aparecerán aquí.")
                } else {
                    List {
                        Section {
                            HStack {
                                VStack(alignment:.leading,spacing:3) {
                                    Text("Pasaporte sanitario").font(.headline)
                                    Text("Vacunas, tratamientos, visitas y datos importantes").font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Button { showEntry=true } label:{Image(systemName:"plus.circle.fill").font(.title2)}
                                    .accessibilityLabel("Añadir registro")
                            }
                            PawlyPersistentDisclosureGroup(
                                title:"Historial",subtitle:"\(passport.count) registros",icon:"list.bullet.rectangle",
                                storageKey:sectionKey("history"),defaultExpanded:true
                            ) {
                                VStack(alignment:.leading,spacing:10) {
                                    ScrollView(.horizontal,showsIndicators:false) {
                                        HStack(spacing:7) {
                                            ForEach(historyCategories,id:\.self) { category in
                                                Button { historyCategory=category } label:{
                                                    Text(LocalizedStringKey(category)).font(.caption.weight(.semibold))
                                                        .foregroundStyle(historyCategory == category ? .white:PawlyColors.primary)
                                                        .padding(.horizontal,12).padding(.vertical,8)
                                                        .background(historyCategory == category ? PawlyColors.primary:PawlyColors.mint,in:Capsule())
                                                }.buttonStyle(.plain)
                                            }
                                        }
                                    }
                                    if filteredPassport.isEmpty {
                                        Text(passport.isEmpty ? "Todavía no hay registros. Añade la primera vacuna, revisión o tratamiento." : "No hay registros de esta categoría.")
                                            .font(.subheadline).foregroundStyle(.secondary).padding(.vertical,5)
                                    } else {
                                        ForEach(filteredPassport) { entry in
                                            VStack(alignment:.leading,spacing:5) {
                                                HStack(alignment:.firstTextBaseline) {
                                                    Text(entry.title).font(.subheadline.weight(.semibold))
                                                    Spacer()
                                                    Text(entry.type).font(.caption.weight(.semibold)).foregroundStyle(PawlyColors.primary)
                                                }
                                                Text("\(entry.date) · \(entry.detail)").font(.caption).foregroundStyle(.secondary)
                                                if !entry.product.isEmpty { Text("Producto: \(entry.product)").font(.caption) }
                                                if !entry.dose.isEmpty || !entry.frequency.isEmpty {
                                                    Text([entry.dose,entry.frequency].filter{!$0.isEmpty}.joined(separator:" · "))
                                                        .font(.caption).foregroundStyle(.secondary)
                                                }
                                                if !entry.clinic.isEmpty { Text("Clínica: \(entry.clinic)").font(.caption) }
                                                if !entry.nextDoseDate.isEmpty {
                                                    Label("Próxima dosis: \(entry.nextDoseDate)",systemImage:"calendar")
                                                        .font(.caption).foregroundStyle(PawlyColors.primary)
                                                }
                                                Text(statusLabel(entry.status))
                                                    .font(.caption2.weight(.bold))
                                                    .foregroundStyle(entry.status == "VENCIDO" ? .red:PawlyColors.primary)
                                            }
                                            .padding(11).frame(maxWidth:.infinity,alignment:.leading)
                                            .background(PawlyColors.canvas,in:RoundedRectangle(cornerRadius:13))
                                            .swipeActions {
                                                Button(role:.destructive) { removeEntry(entry) } label:{Label("Eliminar",systemImage:"trash")}
                                            }
                                        }
                                    }
                                }
                            }.id(sectionKey("history"))
                        }

                        Section {
                            PawlyPersistentDisclosureGroup(
                                title:"Próximas actuaciones",subtitle:"\(upcomingEntries.count) fechas y cuidados previstos",
                                icon:"calendar.badge.clock",storageKey:sectionKey("upcoming"),defaultExpanded:true
                            ) {
                                if upcomingEntries.isEmpty {
                                    Text("No hay próximas actuaciones registradas.")
                                        .font(.subheadline).foregroundStyle(.secondary)
                                } else {
                                    ForEach(upcomingEntries) { entry in
                                        HStack(alignment:.top,spacing:10) {
                                            Image(systemName:"calendar").foregroundStyle(PawlyColors.primary).frame(width:22)
                                            VStack(alignment:.leading,spacing:3) {
                                                Text(entry.title).font(.subheadline.weight(.semibold))
                                                Text("\(entry.type) · \(entry.nextDoseDate.isEmpty ? entry.date : entry.nextDoseDate)")
                                                    .font(.caption).foregroundStyle(.secondary)
                                            }
                                            Spacer()
                                            Text(statusLabel(computedPassportStatus(entry))).font(.caption2.weight(.bold))
                                                .foregroundStyle(computedPassportStatus(entry) == "VENCIDO" ? .red:PawlyColors.primary)
                                        }
                                    }
                                }
                            }.id(sectionKey("upcoming"))
                        }

                        Section {
                            PawlyPersistentDisclosureGroup(
                                title:"Ficha de emergencia",subtitle:"\(emergencyEntries.count) datos importantes",
                                icon:"cross.case.fill",storageKey:sectionKey("emergency"),defaultExpanded:true
                            ) {
                                if emergencyEntries.isEmpty {
                                    Text("Añade microchip, alergias, seguro y veterinario habitual para tener los datos importantes a mano.")
                                        .font(.subheadline).foregroundStyle(.secondary)
                                } else {
                                    ForEach(emergencyEntries) { entry in
                                        LabeledContent(entry.type,value:entry.detail.isEmpty ? entry.title : entry.detail)
                                    }
                                }
                                ShareLink(item:emergencyShareText) {
                                    Label("Compartir ficha",systemImage:"square.and.arrow.up")
                                }
                            }.id(sectionKey("emergency"))
                        }

                        if !weightEntries.isEmpty {
                            Section {
                                PawlyPersistentDisclosureGroup(
                                    title:"Evolución del peso",
                                    subtitle:"Últimas \(min(6,weightEntries.count)) mediciones · \(weightEntries.count) en total",
                                    icon:"chart.bar.fill",storageKey:sectionKey("weight"),defaultExpanded:true
                                ) {
                                    let chartPoints=Array(weightEntries.prefix(6).reversed())
                                    ScrollView(.horizontal,showsIndicators:false) {
                                        HStack(alignment:.bottom,spacing:10) {
                                            ForEach(Array(chartPoints.enumerated()),id:\.offset) { pair in
                                                let index=pair.offset
                                                let point=pair.element
                                                VStack(spacing:5) {
                                                    Text(String(format:"%.1f",point.1)).font(.caption.weight(.bold)).foregroundStyle(PawlyColors.ink)
                                                    Capsule()
                                                        .fill(index == chartPoints.count-1 ? PawlyColors.primary:PawlyColors.primary.opacity(0.38))
                                                        .frame(width:25,height:max(18,CGFloat(point.1/maximumWeight*72)))
                                                    Text(String(point.0.date.suffix(5))).font(.caption2).foregroundStyle(.secondary)
                                                }.frame(width:43)
                                            }
                                        }.padding(.vertical,6)
                                    }
                                    if let latest=weightEntries.first {
                                        Text("Último peso: \(String(format:"%.1f",latest.1)) kg")
                                            .font(.subheadline.weight(.semibold)).foregroundStyle(PawlyColors.primary)
                                    }
                                    Text("La gráfica utiliza únicamente registros de tipo Peso con fecha y valor numérico.")
                                        .font(.caption2).foregroundStyle(.secondary)
                                }.id(sectionKey("weight"))
                            }
                        }

                        Section {
                            PawlyPersistentDisclosureGroup(
                                title:"Historial de cuidados",subtitle:"Paseos, alimentación, medicación y otras acciones",
                                icon:"list.bullet.clipboard",storageKey:sectionKey("careLogs"),defaultExpanded:false
                            ) {
                                NavigationLink { PawlyCareLogsView() } label:{
                                    Label("Ver historial de cuidados",systemImage:"clock.arrow.circlepath")
                                }
                            }.id(sectionKey("careLogs"))
                        }

                        Section {
                            PawlyPersistentDisclosureGroup(
                                title:"Rutinas de cuidado",subtitle:"\(tasks.count) rutinas configuradas",
                                icon:"checklist",storageKey:sectionKey("routines"),defaultExpanded:true
                            ) {
                                if tasks.isEmpty {
                                    Text("Añade alimentación, paseos, medicación o revisiones.").foregroundStyle(.secondary)
                                } else {
                                    ForEach(tasks) { task in
                                        HStack(spacing:10) {
                                            Button { toggle(task) } label:{
                                                Image(systemName:task.completed ? "checkmark.circle.fill":"circle")
                                                    .foregroundStyle(PawlyColors.primary)
                                            }.buttonStyle(.plain)
                                            VStack(alignment:.leading,spacing:3) {
                                                Text(task.title).strikethrough(task.completed)
                                                Text([task.frequency,task.responsible].filter{!$0.isEmpty}.joined(separator:" · "))
                                                    .font(.caption).foregroundStyle(.secondary)
                                            }
                                        }.swipeActions {
                                            Button(role:.destructive) { removeTask(task) } label:{Label("Eliminar",systemImage:"trash")}
                                        }
                                    }
                                }
                                HStack {
                                    Spacer()
                                    Button { showTask=true } label:{Label("Añadir rutina",systemImage:"plus")}
                                }
                            }.id(sectionKey("routines"))
                        }
                        if let error { Section { Text(error).font(.caption).foregroundStyle(.red) } }
                    }.refreshable { await load() }
                }
            }
            .navigationTitle("Salud y cuidados")
            .toolbar {
                ToolbarItemGroup(placement:.topBarTrailing) {
                    Button { showDiary=true } label:{Image(systemName:"book.closed.fill")}.accessibilityLabel("Abrir diario")
                    if let exportURL { ShareLink(item:exportURL){Image(systemName:"square.and.arrow.up")} }
                    else { Button(action:exportPDF){Image(systemName:"square.and.arrow.up")} }
                    Button{showShare=true} label:{Image(systemName:"person.2.badge.plus")}
                    PetPicker()
                }
            }
            .task(id:pets.selectedPetID) {
                historyCategory="Todos"
                await load()
                if startWithNewEntry { showEntry = true }
            }
            .sheet(isPresented:$showEntry){PassportEntrySheet(initialType: initialEntryType, latestWeight: weightEntries.first?.1){entry in try await savePassportEntry(entry)}}
            .sheet(isPresented:$showTask){CareTaskSheet{task in await save(task)}}
            .sheet(isPresented:$showShare){PetSharingSheet()}
            .sheet(isPresented:$showDiary){DiaryView()}
        }
    }

    private func statusLabel(_ status:String)->String {
        switch status {
        case "PENDIENTE": return "Pendiente"
        case "VENCIDO": return "Vencido"
        default: return "Realizado"
        }
    }
    private func exportPDF() {
        guard let pet=pets.selectedPet else{return}
        Task {
            do {
                let entries=try await pets.loadPassport(pet)
                let routines=try await pets.loadTasks(pet)
                exportURL=try PawlyPDFExporter.create(pet:pet,passport:entries,tasks:routines)
                error=nil
            } catch { self.error=error.localizedDescription }
        }
    }
    @MainActor private func load() async {
        guard let pet=pets.selectedPet else { passport=[];tasks=[];return }
        do {
            async let p=pets.loadPassport(pet)
            async let t=pets.loadTasks(pet)
            passport=try await p
            tasks=try await t
            error=nil
        } catch { self.error=error.localizedDescription }
    }
    private func savePassportEntry(_ entry:PawlyPassportEntry) async throws {
        guard let pet=pets.selectedPet else { throw NSError(domain:"Pawly",code:1,userInfo:[NSLocalizedDescriptionKey:"Selecciona una mascota."]) }
        do {
            try await pets.savePassport(entry,pet:pet)
            let reminderID="health-\(pet.id)-\(entry.id)"
            if entry.reminderEnabled,
               let reminderDate=healthReminderDate(nextDoseDate:entry.nextDoseDate,time:entry.reminderTime),
               reminderDate>Date() {
                PawlyReminderStore.shared.upsert(PawlyLocalReminder(
                    id:reminderID,title:"Salud · \(entry.title)",petName:pet.name,date:reminderDate,
                    notes:entry.product.isEmpty ? "Próxima dosis o revisión":entry.product,category:"Veterinario"
                ))
            } else { PawlyReminderStore.shared.remove(id:reminderID) }
            await load()
        } catch { self.error=error.localizedDescription; throw error }
    }
    private func healthReminderDate(nextDoseDate:String,time:String)->Date? {
        let formatter=DateFormatter()
        formatter.locale=Locale(identifier:"en_US_POSIX");formatter.timeZone=TimeZone.current;formatter.dateFormat="yyyy-MM-dd"
        guard let day=formatter.date(from:nextDoseDate) else{return nil}
        let parts=time.split(separator:":").compactMap{Int($0)}
        guard parts.count==2,(0...23).contains(parts[0]),(0...59).contains(parts[1]) else{return nil}
        return Calendar.current.date(bySettingHour:parts[0],minute:parts[1],second:0,of:day)
    }
    private func save(_ task:PawlyCareTask) async {
        guard let pet=pets.selectedPet else{return}
        do{try await pets.saveTask(task,pet:pet);await load()}
        catch{self.error=error.localizedDescription}
    }
    private func removeEntry(_ entry:PawlyPassportEntry) {
        guard let pet=pets.selectedPet else{return}
        Task {
            do {
                try await pets.deletePassport(entry.id,pet:pet)
                PawlyReminderStore.shared.remove(id:"health-\(pet.id)-\(entry.id)")
                await load()
            } catch { self.error=error.localizedDescription }
        }
    }
    private func removeTask(_ task:PawlyCareTask) {
        guard let pet=pets.selectedPet else{return}
        Task { do{try await pets.deleteTask(task.id,pet:pet);await load()}catch{self.error=error.localizedDescription} }
    }
    private func toggle(_ task:PawlyCareTask) {
        guard let pet=pets.selectedPet else{return}
        Task {
            var updated=task
            updated.completed.toggle()
            let dayFormatter=DateFormatter()
            dayFormatter.locale=Locale(identifier:"en_US_POSIX")
            dayFormatter.timeZone=Calendar.current.timeZone
            dayFormatter.dateFormat="yyyy-MM-dd"
            updated.completedOn=updated.completed ? dayFormatter.string(from:Date()) : ""
            do{try await pets.saveTask(updated,pet:pet);await load()}
            catch{self.error=error.localizedDescription}
        }
    }
    private func parseWeight(_ entry:PawlyPassportEntry)->Double? {
        let raw="\(entry.title) \(entry.detail)"
        let range=NSRange(raw.startIndex...,in:raw)
        let unitPattern=try! NSRegularExpression(pattern:"(?<![\\d.,])(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*kg\\b",options:[.caseInsensitive])
        var captured:String?
        if let match=unitPattern.firstMatch(in:raw,range:range),
           let valueRange=Range(match.range(at:1),in:raw) {
            captured=String(raw[valueRange])
        } else {
            let standalone=try! NSRegularExpression(pattern:"^\\s*(?:peso\\s*[:=-]?\\s*)?(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*$",options:[.caseInsensitive])
            if let match=standalone.firstMatch(in:raw,range:range),
               let valueRange=Range(match.range(at:1),in:raw) {
                captured=String(raw[valueRange])
            }
        }
        guard let captured,let value=Double(captured.replacingOccurrences(of:",",with:".")),
              (0.1...150).contains(value) else { return nil }
        return value
    }
    private var weightEntries:[(PawlyPassportEntry,Double)] {
        passport.filter{$0.type.localizedCaseInsensitiveContains("peso")}.compactMap{entry in
            guard let value=parseWeight(entry) else{return nil}
            return(entry,value)
        }.sorted {
            if $0.0.date == $1.0.date { return $0.0.createdAtOrder > $1.0.createdAtOrder }
            return $0.0.date > $1.0.date
        }.prefix(8).map{$0}
    }
    private var maximumWeight:Double { weightEntries.map{$0.1}.max() ?? 1 }
}

struct PassportEntrySheet:View {
    @Environment(\.dismiss) private var dismiss
    var onSave:(PawlyPassportEntry) async throws->Void
    @State private var title=""
    @State private var type:String

    @State private var weight: Double
    @State private var saving = false
    @State private var saveError: String?
    private let fixedWeight: Bool

    init(initialType: String? = nil, latestWeight: Double? = nil, onSave: @escaping (PawlyPassportEntry) async throws -> Void) {
        self.onSave = onSave
        fixedWeight = initialType == "Peso"
        _weight = State(initialValue: latestWeight ?? 10)
        _type = State(initialValue: initialType ?? "Vacuna")
    }
    @State private var detail=""
    @State private var product=""
    @State private var dose=""
    @State private var frequency=""
    @State private var clinic=""
    @State private var nextDose=Calendar.current.date(byAdding:.month,value:12,to:Date()) ?? Date()
    @State private var date=Date()
    @State private var status="REALIZADO"
    @State private var reminderEnabled=false
    @State private var reminderTime=Calendar.current.date(from:DateComponents(hour:9,minute:0)) ?? Date()
    private var dateFormatter:DateFormatter {
        let formatter=DateFormatter();formatter.locale=Locale(identifier:"en_US_POSIX");formatter.dateFormat="yyyy-MM-dd";return formatter
    }
    private var timeFormatter:DateFormatter {
        let formatter=DateFormatter();formatter.locale=Locale(identifier:"en_US_POSIX");formatter.dateFormat="HH:mm";return formatter
    }
    var body:some View {
        NavigationStack {
            Form {
                Section("Registro sanitario") {
                    if !fixedWeight {
                        Picker("Tipo",selection:$type){ForEach(["Vacuna","Desparasitación","Medicación","Visita veterinaria","Revisión","Tratamiento","Alergia","Intervención","Peso","Microchip","Seguro","Veterinario habitual","Contacto de emergencia","Otro"],id:\.self){Text($0)}}
                    }
                    if type == "Peso" {
                        HStack(spacing: 20) {
                            Button { weight = max(0.1, (weight * 10 - 1).rounded() / 10) } label: {
                                Image(systemName:"minus.circle.fill").font(.largeTitle)
                            }.buttonStyle(.borderless).accessibilityLabel("Reducir peso")
                            Spacer()
                            VStack {
                                Text(weight, format: .number.precision(.fractionLength(1))).font(.system(size:36,weight:.bold,design:.rounded)).monospacedDigit()
                                Text("kg").font(.caption)
                            }
                            Spacer()
                            Button { weight = min(150, (weight * 10 + 1).rounded() / 10) } label: {
                                Image(systemName:"plus.circle.fill").font(.largeTitle)
                            }.buttonStyle(.borderless).accessibilityLabel("Aumentar peso")
                        }.foregroundStyle(PawlyColors.primary)
                        TextField("Peso en kg", value: $weight, format: .number).keyboardType(.decimalPad)
                    } else {
                        TextField("Título",text:$title)
                    }
                    DatePicker("Fecha del registro",selection:$date,displayedComponents:.date)
                    if type != "Peso" {
                        Picker("Estado",selection:$status) {
                            Text("Pendiente").tag("PENDIENTE")
                            Text("Realizado").tag("REALIZADO")
                            Text("Vencido").tag("VENCIDO")
                        }
                    }
                    TextField("Detalles",text:$detail,axis:.vertical).lineLimit(3...5)
                }
                if type != "Peso" {
                Section("Tratamiento y clínica") {
                    TextField("Producto o vacuna",text:$product)
                    TextField("Dosis",text:$dose)
                    TextField("Frecuencia o pauta",text:$frequency)
                    TextField("Clínica veterinaria",text:$clinic)
                }
                Section("Próxima dosis o revisión") {
                    DatePicker("Próxima fecha",selection:$nextDose,displayedComponents:.date)
                    Toggle("Recordarme",isOn:$reminderEnabled)
                    if reminderEnabled {
                        DatePicker("Hora del aviso",selection:$reminderTime,displayedComponents:.hourAndMinute)
                    }
                }
                }
                if let saveError { Text(saveError).foregroundStyle(.red) }
            }
            .navigationTitle(type == "Peso" ? "Registrar peso" : "Nuevo registro")
            .toolbar {
                ToolbarItem(placement:.cancellationAction) { Button("Cancelar") { dismiss() } }
                ToolbarItem(placement:.confirmationAction) {
                    Button(saving ? "Guardando…" : "Guardar") {
                        Task {
                            saving = true
                            defer { saving = false }
                            do {
                            try await onSave(PawlyPassportEntry(
                                id:UUID().uuidString,type:type,title:type == "Peso" ? String(format:"%.1f kg",weight) : title.trimmingCharacters(in:.whitespacesAndNewlines),
                                detail:detail.trimmingCharacters(in:.whitespacesAndNewlines),date:dateFormatter.string(from:date),
                                product:String(product.prefix(120)),dose:String(dose.prefix(120)),
                                frequency:String(frequency.prefix(120)),clinic:String(clinic.prefix(120)),
                                nextDoseDate:type == "Peso" ? "" : dateFormatter.string(from:nextDose),status:type == "Peso" ? "REALIZADO" : status,
                                reminderEnabled:type != "Peso" && reminderEnabled,reminderTime:timeFormatter.string(from:reminderTime)
                            ))
                            dismiss()
                            } catch { saveError = error.localizedDescription }
                        }
                    }.disabled(saving || (type == "Peso" ? !((0.1...150).contains(weight)) : title.trimmingCharacters(in:.whitespaces).isEmpty))
                }
            }
        }
    }
}

struct CareTaskSheet:View {
    @Environment(\.dismiss) private var dismiss
    var onSave:(PawlyCareTask) async->Void
    @State private var title=""
    @State private var responsible=""
    @State private var frequency="Diaria"
    @State private var dose=""
    @State private var time="09:00"
    @State private var completedOn=""
    @State private var action="Otro"
    @State private var selectedDays:Set<Int> = [1,2,3,4,5,6,7]
    private let actions=["Paseo","Comida","Medicación","Higiene","Otro"]
    private let weekdays=["L","M","X","J","V","S","D"]

    var body:some View {
        NavigationStack {
            Form {
                Section("Rutina") {
                    TextField("Tarea",text:$title)
                    TextField("Responsable",text:$responsible)
                    Picker("Tipo de cuidado",selection:$action) {
                        ForEach(actions,id:\.self) { Text($0) }
                    }
                    Picker("Frecuencia",selection:$frequency) {
                        ForEach(["Diaria","Semanal","Mensual","Cada 3 meses","Cada 6 meses","Anual","Cuando sea necesario"],id:\.self) { Text($0) }
                    }
                    if frequency == "Semanal" {
                        HStack(spacing: 5) {
                            ForEach(1...7, id: \.self) { day in
                                Button {
                                    if selectedDays.contains(day) { selectedDays.remove(day) }
                                    else { selectedDays.insert(day) }
                                } label: {
                                    Text(weekdays[day - 1])
                                        .font(.caption.weight(.semibold))
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 9)
                                        .background(selectedDays.contains(day) ? PawlyColors.primary : PawlyColors.mint,
                                                    in: Circle())
                                        .foregroundStyle(selectedDays.contains(day) ? .white : PawlyColors.ink)
                                }.buttonStyle(.plain)
                            }
                        }
                        Text("Selecciona los días de la semana.").font(.caption).foregroundStyle(.secondary)
                    }
                }
                Section("Detalles") {
                    TextField("Dosis o indicaciones (opcional)",text:$dose)
                    TextField("Hora (HH:mm)",text:$time)
                        .keyboardType(.numbersAndPunctuation)
                    TextField("Realizada el (AAAA-MM-DD, opcional)",text:$completedOn)
                        .keyboardType(.numbersAndPunctuation)
                }
            }
            .navigationTitle("Nueva rutina")
            .toolbar {
                ToolbarItem(placement:.cancellationAction) { Button("Cancelar") { dismiss() } }
                ToolbarItem(placement:.confirmationAction) {
                    Button("Guardar") {
                        Task {
                            await onSave(PawlyCareTask(
                                id:UUID().uuidString,
                                title:title.trimmingCharacters(in:.whitespacesAndNewlines),
                                responsible:responsible.trimmingCharacters(in:.whitespacesAndNewlines),
                                frequency:frequency,
                                completed:false,
                                dose:dose.trimmingCharacters(in:.whitespacesAndNewlines),
                                time:time,
                                completedOn:completedOn.trimmingCharacters(in:.whitespacesAndNewlines),
                                action:action,
                                daysOfWeek: frequency == "Semanal" ? selectedDays.sorted() : nil
                            ))
                            dismiss()
                        }
                    }
                    .disabled(title.trimmingCharacters(in:.whitespaces).isEmpty || dose.count > 80 || completedOn.count > 10 || time.count > 5 || (frequency == "Semanal" && selectedDays.isEmpty))
                }
            }
        }
    }
}

struct PetSharingSheet:View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var pets:PawlyPetStore
    @State private var username=""
    @State private var role="editor"
    @State private var pending:[PawlyPetInvitation]=[]
    @State private var status:String?
    @State private var busy=false
    var body:some View {
        NavigationStack {
            Form {
                Section("Compartir \(pets.selectedPet?.name ?? "mascota")") {
                    TextField("Nombre de usuario",text:$username).textInputAutocapitalization(.never).autocorrectionDisabled()
                    Picker("Permisos",selection:$role){Text("Puede editar").tag("editor");Text("Solo lectura").tag("reader")}
                    Button(busy ? "Enviando…" : "Enviar invitación"){Task{await invite()}}
                        .disabled(busy || pets.selectedPet == nil || pets.selectedPet?.ownerUID != nil || username.trimmingCharacters(in:.whitespaces).count<3)
                }
                Section("Invitaciones recibidas") {
                    if pending.isEmpty {Text("No tienes invitaciones pendientes.").font(.caption).foregroundStyle(.secondary)}
                    ForEach(pending) { invitation in
                        VStack(alignment:.leading,spacing:6) {
                            Text("\(invitation.ownerName) quiere compartir «\(invitation.petName)» contigo.")
                            Text(invitation.role=="reader" ? "Acceso de solo lectura":"Acceso de edición").font(.caption).foregroundStyle(.secondary)
                            HStack {
                                Button("Aceptar"){respond(invitation,true)}.buttonStyle(.borderedProminent).tint(PawlyColors.primary)
                                Button("Rechazar",role:.destructive){respond(invitation,false)}.buttonStyle(.bordered)
                            }
                        }.padding(.vertical,4)
                    }
                }
                if let status {Section{Text(status).font(.footnote).foregroundStyle(PawlyColors.muted)}}
            }
            .navigationTitle("Compartir perfil")
            .toolbar{ToolbarItem(placement:.confirmationAction){Button("Cerrar"){dismiss()}}}
            .task{await load()}
        }
    }
    @MainActor private func load() async {
        do{pending=try await pets.pendingInvitations()}catch{status=error.localizedDescription}
    }
    @MainActor private func invite() async {
        guard let pet=pets.selectedPet else{return}
        busy=true
        do{try await pets.invite(username,role:role,pet:pet);username="";status="Invitación enviada."}
        catch{status=error.localizedDescription}
        busy=false
    }
    private func respond(_ invitation:PawlyPetInvitation,_ accept:Bool) {
        Task {
            do{try await pets.respondToInvitation(invitation,accept:accept);await pets.loadPets();pending.removeAll{$0.id==invitation.id};status=accept ? "Perfil compartido añadido a tu cuenta.":"Invitación rechazada."}
            catch{status=error.localizedDescription}
        }
    }
}

struct DiaryView:View {
    private let openPhotoPickerOnEnter: Bool

    init(openPhotoPickerOnEnter: Bool = false) {
        self.openPhotoPickerOnEnter = openPhotoPickerOnEnter
    }

    @EnvironmentObject private var pets:PawlyPetStore
    @State private var entries:[PawlyDiaryEntry]=[]
    @State private var title=""
    @State private var note=""
    @State private var category="Paseos"
    @State private var date=Date()
    @State private var selectedPhotos:[PhotosPickerItem]=[]
    @State private var cameraPhotos:[Data]=[]
    @State private var showPhotoSources=false
    @State private var launchCamera=false
    @State private var showPhotoPicker=false
    @State private var diaryFilter="Todos"
    @State private var newestFirst=true
    @State private var busy=false
    @State private var error:String?

    private var preferenceScope:String { "\(Auth.auth().currentUser?.uid ?? "guest").\(pets.selectedPetID ?? "no-pet")" }
    private var editorStorageKey:String { "pawly.diary.\(preferenceScope).newMemory" }
    private var diaryCategories:[String] { ["Todos"] + Set(entries.map(\.category)).sorted() }
    private var filteredEntries:[PawlyDiaryEntry] {
        let matching=entries.filter { diaryFilter == "Todos" || $0.category == diaryFilter }
        return matching.sorted {
            let left=$0.date ?? $0.updatedAt ?? .distantPast
            let right=$1.date ?? $1.updatedAt ?? .distantPast
            return newestFirst ? left > right : left < right
        }
    }

    var body:some View {
        NavigationStack {
            Group {
                if pets.pets.isEmpty {
                    PawlyEmptyState(icon:"book.closed",title:"Tu diario empieza aquí",detail:"Añade una mascota para guardar recuerdos.")
                } else {
                    List {
                        Section {
                            PawlyPersistentDisclosureGroup(
                                title:"Un recuerdo nuevo",
                                subtitle:"Guarda los momentos especiales de vuestra historia.",
                                icon:"book.closed.fill",
                                storageKey:editorStorageKey,
                                defaultExpanded:true,
                                highlighted:true
                            ) {
                                VStack(alignment:.leading,spacing:12) {
                                    TextField("Título del recuerdo",text:$title)
                                        .textInputAutocapitalization(.sentences).textFieldStyle(.roundedBorder)
                                    TextField("Cuenta qué habéis hecho y qué lo hizo especial…",text:$note,axis:.vertical)
                                        .lineLimit(3...7).textFieldStyle(.roundedBorder)
                                    Picker("Categoría",selection:$category) {
                                        ForEach(["Paseos","Viajes","Juegos","Amigos","Celebraciones","Otros"],id:\.self){Text($0)}
                                    }
                                    DatePicker("Fecha del recuerdo",selection:$date,displayedComponents:.date)
                                    PhotosPicker(selection:$selectedPhotos,maxSelectionCount:max(1,8-cameraPhotos.count),matching:.images) {
                                        Label("Elegir de la galería",systemImage:"photo")
                                    }.disabled(cameraPhotos.count >= 8)
                                    PawlyCameraButton(requestCapture:$launchCamera) { data in if selectedPhotos.count+cameraPhotos.count < 8 { cameraPhotos.append(data) } }
                                        .disabled(selectedPhotos.count+cameraPhotos.count >= 8)
                                    if selectedPhotos.count+cameraPhotos.count > 0 {
                                        LabeledContent("Fotografías seleccionadas",value:"\(selectedPhotos.count+cameraPhotos.count)/8")
                                        Button("Quitar selección",role:.destructive){selectedPhotos=[];cameraPhotos=[]}.buttonStyle(.borderless)
                                    }
                                    if let error { Text(error).font(.caption).foregroundStyle(.red) }
                                    Button { Task { await add() } } label:{
                                        HStack {
                                            Spacer()
                                            if busy { ProgressView() }
                                            Label(busy ? "Guardando…":"Guardar recuerdo",systemImage:"plus.circle.fill")
                                            Spacer()
                                        }.padding(.vertical,5)
                                    }
                                    .buttonStyle(.borderedProminent).tint(PawlyColors.primary)
                                    .disabled(busy || (title.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty && note.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty && selectedPhotos.isEmpty && cameraPhotos.isEmpty))
                                }
                            }
                            .id(editorStorageKey)
                            .listRowBackground(PawlyColors.mint.opacity(0.45))
                        }
                        Section {
                            VStack(alignment:.leading,spacing:10) {
                                HStack {
                                    Text("Vuestra historia").font(.headline)
                                    Spacer()
                                    Text("\(filteredEntries.count) recuerdos").font(.caption.weight(.semibold)).foregroundStyle(PawlyColors.primary)
                                }
                                HStack {
                                    Picker("Filtrar recuerdos",selection:$diaryFilter) {
                                        ForEach(diaryCategories,id:\.self){Text(LocalizedStringKey($0)).tag($0)}
                                    }.pickerStyle(.menu)
                                    Spacer()
                                    Button { newestFirst.toggle() } label:{
                                        Label(newestFirst ? "Más recientes":"Más antiguos",systemImage:newestFirst ? "arrow.down":"arrow.up")
                                            .font(.caption.weight(.semibold))
                                    }.buttonStyle(.borderless)
                                }
                            }
                        }
                        if filteredEntries.isEmpty {
                            Section {
                                VStack(spacing:8) {
                                    Image(systemName:"book.closed.fill").font(.title2).foregroundStyle(PawlyColors.primary)
                                    Text(entries.isEmpty ? "Todavía no hay recuerdos. El primero siempre es especial." : "No hay recuerdos de esta categoría.")
                                        .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                                }.frame(maxWidth:.infinity).padding(.vertical,18)
                            }
                        } else {
                            Section {
                                ForEach(filteredEntries){entry in
                                    VStack(alignment:.leading,spacing:8) {
                                        HStack {
                                            VStack(alignment:.leading,spacing:3) {
                                                Text(entry.title.isEmpty ? "Un recuerdo especial":entry.title).font(.headline)
                                                Text("\(entry.category) · \((entry.date ?? entry.updatedAt ?? Date()).formatted(date:.abbreviated,time:.omitted))")
                                                    .font(.caption).foregroundStyle(PawlyColors.primary)
                                            }
                                            Spacer()
                                            Image(systemName:"pawprint.fill").foregroundStyle(PawlyColors.primary)
                                                .padding(9).background(PawlyColors.mint,in:Circle())
                                        }
                                        if !entry.text.isEmpty { Text(entry.text).font(.subheadline).foregroundStyle(PawlyColors.ink) }
                                        if !entry.photos.isEmpty {
                                            ScrollView(.horizontal,showsIndicators:false) {
                                                HStack(spacing:8) {
                                                    ForEach(entry.photos,id:\.self){url in
                                                        AsyncImage(url:URL(string:url)){image in image.resizable().scaledToFill()}
                                                            placeholder:{RoundedRectangle(cornerRadius:12).fill(PawlyColors.mint)}
                                                            .frame(width:150,height:115).clipShape(RoundedRectangle(cornerRadius:12))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .padding(.vertical,5)
                                    .swipeActions { Button(role:.destructive){remove(entry)}label:{Label("Eliminar",systemImage:"trash")} }
                                }
                            }
                        }
                    }
                    .refreshable{await load()}
                }
            }
            .navigationTitle("Recuerdos")
            .task(id:pets.selectedPetID) {
                diaryFilter="Todos"
                await load()
                if openPhotoPickerOnEnter { showPhotoSources = true }
            }
            .confirmationDialog("Añadir fotografía",isPresented:$showPhotoSources,titleVisibility:.visible) {
                Button("Elegir de la galería"){showPhotoPicker=true}
                Button("Hacer foto"){launchCamera=true}
                Button("Cancelar",role:.cancel){}
            } message:{Text("Elige la cámara o la galería del dispositivo.")}
            .photosPicker(isPresented:$showPhotoPicker,selection:$selectedPhotos,maxSelectionCount:max(1,8-cameraPhotos.count),matching:.images)
        }
    }

    @MainActor private func load() async {
        guard let pet=pets.selectedPet else { entries=[];return }
        do { entries=try await pets.loadDiary(pet);error=nil }
        catch { self.error=error.localizedDescription }
    }
    @MainActor private func add() async {
        guard let pet=pets.selectedPet,let uid=Auth.auth().currentUser?.uid else{return}
        busy=true;defer{busy=false}
        do {
            var urls:[String]=[]
            for item in selectedPhotos {
                guard let data=try await item.loadTransferable(type:Data.self) else{continue}
                urls.append(try await PawlyCloudinaryUploader().uploadImage(data,folder:"diary-photos",ownerUID:uid))
            }
            for data in cameraPhotos {
                urls.append(try await PawlyCloudinaryUploader().uploadImage(data,folder:"diary-photos",ownerUID:uid))
            }
            let payload:[String:Any] = [
                "pawlyDiaryVersion":1,
                "title":title.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty ? (note.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty ? "Un recuerdo especial" : note.trimmingCharacters(in:.whitespacesAndNewlines).prefix(48).description):title.trimmingCharacters(in:.whitespacesAndNewlines),
                "text":note.trimmingCharacters(in:.whitespacesAndNewlines),
                "category":category,
                "date":Int64(date.timeIntervalSince1970*1000),
                "photos":urls
            ]
            let data=try JSONSerialization.data(withJSONObject:payload,options:[.sortedKeys])
            guard let raw=String(data:data,encoding:.utf8) else{throw PawlyMediaError.invalidImage}
            try await pets.addDiaryEntry(raw,pet:pet)
            title="";note="";category="Paseos";selectedPhotos=[];cameraPhotos=[];await load()
        } catch let saveError {
            self.error = saveError.localizedDescription
        }
    }
    private func remove(_ entry:PawlyDiaryEntry) {
        guard let pet=pets.selectedPet else{return}
        Task { @MainActor in
            do {
                try await pets.deleteDiaryEntry(entry.id,pet:pet)
                await load()
            } catch let deleteError {
                self.error = deleteError.localizedDescription
            }
        }
    }
}

struct CommunityView:View {
    @StateObject private var store=PawlyCommunityStore()
    @EnvironmentObject private var pets:PawlyPetStore
    @EnvironmentObject private var pushRouter:PawlyPushRouter
    @State private var composer=false
    @State private var composerCategory="STORY"
    @State private var people=false
    @State private var inbox=false
    @State private var inboxConversationID:String?
    @State private var notifications=false
    @State private var comments:PawlyFeedPost?
    @State private var highlightedCommentID:String?
    @State private var lastHandledPushID:String?
    var body:some View {
        NavigationStack {
            ScrollView {
                VStack(spacing:13) {
                    HStack(spacing: 8) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text("La comunidad")
                                .font(.title2.bold())
                                .foregroundStyle(PawlyColors.ink)
                            Text("Historias y aventuras Pawly")
                                .font(.caption)
                                .foregroundStyle(PawlyColors.muted)
                                .lineLimit(1)
                                .minimumScaleFactor(0.85)
                        }
                        Spacer(minLength: 4)

                        Button { notifications = true } label: {
                            ZStack(alignment: .topTrailing) {
                                Image(systemName: "bell")
                                    .font(.headline)
                                    .foregroundStyle(PawlyColors.primary)
                                    .frame(width: 40, height: 40)
                                    .background(PawlyColors.mint, in: Circle())
                                if store.unreadNotificationCount > 0 {
                                    Text(store.unreadNotificationCount > 99 ? "99+" : "\(store.unreadNotificationCount)")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 4)
                                        .padding(.vertical, 2)
                                        .background(Color.red, in: Capsule())
                                        .offset(x: 3, y: -3)
                                }
                            }
                        }
                        .accessibilityLabel("Avisos de Comunidad, \(store.unreadNotificationCount) sin leer")

                        Button { inbox = true } label: {
                            ZStack(alignment: .topTrailing) {
                                Image(systemName: "bubble.left.and.bubble.right")
                                    .font(.headline)
                                    .foregroundStyle(PawlyColors.primary)
                                    .frame(width: 40, height: 40)
                                    .background(PawlyColors.mint, in: Circle())
                                if store.unreadMessageCount > 0 {
                                    Text(store.unreadMessageCount > 99 ? "99+" : "\(store.unreadMessageCount)")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 4)
                                        .padding(.vertical, 2)
                                        .background(Color.red, in: Capsule())
                                        .offset(x: 3, y: -3)
                                }
                            }
                        }
                        .accessibilityLabel("Mensajes, \(store.unreadMessageCount) sin leer")

                        Menu {
                            Button {
                                people = true
                            } label: {
                                Label("Buscar personas", systemImage: "magnifyingglass")
                            }
                            Button {
                                composerCategory = "LOST"
                                composer = true
                            } label: {
                                Label("Avisar de mascota perdida", systemImage: "pawprint.slash.fill")
                            }
                            Button {
                                composerCategory = "STORY"
                                composer = true
                            } label: {
                                Label("Crear publicación", systemImage: "plus")
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle.fill")
                                .font(.headline)
                                .foregroundStyle(PawlyColors.primary)
                                .frame(width: 40, height: 40)
                                .background(PawlyColors.mint, in: Circle())
                        }
                        .accessibilityLabel("Más acciones de Comunidad")
                    }
                    Button { people = true } label: {
                        HStack {
                            Label("Buscar personas", systemImage: "person.crop.circle.badge.plus")
                            Spacer()
                            Image(systemName: "chevron.right").font(.caption)
                        }
                        .font(.subheadline.weight(.semibold))
                        .padding(11).foregroundStyle(PawlyColors.primary)
                        .background(PawlyColors.surface, in: RoundedRectangle(cornerRadius: 14))
                    }.buttonStyle(.plain)
                    if let status=store.statusMessage{Text(status).font(.caption).foregroundStyle(.red)}
                    if store.posts.isEmpty{PawlyEmptyState(icon:"person.3",title:"Todavía no hay historias",detail:"Comparte una aventura para iniciar el muro.").frame(minHeight:260)}
                    ForEach(store.posts){post in
                        FeedPostCard(post:post,onLike:{Task{do{try await store.toggleLike(post)}catch { store.statusMessage = error.localizedDescription }}},onComment:{comments=post})
                    }
                }.padding(15)
            }.background(PawlyColors.canvas).navigationBarHidden(true)
                .onAppear {
                    store.listenFeed()
                    store.listenChats()
                    store.listenNotifications()
                    if let destination = pushRouter.destination {
                        Task { await handlePushDestination(destination) }
                    }
                }
                .onDisappear{
                    store.stopFeed()
                    store.stopChats()
                }
                .onChange(of:pushRouter.destination?.id) { _ in
                    guard let destination=pushRouter.destination else{return}
                    Task { await handlePushDestination(destination) }
                }
                .sheet(isPresented:$composer){PostComposerSheet(petName:pets.selectedPet?.name ?? "", initialCategory:composerCategory){text,category,imageURL,videoURL in try await store.publish(text:text,petName:pets.selectedPet?.name ?? "",category:category,imageURL:imageURL,videoURL:videoURL)}}
                .sheet(item:$comments){post in CommentsView(store:store,post:post,highlightedCommentID:highlightedCommentID)}
                .sheet(isPresented:$people){PeopleSearchSheet(store:store)}
                .sheet(isPresented:$inbox,onDismiss:{
                    inboxConversationID=nil
                    Task { await store.refreshUnreadMessageCounts() }
                }){MessagesView(initialConversationID: inboxConversationID)}
                .sheet(isPresented:$notifications){PawlyNotificationsView()}
        }
    }

    @MainActor private func handlePushDestination(_ destination:PawlyPushDestination) async {
        guard lastHandledPushID != destination.id else{return}
        lastHandledPushID=destination.id
        switch destination.type {
        case "MESSAGE":
            inboxConversationID = destination.relatedID.isEmpty ? nil : destination.relatedID
            inbox = true
        case "FOLLOW":
            notifications=true
        case "LIKE","COMMENT","LOST_PET":
            guard !destination.relatedID.isEmpty else {
                notifications=true
                pushRouter.consume(destination.id)
                return
            }
            do {
                if let post=try await store.loadPost(destination.relatedID) {
                    highlightedCommentID=destination.type == "COMMENT" ? destination.sourceEventID : nil
                    comments=post
                } else {
                    notifications=true
                    store.statusMessage="La publicación ya no está disponible."
                }
            } catch {
                notifications=true
                store.statusMessage=error.localizedDescription
            }
        default:
            notifications=true
        }
        pushRouter.consume(destination.id)
    }
}

private struct PawlyNotice: Identifiable {
    let id:String;let actorName:String;let actorID:String;let type:String;let message:String;let createdAt:Date?;let isRead:Bool
}

struct PawlyNotificationsView:View {
    @State private var notices:[PawlyNotice]=[]
    @State private var message:String?
    @State private var listener:ListenerRegistration?
    private var collection:CollectionReference? {
        guard let uid=Auth.auth().currentUser?.uid else{return nil}
        return Firestore.firestore().collection("users").document(uid).collection("notifications")
    }
    var body:some View {
        NavigationStack {
            List {
                if let message {Text(message).font(.caption).foregroundStyle(.red)}
                if notices.isEmpty {VStack(spacing:8){Image(systemName:"bell").font(.largeTitle).foregroundStyle(PawlyColors.primary);Text("Aún no hay avisos").font(.headline);Text("Aquí aparecerán las novedades de Comunidad.").font(.caption).foregroundStyle(.secondary)}.frame(maxWidth:.infinity).padding(.vertical,30).listRowSeparator(.hidden)}
                ForEach(notices) { notice in
                    Button { markRead(notice) } label: {
                        HStack(alignment:.top,spacing:11) {
                            Image(systemName:notice.type == "FOLLOW" ? "person.badge.plus":notice.type == "COMMENT" ? "text.bubble.fill":"heart.fill")
                                .foregroundStyle(PawlyColors.primary).frame(width:36,height:36).background(PawlyColors.mint,in:Circle())
                            VStack(alignment:.leading,spacing:4){Text(notice.message.isEmpty ? notice.actorName:notice.message).font(.subheadline.weight(notice.isRead ? .regular:.semibold)).foregroundStyle(.primary);Text(notice.createdAt?.formatted(date:.abbreviated,time:.shortened) ?? "Actividad reciente").font(.caption).foregroundStyle(.secondary)}
                            Spacer(minLength:0)
                            if !notice.isRead {Circle().fill(PawlyColors.primary).frame(width:8,height:8)}
                        }.padding(.vertical,4)
                    }.buttonStyle(.plain)
                }
            }
            .navigationTitle("Avisos")
            .toolbar { ToolbarItem(placement:.topBarTrailing) { Button("Marcar leídos",action:markAllRead).disabled(!notices.contains(where:{!$0.isRead})) } }
            .onAppear(perform:startListening).onDisappear{listener?.remove();listener=nil}
        }
    }
    private func startListening() {
        guard let collection else {message="Inicia sesión para ver tus avisos.";return}
        listener=collection.order(by:"createdAt",descending:true).limit(to:60).addSnapshotListener{snapshot,error in
            if let error {DispatchQueue.main.async{message=error.localizedDescription};return}
            let loaded=snapshot?.documents.map{doc in
                PawlyNotice(id:doc.documentID,actorName:doc.get("actorName") as? String ?? "Usuario Pawly",actorID:doc.get("actorId") as? String ?? "",type:doc.get("type") as? String ?? "",message:doc.get("message") as? String ?? "",createdAt:(doc.get("createdAt") as? Timestamp)?.dateValue(),isRead:doc.get("read") as? Bool ?? false)
            } ?? []
            DispatchQueue.main.async{notices=loaded;message=nil}
        }
    }
    private func markRead(_ notice:PawlyNotice) {
        guard !notice.isRead,let collection else{return}
        collection.document(notice.id).updateData(["read":true]){error in if let error{DispatchQueue.main.async{message=error.localizedDescription}}}
    }
    private func markAllRead() {
        guard let collection else{return}
        collection.whereField("read",isEqualTo:false).limit(to:60).getDocuments{snapshot,error in
            if let error {DispatchQueue.main.async{message=error.localizedDescription};return}
            let batch=Firestore.firestore().batch();snapshot?.documents.forEach{batch.updateData(["read":true],forDocument:$0.reference)}
            batch.commit{error in if let error{DispatchQueue.main.async{message=error.localizedDescription}}}
        }
    }
}

struct PeopleSearchSheet:View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var store:PawlyCommunityStore
    @State private var query=""
    @State private var results:[PawlyPublicUser]=[]
    @State private var loading=false
    @State private var showContacts=false
    @State private var status:String?
    @State private var blockCandidate:PawlyPublicUser?
    @State private var reportCandidate:PawlyPublicUser?
    var body:some View {
        NavigationStack {
            VStack(spacing:10) {
                HStack {
                    TextField("Buscar por nombre de usuario",text:$query).textInputAutocapitalization(.never).autocorrectionDisabled().textFieldStyle(.roundedBorder).onSubmit { search() }
                    Button("Buscar"){search()}.disabled(query.trimmingCharacters(in:.whitespaces).count<2)
                }.padding(.horizontal)
                DisclosureGroup(isExpanded:$showContacts) {
                    if showContacts {
                        PawlyContactDiscoveryView { username in
                            showContacts=false
                            query=username
                            if !username.isEmpty { search() }
                        }
                    }
                } label:{Label("Buscar en mis contactos",systemImage:"person.crop.rectangle")}
                    .padding(.horizontal)
                if loading {ProgressView()}
                if let status {Text(status).font(.caption).foregroundStyle(PawlyColors.muted).padding(.horizontal)}
                List(results) { user in
                    VStack(alignment:.leading,spacing:9) {
                        HStack(spacing:10) {
                            if let url=URL(string:user.photoURL),!user.photoURL.isEmpty {
                                AsyncImage(url:url){image in image.resizable().scaledToFill()}placeholder:{PawlyAvatarPlaceholder()}
                                    .frame(width:48,height:48).clipShape(Circle())
                            } else {
                                PawlyAvatarPlaceholder().frame(width:48,height:48)
                            }
                            VStack(alignment:.leading,spacing:3) {
                                Text(user.displayName).font(.headline)
                                Text("@\(user.username) · \(user.followersCount) seguidores").font(.caption).foregroundStyle(.secondary)
                                if !user.bio.isEmpty {Text(user.bio).font(.caption).foregroundStyle(PawlyColors.muted).lineLimit(2)}
                            }
                        }
                        HStack {
                            Button(user.following ? "Siguiendo" : "Seguir") {
                                Task {
                                    do {try await store.setFollowing(user,follow:!user.following);await searchNow()}
                                    catch {status=error.localizedDescription}
                                }
                            }.buttonStyle(.borderedProminent).tint(PawlyColors.primary)
                            Button("Mensaje") {
                                Task {do{_ = try await store.startConversation(username:user.username);status="Conversación creada. Ya aparece en la pestaña Mensajes."}catch{status=error.localizedDescription}}
                            }.buttonStyle(.bordered)
                            Menu {
                                Button(role:.destructive){blockCandidate=user} label:{Label("Bloquear",systemImage:"hand.raised")}
                                Button{reportCandidate=user} label:{Label("Denunciar",systemImage:"flag")}
                            } label:{Image(systemName:"ellipsis").padding(8)}
                        }
                    }.padding(.vertical,5)
                }
            }
            .navigationTitle("Personas Pawly")
            .toolbar{ToolbarItem(placement:.confirmationAction){Button("Cerrar"){dismiss()}}}
            .confirmationDialog("¿Bloquear a @\(blockCandidate?.username ?? "")?",isPresented:Binding(get:{blockCandidate != nil},set:{if !$0{blockCandidate=nil}}),titleVisibility:.visible) {
                Button("Bloquear",role:.destructive){if let user=blockCandidate{Task{do{try await store.blockUser(user);results.removeAll{$0.id==user.id};status="Cuenta bloqueada."}catch{status=error.localizedDescription}}};blockCandidate=nil}
            }
            .confirmationDialog("Motivo de la denuncia",isPresented:Binding(get:{reportCandidate != nil},set:{if !$0{reportCandidate=nil}}),titleVisibility:.visible) {
                ForEach(["Acoso o comportamiento abusivo","Spam o publicidad","Contenido inapropiado","Suplantación de identidad"],id:\.self){reason in
                    Button(reason){if let user=reportCandidate{Task{do{try await store.reportUser(user,reason:reason);status="Denuncia enviada para revisión."}catch{status=error.localizedDescription}}};reportCandidate=nil}
                }
            }
        }
    }
    private func search(){Task{await searchNow()}}
    @MainActor private func searchNow() async {
        loading=true
        do{results=try await store.searchUsers(query.trimmingCharacters(in:.whitespacesAndNewlines).trimmingCharacters(in:CharacterSet(charactersIn:"@")));status=results.isEmpty ? "No se han encontrado perfiles públicos.":nil}
        catch{status=error.localizedDescription}
        loading=false
    }
}

struct FeedPostCard:View {
    let post:PawlyFeedPost
    var onLike:()->Void
    var onComment:()->Void
    var body:some View {
        VStack(alignment:.leading,spacing:11){
            HStack{
                if let url=URL(string:post.authorPhoto),!post.authorPhoto.isEmpty {
                    AsyncImage(url:url){image in image.resizable().scaledToFill()}placeholder:{PawlyAvatarPlaceholder()}
                        .frame(width:38,height:38).clipShape(Circle())
                } else { PawlyAvatarPlaceholder().frame(width:38,height:38) }
                VStack(alignment:.leading,spacing:2){Text(post.authorName).font(.subheadline.weight(.semibold));Text(post.petName.isEmpty ? post.category : "\(post.petName) · \(post.category)").font(.caption).foregroundStyle(PawlyColors.muted)};Spacer();if let d=post.createdAt{Text(d.formatted(date:.abbreviated,time:.omitted)).font(.caption2).foregroundStyle(.secondary)}}
            if !post.text.isEmpty { Text(post.text).frame(maxWidth:.infinity,alignment:.leading) }
            if !post.imageURL.isEmpty, let url=URL(string:post.imageURL) {
                AsyncImage(url:url) { image in image.resizable().scaledToFill() } placeholder: { ProgressView() }
                    .frame(maxWidth:.infinity).frame(height:220).clipShape(RoundedRectangle(cornerRadius:14))
            }
            if !post.videoURL.isEmpty, let url=URL(string:post.videoURL) {
                VideoPlayer(player:AVPlayer(url:url)).frame(height:220).clipShape(RoundedRectangle(cornerRadius:14))
            }
            HStack(spacing:20){Button(action:onLike){Label("\(post.likeCount)",systemImage:post.likedByMe ? "heart.fill":"heart")}.foregroundStyle(post.likedByMe ? .pink:PawlyColors.muted);Button(action:onComment){Label("\(post.commentCount)",systemImage:"bubble.right")}.foregroundStyle(PawlyColors.muted)}
                .font(.subheadline.weight(.semibold))
        }.padding(15).background(PawlyColors.surface,in:RoundedRectangle(cornerRadius:19))
    }
}

struct PostComposerSheet:View {
    @Environment(\.dismiss) private var dismiss
    var petName:String
    var initialCategory:String = "STORY"
    var onPublish:(String,String,String,String) async throws->Void
    @State private var text=""
    @State private var category="STORY"
    @State private var busy=false
    @State private var error:String?
    @State private var imageItem:PhotosPickerItem?
    @State private var videoItem:PhotosPickerItem?
    @State private var cameraImage:Data?
    @State private var cameraVideo:Data?
    var body:some View {
        NavigationStack {
            Form {
                Picker("Tipo",selection:$category){Text("Historia").tag("STORY");Text("Adopción").tag("ADOPTION");Text("Perdido").tag("LOST");Text("Encontrado").tag("FOUND");Text("Acogida").tag("FOSTER")}
                TextField("¿Qué queréis compartir?",text:$text,axis:.vertical).lineLimit(5...10)
                Section("Fotografía") {
                    PhotosPicker(selection:$imageItem,matching:.images){Label("Elegir foto de la galería",systemImage:"photo")}
                    PawlyCameraButton { data in imageItem=nil;cameraImage=data }
                }
                Section("Vídeo") {
                    PhotosPicker(selection:$videoItem,matching:.videos){Label("Elegir vídeo de la galería",systemImage:"film")}
                    PawlyCameraButton(kind:.video,label:"Grabar vídeo"){data in videoItem=nil;cameraVideo=data}
                }
                if imageItem != nil || cameraImage != nil { Label("Fotografía seleccionada",systemImage:"checkmark.circle.fill").font(.caption).foregroundStyle(PawlyColors.primary) }
                if videoItem != nil || cameraVideo != nil { Label("Vídeo seleccionado",systemImage:"checkmark.circle.fill").font(.caption).foregroundStyle(PawlyColors.primary) }
                if !petName.isEmpty{LabeledContent("Mascota",value:petName)}
                if let error{Text(error).foregroundStyle(.red)}
                Text("Para publicar necesitas verificar tu correo.").font(.caption).foregroundStyle(.secondary)
            }
            .onChange(of:imageItem){item in if item != nil {cameraImage=nil}}
            .onChange(of:videoItem){item in if item != nil {cameraVideo=nil}}
            .navigationTitle("Nueva publicación").onAppear{category=initialCategory}.toolbar{
                ToolbarItem(placement:.cancellationAction){Button("Cancelar"){dismiss()}}
                ToolbarItem(placement:.confirmationAction){Button(busy ? "Publicando…" : "Publicar"){
                    Task {
                        busy=true
                        do {
                            guard let uid=Auth.auth().currentUser?.uid else { throw PawlyCommunityError.notSignedIn }
                            let uploader=PawlyCloudinaryUploader()
                            var imageURL="",videoURL=""
                            if let cameraImage {
                                imageURL=try await uploader.uploadImage(cameraImage,folder:"post-photos",ownerUID:uid)
                            } else if let item=imageItem {
                                guard let data=try await item.loadTransferable(type:Data.self) else { throw PawlyMediaError.invalidImage }
                                imageURL=try await uploader.uploadImage(data,folder:"post-photos",ownerUID:uid)
                            }
                            if let cameraVideo {
                                videoURL=try await uploader.uploadVideo(cameraVideo,folder:"post-videos",ownerUID:uid)
                            } else if let item=videoItem {
                                guard let data=try await item.loadTransferable(type:Data.self) else { throw PawlyMediaError.videoTooLarge }
                                videoURL=try await uploader.uploadVideo(data,folder:"post-videos",ownerUID:uid)
                            }
                            try await onPublish(text,category,imageURL,videoURL)
                            dismiss()
                        } catch { self.error=error.localizedDescription }
                        busy=false
                    }
                }.disabled(busy || (text.trimmingCharacters(in:.whitespaces).isEmpty && imageItem == nil && videoItem == nil && cameraImage == nil && cameraVideo == nil))}
            }
        }
    }
}

struct CommentsView:View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var store:PawlyCommunityStore
    let post:PawlyFeedPost
    var highlightedCommentID:String? = nil
    @State private var comments:[PawlyFeedComment]=[]
    @State private var draft=""
    @State private var listener:ListenerRegistration?
    @State private var error:String?
    var body:some View {
        NavigationStack {
            VStack {
                List {
                    Section {
                        VStack(alignment:.leading,spacing:8) {
                            HStack(spacing:8) {
                                Text(post.authorName).font(.subheadline.weight(.semibold))
                                Spacer()
                                Text(post.petName.isEmpty ? post.category : "\(post.petName) · \(post.category)")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            if !post.text.isEmpty { Text(post.text).font(.body) }
                            if !post.imageURL.isEmpty,let url=URL(string:post.imageURL) {
                                AsyncImage(url:url){image in image.resizable().scaledToFill()}placeholder:{ProgressView()}
                                    .frame(maxWidth:.infinity).frame(height:190).clipShape(RoundedRectangle(cornerRadius:12))
                            }
                            if !post.videoURL.isEmpty,let url=URL(string:post.videoURL) {
                                VideoPlayer(player:AVPlayer(url:url)).frame(height:190).clipShape(RoundedRectangle(cornerRadius:12))
                            }
                        }.padding(.vertical,5)
                    } header: { Text("Publicación") }
                    Section("Comentarios") {
                        ForEach(comments){c in
                            VStack(alignment:.leading,spacing:4){
                                Text(c.authorName).font(.caption.bold()).foregroundStyle(PawlyColors.primary)
                                Text(c.text)
                            }
                            .padding(8)
                            .background(c.id == highlightedCommentID ? PawlyColors.mint : Color.clear,in:RoundedRectangle(cornerRadius:10))
                            .id(c.id)
                        }
                    }
                }
                HStack{TextField("Escribe un comentario",text:$draft).textFieldStyle(.roundedBorder);Button("Enviar"){let v=draft;Task{do{try await store.addComment(postID:post.id,text:v);draft=""}catch { self.error = error.localizedDescription }}}.disabled(draft.trimmingCharacters(in:.whitespaces).isEmpty)}.padding()
                if let error{Text(error).font(.caption).foregroundStyle(.red)}
            }.navigationTitle("Comentarios").toolbar{ToolbarItem(placement:.confirmationAction){Button("Cerrar"){dismiss()}}}
                .onAppear{listener=store.listenComments(postID:post.id){comments=$0}}.onDisappear{listener?.remove()}
        }
    }
}

struct MessagesView:View {
    let initialConversationID: String?
    @StateObject private var store=PawlyCommunityStore()

    init(initialConversationID: String? = nil) {
        self.initialConversationID = initialConversationID
    }
    @State private var openedInitialConversation = false
    @State private var newChat=false
    @State private var selected:PawlyChat?
    var body:some View {
        NavigationStack {
            List {
                if store.chats.isEmpty{Text("Tu bandeja está vacía. Inicia una conversación con otra persona de Pawly.").foregroundStyle(.secondary)}
                ForEach(store.chats) { chat in
                    let unread=store.unreadMessageCounts[chat.id] ?? chat.unreadCount
                    Button {
                        selected = chat
                    } label: {
                        HStack(spacing:12) {
                            Image(systemName:"person.crop.circle.fill")
                                .font(.largeTitle)
                                .foregroundStyle(PawlyColors.primary)
                            VStack(alignment:.leading,spacing:4) {
                                HStack {
                                    Text(chat.otherUserName)
                                        .font(.headline.weight(unread > 0 ? .bold : .regular))
                                        .foregroundStyle(PawlyColors.ink)
                                    Spacer()
                                    if let updatedAt=chat.updatedAt {
                                        Text(updatedAt.formatted(date:.omitted,time:.shortened))
                                            .font(.caption2)
                                            .foregroundStyle(unread > 0 ? PawlyColors.primary : PawlyColors.muted)
                                    }
                                }
                                HStack(spacing:8) {
                                    Text(chat.lastMessage.isEmpty ? "Iniciar conversación" : chat.lastMessage)
                                        .font(.caption)
                                        .fontWeight(unread > 0 ? .bold : .regular)
                                        .foregroundStyle(PawlyColors.muted)
                                        .lineLimit(1)
                                    Spacer(minLength:4)
                                    if unread > 0 {
                                        Text(unread > 99 ? "99+" : "\(unread)")
                                            .font(.caption2.weight(.bold))
                                            .foregroundStyle(.white)
                                            .padding(.horizontal,7).padding(.vertical,3)
                                            .background(Color.red,in:Capsule())
                                    }
                                }
                            }
                        }
                        .padding(.vertical,4)
                        .padding(.horizontal,unread > 0 ? 5 : 0)
                        .background(unread > 0 ? PawlyColors.mint.opacity(0.45) : Color.clear,in:RoundedRectangle(cornerRadius:12))
                    }
                    .buttonStyle(.plain)
                }
            }.navigationTitle("Mensajes").toolbar{ToolbarItem(placement:.topBarTrailing){Button{newChat=true} label:{Image(systemName:"square.and.pencil")}}}
                .onAppear {
                    store.listenChats()
                    openInitialConversationIfAvailable()
                }
                .onChange(of: store.chats.map(\.id)) { _ in
                    openInitialConversationIfAvailable()
                }
                .onDisappear{store.stopChats()}
                .sheet(isPresented:$newChat){NewChatSheet{username in try await store.startConversation(username:username);store.listenChats()}}
                .sheet(item:$selected){chat in ChatView(store:store,chat:chat)}
        }
    }

    private func openInitialConversationIfAvailable() {
        guard !openedInitialConversation,
              let initialConversationID,
              let chat = store.chats.first(where: { $0.id == initialConversationID }) else { return }
        openedInitialConversation = true
        selected = chat
    }
}
struct NewChatSheet:View {
    @Environment(\.dismiss) private var dismiss
    var onStart:(String) async throws->Void
    @State private var username=""
    @State private var busy=false
    @State private var error:String?
    var body:some View{
        NavigationStack{Form{TextField("Nombre de usuario de Pawly",text:$username).textInputAutocapitalization(.never).autocorrectionDisabled();if let error{Text(error).foregroundStyle(.red)};Text("Puedes escribir a una cuenta pública ya registrada.").font(.caption).foregroundStyle(.secondary)}
            .navigationTitle("Nuevo mensaje").toolbar{ToolbarItem(placement:.cancellationAction){Button("Cancelar"){dismiss()}};ToolbarItem(placement:.confirmationAction){Button("Continuar"){Task{busy=true;do{try await onStart(username);dismiss()}catch{self.error=error.localizedDescription};busy=false}}.disabled(busy || username.trimmingCharacters(in:.whitespaces).count<3)}}}
    }
}
struct ChatView:View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var store:PawlyCommunityStore
    let chat:PawlyChat
    @State private var messages:[PawlyChatMessage]=[]
    @State private var draft=""
    @State private var error:String?
    @State private var listener:ListenerRegistration?
    @State private var imageItem:PhotosPickerItem?
    @State private var videoItem:PhotosPickerItem?
    @State private var cameraImage:Data?
    @State private var cameraVideo:Data?
    @State private var sending=false

    var body:some View {
        NavigationStack {
            VStack(spacing:0) {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing:9) {
                            ForEach(messages) { message in
                                messageRow(message).id(message.id)
                            }
                        }.padding(14)
                    }
                    .onChange(of:messages.count) { _ in
                        if let last=messages.last { proxy.scrollTo(last.id,anchor:.bottom) }
                        Task { await store.markRead(conversationID:chat.id) }
                    }
                }
                if let error { Text(error).font(.caption).foregroundStyle(.red) }
                composer
            }
            .background(PawlyColors.canvas)
            .navigationTitle(chat.otherUserName)
            .toolbar { ToolbarItem(placement:.confirmationAction) { Button("Cerrar") { dismiss() } } }
            .onAppear {
                store.listenMessages(conversationID:chat.id) { messages=$0 }
                Task { await store.markRead(conversationID:chat.id) }
            }
            .onDisappear { store.stopMessages() }
        }
    }

    @ViewBuilder private func messageRow(_ message:PawlyChatMessage) -> some View {
        HStack {
            if message.senderID == store.uid { Spacer(minLength: 28) }
            VStack(alignment:.leading,spacing:5) {
                if !message.text.isEmpty {
                    Text(message.text).foregroundStyle(message.senderID == store.uid ? .white : PawlyColors.ink)
                }
                if !message.imageURL.isEmpty, let url=URL(string:message.imageURL) {
                    AsyncImage(url:url) { image in image.resizable().scaledToFill() } placeholder: { ProgressView() }
                        .frame(width:190,height:150).clipShape(RoundedRectangle(cornerRadius:10))
                }
                if !message.videoURL.isEmpty, let url=URL(string:message.videoURL) {
                    VideoPlayer(player:AVPlayer(url:url)).frame(width:205,height:150)
                        .clipShape(RoundedRectangle(cornerRadius:10))
                }
                if message.senderID == store.uid && message.readBy.contains(chat.otherUserID) {
                    Text("Visto").font(.caption2).foregroundStyle(.white.opacity(0.7))
                }
            }
            .padding(12)
            .background(message.senderID == store.uid ? PawlyColors.primary : PawlyColors.surface,in:RoundedRectangle(cornerRadius:15))
            if message.senderID != store.uid { Spacer(minLength: 28) }
        }
    }

    private var composer:some View {
        HStack(spacing:10) {
            PhotosPicker(selection:$imageItem,matching:.images) {
                Image(systemName:"photo").font(.title3).foregroundStyle(PawlyColors.primary)
            }.accessibilityLabel("Foto de galería")
            PhotosPicker(selection:$videoItem,matching:.videos) {
                Image(systemName:"film").font(.title3).foregroundStyle(PawlyColors.primary)
            }.accessibilityLabel("Vídeo de galería")
            PawlyCameraButton { data in imageItem=nil;videoItem=nil;cameraVideo=nil;cameraImage=data }
                .labelStyle(.iconOnly)
            PawlyCameraButton(kind:.video,label:"Grabar vídeo") { data in imageItem=nil;videoItem=nil;cameraImage=nil;cameraVideo=data }
                .labelStyle(.iconOnly)
            TextField("Mensaje",text:$draft,axis:.vertical).lineLimit(1...4).textFieldStyle(.roundedBorder)
            Button(action:send) {
                Image(systemName:"arrow.up.circle.fill").font(.system(size:30)).foregroundStyle(PawlyColors.primary)
            }
            .disabled(sending || (draft.trimmingCharacters(in:.whitespaces).isEmpty && imageItem == nil && videoItem == nil && cameraImage == nil && cameraVideo == nil))
        }
        .padding()
        .onChange(of:imageItem) { _ in if imageItem != nil { videoItem=nil;cameraImage=nil;cameraVideo=nil } }
        .onChange(of:videoItem) { _ in if videoItem != nil { imageItem=nil;cameraImage=nil;cameraVideo=nil } }
        .safeAreaInset(edge:.top) {
            if imageItem != nil || videoItem != nil || cameraImage != nil || cameraVideo != nil {
                HStack {
                    Label("Archivo adjunto",systemImage:"paperclip").font(.caption)
                    Spacer()
                    Button("Quitar"){imageItem=nil;videoItem=nil;cameraImage=nil;cameraVideo=nil}.font(.caption)
                }.padding(.horizontal).background(PawlyColors.surface)
            }
        }
    }

    private func send() {
        guard !sending else {return}
        sending=true
        let value=draft
        Task {
            defer{sending=false}
            do {
                guard let uid=Auth.auth().currentUser?.uid else { throw PawlyCommunityError.notSignedIn }
                let uploader=PawlyCloudinaryUploader()
                var imageURL="",videoURL=""
                if let cameraImage {
                    imageURL=try await uploader.uploadImage(cameraImage,folder:"message-photos",ownerUID:uid)
                } else if let item=imageItem {
                    guard let data=try await item.loadTransferable(type:Data.self) else { throw PawlyMediaError.invalidImage }
                    imageURL=try await uploader.uploadImage(data,folder:"message-photos",ownerUID:uid)
                }
                if let cameraVideo {
                    videoURL=try await uploader.uploadVideo(cameraVideo,folder:"message-videos",ownerUID:uid)
                } else if let item=videoItem {
                    guard let data=try await item.loadTransferable(type:Data.self) else { throw PawlyMediaError.videoTooLarge }
                    videoURL=try await uploader.uploadVideo(data,folder:"message-videos",ownerUID:uid)
                }
                try await store.sendMessage(conversationID:chat.id,text:value,imageURL:imageURL,videoURL:videoURL)
                draft="";imageItem=nil;videoItem=nil;cameraImage=nil;cameraVideo=nil;error=nil
            } catch { self.error=error.localizedDescription }
        }
    }
}

struct AccountView:View {
    @EnvironmentObject private var session:PawlySession
    @EnvironmentObject private var pets:PawlyPetStore
    @State private var error:String?
    @State private var deletionStatus:String?
    @State private var showDeletionConfirmation=false
    var body:some View {
        NavigationStack {
            Form {
                Section("Cuenta") {
                    LabeledContent("Correo",value:session.user?.email ?? "—")
                    LabeledContent("Verificación",value:session.user?.isEmailVerified == true ? "Verificado":"Pendiente")
                    NavigationLink { PawlyProfileEditorView() } label:{Label("Editar perfil público",systemImage:"person.crop.circle")}
                    NavigationLink { PawlySettingsView() } label:{Label("Ajustes de Pawly",systemImage:"slider.horizontal.3")}
                }
                Section("Mascotas") {
                    ForEach(pets.pets) { pet in Text(pet.name) }
                    NavigationLink { PetSharingSheet() } label:{Label("Compartir una mascota",systemImage:"person.2.badge.plus")}
                }
                Section("Privacidad") {
                    Button("Solicitar eliminación de cuenta",role:.destructive){showDeletionConfirmation=true}
                    if let deletionStatus { Text(deletionStatus).font(.footnote).foregroundStyle(PawlyColors.muted) }
                }
                Section { Button("Cerrar sesión",role:.destructive){do{try session.signOut()}catch{self.error=error.localizedDescription}} }
                if let error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle("Mi cuenta")
            .confirmationDialog("¿Solicitar la eliminación de tu cuenta?",isPresented:$showDeletionConfirmation,titleVisibility:.visible) {
                Button("Enviar solicitud",role:.destructive) {
                    Task { do {
                        try await session.requestAccountDeletion()
                        deletionStatus="Solicitud registrada. El backend completará el borrado cuando esté desplegado."
                        error=nil
                    } catch { self.error=error.localizedDescription } }
                }
                Button("Cancelar",role:.cancel){}
            } message:{Text("La solicitud se procesa automáticamente solo cuando se ha desplegado la función de eliminación de Firebase.")}
        }
    }
}
struct PawlyEmptyState:View {
    let icon:String;let title:String;let detail:String
    var body:some View{VStack(spacing:12){Image(systemName:icon).font(.system(size:38)).foregroundStyle(PawlyColors.primary).padding(20).background(PawlyColors.mint,in:Circle());Text(title).font(.title3.bold()).foregroundStyle(PawlyColors.ink);Text(detail).font(.subheadline).multilineTextAlignment(.center).foregroundStyle(PawlyColors.muted).padding(.horizontal,28)}.frame(maxWidth:.infinity,maxHeight:.infinity).background(PawlyColors.canvas)}
}
