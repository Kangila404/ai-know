// Original vector-drawn AiKnow avatars. Re-run from the repository root.
import AppKit
import Foundation
let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let colors: [[CGFloat]] = [[0.40,0.31,0.95], [0.23,0.60,0.65], [0.94,0.55,0.39], [0.49,0.47,0.90], [0.58,0.40,0.78]]
let keys = ["lumi", "mori", "nova", "orbi", "pico"]
func color(_ c: [CGFloat], _ alpha: CGFloat = 1) -> CGColor { CGColor(red:c[0],green:c[1],blue:c[2],alpha:alpha) }
for (index,key) in keys.enumerated() {
    let rep = NSBitmapImageRep(bitmapDataPlanes:nil,pixelsWide:512,pixelsHigh:512,bitsPerSample:8,samplesPerPixel:4,hasAlpha:true,isPlanar:false,colorSpaceName:.deviceRGB,bytesPerRow:0,bitsPerPixel:0)!
    let ctx = NSGraphicsContext(bitmapImageRep:rep)!.cgContext
    let accent = colors[index]
    ctx.setFillColor(color(accent.map { 0.92 + $0 * 0.08 }));ctx.fill(CGRect(x:0,y:0,width:512,height:512))
    ctx.setFillColor(color(accent,0.09));ctx.fillEllipse(in:CGRect(x:60,y:46,width:392,height:392))
    ctx.setFillColor(color(accent,0.13));ctx.fillEllipse(in:CGRect(x:126,y:76,width:260,height:40))
    let shape = CGMutablePath()
    switch index {
    case 0: shape.addEllipse(in:CGRect(x:126,y:126,width:260,height:270))
    case 1:
        shape.addRoundedRect(in:CGRect(x:120,y:130,width:272,height:240),cornerWidth:68,cornerHeight:68)
        ctx.setFillColor(color(accent));let leaf=CGMutablePath();leaf.move(to:CGPoint(x:256,y:360));leaf.addQuadCurve(to:CGPoint(x:202,y:443),control:CGPoint(x:175,y:414));leaf.addQuadCurve(to:CGPoint(x:256,y:360),control:CGPoint(x:272,y:445));ctx.addPath(leaf);ctx.fillPath()
        let right=CGMutablePath();right.move(to:CGPoint(x:255,y:365));right.addQuadCurve(to:CGPoint(x:332,y:430),control:CGPoint(x:276,y:449));right.addQuadCurve(to:CGPoint(x:255,y:365),control:CGPoint(x:350,y:366));ctx.addPath(right);ctx.fillPath()
    case 2:
        let points=(0..<10).map { i -> CGPoint in let a=CGFloat(i)*CGFloat.pi/5+CGFloat.pi/2;let r:CGFloat=i%2==0 ? 164 : 108;return CGPoint(x:256+cos(a)*r,y:267+sin(a)*r) }
        shape.move(to:points[0]);for point in points.dropFirst(){shape.addLine(to:point)};shape.closeSubpath()
    case 3: shape.addEllipse(in:CGRect(x:126,y:128,width:260,height:260))
    default:
        shape.addRoundedRect(in:CGRect(x:108,y:142,width:296,height:234),cornerWidth:52,cornerHeight:52)
    }
    ctx.saveGState();ctx.addPath(shape);ctx.clip()
    let gradient=CGGradient(colorsSpace:CGColorSpaceCreateDeviceRGB(),colors:[color(accent.map{min(1,$0+0.18)}),color(accent)] as CFArray,locations:[0,1])!
    ctx.drawLinearGradient(gradient,start:CGPoint(x:145,y:405),end:CGPoint(x:355,y:140),options:[]);ctx.restoreGState()
    if index == 3 {
        ctx.saveGState();ctx.translateBy(x:256,y:258);ctx.rotate(by:0.3);ctx.setStrokeColor(CGColor(red:0.79,green:0.76,blue:1,alpha:1));ctx.setLineWidth(22);ctx.addEllipse(in:CGRect(x:-192,y:-64,width:384,height:128));ctx.strokePath();ctx.restoreGState()
        ctx.setFillColor(CGColor(red:1,green:0.77,blue:0.49,alpha:1));ctx.fillEllipse(in:CGRect(x:363,y:341,width:42,height:42))
    }
    if index == 4 {
        ctx.setStrokeColor(CGColor(gray:1,alpha:0.3));ctx.setLineWidth(5);ctx.move(to:CGPoint(x:256,y:160));ctx.addLine(to:CGPoint(x:256,y:354));ctx.strokePath()
    }
    // Faces retain the same proportions and ink tone across all five characters.
    let ink=CGColor(red:0.16,green:0.13,blue:0.29,alpha:1)
    for x:CGFloat in [204,308] {
        ctx.setFillColor(CGColor(gray:1,alpha:1));ctx.fillEllipse(in:CGRect(x:x-22,y:252,width:44,height:58))
        ctx.setFillColor(ink);ctx.fillEllipse(in:CGRect(x:x-10,y:263,width:20,height:30))
        ctx.setFillColor(CGColor(gray:1,alpha:1));ctx.fillEllipse(in:CGRect(x:x-4,y:282,width:6,height:6))
    }
    ctx.setFillColor(CGColor(red:1,green:0.71,blue:0.77,alpha:0.65));for x:CGFloat in [172,328] {ctx.fillEllipse(in:CGRect(x:x,y:228,width:30,height:16))}
    ctx.setStrokeColor(ink);ctx.setLineWidth(7);ctx.setLineCap(.round);ctx.move(to:CGPoint(x:237,y:229));ctx.addQuadCurve(to:CGPoint(x:275,y:229),control:CGPoint(x:256,y:209));ctx.strokePath()
    let data=rep.representation(using:.png,properties:[:])!
    let asset=root.appendingPathComponent("ios/AiKnow/AiKnow/Assets.xcassets/Avatar-\(key).imageset")
    try FileManager.default.createDirectory(at:asset,withIntermediateDirectories:true)
    try data.write(to:asset.appendingPathComponent("\(key)-v1.png"))
    let manifest:[String:Any] = ["images":[["filename":"\(key)-v1.png","idiom":"universal"]],"info":["author":"xcode","version":1]]
    try JSONSerialization.data(withJSONObject:manifest,options:[.prettyPrinted,.sortedKeys]).write(to:asset.appendingPathComponent("Contents.json"))
    let server=root.appendingPathComponent("server/src/main/resources/static/avatars")
    try FileManager.default.createDirectory(at:server,withIntermediateDirectories:true)
    try data.write(to:server.appendingPathComponent("\(key)-v1.png"))
    print(key,data.count)
}
