import SwiftUI
import MapKit
@preconcurrency import CoreLocation
import FirebaseAuth
import FirebaseFirestore
import PhotosUI
import UIKit

struct PawlyMoreHubView: View {
 @EnvironmentObject private var session:PawlySession
 private var preferenceScope:String { Auth.auth().currentUser?.uid ?? "guest" }
 private func sectionKey(_ name:String)->String { "pawly.more.\(preferenceScope).\(name)" }

 var body:some View {
  NavigationStack {
   List {
    Section {
     HStack(spacing:12) {
      PawlyAvatarPlaceholder().frame(width:50,height:50)
      VStack(alignment:.leading,spacing:3) {
       Text("Tu espacio Pawly").font(.headline).foregroundStyle(PawlyColors.ink)
       Text(session.user?.displayName ?? session.user?.email ?? "Tu compañero y todos vuestros recursos")
        .font(.caption).foregroundStyle(.secondary)
      }
     }.padding(.vertical,6)
    }
    Section {
     PawlyPersistentDisclosureGroup(
      title:"Tu día a día", subtitle:"Diario, agenda, eventos y recursos", icon:"pawprint.fill",
      storageKey:sectionKey("daily"), defaultExpanded:true
     ) {
      NavigationLink { DiaryView() } label:{Label("Diario de recuerdos",systemImage:"book.closed.fill")}
      NavigationLink { PawlyLearningHubView() } label:{Label("Aprender y descubrir",systemImage:"graduationcap.fill")}
      NavigationLink { PawlyCalendarView() } label:{Label("Calendario de cuidados",systemImage:"calendar")}
      NavigationLink { PawlyEventsView() } label:{Label("Eventos y quedadas",systemImage:"calendar.badge.plus")}
      NavigationLink { PawlyCommunityPlacesView() } label:{Label("Lugares de la comunidad",systemImage:"map.pin.and.ellipse")}
      NavigationLink { PawlyRemindersView() } label:{Label("Agenda y recordatorios",systemImage:"calendar.badge.clock")}
      NavigationLink { PawlyExpensesView() } label:{Label("Gastos de mi mascota",systemImage:"receipt")}
      NavigationLink { MessagesView() } label:{Label("Mensajes privados",systemImage:"bubble.left.and.bubble.right.fill")}
      NavigationLink { PawlyProfessionalDirectoryView() } label:{Label("Profesionales",systemImage:"stethoscope")}
      NavigationLink { PawlyMarketView() } label:{Label("Pawly Market",systemImage:"bag.fill")}
      NavigationLink { PawlyLostPetView() } label:{Label("He perdido mi mascota",systemImage:"pawprint")}
     }.id(sectionKey("daily"))
    }
    Section {
     PawlyPersistentDisclosureGroup(
      title:"Personas y mascotas", subtitle:"Perfiles, compartición y privacidad", icon:"person.2.fill",
      storageKey:sectionKey("people"), defaultExpanded:false
     ) {
      NavigationLink { PawlyProfileEditorView() } label:{Label("Mi perfil público",systemImage:"person.crop.circle")}
      NavigationLink { PetSharingSheet() } label:{Label("Compartir mascotas",systemImage:"person.2.badge.plus")}
      NavigationLink { AccountView() } label:{Label("Cuenta y privacidad",systemImage:"person.badge.key.fill")}
      NavigationLink { PawlyBlockedUsersView() } label:{Label("Usuarios bloqueados",systemImage:"hand.raised")}
      NavigationLink { PawlyBackupView() } label:{Label("Copias de seguridad",systemImage:"externaldrive")}
     }.id(sectionKey("people"))
    }
    Section {
     PawlyPersistentDisclosureGroup(
      title:"Aplicación", subtitle:"Apariencia, información y administración", icon:"slider.horizontal.3",
      storageKey:sectionKey("app"), defaultExpanded:false
     ) {
      NavigationLink { PawlySettingsView() } label:{Label("Ajustes y apariencia",systemImage:"slider.horizontal.3")}
      NavigationLink { PawlyAboutView() } label:{Label("Acerca de Pawly",systemImage:"pawprint")}
      NavigationLink { PawlyModerationView() } label:{Label("Centro de moderación",systemImage:"shield.lefthalf.filled")}
     }.id(sectionKey("app"))
    }
    Section {
     Button(role:.destructive){try? session.signOut()} label:{Label("Cerrar sesión",systemImage:"rectangle.portrait.and.arrow.right")}
    }
   }
   .navigationTitle("Tu espacio Pawly")
  }
 }
}

struct PawlySettingsView:View {
 @AppStorage("pawly.darkMode") private var darkMode=false
 @AppStorage("pawly.accent") private var accent="forest"
 @AppStorage("pawly.designStyle") private var designStyle="elegant"
 @AppStorage("pawly.language") private var language="es"
 @AppStorage("pawly.notificationsEnabled") private var notificationsEnabled=true
 @StateObject private var reminders=PawlyReminderStore.shared
 @State private var message:String?
 private let accents:[(String,String,Color)]=[
  ("forest","Bosque",Color(red:0.17,green:0.48,blue:0.39)),("ocean","Océano",Color(red:0.10,green:0.39,blue:0.57)),
  ("berry","Baya",Color(red:0.48,green:0.29,blue:0.50)),("sunset","Atardecer",Color(red:0.72,green:0.34,blue:0.22))]
 var body:some View {
  Form {
   Section("Diseño") {
    Picker("Estilo",selection:$designStyle){Text("Simple").tag("simple");Text("Elegante").tag("elegant")}
    Toggle("Modo oscuro",isOn:$darkMode)
    VStack(alignment:.leading,spacing:12) {
     Text("Color principal")
     HStack(spacing:18){ForEach(accents.indices,id:\.self){index in
      let item=accents[index]
      Button{accent=item.0}label:{Circle().fill(item.2).frame(width:38,height:38)
       .overlay{if accent==item.0{Image(systemName:"checkmark").foregroundStyle(.white).font(.headline.bold())}}
       .overlay(Circle().stroke(accent==item.0 ? Color.primary:.clear,lineWidth:2).padding(-4))
       .accessibilityLabel(item.1)}.buttonStyle(.plain)
     }}
    }.padding(.vertical,5)
   }
   Section("Idioma") {
    Picker("Idioma preferido",selection:$language){Text("Español").tag("es");Text("English").tag("en");Text("Català").tag("ca")}
    Text("La preferencia queda guardada; la traducción integral se incorporará progresivamente.").font(.caption).foregroundStyle(.secondary)
   }
   Section("Avisos") {
    Toggle("Notificaciones de Pawly",isOn:$notificationsEnabled).onChange(of:notificationsEnabled){enabled in Task{
     if enabled {message=await reminders.requestPermission();if message?.contains("activadas")==true{UIApplication.shared.registerForRemoteNotifications()}}
     else{reminders.cancelAllNotifications()}
    }}
    NavigationLink{PawlyRemindersView()}label:{Label("Gestionar recordatorios",systemImage:"bell.badge")}
    Button("Activar notificaciones push"){Task{
     message=await reminders.requestPermission()
     if message?.contains("activadas")==true{UIApplication.shared.registerForRemoteNotifications()}
    }}
    if let message{Text(message).font(.caption).foregroundStyle(.secondary)}
   }
   Section("Privacidad"){Text("Los ajustes de apariencia se guardan en este dispositivo. Los recordatorios son locales; las notificaciones entre usuarios requieren APNs y las funciones de Firebase.").font(.caption).foregroundStyle(.secondary)}
  }.navigationTitle("Ajustes")
 }
}

