import SwiftUI
import MapKit
import FirebaseAuth
import FirebaseFirestore
import CryptoKit

struct PawlyExploreIdea:Identifiable {
    let id:String
    let category:String
    let name:String
    let subtitle:String
    let detail:String
    let image:String
    let searchCategory:String
    static let all=[
        PawlyExploreIdea(id:"trail",category:"Naturaleza",name:"Rutas y senderos",subtitle:"Ideas para caminar juntos",detail:"Busca caminos, sombra y agua antes de salir.",image:"https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=1000&h=500&fit=crop",searchCategory:"Rutas y senderos"),
        PawlyExploreIdea(id:"beach",category:"Playas",name:"Playas caninas",subtitle:"Consulta normas y temporada",detail:"Las restricciones cambian según municipio y época del año.",image:"https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1000&h=500&fit=crop",searchCategory:"Playas para perros"),
        PawlyExploreIdea(id:"cafe",category:"Comer",name:"Terrazas dog-friendly",subtitle:"Cafés y restaurantes",detail:"Comprueba siempre con el establecimiento antes de ir.",image:"https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=1000&h=500&fit=crop",searchCategory:"Restaurantes pet-friendly"),
        PawlyExploreIdea(id:"stay",category:"Alojarse",name:"Escapadas con perro",subtitle:"Alojamientos pet-friendly",detail:"Revisa tamaño permitido, suplementos y condiciones.",image:"https://images.unsplash.com/photo-1449158743715-0a90ebb6d2d8?w=1000&h=500&fit=crop",searchCategory:"Alojamientos pet-friendly")
    ]
}
struct PawlyExploreCollection:Identifiable {
    let id:String
    let name:String
    let description:String
    let entries:[String]
}
@MainActor final class PawlyExploreCollectionsStore:ObservableObject {
    @Published private(set) var collections:[PawlyExploreCollection]=[]
    @Published var message:String?
    private var root:CollectionReference? {
        guard let uid=Auth.auth().currentUser?.uid else{return nil}
        return Firestore.firestore().collection("users").document(uid).collection("exploreCollections")
    }
    func load() async {
        guard let root else {collections=[];return}
        do {
            collections=try await root.getDocuments().documents.compactMap{doc in
                guard doc.documentID != "ios-favourites",let name=doc.get("name") as? String,!name.isEmpty else{return nil}
                return PawlyExploreCollection(id:doc.documentID,name:name,description:doc.get("description") as? String ?? "",entries:doc.get("entries") as? [String] ?? [])
            }.sorted{$0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending}
            message=nil
        } catch {message=error.localizedDescription}
    }
    func create(name:String,description:String) async -> Bool {
        let clean=name.trimmingCharacters(in:.whitespacesAndNewlines)
        guard let root,!clean.isEmpty,clean.count<=32,description.count<=120,collections.count<30,
              !collections.contains(where:{$0.name.caseInsensitiveCompare(clean) == .orderedSame}) else {
            message="Elige un nombre distinto de hasta 32 caracteres. Máximo 30 colecciones.";return false
        }
        // Java UUID.nameUUIDFromBytes used by Android: MD5, version 3, RFC 4122 variant.
        var bytes=Array(Insecure.MD5.hash(data:Data(clean.lowercased().utf8)))
        bytes[6]=(bytes[6]&0x0f)|0x30;bytes[8]=(bytes[8]&0x3f)|0x80
        let id=UUID(uuid:(bytes[0],bytes[1],bytes[2],bytes[3],bytes[4],bytes[5],bytes[6],bytes[7],bytes[8],bytes[9],bytes[10],bytes[11],bytes[12],bytes[13],bytes[14],bytes[15])).uuidString.lowercased()
        do {
            let ref=root.document(id)
            guard !(try await ref.getDocument()).exists else {message="Ya existe esta colección.";return false}
            try await ref.setData(["name":clean,"description":description,"entries":[String](),"updatedAt":FieldValue.serverTimestamp()])
            await load();return true
        } catch {message=error.localizedDescription;return false}
    }
    func change(_ entry:String,in collection:PawlyExploreCollection,add:Bool) async {
        guard let root else{return}
        if add && collection.entries.count>=100 && !collection.entries.contains(entry) {message="La colección admite hasta 100 elementos.";return}
        do {
            let update:FieldValue=add ? .arrayUnion([entry]):.arrayRemove([entry])
            try await root.document(collection.id).updateData(["entries":update,"updatedAt":FieldValue.serverTimestamp()])
            await load()
        } catch {message=error.localizedDescription}
    }
    func remove(_ collection:PawlyExploreCollection) async {
        guard let root else{return}
        do {try await root.document(collection.id).delete();await load()} catch {message=error.localizedDescription}
    }
}

