import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.Reference;

public class DumpFunctionXrefsFromList extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 2) {
            printerr("Usage: DumpFunctionXrefsFromList <addr_list.txt> <out.txt>");
            return;
        }

        File addrFile = new File(args[0]);
        File outFile = new File(args[1]);

        try (BufferedReader reader = new BufferedReader(new FileReader(addrFile));
             PrintWriter out = new PrintWriter(outFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                Address addr = toAddr(line.startsWith("0x") ? line : "0x" + line);
                Function fn = getFunctionAt(addr);
                out.println("### Function @ " + addr);
                if (fn == null) {
                    out.println("Function: <none>");
                    out.println();
                    continue;
                }

                out.println("Function: " + fn.getName() + " @ " + fn.getEntryPoint());
                Reference[] refs = getReferencesTo(fn.getEntryPoint());
                for (Reference ref : refs) {
                    Function caller = getFunctionContaining(ref.getFromAddress());
                    String callerName = caller != null ? caller.getName() : "<no_function>";
                    out.println(ref.getFromAddress() + "\t" + ref.getReferenceType() + "\t" + callerName);
                }
                out.println();
            }
        }

        println("Wrote xrefs to " + outFile.getAbsolutePath());
    }
}
