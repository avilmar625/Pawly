import Foundation
import UIKit

enum PawlyPDFExporter {
    static func create(pet:PawlyPet,passport:[PawlyPassportEntry],tasks:[PawlyCareTask]) throws -> URL {
        let page=CGRect(x:0,y:0,width:595,height:842)
        let renderer=UIGraphicsPDFRenderer(bounds:page)
        let data=renderer.pdfData { context in
            context.beginPage()
            var y:CGFloat=48
            func draw(_ value:String,size:CGFloat,bold:Bool=false,color:UIColor = .darkText,spacing:CGFloat=10) {
                let font=bold ? UIFont.boldSystemFont(ofSize:size) : UIFont.systemFont(ofSize:size)
                let attributes:[NSAttributedString.Key:Any]=[.font:font,.foregroundColor:color]
                let width=page.width-80
                let bounds=(value as NSString).boundingRect(with:CGSize(width:width,height:2000),options:.usesLineFragmentOrigin,attributes:attributes,context:nil)
                let height=max(bounds.height, size+4)
                if y+height > page.height-48 { context.beginPage();y=48 }
                (value as NSString).draw(in:CGRect(x:40,y:y,width:width,height:height),withAttributes:attributes)
                y += height+spacing
            }
            draw("Pawly · Pasaporte de cuidados",size:23,bold:true,color:UIColor(red:0.17,green:0.48,blue:0.39,alpha:1),spacing:8)
            draw(pet.name,size:19,bold:true,spacing:4)
            let details=[pet.species,pet.breed,pet.age].filter{!$0.isEmpty}.joined(separator:" · ")
            if !details.isEmpty { draw(details,size:11,color:.secondaryLabel,spacing:18) }
            draw("PASAPORTE SANITARIO",size:14,bold:true,color:UIColor(red:0.17,green:0.48,blue:0.39,alpha:1),spacing:9)
            if passport.isEmpty { draw("Todavía no hay registros sanitarios.",size:11,color:.secondaryLabel) }
            for entry in passport {
                draw("\(entry.date) · \(entry.type): \(entry.title)",size:12,bold:true,spacing:3)
                if !entry.detail.isEmpty { draw(entry.detail,size:10,color:.secondaryLabel,spacing:10) }
            }
            draw("RUTINAS DE CUIDADO",size:14,bold:true,color:UIColor(red:0.17,green:0.48,blue:0.39,alpha:1),spacing:9)
            if tasks.isEmpty { draw("Todavía no hay rutinas registradas.",size:11,color:.secondaryLabel) }
            for task in tasks {
                draw("\(task.completed ? "✓" : "○") \(task.title)",size:12,bold:true,spacing:3)
                let detail=[task.frequency,task.responsible].filter{!$0.isEmpty}.joined(separator:" · ")
                if !detail.isEmpty { draw(detail,size:10,color:.secondaryLabel,spacing:9) }
            }
            draw("Generado con Pawly · \(Date.now.formatted(date:.long,time:.shortened))",size:8,color:.tertiaryLabel,spacing:0)
        }
        let url=FileManager.default.temporaryDirectory.appendingPathComponent("Pawly-\(pet.name.replacingOccurrences(of:" ",with:"-"))-cuidados.pdf")
        try data.write(to:url,options:.atomic)
        return url
    }
}