struct PawlyProfileEditorView:View {
 @EnvironmentObject private var session:PawlySession
 @State private var displayName=""
 @State private var bio=""
 @State private var isPublic=true
 @State private var photoURL=""
 @State private var accountType="personal"
 @State private var professionalCategory="Veterinario"
 @State private var professionalLocation=""
 @State private var professionalDescription=""
 @State private var professionalContact=""
 @State private var professionalContactPublic=false
 @State private var imageItem:PhotosPickerItem?
 @State private var followers=0
 @State private var following=0
 @State private var busy=false
 @State private var message:String?
 @State private var hasError=false
 private let professionalCategories=["Veterinario","Urgencias veterinarias","Adiestramiento","Peluquería canina","Guardería para mascotas","Fisioterapia","Nutrición animal","Tienda de mascotas","Protección animal","Otro"]

 var body:some View {
  Form {
   Section("Tu identidad") {
    HStack(spacing:14) {
     if let url=URL(string:photoURL),!photoURL.isEmpty{AsyncImage(url:url) { image in image.resizable().scaledToFill() } placeholder: { PawlyAvatarPlaceholder() }.frame(width:76,height:76).clipShape(Circle())}
     else{PawlyAvatarPlaceholder().frame(width:76,height:76)}
     VStack(alignment:.leading,spacing:6){Text(displayName.isEmpty ? "Tu nombre":displayName).font(.headline);Text(session.user?.email ?? "").font(.caption).foregroundStyle(.secondary)
      PhotosPicker(selection:$imageItem,matching:.images){Label("Cambiar fotografía",systemImage:"camera")}.font(.caption)}
    }
    TextField("Nombre visible",text:$displayName)
    TextField("Biografía",text:$bio,axis:.vertical).lineLimit(3...6)
    Toggle("Perfil público",isOn:$isPublic)
   }
   Section("Tipo de cuenta") {
    Picker("Cuenta",selection:$accountType) {
     Text("Personal").tag("personal")
     Text("Profesional").tag("professional")
    }.pickerStyle(.segmented)
    if accountType == "professional" {
     Picker("Actividad profesional",selection:$professionalCategory) {
      ForEach(professionalCategories,id:\.self) { Text($0) }
     }
     TextField("Ciudad o zona",text:$professionalLocation)
     TextField("Descripción de tus servicios",text:$professionalDescription,axis:.vertical).lineLimit(3...6)
     TextField("Teléfono, correo o web de contacto",text:$professionalContact)
      .textInputAutocapitalization(.never).autocorrectionDisabled()
     Toggle("Mostrar contacto en mi perfil público",isOn:$professionalContactPublic)
     Text("El contacto se guarda en privado. Solo se muestra cuando el perfil es público y activas esta opción.")
      .font(.caption).foregroundStyle(.secondary)
    }
   }
   Section("Comunidad") {
    LabeledContent("Seguidores",value:"\(followers)")
    LabeledContent("Siguiendo",value:"\(following)")
    Text("El nombre de usuario es tu identificador y no se cambia desde esta pantalla.")
     .font(.caption).foregroundStyle(.secondary)
   }
   if let message { Section { Text(message).foregroundStyle(hasError ? .red:PawlyColors.primary) } }
   Section {
    Button { Task { await save() } } label: {
     HStack { Spacer(); if busy { ProgressView() }; Text(busy ? "Guardando…":"Guardar perfil"); Spacer() }
    }
    .disabled(busy || displayName.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty ||
      (accountType == "professional" && professionalCategory.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty))
   }
  }
  .navigationTitle("Mi perfil")
  .task { await load() }
  .onChange(of:imageItem) { item in
   guard let item else { return }
   Task {
    do {
     guard let data=try await item.loadTransferable(type:Data.self),let uid=session.user?.uid else{return}
     photoURL=try await PawlyCloudinaryUploader().uploadImage(data,folder:"profile-photos",ownerUID:uid)
     message="Fotografía subida. Guarda el perfil para aplicar el cambio.";hasError=false
    } catch { message=error.localizedDescription;hasError=true }
   }
  }
 }

 @MainActor private func load() async {
  guard let uid=session.user?.uid else{return}
  do {
   let userRef=Firestore.firestore().collection("users").document(uid)
   let doc=try await userRef.getDocument()
   let d=doc.data() ?? [:]
   displayName=d["displayName"] as? String ?? session.user?.displayName ?? ""
   bio=d["bio"] as? String ?? ""
   isPublic=d["isPublic"] as? Bool ?? true
   photoURL=d["photoUrl"] as? String ?? ""
   followers=d["followersCount"] as? Int ?? 0
   following=d["followingCount"] as? Int ?? 0
   accountType=d["accountType"] as? String ?? "personal"
   professionalCategory=d["professionalCategory"] as? String ?? "Veterinario"
   professionalLocation=d["professionalLocation"] as? String ?? ""
   professionalDescription=d["professionalDescription"] as? String ?? ""
   professionalContactPublic=d["professionalContactPublic"] as? Bool ?? false
   let privateDoc=try await userRef.collection("privateData").document("professionalContact").getDocument()
   professionalContact=privateDoc.get("value") as? String ?? ""
  } catch { message=error.localizedDescription;hasError=true }
 }

