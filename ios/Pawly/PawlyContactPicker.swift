import SwiftUI
import Contacts
import ContactsUI

/// Apple's single-contact property picker: never enumerates or uploads the address book.
struct PawlyContactPicker: UIViewControllerRepresentable {
    var onSelect: (String,String) -> Void
    var onCancel: () -> Void
    func makeUIViewController(context:Context) -> CNContactPickerViewController {
        let picker=CNContactPickerViewController()
        picker.delegate=context.coordinator
        picker.displayedPropertyKeys=[CNContactPhoneNumbersKey]
        picker.predicateForEnablingContact=NSPredicate(format:"phoneNumbers.@count > 0")
        picker.predicateForSelectionOfContact=NSPredicate(value:false)
        picker.predicateForSelectionOfProperty=NSPredicate(format:"key == %@",CNContactPhoneNumbersKey)
        return picker
    }
    func updateUIViewController(_ controller:CNContactPickerViewController,context:Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(parent:self) }
    final class Coordinator:NSObject,CNContactPickerDelegate {
        let parent:PawlyContactPicker
        init(parent:PawlyContactPicker){ self.parent=parent }
        func contactPickerDidCancel(_ picker:CNContactPickerViewController){ parent.onCancel() }
        func contactPicker(_ picker:CNContactPickerViewController,didSelect property:CNContactProperty){
            guard let phone=property.value as? CNPhoneNumber else { parent.onCancel();return }
            let name=CNContactFormatter.string(from:property.contact,style:.fullName) ?? "Contacto"
            parent.onSelect(name,phone.stringValue)
        }
    }
}

struct PawlyContactDiscoveryView: View {
    var onSearchUsername: (String) -> Void
    @State private var showPicker=false
    @State private var selectedName=""
    @State private var selectedPhone=""
    @State private var verified=false
    @State private var enabled=false
    @State private var testNumber=false
    @State private var busy=false
    @State private var message:String?
    @State private var matches:[ContactMatch]=[]

    private struct ContactMatch:Identifiable {
        let id:String
        let username:String
        let name:String
    }
    private var normalizedPhone:String? {
        let raw=selectedPhone.trimmingCharacters(in:.whitespacesAndNewlines)
        let digits=raw.filter { $0.isASCII && $0.isNumber }
        let normalized:String
        if raw.hasPrefix("+") { normalized="+"+digits }
        else if raw.hasPrefix("00") { normalized="+"+String(digits.dropFirst(2)) }
        else if Locale.current.region?.identifier == "ES" && digits.count == 9 { normalized="+34"+digits }
        else { return nil }
        return normalized.range(of:#"^\+[1-9][0-9]{7,14}$"#,options:.regularExpression) != nil ? normalized:nil
    }

    var body:some View {
        VStack(alignment:.leading,spacing:9) {
            Label("Contactos de mi agenda",systemImage:"person.crop.rectangle").font(.headline)
            Text("Selecciona un único número. Pawly no accede a tu agenda completa ni la sube al servidor.").font(.caption).foregroundStyle(.secondary)
            if verified && !testNumber {
                Toggle("Permitir que me encuentren por teléfono",isOn:Binding(get:{enabled},set:{value in Task { await changeSetting(value) }})).disabled(busy)
            } else {
                Text(testNumber ? "Tu número de pruebas no permite buscar contactos reales." : "Para buscar por teléfono debes tener un número verificado por Pawly. La verificación nativa de iOS aún no está habilitada.").font(.caption).foregroundStyle(.secondary)
            }
            Button { showPicker=true } label:{Label("Seleccionar contacto",systemImage:"person.crop.circle.badge.plus")}.buttonStyle(.bordered)
            if !selectedName.isEmpty {
                Text(selectedName).font(.subheadline.weight(.semibold))
                TextField("Teléfono con prefijo internacional",text:$selectedPhone).keyboardType(.phonePad).textFieldStyle(.roundedBorder)
                    .onChange(of:selectedPhone){_ in matches=[]}
                if normalizedPhone == nil { Text("Introduce el prefijo internacional, por ejemplo +34.").font(.caption).foregroundStyle(.secondary) }
                Button { Task { await findMatches() } } label:{Label("Buscar este contacto en Pawly",systemImage:"magnifyingglass")}
                    .buttonStyle(.borderedProminent).disabled(busy || !verified || !enabled || testNumber || normalizedPhone == nil)
                Button("Buscar por nombre de usuario") { onSearchUsername("") }.buttonStyle(.borderless)
            }
            if busy { ProgressView() }
            if let message { Text(message).font(.caption).foregroundStyle(.secondary) }
            ForEach(matches){match in
                Button { onSearchUsername(match.username) } label:{
                    HStack { VStack(alignment:.leading){Text(match.name).font(.subheadline.weight(.semibold));Text("@\(match.username)").font(.caption)};Spacer();Image(systemName:"chevron.right") }
                }.buttonStyle(.borderless)
            }
        }
        .task { await refresh() }
        .sheet(isPresented:$showPicker) {
            PawlyContactPicker(onSelect:{name,phone in
                selectedName=name;selectedPhone=phone;matches=[];showPicker=false
            },onCancel:{showPicker=false})
        }
    }
    @MainActor private func apply(_ result:[String:Any]) {
        verified=result["verified"] as? Bool ?? false
        enabled=result["enabled"] as? Bool ?? false
        testNumber=result["testNumber"] as? Bool ?? false
    }
    @MainActor private func refresh() async {
        busy=true;defer{busy=false}
        do { apply(try await PawlyBackendClient.postJSON(path:"/v1/phone/discovery/status",body:[:]));message=nil }
        catch { message=error.localizedDescription }
    }
    @MainActor private func changeSetting(_ value:Bool) async {
        busy=true;defer{busy=false}
        do { apply(try await PawlyBackendClient.postJSON(path:"/v1/phone/discovery/settings",body:["enabled":value]));message=nil;matches=[] }
        catch { message=error.localizedDescription }
    }
    @MainActor private func findMatches() async {
        guard verified,enabled,!testNumber,let phone=normalizedPhone else { return }
        busy=true;defer{busy=false}
        do {
            let result=try await PawlyBackendClient.postJSON(path:"/v1/phone/discovery/matches",body:["phoneNumbers":[phone]])
            matches=(result["matches"] as? [[String:Any]] ?? []).compactMap { row in
                guard let uid=row["uid"] as? String,let username=row["username"] as? String,!username.isEmpty else{return nil}
                return ContactMatch(id:uid,username:username,name:row["displayName"] as? String ?? username)
            }
            message=matches.isEmpty ? "No hay coincidencias o el contacto no ha dado su consentimiento." : "Coincidencias encontradas."
        } catch { message=error.localizedDescription;matches=[] }
    }
}
