import SwiftUI
import MapKit
import CoreLocation
import FirebaseAuth
import FirebaseFirestore
import PhotosUI
import UIKit

struct PawlyMoreHubView: View {
 @EnvironmentObject private var session:PawlySession
 var body:some View {
  NavigationStack {
   List {
    Section { HStack(spacing:12) { PawlyAvatarPlaceholder().frame(width:50,height:50); VStack(alignment:.leading){Text(session.user?.displayName ?? "Mi Pawly").font(.headline);Text(session.user?.email ?? "").font(.caption).foregroundStyle(.secondary)} }.padding(.vertical,6) }
    Section("Tu día a día") {
     NavigationLink { DiaryView() } label:{Label("Diario de recuerdos",systemImage:"book.closed.fill")}
     NavigationLink { PawlyLearningHubView() } label:{Label("Aprender y descubrir",systemImage:"graduationcap.fill")}
     NavigationLink { PawlyRemindersView() } label:{Label("Agenda y recordatorios",systemImage:"calendar.badge.clock")}
     NavigationLink { MessagesView() } label:{Label("Mensajes privados",systemImage:"bubble.left.and.bubble.right.fill")}
    }
    Section("Personas y mascotas") {
     NavigationLink { PawlyProfileEditorView() } label:{Label("Mi perfil público",systemImage:"person.crop.circle")}
     NavigationLink { PetSharingSheet() } label:{Label("Compartir mascotas",systemImage:"person.2.badge.plus")}
     NavigationLink { AccountView() } label:{Label("Cuenta y privacidad",systemImage:"person.badge.key.fill")}
    }
    Section("Aplicación") {
     NavigationLink { PawlySettingsView() } label:{Label("Ajustes y apariencia",systemImage:"slider.horizontal.3")}
     NavigationLink { PawlyAboutView() } label:{Label("Acerca de Pawly",systemImage:"pawprint")}
    }
    Section { Button(role:.destructive){try? session.signOut()} label:{Label("Cerrar sesión",systemImage:"rectangle.portrait.and.arrow.right")} }
   }.navigationTitle("Más")
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
     HStack(spacing:18){ForEach(accents,id:\.0){item in
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
 @State private var imageItem:PhotosPickerItem?
 @State private var followers=0
 @State private var following=0
 @State private var busy=false
 @State private var message:String?
 @State private var hasError=false
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
   Section("Comunidad"){LabeledContent("Seguidores",value:"\(followers)");LabeledContent("Siguiendo",value:"\(following)");Text("El nombre de usuario es tu identificador y no se cambia desde esta pantalla.").font(.caption).foregroundStyle(.secondary)}
   if let message{Section{Text(message).foregroundStyle(hasError ? .red:PawlyColors.primary)}}
   Section{Button{Task{await save()}}label:{HStack{Spacer();if busy{ProgressView()};Text(busy ? "Guardando…":"Guardar perfil");Spacer()}}.disabled(busy || displayName.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty)}
  }.navigationTitle("Mi perfil").task{await load()}.onChange(of:imageItem){item in guard let item else{return};Task{do{
   guard let data=try await item.loadTransferable(type:Data.self),let uid=session.user?.uid else{return}
   photoURL=try await PawlyCloudinaryUploader().uploadImage(data,folder:"profile-photos",ownerUID:uid)
   message="Fotografía subida. Guarda el perfil para aplicar el cambio.";hasError=false
  }catch{message=error.localizedDescription;hasError=true}}}
 }
 @MainActor private func load()async{
  guard let uid=session.user?.uid else{return}
  do{let doc=try await Firestore.firestore().collection("users").document(uid).getDocument();let d=doc.data() ?? [:]
   displayName=d["displayName"] as? String ?? session.user?.displayName ?? "";bio=d["bio"] as? String ?? ""
   isPublic=d["isPublic"] as? Bool ?? true;photoURL=d["photoUrl"] as? String ?? ""
   followers=d["followersCount"] as? Int ?? 0;following=d["followingCount"] as? Int ?? 0
  }catch{message=error.localizedDescription;hasError=true}
 }
 @MainActor private func save()async{
  guard let user=session.user else{return};busy=true;defer{busy=false}
  do{let name=displayName.trimmingCharacters(in:.whitespacesAndNewlines)
   try await Firestore.firestore().collection("users").document(user.uid).updateData(["displayName":name,"bio":String(bio.prefix(500)),"isPublic":isPublic,"photoUrl":photoURL])
   let change=user.createProfileChangeRequest();change.displayName=name;try await change.commitChanges()
   message="Perfil actualizado.";hasError=false
  }catch{message=error.localizedDescription;hasError=true}
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
 @State private var saved=PawlySavedPlace.load()
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
      Spacer();Button{toggleSaved(p)}label:{Image(systemName:saved.contains(where:{$0.id==p.id}) ? "bookmark.fill":"bookmark").foregroundStyle(PawlyColors.primary)}.buttonStyle(.plain)}
     HStack{if let phone=p.phone,!phone.isEmpty,let tel=URL(string:"tel:\(phone.filter{$0.isNumber || $0=="+"})"){Link("Llamar",destination:tel)}
      Spacer();Button("Cómo llegar"){p.mapItem.openInMaps(launchOptions:[MKLaunchOptionsDirectionsModeKey:MKLaunchOptionsDirectionsModeDriving])}
     }.font(.caption.weight(.semibold))
    }.padding(.vertical,4)}
    if results.isEmpty && !isSearching{VStack(spacing:8){Image(systemName:"map").font(.largeTitle).foregroundStyle(PawlyColors.primary);Text("Explora cerca de ti").font(.headline);Text("Elige una categoría y una zona para encontrar servicios y lugares pet-friendly.").font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)}.padding(25).frame(maxWidth:.infinity)}
   }.navigationTitle("Explorar")
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
 private func toggleSaved(_ p:PawlyPlaceResult){
  if let i=saved.firstIndex(where:{$0.id==p.id}){saved.remove(at:i)}
  else{saved.append(PawlySavedPlace(id:p.id,name:p.name,subtitle:p.subtitle,latitude:p.coordinate.latitude,longitude:p.coordinate.longitude,phone:p.phone))}
  if let data=try? JSONEncoder().encode(saved){UserDefaults.standard.set(data,forKey:"pawly.savedPlaces.v2")}
 }
}
struct PawlySavedPlace:Identifiable,Codable{
 let id:String;let name:String;let subtitle:String;let latitude:Double;let longitude:Double;let phone:String?
 static func load()->[PawlySavedPlace]{guard let data=UserDefaults.standard.data(forKey:"pawly.savedPlaces.v2"),let values=try? JSONDecoder().decode([PawlySavedPlace].self,from:data)else{return []};return values}
}
struct PawlySavedPlacesView:View{
 @State private var places=PawlySavedPlace.load()
 var body:some View{
  List{
   if places.isEmpty{Text("Guarda lugares desde Explorar para tenerlos a mano.").foregroundStyle(.secondary)}
   ForEach(places){p in VStack(alignment:.leading,spacing:6){
    Text(p.name).font(.headline);Text(p.subtitle).font(.caption).foregroundStyle(.secondary)
    HStack{Button("Cómo llegar"){let item=MKMapItem(placemark:MKPlacemark(coordinate:CLLocationCoordinate2D(latitude:p.latitude,longitude:p.longitude)));item.name=p.name;item.openInMaps(launchOptions:[MKLaunchOptionsDirectionsModeKey:MKLaunchOptionsDirectionsModeDriving])};Spacer();if let phone=p.phone,!phone.isEmpty,let url=URL(string:"tel:\(phone.filter{$0.isNumber || $0=="+"})"){Link("Llamar",destination:url)}}
   }.padding(.vertical,4).swipeActions{Button(role:.destructive){places.removeAll{$0.id==p.id};persist()}label:{Label("Eliminar",systemImage:"trash")}}}
  }.navigationTitle("Lugares favoritos")
 }
 private func persist(){if let data=try? JSONEncoder().encode(places){UserDefaults.standard.set(data,forKey:"pawly.savedPlaces.v2")}}
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
   ForEach(guides,id:\.0){g in NavigationLink{PawlyGuideDetailView(title:g.0,subtitle:g.1,icon:g.2,content:g.3)}label:{
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