 @MainActor private func save() async {
  guard let user=session.user else{return}
  busy=true;defer{busy=false}
  do {
   let name=displayName.trimmingCharacters(in:.whitespacesAndNewlines)
   let professional=accountType == "professional"
   let category=professional ? String(professionalCategory.trimmingCharacters(in:.whitespacesAndNewlines).prefix(60)) : ""
   let location=professional ? String(professionalLocation.trimmingCharacters(in:.whitespacesAndNewlines).prefix(100)) : ""
   let description=professional ? String(professionalDescription.trimmingCharacters(in:.whitespacesAndNewlines).prefix(500)) : ""
   let privateContact=professional ? String(professionalContact.trimmingCharacters(in:.whitespacesAndNewlines).prefix(120)) : ""
   let showContact=professional && professionalContactPublic && isPublic && !privateContact.isEmpty
   let userRef=Firestore.firestore().collection("users").document(user.uid)
   try await userRef.updateData([
    "displayName":name,
    "bio":String(bio.prefix(500)),
    "isPublic":isPublic,
    "photoUrl":photoURL,
    "accountType":accountType,
    "professionalCategory":category,
    "professionalLocation":location,
    "professionalDescription":description,
    "professionalContactPublic":showContact,
    "professionalPublicContact":""
   ])
   let contactRef=userRef.collection("privateData").document("professionalContact")
   if privateContact.isEmpty {
    try? await contactRef.delete()
   } else {
    try await contactRef.setData(["value":privateContact])
   }
   let change=user.createProfileChangeRequest()
   change.displayName=name
   try await change.commitChanges()
   message=professional ? "Perfil profesional actualizado." : "Perfil actualizado."
   hasError=false
  } catch { message=error.localizedDescription;hasError=true }
 }
}

struct PawlyAvatarPlaceholder:View{
 var body:some View{Image(systemName:"person.fill").font(.system(size:30)).foregroundStyle(PawlyColors.primary).frame(maxWidth:.infinity,maxHeight:.infinity).background(PawlyColors.mint,in:Circle())}
}
struct PawlyAboutView:View{
 var body:some View{List{
  Section{HStack{Spacer();PawlyLogo();Spacer()}.padding(.vertical,18)}
  Section("Pawly"){LabeledContent("Versión",value:Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0");Text("Más vida juntos. Un espacio para cuidar, recordar y compartir la vida con tus animales.")}
  Section("Uso responsable"){Text("Pawly organiza información y recordatorios, pero no sustituye el diagnóstico ni las pautas de un profesional veterinario.");Text("Los avisos comunitarios de animales perdidos o encontrados no garantizan respuesta inmediata.")}
 }.navigationTitle("Acerca de Pawly")}
}

struct PawlyExploreView:View{
 @State private var query=""
 @State private var area=""
 @State private var category="Veterinarios"
 @State private var results:[PawlyPlaceResult]=[]
 @StateObject private var savedStore = PawlySavedPlacesStore()
 @State private var region=MKCoordinateRegion(center:CLLocationCoordinate2D(latitude:40.4168,longitude:-3.7038),span:MKCoordinateSpan(latitudeDelta:0.12,longitudeDelta:0.12))
 @State private var isSearching=false
 @State private var error:String?
 @StateObject private var locationProvider=PawlyLocationProvider()
 private let categories=["Veterinarios","Urgencias veterinarias","Parques caninos","Protectoras","Playas para perros","Restaurantes pet-friendly","Alojamientos pet-friendly","Rutas y senderos","Tiendas de animales"]
 var body:some View{
  NavigationStack{
   VStack(spacing:0){
    VStack(alignment:.leading,spacing:9){
     Text("Encuentra lugares para compartir más aventuras.").font(.subheadline).foregroundStyle(.secondary)
     Picker("Categoría",selection:$category){ForEach(categories,id:\.self){Text($0)}}.pickerStyle(.menu)
     TextField("Ciudad o zona (ej. Málaga)",text:$area).textFieldStyle(.roundedBorder)
     Button{locationProvider.request()}label:{Label("Usar mi ubicación actual",systemImage:"location.fill").font(.caption.weight(.semibold))}
     TextField("Búsqueda concreta (opcional)",text:$query).textFieldStyle(.roundedBorder)
     Button{Task{await search()}}label:{HStack{Spacer();if isSearching{ProgressView().tint(.white)};Label("Buscar lugares",systemImage:"magnifyingglass");Spacer()}}.buttonStyle(.borderedProminent).tint(PawlyColors.primary).disabled(isSearching)
    }.padding()
    if !results.isEmpty{Map(coordinateRegion:$region,annotationItems:results){p in MapMarker(coordinate:p.coordinate,tint:.green)}.frame(height:205)}
    if let error{Text(error).font(.caption).foregroundStyle(.red).padding(.horizontal)}
    if let locationMessage=locationProvider.message{Text(locationMessage).font(.caption).foregroundStyle(.secondary).padding(.horizontal)}
    List(results){p in VStack(alignment:.leading,spacing:6){
     HStack{VStack(alignment:.leading,spacing:3){Text(p.name).font(.headline);Text(p.subtitle).font(.caption).foregroundStyle(.secondary)}
      Spacer();Button{savedStore.toggle(PawlySavedPlace(id:p.id,name:p.name,subtitle:p.subtitle,latitude:p.coordinate.latitude,longitude:p.coordinate.longitude,phone:p.phone))}label:{Image(systemName:savedStore.places.contains(where:{$0.id==p.id}) ? "bookmark.fill":"bookmark").foregroundStyle(PawlyColors.primary)}.buttonStyle(.plain)}
     HStack{if let phone=p.phone,!phone.isEmpty,let tel=URL(string:"tel:\(phone.filter{$0.isNumber || $0=="+"})"){Link("Llamar",destination:tel)}
      Spacer();Button("Cómo llegar"){p.mapItem.openInMaps(launchOptions:[MKLaunchOptionsDirectionsModeKey:MKLaunchOptionsDirectionsModeDriving])}
     }.font(.caption.weight(.semibold))
    }.padding(.vertical,4)}
    if results.isEmpty && !isSearching{VStack(spacing:8){Image(systemName:"map").font(.largeTitle).foregroundStyle(PawlyColors.primary);Text("Explora cerca de ti").font(.headline);Text("Elige una categoría y una zona para encontrar servicios y lugares pet-friendly.").font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)}.padding(25).frame(maxWidth:.infinity)}
   }.navigationTitle("Explorar").task { await savedStore.load() }
  }
 }
 @MainActor private func search()async{
  isSearching=true;error=nil;defer{isSearching=false}
  let term=query.trimmingCharacters(in:.whitespacesAndNewlines),place=area.trimmingCharacters(in:.whitespacesAndNewlines)
  let request=MKLocalSearch.Request();request.naturalLanguageQuery=[term.isEmpty ? category:term,category,place].filter{!$0.isEmpty}.joined(separator:" ");request.region=region
  do{let response=try await MKLocalSearch(request:request).start()
   results=response.mapItems.prefix(35).map{item in let address=[item.placemark.thoroughfare,item.placemark.locality].compactMap{$0}.joined(separator:", ")
    return PawlyPlaceResult(id:"\(item.placemark.coordinate.latitude):\(item.placemark.coordinate.longitude)",name:item.name ?? "Lugar",subtitle:address,coordinate:item.placemark.coordinate,phone:item.phoneNumber,mapItem:item)}
   if let first=results.first{region=MKCoordinateRegion(center:first.coordinate,span:MKCoordinateSpan(latitudeDelta:0.07,longitudeDelta:0.07))}
   if results.isEmpty{error="No se han encontrado resultados. Prueba con otra zona o término."}
  }catch{self.error=error.localizedDescription}
 }
}
struct PawlySavedPlace:Identifiable,Codable{
 let id:String;let name:String;let subtitle:String;let latitude:Double;let longitude:Double;let phone:String?
 static func load()->[PawlySavedPlace]{guard let data=UserDefaults.standard.data(forKey:"pawly.savedPlaces.v2"),let values=try? JSONDecoder().decode([PawlySavedPlace].self,from:data)else{return []};return values}
}
@MainActor
final class PawlySavedPlacesStore: ObservableObject {
    @Published private(set) var places: [PawlySavedPlace] = []
    @Published var message: String?
    private let db = Firestore.firestore()
    private var document: DocumentReference? {
        guard let uid = Auth.auth().currentUser?.uid else { return nil }
        return db.collection("users").document(uid).collection("exploreCollections").document("ios-favourites")
    }

