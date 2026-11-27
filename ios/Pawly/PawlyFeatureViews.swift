import SwiftUI
import FirebaseFirestore
import PhotosUI
import UIKit
import AVKit
import FirebaseAuth
import Combine

struct PawlyTabView: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var pets = PawlyPetStore()
    @StateObject private var pushRouter = PawlyPushRouter.shared
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            PawlyMyPetView().tabItem { Label("Mi perro", systemImage: "pawprint.fill") }.tag(0)
            CommunityView().tabItem { Label("Comunidad", systemImage: "person.3.fill") }.tag(3)
            PawlyExploreView().tabItem { Label("Explorar", systemImage: "safari.fill") }.tag(1)
            PawlyMoreHubView().tabItem { Label("Más", systemImage: "ellipsis") }.tag(4)
        }
        .environmentObject(pets)
        .environmentObject(pushRouter)
        .toolbarBackground(PawlyColors.surface, for:.tabBar)
        .toolbarBackground(.visible, for:.tabBar)
        .onReceive(pushRouter.$destination.compactMap { $0 }) { _ in
            selectedTab = 3
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active { PawlyReminderStore.shared.rescheduleAll() }
        }
        .task { await pets.loadPets(); pets.startListening() }
        .task(id: pets.selectedPet) {
            let reminders = PawlyReminderStore.shared
            reminders.loadForCurrentUser()
            reminders.stopListening()
            guard let pet = pets.selectedPet, let user = Auth.auth().currentUser, user.isEmailVerified else { return }
            do {
                try await pets.ensureSharedPet(pet)
                try Task.checkCancellation()
                reminders.listenAgenda(ownerUID: pet.ownerUID ?? user.uid, petID: pet.id, petName: pet.name)
            } catch { if !Task.isCancelled { reminders.statusMessage = PawlyServiceMessage.key(for: error) } }
        }
        .onDisappear { pets.stopListening(); PawlyReminderStore.shared.stopListening() }
    }
}

private struct PawlyMyPetView: View {
    @EnvironmentObject private var pets: PawlyPetStore
    @SceneStorage("pawly.myPet.section") private var section = 0

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 9) {
                PawlyPetHeader()
                HStack(spacing: 4) {
                    ForEach(Array(["Hoy", "Salud", "Diario", "Agenda"].enumerated()), id: \.offset) { index, label in
                        Button { section = index } label: {
                            Text(LocalizedStringKey(label))
                                .font(.subheadline.weight(section == index ? .semibold : .regular))
                                .lineLimit(1).minimumScaleFactor(0.8)
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .contentShape(Rectangle())
                                .foregroundStyle(section == index ? PawlyColors.primary : PawlyColors.ink)
                                .background(section == index ? PawlyColors.mint : Color.clear, in: RoundedRectangle(cornerRadius: 12))
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("pet.section." + String(index))
                        .accessibilityAddTraits(section == index ? .isSelected : [])
                    }
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(PawlyColors.surface)
            Divider()
            switch section {
            case 1: CareView().id(pets.selectedPetID)
            case 2: DiaryView().id(pets.selectedPetID)
            case 3:
                NavigationStack {
                    PawlyCalendarView().id(pets.selectedPetID)

                }
            default: HomeView()
            }
        }
        .background(PawlyColors.canvas)
    }
}

struct HomeView: View {
    @EnvironmentObject private var pets: PawlyPetStore
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let pet = pets.selectedPet {
                        PawlyHomeCareSection(pet: pet).id(pet)
                    } else {
                        PawlyEmptyState(
                            icon: "pawprint",
                            title: "Tu historia empieza aquí",
                            detail: "Añade el perfil de tu mascota para organizar sus cuidados y recuerdos."
                        )
                        .frame(minHeight: 230)
                    }

                    if let error = pets.errorMessage {
                        Text(LocalizedStringKey(error))
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
                    Picker("Especie",selection:$species){ForEach(["Perro","Gato","Conejo","Ave","Roedor","Reptil","Otro"],id:\.self){Text(LocalizedStringKey($0))}}
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
                        .frame(height:150).clipped().clipShape(RoundedRectangle(cornerRadius:14)).pawlyPhoto(url)
                    }
                }
                if let error {Text(LocalizedStringKey(error)).foregroundStyle(.red)}
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
                            } catch {self.error=PawlyServiceMessage.key(for: error)}
                            saving=false
                        }
                    }.disabled(saving || name.trimmingCharacters(in:.whitespaces).isEmpty)
                }
            }
            .onChange(of:photoItem){item in
                guard let item else{return}
                Task {do{photoData=try await item.loadTransferable(type:Data.self)}catch{self.error=PawlyServiceMessage.key(for: error)}}
            }
        }
    }
}

