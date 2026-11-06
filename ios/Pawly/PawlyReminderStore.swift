import Foundation
import UserNotifications
import Combine
import FirebaseAuth

struct PawlyLocalReminder:Identifiable,Codable{
 var id:String;var title:String;var petName:String;var date:Date;var notes:String;var repeatRule:String;var completed:Bool
}
@MainActor final class PawlyReminderStore:ObservableObject{
 static let shared=PawlyReminderStore()
 @Published private(set) var reminders:[PawlyLocalReminder]=[]
 @Published var statusMessage:String?
 private let center=UNUserNotificationCenter.current()
 private var activeUID:String?
 private var key:String{"pawly.localReminders.\(activeUID ?? "local")"}
 private init(){activeUID=Auth.auth().currentUser?.uid;loadStored();rescheduleAll()}
 func loadForCurrentUser(){
  let uid=Auth.auth().currentUser?.uid
  guard uid != activeUID else{return}
  center.removeAllPendingNotificationRequests();activeUID=uid;loadStored();rescheduleAll()
 }
 private func loadStored(){
  if let data=UserDefaults.standard.data(forKey:key),let values=try? JSONDecoder().decode([PawlyLocalReminder].self,from:data){reminders=values}else{reminders=[]}
 }
 func requestPermission()async->String{
  do{let granted=try await center.requestAuthorization(options:[.alert,.sound,.badge]);statusMessage=granted ? "Notificaciones activadas en este dispositivo.":"Permiso denegado. Actívalo en Ajustes de iOS.";if granted{rescheduleAll()}}
  catch{statusMessage=error.localizedDescription};return statusMessage ?? ""
 }
 func refreshAuthorizationStatus(){center.getNotificationSettings{[weak self] settings in Task{@MainActor in self?.statusMessage=settings.authorizationStatus == .authorized ? "Avisos locales activados.":"Activa las notificaciones en Ajustes de iOS."}}}
 func add(title:String,petName:String,date:Date,notes:String,repeatRule:String){let x=PawlyLocalReminder(id:UUID().uuidString,title:title.trimmingCharacters(in:.whitespacesAndNewlines),petName:petName,date:date,notes:notes,repeatRule:repeatRule,completed:false);reminders.append(x);persist();schedule(x)}
 func toggle(_ item:PawlyLocalReminder){guard let i=reminders.firstIndex(where:{$0.id==item.id})else{return};reminders[i].completed.toggle();if reminders[i].completed{center.removePendingNotificationRequests(withIdentifiers:[item.id])}else{schedule(reminders[i])};persist()}
 func remove(_ item:PawlyLocalReminder){reminders.removeAll{$0.id==item.id};center.removePendingNotificationRequests(withIdentifiers:[item.id]);persist()}
 func cancelAllNotifications(){center.removeAllPendingNotificationRequests();statusMessage="Avisos pausados. Los recordatorios siguen guardados."}
 private func persist(){if let d=try? JSONEncoder().encode(reminders){UserDefaults.standard.set(d,forKey:key)}}
 private func rescheduleAll(){center.removeAllPendingNotificationRequests();reminders.filter{!$0.completed && $0.date>Date()}.forEach(schedule)}
 private func schedule(_ item:PawlyLocalReminder){
  guard !item.completed,item.date>Date()else{return}
  let content=UNMutableNotificationContent();content.title=item.title;content.body=item.notes.isEmpty ? "Recordatorio para \(item.petName)":"\(item.petName): \(item.notes)";content.sound = .default
  var c=Calendar.current.dateComponents([.year,.month,.day,.hour,.minute],from:item.date);let repeats=item.repeatRule != "Nunca"
  if repeats{switch item.repeatRule{case "Cada día":c=Calendar.current.dateComponents([.hour,.minute],from:item.date);case "Cada semana":c=Calendar.current.dateComponents([.weekday,.hour,.minute],from:item.date);case "Cada mes":c=Calendar.current.dateComponents([.day,.hour,.minute],from:item.date);case "Cada año":c=Calendar.current.dateComponents([.month,.day,.hour,.minute],from:item.date);default:break}}
  center.add(UNNotificationRequest(identifier:item.id,content:content,trigger:UNCalendarNotificationTrigger(dateMatching:c,repeats:repeats))){[weak self] e in if let e{Task{@MainActor in self?.statusMessage=e.localizedDescription}}}
 }
}
struct PawlyRemindersView:View{
 @StateObject private var store=PawlyReminderStore.shared
 @EnvironmentObject private var pets:PawlyPetStore
 @State private var showAdd=false
 var body:some View{
  List{
   Section("Mis recordatorios"){
    if store.reminders.isEmpty{Text("Todavía no tienes avisos. Crea uno para vacunas, medicación, revisiones o cualquier cuidado.").font(.subheadline).foregroundStyle(.secondary)}
    ForEach(store.reminders){item in HStack(spacing:10){
     Button{store.toggle(item)}label:{Image(systemName:item.completed ? "checkmark.circle.fill":"circle").font(.title2).foregroundStyle(PawlyColors.primary)}.buttonStyle(.plain)
     VStack(alignment:.leading,spacing:3){Text(item.title).strikethrough(item.completed).font(.headline);Text("\(item.petName) · \(item.date.formatted(date:.abbreviated,time:.shortened))").font(.caption).foregroundStyle(.secondary);if !item.notes.isEmpty{Text(item.notes).font(.caption).foregroundStyle(.secondary)}}
     Spacer();Button(role:.destructive){store.remove(item)}label:{Image(systemName:"trash")}.buttonStyle(.borderless)
    }.padding(.vertical,4)}
   }
   Section{Button{showAdd=true}label:{Label("Crear recordatorio",systemImage:"plus.circle.fill")};Button{Task{_ = await store.requestPermission()}}label:{Label("Activar avisos",systemImage:"bell.badge")}}
   if let s=store.statusMessage{Section{Text(s).font(.caption).foregroundStyle(.secondary)}}
  }.navigationTitle("Agenda").sheet(isPresented:$showAdd){PawlyReminderForm(petName:pets.selectedPet?.name ?? "Mi mascota"){t,d,n,r in store.add(title:t,petName:pets.selectedPet?.name ?? "Mi mascota",date:d,notes:n,repeatRule:r)}}.onAppear{store.loadForCurrentUser();store.refreshAuthorizationStatus()}
 }
}
struct PawlyReminderForm:View{
 @Environment(\.dismiss) private var dismiss
 let petName:String
 var onSave:(String,Date,String,String)->Void
 @State private var title=""
 @State private var date=Calendar.current.date(byAdding:.day,value:1,to:Date()) ?? Date()
 @State private var notes=""
 @State private var repeatRule="Nunca"
 var body:some View{NavigationStack{Form{
  TextField("Qué hay que recordar",text:$title);DatePicker("Fecha y hora",selection:$date,in:Date()...)
  TextField("Notas (opcional)",text:$notes,axis:.vertical).lineLimit(2...4)
  Picker("Repetición",selection:$repeatRule){ForEach(["Nunca","Cada día","Cada semana","Cada mes","Cada año"],id:\.self){Text($0)}}
  LabeledContent("Mascota",value:petName)
 }.navigationTitle("Nuevo aviso").toolbar{
  ToolbarItem(placement:.cancellationAction){Button("Cancelar"){dismiss()}}
  ToolbarItem(placement:.confirmationAction){Button("Guardar"){onSave(title,date,notes,repeatRule);dismiss()}.disabled(title.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty || date<=Date())}
 }}}
}