    func load() async {
        let local = PawlySavedPlace.load()
        guard let document else { places = local; return }
        do {
            let snapshot = try await document.getDocument()
            if snapshot.exists {
                places = Self.decodePlaces(snapshot.get("entries") as? [String] ?? [])
                persistLocal()
            } else {
                places = local
                try await seedCloudIfMissing(local)
                let refreshed = try await document.getDocument()
                places = Self.decodePlaces(refreshed.get("entries") as? [String] ?? [])
                persistLocal()
            }
            message = nil
        } catch {
            places = local
            message = "No se pudieron sincronizar los favoritos: \(error.localizedDescription)"
        }
    }

    func toggle(_ place: PawlySavedPlace) {
        let wasSaved = places.contains(where: { $0.id == place.id })
        if wasSaved {
            places.removeAll { $0.id == place.id }
        } else {
            places.append(place)
        }
        persistLocal()
        Task {
            do {
                try await mutateCloud { current in
                    if current.contains(where: { $0.id == place.id }) {
                        return current.filter { $0.id != place.id }
                    }
                    return current + [place]
                }
                await reloadFromCloud()
            } catch {
                message = "No se pudo guardar el favorito: \(error.localizedDescription)"
                await reloadFromCloud()
            }
        }
    }

    func remove(_ place: PawlySavedPlace) {
        places.removeAll { $0.id == place.id }
        persistLocal()
        Task {
            do {
                try await mutateCloud { current in current.filter { $0.id != place.id } }
                await reloadFromCloud()
            } catch {
                message = "No se pudo eliminar el favorito: \(error.localizedDescription)"
                await reloadFromCloud()
            }
        }
    }

    private func reloadFromCloud() async {
        guard let document else { return }
        do {
            let snapshot = try await document.getDocument()
            places = Self.decodePlaces(snapshot.get("entries") as? [String] ?? [])
            persistLocal()
        } catch {
            message = error.localizedDescription
        }
    }

    private func persistLocal() {
        if let data = try? JSONEncoder().encode(places) {
            UserDefaults.standard.set(data, forKey: "pawly.savedPlaces.v2")
        }
    }

    private static func decodePlaces(_ values: [String]) -> [PawlySavedPlace] {
        values.compactMap { value in
            guard let data = value.data(using: .utf8) else { return nil }
            return try? JSONDecoder().decode(PawlySavedPlace.self, from: data)
        }
    }

    private func encodePlaces(_ values: [PawlySavedPlace]) throws -> [String] {
        try values.map { place in
            let data = try JSONEncoder().encode(place)
            guard let value = String(data: data, encoding: .utf8) else {
                throw PawlyExploreError.encodingFailed
            }
            return value
        }
    }

    private func seedCloudIfMissing(_ local: [PawlySavedPlace]) async throws {
        guard let document else { return }
        let encoded = try encodePlaces(Array(local.prefix(100)))
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            db.runTransaction({ transaction, errorPointer -> Any? in
                do {
                    let snapshot = try transaction.getDocument(document)
                    if !snapshot.exists {
                        transaction.setData([
                            "name": "Lugares favoritos",
                            "description": "Sitios guardados desde Explorar en iOS",
                            "entries": encoded,
                            "updatedAt": FieldValue.serverTimestamp()
                        ], forDocument: document)
                    }
                    return nil
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
            }, completion: { _, error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: ()) }
            })
        }
    }

    private func mutateCloud(_ mutation: @escaping ([PawlySavedPlace]) throws -> [PawlySavedPlace]) async throws {
        guard let document else { persistLocal(); return }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            db.runTransaction({ transaction, errorPointer -> Any? in
                do {
                    let snapshot = try transaction.getDocument(document)
                    let current = Self.decodePlaces(snapshot.get("entries") as? [String] ?? [])
                    let updated = try mutation(current)
                    guard updated.count <= 100 else { throw PawlyExploreError.tooManySavedPlaces }
                    let encoded = try self.encodePlaces(updated)
                    transaction.setData([
                        "name": "Lugares favoritos",
                        "description": "Sitios guardados desde Explorar en iOS",
                        "entries": encoded,
                        "updatedAt": FieldValue.serverTimestamp()
                    ], forDocument: document)
                    return nil
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
            }, completion: { _, error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: ()) }
            })
        }
    }
}

enum PawlyExploreError: LocalizedError {
    case tooManySavedPlaces
    case encodingFailed
    var errorDescription: String? {
        switch self {
        case .tooManySavedPlaces: return "Puedes guardar hasta 100 lugares."
        case .encodingFailed: return "No se pudo preparar la lista de lugares."
        }
    }
}
struct PawlySavedPlacesView:View{
 @StateObject private var store = PawlySavedPlacesStore()
 var body:some View{
  List{
   if store.places.isEmpty{Text("Guarda lugares desde Explorar para tenerlos a mano.").foregroundStyle(.secondary)}
   ForEach(store.places){p in VStack(alignment:.leading,spacing:6){
    Text(p.name).font(.headline);Text(p.subtitle).font(.caption).foregroundStyle(.secondary)
    HStack{Button("Cómo llegar"){let item=MKMapItem(placemark:MKPlacemark(coordinate:CLLocationCoordinate2D(latitude:p.latitude,longitude:p.longitude)));item.name=p.name;item.openInMaps(launchOptions:[MKLaunchOptionsDirectionsModeKey:MKLaunchOptionsDirectionsModeDriving])};Spacer();if let phone=p.phone,!phone.isEmpty,let url=URL(string:"tel:\(phone.filter{$0.isNumber || $0=="+"})"){Link("Llamar",destination:url)}}
   }.padding(.vertical,4).swipeActions{Button(role:.destructive){store.remove(p)}label:{Label("Eliminar",systemImage:"trash")}}}
  }.navigationTitle("Lugares favoritos").task { await store.load() }
 }
}
@MainActor final class PawlyLocationProvider:NSObject,ObservableObject,CLLocationManagerDelegate{
 private let manager=CLLocationManager()
 @Published var location:CLLocation?
 @Published var message:String?
 override init(){super.init();manager.delegate=self;manager.desiredAccuracy=kCLLocationAccuracyHundredMeters}
 func request(){
  switch manager.authorizationStatus{
  case .notDetermined:manager.requestWhenInUseAuthorization()
  case .authorizedAlways,.authorizedWhenInUse:manager.requestLocation()
  case .denied,.restricted:message="Permite el acceso a la ubicación en Ajustes de iOS."
  @unknown default:message="No se ha podido consultar la ubicación."
  }
 }
 func locationManagerDidChangeAuthorization(_ manager:CLLocationManager){
  if manager.authorizationStatus == .authorizedAlways || manager.authorizationStatus == .authorizedWhenInUse{manager.requestLocation()}
 }
 func locationManager(_ manager:CLLocationManager,didUpdateLocations locations:[CLLocation]){location=locations.last;message=nil}
 func locationManager(_ manager:CLLocationManager,didFailWithError error:Error){message=error.localizedDescription}
}
struct PawlyPlaceResult:Identifiable{let id:String;let name:String;let subtitle:String;let coordinate:CLLocationCoordinate2D;let phone:String?;let mapItem:MKMapItem}