struct CareView:View {
    @Environment(\.locale) private var locale
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
    @State private var selectedType: String?
    @State private var dueDetail: PawlyPassportEntry?
    @State private var sharePDF = false
    @State private var showCareForm = false
    @State private var showTask=false
    @State private var showShare=false
    @State private var exportURL:URL?
    @State private var exportReady = false
    @State private var exporting = false
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
        let types:Set<String>=["Microchip","Alergia","Enfermedad","Medicación","Tratamiento","Seguro","Veterinario habitual","Contacto de emergencia"]
        return passport.filter { types.contains($0.type) }
    }
    private var emergencyShareText:String {
        ([PawlyLanguage.text("Ficha de emergencia", language:locale.identifier), pets.selectedPet?.name ?? "", pets.selectedPet?.breed ?? "", weightEntries.first.map { Measurement(value:$0.1,unit:UnitMass.kilograms).formatted() } ?? ""].filter { !$0.isEmpty } +
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

                            }
                            PawlyPersistentDisclosureGroup(
                                title:"Historial",subtitle:"\(passport.count) registros",icon:"list.bullet.rectangle",
                                storageKey:sectionKey("history"),defaultExpanded:true, onAdd: { selectedType=nil; showEntry=true }
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
                                                    Text(LocalizedStringKey(entry.type)).font(.caption.weight(.semibold)).foregroundStyle(PawlyColors.primary)
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
                                icon:"calendar.badge.clock",storageKey:sectionKey("upcoming"),defaultExpanded:true, onAdd: { selectedType="Revisión"; showEntry=true }
                            ) {
                                if upcomingEntries.isEmpty {
                                    Text("No hay próximas actuaciones registradas.")
                                        .font(.subheadline).foregroundStyle(.secondary)
                                } else {
                                    ForEach(upcomingEntries) { entry in
                                        Button { dueDetail=entry } label: { HStack(alignment:.top,spacing:10) {
                                            Image(systemName:"calendar").foregroundStyle(PawlyColors.primary).frame(width:22)
                                            VStack(alignment:.leading,spacing:3) {
                                                Text(entry.title).font(.subheadline.weight(.semibold))
                                                Text("\(Text(LocalizedStringKey(entry.type))) · \(entry.nextDoseDate.isEmpty ? entry.date : entry.nextDoseDate)")
                                                    .font(.caption).foregroundStyle(.secondary)
                                            }
                                            Spacer()
                                            Text(statusLabel(computedPassportStatus(entry))).font(.caption2.weight(.bold))
                                                .foregroundStyle(computedPassportStatus(entry) == "VENCIDO" ? .red:PawlyColors.primary)
                                        }
                                        .contentShape(Rectangle()) }.buttonStyle(.borderless)
                                    }
                                }
                            }.id(sectionKey("upcoming"))
                        }

                        Section {
                            PawlyPersistentDisclosureGroup(
                                title:"Ficha de emergencia",subtitle:"\(emergencyEntries.count) datos importantes",
                                icon:"cross.case.fill",storageKey:sectionKey("emergency"),defaultExpanded:true, onAdd: { selectedType="Contacto de emergencia"; showEntry=true }
                            ) {
                                if let pet = pets.selectedPet {
                                    HStack {
                                        if let url = URL(string: pet.photoURL), !pet.photoURL.isEmpty {
                                            AsyncImage(url:url) { $0.resizable().scaledToFill() } placeholder: { PawlyAvatarPlaceholder() }
                                                .frame(width:48,height:48).clipShape(Circle()).pawlyPhoto(url)
                                        }
                                        VStack(alignment:.leading) { Text(verbatim:pet.name).font(.headline)
                                            Text(verbatim:[pet.breed,pet.age].filter { !$0.isEmpty }.joined(separator:" · ")).font(.caption)
                                        }
                                    }
                                    if let kg = weightEntries.first?.1 { LabeledContent("Peso",value:Measurement(value:kg,unit:UnitMass.kilograms).formatted()) }
                                }
                                if emergencyEntries.isEmpty {
                                    Text("Añade microchip, alergias, seguro y veterinario habitual para tener los datos importantes a mano.")
                                        .font(.subheadline).foregroundStyle(.secondary)
                                } else {
                                    ForEach(emergencyEntries) { entry in
                                        LabeledContent(LocalizedStringKey(entry.type),value:entry.detail.isEmpty ? entry.title : entry.detail)
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
                                    icon:"chart.bar.fill",storageKey:sectionKey("weight"),defaultExpanded:true, onAdd: { selectedType="Peso"; showEntry=true }
                                ) {
                                    let chartPoints=Array(weightEntries.prefix(6).reversed())
                                    Group {
                                        HStack(alignment:.bottom,spacing:3) {
                                            ForEach(Array(chartPoints.enumerated()),id:\.offset) { pair in
                                                let index=pair.offset
                                                let point=pair.element
                                                VStack(spacing:5) {
                                                    Text(point.1, format: .number.precision(.fractionLength(1))).font(.caption.weight(.bold)).foregroundStyle(PawlyColors.ink)
                                                    Capsule()
                                                        .fill(index == chartPoints.count-1 ? PawlyColors.primary:PawlyColors.primary.opacity(0.38))
                                                        .frame(width:25,height:max(18,CGFloat(point.1/maximumWeight*72)))
                                                    Text(String(point.0.date.suffix(5))).font(.caption2).foregroundStyle(.secondary)
                                                }.frame(maxWidth: .infinity)
                                            }
                                        }.padding(.vertical,6)
                                    }
                                    if let latest=weightEntries.first {
                                        Text("Último peso: \(latest.1.formatted(.number.precision(.fractionLength(1)).locale(locale))) kg")
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
                                icon:"list.bullet.clipboard",storageKey:sectionKey("careLogs"),defaultExpanded:false, onAdd: { showCareForm=true }
                            ) {
                                NavigationLink { PawlyCareLogsView() } label:{
                                    Label("Ver historial de cuidados",systemImage:"clock.arrow.circlepath")
                                }
                            }.id(sectionKey("careLogs"))
                        }

                        Section {
                            PawlyPersistentDisclosureGroup(
                                title:"Rutinas de cuidado",subtitle:"\(tasks.count) rutinas configuradas",
                                icon:"checklist",storageKey:sectionKey("routines"),defaultExpanded:true, onAdd: { showTask=true }
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
                                                Text([PawlyLanguage.text(task.frequency, language: locale.identifier), task.responsible].filter{!$0.isEmpty}.joined(separator:" · "))
                                                    .font(.caption).foregroundStyle(.secondary)
                                            }
                                        }.swipeActions {
                                            Button(role:.destructive) { removeTask(task) } label:{Label("Eliminar",systemImage:"trash")}
                                        }
                                    }
                                }
                            }.id(sectionKey("routines"))
                        }
                        if let error { Section { Text(LocalizedStringKey(error)).font(.caption).foregroundStyle(.red) } }
                    }.refreshable { await load() }
                }
            }
            .navigationTitle("Salud y cuidados")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement:.topBarTrailing) {
                    Button {
                        if exportURL != nil { sharePDF=true } else { exportPDF() }
                    } label: { if exporting { ProgressView() } else { Image(systemName:"square.and.arrow.up") } }
                    .disabled(exporting || !exportReady).accessibilityLabel("Exportar PDF")
                    Button{showShare=true} label:{Image(systemName:"person.2.badge.plus")}
                }
            }
            .task(id:pets.selectedPetID) {
                historyCategory="Todos"
                await load()
                if startWithNewEntry { showEntry = true }
            }
            .sheet(isPresented:$showEntry){PassportEntrySheet(initialType: selectedType ?? initialEntryType, latestWeight: weightEntries.first?.1){entry in try await savePassportEntry(entry)}}
            .sheet(isPresented:$sharePDF) { if let exportURL { PawlyFileShareSheet(file: exportURL) } }
            .sheet(isPresented:$showCareForm) { PawlyCareLogsView(startWithForm: true) }
            .sheet(item:$dueDetail) { entry in
                NavigationStack { List {
                    Text(verbatim:entry.title).font(.headline)
                    if !entry.detail.isEmpty { Text(verbatim:entry.detail) }
                    if !entry.nextDoseDate.isEmpty { LabeledContent("Fecha",value:entry.nextDoseDate) }
                    ForEach([entry.clinic,entry.product,entry.dose,entry.frequency].enumerated().map { $0.offset },id:\.self) { i in
                        let value = [entry.clinic,entry.product,entry.dose,entry.frequency][i]
                        if !value.isEmpty { Text(verbatim:value) }
                    }
                    Button("Marcar como realizado") {
                        Task { do {
                            var done=entry; done.status="REALIZADO"; done.nextDoseDate=""; done.reminderEnabled=false
                            try await savePassportEntry(done); dueDetail=nil
                        } catch { self.error=PawlyServiceMessage.key(for:error); dueDetail=nil } }
                    }
                }.navigationTitle("Próxima actuación").toolbar { Button("Cerrar") { dueDetail=nil } } }
            }
            .sheet(isPresented:$showTask){CareTaskSheet{task in await save(task)}}
            .sheet(isPresented:$showShare){PetSharingSheet()}
        }
    }

    private func statusLabel(_ status:String)->LocalizedStringKey {
        switch status {
        case "PENDIENTE": return "Pendiente"
        case "VENCIDO": return "Vencido"
        default: return "Realizado"
        }
    }
    private func exportPDF() {
        guard let pet = pets.selectedPet, exportReady, !exporting else { return }
        let entries = passport, routines = tasks
        exporting = true
        Task {
            defer { exporting = false }
            do {
                let file = try await Task.detached(priority: .userInitiated) {
                    try PawlyPDFExporter.create(pet: pet, passport: entries, tasks: routines)
                }.value
                guard pets.selectedPet == pet, exportReady else { return }
                exportURL = file; sharePDF = true
                error = nil
            } catch { self.error = PawlyServiceMessage.key(for: error) }
        }
    }
    @MainActor private func load() async {
        exportReady = false
        exportURL = nil
        guard let pet = pets.selectedPet else { passport = []; tasks = []; return }
        do {
            async let p = pets.loadPassport(pet)
            async let t = pets.loadTasks(pet)
            let values = try await (p, t)
            guard !Task.isCancelled, pets.selectedPet == pet else { return }
            (passport, tasks) = values
            exportReady = true
            error = nil
        } catch { if !Task.isCancelled { self.error = PawlyServiceMessage.key(for: error) } }
    }
    private func savePassportEntry(_ entry:PawlyPassportEntry) async throws {
        guard let pet=pets.selectedPet else { throw NSError(domain:"Pawly",code:1,userInfo:[NSLocalizedDescriptionKey:"Selecciona una mascota."]) }
        do {
            try await pets.savePassport(entry,pet:pet)
            let reminderID="health-\(pet.id)-\(entry.id)"
            if entry.reminderEnabled,
               let reminderDate=healthReminderDate(nextDoseDate:entry.nextDoseDate,time:entry.reminderTime),
               reminderDate>Date() {
                try await PawlyReminderStore.shared.save(PawlyLocalReminder(
                    id:reminderID,title:"Salud · \(entry.title)",petName:pet.name,date:reminderDate,
                    notes:entry.product.isEmpty ? "Próxima dosis o revisión":entry.product,category:"Veterinario"
                ), pet: pet)
            } else if PawlyReminderStore.shared.items(for: pet).contains(where: { $0.id == reminderID }) {
                try await PawlyReminderStore.shared.remove(id: reminderID, pet: pet)
            }
            await load()
        } catch { self.error=PawlyServiceMessage.key(for: error); throw error }
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
        catch{self.error=PawlyServiceMessage.key(for: error)}
    }
    private func removeEntry(_ entry:PawlyPassportEntry) {
        guard let pet=pets.selectedPet else{return}
        Task {
            do {
                try await pets.deletePassport(entry.id,pet:pet)
                try await PawlyReminderStore.shared.remove(id:"health-\(pet.id)-\(entry.id)", pet: pet)
                await load()
            } catch { self.error=PawlyServiceMessage.key(for: error) }
        }
    }
    private func removeTask(_ task:PawlyCareTask) {
        guard let pet=pets.selectedPet else{return}
        Task { do{try await pets.deleteTask(task.id,pet:pet);await load()}catch{self.error=PawlyServiceMessage.key(for: error)} }
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
            catch{self.error=PawlyServiceMessage.key(for: error)}
        }
    }
    private var weightEntries:[(PawlyPassportEntry,Double)] {
        passport.filter{$0.type.localizedCaseInsensitiveContains("peso")}.compactMap{entry in
            guard let value=PawlyWeight.kilograms(title: entry.title, detail: entry.detail) else{return nil}
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
    @State private var recordID = UUID().uuidString
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
                Section(type == "Peso" ? "Peso" : "Registro sanitario") {
                    if !fixedWeight {
                        Picker("Tipo",selection:$type){ForEach(["Vacuna","Desparasitación","Medicación","Visita veterinaria","Revisión","Tratamiento","Alergia","Enfermedad","Intervención","Peso","Microchip","Seguro","Veterinario habitual","Contacto de emergencia","Otro"],id:\.self){Text(LocalizedStringKey($0))}}
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
                        Slider(value: $weight, in: 0.1...150, step: 0.1)
                            .accessibilityLabel("Peso en kg")
                            .accessibilityValue(Text(weight, format: .number.precision(.fractionLength(1))))
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
                                id:recordID,type:type,title:type == "Peso" ? String(format:"%.1f kg",weight) : title.trimmingCharacters(in:.whitespacesAndNewlines),
                                detail:detail.trimmingCharacters(in:.whitespacesAndNewlines),date:dateFormatter.string(from:date),
                                product:String(product.prefix(120)),dose:String(dose.prefix(120)),
                                frequency:String(frequency.prefix(120)),clinic:String(clinic.prefix(120)),
                                nextDoseDate:type == "Peso" ? "" : dateFormatter.string(from:nextDose),status:type == "Peso" ? "REALIZADO" : status,
                                reminderEnabled:type != "Peso" && reminderEnabled,reminderTime:timeFormatter.string(from:reminderTime)
                            ))
                            dismiss()
                            } catch { saveError = PawlyServiceMessage.key(for: error) }
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
    @Environment(\.calendar) private var calendar

    var body:some View {
        NavigationStack {
            Form {
                Section("Rutina") {
                    TextField("Tarea",text:$title)
                    TextField("Responsable",text:$responsible)
                    Picker("Tipo de cuidado",selection:$action) {
                        ForEach(actions,id:\.self) { Text(LocalizedStringKey($0)) }
                    }
                    Picker("Frecuencia",selection:$frequency) {
                        ForEach(["Diaria","Semanal","Mensual","Cada 3 meses","Cada 6 meses","Anual","Cuando sea necesario"],id:\.self) { Text(LocalizedStringKey($0)) }
                    }
                    if frequency == "Semanal" {
                        HStack(spacing: 5) {
                            ForEach(1...7, id: \.self) { day in
                                Button {
                                    if selectedDays.contains(day) { selectedDays.remove(day) }
                                    else { selectedDays.insert(day) }
                                } label: {
                                    Text(verbatim: calendar.veryShortStandaloneWeekdaySymbols[day % 7])
                                        .font(.caption.weight(.semibold))
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 9)
                                        .background(selectedDays.contains(day) ? PawlyColors.primary : PawlyColors.mint,
                                                    in: Circle())
                                        .foregroundStyle(selectedDays.contains(day) ? .white : PawlyColors.ink)
                                }.buttonStyle(.plain)
                                .accessibilityLabel(Text(verbatim: calendar.standaloneWeekdaySymbols[day % 7]))
                                .accessibilityAddTraits(selectedDays.contains(day) ? .isSelected : [])
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
                Section("Compartir mascotas") {
                    LabeledContent("Mascota") { PetPicker().disabled(busy) }
                    TextField("Nombre de usuario",text:$username).textInputAutocapitalization(.never).autocorrectionDisabled()
                    Picker("Permisos",selection:$role){Text("Puede editar").tag("editor");Text("Solo lectura").tag("reader")}
                    Button(busy ? "Enviando…" : "Enviar invitación"){Task{await invite()}}
                        .disabled(busy || pets.selectedPet == nil || pets.selectedPet?.ownerUID != nil || username.trimmingCharacters(in:.whitespaces).count<3)
                }
                PawlyRecipientPicker { person in username = person.username }
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
            .navigationTitle("Compartir mascotas")
            .toolbar{ToolbarItem(placement:.confirmationAction){Button("Cerrar"){dismiss()}}}
            .task{await load()}
        }
    }
    @MainActor private func load() async {
        do{pending=try await pets.pendingInvitations()}catch{status=PawlyServiceMessage.key(for: error)}
    }
    @MainActor private func invite() async {
        guard let pet=pets.selectedPet else{return}
        busy=true
        do{try await pets.invite(username,role:role,pet:pet);username="";status="Invitación enviada."}
        catch{status=PawlyServiceMessage.key(for: error)}
        busy=false
    }
    private func respond(_ invitation:PawlyPetInvitation,_ accept:Bool) {
        Task {
            do{try await pets.respondToInvitation(invitation,accept:accept);await pets.loadPets();pending.removeAll{$0.id==invitation.id};status=accept ? "Perfil compartido añadido a tu cuenta.":"Invitación rechazada."}
            catch{status=PawlyServiceMessage.key(for: error)}
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
    @State private var editorExpanded = true
    @State private var showPhotoSources=false
    @State private var launchCamera=false
    @State private var showPhotoPicker=false
    @State private var diaryFilter="Todos"
    @State private var pickedClips: [PhotosPickerItem] = []
    @State private var recordedClips: [PawlyRecordedClip] = []
    @State private var searchOpen = false
    @State private var search = ""
    @State private var newestFirst=true
    @State private var busy=false
    @State private var error:String?

    private var preferenceScope:String { "\(Auth.auth().currentUser?.uid ?? "guest").\(pets.selectedPetID ?? "no-pet")" }
    private var editorStorageKey:String { "pawly.diary.\(preferenceScope).newMemory" }
    private var diaryCategories:[String] { ["Todos"] + Set(entries.map(\.category)).sorted() }
    private var filteredEntries:[PawlyDiaryEntry] {
        let matching=entries.filter { entry in (diaryFilter == "Todos" || entry.category == diaryFilter) && (search.isEmpty || [entry.title,entry.text,entry.category].contains { $0.localizedStandardContains(search) }) }
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
                            Button {
                                editorExpanded.toggle()
                                UserDefaults.standard.set(editorExpanded, forKey: editorStorageKey)
                            } label: {
                                HStack {
                                    Label("Un recuerdo nuevo", systemImage: "book.closed.fill")
                                    Spacer()
                                    Image(systemName: editorExpanded ? "chevron.up" : "chevron.down")
                                }
                            }.buttonStyle(.borderless)
                            if editorExpanded {
                                    TextField("Título del recuerdo",text:$title)
                                        .textInputAutocapitalization(.sentences).textFieldStyle(.roundedBorder)
                                    TextField("Cuenta qué habéis hecho y qué lo hizo especial…",text:$note,axis:.vertical)
                                        .lineLimit(3...7).textFieldStyle(.roundedBorder)
                                    Picker("Categoría",selection:$category) {
                                        ForEach(["Paseos","Viajes","Juegos","Amigos","Celebraciones","Otros"],id:\.self){Text(LocalizedStringKey($0))}
                                    }
                                    .pickerStyle(.menu).buttonStyle(.borderless)
                                    DatePicker("Fecha del recuerdo",selection:$date,displayedComponents:.date)
                                    HStack(spacing: 18) {
                                    PhotosPicker(selection:$selectedPhotos,maxSelectionCount:max(1,8-cameraPhotos.count),matching:.images) {
                                        Label("Elegir de la galería",systemImage:"photo")
                                    }.buttonStyle(.borderless).disabled(cameraPhotos.count >= 8)
                                    PawlyCameraButton(requestCapture:$launchCamera) { data in if selectedPhotos.count+cameraPhotos.count < 8 { cameraPhotos.append(data) } }
                                        .disabled(selectedPhotos.count+cameraPhotos.count >= 8)
                                        PhotosPicker(selection: $pickedClips, maxSelectionCount: max(1, 8-recordedClips.count), matching: .videos) {
                                            Label("Elegir vídeo", systemImage: "film")
                                        }.disabled(recordedClips.count >= 8)
                                        PawlyCameraButton(kind: .video, label: "Grabar vídeo") { data in
                                            guard pickedClips.count + recordedClips.count < 8 else { return }
                                            do { recordedClips.append(try PawlyRecordedClip(data: data)) }
                                            catch { self.error = PawlyServiceMessage.key(for: error) }
                                        }.disabled(pickedClips.count + recordedClips.count >= 8)
                                    }.labelStyle(.iconOnly).buttonStyle(.borderless).disabled(busy)
                                    if selectedPhotos.count+cameraPhotos.count > 0 {
                                        PawlyPhotoSelection(gallery: $selectedPhotos, camera: $cameraPhotos)
                                        LabeledContent("Fotografías seleccionadas",value:"\(selectedPhotos.count+cameraPhotos.count)/8")
                                        Button("Quitar selección",role:.destructive){selectedPhotos=[];cameraPhotos=[]}.buttonStyle(.borderless)
                                    }
                                    if let error { Text(LocalizedStringKey(error)).font(.caption).foregroundStyle(.red) }
                                    if !pickedClips.isEmpty || !recordedClips.isEmpty {
                                        HStack {
                                            LabeledContent("Vídeo", value: "\(pickedClips.count + recordedClips.count)/8")
                                            Button(role: .destructive) { pickedClips = []; recordedClips = [] } label: { Label("Quitar", systemImage: "trash").labelStyle(.iconOnly) }
                                        }.disabled(busy)
                                    }

                            }
                        }
                        Section {
                            VStack(alignment:.leading,spacing:10) {
                                HStack {
                                    Text("Vuestra historia").font(.headline)
                                    Spacer()
                                    Text("\(filteredEntries.count) recuerdos").font(.caption.weight(.semibold)).foregroundStyle(PawlyColors.primary)
                                }
                                HStack {
                                    Button { searchOpen.toggle(); if !searchOpen { search = "" } } label: { Image(systemName:"magnifyingglass") }.accessibilityLabel("Buscar").buttonStyle(.borderless)
                                    if searchOpen { TextField("Buscar", text: $search).textFieldStyle(.roundedBorder) }
                                    Picker("Filtrar recuerdos",selection:$diaryFilter) {
                                        ForEach(diaryCategories,id:\.self){Text(LocalizedStringKey($0)).tag($0)}
                                    }.pickerStyle(.menu).labelsHidden().lineLimit(1)
                                    Spacer(minLength: 2)
                                    Button { newestFirst.toggle() } label:{
                                        Label(newestFirst ? "Más recientes":"Más antiguos",systemImage:newestFirst ? "arrow.down":"arrow.up").labelStyle(.iconOnly)
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
                                                Text("\(Text(LocalizedStringKey(entry.category))) · \(entry.date ?? entry.updatedAt ?? Date(), format: .dateTime.day().month(.abbreviated).year())")
                                                    .font(.caption).foregroundStyle(PawlyColors.primary)
                                            }
                                            Spacer()
                                            Image(systemName:"pawprint.fill").foregroundStyle(PawlyColors.primary)
                                                .padding(9).background(PawlyColors.mint,in:Circle())
                                        }
                                        if !entry.text.isEmpty { Text(entry.text).font(.subheadline).foregroundStyle(PawlyColors.ink) }
                                        PawlyDiaryGallery(photos: entry.photos, videos: entry.videoURLs)

                                    }
                                    .padding(.vertical,5)
                                    .swipeActions { Button(role:.destructive){remove(entry)}label:{Label("Eliminar",systemImage:"trash")} }
                                }
                            }
                        }
                    }
                    .listStyle(.plain).refreshable{await load()}
                }
            }
            .toolbar {
                if editorExpanded && !pets.pets.isEmpty {
                    ToolbarItem(placement:.cancellationAction) { Button("Cancelar") {
                        title=""; note=""; selectedPhotos=[]; cameraPhotos=[]; pickedClips=[]; recordedClips=[]; editorExpanded=false
                        UserDefaults.standard.set(false, forKey:editorStorageKey)
                    }.disabled(busy) }
                    ToolbarItem(placement:.confirmationAction) { Button(busy ? "Guardando…" : "Guardar") { Task { await add() } }
                        .disabled(busy || (title.isEmpty && note.isEmpty && selectedPhotos.isEmpty && cameraPhotos.isEmpty && pickedClips.isEmpty && recordedClips.isEmpty)) }
                }
            }
            .navigationTitle("Recuerdos")
            .navigationBarTitleDisplayMode(.inline)
            .task(id:pets.selectedPetID) {
                editorExpanded = openPhotoPickerOnEnter || (UserDefaults.standard.object(forKey: editorStorageKey) as? Bool ?? true)
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
        catch { self.error=PawlyServiceMessage.key(for: error) }
    }
    @MainActor private func add() async {
        guard let pet=pets.selectedPet,let uid=Auth.auth().currentUser?.uid else{return}
        busy=true;defer{busy=false}
        do {
            var urls:[String]=[]
            for item in selectedPhotos {
                guard let data=try await item.loadTransferable(type:Data.self) else{throw PawlyMediaError.invalidImage}
                urls.append(try await PawlyCloudinaryUploader().uploadImage(data,folder:"diary-photos",ownerUID:uid))
            }
            for data in cameraPhotos {
                urls.append(try await PawlyCloudinaryUploader().uploadImage(data,folder:"diary-photos",ownerUID:uid))
            }
            var clips: [String] = []
            for item in pickedClips {
                guard let movie = try await item.loadTransferable(type: PickedMovie.self) else { throw PawlyMediaError.unsupportedVideo }
                clips.append(try await PawlyAttachmentDraft.uploadDiaryClip(movie.data, owner: uid))
            }
            for recorded in recordedClips {
                clips.append(try await PawlyAttachmentDraft.uploadDiaryClip(Data(contentsOf: recorded.url, options: .mappedIfSafe), owner: uid))
            }
            let payload:[String:Any] = [
                "pawlyDiaryVersion":1,
                "title":title.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty ? (note.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty ? "Un recuerdo especial" : note.trimmingCharacters(in:.whitespacesAndNewlines).prefix(48).description):title.trimmingCharacters(in:.whitespacesAndNewlines),
                "text":note.trimmingCharacters(in:.whitespacesAndNewlines),
                "category":category,
                "date":Int64(date.timeIntervalSince1970*1000),
                "photos":urls, "videoUrls":clips, "videoUrl":clips.first ?? ""
            ]
            let data=try JSONSerialization.data(withJSONObject:payload,options:[.sortedKeys])
            guard let raw=String(data:data,encoding:.utf8) else{throw PawlyMediaError.invalidImage}
            try await pets.addDiaryEntry(raw,pet:pet)
            title="";note="";category="Paseos";selectedPhotos=[];cameraPhotos=[];pickedClips=[];recordedClips=[];await load()
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
    @State private var filter = PawlyFeedFilter()
    @State private var searching = false
    @State private var browsing = false
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

                        Button { people = true } label: {
                            Label("Buscar personas", systemImage: "magnifyingglass").labelStyle(.iconOnly).frame(width: 44, height: 44)
                        }
                    }
                    HStack {
                        Button { composerCategory = "STORY"; composer = true } label: {
                            Label("Crear publicación", systemImage: "plus")
                        }.buttonStyle(.borderedProminent)
                        Spacer()
                        Button { searching.toggle(); if !searching { filter.query = "" } } label: {
                            Label("Buscar publicaciones", systemImage: "text.magnifyingglass").labelStyle(.iconOnly)
                        }
                        Menu {
                            Picker("Tipo", selection: $filter.category) {
                                Text("Todas las categorías").tag("")
                                Text("Historia").tag("STORY")
                                Text("Adopción").tag("ADOPTION")
                                Text("Perdido").tag("LOST")
                                Text("Encontrado").tag("FOUND")
                                Text("Acogida").tag("FOSTER")
                            }
                            Picker("Ordenar", selection: $filter.order) {
                                ForEach(PawlyFeedOrder.allCases, id: \.self) { Text(LocalizedStringKey($0.rawValue)).tag($0) }
                            }
                            Button("Restablecer filtros") { filter = PawlyFeedFilter(); browsing = false }

                        } label: { Label("Filtrar", systemImage: filter == PawlyFeedFilter() ? "line.3.horizontal.decrease.circle" : "line.3.horizontal.decrease.circle.fill").labelStyle(.iconOnly) }
                    }
                    if let status=store.statusMessage{Text(LocalizedStringKey(status)).font(.caption).foregroundStyle(.red)}
                    if searching {
                        HStack {
                            TextField("Buscar publicaciones", text: $filter.query).textFieldStyle(.roundedBorder)
                            Button { filter.query = "" } label: { Label("Borrar", systemImage: "xmark.circle.fill").labelStyle(.iconOnly) }
                        }
                    }
                    if !store.feedPrivacyReady { ProgressView() }
                    else if filter != PawlyFeedFilter() || browsing {
                        PawlyFeedBrowser(filter: filter, store: store, initialPageSize: filter == PawlyFeedFilter() ? 90 : 40) { comments = $0 }.id(filter)
                    } else {
                        if store.posts.isEmpty { Text("Todavía no hay historias") }
                        ForEach(store.posts) { post in
                            FeedPostCard(post:post,onLike:{Task{do{try await store.toggleLike(post)}catch { store.statusMessage = PawlyServiceMessage.key(for: error) }}},onComment:{comments=post})
                        }
                        Button("Cargar más") { browsing = true }
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
                store.statusMessage=PawlyServiceMessage.key(for: error)
            }
        default:
            notifications=true
        }
        pushRouter.consume(destination.id)
    }
}

