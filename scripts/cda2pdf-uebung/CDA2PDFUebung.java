// Wrapper um den ELGA CDA2PDF Konverter mit Übungs-spezifischen Optionen:
//   - hideDocumentInformation()  → entfernt den "Zusätzliche Informationen"-Block
//   - enableFullDocument() WIRD NICHT aufgerufen → "normale" (kürzere) PDF-Variante
//   - setBannerText("ÜBUNGSDOKUMENT")  → Probe für Watermark-ähnlichen Hinweis
//
// Aufruf (analog Demo):
//   java -cp "CDA2PDFUebung.jar:CDA2PDF-API.jar:CDA2PDF-DEPS.jar" CDA2PDFUebung <in.xml> <out-dir/> <out.pdf>

import at.gv.elga.cda2pdflib.addon.CDA2PDFBuilder;
import at.gv.elga.cda2pdflib.CDA2PDFConverter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class CDA2PDFUebung {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: CDA2PDFUebung <input.xml> <output-folder/> <output-filename.pdf>");
            System.exit(1);
        }
        final String input = args[0];
        final String outDir = args[1];
        final String outName = args[2];

        CDA2PDFBuilder builder = new CDA2PDFBuilder();
        builder.setAuthUser("Übungs-Generator");
        // enableFullDocument() bewusst NICHT aufrufen → "normale" Variante
        builder.hideDocumentInformation();
        builder.setBannerText("ÜBUNGSDOKUMENT — NUR FÜR TRAININGS!");

        CDA2PDFConverter converter = new CDA2PDFConverter(builder);
        try (FileInputStream in = new FileInputStream(input)) {
            ByteArrayOutputStream baos = converter.xmlToPdfPerXsl(in);
            String outPath = outDir.endsWith("/") ? outDir + outName : outDir + "/" + outName;
            try (FileOutputStream fos = new FileOutputStream(outPath)) {
                baos.writeTo(fos);
            }
            System.out.println("successfully created pdf file: " + outPath);
        }
    }
}