struct PawlyLearningHubView:View{
 @State private var articles:[PawlyNewsArticle]=[]
 @State private var loading=false
 @State private var error:String?
 @State private var topic="Actualidad"
 private let topics=["Actualidad","Adopción","Comportamiento","Salud","Perros","Gatos"]
 private let guides:[(String,String,String,String)]=[
  ("Adopción responsable","Antes y después de la llegada","heart.fill","Prepara un espacio tranquilo, acuerda responsabilidades, respeta los tiempos de adaptación y concierta una revisión veterinaria. Evita exigir contacto o paseos largos durante los primeros días."),
  ("Comunicación animal","Señales y lenguaje corporal","eye.fill","Observa postura, mirada, orejas, cola y distancia. Bostezos, lamidos de hocico o apartar la cabeza pueden indicar incomodidad. Permite que el animal se aleje y no fuerces interacciones."),
  ("Paseos seguros","Explorar con calma","figure.walk","Usa equipo bien ajustado, identificación y correa segura en zonas no valladas. Aumenta progresivamente duración y dificultad. En calor, prioriza horarios frescos, sombra y agua."),
  ("Salud preventiva","Rutinas que ayudan","cross.case.fill","Registra vacunas, desparasitación, medicación, peso y revisiones. Las pautas deben confirmarse con tu veterinario según especie, edad, entorno y antecedentes."),
  ("Convivencia con niños","Interacciones respetuosas","figure.2.and.child.holdinghands","Supervisa siempre. Enseña a no molestar mientras come, duerme o se refugia. Crea zonas de descanso seguras y deja que el animal inicie el contacto.")
 ]
 var body:some View{
  NavigationStack{ScrollView{VStack(alignment:.leading,spacing:17){
   VStack(alignment:.leading,spacing:6){Text("Aprender también es cuidar").font(.title2.bold());Text("Guías prácticas y actualidad sobre bienestar animal.").font(.subheadline).foregroundStyle(.secondary)}
   ScrollView(.horizontal,showsIndicators:false){HStack{ForEach(topics,id:\.self){t in Button(t){topic=t;Task{await loadNews()}}.buttonStyle(.bordered).tint(topic==t ? PawlyColors.primary:.secondary)}}}
   Text("Guías de Pawly").font(.title3.bold())
   ForEach(guides.indices,id:\.self){index in
    let g=guides[index]
    NavigationLink{PawlyGuideDetailView(title:g.0,subtitle:g.1,icon:g.2,content:g.3)}label:{
    HStack(spacing:12){Image(systemName:g.2).font(.title2).foregroundStyle(PawlyColors.primary).frame(width:45,height:45).background(PawlyColors.mint,in:RoundedRectangle(cornerRadius:14))
     VStack(alignment:.leading,spacing:3){Text(g.0).font(.headline).foregroundStyle(.primary);Text(g.1).font(.caption).foregroundStyle(.secondary)};Spacer();Image(systemName:"chevron.right").font(.caption).foregroundStyle(.secondary)
    }.padding(12).background(PawlyColors.canvas,in:RoundedRectangle(cornerRadius:16))
   }.buttonStyle(.plain)}
   HStack{Text("Noticias").font(.title3.bold());Spacer();Button("Actualizar"){Task{await loadNews()}}.font(.caption.weight(.semibold))}
   if loading{ProgressView().frame(maxWidth:.infinity)}
   if let error{Text(error).font(.caption).foregroundStyle(.secondary)}
   ForEach(articles){a in Link(destination:a.url){VStack(alignment:.leading,spacing:5){Text(a.title).font(.subheadline.weight(.semibold)).foregroundStyle(.primary);Text(a.source).font(.caption).foregroundStyle(.secondary)}.padding(13).frame(maxWidth:.infinity,alignment:.leading).background(.white,in:RoundedRectangle(cornerRadius:14))}}
  }.padding(18)}.background(PawlyColors.canvas).navigationTitle("Aprender").task{await loadNews()}}
 }
 @MainActor private func loadNews()async{
  loading=true;error=nil;defer{loading=false}
  let term=topic=="Actualidad" ? "bienestar animal mascotas":"mascotas \(topic.lowercased())"
  guard let q=term.addingPercentEncoding(withAllowedCharacters:.urlQueryAllowed),let url=URL(string:"https://news.google.com/rss/search?q=\(q)&hl=es&gl=ES&ceid=ES:es")else{return}
  do{let(data,_)=try await URLSession.shared.data(from:url);articles=PawlyRSSParser.parse(data);if articles.isEmpty{error="No hay noticias disponibles ahora."}}
  catch{self.error="No se han podido cargar las noticias. Comprueba la conexión."}
 }
}
struct PawlyGuideDetailView:View{
 let title:String;let subtitle:String;let icon:String;let content:String
 var body:some View{ScrollView{VStack(alignment:.leading,spacing:16){Image(systemName:icon).font(.system(size:38)).foregroundStyle(PawlyColors.primary).frame(width:76,height:76).background(PawlyColors.mint,in:RoundedRectangle(cornerRadius:22));Text(subtitle).font(.title3.bold());Text(content).font(.body).lineSpacing(5);Text("Información general. Adapta las recomendaciones a tu animal y consulta a profesionales cuando sea necesario.").font(.caption).foregroundStyle(.secondary).padding(14).background(PawlyColors.canvas,in:RoundedRectangle(cornerRadius:14))}.padding(22)}.navigationTitle(title).navigationBarTitleDisplayMode(.inline)}
}

private struct PawlyMarketCategory: Identifiable {
 let id: String; let detail: String; let query: String; let icon: String
}