private struct PawlyNotice: Identifiable {
    let id:String;let actorName:String;let actorID:String;let type:String;let message:String;let createdAt:Date?;let isRead:Bool; let postID:String; let sourceID:String
}

struct PawlyNotificationsView:View {
    @StateObject private var community = PawlyCommunityStore()
    @State private var openedPost: PawlyFeedPost?
    @State private var highlight: String?
    @State private var opening = false
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
                if let message {Text(LocalizedStringKey(message)).font(.caption).foregroundStyle(.red)}
                if notices.isEmpty {VStack(spacing:8){Image(systemName:"bell").font(.largeTitle).foregroundStyle(PawlyColors.primary);Text("Aún no hay avisos").font(.headline);Text("Aquí aparecerán las novedades de Comunidad.").font(.caption).foregroundStyle(.secondary)}.frame(maxWidth:.infinity).padding(.vertical,30).listRowSeparator(.hidden)}
                ForEach(notices) { notice in
                    Button { open(notice) } label: {
                        HStack(alignment:.top,spacing:11) {
                            Image(systemName:notice.type == "FOLLOW" ? "person.badge.plus":notice.type == "COMMENT" ? "text.bubble.fill":"heart.fill")
                                .foregroundStyle(PawlyColors.primary).frame(width:36,height:36).background(PawlyColors.mint,in:Circle())
                            VStack(alignment:.leading,spacing:4){Text(LocalizedStringKey(notice.message.isEmpty ? notice.actorName:notice.message)).font(.subheadline.weight(notice.isRead ? .regular:.semibold)).foregroundStyle(.primary);Text(notice.createdAt?.formatted(date:.abbreviated,time:.shortened) ?? "Actividad reciente").font(.caption).foregroundStyle(.secondary)}
                            Spacer(minLength:0)
                            if !notice.isRead {Circle().fill(PawlyColors.primary).frame(width:8,height:8)}
                        }.padding(.vertical,4)
                    }.buttonStyle(.plain)
                }
            }
            .navigationTitle("Avisos")
            .toolbar { ToolbarItem(placement:.topBarTrailing) { Button("Marcar leídos",action:markAllRead).disabled(!notices.contains(where:{!$0.isRead})) } }
            .sheet(item: $openedPost) { post in CommentsView(store: community, post: post, highlightedCommentID: highlight) }
            .onAppear(perform:startListening).onDisappear{listener?.remove();listener=nil}
        }
    }
    private func startListening() {
        guard let collection else {message="Inicia sesión para ver tus avisos.";return}
        listener=collection.order(by:"createdAt",descending:true).limit(to:60).addSnapshotListener{snapshot,error in
            if let error {DispatchQueue.main.async{message=PawlyServiceMessage.key(for: error)};return}
            let loaded=snapshot?.documents.map{doc in
                PawlyNotice(id:doc.documentID,actorName:doc.get("actorName") as? String ?? "Usuario Pawly",actorID:doc.get("actorId") as? String ?? "",type:doc.get("type") as? String ?? "",message:doc.get("message") as? String ?? "",createdAt:(doc.get("createdAt") as? Timestamp)?.dateValue(),isRead:doc.get("read") as? Bool ?? false, postID:doc.get("relatedId") as? String ?? "", sourceID:doc.get("sourceEventId") as? String ?? "")
            } ?? []
            DispatchQueue.main.async{notices=loaded;message=nil}
        }
    }
    private func open(_ notice: PawlyNotice) {
        markRead(notice)
        guard !opening, ["LIKE","COMMENT","LOST_PET"].contains(notice.type), !notice.postID.isEmpty else { return }
        opening = true
        Task {
            defer { opening = false }
            do {
                if let post = try await community.loadPost(notice.postID) {
                    highlight = notice.type == "COMMENT" && !notice.sourceID.isEmpty ? notice.sourceID : nil
                    openedPost = post
                } else { message = "Esta publicación ya no está disponible." }
            } catch { message = PawlyServiceMessage.key(for: error) }
        }
    }
    private func markRead(_ notice:PawlyNotice) {
        guard !notice.isRead,let collection else{return}
        collection.document(notice.id).updateData(["read":true]){error in if let error{DispatchQueue.main.async{message=PawlyServiceMessage.key(for: error)}}}
    }
    private func markAllRead() {
        guard let collection else{return}
        collection.whereField("read",isEqualTo:false).limit(to:60).getDocuments{snapshot,error in
            if let error {DispatchQueue.main.async{message=PawlyServiceMessage.key(for: error)};return}
            let batch=Firestore.firestore().batch();snapshot?.documents.forEach{batch.updateData(["read":true],forDocument:$0.reference)}
            batch.commit{error in if let error{DispatchQueue.main.async{message=PawlyServiceMessage.key(for: error)}}}
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
                                NavigationLink { PawlyPublicProfile(userID: user.id) } label: { Text(verbatim: user.displayName).font(.headline) }
                                Text("@\(user.username) · \(user.followersCount) seguidores").font(.caption).foregroundStyle(.secondary)
                                if !user.bio.isEmpty {Text(user.bio).font(.caption).foregroundStyle(PawlyColors.muted).lineLimit(2)}
                            }
                        }
                        HStack {
                            Button(user.following ? "Siguiendo" : "Seguir") {
                                Task {
                                    do {try await store.setFollowing(user,follow:!user.following);await searchNow()}
                                    catch {status=PawlyServiceMessage.key(for: error)}
                                }
                            }.buttonStyle(.borderedProminent).tint(PawlyColors.primary)
                            Button("Mensaje") {
                                Task {do{_ = try await store.startConversation(username:user.username);status="Conversación creada. Ya aparece en la pestaña Mensajes."}catch{status=PawlyServiceMessage.key(for: error)}}
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
                Button("Bloquear",role:.destructive){if let user=blockCandidate{Task{do{try await store.blockUser(user);results.removeAll{$0.id==user.id};status="Cuenta bloqueada."}catch{status=PawlyServiceMessage.key(for: error)}}};blockCandidate=nil}
            }
            .confirmationDialog("Motivo de la denuncia",isPresented:Binding(get:{reportCandidate != nil},set:{if !$0{reportCandidate=nil}}),titleVisibility:.visible) {
                ForEach(["Acoso o comportamiento abusivo","Spam o publicidad","Contenido inapropiado","Suplantación de identidad"],id:\.self){reason in
                    Button(LocalizedStringKey(reason)){if let user=reportCandidate{Task{do{try await store.reportUser(user,reason:reason);status="Denuncia enviada para revisión."}catch{status=PawlyServiceMessage.key(for: error)}}};reportCandidate=nil}
                }
            }
        }
    }
    private func search(){Task{await searchNow()}}
    @MainActor private func searchNow() async {
        loading=true
        do{results=try await store.searchUsers(query.trimmingCharacters(in:.whitespacesAndNewlines).trimmingCharacters(in:CharacterSet(charactersIn:"@")));status=results.isEmpty ? "No se han encontrado perfiles públicos.":nil}
        catch{status=PawlyServiceMessage.key(for: error)}
        loading=false
    }
}

