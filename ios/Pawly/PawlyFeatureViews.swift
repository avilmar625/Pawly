import SwiftUI
import FirebaseFirestore
import PhotosUI
import AVKit
import FirebaseAuth

struct PawlyTabView: View {
    @StateObject private var pets = PawlyPetStore()
    var body: some View {
        TabView {
            HomeView().tabItem { Label("Inicio",systemImage:"house.fill") }
            CareView().tabItem { Label("Cuidados",systemImage:"cross.case.fill") }
            DiaryView().tabItem { Label("Recuerdos",systemImage:"book.closed.fill") }
            CommunityView().tabItem { Label("Comunidad",systemImage:"person.3.fill") }
            MessagesView().tabItem { Label("Mensajes",systemImage:"bubble.left.and.bubble.right.fill") }
            AccountView().tabItem { Label("Cuenta",systemImage:"person.crop.circle") }
        }
        .tint(PawlyColors.primary)
        .environmentObject(pets)
        .task { await pets.loadPets() }
    }
}

struct HomeView: View {
    @EnvironmentObject private var session:PawlySession
    @EnvironmentObject private var pets:PawlyPetStore
    @State private var showAdd=false
    @State private var showDelete=false
    @State private var petToDelete:PawlyPet?
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment:.leading,spacing:18) {
                    HStack {
                        VStack(alignment:.leading,spacing:4) {
                            Text("Hola, \(session.user?.displayName?.components(separatedBy:" ").first ?? "bienvenido")")
                                .font(.title2.bold()).foregroundStyle(PawlyColors.ink)
                            Text("Un día más de aventuras juntos").font(.subheadline).foregroundStyle(PawlyColors.muted)
                        }
                        Spacer()
                        Image(systemName:"sun.max.fill").font(.title2).foregroundStyle(.orange)
                            .padding(13).background(PawlyColors.peach,in:Circle())
                    }
                    if let pet=pets.selectedPet {
                        VStack(alignment:.leading,spacing:10) {
                            HStack {
                                Image(systemName:"pawprint.fill").font(.title2)
                                Spacer()
                                PetPicker()
                            }.foregroundStyle(.white)
                            Text(pet.name).font(.largeTitle.bold()).foregroundStyle(.white)
                            Text([pet.species,pet.breed,pet.age].filter{!$0.isEmpty}.joined(separator:" · "))
                                .font(.subheadline).foregroundStyle(.white.opacity(0.85))
                            if !pet.description.isEmpty { Text(pet.description).font(.subheadline).foregroundStyle(.white.opacity(0.9)) }
                        }
                        .padding(20).frame(maxWidth:.infinity,alignment:.leading)
                        .background(LinearGradient(colors:[PawlyColors.primary,Color(red:0.35,green:0.66,blue:0.53)],startPoint:.topLeading,endPoint:.bottomTrailing),
                                    in:RoundedRectangle(cornerRadius:24))
                        .contextMenu { Button(role:.destructive) { petToDelete=pet;showDelete=true } label:{Label("Eliminar perfil",systemImage:"trash")} }
                    } else {
                        PawlyEmptyState(icon:"pawprint",title:"Tu historia empieza aquí",detail:"Añade el perfil de tu mascota para organizar sus cuidados y recuerdos.")
                    }
                    HStack {
                        Text("Mis compañeros").font(.title3.bold()).foregroundStyle(PawlyColors.ink)
                        Spacer()
                        Button { showAdd=true } label:{Label("Añadir",systemImage:"plus").font(.subheadline.weight(.semibold))}
                            .tint(PawlyColors.primary)
                    }
                    ForEach(pets.pets) { pet in
                        Button { pets.select(pet) } label:{
                            HStack(spacing:12) {
                                Image(systemName:pet.species == "Gato" ? "cat.fill":"pawprint.fill")
                                    .foregroundStyle(PawlyColors.primary).padding(12).background(PawlyColors.mint,in:Circle())
                                VStack(alignment:.leading,spacing:3) {
                                    Text(pet.name).font(.headline).foregroundStyle(PawlyColors.ink)
                                    Text([pet.breed,pet.age].filter{!$0.isEmpty}.joined(separator:" · "))
                                        .font(.caption).foregroundStyle(PawlyColors.muted)
                                }
                                Spacer()
                                if pets.selectedPetID==pet.id { Image(systemName:"checkmark.circle.fill").foregroundStyle(PawlyColors.primary) }
                            }.padding(13).background(.white,in:RoundedRectangle(cornerRadius:16))
                        }.buttonStyle(.plain)
                    }
                    if let error=pets.errorMessage { Text(error).font(.caption).foregroundStyle(.red) }
                }.padding(20)
            }
            .background(PawlyColors.canvas)
            .navigationBarHidden(true)
            .toolbar { ToolbarItem(placement:.topBarTrailing) { Button { showAdd=true } label:{Image(systemName:"plus")} } }
            .sheet(isPresented:$showAdd) { PetFormSheet() }
            .confirmationDialog("¿Eliminar el perfil de \(petToDelete?.name ?? "esta mascota")?",isPresented:$showDelete,titleVisibility:.visible) {
                Button("Eliminar perfil",role:.destructive) {
                    if let pet=petToDelete { Task { try? await pets.deletePet(pet) } }
                }
            } message:{Text("Esto elimina el perfil de la lista de tu cuenta. Los registros históricos no se borran automáticamente.")}
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
                    if pet.id==pets.selectedPetID { Label(pet.name,systemImage:"checkmark") } else { Text(pet.name) }
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
    @State private var name=""
    @State private var breed=""
    @State private var age=""
    @State private var species="Perro"
    @State private var description=""
    @State private var error:String?
    @State private var saving=false
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
                if let error {Text(error).foregroundStyle(.red)}
            }
            .navigationTitle("Añadir mascota")
            .toolbar {
                ToolbarItem(placement:.cancellationAction){Button("Cancelar"){dismiss()}}
                ToolbarItem(placement:.confirmationAction){
                    Button(saving ? "Guardando…" : "Guardar"){
                        Task {saving=true;do{try await pets.savePet(name:name,breed:breed,age:age,species:species,description:description);dismiss()}catch{self.error=error.localizedDescription};saving=false}
                    }.disabled(saving || name.trimmingCharacters(in:.whitespaces).isEmpty)
                }
            }
        }
    }
}