struct PawlyMarketView: View {
 @State private var search = ""
 @State private var category = "Todo"
 @State private var message: String?
 private let categories = [
  PawlyMarketCategory(id:"Pienso y alimentación",detail:"Nutrición diaria y dietas especiales",query:"pienso para perros",icon:"takeoutbag.and.cup.and.straw.fill"),
  PawlyMarketCategory(id:"Comida húmeda",detail:"Texturas y formatos para cada etapa",query:"comida húmeda perros",icon:"fork.knife"),
  PawlyMarketCategory(id:"Snacks y premios",detail:"Premios para entrenar y disfrutar",query:"snacks naturales perros",icon:"heart.fill"),
  PawlyMarketCategory(id:"Juguetes y enriquecimiento",detail:"Olfato, masticación y juego",query:"juguetes enriquecimiento perros",icon:"tennisball.fill"),
  PawlyMarketCategory(id:"Paseo y viaje",detail:"Arneses, correas y accesorios",query:"arneses correas perros",icon:"figure.walk"),
  PawlyMarketCategory(id:"Higiene y cuidado",detail:"Champús, cepillos y limpieza",query:"higiene perros",icon:"drop.fill"),
  PawlyMarketCategory(id:"Camas y descanso",detail:"Camas, mantas y transportines",query:"camas perros",icon:"bed.double.fill")
 ]
 private var visible: [PawlyMarketCategory] { categories.filter { (category == "Todo" || $0.id == category) && (search.isEmpty || $0.id.localizedCaseInsensitiveContains(search) || $0.detail.localizedCaseInsensitiveContains(search)) } }
 var body: some View {
  NavigationStack {
   ScrollView {
    VStack(alignment:.leading,spacing:16) {
     VStack(alignment:.leading,spacing:5){Text("Pawly Market").font(.title2.bold());Text("Encuentra productos en tiendas externas. Pawly no procesa pagos ni pedidos.").font(.subheadline).foregroundStyle(.secondary)}
     HStack { TextField("¿Qué estás buscando?",text:$search).textFieldStyle(.roundedBorder);Button { openSearch(search) } label:{Image(systemName:"magnifyingglass").padding(11)}.buttonStyle(.borderedProminent).tint(PawlyColors.primary).disabled(search.trimmingCharacters(in:.whitespaces).isEmpty) }
     ScrollView(.horizontal,showsIndicators:false){HStack{ForEach(["Todo"]+categories.map(\.id),id:\.self){item in Button(item){category=item}.buttonStyle(.bordered).tint(category == item ? PawlyColors.primary:.secondary)}}}
     ForEach(visible) { item in
      Button { openSearch(search.isEmpty ? item.query:search) } label: {
       HStack(spacing:12){Image(systemName:item.icon).font(.title3).foregroundStyle(PawlyColors.primary).frame(width:43,height:43).background(PawlyColors.mint,in:RoundedRectangle(cornerRadius:13));VStack(alignment:.leading,spacing:3){Text(item.id).font(.headline).foregroundStyle(.primary);Text(item.detail).font(.caption).foregroundStyle(.secondary)};Spacer();Image(systemName:"arrow.up.right").foregroundStyle(PawlyColors.primary)}.padding(13).background(.white,in:RoundedRectangle(cornerRadius:16))
      }.buttonStyle(.plain)
     }
     Text("Compara ingredientes, necesidades veterinarias y precio antes de comprar.").font(.caption).foregroundStyle(.secondary)
     if let message { Text(message).font(.caption).foregroundStyle(.secondary) }
    }.padding(18)
   }.background(PawlyColors.canvas).navigationTitle("Pawly Market").navigationBarTitleDisplayMode(.inline)
  }
 }
 private func openSearch(_ term:String) {
  var parts=URLComponents(string:"https://www.google.com/search")!;parts.queryItems=[URLQueryItem(name:"tbm",value:"shop"),URLQueryItem(name:"q",value:term)]
  guard let url=parts.url else { message="No se pudo preparar la búsqueda.";return }
  UIApplication.shared.open(url)
 }
}

struct PawlyProfessionalProfile: Identifiable {
 let id: String
 let displayName: String
 let category: String
 let location: String
 let description: String
 let contact: String
 var contactURL: URL? {
  let value = contact.trimmingCharacters(in:.whitespacesAndNewlines)
  guard !value.isEmpty else { return nil }
  if value.contains("@"), let url = URL(string:"mailto:\(value)") { return url }
  if value.range(of:"^[+0-9() .-]+$",options:.regularExpression) != nil {
   let digits = value.filter { $0.isNumber || $0 == "+" }
   return URL(string:"tel:\(digits)")
  }
  if let url = URL(string:value), ["https","http"].contains(url.scheme?.lowercased() ?? "") { return url }
  return nil
 }
}