private extension PawlyFeedPost {
    var categoryCaption: Text {
        let key: LocalizedStringKey
        switch category {
        case "STORY": key = "Historia"
        case "ADOPTION": key = "Adopción"
        case "LOST": key = "Perdido"
        case "FOUND": key = "Encontrado"
        case "FOSTER": key = "Acogida"
        default: key = LocalizedStringKey(category)
        }
        return petName.isEmpty ? Text(key) : Text("\(petName) · \(Text(key))")
    }
}

struct FeedPostCard:View {
    let post:PawlyFeedPost
    var onLike:()->Void
    var onComment:()->Void
    var onEdited: (PawlyFeedPost) -> Void = { _ in }
    var body:some View {
        VStack(alignment:.leading,spacing:11){
            HStack{
                NavigationLink { PawlyPublicProfile(userID: post.authorId) } label: {
                    HStack {
                        PawlyChatAvatar(userID: post.authorId, name: post.authorName)
                        Text(verbatim: post.authorName).font(.subheadline.weight(.semibold))
                    }
                }
                VStack(alignment:.leading,spacing:2){post.categoryCaption.font(.caption).foregroundStyle(PawlyColors.muted)};Spacer();if let d=post.createdAt{Text(d.formatted(date:.abbreviated,time:.omitted)).font(.caption2).foregroundStyle(.secondary)}}
            if !post.text.isEmpty { Text(post.text).frame(maxWidth:.infinity,alignment:.leading) }
            if !post.imageURL.isEmpty, let url=URL(string:post.imageURL) {
                AsyncImage(url:url) { image in image.resizable().scaledToFill() } placeholder: { ProgressView() }
                    .frame(maxWidth:.infinity).frame(height:220).clipShape(RoundedRectangle(cornerRadius:14)).pawlyPhoto(url)
            }
            if !post.videoURL.isEmpty, let url=URL(string:post.videoURL) {
                PawlyVideoPreview(url: url, inline: true).frame(height:220).clipShape(RoundedRectangle(cornerRadius:14))
            }
            if post.category == "LOST" { PawlyLostPostActions(post: post, onEdited: onEdited) }
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
    @StateObject private var attachment = PawlyAttachmentDraft()
    var body:some View {
        NavigationStack {
            Form {
                Picker("Tipo",selection:$category){Text("Historia").tag("STORY");Text("Adopción").tag("ADOPTION");Text("Perdido").tag("LOST");Text("Encontrado").tag("FOUND");Text("Acogida").tag("FOSTER")}
                TextField("¿Qué queréis compartir?",text:$text,axis:.vertical).lineLimit(5...10)
                Section("Archivo adjunto") { PawlyAttachmentPicker(draft: attachment) }
                if busy { ProgressView("Publicando…") }
                if !petName.isEmpty{LabeledContent("Mascota",value:petName)}
                if let error{Text(LocalizedStringKey(error)).foregroundStyle(.red)}
            }
            .disabled(busy)
            .interactiveDismissDisabled(busy)
            .navigationTitle("Nueva publicación").onAppear{category=initialCategory}.toolbar{
                ToolbarItem(placement:.cancellationAction){Button("Cancelar"){dismiss()}.disabled(busy)}
                ToolbarItem(placement:.confirmationAction){Button(busy ? "Publicando…" : "Publicar"){
                    Task {
                        busy=true
                        do {
                            guard let uid=Auth.auth().currentUser?.uid else { throw PawlyCommunityError.notSignedIn }
                            let media = try await attachment.upload(owner: uid, forPost: true)
                            try await onPublish(text, category, media.image, media.video)
                            dismiss()
                        } catch { self.error=PawlyServiceMessage.key(for: error) }
                        busy=false
                    }
                }.disabled(busy || attachment.loading || text.count > 2000 || (text.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty && !attachment.hasAttachment))}
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
    @State private var limit = 40
    @State private var more = false
    @State private var loading = true
    @State private var sending = false
    @State private var expanded = Set<String>()
    @State private var target: PawlyFeedComment?
    @State private var focused: PawlyFeedComment?
    @FocusState private var composing: Bool

    private var roots: [String] {
        var seen = Set<String>()
        let ids = comments.map { $0.threadID ?? $0.id }
        return ((focused.map { [$0.threadID ?? $0.id] } ?? []) + ids).filter { seen.insert($0).inserted }
    }

    var body:some View {
        NavigationStack {
            VStack {
                List {
                    Section {
                        VStack(alignment:.leading,spacing:8) {
                            HStack(spacing:8) {
                                NavigationLink { PawlyPublicProfile(userID: post.authorId) } label: { Text(verbatim: post.authorName).font(.subheadline.weight(.semibold)) }
                                Spacer()
                                post.categoryCaption
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            if !post.text.isEmpty { Text(post.text).font(.body) }
                            if !post.imageURL.isEmpty,let url=URL(string:post.imageURL) {
                                AsyncImage(url:url){image in image.resizable().scaledToFill()}placeholder:{ProgressView()}
                                    .frame(maxWidth:.infinity).frame(height:190).clipShape(RoundedRectangle(cornerRadius:12)).pawlyPhoto(url)
                            }
                            if !post.videoURL.isEmpty,let url=URL(string:post.videoURL) {
                                PawlyVideoPreview(url: url).frame(height:190).clipShape(RoundedRectangle(cornerRadius:12))
                            }
                        }.padding(.vertical,5)
                    } header: { Text("Publicación") }
                    Section("Comentarios") {
                        if loading { ProgressView() }
                        ForEach(roots, id: \.self) { id in
                            thread(id)
                        }
                        if more { Button("Cargar más") { limit += 40; listen() } }
                    }
                }
                if let target {
                    HStack {
                        Image(systemName: "arrowshape.turn.up.left")
                        Text(verbatim: target.authorName)
                        Spacer()
                        Button("Cancelar") { self.target = nil }
                    }.font(.caption).padding(.horizontal)
                }
                HStack {
                    TextField("Escribe un comentario", text: $draft).textFieldStyle(.roundedBorder).focused($composing)
                    if sending { ProgressView() } else {
                        Button("Enviar") { Task { await send() } }
                            .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }.padding().disabled(sending)
                if let error{Text(LocalizedStringKey(error)).font(.caption).foregroundStyle(.red)}
            }.navigationTitle("Comentarios").toolbar{ToolbarItem(placement:.confirmationAction){Button("Cerrar"){dismiss()}}}
                .onAppear { listen() }.onDisappear { listener?.remove() }
                .task(id: highlightedCommentID) {
                    guard let id = highlightedCommentID else { return }
                    do {
                        focused = try await store.comment(postID: post.id, id: id)
                        if let root = focused?.threadID { expanded.insert(root) }
                    } catch { self.error = PawlyServiceMessage.key(for: error) }
                }
        }
    }
    @ViewBuilder private func thread(_ id: String) -> some View {
        let root = comments.first { $0.id == id } ?? (focused?.id == id ? focused : nil)
        let count = root?.replyCount ?? comments.filter { $0.threadID == id }.count
        VStack(alignment: .leading, spacing: 8) {
            if let root { row(root) } else { Text("Comentario anterior no disponible").foregroundStyle(.secondary) }
            if count > 0 || focused?.threadID == id {
                Button {
                    if !expanded.insert(id).inserted { expanded.remove(id) }
                } label: {
                    HStack { Image(systemName: expanded.contains(id) ? "chevron.up" : "chevron.down")
                        Text("Respuestas"); Text(verbatim: String(count)) }
                }.font(.caption).buttonStyle(.borderless)
                if expanded.contains(id) {
                    PawlyThreadReplies(store: store, postID: post.id, threadID: id,
                                       focused: focused?.threadID == id ? focused : nil, row: row)
                        .padding(.leading, 18)
                }
            }
        }.padding(.vertical, 4)
    }
    private func row(_ comment: PawlyFeedComment) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            NavigationLink { PawlyPublicProfile(userID: comment.authorId) } label: {
                Text(verbatim: comment.authorName).font(.subheadline.bold())
            }.buttonStyle(.borderless)
            if let name = comment.replyToName {
                Label { Text(verbatim: name) } icon: { Image(systemName: "arrowshape.turn.up.left") }
                    .font(.caption).foregroundStyle(.secondary)
            }
            Text(verbatim: comment.text)
            Button("Responder") { target = comment; composing = true }
                .font(.caption).buttonStyle(.borderless).disabled(sending)
        }.padding(4).background(comment.id == highlightedCommentID ? PawlyColors.mint : .clear,
                                in: RoundedRectangle(cornerRadius: 8))
    }
    private func listen() {
        listener?.remove()
        listener = store.listenComments(postID: post.id, limit: limit) { result in
            loading = false
            switch result {
            case .success(let page): comments = page.0; more = page.1
            case .failure(let failure): error = PawlyServiceMessage.key(for: failure)
            }
        }
    }
    @MainActor private func send() async {
        guard !sending else { return }
        sending = true; defer { sending = false }
        do {
            try await store.addComment(postID: post.id, text: draft, replyingTo: target)
            if let target { expanded.insert(target.threadID ?? target.id) }
            draft = ""; target = nil; error = nil; composing = false
        } catch { self.error = PawlyServiceMessage.key(for: error) }
    }
}

private struct PawlyThreadReplies<Row: View>: View {
    let store: PawlyCommunityStore
    let postID: String
    let threadID: String
    let focused: PawlyFeedComment?
    let row: (PawlyFeedComment) -> Row
    @State private var replies: [PawlyFeedComment] = []
    @State private var limit = 30
    @State private var more = false
    @State private var listener: ListenerRegistration?
    @State private var error: String?
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let focused, !replies.contains(where: { $0.id == focused.id }) { row(focused) }
            ForEach(replies) { row($0) }
            if more { Button("Cargar más") { limit += 30; listen() }.buttonStyle(.borderless) }
            if let error { Text(LocalizedStringKey(error)).font(.caption).foregroundStyle(.secondary) }
        }.onAppear { listen() }.onDisappear { listener?.remove() }
    }
    private func listen() {
        listener?.remove()
        listener = store.listenComments(postID: postID, threadID: threadID, limit: limit) { result in
            switch result {
            case .success(let page): replies = page.0; more = page.1; error = nil
            case .failure(let failure): error = PawlyServiceMessage.key(for: failure)
            }
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
                            PawlyChatAvatar(userID: chat.otherUserID, name: chat.otherUserName)
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
                                    (chat.lastMessage.isEmpty ? Text("Iniciar conversación") : Text(verbatim: chat.lastMessage))
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
        NavigationStack{Form{PawlyRecipientPicker { person in username = person.username };TextField("Nombre de usuario de Pawly",text:$username).textInputAutocapitalization(.never).autocorrectionDisabled();if let error{Text(LocalizedStringKey(error)).foregroundStyle(.red)};Text("Puedes escribir a una cuenta pública ya registrada.").font(.caption).foregroundStyle(.secondary)}
            .navigationTitle("Nuevo mensaje").toolbar{ToolbarItem(placement:.cancellationAction){Button("Cancelar"){dismiss()}};ToolbarItem(placement:.confirmationAction){Button("Continuar"){Task{busy=true;do{try await onStart(username);dismiss()}catch{self.error=PawlyServiceMessage.key(for: error)};busy=false}}.disabled(busy || username.trimmingCharacters(in:.whitespaces).count<3)}}}
    }
}
struct ChatView:View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var store:PawlyCommunityStore
    let chat:PawlyChat
    @State private var messages:[PawlyChatMessage]=[]
    @State private var draft=""
    @State private var error:String?
    private static let pageSize = 200
    @State private var messageLimit = ChatView.pageSize
    @State private var hasOlderMessages = false
    @State private var historyError: String?
    @State private var historyLoading = false
    @StateObject private var attachment = PawlyAttachmentDraft()
    @State private var sending=false

    var body:some View {
        NavigationStack {
            VStack(spacing:0) {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing:9) {
                            if let historyError {
                                Text(LocalizedStringKey(historyError)).foregroundStyle(.secondary)
                                Button("Reintentar") { listen() }
                            } else if historyLoading {
                                ProgressView()
                            } else if hasOlderMessages {
                                Button("Cargar más") {
                                    messageLimit += Self.pageSize
                                    listen()
                                }
                            }
                            ForEach(messages) { message in
                                messageRow(message).id(message.id)
                            }
                        }.padding(14)
                    }
                    .onChange(of: messages.last?.id) { newestID in
                        if let newestID { proxy.scrollTo(newestID, anchor: .bottom) }
                    }
                }
                if let error { Text(LocalizedStringKey(error)).font(.caption).foregroundStyle(.red) }
                composer
            }
            .background(PawlyColors.canvas)
            .navigationTitle(chat.otherUserName)
            .toolbar { ToolbarItem(placement: .principal) { NavigationLink { PawlyPublicProfile(userID: chat.otherUserID) } label: { HStack { PawlyChatAvatar(userID: chat.otherUserID, name: chat.otherUserName, size: 30); Text(verbatim: chat.otherUserName).font(.headline) } } } }
            .toolbar { ToolbarItem(placement:.confirmationAction) { Button("Cerrar") { dismiss() } } }
            .onAppear { listen() }
            .onDisappear { store.stopMessages() }
        }
    }

