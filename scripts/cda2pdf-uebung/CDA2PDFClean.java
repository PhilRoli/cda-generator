import at.gv.elga.cda2pdflib.addon.CDA2PDFBuilder;
import at.gv.elga.cda2pdflib.CDA2PDFConverter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class CDA2PDFClean {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: CDA2PDFClean <input.xml> <output-folder/> <output-filename.pdf>");
            System.exit(1);
        }
        final String input = args[0];
        final String outDir = args[1];
        final String outName = args[2];

        CDA2PDFBuilder builder = new CDA2PDFBuilder();
        builder.setAuthUser("CDA-Konverter");
        builder.enableFullDocument();

        CDA2PDFConverter converter = new CDA2PDFConverter(builder);
        try (FileInputStream in = new FileInputStream(input)) {
            ByteArrayOutputStream baos = converter.xmlToPdfPerXsl(in);
            if (baos == null) {
                System.err.println("Conversion returned null — XML may be invalid or unsupported.");
                System.exit(1);
            }
            String outPath = outDir.endsWith("/") ? outDir + outName : outDir + "/" + outName;
            try (FileOutputStream fos = new FileOutputStream(outPath)) {
                baos.writeTo(fos);
            }
            System.out.println("successfully created pdf file: " + outPath);
        }
    }
}