struct PawlyProfessionalDirectoryView: View {
 @State private var category="Veterinario"
 @State private var query=""
 @State private var area=""
 @State private var results:[MKMapItem]=[]
 @State private var pawlyProfessionals:[PawlyProfessionalProfile]=[]
 @State private var busy=false
 @State private var loadingPawly=false
 @State private var message:String?
 private let categories=["Veterinario","Urgencias veterinarias","Adiestramiento","Peluquería canina","Guardería para mascotas","Fisioterapia","Nutrición animal","Tiendas de mascotas"]
 var body:some View {
  NavigationStack {
   VStack(spacing:0) {
    Form {
     Section("Encuentra profesionales cerca") {
      Picker("Servicio",selection:$category){ForEach(categories,id:\.self){Text($0)}}
      TextField("Ciudad o zona",text:$area)
      TextField("Nombre o servicio concreto",text:$query)
      Button { Task { await search() } } label:{HStack{Spacer();if busy{ProgressView()};Label("Buscar en Mapas",systemImage:"magnifyingglass");Spacer()}}.disabled(busy)
     }
     if let message { Section { Text(message).font(.caption).foregroundStyle(.secondary) } }
     Section("Profesionales de Pawly") {
      if loadingPawly { ProgressView("Buscando profesionales de Pawly…") }
      else if pawlyProfessionals.isEmpty { Text("Todavía no hay perfiles profesionales públicos de esta categoría.").font(.caption).foregroundStyle(.secondary) }
      ForEach(pawlyProfessionals) { professional in
       VStack(alignment:.leading,spacing:5) {
        Text(professional.displayName).font(.headline)
        Text([professional.category,professional.location].filter { !$0.isEmpty }.joined(separator:" · "))
         .font(.caption).foregroundStyle(.secondary)
        if !professional.description.isEmpty { Text(professional.description).font(.subheadline) }
        if !professional.contact.isEmpty {
         Link(destination: professional.contactURL ?? URL(string:"https://pawly.app")!) {
          Label(professional.contact,systemImage:"paperplane")
         }.font(.caption.weight(.semibold))
        }
       }.padding(.vertical,4)
      }
     }
     Section("Resultados de Apple Maps") {
      if results.isEmpty && !busy { Text("Busca un servicio para ver opciones de Apple Maps.").font(.caption).foregroundStyle(.secondary) }
      ForEach(Array(results.enumerated()),id:\.offset) { _,item in
       Button { item.openInMaps() } label:{VStack(alignment:.leading,spacing:4){Text(item.name ?? "Profesional").font(.headline).foregroundStyle(.primary);Text(item.placemark.title ?? item.placemark.locality ?? "Dirección no disponible").font(.caption).foregroundStyle(.secondary);Label("Abrir en Mapas",systemImage:"arrow.up.right").font(.caption.weight(.semibold)).foregroundStyle(PawlyColors.primary)}}.padding(.vertical,4)
      }
     }
    }
   }.navigationTitle("Profesionales").task { await loadPawlyProfessionals() }
  }
 }
 @MainActor private func loadPawlyProfessionals() async {
  guard Auth.auth().currentUser != nil else { return }
  loadingPawly = true
  defer { loadingPawly = false }
  do {
   let snapshot = try await Firestore.firestore().collection("users")
    .whereField("isPublic",isEqualTo:true).limit(to:100).getDocuments()
   let wanted = category.lowercased()
   pawlyProfessionals = snapshot.documents.compactMap { document in
    let data = document.data()
    guard data["accountType"] as? String == "professional" else { return nil }
    let category = data["professionalCategory"] as? String ?? ""
    let location = data["professionalLocation"] as? String ?? ""
    let description = data["professionalDescription"] as? String ?? ""
    let displayName = data["displayName"] as? String ?? data["username"] as? String ?? "Profesional"
    let publicContact = data["professionalPublicContact"] as? String ?? ""
    return PawlyProfessionalProfile(
     id: document.documentID, displayName: displayName, category: category,
     location: location, description: description, contact: publicContact
    )
   }
   .filter { wanted.isEmpty || $0.category.localizedCaseInsensitiveContains(wanted) || wanted.contains($0.category.lowercased()) }
   .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
   let professionalIDs = pawlyProfessionals.indices.filter { pawlyProfessionals[$0].contact.isEmpty }
   for index in professionalIDs {
    let profile = snapshot.documents.first(where: { $0.documentID == pawlyProfessionals[index].id })
    guard profile?.get("professionalContactPublic") as? Bool == true else { continue }
    let privateDoc = try await Firestore.firestore().collection("users").document(pawlyProfessionals[index].id)
     .collection("privateData").document("professionalContact").getDocument()
    let contact = privateDoc.get("value") as? String ?? ""
    if !contact.isEmpty {
     pawlyProfessionals[index] = PawlyProfessionalProfile(
      id: pawlyProfessionals[index].id,
      displayName: pawlyProfessionals[index].displayName,
      category: pawlyProfessionals[index].category,
      location: pawlyProfessionals[index].location,
      description: pawlyProfessionals[index].description,
      contact: contact
     )
    }
   }
  } catch {
   message = "No se pudieron cargar los perfiles de Pawly: \(error.localizedDescription)"
  }
 }

 @MainActor private func search() async {
  busy=true;message=nil;defer{busy=false}
  let term=query.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty ? category:query
  let request=MKLocalSearch.Request();request.naturalLanguageQuery=[term,area].filter{!$0.isEmpty}.joined(separator:" ")
  do { results=try await MKLocalSearch(request:request).start().mapItems; if results.isEmpty{message="No se encontraron resultados. Prueba otra zona o servicio."} }
  catch { message="No se pudo buscar ahora. Comprueba la conexión y vuelve a intentarlo." }
 }
}

struct PawlyLostPetView: View {
 @EnvironmentObject private var session:PawlySession
 @EnvironmentObject private var pets:PawlyPetStore
 @StateObject private var community=PawlyCommunityStore()
 @StateObject private var locationProvider=PawlyLocationProvider()
 @State private var name=""
 @State private var area=""
 @State private var lastSeen=""
 @State private var contact=""
 @State private var details=""
 @State private var photoItem:PhotosPickerItem?
 @State private var photoData:Data?
 @State private var busy=false
 @State private var geocoding=false
 @State private var savingSubscription=false
 @State private var status:String?
 @State private var error:String?
 @State private var receiveNearbyAlerts=false
 @State private var alertRadius=10
 @State private var subscriptionLocation:CLLocationCoordinate2D?
 @State private var lostLocation:CLLocationCoordinate2D?
 @State private var subscriptionSaved=false
 @Environment(\.dismiss) private var dismiss