    private func listen() {
        historyLoading = true
        historyError = nil
        store.listenMessages(conversationID: chat.id, limit: messageLimit) { result in
            historyLoading = false
            switch result {
            case .success(let page):
                messages = page.0
                hasOlderMessages = page.1
                Task { await store.markRead(conversationID: chat.id, messages: page.0) }
            case .failure(let error):
                messages = []
                hasOlderMessages = false
                historyError = PawlyServiceMessage.key(for: error)
            }
        }
    }

    @ViewBuilder private func messageRow(_ message:PawlyChatMessage) -> some View {
        HStack {
            if message.senderID == store.uid { Spacer(minLength: 28) }
            VStack(alignment:.leading,spacing:5) {
                if let profile = message.sharedProfileUID {
                    NavigationLink { PawlyPublicProfile(userID: profile) } label: {
                        Label { Text(verbatim: message.text) } icon: { Image(systemName: "person.crop.rectangle") }
                            .foregroundStyle(message.senderID == store.uid ? .white : PawlyColors.ink)
                    }.buttonStyle(.borderless)
                } else if !message.text.isEmpty {
                    Text(message.text).foregroundStyle(message.senderID == store.uid ? .white : PawlyColors.ink)
                }
                if !message.imageURL.isEmpty, let url=URL(string:message.imageURL) {
                    AsyncImage(url:url) { image in image.resizable().scaledToFill() } placeholder: { ProgressView() }
                        .frame(width:190,height:150).clipShape(RoundedRectangle(cornerRadius:10)).pawlyPhoto(url)
                }
                if !message.videoURL.isEmpty, let url=URL(string:message.videoURL) {
                    PawlyVideoPreview(url: url).frame(width:205,height:150)
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
        VStack(alignment: .leading) {
            PawlyAttachmentPicker(draft: attachment)
            HStack {
                TextField("Mensaje", text: $draft, axis: .vertical).lineLimit(1...4).textFieldStyle(.roundedBorder)
                if sending { ProgressView() }
                else {
                    Button(action: send) { Label("Enviar", systemImage: "arrow.up.circle.fill").labelStyle(.iconOnly).font(.title) }
                        .disabled(attachment.loading || (draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !attachment.hasAttachment))
                }
            }
        }.padding(.horizontal).padding(.vertical, 5).disabled(sending)
    }

    private func send() {
        guard !sending else {return}
        sending=true
        let value=draft
        Task {
            defer{sending=false}
            do {
                guard let uid=Auth.auth().currentUser?.uid else { throw PawlyCommunityError.notSignedIn }
                guard Auth.auth().currentUser?.isEmailVerified == true else { throw PawlyCommunityError.verifyEmail }
                let media = try await attachment.upload(owner: uid, forPost: false)
                try await store.sendMessage(conversationID: chat.id, text: value, imageURL: media.image, videoURL: media.video)
                draft = ""
                attachment.clear()
                error = nil
            } catch { self.error=PawlyServiceMessage.key(for: error) }
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
                }
                Section("Mascotas") {
                    ForEach(pets.pets) { pet in Text(pet.name) }
                    NavigationLink { PetSharingSheet() } label:{Label("Compartir una mascota",systemImage:"person.2.badge.plus")}
                }
                Section("Privacidad") {
                    Button("Solicitar eliminación de cuenta",role:.destructive){showDeletionConfirmation=true}
                    if let deletionStatus { Text(deletionStatus).font(.footnote).foregroundStyle(PawlyColors.muted) }
                }
                Section { Button("Cerrar sesión",role:.destructive){do{try session.signOut()}catch{self.error=PawlyServiceMessage.key(for: error)}} }
                if let error { Text(LocalizedStringKey(error)).foregroundStyle(.red) }
            }
            .navigationTitle("Mi cuenta")
            .confirmationDialog("¿Solicitar la eliminación de tu cuenta?",isPresented:$showDeletionConfirmation,titleVisibility:.visible) {
                Button("Enviar solicitud",role:.destructive) {
                    Task { do {
                        try await session.requestAccountDeletion()
                        deletionStatus="Solicitud registrada. El backend completará el borrado cuando esté desplegado."
                        error=nil
                    } catch { self.error=PawlyServiceMessage.key(for: error) } }
                }
                Button("Cancelar",role:.cancel){}
            } message:{Text("La solicitud queda registrada para su revisión. El borrado de la cuenta y sus archivos está pendiente de confirmación.")}
        }
    }
}
struct PawlyEmptyState:View {
    let icon:String;let title:String;let detail:String
    var body:some View{VStack(spacing:12){Image(systemName:icon).font(.system(size:38)).foregroundStyle(PawlyColors.primary).padding(20).background(PawlyColors.mint,in:Circle());Text(LocalizedStringKey(title)).font(.title3.bold()).foregroundStyle(PawlyColors.ink);Text(LocalizedStringKey(detail)).font(.subheadline).multilineTextAlignment(.center).foregroundStyle(PawlyColors.muted).padding(.horizontal,28)}.frame(maxWidth:.infinity,maxHeight:.infinity).background(PawlyColors.canvas)}
}