struct CareView:View {
    @EnvironmentObject private var pets:PawlyPetStore
    @State private var passport:[PawlyPassportEntry]=[]
    @State private var tasks:[PawlyCareTask]=[]
    @State private var showEntry=false
    @State private var showTask=false
    @State private var showShare=false
    @State private var exportURL:URL?
    @State private var error:String?
    var body:some View {
        NavigationStack {
            Group {
                if pets.pets.isEmpty {PawlyEmptyState(icon:"cross.case",title:"Añade a tu mascota",detail:"Su pasaporte sanitario y sus rutinas aparecerán aquí.")}
                else {
                    List {
                        Section {
                            HStack {Text("Pasaporte sanitario").font(.headline);Spacer();Button {showEntry=true} label:{Image(systemName:"plus.circle.fill").font(.title2)} }
                            if passport.isEmpty {Text("Todavía no hay registros.").foregroundStyle(.secondary)}
                            ForEach(passport){entry in
                                VStack(alignment:.leading,spacing:4){
                                    HStack{Text(entry.title).font(.subheadline.weight(.semibold));Spacer();Text(entry.type).font(.caption).foregroundStyle(PawlyColors.primary)}
                                    Text("\(entry.date) · \(entry.detail)").font(.caption).foregroundStyle(.secondary)
                                }.swipeActions{Button(role:.destructive){removeEntry(entry)} label:{Label("Eliminar",systemImage:"trash")}}
                            }
                        }
                        Section {
                            HStack {Text("Rutinas de cuidado").font(.headline);Spacer();Button {showTask=true} label:{Image(systemName:"plus.circle.fill").font(.title2)} }
                            if tasks.isEmpty {Text("Añade alimentación, paseos, medicación o revisiones.").foregroundStyle(.secondary)}
                            ForEach(tasks){task in
                                HStack {
                                    Button {toggle(task)} label:{Image(systemName:task.completed ? "checkmark.circle.fill":"circle").foregroundStyle(PawlyColors.primary)}
                                        .buttonStyle(.plain)
                                    VStack(alignment:.leading,spacing:3){Text(task.title).strikethrough(task.completed);Text([task.frequency,task.responsible].filter{!$0.isEmpty}.joined(separator:" · ")).font(.caption).foregroundStyle(.secondary)}
                                }.swipeActions{Button(role:.destructive){removeTask(task)} label:{Label("Eliminar",systemImage:"trash")}}
                            }
                        }
                        if let error {Section{Text(error).font(.caption).foregroundStyle(.red)}}
                    }.refreshable{await load()}
                }
            }
            .navigationTitle("Salud y cuidados")
              .toolbar{ToolbarItemGroup(placement:.topBarTrailing){
                if let exportURL { ShareLink(item:exportURL){Image(systemName:"square.and.arrow.up")} }
                else { Button(action:exportPDF){Image(systemName:"square.and.arrow.up")} }
                Button{showShare=true} label:{Image(systemName:"person.2.badge.plus")}
                PetPicker()
            }}
            .task(id:pets.selectedPetID){await load()}
            .sheet(isPresented:$showEntry){PassportEntrySheet{entry in await save(entry)}}
             .sheet(isPresented:$showTask){CareTaskSheet{task in await save(task)}}
            .sheet(isPresented:$showShare){PetSharingSheet()}
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
        guard let pet=pets.selectedPet else{return}
        do{async let p=pets.loadPassport(pet.id);async let t=pets.loadTasks(pet.id);passport=try await p;tasks=try await t;error=nil}
        catch{self.error=error.localizedDescription}
    }
    private func save(_ entry:PawlyPassportEntry) async {guard let pet=pets.selectedPet else{return};do{try await pets.savePassport(entry,pet:pet);await load()}catch{self.error=error.localizedDescription}}
    private func save(_ task:PawlyCareTask) async {guard let pet=pets.selectedPet else{return};do{try await pets.saveTask(task,pet:pet);await load()}catch{self.error=error.localizedDescription}}
    private func removeEntry(_ e:PawlyPassportEntry){guard let p=pets.selectedPet else{return};Task{do{try await pets.deletePassport(e.id,pet:p);await load()}catch { self.error = error.localizedDescription }}}
    private func removeTask(_ t:PawlyCareTask){guard let p=pets.selectedPet else{return};Task{do{try await pets.deleteTask(t.id,pet:p);await load()}catch { self.error = error.localizedDescription }}}
    private func toggle(_ t:PawlyCareTask){guard let p=pets.selectedPet else{return};Task{var x=t;x.completed.toggle();do{try await pets.saveTask(x,pet:p);await load()}catch { self.error = error.localizedDescription }}}
}

struct PassportEntrySheet:View {
    @Environment(\.dismiss) private var dismiss
    var onSave:(PawlyPassportEntry) async->Void
    @State private var title=""
    @State private var type="Vacuna"
    @State private var detail=""
    @State private var date=Date()
    var body:some View {
        NavigationStack {
            Form {
                TextField("Título",text:$title)
                Picker("Tipo",selection:$type){ForEach(["Vacuna","Revisión","Tratamiento","Desparasitación","Alergia","Otro"],id:\.self){Text($0)}}
                DatePicker("Fecha",selection:$date,displayedComponents:.date)
                TextField("Detalles, clínica, dosis…",text:$detail,axis:.vertical).lineLimit(3...6)
            }.navigationTitle("Nuevo registro").toolbar{
                ToolbarItem(placement:.cancellationAction){Button("Cancelar"){dismiss()}}
                ToolbarItem(placement:.confirmationAction){Button("Guardar"){
                    Task{let f=DateFormatter();f.dateFormat="yyyy-MM-dd";await onSave(PawlyPassportEntry(id:UUID().uuidString,type:type,title:title,detail:detail,date:f.string(from:date)));dismiss()}
                }.disabled(title.trimmingCharacters(in:.whitespaces).isEmpty)}
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
    var body:some View {
        NavigationStack {
            Form {
                TextField("Tarea",text:$title);TextField("Responsable",text:$responsible)
                Picker("Frecuencia",selection:$frequency){ForEach(["Diaria","Semanal","Mensual","Cada 3 meses","Cada 6 meses","Anual","Cuando sea necesario"],id:\.self){Text($0)}}
            }.navigationTitle("Nueva rutina").toolbar{
                ToolbarItem(placement:.cancellationAction){Button("Cancelar"){dismiss()}}
                ToolbarItem(placement:.confirmationAction){Button("Guardar"){
                    Task{await onSave(PawlyCareTask(id:UUID().uuidString,title:title,responsible:responsible,frequency:frequency,completed:false));dismiss()}
                }.disabled(title.trimmingCharacters(in:.whitespaces).isEmpty)}
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
                        .disabled(busy || pets.selectedPet == nil || username.trimmingCharacters(in:.whitespaces).count<3)
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
    @EnvironmentObject private var pets:PawlyPetStore
    @State private var entries:[PawlyDiaryEntry]=[]
    @State private var draft=""
    @State private var error:String?
    var body:some View {
        NavigationStack {
            Group {
                if pets.pets.isEmpty {PawlyEmptyState(icon:"book.closed",title:"Tu diario empieza aquí",detail:"Añade una mascota para guardar recuerdos.")}
                else {
                    VStack(spacing:0) {
                        VStack(alignment:.leading,spacing:9){
                            PetPicker()
                            Text("Pequeños momentos, grandes recuerdos").font(.title2.bold()).foregroundStyle(PawlyColors.ink)
                            Text("Tus recuerdos se sincronizan con el perfil compartido de la mascota.").font(.subheadline).foregroundStyle(PawlyColors.muted)
                            TextField("¿Qué queréis recordar?",text:$draft,axis:.vertical).lineLimit(2...4).padding(12).background(PawlyColors.canvas,in:RoundedRectangle(cornerRadius:14))
                            Button(action:add){Label("Guardar recuerdo",systemImage:"plus").frame(maxWidth:.infinity).padding(12).background(PawlyColors.primary,in:RoundedRectangle(cornerRadius:13)).foregroundStyle(.white)}
                                .disabled(draft.trimmingCharacters(in:.whitespaces).isEmpty)
                            if let error {Text(error).font(.caption).foregroundStyle(.red)}
                        }.padding(17)
                        List {
                            if entries.isEmpty {Text("Todavía no hay recuerdos. El primero siempre es especial.").foregroundStyle(.secondary)}
                            ForEach(entries){e in
                                VStack(alignment:.leading,spacing:5){Text(e.text);if let d=e.updatedAt{Text(d.formatted(date:.abbreviated,time:.shortened)).font(.caption).foregroundStyle(.secondary)}}
                                    .swipeActions{Button(role:.destructive){remove(e)} label:{Label("Eliminar",systemImage:"trash")}}
                            }
                        }.listStyle(.plain).refreshable{await load()}
                    }
                }
            }.navigationTitle("Recuerdos").task(id:pets.selectedPetID){await load()}
        }
    }
    @MainActor private func load() async {guard let p=pets.selectedPet else{return};do{entries=try await pets.loadDiary(p);error=nil}catch{self.error=error.localizedDescription}}
    private func add(){guard let p=pets.selectedPet else{return};let value=draft;Task{do{try await pets.addDiaryEntry(value,pet:p);draft="";await load()}catch { self.error = error.localizedDescription }}}
    private func remove(_ e:PawlyDiaryEntry){guard let p=pets.selectedPet else{return};Task{do{try await pets.deleteDiaryEntry(e.id,pet:p);await load()}catch { self.error = error.localizedDescription }}}
}

struct CommunityView:View {
    @StateObject private var store=PawlyCommunityStore()
    @EnvironmentObject private var pets:PawlyPetStore
    @State private var composer=false
    @State private var people=false
    @State private var comments:PawlyFeedPost?
    var body:some View {
        NavigationStack {
            ScrollView {
                VStack(spacing:13) {
                    HStack{VStack(alignment:.leading,spacing:3){Text("La comunidad").font(.title2.bold());Text("Historias y aventuras Pawly").font(.caption).foregroundStyle(PawlyColors.muted)};Spacer();Button{people=true} label:{Image(systemName:"magnifyingglass").font(.headline).foregroundStyle(PawlyColors.primary).padding(12).background(PawlyColors.mint,in:Circle())};Button{composer=true} label:{Image(systemName:"plus").font(.headline).foregroundStyle(.white).padding(12).background(PawlyColors.primary,in:Circle())}}
                    if let status=store.statusMessage{Text(status).font(.caption).foregroundStyle(.red)}
                    if store.posts.isEmpty{PawlyEmptyState(icon:"person.3",title:"Todavía no hay historias",detail:"Comparte una aventura para iniciar el muro.").frame(minHeight:260)}
                    ForEach(store.posts){post in
                        FeedPostCard(post:post,onLike:{Task{do{try await store.toggleLike(post)}catch { store.statusMessage = error.localizedDescription }}},onComment:{comments=post})
                    }
                }.padding(15)
            }.background(PawlyColors.canvas).navigationBarHidden(true)
                .onAppear{store.listenFeed()}.onDisappear{store.stopFeed()}
                .sheet(isPresented:$composer){PostComposerSheet(petName:pets.selectedPet?.name ?? ""){text,category,imageURL,videoURL in try await store.publish(text:text,petName:pets.selectedPet?.name ?? "",category:category,imageURL:imageURL,videoURL:videoURL)}}
                .sheet(item:$comments){post in CommentsView(store:store,post:post)}
                .sheet(isPresented:$people){PeopleSearchSheet(store:store)}
        }
    }
}

struct PeopleSearchSheet:View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var store:PawlyCommunityStore
    @State private var query=""
    @State private var results:[PawlyPublicUser]=[]
    @State private var loading=false
    @State private var status:String?
    @State private var blockCandidate:PawlyPublicUser?
    @State private var reportCandidate:PawlyPublicUser?
    var body:some View {
        NavigationStack {
            VStack(spacing:10) {
                HStack {
                    TextField("Buscar por nombre de usuario",text:$query).textInputAutocapitalization(.never).autocorrectionDisabled().textFieldStyle(.roundedBorder)
                    Button("Buscar"){search()}.disabled(query.trimmingCharacters(in:.whitespaces).count<2)
                }.padding(.horizontal)
                if loading {ProgressView()}
                if let status {Text(status).font(.caption).foregroundStyle(PawlyColors.muted).padding(.horizontal)}
                List(results) { user in
                    VStack(alignment:.leading,spacing:9) {
                        HStack(spacing:10) {
                            Image(systemName:"person.crop.circle.fill").font(.largeTitle).foregroundStyle(PawlyColors.primary)
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
        do{results=try await store.searchUsers(query);status=results.isEmpty ? "No se han encontrado perfiles públicos.":nil}
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
            HStack{Image(systemName:"person.crop.circle.fill").font(.title2).foregroundStyle(PawlyColors.primary);VStack(alignment:.leading,spacing:2){Text(post.authorName).font(.subheadline.weight(.semibold));Text(post.petName.isEmpty ? post.category : "\(post.petName) · \(post.category)").font(.caption).foregroundStyle(PawlyColors.muted)};Spacer();if let d=post.createdAt{Text(d.formatted(date:.abbreviated,time:.omitted)).font(.caption2).foregroundStyle(.secondary)}}
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
        }.padding(15).background(.white,in:RoundedRectangle(cornerRadius:19))
    }
}

struct PostComposerSheet:View {
    @Environment(\.dismiss) private var dismiss
    var petName:String
    var onPublish:(String,String,String,String) async throws->Void
    @State private var text=""
    @State private var category="STORY"
    @State private var busy=false
    @State private var error:String?
    @State private var imageItem:PhotosPickerItem?
    @State private var videoItem:PhotosPickerItem?
    var body:some View {
        NavigationStack {
            Form {
                Picker("Tipo",selection:$category){Text("Historia").tag("STORY");Text("Adopción").tag("ADOPTION");Text("Perdido").tag("LOST");Text("Encontrado").tag("FOUND");Text("Acogida").tag("FOSTER")}
                TextField("¿Qué queréis compartir?",text:$text,axis:.vertical).lineLimit(5...10)
                HStack {
                    PhotosPicker(selection:$imageItem,matching:.images){Label("Foto",systemImage:"photo")}
                    PhotosPicker(selection:$videoItem,matching:.videos){Label("Vídeo",systemImage:"video")}
                }
                if imageItem != nil { Label("Fotografía seleccionada",systemImage:"checkmark.circle.fill").font(.caption).foregroundStyle(PawlyColors.primary) }
                if videoItem != nil { Label("Vídeo seleccionado",systemImage:"checkmark.circle.fill").font(.caption).foregroundStyle(PawlyColors.primary) }
                if !petName.isEmpty{LabeledContent("Mascota",value:petName)}
                if let error{Text(error).foregroundStyle(.red)}
                Text("Para publicar necesitas verificar tu correo.").font(.caption).foregroundStyle(.secondary)
            }.navigationTitle("Nueva publicación").toolbar{
                ToolbarItem(placement:.cancellationAction){Button("Cancelar"){dismiss()}}
                ToolbarItem(placement:.confirmationAction){Button(busy ? "Publicando…" : "Publicar"){
                    Task {
                        busy=true
                        do {
                            guard let uid=Auth.auth().currentUser?.uid else { throw PawlyCommunityError.notSignedIn }
                            let uploader=PawlyCloudinaryUploader()
                            var imageURL="",videoURL=""
                            if let item=imageItem {
                                guard let data=try await item.loadTransferable(type:Data.self) else { throw PawlyMediaError.invalidImage }
                                imageURL=try await uploader.uploadImage(data,folder:"post-photos",ownerUID:uid)
                            }
                            if let item=videoItem {
                                guard let data=try await item.loadTransferable(type:Data.self) else { throw PawlyMediaError.videoTooLarge }
                                videoURL=try await uploader.uploadVideo(data,folder:"post-videos",ownerUID:uid)
                            }
                            try await onPublish(text,category,imageURL,videoURL)
                            dismiss()
                        } catch { self.error=error.localizedDescription }
                        busy=false
                    }
                }.disabled(busy || (text.trimmingCharacters(in:.whitespaces).isEmpty && imageItem == nil && videoItem == nil))}
            }
        }
    }
}

struct CommentsView:View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var store:PawlyCommunityStore
    let post:PawlyFeedPost
    @State private var comments:[PawlyFeedComment]=[]
    @State private var draft=""
    @State private var listener:ListenerRegistration?
    @State private var error:String?
    var body:some View {
        NavigationStack {
            VStack {
                List(comments){c in VStack(alignment:.leading,spacing:4){Text(c.authorName).font(.caption.bold()).foregroundStyle(PawlyColors.primary);Text(c.text)}}
                HStack{TextField("Escribe un comentario",text:$draft).textFieldStyle(.roundedBorder);Button("Enviar"){let v=draft;Task{do{try await store.addComment(postID:post.id,text:v);draft=""}catch { self.error = error.localizedDescription }}}.disabled(draft.trimmingCharacters(in:.whitespaces).isEmpty)}.padding()
                if let error{Text(error).font(.caption).foregroundStyle(.red)}
            }.navigationTitle("Comentarios").toolbar{ToolbarItem(placement:.confirmationAction){Button("Cerrar"){dismiss()}}}
                .onAppear{listener=store.listenComments(postID:post.id){comments=$0}}.onDisappear{listener?.remove()}
        }
    }
}

struct MessagesView:View {
    @StateObject private var store=PawlyCommunityStore()
    @State private var newChat=false
    @State private var selected:PawlyChat?
    var body:some View {
        NavigationStack {
            List {
                if store.chats.isEmpty{Text("Tu bandeja está vacía. Inicia una conversación con otra persona de Pawly.").foregroundStyle(.secondary)}
                ForEach(store.chats){chat in Button{selected=chat}label{HStack(spacing:12){Image(systemName:"person.crop.circle.fill").font(.largeTitle).foregroundStyle(PawlyColors.primary);VStack(alignment:.leading,spacing:4){Text(chat.otherUserName).font(.headline).foregroundStyle(PawlyColors.ink);Text(chat.lastMessage.isEmpty ? "Iniciar conversación":chat.lastMessage).font(.caption).foregroundStyle(PawlyColors.muted).lineLimit(1)}}.padding(.vertical,4)}}
            }.navigationTitle("Mensajes").toolbar{ToolbarItem(placement:.topBarTrailing){Button{newChat=true} label:{Image(systemName:"square.and.pencil")}}}
                .onAppear{store.listenChats()}.onDisappear{store.stopChats()}
                .sheet(isPresented:$newChat){NewChatSheet{username in try await store.startConversation(username:username);store.listenChats()}}
                .sheet(item:$selected){chat in ChatView(store:store,chat:chat)}
        }
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
            if message.senderID == store.uid { Spacer(minLength:50) }
            VStack(alignment:.leading,spacing:5) {
                if !message.text.isEmpty {
                    Text(message.text).foregroundStyle(message.senderID == store.uid ? .white : PawlyColors.ink)
                }
                if !message.imageURL.isEmpty, let url=URL(string:message.imageURL) {
                    AsyncImage(url:url) { image in image.resizable().scaledToFill() } placeholder: { ProgressView() }
                        .frame(width:210,height:160).clipShape(RoundedRectangle(cornerRadius:10))
                }
                if !message.videoURL.isEmpty, let url=URL(string:message.videoURL) {
                    VideoPlayer(player:AVPlayer(url:url)).frame(width:230,height:160)
                        .clipShape(RoundedRectangle(cornerRadius:10))
                }
                if message.senderID == store.uid && message.readBy.contains(chat.otherUserID) {
                    Text("Visto").font(.caption2).foregroundStyle(.white.opacity(0.7))
                }
            }
            .padding(12)
            .background(message.senderID == store.uid ? PawlyColors.primary : Color.white,in:RoundedRectangle(cornerRadius:15))
            if message.senderID != store.uid { Spacer(minLength:50) }
        }
    }

    private var composer:some View {
        HStack(spacing:10) {
            PhotosPicker(selection:$imageItem,matching:.images) {
                Image(systemName:"photo").font(.title3).foregroundStyle(PawlyColors.primary)
            }
            PhotosPicker(selection:$videoItem,matching:.videos) {
                Image(systemName:"video").font(.title3).foregroundStyle(PawlyColors.primary)
            }
            TextField("Mensaje",text:$draft,axis:.vertical).lineLimit(1...4).textFieldStyle(.roundedBorder)
            Button(action:send) {
                Image(systemName:"arrow.up.circle.fill").font(.system(size:30)).foregroundStyle(PawlyColors.primary)
            }
            .disabled(draft.trimmingCharacters(in:.whitespaces).isEmpty && imageItem == nil && videoItem == nil)
        }
        .padding()
        .onChange(of:imageItem) { _ in if imageItem != nil { videoItem=nil } }
        .onChange(of:videoItem) { _ in if videoItem != nil { imageItem=nil } }
    }

    private func send() {
        let value=draft
        Task {
            do {
                guard let uid=Auth.auth().currentUser?.uid else { throw PawlyCommunityError.notSignedIn }
                let uploader=PawlyCloudinaryUploader()
                var imageURL="",videoURL=""
                if let item=imageItem {
                    guard let data=try await item.loadTransferable(type:Data.self) else { throw PawlyMediaError.invalidImage }
                    imageURL=try await uploader.uploadImage(data,folder:"message-photos",ownerUID:uid)
                }
                if let item=videoItem {
                    guard let data=try await item.loadTransferable(type:Data.self) else { throw PawlyMediaError.videoTooLarge }
                    videoURL=try await uploader.uploadVideo(data,folder:"message-videos",ownerUID:uid)
                }
                try await store.sendMessage(conversationID:chat.id,text:value,imageURL:imageURL,videoURL:videoURL)
                draft="";imageItem=nil;videoItem=nil;error=nil
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
    var body:some View{
        NavigationStack{
            Form{
                Section("Cuenta"){
                    LabeledContent("Correo",value:session.user?.email ?? "—")
                    LabeledContent("Verificación",value:session.user?.isEmailVerified == true ? "Verificado":"Pendiente")
                }
                Section("Mascotas"){ForEach(pets.pets){pet in Text(pet.name)}}
                Section("Privacidad"){
                    Button("Solicitar eliminación de cuenta",role:.destructive){showDeletionConfirmation=true}
                    if let deletionStatus { Text(deletionStatus).font(.footnote).foregroundStyle(PawlyColors.muted) }
                }
                Section{Button("Cerrar sesión",role:.destructive){do{try session.signOut()}catch{self.error=error.localizedDescription}}}
                if let error{Text(error).foregroundStyle(.red)}
            }
            .navigationTitle("Mi cuenta")
            .confirmationDialog("¿Solicitar la eliminación de tu cuenta?",isPresented:$showDeletionConfirmation,titleVisibility:.visible){
                Button("Enviar solicitud",role:.destructive){
                    Task{do{try await session.requestAccountDeletion();deletionStatus="Solicitud registrada. El equipo debe completar la eliminación de los datos.";error=nil}
                        catch{self.error=error.localizedDescription}}
                }
                Button("Cancelar",role:.cancel){}
            }message:{Text("Esto envía una solicitud al equipo de Pawly; no elimina automáticamente tu cuenta ni los archivos almacenados.")}
        }
    }
}
struct PawlyEmptyState:View {
    let icon:String;let title:String;let detail:String
    var body:some View{VStack(spacing:12){Image(systemName:icon).font(.system(size:38)).foregroundStyle(PawlyColors.primary).padding(20).background(PawlyColors.mint,in:Circle());Text(title).font(.title3.bold()).foregroundStyle(PawlyColors.ink);Text(detail).font(.subheadline).multilineTextAlignment(.center).foregroundStyle(PawlyColors.muted).padding(.horizontal,28)}.frame(maxWidth:.infinity,maxHeight:.infinity).background(PawlyColors.canvas)}
}