struct PawlyExploreIdeasView:View {
    @Binding var saved:Set<String>
    @ObservedObject var collections:PawlyExploreCollectionsStore
    var onSearch:(String)->Void
    @State private var category="Todo"
    @State private var query=""
    @State private var showCommunity=false
    private var visible:[PawlyExploreIdea] {
        PawlyExploreIdea.all.filter{idea in
            (category=="Todo" || idea.category==category) && (query.isEmpty || [idea.name,idea.subtitle,idea.detail].joined(separator:" ").localizedCaseInsensitiveContains(query))
        }
    }
    var body:some View {
        ScrollView {
            VStack(alignment:.leading,spacing:15) {
                DisclosureGroup(isExpanded:$showCommunity) {
                    NavigationLink{PawlyCommunityPlacesView()}label:{Label("Ver recomendaciones y aportar",systemImage:"mappin.and.ellipse")}.padding(.top,8)
                } label:{
                    VStack(alignment:.leading,spacing:5) {
                        Text("Recomendaciones de la comunidad").font(.caption.weight(.semibold)).foregroundStyle(PawlyColors.primary)
                        Text("Lugares que otros miembros comparten").font(.subheadline).foregroundStyle(PawlyColors.ink)
                    }
                }.padding(14).background(PawlyColors.surface,in:RoundedRectangle(cornerRadius:18))
                HStack { Image(systemName:"magnifyingglass").foregroundStyle(PawlyColors.muted);TextField("Buscar planes o lugares",text:$query) }
                    .padding(13).background(PawlyColors.surface,in:RoundedRectangle(cornerRadius:15))
                HStack {
                    Label("Categoría",systemImage:"line.3.horizontal.decrease")
                    Spacer()
                    Picker("Categoría",selection:$category){ForEach(["Todo","Naturaleza","Playas","Comer","Alojarse"],id:\.self){Text(LocalizedStringKey($0)).tag($0)}}.pickerStyle(.menu)
                }.padding(8).background(PawlyColors.surface,in:RoundedRectangle(cornerRadius:15))
                Text("Ideas para vuestra próxima salida").font(.headline).foregroundStyle(PawlyColors.ink)
                ForEach(visible){idea in
                    VStack(alignment:.leading,spacing:0) {
                        ZStack(alignment:.bottomLeading) {
                            AsyncImage(url:URL(string:idea.image)){image in image.resizable().scaledToFill()}placeholder:{
                                PawlyColors.mint.overlay{Image(systemName:"leaf").font(.largeTitle).foregroundStyle(PawlyColors.primary)}
                            }.frame(height:175).clipped()
                            LinearGradient(colors:[.clear,.black.opacity(0.82)],startPoint:.top,endPoint:.bottom)
                            VStack(alignment:.leading,spacing:4) {
                                Text(LocalizedStringKey(idea.category)).font(.caption.weight(.semibold))
                                Text(LocalizedStringKey(idea.name)).font(.title3.bold())
                                Text(LocalizedStringKey(idea.subtitle)).font(.caption)
                            }.foregroundStyle(.white).padding(14)
                        }.frame(height:175)
                        .overlay(alignment:.topTrailing){
                            Button {
                                if saved.contains(idea.id){saved.remove(idea.id)}else{saved.insert(idea.id)}
                            }label:{Image(systemName:saved.contains(idea.id) ? "bookmark.fill":"bookmark").padding(12).background(PawlyColors.surface,in:Circle()).foregroundStyle(PawlyColors.primary)}
                                .buttonStyle(.plain).accessibilityLabel(saved.contains(idea.id) ? "Quitar de guardados":"Guardar idea").padding(10)
                        }
                        Text(LocalizedStringKey(idea.detail)).font(.caption).foregroundStyle(PawlyColors.muted).padding(12)
                        HStack {
                            Button {onSearch(idea.searchCategory)}label:{Label("Buscar cerca",systemImage:"location")}
                                .buttonStyle(.borderedProminent)
                            Spacer()
                            Menu {
                                if collections.collections.isEmpty{Text("Primero crea una colección")}
                                ForEach(collections.collections){collection in
                                    Button {
                                        Task{await collections.change(idea.name,in:collection,add:!collection.entries.contains(idea.name))}
                                    }label:{Label(collection.name,systemImage:collection.entries.contains(idea.name) ? "checkmark":"folder")}
                                }
                            }label:{Label("Colección",systemImage:"folder.badge.plus")}
                        }.font(.caption.weight(.semibold)).padding([.horizontal,.bottom],12)
                    }.background(PawlyColors.surface,in:RoundedRectangle(cornerRadius:18)).clipShape(RoundedRectangle(cornerRadius:18))
                }
                if visible.isEmpty{Text("No hay ideas que coincidan.").font(.caption).foregroundStyle(.secondary)}
                if let message=collections.message{Text(message).font(.caption).foregroundStyle(.secondary)}
            }.padding(16)
        }
    }
}
struct PawlyExploreSavedView:View {
    @Binding var savedIdeas:Set<String>
    @ObservedObject var savedPlaces:PawlySavedPlacesStore
    var onSearch:(String)->Void
    var body:some View {
        List {
            Section("Ideas guardadas") {
                if savedIdeas.isEmpty {Text("Todavía no has guardado ideas.").foregroundStyle(.secondary)}
                ForEach(PawlyExploreIdea.all.filter{savedIdeas.contains($0.id)}){idea in
                    HStack {
                        Button {onSearch(idea.searchCategory)} label:{VStack(alignment:.leading){Text(LocalizedStringKey(idea.name));Text(LocalizedStringKey(idea.subtitle)).font(.caption).foregroundStyle(.secondary)}}.buttonStyle(.borderless)
                        Spacer()
                        Button{savedIdeas.remove(idea.id)}label:{Image(systemName:"bookmark.slash")}.buttonStyle(.borderless)
                    }
                }
            }
            Section("Lugares favoritos") {
                if savedPlaces.places.isEmpty{Text("Guarda lugares desde Cerca para tenerlos a mano.").foregroundStyle(.secondary)}
                ForEach(savedPlaces.places){place in
                    VStack(alignment:.leading,spacing:6){
                        Text(place.name).font(.headline);Text(place.subtitle).font(.caption).foregroundStyle(.secondary)
                        Button("Cómo llegar"){
                            let item=MKMapItem(placemark:MKPlacemark(coordinate:CLLocationCoordinate2D(latitude:place.latitude,longitude:place.longitude)))
                            item.name=place.name;item.openInMaps()
                        }.buttonStyle(.borderless)
                    }.swipeActions{Button(role:.destructive){savedPlaces.remove(place)}label:{Label("Eliminar",systemImage:"trash")}}
                }
            }
            if let message=savedPlaces.message{Text(message).font(.caption).foregroundStyle(.secondary)}
        }.refreshable{await savedPlaces.load()}
    }
}
struct PawlyExploreCollectionsView:View {
    @ObservedObject var store:PawlyExploreCollectionsStore
    @State private var create=false
    @State private var name=""
    @State private var description=""
    @State private var busy=false
    var body:some View {
        List {
            Section {
                Button{create=true}label:{Label("Crear colección",systemImage:"folder.badge.plus")}
                if store.collections.isEmpty{Text("Organiza tus ideas y lugares en colecciones privadas.").font(.caption).foregroundStyle(.secondary)}
            }
            ForEach(store.collections){collection in
                Section {
                    DisclosureGroup {
                        if collection.entries.isEmpty {Text("Tu colección está lista. Añade ideas desde Explorar.").font(.caption).foregroundStyle(.secondary)}
                        ForEach(collection.entries,id:\.self){entry in
                            HStack{Text(entry);Spacer();Button(role:.destructive){Task{await store.change(entry,in:collection,add:false)}}label:{Image(systemName:"minus.circle")}.buttonStyle(.borderless)}
                        }
                    }label:{VStack(alignment:.leading){Text(collection.name).font(.headline);Text(collection.description).font(.caption).foregroundStyle(.secondary);Text("\(collection.entries.count) elementos").font(.caption)}}
                }
            }
            if let message=store.message {Text(message).font(.caption).foregroundStyle(.secondary)}
        }.refreshable{await store.load()}
        .sheet(isPresented:$create) {
            NavigationStack {
                Form {
                    TextField("Nombre de la colección",text:$name).onChange(of:name){name=String($0.prefix(32))}
                    TextField("Descripción (opcional)",text:$description,axis:.vertical).onChange(of:description){description=String($0.prefix(120))}
                    if let message=store.message{Text(message).font(.caption).foregroundStyle(.red)}
                }.navigationTitle("Nueva colección").toolbar {
                    ToolbarItem(placement:.cancellationAction){Button("Cancelar"){create=false}}
                    ToolbarItem(placement:.confirmationAction){Button("Crear"){
                        Task{busy=true;defer{busy=false};if await store.create(name:name,description:description){name="";description="";create=false}}
                    }.disabled(busy || name.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty)}
                }
            }
        }
    }
}
