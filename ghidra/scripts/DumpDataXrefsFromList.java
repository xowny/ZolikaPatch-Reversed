import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.Reference;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class DumpDataXrefsFromList extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 2) {
            println("Usage: DumpDataXrefsFromList <addr_list_path> <out_path>");
            return;
        }

        List<String> lines = Files.readAllLines(Paths.get(args[0]));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(args[1]))) {
            for (String line : lines) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) {
                    continue;
                }

                long addrLong = Long.parseUnsignedLong(s.replace("0x", ""), 16);
                Address addr = toAddr(addrLong);
                bw.write("### Address 0x" + String.format("%08x", addrLong) + "\n");

                Reference[] refs = getReferencesTo(addr);
                if (refs.length == 0) {
                    bw.write("<no xrefs>\n\n");
                    continue;
                }

                for (Reference ref : refs) {
                    Function fn = getFunctionContaining(ref.getFromAddress());
                    bw.write(ref.getFromAddress().toString());
                    bw.write("\t");
                    bw.write(ref.getReferenceType().toString());
                    bw.write("\t");
                    bw.write(fn != null ? fn.getName() : "<no_function>");
                    bw.write("\n");
                }
                bw.write("\n");
            }
        }
    }
}