 var body:some View {
  NavigationStack {
   Form {
    Section("Datos del aviso") {
     if let pet=pets.selectedPet {
      Button(pet.name){name=pet.name}.foregroundStyle(PawlyColors.primary)
     }
     TextField("Nombre de la mascota",text:$name)
     TextField("Zona donde se perdió",text:$area)
     TextField("Cuándo se vio por última vez",text:$lastSeen)
     TextField("Teléfono o contacto (opcional)",text:$contact).keyboardType(.phonePad)
     TextField("Rasgos y otra información útil",text:$details,axis:.vertical).lineLimit(3...7)
     PhotosPicker(selection:$photoItem,matching:.images) {
      Label(photoData == nil ? "Añadir foto":"Cambiar foto",systemImage:"photo")
     }
     if let photoData,let image=UIImage(data:photoData) {
      Image(uiImage:image).resizable().scaledToFill()
       .frame(maxWidth:.infinity).frame(height:190).clipShape(RoundedRectangle(cornerRadius:14))
     }
    }
    Section("Ubicación aproximada del aviso") {
     Text("La zona del aviso se localiza a partir de la dirección o barrio que escribas. Pawly redondea la posición a una celda de aproximadamente 1 km antes de guardarla.")
      .font(.caption).foregroundStyle(.secondary)
     Button {
      Task { await resolveLostLocation() }
     } label: {
      HStack {
       if geocoding { ProgressView() }
       Label(lostLocation == nil ? "Localizar la zona del aviso":"Zona localizada",systemImage:"mappin.and.ellipse")
      }
     }.disabled(geocoding || area.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty)
     if let lostLocation {
      Text(String(format:"Ubicación aproximada: %.2f, %.2f",lostLocation.latitude,lostLocation.longitude))
       .font(.caption).foregroundStyle(.secondary)
     }
    }
    Section("Alertas de mascotas perdidas cerca de ti") {
     Toggle("Quiero recibir avisos de mi zona",isOn:$receiveNearbyAlerts)
      .onChange(of:receiveNearbyAlerts){_ in subscriptionSaved=false}
     Picker("Radio de recepción",selection:$alertRadius) {
      ForEach([5,10,20,30,50,100],id:\.self){Text("\($0) km").tag($0)}
     }
     Text("Solo se enviarán alertas si otra persona también ha activado esta función y el aviso está dentro del radio. Tu posición exacta no se comparte.")
      .font(.caption).foregroundStyle(.secondary)
     Button {
      locationProvider.request()
     } label: {
      Label(subscriptionLocation == nil ? "Usar mi ubicación aproximada":"Actualizar mi ubicación aproximada",
            systemImage:"location.circle")
     }
     if let locationMessage=locationProvider.message {
      Text(locationMessage).font(.caption).foregroundStyle(.secondary)
     }
     if subscriptionLocation != nil {
      Text("Ubicación de recepción disponible (se guarda redondeada a ~1 km).")
       .font(.caption).foregroundStyle(PawlyColors.primary)
     }
     Button {
      Task { await saveSubscription() }
     } label: {
      HStack {
       if savingSubscription { ProgressView() }
       Text(subscriptionSaved ? "Preferencias guardadas":"Guardar preferencias de alertas")
      }
     }.disabled(savingSubscription || subscriptionLocation == nil)
    }
    Section {
     Button {
      Task { await publish() }
     } label: {
      HStack { Spacer();if busy{ProgressView()};Text(busy ? "Publicando aviso…":"Publicar aviso en Comunidad");Spacer() }
     }
     .disabled(busy || name.trimmingCharacters(in:.whitespaces).isEmpty ||
               area.trimmingCharacters(in:.whitespaces).isEmpty ||
               details.trimmingCharacters(in:.whitespaces).isEmpty)
    }
    if let status { Section { Text(status).font(.caption).foregroundStyle(PawlyColors.primary) } }
    if let error { Section { Text(error).font(.caption).foregroundStyle(.red) } }
    Section {
     Text("El aviso queda publicado en Comunidad. Las notificaciones por proximidad requieren que el servicio Cloudflare esté desplegado y que las personas cercanas hayan dado su consentimiento.")
      .font(.caption).foregroundStyle(.secondary)
    }
   }
   .navigationTitle("Mascota perdida")
   .navigationBarTitleDisplayMode(.inline)
   .toolbar {
    ToolbarItem(placement:.topBarTrailing) {
     Button("Cerrar") { dismiss() }
    }
   }
   .onChange(of:photoItem){item in
    guard let item else{return}
    Task { photoData=try? await item.loadTransferable(type:Data.self) }
   }
   .onChange(of:locationProvider.location?.coordinate.latitude){_ in
    if let location=locationProvider.location { subscriptionLocation=location.coordinate;subscriptionSaved=false }
   }
   .task { await loadSubscription() }
  }
 }

 @MainActor private func loadSubscription() async {
  do {
   if let saved=try await community.loadLostPetSubscription() {
    receiveNearbyAlerts=saved.enabled
    alertRadius=saved.radiusKm
    subscriptionLocation=CLLocationCoordinate2D(latitude:saved.latitude,longitude:saved.longitude)
    subscriptionSaved=true
   }
  } catch let loadError { self.error=loadError.localizedDescription }
 }

 @MainActor private func saveSubscription() async {
  guard let coordinate=subscriptionLocation else {
   error="Permite el acceso a la ubicación para guardar las alertas."
   return
  }
  savingSubscription=true
  defer { savingSubscription=false }
  do {
   try await community.saveLostPetSubscription(
    enabled:receiveNearbyAlerts,radiusKm:alertRadius,
    latitude:coordinate.latitude,longitude:coordinate.longitude
   )
   subscriptionSaved=true
   error=nil
   status="Preferencias de alertas guardadas."
  } catch let saveError { self.error=saveError.localizedDescription }
 }

 @MainActor private func resolveLostLocation() async {
  geocoding=true
  defer { geocoding=false }
  do {
   let results=try await CLGeocoder().geocodeAddressString(area)
   guard let coordinate=results.first?.location?.coordinate else {
    error="No se ha podido localizar la zona. Prueba con una dirección o municipio más concreto."
    return
   }
   lostLocation=coordinate
   error=nil
  } catch let geocodingError { self.error=geocodingError.localizedDescription }
 }

 @MainActor private func publish() async {
  guard let uid=session.user?.uid else {
   error="Inicia sesión para publicar el aviso."
   return
  }
  busy=true
  defer { busy=false }
  status=nil
  error=nil
  do {
   var imageURL=""
   if let photoData {
    imageURL=try await PawlyCloudinaryUploader().uploadImage(photoData,folder:"post-photos",ownerUID:uid)
   }
   let message="MASCOTA PERDIDA · \(name)\nZona: \(area)\nÚltima vez vista: \(lastSeen.isEmpty ? "No indicada":lastSeen)\nContacto: \(contact.isEmpty ? "No indicado":contact)\n\n\(details)"
   let postID=try await community.publish(text:message,petName:name,category:"LOST",imageURL:imageURL)
   guard let lostLocation else {
    status="La publicación se ha creado, pero no se han enviado alertas por proximidad porque falta localizar la zona del aviso."
    return
   }
   do {
    let delivered=try await community.publishLostPetAlert(postID:postID,dogName:name,coordinate:lostLocation)
    status=delivered > 0
      ? "Aviso publicado. Se han entregado \(delivered) notificaciones push a dispositivos cercanos."
      : "Aviso publicado y zona registrada. No había dispositivos cercanos habilitados para recibir alertas."
   } catch {
    status="La publicación y la zona aproximada se han guardado en Pawly, pero no se pudo completar el envío de avisos: \(error.localizedDescription)"
   }
  } catch let publishError { self.error="No se pudo publicar: \(publishError.localizedDescription)" }
 }
}

struct PawlyNewsArticle:Identifiable{let id:String;let title:String;let url:URL;let source:String}
final class PawlyRSSParser:NSObject,XMLParserDelegate{
 private var articles:[PawlyNewsArticle]=[];private var current:[String:String]=[:];private var buffer=""
 static func parse(_ data:Data)->[PawlyNewsArticle]{let parser=XMLParser(data:data),delegate=PawlyRSSParser();parser.delegate=delegate;parser.parse();return delegate.articles}
 func parser(_ parser:XMLParser,didStartElement elementName:String,namespaceURI:String?,qualifiedName qName:String?,attributes attributeDict:[String:String]=[:]){if elementName=="item"{current=[:]};buffer=""}
 func parser(_ parser:XMLParser,foundCharacters string:String){buffer+=string}
 func parser(_ parser:XMLParser,didEndElement elementName:String,namespaceURI:String?,qualifiedName qName:String?){
  if ["title","link","source"].contains(elementName){current[elementName]=(current[elementName] ?? "")+buffer}
  if elementName=="item",let title=current["title"],let link=current["link"],let url=URL(string:link){articles.append(PawlyNewsArticle(id:link,title:title,url:url,source:current["source"] ?? "Google News"))}
  buffer=""
 }
}
